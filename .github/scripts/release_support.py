"""发布脚本的 APK 元数据和 gh CLI 适配，全部使用标准库。"""

from dataclasses import dataclass
from pathlib import Path
import hashlib
import json
import re
import subprocess
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener


class ReleaseError(RuntimeError):
    def __init__(self, message, status=None):
        super().__init__(message)
        self.status = status


@dataclass(frozen=True)
class Artifact:
    apk: Path
    source_sha: str
    run_number: int
    version_code: int
    version_name: str
    size: int
    sha256: str
    certificate_sha256: str
    package_name: str

    @property
    def tag(self):
        return f"v{self.version_name}-{self.run_number}"


def load_artifact(apk, source_sha, run_number, metadata=None, signing_metadata=None):
    apk = Path(apk).resolve()
    if not isinstance(source_sha, str) or not re.fullmatch(r"[0-9a-fA-F]{40}", source_sha):
        raise ReleaseError("sourceSHA 必须是完整提交 SHA")
    if type(run_number) is not int or run_number < 1:
        raise ReleaseError("run_number 必须是正整数")
    if not apk.is_file() or apk.suffix.lower() != ".apk" or apk.stat().st_size == 0:
        raise ReleaseError("最终签名 APK 不存在或为空")
    build = read_json(Path(metadata) if metadata else apk.parent / "output-metadata.json")
    signing = read_json(Path(signing_metadata) if signing_metadata else Path(str(apk) + ".signing.json"))
    if not isinstance(build.get("elements"), list):
        raise ReleaseError("Gradle 输出元数据缺少 elements 列表")
    elements = [item for item in build["elements"]
                if isinstance(item, dict) and item.get("outputFile") == apk.name]
    if len(elements) != 1:
        raise ReleaseError("output-metadata.json 必须唯一匹配最终 APK 文件名")
    item = elements[0]
    code, name, package = item.get("versionCode"), item.get("versionName"), build.get("applicationId")
    if type(code) is not int or code < 1:
        raise ReleaseError("APK versionCode 必须是正整数")
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]*", name) or ".." in name:
        raise ReleaseError("APK versionName 不适合作为固定标签")
    if not isinstance(package, str) or not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", package):
        raise ReleaseError("APK applicationId 无效")
    if signing.get("verified") is not True:
        raise ReleaseError("最终 APK 必须通过签名校验")
    for field, expected in (("versionCode", code), ("versionName", name), ("packageName", package)):
        if signing.get(field) != expected:
            raise ReleaseError(f"最终 APK {field} 与 Gradle 输出元数据不一致")
    before = apk.stat()
    digest = hashlib.sha256()
    with apk.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    sha256 = digest.hexdigest()
    after = apk.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise ReleaseError("最终 APK 在校验过程中改变")
    signed_hash = signing.get("apkSha256")
    if not isinstance(signed_hash, str) or signed_hash.lower() != sha256:
        raise ReleaseError("签名校验记录不属于最终 APK 字节")
    certificate = signing.get("signingCertificateSha256", "")
    certificate = certificate.replace(":", "").lower() if isinstance(certificate, str) else ""
    if not re.fullmatch(r"[0-9a-f]{64}", certificate):
        raise ReleaseError("公有签名证书 SHA-256 无效")
    return Artifact(apk, source_sha.lower(), run_number, code, name, after.st_size,
                    sha256, certificate, package)


def read_json(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as error:
        raise ReleaseError(f"无法读取公有校验元数据: {path.name}") from error
    if not isinstance(data, dict):
        raise ReleaseError(f"校验元数据必须是 JSON 对象: {path.name}")
    return data


def run_gh(arguments, input_text=None):
    # 使用参数数组，不经过 shell；GH_TOKEN 只由 gh 继承，脚本不读取或输出它。
    return subprocess.run(arguments, input=input_text, capture_output=True,
                          text=True, encoding="utf-8", check=False)


PUBLIC_HEADERS = {"User-Agent": "Konachan-Release-Publisher", "Accept": "application/octet-stream"}


def require_public_asset_host(url):
    parsed = urlparse(url)
    if (parsed.scheme != "https" or parsed.hostname not in
            {"github.com", "release-assets.githubusercontent.com"}
            or parsed.username is not None or parsed.password is not None
            or parsed.port not in (None, 443)):
        raise ValueError("公开 APK 重定向主机不在允许列表")


class PublicAssetRedirect(HTTPRedirectHandler):
    max_redirections = 5

    def redirect_request(self, request, fp, code, message, headers, new_url):
        require_public_asset_host(new_url)
        redirected = super().redirect_request(request, fp, code, message, headers, new_url)
        if redirected is None:
            return None
        # 不继承原请求的认证/Cookie 等头，只保留公开下载专用的两个静态头。
        return Request(redirected.full_url, headers=PUBLIC_HEADERS, method="GET")


def open_public_download(url):
    require_public_asset_host(url)
    # 禁用环境代理与认证处理器，绝不把 GH_TOKEN 或代理凭据带到资产域。
    opener = build_opener(ProxyHandler({}), PublicAssetRedirect())
    return opener.open(Request(url, headers=PUBLIC_HEADERS, method="GET"), timeout=30)


class GhClient:
    def __init__(self, repo, runner=run_gh, downloader=open_public_download):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo or ""):
            raise ReleaseError("仓库必须使用 owner/repo 格式")
        self.repo, self.runner = repo, runner
        self.downloader = downloader

    def api(self, method, path, payload=None, missing_ok=False):
        arguments = ["gh", "api", path, "--method", method, "--include",
                     "-H", "Accept: application/vnd.github+json",
                     "-H", "X-GitHub-Api-Version: 2026-03-10"]
        body = None
        if payload is not None:
            arguments += ["--input", "-"]
            body = json.dumps(payload, ensure_ascii=False)
        result = self.runner(arguments, body)
        output = (result.stdout or "").replace("\r\n", "\n")
        header, separator, content = output.partition("\n\n")
        match = re.match(r"HTTP/\S+\s+(\d{3})\b", header)
        if not match:
            raise ReleaseError("gh API 未返回可核验的 HTTP 状态")
        status = int(match.group(1))
        if missing_ok and status == 404:
            return None
        if result.returncode or not 200 <= status < 300:
            raise ReleaseError(f"GitHub API {method} {path.split('?')[0]} 失败（HTTP {status}）", status)
        if status == 204 or not content.strip():
            return None
        try:
            return json.loads(content)
        except ValueError as error:
            raise ReleaseError("GitHub API 返回了不可解析的 JSON") from error

    def upload(self, tag, apk):
        # 不使用 --clobber，绝不先删已成功上传的资产。
        result = self.runner(["gh", "release", "upload", tag, str(apk), "--repo", self.repo], None)
        if result.returncode:
            raise ReleaseError("APK 资产上传失败；保留原版本文件")

    def verify_download(self, artifact, url):
        try:
            require_public_asset_host(url)
            digest, size = hashlib.sha256(), 0
            with self.downloader(url) as response:
                if response.status != 200:
                    raise ValueError("公开下载未返回完整文件")
                while True:
                    block = response.read(1024 * 1024)
                    if not block:
                        break
                    size += len(block)
                    if size > artifact.size:
                        raise ValueError("公开下载大小超过最终 APK")
                    digest.update(block)
            if size != artifact.size or digest.hexdigest() != artifact.sha256:
                raise ValueError("公开下载大小或 SHA-256 不匹配")
        except Exception:
            # urllib 异常可能含临时签名 URL，禁止转发其异常正文或原因链。
            raise ReleaseError("公开 APK 下载验证失败；保留原版本文件") from None
