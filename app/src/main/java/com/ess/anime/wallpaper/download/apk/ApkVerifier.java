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

/** 下载 APK 的唯一信任门禁。只验证，不安装，不删除失败文件。应在 IO 线程调用。 */
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
        if (metadata == null || !metadata.isDownloadable()) return failed("更新信息不完整或不可信");
        if (context == null || file == null || !file.isFile() || !file.canRead() || file.length() == 0) {
            return failed("APK不存在、为空或不可读取");
        }
        if (file.length() != metadata.apkSize) return failed("APK大小与更新信息不一致");
        try {
            if (!fileSha256(file).equalsIgnoreCase(metadata.apkSha256)) return failed("APK的SHA256校验失败");
            PackageManager manager = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo candidate = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
            if (candidate == null) return failed("无法解析APK安装包");
            if (!PACKAGE_NAME.equals(candidate.packageName) || !PACKAGE_NAME.equals(context.getPackageName())) {
                return failed("APK包名与本应用不一致");
            }
            if (versionCode(candidate) != metadata.versionCode
                    || !metadata.versionName.equals(candidate.versionName)) {
                return failed("APK实际版本与更新信息不一致");
            }
            PackageInfo installed = manager.getPackageInfo(PACKAGE_NAME, flags);
            if (versionCode(candidate) <= versionCode(installed)) return failed("APK不是比当前安装版本更新的版本");
            String candidateCertificate = certificateSha256(candidate);
            String installedCertificate = certificateSha256(installed);
            if (candidateCertificate == null || installedCertificate == null
                    || !candidateCertificate.equals(installedCertificate)
                    || !candidateCertificate.equalsIgnoreCase(metadata.signingCertificateSha256)) {
                return failed("APK签名与本机安装证书或更新信息不一致，请勿覆盖安装");
            }
            // 校验过程中若文件大小变化，不能交给安装器。
            if (file.length() != metadata.apkSize) return failed("APK在校验过程中发生变化");
            return new Result(true, "APK完整性、版本和签名已校验");
        } catch (Exception verificationError) {
            return failed("无法完成APK校验，请重新检查更新");
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
        // 本项目使用单一专属证书，普通更新不接受换证或多签名 APK。
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
