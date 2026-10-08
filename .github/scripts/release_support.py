"""APK metadata and gh CLI adapters for release scripts, using only the standard library."""

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
        raise ReleaseError("sourceSHA must be a full commit SHA")
    if type(run_number) is not int or run_number < 1:
        raise ReleaseError("run_number must be a positive integer")
    if not apk.is_file() or apk.suffix.lower() != ".apk" or apk.stat().st_size == 0:
        raise ReleaseError("The final signed APK is missing or empty")
    build = read_json(Path(metadata) if metadata else apk.parent / "output-metadata.json")
    signing = read_json(Path(signing_metadata) if signing_metadata else Path(str(apk) + ".signing.json"))
    if not isinstance(build.get("elements"), list):
        raise ReleaseError("Gradle output metadata is missing the elements list")
    elements = [item for item in build["elements"]
                if isinstance(item, dict) and item.get("outputFile") == apk.name]
    if len(elements) != 1:
        raise ReleaseError("output-metadata.json must uniquely match the final APK filename")
    item = elements[0]
    code, name, package = item.get("versionCode"), item.get("versionName"), build.get("applicationId")
    if type(code) is not int or code < 1:
        raise ReleaseError("APK versionCode must be a positive integer")
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]*", name) or ".." in name:
        raise ReleaseError("APK versionName cannot be used as a fixed tag")
    if not isinstance(package, str) or not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", package):
        raise ReleaseError("APK applicationId is invalid")
    if signing.get("verified") is not True:
        raise ReleaseError("The final APK must pass signature verification")
    for field, expected in (("versionCode", code), ("versionName", name), ("packageName", package)):
        if signing.get(field) != expected:
            raise ReleaseError(f"The final APK {field} does not match the Gradle output metadata")
    before = apk.stat()
    digest = hashlib.sha256()
    with apk.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    sha256 = digest.hexdigest()
    after = apk.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise ReleaseError("The final APK changed during verification")
    signed_hash = signing.get("apkSha256")
    if not isinstance(signed_hash, str) or signed_hash.lower() != sha256:
        raise ReleaseError("The signature verification record does not match the final APK bytes")
    certificate = signing.get("signingCertificateSha256", "")
    certificate = certificate.replace(":", "").lower() if isinstance(certificate, str) else ""
    if not re.fullmatch(r"[0-9a-f]{64}", certificate):
        raise ReleaseError("The public signing certificate SHA-256 is invalid")
    return Artifact(apk, source_sha.lower(), run_number, code, name, after.st_size,
                    sha256, certificate, package)


def read_json(path):
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as error:
        raise ReleaseError(f"Unable to read public verification metadata: {path.name}") from error
    if not isinstance(data, dict):
        raise ReleaseError(f"Verification metadata must be a JSON object: {path.name}")
    return data


def run_gh(arguments, input_text=None):
    # Pass an argument array without a shell; only gh inherits GH_TOKEN, and this script never reads or prints it.
    return subprocess.run(arguments, input=input_text, capture_output=True,
                          text=True, encoding="utf-8", check=False)


PUBLIC_HEADERS = {"User-Agent": "Konachan-Release-Publisher", "Accept": "application/octet-stream"}


def require_public_asset_host(url):
    parsed = urlparse(url)
    if (parsed.scheme != "https" or parsed.hostname not in
            {"github.com", "release-assets.githubusercontent.com"}
            or parsed.username is not None or parsed.password is not None
            or parsed.port not in (None, 443)):
        raise ValueError("The public APK redirect host is not on the allowlist")


class PublicAssetRedirect(HTTPRedirectHandler):
    max_redirections = 5

    def redirect_request(self, request, fp, code, message, headers, new_url):
        require_public_asset_host(new_url)
        redirected = super().redirect_request(request, fp, code, message, headers, new_url)
        if redirected is None:
            return None
        # Do not forward authentication, cookies, or other original request headers; retain only the two static public-download headers.
        return Request(redirected.full_url, headers=PUBLIC_HEADERS, method="GET")


def open_public_download(url):
    require_public_asset_host(url)
    # Disable environment proxies and authentication handlers so GH_TOKEN and proxy credentials never reach the asset host.
    opener = build_opener(ProxyHandler({}), PublicAssetRedirect())
    return opener.open(Request(url, headers=PUBLIC_HEADERS, method="GET"), timeout=30)


class GhClient:
    def __init__(self, repo, runner=run_gh, downloader=open_public_download):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo or ""):
            raise ReleaseError("The repository must use the owner/repo format")
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
            raise ReleaseError("gh API did not return a verifiable HTTP status")
        status = int(match.group(1))
        if missing_ok and status == 404:
            return None
        if result.returncode or not 200 <= status < 300:
            raise ReleaseError(f"GitHub API {method} {path.split('?')[0]} failed (HTTP {status})", status)
        if status == 204 or not content.strip():
            return None
        try:
            return json.loads(content)
        except ValueError as error:
            raise ReleaseError("GitHub API returned invalid JSON") from error

    def upload(self, tag, apk):
        # Do not use --clobber or delete an asset that was already uploaded successfully.
        result = self.runner(["gh", "release", "upload", tag, str(apk), "--repo", self.repo], None)
        if result.returncode:
            raise ReleaseError("APK asset upload failed; preserving the original version file")

    def verify_download(self, artifact, url):
        try:
            require_public_asset_host(url)
            digest, size = hashlib.sha256(), 0
            with self.downloader(url) as response:
                if response.status != 200:
                    raise ValueError("The public download did not return a complete file")
                while True:
                    block = response.read(1024 * 1024)
                    if not block:
                        break
                    size += len(block)
                    if size > artifact.size:
                        raise ValueError("The public download exceeds the size of the final APK")
                    digest.update(block)
            if size != artifact.size or digest.hexdigest() != artifact.sha256:
                raise ValueError("The public download size or SHA-256 does not match")
        except Exception:
            # urllib exceptions may contain temporary signed URLs; do not expose their text or cause chain.
            raise ReleaseError("Public APK download verification failed; preserving the original version file") from None
