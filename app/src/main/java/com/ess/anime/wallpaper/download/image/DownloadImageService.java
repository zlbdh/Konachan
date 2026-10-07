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

    // 任务直到网络和最终保存都完成才结束，不能在线程提交异步下载后就停服务。
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
            // 下载过程中若关闭app会导致intent为null
            // 此时终止下载并清除所有notification
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
            return;
        }

        DownloadBean downloadBean = intent.getParcelableExtra(Constants.DOWNLOAD_BEAN);
        String url = downloadBean.downloadUrl;
        String savePath = downloadBean.savePath;

        // 绑定下载进度监听器
        DownloadImageProgressListener listener;
        if (!OkHttp.isUrlInProgressListener(url)) {
            listener = new DownloadImageProgressListener(this, downloadBean, intent);
            OkHttp.addUrlToProgressListener(url, listener);
        } else {
            listener = (DownloadImageProgressListener) OkHttp.getProgressListener(url);
            listener.prepareNotification();
        }

        // 发布失败后的重试复用完整文件，避免再次下载和误判为已完成。
        File saved = new File(savePath);
        if (saved.isFile()) {
            boolean published = BitmapUtils.insertToMediaStore(this, saved);
            DownloadTaskState.record(downloadBean, published, "相册发布失败，保留已下载文件");
            mMainHandler.post(() -> {
                if (published) listener.onFinish(); else listener.onError();
                DownloadImageManager.getInstance().addOrUpdate(downloadBean);
                finishTask(url);
            });
            return;
        }

        // 临时下载文件
        File tempFolder = new File(Constants.IMAGE_TEMP);
        String tempName = savePath.substring(savePath.lastIndexOf("/") + 1, savePath.lastIndexOf("."));
        File tempFile = new File(tempFolder, tempName);
        if (!tempFolder.exists() && !tempFolder.mkdirs()) {
            DownloadTaskState.record(downloadBean, false, "无法创建临时下载目录");
            mMainHandler.post(() -> {
                listener.onError();
                finishTask(url);
            });
            return;
        }

        // 下载
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
                                        // 下载成功，保存为图片
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
                                            DownloadTaskState.record(downloadBean, false, "下载文件未能完整保存或发布到相册");
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
            android.util.Log.e("DownloadImageService", "下载启动异常类型: "
                    + e.getClass().getName() + " @ " + position);
            DownloadTaskState.record(downloadBean, false, "下载任务启动失败: " + e.getClass().getSimpleName());
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
