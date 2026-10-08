package com.ess.anime.wallpaper.download.apk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.global.AppForegroundState;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.http.OkHttp;
import com.google.gson.Gson;
import com.lzy.okgo.db.DownloadManager;
import com.lzy.okgo.model.Progress;
import com.lzy.okserver.OkDownload;
import com.lzy.okserver.download.DownloadListener;
import com.lzy.okserver.download.DownloadTask;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** 下载及验包全部结束才停止前台服务；自动模式仅通知，安装由用户点击触发。 */
public class DownloadApkService extends Service {
    private static final String CHANNEL = "apk-update-progress-v2";
    private static final int FOREGROUND_ID = 1248;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> active = new HashSet<>();
    private NotificationManager mNotificationManager;
    private Notification.Builder mForegroundBuilder;

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) mNotificationManager.createNotificationChannel(
                new NotificationChannel(CHANNEL, "应用更新", NotificationManager.IMPORTANCE_DEFAULT));
        mForegroundBuilder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        mForegroundBuilder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("正在下载更新")
                .setProgress(100, 0, false)
                .setOngoing(true);
        startForeground(FOREGROUND_ID, mForegroundBuilder.build());
    }

    /** 更新前台通知的下载进度 */
    private void updateForegroundProgress(int progress, long currentSize, long totalSize) {
        if (mForegroundBuilder == null || mNotificationManager == null) return;
        String text = com.ess.anime.wallpaper.utils.FileUtils.computeFileSize(currentSize)
                + " / " + com.ess.anime.wallpaper.utils.FileUtils.computeFileSize(totalSize);
        mForegroundBuilder.setProgress(100, progress, false)
                .setContentText(text);
        mNotificationManager.notify(FOREGROUND_ID, mForegroundBuilder.build());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ApkBean supplied = intent == null ? null : intent.getParcelableExtra(Constants.APK_BEAN);
        ApkBean apk = supplied == null ? null : ApkBean.parse(this, new Gson().toJson(supplied));
        if (apk == null) { if (active.isEmpty()) stopSelf(); return START_NOT_STICKY; }
        if (active.add(apk.apkUrl)) {
            if (!OkHttp.isUrlInDownloadQueue(apk.apkUrl)) OkHttp.addUrlToDownloadQueue(apk.apkUrl);
            boolean automatic = intent.getBooleanExtra(UpdateDownloadManager.AUTOMATIC, false);
            new Thread(() -> download(apk, intent, automatic), "apk-update-download").start();
        }
        return START_NOT_STICKY;
    }
    private void download(ApkBean apk, Intent intent, boolean automatic) {
        DownloadApkProgressListener listener = new DownloadApkProgressListener(getApplicationContext(), apk, intent);
        OkHttp.addUrlToProgressListener(apk.apkUrl, listener);
        File cache = new File(apk.localFilePath);
        if (cache.isFile()) {
            if (cache.length() == apk.apkSize && ApkVerifier.verify(this, cache, apk).valid) {
                ready(apk, listener, automatic); return;
            }
            DownloadTask previous = OkDownload.getInstance().getTask(apk.apkUrl);
            boolean resumable = previous != null && previous.progress.status != Progress.FINISH
                    && cache.length() > 0 && cache.length() < apk.apkSize
                    && previous.progress.currentSize == cache.length();
            if (!resumable) {
            File isolated = new File(cache.getParentFile(), cache.getName() + ".invalid-" + System.currentTimeMillis());
            if (!cache.renameTo(isolated)) { failed(apk, listener, "无法隔离无效更新缓存"); return; }
            OkHttp.cancelDownloadFile(apk.apkUrl);
            }
        }
        try {
            OkHttp.startDownloadFile(apk.apkUrl, apk.localFileFolder, apk.localFileName, null,
                    new DownloadListener(apk.apkUrl) {
                        @Override public void onStart(Progress progress) { state(apk, "DOWNLOADING", ""); }
                        @Override public void onProgress(Progress progress) {
                            int percent = (int) (progress.fraction * 100);
                            listener.onProgress(percent, progress.currentSize, progress.totalSize, progress.speed);
                            updateForegroundProgress(percent, progress.currentSize, progress.totalSize);
                        }
                        @Override public void onError(Progress progress) { failed(apk, listener, "更新下载失败，请重试"); }
                        @Override public void onRemove(Progress progress) { listener.onRemove(); finish(apk.apkUrl); }
                        @Override public void onFinish(File file, Progress progress) {
                            state(apk, "VERIFYING", "");
                            new Thread(() -> {
                                ApkVerifier.Result result = ApkVerifier.verify(DownloadApkService.this, file, apk);
                                if (result.valid) ready(apk, listener, automatic);
                                else failed(apk, listener, result.message);
                            }, "apk-update-verification").start();
                        }
                    });
        } catch (Exception error) { failed(apk, listener, "更新下载未能启动，请重试"); }
    }
    private void ready(ApkBean apk, DownloadApkProgressListener listener, boolean automatic) {
        state(apk, "READY", "更新已下载并校验，点击通知安装");
        if (mForegroundBuilder != null && mNotificationManager != null) {
            mForegroundBuilder.setContentTitle("更新已下载并校验")
                    .setContentText("点击通知安装")
                    .setProgress(0, 0, false)
                    .setOngoing(false);
            mNotificationManager.notify(FOREGROUND_ID, mForegroundBuilder.build());
        }
        main.post(() -> {
            listener.onFinish();
            if (!automatic && AppForegroundState.isVisible()) startActivity(InstallUpdateActivity.intent(this, apk));
            finish(apk.apkUrl);
        });
    }
    private void failed(ApkBean apk, DownloadApkProgressListener listener, String message) {
        DownloadTask task = OkDownload.getInstance().getTask(apk.apkUrl);
        if (task != null) {
            task.progress.status = Progress.ERROR;
            task.progress.exception = new IOException(message);
            DownloadManager.getInstance().update(task.progress);
        }
        state(apk, "FAILED", message);
        main.post(() -> { listener.verificationFailed(message); finish(apk.apkUrl); });
    }
    private void state(ApkBean apk, String status, String message) {
        getSharedPreferences("update-download-state", MODE_PRIVATE).edit()
                .putString("status", status).putString("message", message)
                .putInt("versionCode", apk.versionCode).putString("url", apk.apkUrl).apply();
    }
    private void finish(String url) {
        main.post(() -> {
            DownloadTask task = OkDownload.getInstance().getTask(url);
            if (task != null) task.unRegister(url);
            OkHttp.removeUrlFromDownloadQueue(url);
            active.remove(url);
            if (active.isEmpty()) stopSelf();
        });
    }
    @Override public void onDestroy() {
        for (String url : new HashSet<>(active)) {
            DownloadTask task = OkDownload.getInstance().getTask(url);
            if (task != null) task.pause();
            OkHttp.removeUrlFromDownloadQueue(url);
        }
        active.clear();
        stopForeground(true);
        super.onDestroy();
    }
}
