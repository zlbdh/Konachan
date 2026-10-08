package com.ess.anime.wallpaper.download.apk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** The sole trust gate for downloaded APKs. Verifies only; does not install or delete failed files. Call on an IO thread. */
public final class ApkVerifier {
    private static final String PACKAGE_NAME = "com.ess.anime.wallpaper";
    private ApkVerifier() { }

    public static final class Result {
        public final boolean valid;
        public final String message;
        public Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
    }

    public static Result verify(Context context, File file, ApkBean metadata) {
        if (metadata == null || !metadata.isDownloadable()) return failed("Update information is incomplete or untrusted");
        if (context == null || file == null || !file.isFile() || !file.canRead() || file.length() == 0) {
            return failed("The APK is missing, empty, or unreadable");
        }
        if (file.length() != metadata.apkSize) return failed("The APK size does not match the update information");
        try {
            if (!fileSha256(file).equalsIgnoreCase(metadata.apkSha256)) return failed("APK SHA-256 verification failed");
            PackageManager manager = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo candidate = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
            if (candidate == null) return failed("Unable to parse the APK package");
            if (!PACKAGE_NAME.equals(candidate.packageName) || !PACKAGE_NAME.equals(context.getPackageName())) {
                return failed("The APK package name does not match this app");
            }
            if (versionCode(candidate) != metadata.versionCode
                    || !metadata.versionName.equals(candidate.versionName)) {
                return failed("The APK version does not match the update information");
            }
            PackageInfo installed = manager.getPackageInfo(PACKAGE_NAME, flags);
            if (versionCode(candidate) <= versionCode(installed)) return failed("The APK is not newer than the installed version");
            String candidateCertificate = certificateSha256(candidate);
            String installedCertificate = certificateSha256(installed);
            if (candidateCertificate == null || installedCertificate == null
                    || !candidateCertificate.equals(installedCertificate)
                    || !candidateCertificate.equalsIgnoreCase(metadata.signingCertificateSha256)) {
                return failed("The APK signature does not match the installed certificate or update information. Do not install it over the current app.");
            }
            // Do not pass the APK to the installer if its size changes during verification.
            if (file.length() != metadata.apkSize) return failed("The APK changed during verification");
            return new Result(true, "APK integrity, version, and signature verified");
        } catch (Exception verificationError) {
            return failed("Unable to verify the APK. Check for updates again.");
        }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
    }

    private static String certificateSha256(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        // This project uses one dedicated certificate; routine updates do not accept certificate changes or multiple signatures.
        return signatures != null && signatures.length == 1
                ? hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())) : null;
    }

    private static String fileSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item & 255));
        return value.toString();
    }

    private static Result failed(String message) { return new Result(false, message); }
}
