"""完全在内存模拟 GitHub，测试不会调用 gh 或网络。"""

import base64
import copy
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".github" / "scripts"))
from release_support import Artifact, ReleaseError


def artifact(apk):
    return Artifact(apk, "a" * 40, 7, 35, "1.9.6", apk.stat().st_size,
                    hashlib.sha256(apk.read_bytes()).hexdigest(), "c" * 64,
                    "com.ess.anime.wallpaper")


class FakeGitHub:
    repo = "test-owner/Konachan"

    def __init__(self, candidate):
        self.artifact = candidate
        self.latest = {"versionCode": 34, "versionName": "1.9.5",
                       "updatedContentZh": "12 个站点", "updatedContentEn": "12 sites"}
        self.blob = "blob-1"
        self.ref = None
        self.release = None
        self.calls = []
        self.fail_upload = False
        self.bad_digest = False
        self.fail_publish = False
        self.fail_contents = False
        self.advance_after_publish = False
        self.start_asset = False
        self.fail_download = False
        self.download_size_mismatch = False
        self.download_hash_mismatch = False
        self.draft_tag = "untagged-51efee1e84f7f4552b8f"
        self.keep_untagged_after_publish = False

    def uploaded_asset(self, draft=None):
        item = self.artifact
        if draft is None:
            draft = self.release is not None and self.release["draft"]
        tag = self.draft_tag if draft else item.tag
        return {"id": 20, "name": item.apk.name, "size": item.size, "state": "uploaded",
                "digest": "sha256:" + ("0" * 64 if self.bad_digest else item.sha256),
                "browser_download_url": f"https://github.com/{self.repo}/releases/download/{tag}/{item.apk.name}"}

    def set_existing_release(self, draft=False):
        self.ref = {"object": {"type": "commit", "sha": self.artifact.source_sha}}
        self.release = {"id": 10, "tag_name": self.artifact.tag,
                        "draft": draft, "prerelease": True, "assets": [self.uploaded_asset(draft)]}

    def api(self, method, path, payload=None, missing_ok=False):
        self.calls.append((method, path, copy.deepcopy(payload)))
        endpoint = path.removeprefix(f"repos/{self.repo}/")
        if endpoint.startswith("contents/"):
            if method == "GET":
                return {"sha": self.blob, "content": base64.b64encode(
                    json.dumps(self.latest).encode()).decode()}
            if method == "PUT":
                if self.fail_contents or payload.get("sha") != self.blob:
                    raise ReleaseError("版本文件冲突", 409)
                self.latest = json.loads(base64.b64decode(payload["content"]))
                self.blob = "blob-2"
                return {"commit": {"sha": "d" * 40}}
        if endpoint.startswith("git/ref/tags/"):
            return copy.deepcopy(self.ref)
        if endpoint == "git/refs" and method == "POST":
            self.ref = {"object": {"type": "commit", "sha": payload["sha"]}}
            return copy.deepcopy(self.ref)
        if endpoint.startswith("git/tags/"):
            return {"object": {"type": "commit", "sha": self.artifact.source_sha}}
        if endpoint.startswith("releases/tags/"):
            return copy.deepcopy(self.release)
        if endpoint.startswith("releases?"):
            return [copy.deepcopy(self.release)] if self.release else []
        if endpoint == "releases" and method == "POST":
            self.release = {"id": 10, "tag_name": payload["tag_name"], "draft": True,
                            "prerelease": True, "assets": []}
            return copy.deepcopy(self.release)
        if endpoint.startswith("releases/assets/") and method == "DELETE":
            self.release["assets"] = []
            return None
        if endpoint == "releases/10":
            if method == "PATCH":
                if self.fail_publish:
                    raise ReleaseError("发布失败", 500)
                self.release.update(payload)
                if not self.keep_untagged_after_publish:
                    for asset in self.release["assets"]:
                        asset["browser_download_url"] = asset["browser_download_url"].replace(
                            "/" + self.draft_tag + "/", "/" + self.artifact.tag + "/")
                if self.advance_after_publish:
                    self.latest["versionCode"] = self.artifact.version_code + 1
                    self.blob = "newer-blob"
            return copy.deepcopy(self.release)
        raise AssertionError(f"未模拟的请求: {method} {path}")

    def upload(self, tag, apk):
        self.calls.append(("UPLOAD", tag, str(apk)))
        if self.fail_upload:
            raise ReleaseError("上传失败")
        self.release["assets"] = [self.uploaded_asset()]

    def verify_download(self, candidate, url):
        self.calls.append(("DOWNLOAD", url, None))
        if self.fail_download or self.download_size_mismatch or self.download_hash_mismatch:
            raise ReleaseError("公开 APK 下载验证失败；保留原版本文件")
