package com.ess.anime.wallpaper.download.image;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.ess.anime.wallpaper.download.image.notification.MyNotification;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.http.HandlerFuture;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.utils.BitmapUtils;
import com.ess.anime.wallpaper.utils.FileUtils;
import com.lzy.okgo.model.Progress;
import com.lzy.okserver.download.DownloadListener;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class DownloadImageService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Keep the task active until networking and final storage complete; submitting an asynchronous download must not stop the service.
    private final Set<String> mActiveUrls = new HashSet<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private MyNotification mNotify;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotify = new MyNotification();
            mNotify.show(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (String url : new HashSet<>(mActiveUrls)) {
            com.lzy.okserver.download.DownloadTask task = com.lzy.okserver.OkDownload.getInstance().getTask(url);
            if (task != null) task.pause();
            OkHttp.removeUrlFromDownloadQueue(url);
        }
        mActiveUrls.clear();
        if (mNotify != null) {
            mNotify.stop();
            mNotify = null;
        }
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        DownloadBean bean = intent == null ? null : intent.getParcelableExtra(Constants.DOWNLOAD_BEAN);
        if (bean == null || bean.downloadUrl == null || bean.savePath == null) {
            if (mActiveUrls.isEmpty()) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (mActiveUrls.add(bean.downloadUrl)) {
            new Thread(() -> downloadBitmap(intent), "image-download").start();
        }
        return START_NOT_STICKY;
    }

    private void downloadBitmap(Intent intent) {
        if (intent == null) {
            // Closing the app during a download can produce a null intent
            // Stop the download and clear all notifications in that case
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
            return;
        }

        DownloadBean downloadBean = intent.getParcelableExtra(Constants.DOWNLOAD_BEAN);
        String url = downloadBean.downloadUrl;
        String savePath = downloadBean.savePath;

        // Attach the download progress listener
        DownloadImageProgressListener listener;
        if (!OkHttp.isUrlInProgressListener(url)) {
            listener = new DownloadImageProgressListener(this, downloadBean, intent);
            OkHttp.addUrlToProgressListener(url, listener);
        } else {
            listener = (DownloadImageProgressListener) OkHttp.getProgressListener(url);
            listener.prepareNotification();
        }

        // Reuse the complete file after publication failure to avoid downloading it again or falsely reporting completion.
        File saved = new File(savePath);
        if (saved.isFile()) {
            boolean published = BitmapUtils.insertToMediaStore(this, saved);
            DownloadTaskState.record(downloadBean, published, "Gallery publication failed; keeping the downloaded file");
            mMainHandler.post(() -> {
                if (published) listener.onFinish(); else listener.onError();
                DownloadImageManager.getInstance().addOrUpdate(downloadBean);
                finishTask(url);
            });
            return;
        }

        // Temporary download file
        File tempFolder = new File(Constants.IMAGE_TEMP);
        String tempName = savePath.substring(savePath.lastIndexOf("/") + 1, savePath.lastIndexOf("."));
        File tempFile = new File(tempFolder, tempName);
        if (!tempFolder.isDirectory() && !tempFolder.mkdirs() && !tempFolder.isDirectory()) {
            DownloadTaskState.record(downloadBean, false, "Unable to create the temporary download directory");
            mMainHandler.post(() -> {
                listener.onError();
                finishTask(url);
            });
            return;
        }

        // Download
        try {
            mMainHandler.post(() -> DownloadImageManager.getInstance().addOrUpdate(downloadBean));
            OkHttp.startDownloadFile(OkHttp.convertSchemeToHttps(url), tempFolder.getAbsolutePath(), tempName, null,
                    new DownloadListener(url) {
                        @Override
                        public void onStart(Progress progress) {
                            DownloadImageManager.getInstance().addOrUpdate(downloadBean);
                        }

                        @Override
                        public void onProgress(Progress progress) {
                            listener.onProgress((int) (progress.fraction * 100), progress.currentSize, progress.totalSize, progress.speed);
                            DownloadImageManager.getInstance().addOrUpdate(downloadBean);
                        }

                        @Override
                        public void onError(Progress progress) {
                            listener.onError();
                            DownloadImageManager.getInstance().addOrUpdate(downloadBean);
                            finishTask(url);
                        }

                        @Override
                        public void onFinish(File file, Progress progress) {
                            HandlerFuture.ofWork(tempFile)
                                    .applyThen(tempFile -> {
                                        // Download succeeded; save as an image
                                        File saveFile = new File(savePath);
                                        boolean success = FileUtils.moveFile(tempFile, saveFile);
                                        success = success && BitmapUtils.insertToMediaStore(DownloadImageService.this, saveFile);
                                        return success;
                                    }, throwable -> false)
                                    .runOn(HandlerFuture.IO.UI)
                                    .applyThen(saved -> {
                                        if (saved) {
                                            DownloadTaskState.record(downloadBean, true, "");
                                            listener.onFinish();
                                        } else {
                                            DownloadTaskState.record(downloadBean, false, "The downloaded file could not be fully saved or published to the gallery");
                                            listener.onError();
                                        }
                                        DownloadImageManager.getInstance().addOrUpdate(downloadBean);
                                        finishTask(url);
                                    });
                        }

                        @Override
                        public void onRemove(Progress progress) {
                            listener.onRemove();
                            DownloadImageManager.getInstance().remove(downloadBean);
                            finishTask(url);
                        }
                    });
        } catch (Exception e) {
            String position = e.getStackTrace().length == 0 ? "" : e.getStackTrace()[0].toString();
            android.util.Log.e("DownloadImageService", "Download startup exception type: "
                    + e.getClass().getName() + " @ " + position);
            DownloadTaskState.record(downloadBean, false, "Failed to start download task: " + e.getClass().getSimpleName());
            listener.onError();
            mMainHandler.post(() -> DownloadImageManager.getInstance().addOrUpdate(downloadBean));
            finishTask(url);
        }
    }

    private void finishTask(String url) {
        mMainHandler.post(() -> {
            OkHttp.removeUrlFromDownloadQueue(url);
            mActiveUrls.remove(url);
            if (mActiveUrls.isEmpty()) {
                stopSelf();
            }
        });
    }
}
