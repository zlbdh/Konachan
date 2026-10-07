package com.ess.anime.wallpaper.download.apk;

import android.content.Context;
import android.content.Intent;
import com.ess.anime.wallpaper.global.AppForegroundState;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.http.OkHttp;
import com.google.gson.Gson;
import androidx.core.content.ContextCompat;

/** 更新下载统一入口，后台请求留到下一次前台，不绕过系统限制。 */
public final class UpdateDownloadManager {
    public static final String AUTOMATIC = "automaticApkDownload";
    private UpdateDownloadManager() { }

    public static synchronized boolean start(Context context, ApkBean candidate, boolean automatic) {
        if (context == null || candidate == null || !AppForegroundState.isVisible()) return false;
        ApkBean apk = ApkBean.parse(context, new Gson().toJson(candidate));
        if (apk == null || OkHttp.isUrlInDownloadQueue(apk.apkUrl)) return false;
        Intent intent = new Intent(context, DownloadApkService.class)
                .putExtra(Constants.APK_BEAN, apk).putExtra(AUTOMATIC, automatic);
        OkHttp.addUrlToDownloadQueue(apk.apkUrl);
        try {
            ContextCompat.startForegroundService(context.getApplicationContext(), intent);
            return true;
        } catch (RuntimeException denied) {
            OkHttp.removeUrlFromDownloadQueue(apk.apkUrl);
            return false;
        }
    }
}
