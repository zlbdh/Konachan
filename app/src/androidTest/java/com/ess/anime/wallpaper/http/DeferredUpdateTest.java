package com.ess.anime.wallpaper.http;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import com.ess.anime.wallpaper.bean.MsgBean;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.ui.activity.SettingActivity;
import com.ess.anime.wallpaper.utils.SystemUtils;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.greenrobot.eventbus.EventBus;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeferredUpdateTest {
    @Test public void validBackgroundCacheIsOfferedOnceWhenForegroundReturns() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File cache = new File(context.getExternalFilesDir(null), FireBase.UPDATE_FILE_NAME);
        byte[] previous = cache.isFile() ? Files.readAllBytes(cache.toPath()) : null;
        boolean auto = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.AUTO_DOWNLOAD_UPDATE, false);
        Activity activity = null;
        JsonObject json = new JsonObject();
        json.addProperty("versionCode", SystemUtils.getVersionCode(context) + 100);
        json.addProperty("versionName", "9.9.9");
        json.addProperty("apkName", "deferred-test.apk");
        json.addProperty("apkUrl", "https://github.com/zlbdh/Konachan/releases/download/deferred-test/deferred-test.apk");
        json.addProperty("apkSize", 100);
        json.addProperty("apkSha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        json.addProperty("signingCertificateSha256", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(Constants.AUTO_DOWNLOAD_UPDATE, false).commit();
            Files.write(cache.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
            EventBus.getDefault().removeStickyEvent(MsgBean.class);
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context, SettingActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> FireBase.getInstance().resumeCachedUpdate());
            MsgBean offered = EventBus.getDefault().getStickyEvent(MsgBean.class);
            assertNotNull("返回前台应消费有效缓存", offered);
            assertEquals(Constants.CHECK_UPDATE, offered.msg);
            EventBus.getDefault().removeStickyEvent(offered);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> FireBase.getInstance().resumeCachedUpdate());
            assertNull("同一前台会话忽略过的版本不重复提示", EventBus.getDefault().getStickyEvent(MsgBean.class));
        } finally {
            EventBus.getDefault().removeStickyEvent(MsgBean.class);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(Constants.AUTO_DOWNLOAD_UPDATE, auto).commit();
            if (previous != null) Files.write(cache.toPath(), previous); else cache.delete();
            if (activity != null) { Activity finished = activity; InstrumentationRegistry.getInstrumentation().runOnMainSync(finished::finish); }
        }
    }
}
