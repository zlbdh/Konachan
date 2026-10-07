"""匿名下载、重定向允许列表与流式校验的离线测试。"""

import hashlib
import io
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.request import Request

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".github" / "scripts"))
from release_support import (Artifact, GhClient, PublicAssetRedirect, ReleaseError,
                             open_public_download, require_public_asset_host)


class Response(io.BytesIO):
    status = 200

    def __init__(self, data):
        super().__init__(data)
        self.read_sizes = []

    def read(self, count=-1):
        self.read_sizes.append(count)
        return super().read(count)


class PublicDownloadTest(unittest.TestCase):
    url = "https://github.com/test-owner/Konachan/releases/download/v1.9.6-1/signed.apk"

    def setUp(self):
        self.data = b"public-apk-fixture" * 200000
        self.artifact = Artifact(Path("/unused.apk"), "a" * 40, 1, 35, "1.9.6",
                                 len(self.data), hashlib.sha256(self.data).hexdigest(),
                                 "c" * 64, "com.ess.anime.wallpaper")

    def client(self, response):
        return GhClient("test-owner/Konachan", downloader=lambda url: response)

    def test_bytes_are_verified_streaming_without_whole_file_read(self):
        response = Response(self.data)
        self.client(response).verify_download(self.artifact, self.url)
        self.assertGreater(len(response.read_sizes), 2)
        self.assertTrue(all(size == 1024 * 1024 for size in response.read_sizes))

    def test_short_and_oversized_downloads_fail(self):
        for data in (self.data[:-1], self.data + b"x"):
            with self.subTest(size=len(data)):
                with self.assertRaises(ReleaseError):
                    self.client(Response(data)).verify_download(self.artifact, self.url)

    def test_same_size_wrong_bytes_fail_sha_check(self):
        response = Response(b"x" + self.data[1:])
        with self.assertRaises(ReleaseError):
            self.client(response).verify_download(self.artifact, self.url)

    def test_partial_http_response_is_not_complete_apk(self):
        response = Response(self.data)
        response.status = 206
        with self.assertRaises(ReleaseError):
            self.client(response).verify_download(self.artifact, self.url)

    def test_exception_does_not_expose_signed_redirect_url(self):
        def failed(url):
            raise OSError("https://release-assets.githubusercontent.com/x?sig=diagnostic-marker")
        client = GhClient("test-owner/Konachan", downloader=failed)
        with self.assertRaises(ReleaseError) as raised:
            client.verify_download(self.artifact, self.url)
        self.assertEqual(str(raised.exception), "公开 APK 下载验证失败；保留原版本文件")
        self.assertNotIn("diagnostic-marker", str(raised.exception))
        self.assertIsNone(raised.exception.__cause__)

    def test_redirects_strip_auth_cookie_and_proxy_headers(self):
        original = Request(self.url, headers={"Authorization": "test-marker",
                           "Cookie": "test-cookie", "Proxy-Authorization": "test-proxy"})
        target = "https://release-assets.githubusercontent.com/x?sig=offline-test"
        redirected = PublicAssetRedirect().redirect_request(original, None, 302, "Found", {}, target)
        headers = {name.lower(): value for name, value in redirected.header_items()}
        self.assertEqual(redirected.get_method(), "GET")
        self.assertEqual(set(headers), {"accept", "user-agent"})
        self.assertNotIn("authorization", headers)

    def test_only_two_https_hosts_and_standard_port_are_allowed(self):
        for url in ("https://github.com/x", "https://release-assets.githubusercontent.com/x?sig=offline-test"):
            require_public_asset_host(url)
        for url in ("http://github.com/x", "https://github.com.evil/x",
                    "https://objects.githubusercontent.com/x", "https://user@github.com/x",
                    "https://github.com:8443/x"):
            with self.subTest(url=url):
                with self.assertRaises(ValueError):
                    require_public_asset_host(url)

    def test_open_request_has_no_auth_and_disables_environment_proxy(self):
        with patch("release_support.build_opener") as mocked:
            open_public_download(self.url)
        handlers = mocked.call_args.args
        self.assertEqual(handlers[0].proxies, {})
        request = mocked.return_value.open.call_args.args[0]
        headers = {name.lower(): value for name, value in request.header_items()}
        self.assertEqual(set(headers), {"accept", "user-agent"})
        self.assertEqual(request.get_method(), "GET")
        self.assertEqual(mocked.return_value.open.call_args.kwargs["timeout"], 30)


if __name__ == "__main__":
    unittest.main()
