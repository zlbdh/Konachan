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

/** 必须提供真实已发布JSON；基线使用相同项目签名且低一个versionCode。默认不运行。 */
@RunWith(AndroidJUnit4.class)
public class PublishedUpdateIntegrationTest {
    @Test(timeout = 240000) public void publishedUpdateDownloadsInBackgroundAndOnlyNotifies() throws Exception {
        String encoded = InstrumentationRegistry.getArguments().getString("publishedUpdate");
        assumeTrue("未提供发布fixture，不执行真实更新下载", encoded != null);
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
            assertTrue("实际仓库更新检查应有界结束", checked.await(30, TimeUnit.SECONDS));
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
            assertTrue("前台发现新版应启动自动下载", started.get());
            assertFalse("重复请求不创建第二任务", duplicate.get());
            long deadline = SystemClock.elapsedRealtime() + 180000;
            String status;
            do {
                status = context.getSharedPreferences("update-download-state", Context.MODE_PRIVATE).getString("status", "");
                assertNotEquals("实际下载不得失败", "FAILED", status);
                if ("READY".equals(status) && !OkHttp.isUrlInDownloadQueue(candidate.apkUrl)) break;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < deadline);
            assertEquals("自动下载及验包必须完成", "READY", status);
            assertFalse(OkHttp.isUrlInDownloadQueue(candidate.apkUrl));
            File downloaded = new File(candidate.localFilePath);
            assertEquals(candidate.apkSize, downloaded.length());
            ApkVerifier.Result verified = ApkVerifier.verify(context, downloaded, candidate);
            assertTrue(verified.message, verified.valid);
            assertEquals("自动模式不得启动安装页面", 0, installs.getHits());
            // 下载文件故意保留，主代理在测试结束后做真实覆盖升级，避免杀死本测试进程。
        } finally {
            instrumentation.removeMonitor(installs);
            instrumentation.runOnMainSync(foreground::finish);
        }
    }
}
