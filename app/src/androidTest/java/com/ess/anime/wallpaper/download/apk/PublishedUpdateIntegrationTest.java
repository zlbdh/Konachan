package com.ess.anime.wallpaper.download.apk;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Base64;
import com.ess.anime.wallpaper.global.AppForegroundState;
import com.ess.anime.wallpaper.http.FireBase;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.http.UpdateCheckController;
import com.ess.anime.wallpaper.ui.activity.SettingActivity;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Requires a real published JSON fixture; the baseline must use the same project signature and a versionCode one lower. Disabled by default. */
@RunWith(AndroidJUnit4.class)
public class PublishedUpdateIntegrationTest {
    @Test(timeout = 240000) public void publishedUpdateDownloadsInBackgroundAndOnlyNotifies() throws Exception {
        String encoded = InstrumentationRegistry.getArguments().getString("publishedUpdate");
        assumeTrue("No published fixture provided; skipping the real update download", encoded != null);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        String expectedJson = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        ApkBean expected = ApkBean.parse(context, expectedJson);
        assertNotNull(expected);
        Activity foreground = instrumentation.startActivitySync(new Intent(context, SettingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Instrumentation.ActivityMonitor installs = instrumentation.addMonitor(InstallUpdateActivity.class.getName(), null, false);
        try {
            assertTrue(AppForegroundState.isVisible());
            CountDownLatch checked = new CountDownLatch(1);
            AtomicReference<UpdateCheckController.Result> result = new AtomicReference<>();
            FireBase.getInstance().checkUpdate(value -> { result.set(value); checked.countDown(); });
            assertTrue("The live repository update check must finish within the time limit", checked.await(30, TimeUnit.SECONDS));
            assertEquals(UpdateCheckController.Status.AVAILABLE, result.get().status);
            ApkBean candidate = result.get().apk;
            assertEquals(expected.versionCode, candidate.versionCode);
            assertEquals(expected.apkSha256, candidate.apkSha256);
            AtomicBoolean started = new AtomicBoolean();
            AtomicBoolean duplicate = new AtomicBoolean(true);
            instrumentation.runOnMainSync(() -> {
                started.set(UpdateDownloadManager.start(context, candidate, true));
                duplicate.set(UpdateDownloadManager.start(context, candidate, true));
                foreground.moveTaskToBack(true);
            });
            assertTrue("Finding an update in the foreground must start automatic downloading", started.get());
            assertFalse("Repeated requests must not create a second task", duplicate.get());
            long deadline = SystemClock.elapsedRealtime() + 180000;
            String status;
            do {
                status = context.getSharedPreferences("update-download-state", Context.MODE_PRIVATE).getString("status", "");
                assertNotEquals("The live download must not fail", "FAILED", status);
                if ("READY".equals(status) && !OkHttp.isUrlInDownloadQueue(candidate.apkUrl)) break;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < deadline);
            assertEquals("Automatic download and APK verification must complete", "READY", status);
            assertFalse(OkHttp.isUrlInDownloadQueue(candidate.apkUrl));
            File downloaded = new File(candidate.localFilePath);
            assertEquals(candidate.apkSize, downloaded.length());
            ApkVerifier.Result verified = ApkVerifier.verify(context, downloaded, candidate);
            assertTrue(verified.message, verified.valid);
            assertEquals("Automatic mode must not open the installation screen", 0, installs.getHits());
            // Keep the download so the parent agent can perform a real in-place upgrade after the test without killing this test process.
        } finally {
            instrumentation.removeMonitor(installs);
            instrumentation.runOnMainSync(foreground::finish);
        }
    }
}
