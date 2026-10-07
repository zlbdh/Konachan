package com.ess.anime.wallpaper.download.apk;

import android.content.Context;

import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ApkVerifierTest {
    private Context context;
    private File file;
    private ApkBean bean;

    @Before public void prepareOwnFixture() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        file = File.createTempFile("apk-verifier-test-", ".apk", context.getCacheDir());
        byte[] bytes = {1, 2, 3, 4};
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        }
        JsonObject root = new JsonObject();
        root.addProperty("versionCode", 35);
        root.addProperty("versionName", "1.9.6");
        root.addProperty("apkName", "kanimeG1.9.6.apk");
        root.addProperty("apkUrl", "https://github.com/zlbdh/Konachan/releases/download/v1.9.6/kanimeG1.9.6.apk");
        root.addProperty("apkSize", file.length());
        root.addProperty("apkSha256", hash.toString());
        root.addProperty("signingCertificateSha256", hash.toString());
        bean = ApkBean.parse(context, root.toString());
        assertNotNull(bean);
    }

    @After public void cleanOnlyOwnFixture() {
        if (file != null && file.exists()) assertTrue(file.delete());
    }

    @Test public void sizeMismatchIsRejectedWithoutDeletingFile() {
        bean.apkSize++;
        ApkVerifier.Result result = ApkVerifier.verify(context, file, bean);
        assertFalse(result.valid);
        assertTrue(result.message.contains("大小"));
        assertTrue(file.isFile());
    }

    @Test public void shaMismatchIsRejectedEvenWhenSizeMatches() {
        bean.apkSha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        ApkVerifier.Result result = ApkVerifier.verify(context, file, bean);
        assertFalse(result.valid);
        assertTrue(result.message.contains("SHA256"));
        assertTrue(file.isFile());
    }

    @Test public void mutatedInvalidSchemaIsRejectedBeforeReadingArchive() {
        bean.versionCode = 0;
        ApkVerifier.Result result = ApkVerifier.verify(context, file, bean);
        assertFalse(result.valid);
        assertTrue(result.message.contains("更新信息"));
        assertTrue(file.isFile());
    }

    @Test public void nonApkCannotPassEvenWithMatchingSizeAndSha() {
        ApkVerifier.Result result = ApkVerifier.verify(context, file, bean);
        assertFalse(result.valid);
        assertTrue(result.message.contains("APK"));
        assertTrue(file.isFile());
    }

    @Test public void missingFileIsRejected() {
        assertTrue(file.delete());
        ApkVerifier.Result result = ApkVerifier.verify(context, file, bean);
        assertFalse(result.valid);
        assertTrue(result.message.contains("APK"));
    }
}
