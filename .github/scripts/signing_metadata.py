"""Verify the final signed APK and generate public release metadata without accessing the signing private key."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


def signing_metadata(apk, sdk, expected_certificate):
    apk = Path(apk).resolve()
    tools = Path(sdk) / "build-tools" / "35.0.0"
    verification = subprocess.run(["java", "-jar", str(tools / "lib" / "apksigner.jar"),
        "verify", "--verbose", "--print-certs", str(apk)], capture_output=True, check=True)
    output = verification.stdout.decode("utf-8", "replace")
    certificates = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", output)
    if len(certificates) != 1:
        raise ValueError("The APK must contain exactly one signing certificate for this project")
    certificate = certificates[0].replace(":", "").lower()
    expected = expected_certificate.replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected) or certificate != expected:
        raise ValueError("The APK signing certificate does not match the pinned project certificate")
    aapt = tools / ("aapt.exe" if os.name == "nt" else "aapt")
    badging = subprocess.run([str(aapt), "dump", "badging", str(apk)],
                            capture_output=True, check=True).stdout.decode("utf-8", "replace")
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if package is None or package.group(1) != "com.ess.anime.wallpaper":
        raise ValueError("The APK package name or version metadata could not be recognized")
    digest = hashlib.sha256()
    with apk.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    result = {"verified": True, "apkSha256": digest.hexdigest(),
              "signingCertificateSha256": certificate, "packageName": package.group(1),
              "versionCode": int(package.group(2)), "versionName": package.group(3)}
    Path(str(apk) + ".signing.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--expected-certificate", required=True)
    args = parser.parse_args()
    print(json.dumps(signing_metadata(args.apk, args.sdk, args.expected_certificate), ensure_ascii=False))
