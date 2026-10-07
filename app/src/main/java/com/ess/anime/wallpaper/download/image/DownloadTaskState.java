package com.ess.anime.wallpaper.download.image;

import com.ess.anime.wallpaper.global.Constants;
import com.lzy.okgo.db.DownloadManager;
import com.lzy.okgo.model.Progress;
import com.lzy.okserver.OkDownload;
import com.lzy.okserver.download.DownloadTask;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/** 统一失败终态，避免已有文件把未完成的相册发布当成成功。 */
public final class DownloadTaskState {
    private DownloadTaskState() { }

    public static boolean isFinishedFile(DownloadBean bean) {
        if (bean == null || bean.savePath == null || !new File(bean.savePath).isFile()) return false;
        Progress progress = DownloadManager.getInstance().get(bean.downloadUrl);
        return progress == null || progress.status != Progress.ERROR;
    }

    public static Progress record(DownloadBean bean, boolean success, String reason) {
        DownloadTask task = OkDownload.getInstance().getTask(bean.downloadUrl);
        Progress progress = task == null ? DownloadManager.getInstance().get(bean.downloadUrl) : task.progress;
        if (progress == null) {
            progress = new Progress();
            progress.tag = bean.downloadUrl;
            progress.url = bean.downloadUrl;
            progress.folder = Constants.IMAGE_TEMP;
            progress.fileName = new File(bean.savePath).getName();
            progress.filePath = new File(progress.folder, progress.fileName).getAbsolutePath();
            progress.totalSize = bean.downloadSize;
        }
        progress.status = success ? Progress.FINISH : Progress.ERROR;
        progress.exception = success ? null : new IOException(reason);
        if (success) {
            progress.currentSize = progress.totalSize = new File(bean.savePath).length();
            progress.fraction = 1;
        }
        DownloadManager.getInstance().replace(progress);
        if (task == null) OkDownload.restore(Collections.singletonList(progress));
        return progress;
    }
}
