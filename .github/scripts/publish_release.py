"""发布经过校验的 APK，并在最后更新应用版本文件。"""

import argparse
import base64
import binascii
import json
import os
import sys
from urllib.parse import quote, unquote, urlparse

from release_support import GhClient, ReleaseError, load_artifact


def read_latest(client):
    response = client.api("GET", f"repos/{client.repo}/contents/latest_version.json?ref=master", missing_ok=True)
    if response is None:
        return {}, None
    try:
        document = json.loads(base64.b64decode(response["content"]))
        blob_sha = response["sha"]
    except (KeyError, TypeError, ValueError, binascii.Error) as error:
        raise ReleaseError("远端版本文件不可解析，禁止覆盖") from error
    if not isinstance(document, dict) or not isinstance(blob_sha, str) or not blob_sha:
        raise ReleaseError("远端版本文件缺少有效 JSON 或 blob SHA")
    return document, blob_sha


def version_is_current(artifact, latest):
    code = latest.get("versionCode", 0)
    if type(code) is not int or code < 0:
        raise ReleaseError("远端 versionCode 无效，禁止覆盖")
    if code > artifact.version_code:
        return False
    if code == artifact.version_code:
        same = (latest.get("sourceSha") == artifact.source_sha
                and latest.get("apkSha256") == artifact.sha256
                and latest.get("signingCertificateSha256") == artifact.certificate_sha256)
        if not same:
            raise ReleaseError("相同 versionCode 对应不同源码、APK 或证书；必须增加版本码")
    return True


def ensure_tag(artifact, client):
    path = f"repos/{client.repo}/git/ref/tags/{quote(artifact.tag, safe='')}"
    ref = client.api("GET", path, missing_ok=True)
    if ref is None:
        try:
            ref = client.api("POST", f"repos/{client.repo}/git/refs",
                             {"ref": "refs/tags/" + artifact.tag, "sha": artifact.source_sha})
        except ReleaseError as error:
            if error.status not in (409, 422):
                raise
            ref = client.api("GET", path)
    target = ref.get("object", {}) if isinstance(ref, dict) else {}
    for _ in range(8):
        if target.get("type") != "tag":
            break
        tag = client.api("GET", f"repos/{client.repo}/git/tags/{target.get('sha', '')}")
        target = tag.get("object", {})
    if target.get("type") != "commit" or target.get("sha") != artifact.source_sha:
        raise ReleaseError("版本标签已指向其他源码提交；禁止移动或强制覆盖")


def find_release(artifact, client):
    release = client.api("GET", f"repos/{client.repo}/releases/tags/{quote(artifact.tag, safe='')}", missing_ok=True)
    if release is not None:
        return release
    # 兼容按标签查询未返回草稿的情形；只检索版本公开元数据。
    for page in range(1, 101):
        items = client.api("GET", f"repos/{client.repo}/releases?per_page=100&page={page}")
        matches = [item for item in items if item.get("tag_name") == artifact.tag]
        if len(matches) > 1:
            raise ReleaseError("同标签存在多个 Release，需先明确冲突")
        if matches:
            return matches[0]
        if len(items) < 100:
            return None
    raise ReleaseError("Release 列表超出核验范围，禁止盲目创建重复版本")


def matching_asset(artifact, release):
    assets = [item for item in release.get("assets", []) if item.get("name") == artifact.apk.name]
    if len(assets) > 1:
        raise ReleaseError("同名 APK 资产不唯一")
    return assets[0] if assets else None


def verify_asset(artifact, client, asset):
    if not asset or asset.get("state") != "uploaded" or asset.get("size") != artifact.size:
        raise ReleaseError("远端 APK 资产不完整或大小不一致，禁止发布版本文件")
    if str(asset.get("digest", "")).lower() != "sha256:" + artifact.sha256:
        raise ReleaseError("远端 APK SHA-256 与最终签名 APK 不一致")
    url = asset.get("browser_download_url", "")
    parsed = urlparse(url)
    expected_path = f"/{client.repo}/releases/download/{artifact.tag}/{artifact.apk.name}"
    if (parsed.scheme != "https" or parsed.netloc != "github.com" or parsed.query or parsed.fragment
            or unquote(parsed.path) != expected_path):
        raise ReleaseError("APK 下载地址不是当前仓库、标签和文件的固定资产地址")
    return url


def latest_document(artifact, url, previous):
    return {"versionCode": artifact.version_code, "versionName": artifact.version_name,
            "apkName": artifact.apk.name, "apkSize": artifact.size, "apkUrl": url,
            "apkSha256": artifact.sha256, "signingCertificateSha256": artifact.certificate_sha256,
            "sourceSha": artifact.source_sha, "releaseTag": artifact.tag,
            "updatedContentZh": previous.get("updatedContentZh", "自动构建版本"),
            "updatedContentEn": previous.get("updatedContentEn", "Automated build")}


def publish_release(artifact, client):
    previous, _ = read_latest(client)
    if not version_is_current(artifact, previous):
        return {"status": "superseded", "tag": artifact.tag, "versionCode": artifact.version_code}
    ensure_tag(artifact, client)
    release = find_release(artifact, client)
    if release is None:
        release = client.api("POST", f"repos/{client.repo}/releases", {
            "tag_name": artifact.tag, "target_commitish": artifact.source_sha,
            "name": f"Konachan {artifact.version_name}（构建 {artifact.run_number}）",
            "body": f"自动构建 APK。\n源码提交：{artifact.source_sha}\nAPK SHA-256：{artifact.sha256}",
            "draft": True, "prerelease": True, "make_latest": "false"})
    if not isinstance(release, dict) or type(release.get("draft")) is not bool or not release.get("id"):
        raise ReleaseError("GitHub 未返回有效 Release 状态")
    asset = matching_asset(artifact, release)
    if release["draft"]:
        if asset and asset.get("state") == "starter":
            client.api("DELETE", f"repos/{client.repo}/releases/assets/{asset['id']}")
            asset = None
        if asset is None:
            client.upload(artifact.tag, artifact.apk)
        else:
            verify_asset(artifact, client, asset)
        release = client.api("GET", f"repos/{client.repo}/releases/{release['id']}")
        verify_asset(artifact, client, matching_asset(artifact, release))
        client.api("PATCH", f"repos/{client.repo}/releases/{release['id']}",
                   {"draft": False, "prerelease": True, "make_latest": "false"})
        release = client.api("GET", f"repos/{client.repo}/releases/{release['id']}")
        if release.get("draft") is not False or release.get("prerelease") is not True:
            raise ReleaseError("GitHub 未确认 APK 预发布成功")
    url = verify_asset(artifact, client, matching_asset(artifact, release))
    client.verify_download(artifact, url)
    # 发布完成后重新读最新 blob，阻止旧运行覆盖刚产生的更高版本。
    current, blob_sha = read_latest(client)
    if not version_is_current(artifact, current):
        return {"status": "superseded", "tag": artifact.tag, "versionCode": artifact.version_code}
    document = latest_document(artifact, url, current)
    if document == current:
        return {"status": "unchanged", "tag": artifact.tag, "apkUrl": url}
    body = {"message": f"构建：更新版本 {artifact.version_name} 信息 [skip ci]", "branch": "master",
            "content": base64.b64encode((json.dumps(document, ensure_ascii=False, indent=2) + "\n").encode()).decode()}
    if blob_sha is not None:
        body["sha"] = blob_sha
    client.api("PUT", f"repos/{client.repo}/contents/latest_version.json", body)
    return {"status": "published", "tag": artifact.tag, "versionCode": artifact.version_code, "apkUrl": url}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-number", required=True, type=int)
    parser.add_argument("--metadata")
    parser.add_argument("--signing-metadata")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"))
    args = parser.parse_args()
    try:
        artifact = load_artifact(args.apk, args.source_sha, args.run_number,
                                 args.metadata, args.signing_metadata)
        result = publish_release(artifact, GhClient(args.repo))
        print(json.dumps(result, ensure_ascii=False))
    except (ReleaseError, OSError) as error:
        print(f"发布失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
