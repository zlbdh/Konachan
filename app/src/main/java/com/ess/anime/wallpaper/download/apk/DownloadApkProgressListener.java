package com.ess.anime.wallpaper.download.apk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.download.BaseDownloadProgressListener;
import com.ess.anime.wallpaper.utils.SystemUtils;

import java.io.File;

public class DownloadApkProgressListener extends BaseDownloadProgressListener<ApkBean> {

    private ApkBean mApkBean;

    public DownloadApkProgressListener(Context context, ApkBean apkBean, Intent intent) {
        super(context, apkBean, intent);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            Intent retry = new Intent(context, DownloadApkService.class).putExtras(intent);
            mReloadIntent = PendingIntent.getForegroundService(context, mNotifyId, retry,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
    }

    @Override public void prepareNotification() {
        if (mNotifyBuilder == null) {
            mNotifyManager = (android.app.NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                mNotifyManager.createNotificationChannel(new android.app.NotificationChannel(
                        "application-updates", "应用更新", android.app.NotificationManager.IMPORTANCE_LOW));
                mNotifyBuilder = new android.app.Notification.Builder(mContext, "application-updates");
            } else mNotifyBuilder = new android.app.Notification.Builder(mContext);
            mNotifyId = (int) System.currentTimeMillis();
            mFileAvailable = com.ess.anime.wallpaper.utils.FileUtils.computeFileSize(getTotalFileSize());
            mNotifyBuilder.setContentTitle(getNotifyTitle());
        }
        super.prepareNotification();
    }

    @Override
    protected void setData(ApkBean data) {
        mApkBean = data;
    }

    @Override
    protected PendingIntent prepareContentIntent() {
        return null;
    }

    @Override
    protected long getTotalFileSize() {
        return mApkBean.apkSize;
    }

    @Override
    protected String getNotifyTitle() {
        return mContext.getString(R.string.app_name);
    }

    @Override
    protected Class<?> getClassToReload() {
        return DownloadApkService.class;
    }

    @Override
    protected void createOperatePendingIntent() {
        mOperateIntent = PendingIntent.getActivity(mContext, mNotifyId,
                InstallUpdateActivity.intent(mContext, mApkBean),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onFinish() {
        super.onFinish();
        mNotifyBuilder.setContentText("更新已下载并校验，点击安装");
        mNotifyManager.notify(mNotifyId, mNotifyBuilder.build());
    }

    public void verificationFailed(String message) {
        super.onError();
        mNotifyBuilder.setContentText(message);
        mNotifyManager.notify(mNotifyId, mNotifyBuilder.build());
    }

}
