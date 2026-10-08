"""Tests for the gh CLI transport adapter, with local test doubles for every process and HTTP response."""

import json
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".github" / "scripts"))
from release_support import GhClient, ReleaseError, run_gh


class GhClientTest(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.response = subprocess.CompletedProcess([], 0, "", "")
        self.client = GhClient("test-owner/Konachan", self.runner)

    def runner(self, arguments, input_text):
        self.calls.append((arguments, input_text))
        return self.response

    def http(self, status, body=None, failed=False):
        text = f"HTTP/2.0 {status} response\r\nContent-Type: application/json\r\n\r\n"
        if body is not None:
            text += json.dumps(body)
        self.response = subprocess.CompletedProcess([], 1 if failed else 0, text, "Process diagnostics that must not be echoed")

    def test_successful_get_parses_included_headers(self):
        self.http(200, {"id": 10})
        self.assertEqual(self.client.api("GET", "repos/test-owner/Konachan/releases/10"), {"id": 10})
        self.assertIn("--include", self.calls[0][0])
        self.assertIsNone(self.calls[0][1])

    def test_api_payload_is_stdin_json_not_shell_interpolation(self):
        self.http(201, {"id": 10})
        payload = {"message": "Unicode update: café [skip ci]", "literal": "$(must-not-execute)"}
        self.client.api("POST", "repos/test-owner/Konachan/releases", payload)
        arguments, body = self.calls[0]
        self.assertEqual(arguments[-2:], ["--input", "-"])
        self.assertEqual(json.loads(body), payload)
        self.assertNotIn(payload["literal"], arguments)

    def test_only_http_404_can_mean_missing(self):
        self.http(404, {"message": "not found"}, failed=True)
        self.assertIsNone(self.client.api("GET", "missing", missing_ok=True))
        self.http(403, {"message": "forbidden"}, failed=True)
        with self.assertRaises(ReleaseError) as raised:
            self.client.api("GET", "forbidden", missing_ok=True)
        self.assertEqual(raised.exception.status, 403)

    def test_http_conflict_preserves_status_without_raw_diagnostics(self):
        self.http(409, {"message": "conflict"}, failed=True)
        with self.assertRaises(ReleaseError) as raised:
            self.client.api("PUT", "repos/test-owner/Konachan/contents/latest_version.json", {})
        self.assertEqual(raised.exception.status, 409)
        self.assertNotIn("Process diagnostics", str(raised.exception))

    def test_delete_204_needs_no_json_body(self):
        self.http(204)
        self.assertIsNone(self.client.api("DELETE", "asset"))

    def test_invalid_json_does_not_count_as_success(self):
        self.response = subprocess.CompletedProcess([], 0, "HTTP/2.0 200 OK\n\nnot-json", "")
        with self.assertRaises(ReleaseError):
            self.client.api("GET", "release")

    def test_missing_http_status_does_not_count_as_success(self):
        self.response = subprocess.CompletedProcess([], 0, '{"id":10}', "")
        with self.assertRaises(ReleaseError):
            self.client.api("GET", "release")

    def test_upload_never_uses_clobber(self):
        self.response = subprocess.CompletedProcess([], 0, "uploaded", "")
        self.client.upload("v1.9.6-7", Path("/tmp/signed.apk"))
        arguments, body = self.calls[0]
        self.assertEqual(arguments[:3], ["gh", "release", "upload"])
        self.assertNotIn("--clobber", arguments)
        self.assertIsNone(body)

    def test_upload_failure_is_explicit(self):
        self.response = subprocess.CompletedProcess([], 1, "", "Process diagnostics that must not be echoed")
        with self.assertRaises(ReleaseError):
            self.client.upload("v1.9.6-7", Path("/tmp/signed.apk"))

    def test_default_runner_uses_subprocess_without_shell_or_secret_reads(self):
        completed = subprocess.CompletedProcess([], 0, "", "")
        with patch("release_support.subprocess.run", return_value=completed) as mocked:
            self.assertIs(run_gh(["gh", "api", "dummy"], "{}"), completed)
        kwargs = mocked.call_args.kwargs
        self.assertEqual(mocked.call_args.args[0], ["gh", "api", "dummy"])
        self.assertEqual(kwargs["input"], "{}")
        self.assertNotIn("shell", kwargs)
        self.assertNotIn("env", kwargs)

    def test_repository_argument_cannot_inject_cli_options(self):
        for repo in ("bad owner/repo", "owner/repo/extra", None):
            with self.subTest(repo=repo):
                with self.assertRaises(ReleaseError):
                    GhClient(repo, self.runner)


if __name__ == "__main__":
    unittest.main()
