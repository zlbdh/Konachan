package com.ess.anime.wallpaper.download.image;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.ess.anime.wallpaper.MyApp;
import com.ess.anime.wallpaper.database.GreenDaoUtils;
import com.ess.anime.wallpaper.download.BaseDownloadProgressListener;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.utils.NetworkAvailabilityMonitor;
import com.lzy.okgo.model.Progress;
import com.lzy.okserver.OkDownload;
import com.lzy.okserver.download.DownloadTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import androidx.core.content.ContextCompat;

public class DownloadImageManager {

    private static class DownloadImageHolder {
        private final static DownloadImageManager instance = new DownloadImageManager();
    }

    public static DownloadImageManager getInstance() {
        return DownloadImageHolder.instance;
    }

    private DownloadImageManager() {
        NetworkAvailabilityMonitor.observe(MyApp.getInstance(), this::onConnected);
        initDownloadList();
        continueToDownloadAll();
    }

    private void continueToDownloadAll() {
        mMainHandler.post(() -> {
            for (DownloadBean downloadBean : mDownloadList) {
                String tag = downloadBean.downloadUrl;
                DownloadTask task = OkDownload.getInstance().getTask(tag);
                if (task == null || task.progress.status != Progress.FINISH) {
                    checkToDownload(downloadBean);
                }
            }
        });
    }

    /*******************************************************************/

    private final List<DownloadBean> mDownloadList = new ArrayList<>();

    private void initDownloadList() {
        synchronized (mDownloadList) {
            mDownloadList.clear();
            mDownloadList.addAll(GreenDaoUtils.queryAllDownloadBeans());
        }
    }

    public void addOrUpdate(DownloadBean downloadBean) {
        synchronized (mDownloadList) {
            int index = mDownloadList.indexOf(downloadBean);
            if (index == -1) {
                downloadBean.addedTime = System.currentTimeMillis();
                mDownloadList.add(downloadBean);
                notifyDataAdded(downloadBean);
            } else {
                mDownloadList.set(index, downloadBean);
                notifyDataChanged(downloadBean);
            }
            GreenDaoUtils.updateDownloadBean(downloadBean);
        }
    }

    public void remove(DownloadBean downloadBean) {
        synchronized (mDownloadList) {
            if (mDownloadList.remove(downloadBean)) {
                notifyDataRemoved(downloadBean);
                GreenDaoUtils.deleteDownloadBean(downloadBean);
            }
        }
    }

    public void clearAllFinished() {
        synchronized (mDownloadList) {
            Iterator<DownloadBean> iterator = mDownloadList.iterator();
            while (iterator.hasNext()) {
                DownloadBean downloadBean = iterator.next();
                String tag = downloadBean.downloadUrl;
                DownloadTask task = OkDownload.getInstance().getTask(tag);
                if (task != null && task.progress.status == Progress.FINISH) {
                    iterator.remove();
                    notifyDataRemoved(downloadBean);
                    GreenDaoUtils.deleteDownloadBean(downloadBean);
                    BaseDownloadProgressListener listener = OkHttp.getProgressListener(downloadBean.downloadUrl);
                    if (listener != null) {
                        listener.onRemove();
                    }
                }
            }
        }
    }

    public List<DownloadBean> getDownloadList() {
        synchronized (mDownloadList) {
            return new ArrayList<>(mDownloadList);
        }
    }


    /*******************************************************************/

    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    public void onConnected() {
        synchronized (mDownloadList) {
            mMainHandler.post(() -> {
                // 网络可用时恢复所有断点下载
                for (DownloadBean downloadBean : mDownloadList) {
                    String tag = downloadBean.downloadUrl;
                    DownloadTask task = OkDownload.getInstance().getTask(tag);
                    if (task != null && (task.progress.status == Progress.ERROR || task.progress.status == Progress.PAUSE)) {
                        checkToDownload(downloadBean);
                    }
                }
            });
        }
    }

    public void onDisconnected() {
    }

    /** 将一项任务真正交给下载服务；必须在主线程调用。 */
    public boolean enqueue(DownloadBean downloadBean) {
        if (downloadBean == null || downloadBean.downloadUrl == null
                || downloadBean.savePath == null || DownloadTaskState.isFinishedFile(downloadBean)) {
            return false;
        }
        return checkToDownload(downloadBean);
    }

    private boolean checkToDownload(DownloadBean downloadBean) {
        if (OkHttp.isUrlInDownloadQueue(downloadBean.downloadUrl)) {
            return false;
        }
        Context context = MyApp.getInstance();
        Intent downloadIntent = new Intent(context, DownloadImageService.class);
        downloadIntent.putExtra(Constants.DOWNLOAD_BEAN, downloadBean);
        OkHttp.addUrlToDownloadQueue(downloadBean.downloadUrl);
        try {
            ContextCompat.startForegroundService(context, downloadIntent);
            addOrUpdate(downloadBean);
            return true;
        } catch (RuntimeException exception) {
            OkHttp.removeUrlFromDownloadQueue(downloadBean.downloadUrl);
            android.util.Log.w("DownloadImageManager", "下载服务未能启动", exception);
            return false;
        }
    }

    /*******************************************************************/
    private final List<IDownloadImageListener> mListenerList = new ArrayList<>();

    public void addListener(IDownloadImageListener listener) {
        synchronized (mListenerList) {
            if (!mListenerList.contains(listener)) {
                mListenerList.add(listener);
            }
        }
    }

    public void removeListener(IDownloadImageListener listener) {
        synchronized (mListenerList) {
            mListenerList.remove(listener);
        }
    }

    public void notifyDataAdded(DownloadBean downloadBean) {
        synchronized (mListenerList) {
            for (IDownloadImageListener listener : mListenerList) {
                listener.onDataAdded(downloadBean);
            }
        }
    }

    public void notifyDataRemoved(DownloadBean downloadBean) {
        synchronized (mListenerList) {
            for (IDownloadImageListener listener : mListenerList) {
                listener.onDataRemoved(downloadBean);
            }
        }
    }

    public void notifyDataChanged(DownloadBean downloadBean) {
        synchronized (mListenerList) {
            for (IDownloadImageListener listener : mListenerList) {
                listener.onDataChanged(downloadBean);
            }
        }
    }
}
