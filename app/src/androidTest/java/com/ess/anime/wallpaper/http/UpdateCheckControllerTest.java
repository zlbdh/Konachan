package com.ess.anime.wallpaper.http;

import android.content.Context;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;

/** 注入合成网络响应；从不访问图站、生产更新 URL 或私人 key。 */
@RunWith(AndroidJUnit4.class)
public class UpdateCheckControllerTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final File cache = new File(context.getCacheDir(), "update-check-test-" + UUID.randomUUID());

    @After public void cleanOwnCache() {
        for (String suffix : new String[]{"", ".bak", ".new"}) {
            File file = new File(cache.getPath() + suffix);
            if (file.exists()) assertTrue("只清理本次 UUID 缓存", file.delete());
        }
    }

    @Test public void validNewVersionReturnsAvailableAndReplacesCache() throws Exception {
        FakeNetwork network = new FakeNetwork();
        write("previous-valid-cache");
        String json = validJson(35);
        UpdateCheckController.Result result = check(network, json, false);
        assertEquals(UpdateCheckController.Status.AVAILABLE, result.status);
        assertNotNull(result.apk);
        assertEquals(35, result.apk.versionCode);
        assertEquals(json, read());
        assertEquals("每次检查只发起一次请求", 1, network.requests);
    }

    @Test public void equalAndOlderValidMetadataReturnLatest() throws Exception {
        for (int version : new int[]{34, 33}) {
            UpdateCheckController.Result result = check(new FakeNetwork(), validJson(version), false);
            assertEquals(UpdateCheckController.Status.LATEST, result.status);
        }
    }

    @Test public void malformedOrIncompleteSchemaNeverDestroysLastValidCache() throws Exception {
        String previous = validJson(35);
        String[] invalid = {"not-json", "null", "{}", "{\"versionCode\":99}",
                validJson(35).replace("\"apkSize\":100", "\"apkSize\":0"),
                validJson(35).replace("\"apkSha256\":\"" + repeat('a') + "\",", "")};
        for (String json : invalid) {
            write(previous);
            UpdateCheckController.Result result = check(new FakeNetwork(), json, false);
            assertEquals(UpdateCheckController.Status.FAILED, result.status);
            assertNull(result.apk);
            assertEquals("坏 schema 不能覆盖最后有效缓存", previous, read());
        }
    }

    @Test public void networkFailureIsFailedRatherThanLatestAndHasNoRetry() throws Exception {
        FakeNetwork network = new FakeNetwork();
        String previous = validJson(35);
        write(previous);
        UpdateCheckController.Result result = check(network, null, true);
        assertEquals(UpdateCheckController.Status.FAILED, result.status);
        assertEquals(previous, read());
        assertEquals(1, network.requests);
    }

    @Test public void oversizedMetadataKeepsLastValidCache() throws Exception {
        String previous = validJson(35);
        write(previous);
        char[] oversized = new char[65537];
        java.util.Arrays.fill(oversized, 'x');
        UpdateCheckController.Result result = check(new FakeNetwork(), new String(oversized), false);
        assertEquals(UpdateCheckController.Status.FAILED, result.status);
        assertEquals(previous, read());
    }

    @Test public void duplicateNetworkCallbacksCannotOverwriteCompletedResult() throws Exception {
        FakeNetwork network = new FakeNetwork();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<UpdateCheckController.Result> result = new AtomicReference<>();
        UpdateCheckController controller = new UpdateCheckController(context, cache, 34, network);
        controller.check(value -> { result.set(value); complete.countDown(); });
        network.callback.onSuccess(validJson(35));
        network.callback.onSuccess("{}");
        assertTrue(complete.await(3, TimeUnit.SECONDS));
        assertEquals(UpdateCheckController.Status.AVAILABLE, result.get().status);
        assertEquals(validJson(35), read());
    }

    @Test public void cancelledLateResponseCannotWriteCache() throws Exception {
        FakeNetwork network = new FakeNetwork();
        String previous = validJson(35);
        write(previous);
        UpdateCheckController controller = new UpdateCheckController(context, cache, 34, network);
        controller.check(value -> { });
        controller.cancel();
        network.callback.onSuccess(validJson(36));
        assertEquals(previous, read());
    }

    private UpdateCheckController.Result check(FakeNetwork network, String body, boolean failure) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<UpdateCheckController.Result> result = new AtomicReference<>();
        UpdateCheckController controller = new UpdateCheckController(context, cache, 34, network);
        controller.check(value -> { result.set(value); complete.countDown(); });
        if (failure) network.callback.onFailure("测试网络失败"); else network.callback.onSuccess(body);
        assertTrue("回调必须有界完成", complete.await(3, TimeUnit.SECONDS));
        return result.get();
    }

    private String validJson(int version) {
        return "{\"versionCode\":" + version + ",\"versionName\":\"1.9.6\","
                + "\"apkName\":\"kanimeG1.9.6-debug.apk\","
                + "\"apkUrl\":\"https://github.com/zlbdh/Konachan/releases/download/v1.9.6-3/kanimeG1.9.6-debug.apk\","
                + "\"apkSize\":100,\"apkSha256\":\"" + repeat('a') + "\","
                + "\"signingCertificateSha256\":\"" + repeat('b') + "\","
                + "\"updatedContentZh\":\"测试\",\"updatedContentEn\":\"test\"}";
    }

    private static String repeat(char value) {
        char[] chars = new char[64];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }

    private void write(String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(cache)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String read() throws Exception {
        try (FileInputStream input = new FileInputStream(cache);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int length;
            while ((length = input.read(bytes)) != -1) output.write(bytes, 0, length);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static class FakeNetwork implements UpdateCheckController.Network {
        int requests;
        UpdateCheckController.NetworkCallback callback;
        @Override public void fetch(UpdateCheckController.NetworkCallback value) { requests++; callback = value; }
        @Override public void cancel() { }
    }
}
