"""发布事务、幂等和版本回退保护的纯 Python 测试。"""

import tempfile
import unittest
from pathlib import Path

from release_fake import FakeGitHub, ReleaseError, artifact
from publish_release import publish_release


class PublishReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        apk = Path(self.directory.name) / "kanimeG1.9.6-debug.apk"
        apk.write_bytes(b"signed-apk-test-fixture")
        self.item = artifact(apk)
        self.client = FakeGitHub(self.item)

    def kinds(self):
        return [call[0] for call in self.client.calls]

    def assertNoVersionWrite(self):
        self.assertNotIn("PUT", self.kinds())

    def test_success_publishes_before_last_json_write(self):
        result = publish_release(self.item, self.client)
        self.assertIsInstance(result, dict)
        self.assertEqual(result["status"], "published")
        self.assertFalse(self.client.release["draft"])
        self.assertTrue(self.client.release["prerelease"])
        self.assertEqual(self.client.calls[-1][0], "PUT")
        latest = self.client.latest
        self.assertEqual(latest["versionCode"], 35)
        self.assertEqual(latest["versionName"], "1.9.6")
        self.assertEqual(latest["apkSize"], self.item.size)
        self.assertEqual(latest["apkSha256"], self.item.sha256)
        self.assertEqual(latest["signingCertificateSha256"], self.item.certificate_sha256)
        self.assertEqual(latest["sourceSha"], self.item.source_sha)
        self.assertEqual(latest["releaseTag"], "v1.9.6-7")
        self.assertIn("/releases/download/v1.9.6-7/", latest["apkUrl"])
        self.assertEqual(latest["updatedContentZh"], "12 个站点")
        self.assertEqual(latest["updatedContentEn"], "12 sites")
        request = self.client.calls[-1][2]
        self.assertEqual(request["branch"], "master")
        self.assertEqual(request["sha"], "blob-1")
        self.assertIn("[skip ci]", request["message"])

    def test_published_rerun_never_uploads_or_changes_release(self):
        publish_release(self.item, self.client)
        self.client.calls.clear()
        result = publish_release(self.item, self.client)
        self.assertEqual(result["status"], "unchanged")
        self.assertNotIn("UPLOAD", self.kinds())
        self.assertNotIn("PATCH", self.kinds())
        self.assertNoVersionWrite()

    def test_existing_published_release_can_repair_only_json(self):
        self.client.set_existing_release()
        publish_release(self.item, self.client)
        self.assertNotIn("UPLOAD", self.kinds())
        self.assertNotIn("PATCH", self.kinds())
        self.assertEqual(self.client.latest["versionCode"], 35)

    def test_tag_for_another_source_is_conflict(self):
        self.client.ref = {"object": {"type": "commit", "sha": "b" * 40}}
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertNoVersionWrite()
        self.assertNotIn("POST", self.kinds())

    def test_annotated_tag_peels_to_matching_source(self):
        self.client.ref = {"object": {"type": "tag", "sha": "e" * 40}}
        result = publish_release(self.item, self.client)
        self.assertIsInstance(result, dict)
        self.assertEqual(self.client.latest["versionCode"], 35)

    def test_public_asset_with_different_hash_is_not_clobbered(self):
        self.client.bad_digest = True
        self.client.set_existing_release()
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertNotIn("UPLOAD", self.kinds())
        self.assertNotIn("DELETE", self.kinds())
        self.assertNoVersionWrite()

    def test_upload_failure_does_not_publish_or_write_json(self):
        self.client.fail_upload = True
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertNotIn("PATCH", self.kinds())
        self.assertNoVersionWrite()

    def test_server_digest_mismatch_keeps_draft_and_old_json(self):
        self.client.bad_digest = True
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertTrue(self.client.release["draft"])
        self.assertNoVersionWrite()

    def test_publish_failure_does_not_write_json(self):
        self.client.fail_publish = True
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertNoVersionWrite()

    def test_draft_starter_asset_can_be_retried(self):
        self.client.set_existing_release(draft=True)
        self.client.release["assets"][0]["state"] = "starter"
        publish_release(self.item, self.client)
        self.assertIn("DELETE", self.kinds())
        self.assertIn("UPLOAD", self.kinds())

    def test_draft_completed_asset_is_reused_without_upload(self):
        self.client.set_existing_release(draft=True)
        publish_release(self.item, self.client)
        self.assertNotIn("UPLOAD", self.kinds())
        self.assertIn("PATCH", self.kinds())

    def test_newer_remote_version_before_run_has_no_mutation(self):
        self.client.latest["versionCode"] = 36
        result = publish_release(self.item, self.client)
        self.assertIsInstance(result, dict)
        self.assertEqual(result["status"], "superseded")
        self.assertTrue(all(kind == "GET" for kind in self.kinds()))

    def test_newer_remote_version_after_publish_is_not_replaced(self):
        self.client.advance_after_publish = True
        result = publish_release(self.item, self.client)
        self.assertIsInstance(result, dict)
        self.assertEqual(result["status"], "superseded")
        self.assertEqual(self.client.latest["versionCode"], 36)
        self.assertNoVersionWrite()

    def test_equal_version_without_provenance_is_rejected(self):
        self.client.latest["versionCode"] = 35
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertTrue(all(kind == "GET" for kind in self.kinds()))

    def test_equal_version_with_different_source_is_rejected(self):
        self.client.latest.update(versionCode=35, sourceSha="b" * 40,
                                  apkSha256=self.item.sha256,
                                  signingCertificateSha256=self.item.certificate_sha256)
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertNoVersionWrite()

    def test_contents_conflict_is_explicit_and_never_forced(self):
        self.client.fail_contents = True
        with self.assertRaises(ReleaseError) as raised:
            publish_release(self.item, self.client)
        self.assertEqual(raised.exception.status, 409)
        self.assertEqual(self.kinds().count("PUT"), 1)
        self.assertEqual(self.client.latest["versionCode"], 34)

    def test_anonymous_download_failure_does_not_write_json(self):
        self.client.fail_download = True
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertNoVersionWrite()
        self.assertEqual(self.client.latest["versionCode"], 34)

    def test_download_size_mismatch_does_not_write_json(self):
        self.client.download_size_mismatch = True
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertNoVersionWrite()

    def test_download_hash_mismatch_does_not_write_json(self):
        self.client.download_hash_mismatch = True
        with self.assertRaises(ReleaseError):
            publish_release(self.item, self.client)
        self.assertNoVersionWrite()

    def test_download_verification_is_after_publish_before_json(self):
        publish_release(self.item, self.client)
        self.assertIn("DOWNLOAD", self.kinds())
        self.assertLess(self.kinds().index("PATCH"), self.kinds().index("DOWNLOAD"))
        self.assertLess(self.kinds().index("DOWNLOAD"), self.kinds().index("PUT"))

    def test_temporary_query_or_fragment_cannot_be_version_url(self):
        for suffix in ("?temporary=offline-test", "#offline-test"):
            with self.subTest(suffix=suffix):
                self.client.set_existing_release()
                self.client.release["assets"][0]["browser_download_url"] += suffix
                with self.assertRaises(ReleaseError):
                    publish_release(self.item, self.client)
                self.assertNoVersionWrite()


if __name__ == "__main__":
    unittest.main()
