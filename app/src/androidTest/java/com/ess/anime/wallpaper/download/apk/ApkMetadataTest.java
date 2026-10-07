package com.ess.anime.wallpaper.download.apk;

import android.content.Context;
import android.os.Parcel;

import com.google.gson.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ApkMetadataTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String URL = "https://github.com/zlbdh/Konachan/releases/download/v1.9.6/kanimeG1.9.6.apk";

    private JsonObject metadata() {
        JsonObject root = new JsonObject();
        root.addProperty("versionCode", 35);
        root.addProperty("versionName", "1.9.6");
        root.addProperty("apkName", "kanimeG1.9.6.apk");
        root.addProperty("apkUrl", URL);
        root.addProperty("apkSize", 72L * 1024 * 1024);
        root.addProperty("apkSha256", HASH);
        root.addProperty("signingCertificateSha256", HASH);
        return root;
    }

    @Test public void validPinnedMetadataAndParcelRetainVerificationFields() {
        ApkBean bean = ApkBean.parse(context, metadata().toString());
        assertNotNull(bean);
        assertTrue(bean.isDownloadable());
        assertTrue(bean.localFilePath.endsWith("/kanimeG1.9.6.apk"));
        Parcel parcel = Parcel.obtain();
        try {
            bean.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ApkBean restored = ApkBean.CREATOR.createFromParcel(parcel);
            assertEquals(HASH, restored.apkSha256);
            assertEquals(HASH, restored.signingCertificateSha256);
            assertTrue(restored.isDownloadable());
        } finally { parcel.recycle(); }
    }

    @Test public void malformedNullAndNonObjectJsonReturnNull() {
        for (String json : new String[]{null, "", "{", "null", "[]", "35", "\"text\""}) {
            assertNull(ApkBean.parse(context, json));
        }
    }

    @Test public void everyRequiredFieldIsEnforced() {
        for (String field : new String[]{"versionCode", "versionName", "apkName", "apkUrl", "apkSize",
                "apkSha256", "signingCertificateSha256"}) {
            JsonObject root = metadata();
            root.remove(field);
            assertNull("缺字段: " + field, ApkBean.parse(context, root.toString()));
        }
    }

    @Test public void filenamesCannotEscapePrivateDirectory() {
        for (String name : new String[]{"../x.apk", "..\\x.apk", "/tmp/x.apk", ".apk", "x.zip", "x.apk/other"}) {
            JsonObject root = metadata();
            root.addProperty("apkName", name);
            assertNull(ApkBean.parse(context, root.toString()));
        }
    }

    @Test public void onlyOwnHttpsPinnedReleaseWithMatchingNameIsAccepted() {
        for (String url : new String[]{URL.replace("https:", "http:"), URL.replace("github.com", "example.com"),
                URL.replace("/zlbdh/", "/other/"), URL.replace("/Konachan/", "/Other/"),
                "https://github.com/zlbdh/Konachan/releases/latest/download/kanimeG1.9.6.apk",
                URL + "?download=1", URL + "#part", URL.replace("kanimeG1.9.6.apk", "other.apk"),
                URL.replace("/v1.9.6/", "/../"), URL.replace("/v1.9.6/", "/v1%2F9/"),
                URL.replace("github.com", "user@github.com")}) {
            JsonObject root = metadata();
            root.addProperty("apkUrl", url);
            assertNull(ApkBean.parse(context, root.toString()));
        }
    }

    @Test public void numericTypesBoundsAndHashesAreStrict() {
        JsonObject root = metadata();
        root.addProperty("versionCode", "35");
        assertNull(ApkBean.parse(context, root.toString()));
        for (Number code : new Number[]{0, -1, 35.5, 2147483648L}) {
            root = metadata(); root.addProperty("versionCode", code);
            assertNull(ApkBean.parse(context, root.toString()));
        }
        for (Number size : new Number[]{0, -1, 1.5, 512L * 1024 * 1024 + 1}) {
            root = metadata(); root.addProperty("apkSize", size);
            assertNull(ApkBean.parse(context, root.toString()));
        }
        for (String hash : new String[]{"", "abcd", HASH.substring(1), HASH + "0", "g" + HASH.substring(1)}) {
            root = metadata(); root.addProperty("apkSha256", hash);
            assertNull(ApkBean.parse(context, root.toString()));
            root = metadata(); root.addProperty("signingCertificateSha256", hash);
            assertNull(ApkBean.parse(context, root.toString()));
        }
    }

    @Test public void blankControlCharacterAndNonStringVersionNamesAreRejected() {
        for (String name : new String[]{"", " ", "1.9.6 ", "v\n1\n2", "v\u00001"}) {
            JsonObject root = metadata();
            root.addProperty("versionName", name);
            assertNull(ApkBean.parse(context, root.toString()));
        }
        JsonObject root = metadata();
        root.addProperty("versionName", 196);
        assertNull(ApkBean.parse(context, root.toString()));
        root = metadata();
        root.add("versionName", new JsonObject());
        assertNull(ApkBean.parse(context, root.toString()));
    }
}
