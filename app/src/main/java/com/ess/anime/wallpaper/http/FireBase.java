package com.ess.anime.wallpaper.http;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.ess.anime.wallpaper.MyApp;
import com.ess.anime.wallpaper.bean.MsgBean;
import com.ess.anime.wallpaper.download.apk.UpdateDownloadManager;
import com.ess.anime.wallpaper.download.apk.ApkBean;
import com.ess.anime.wallpaper.global.AppForegroundState;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.utils.SystemUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

/** Retain the legacy entry-point name; update checks now use GitHub version metadata. */
public class FireBase {
    private static class FirebaseHolder { private static final FireBase instance = new FireBase(); }
    public static FireBase getInstance() { return FirebaseHolder.instance; }
    public static final String UPDATE_FILE_URL = "https://raw.githubusercontent.com/zlbdh/Konachan/master/latest_version.json";
    public static final String UPDATE_FILE_NAME = "latest_version";
    public interface Callback { void onComplete(UpdateCheckController.Result result); }

    private final Context context = MyApp.getInstance().getApplicationContext();
    private final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    private final UpdateCheckController controller = new UpdateCheckController(context,
            new File(context.getExternalFilesDir(null), UPDATE_FILE_NAME), SystemUtils.getVersionCode(context));
    private int offeredVersion;

    private FireBase() { }

    /** Check once at startup. Cache valid background results; only foreground sessions may prompt or download automatically. */
    public void checkUpdate() {
        controller.check(result -> {
            if (result.status != UpdateCheckController.Status.AVAILABLE) return;
            offer(result.apk);
        });
    }

    /** After a background request completes, consume the valid cache once on returning to the foreground without repeating an ignored prompt. */
    public void resumeCachedUpdate() {
        File cache = new File(context.getExternalFilesDir(null), UPDATE_FILE_NAME);
        if (!cache.isFile() || cache.length() > 65536) return;
        ApkBean apk = ApkBean.parse(context, com.ess.anime.wallpaper.utils.FileUtils.fileToString(cache));
        if (apk != null) offer(apk);
    }

    private void offer(ApkBean apk) {
        if (apk.versionCode <= offeredVersion) return;
        UpdateDecision.Action action = UpdateDecision.decide(SystemUtils.getVersionCode(context),
                apk.versionCode, preferences.getBoolean(Constants.AUTO_DOWNLOAD_UPDATE, false),
                AppForegroundState.isVisible());
        if (action == UpdateDecision.Action.DOWNLOAD) {
            if (UpdateDownloadManager.start(context, apk, true)) offeredVersion = apk.versionCode;
        } else if (action == UpdateDecision.Action.PROMPT) {
            offeredVersion = apk.versionCode;
            EventBus.getDefault().postSticky(new MsgBean(Constants.CHECK_UPDATE, apk));
        }
    }

    /** Manual checks use the live network; the calling screen displays update available, up to date, or failure. */
    public void checkUpdate(Callback callback) { controller.check(callback::onComplete); }

    public void checkToAddUser() { }
    public void cancelAll() { controller.cancel(); }
}
