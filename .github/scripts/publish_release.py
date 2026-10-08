"""Publish a verified APK and update the app version file as the final step."""

import argparse
import base64
import binascii
import json
import os
import re
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
        raise ReleaseError("The remote version file cannot be parsed; refusing to overwrite it") from error
    if not isinstance(document, dict) or not isinstance(blob_sha, str) or not blob_sha:
        raise ReleaseError("The remote version file has no valid JSON or blob SHA")
    return document, blob_sha


def version_is_current(artifact, latest):
    code = latest.get("versionCode", 0)
    if type(code) is not int or code < 0:
        raise ReleaseError("The remote versionCode is invalid; refusing to overwrite it")
    if code > artifact.version_code:
        return False
    if code == artifact.version_code:
        same = (latest.get("sourceSha") == artifact.source_sha
                and latest.get("apkSha256") == artifact.sha256
                and latest.get("signingCertificateSha256") == artifact.certificate_sha256)
        if not same:
            raise ReleaseError("The same versionCode refers to different source, APK, or certificate; increment the version code")
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
        raise ReleaseError("The version tag points to a different source commit; moving or force-overwriting it is not allowed")


def find_release(artifact, client):
    release = client.api("GET", f"repos/{client.repo}/releases/tags/{quote(artifact.tag, safe='')}", missing_ok=True)
    if release is not None:
        return release
    # Handle draft releases omitted by tag lookup; retrieve only public release metadata.
    for page in range(1, 101):
        items = client.api("GET", f"repos/{client.repo}/releases?per_page=100&page={page}")
        matches = [item for item in items if item.get("tag_name") == artifact.tag]
        if len(matches) > 1:
            raise ReleaseError("Multiple releases use the same tag; resolve the conflict first")
        if matches:
            return matches[0]
        if len(items) < 100:
            return None
    raise ReleaseError("The release list exceeds the verification limit; refusing to create a potentially duplicate release")


def matching_asset(artifact, release):
    assets = [item for item in release.get("assets", []) if item.get("name") == artifact.apk.name]
    if len(assets) > 1:
        raise ReleaseError("Multiple APK assets have the same name")
    return assets[0] if assets else None


def verify_asset(artifact, client, asset, allow_draft=False):
    if not asset or asset.get("state") != "uploaded" or asset.get("size") != artifact.size:
        raise ReleaseError("The remote APK asset is incomplete or has a different size; refusing to publish the version file")
    if str(asset.get("digest", "")).lower() != "sha256:" + artifact.sha256:
        raise ReleaseError("The remote APK SHA-256 does not match the final signed APK")
    url = asset.get("browser_download_url", "")
    parsed = urlparse(url)
    prefix = f"/{client.repo}/releases/download/"
    path = unquote(parsed.path)
    parts = path[len(prefix):].split("/") if path.startswith(prefix) else []
    valid_tag = len(parts) == 2 and (parts[0] == artifact.tag or
                (allow_draft and re.fullmatch(r"untagged-[0-9a-fA-F]+", parts[0]) is not None))
    if (parsed.scheme != "https" or parsed.netloc != "github.com" or parsed.query or parsed.fragment
            or not valid_tag or parts[1] != artifact.apk.name):
        raise ReleaseError("The APK download URL is not the fixed asset URL for this repository, tag, and file")
    return url


def latest_document(artifact, url, previous):
    return {"versionCode": artifact.version_code, "versionName": artifact.version_name,
            "apkName": artifact.apk.name, "apkSize": artifact.size, "apkUrl": url,
            "apkSha256": artifact.sha256, "signingCertificateSha256": artifact.certificate_sha256,
            "sourceSha": artifact.source_sha, "releaseTag": artifact.tag,
            "updatedContentZh": previous.get("updatedContentZh", "Automated build"),
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
            "name": f"Konachan {artifact.version_name} (build {artifact.run_number})",
            "body": f"Automatically built APK.\nSource commit: {artifact.source_sha}\nAPK SHA-256: {artifact.sha256}",
            "draft": True, "prerelease": True, "make_latest": "false"})
    if not isinstance(release, dict) or type(release.get("draft")) is not bool or not release.get("id"):
        raise ReleaseError("GitHub did not return a valid release status")
    asset = matching_asset(artifact, release)
    if release["draft"]:
        if asset and asset.get("state") == "starter":
            client.api("DELETE", f"repos/{client.repo}/releases/assets/{asset['id']}")
            asset = None
        if asset is None:
            client.upload(artifact.tag, artifact.apk)
        else:
            verify_asset(artifact, client, asset, allow_draft=True)
        release = client.api("GET", f"repos/{client.repo}/releases/{release['id']}")
        verify_asset(artifact, client, matching_asset(artifact, release),
                     allow_draft=release.get("draft") is True)
        client.api("PATCH", f"repos/{client.repo}/releases/{release['id']}",
                   {"draft": False, "prerelease": True, "make_latest": "false"})
        release = client.api("GET", f"repos/{client.repo}/releases/{release['id']}")
        if release.get("draft") is not False or release.get("prerelease") is not True:
            raise ReleaseError("GitHub did not confirm that the APK prerelease was published successfully")
    url = verify_asset(artifact, client, matching_asset(artifact, release))
    client.verify_download(artifact, url)
    # Read the latest blob after publishing to prevent an older run from overwriting a newer version.
    current, blob_sha = read_latest(client)
    if not version_is_current(artifact, current):
        return {"status": "superseded", "tag": artifact.tag, "versionCode": artifact.version_code}
    document = latest_document(artifact, url, current)
    if document == current:
        return {"status": "unchanged", "tag": artifact.tag, "apkUrl": url}
    body = {"message": f"build: update version {artifact.version_name} metadata [skip ci]", "branch": "master",
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
        print(f"Release failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
