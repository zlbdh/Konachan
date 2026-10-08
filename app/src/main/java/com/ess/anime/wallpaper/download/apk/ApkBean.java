package com.ess.anime.wallpaper.download.apk;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.net.URI;
import java.util.Locale;

public class ApkBean implements Parcelable {

    public static final long MAX_APK_SIZE = 512L * 1024 * 1024;
    private static final String RELEASE_PREFIX = "/zlbdh/Konachan/releases/download/";

    public int versionCode;

    public String versionName;

    public String apkName;

    public String apkUrl;

    public long apkSize;

    public String apkSha256;

    public String signingCertificateSha256;

    public String updatedContentEn;

    public String updatedContentZh;

    public String localFileFolder;

    public String localFileName;

    public String localFilePath;

    public ApkBean() { }

    public static ApkBean getApkDetailFromJson(Context context, String json) {
        return parse(context, json);
    }

    /** Strict new schema; invalid data returns null. Replace the cache only after successful parsing. */
    public static ApkBean parse(Context context, String json) {
        if (context == null || json == null || json.length() == 0 || json.length() > 65536) return null;
        try {
            JsonElement document = new JsonParser().parse(json);
            if (!document.isJsonObject()) return null;
            JsonObject root = document.getAsJsonObject();
            long code = requiredInteger(root, "versionCode");
            if (code <= 0 || code > Integer.MAX_VALUE) return null;
            ApkBean bean = new ApkBean();
            bean.versionCode = (int) code;
            bean.versionName = requiredString(root, "versionName");
            bean.apkName = requiredString(root, "apkName");
            bean.apkUrl = requiredString(root, "apkUrl");
            bean.apkSize = requiredInteger(root, "apkSize");
            bean.apkSha256 = requiredString(root, "apkSha256");
            bean.signingCertificateSha256 = requiredString(root, "signingCertificateSha256");
            bean.updatedContentEn = optionalString(root, "updatedContentEn");
            bean.updatedContentZh = optionalString(root, "updatedContentZh");
            if (!bean.isDownloadable()) return null;
            bean.apkSha256 = bean.apkSha256.toLowerCase(Locale.ROOT);
            bean.signingCertificateSha256 = bean.signingCertificateSha256.toLowerCase(Locale.ROOT);
            File folder = context.getExternalFilesDir(null);
            if (folder == null) folder = context.getFilesDir();
            if (folder == null) return null;
            File canonicalFolder = folder.getCanonicalFile();
            File destination = new File(canonicalFolder, bean.apkName).getCanonicalFile();
            if (!canonicalFolder.equals(destination.getParentFile())) return null;
            bean.localFileFolder = canonicalFolder.getAbsolutePath();
            bean.localFileName = bean.apkName;
            bean.localFilePath = destination.getAbsolutePath();
            return bean;
        } catch (Exception invalid) {
            // Do not print remote bodies, URLs, or local paths; invalid metadata must never enter the download or installation flow.
            return null;
        }
    }

    /** Revalidate Parcelable and directly constructed objects; do not rely solely on initial parsing. */
    public boolean isDownloadable() {
        if (versionCode <= 0 || versionName == null || versionName.isEmpty() || versionName.length() > 64
                || !versionName.equals(versionName.trim()) || !versionName.matches("[^\\p{Cntrl}]+")) return false;
        if (apkName == null || !apkName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.apk")
                || apkSize <= 0 || apkSize > MAX_APK_SIZE || !isSha256(apkSha256)
                || !isSha256(signingCertificateSha256)) return false;
        try {
            URI uri = new URI(apkUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null
                    || uri.getFragment() != null) return false;
            String path = uri.getRawPath();
            if (path == null || !path.equals(uri.getPath()) || !path.startsWith(RELEASE_PREFIX)) return false;
            String[] asset = path.substring(RELEASE_PREFIX.length()).split("/", -1);
            return asset.length == 2 && asset[0].matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                    && apkName.equals(asset[1]);
        } catch (Exception invalidUrl) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private static String requiredString(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Incorrect field type");
        }
        return value.getAsString();
    }

    private static long requiredInteger(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Incorrect numeric field type");
        }
        return value.getAsBigDecimal().longValueExact();
    }

    private static String optionalString(JsonObject root, String name) {
        if (!root.has(name) || root.get(name).isJsonNull()) return "";
        String value = requiredString(root, name);
        if (value.length() > 10000) throw new IllegalArgumentException("Release notes are too long");
        return value;
    }

    protected ApkBean(Parcel in) {
        versionCode = in.readInt();
        versionName = in.readString();
        apkName = in.readString();
        apkUrl = in.readString();
        apkSize = in.readLong();
        apkSha256 = in.readString();
        signingCertificateSha256 = in.readString();
        updatedContentEn = in.readString();
        updatedContentZh = in.readString();
        localFileFolder = in.readString();
        localFileName = in.readString();
        localFilePath = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(versionCode);
        dest.writeString(versionName);
        dest.writeString(apkName);
        dest.writeString(apkUrl);
        dest.writeLong(apkSize);
        dest.writeString(apkSha256);
        dest.writeString(signingCertificateSha256);
        dest.writeString(updatedContentEn);
        dest.writeString(updatedContentZh);
        dest.writeString(localFileFolder);
        dest.writeString(localFileName);
        dest.writeString(localFilePath);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ApkBean> CREATOR = new Creator<ApkBean>() {
        @Override
        public ApkBean createFromParcel(Parcel in) {
            return new ApkBean(in);
        }

        @Override
        public ApkBean[] newArray(int size) {
            return new ApkBean[size];
        }
    };

}
