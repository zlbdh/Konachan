"""从最终签名 APK 核验并生成公开发布元数据，不接触签名私钥。"""
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
        raise ValueError("APK 必须只有本项目的一个签名证书")
    certificate = certificates[0].replace(":", "").lower()
    expected = expected_certificate.replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected) or certificate != expected:
        raise ValueError("APK 签名证书与固定项目证书不一致")
    aapt = tools / ("aapt.exe" if os.name == "nt" else "aapt")
    badging = subprocess.run([str(aapt), "dump", "badging", str(apk)],
                            capture_output=True, check=True).stdout.decode("utf-8", "replace")
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if package is None or package.group(1) != "com.ess.anime.wallpaper":
        raise ValueError("APK 包名或版本元数据不可识别")
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
