"""最终 APK、Gradle 输出元数据与公有签名校验记录必须一致。"""

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".github" / "scripts"))
from release_support import ReleaseError, load_artifact


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / "kanimeG1.9.6-debug.apk"
        self.apk.write_bytes(b"signed-apk-test-fixture")
        self.sha = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.metadata = {"applicationId": "com.ess.anime.wallpaper", "elements": [{
            "versionCode": 35, "versionName": "1.9.6", "outputFile": self.apk.name}]}
        self.signing = {"verified": True, "apkSha256": self.sha,
                        "signingCertificateSha256": "CC:" * 31 + "CC",
                        "packageName": "com.ess.anime.wallpaper",
                        "versionCode": 35, "versionName": "1.9.6"}
        self.save()

    def save(self):
        (self.apk.parent / "output-metadata.json").write_text(json.dumps(self.metadata), encoding="utf-8")
        Path(str(self.apk) + ".signing.json").write_text(json.dumps(self.signing), encoding="utf-8")

    def load(self):
        return load_artifact(self.apk, "a" * 40, 7)

    def test_validated_metadata_uses_final_apk_size_and_hash(self):
        item = self.load()
        self.assertIsNotNone(item)
        self.assertEqual(item.sha256, self.sha)
        self.assertEqual(item.certificate_sha256, "c" * 64)
        self.assertEqual(item.size, self.apk.stat().st_size)
        self.assertEqual(item.version_code, 35)
        self.assertEqual(item.tag, "v1.9.6-7")

    def test_signing_record_cannot_belong_to_other_apk(self):
        self.signing["apkSha256"] = "0" * 64
        self.save()
        with self.assertRaises(ReleaseError):
            self.load()

    def test_signing_record_must_be_verified(self):
        self.signing["verified"] = False
        self.save()
        with self.assertRaises(ReleaseError):
            self.load()

    def test_final_apk_versions_must_match_gradle_metadata(self):
        for field in ("versionCode", "versionName", "packageName"):
            with self.subTest(field=field):
                previous = self.signing[field]
                self.signing[field] = "different"
                self.save()
                with self.assertRaises(ReleaseError):
                    self.load()
                self.signing[field] = previous
        self.save()

    def test_metadata_cannot_select_other_apk(self):
        self.metadata["elements"][0]["outputFile"] = "other.apk"
        self.save()
        with self.assertRaises(ReleaseError):
            self.load()

    def test_unverified_fingerprint_is_rejected(self):
        self.signing["signingCertificateSha256"] = "not-a-certificate"
        self.save()
        with self.assertRaises(ReleaseError):
            self.load()

    def test_source_sha_and_run_number_are_validated(self):
        for source, run in (("master", 7), ("a" * 40, 0), ("a" * 40, -1)):
            with self.subTest(source=source, run=run):
                with self.assertRaises(ReleaseError):
                    load_artifact(self.apk, source, run)

    def test_null_digest_and_elements_are_clean_validation_failures(self):
        self.signing["apkSha256"] = None
        self.save()
        with self.assertRaises(ReleaseError):
            self.load()
        self.signing["apkSha256"] = self.sha
        self.metadata["elements"] = None
        self.save()
        with self.assertRaises(ReleaseError):
            self.load()


if __name__ == "__main__":
    unittest.main()
