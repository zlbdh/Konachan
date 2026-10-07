package com.ess.anime.wallpaper.model.helper;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.bean.ThumbBean;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;

/** 列表页批量下载 UI 和生命周期；取消仅停止后续入队，已开始下载的任务继续。 */
public final class BatchDownloadController {
    private final Activity activity;
    private AlertDialog chooser;
    private ProgressDialog progress;
    private BatchDownloadHelper.Task task;
    private boolean disposed, waitingPermission;
    private int generation;

    public BatchDownloadController(Activity activity) { this.activity = activity; }

    public void show(List<ThumbBean> data) {
        if (!active() || data.isEmpty() || task != null || waitingPermission || chooser != null) return;
        List<ThumbBean> snapshot = new ArrayList<>(data);
        String[] qualities = {activity.getString(R.string.batch_download_sample),
                activity.getString(R.string.batch_download_large),
                activity.getString(R.string.batch_download_original)};
        chooser = new AlertDialog.Builder(activity)
                .setTitle("批量下载当前已加载列表（" + snapshot.size() + " 张）")
                .setItems(qualities, (dialog, quality) -> requestDownload(snapshot, quality))
                .setNegativeButton(android.R.string.cancel, null).create();
        chooser.setOnDismissListener(dialog -> chooser = null);
        chooser.show();
    }

    private void requestDownload(List<ThumbBean> data, int quality) {
        waitingPermission = true;
        int run = ++generation;
        PermissionHelper.checkStoragePermissions(activity, new PermissionHelper.RequestListener() {
            @Override public void onGranted() {
                if (!active() || run != generation) return;
                waitingPermission = false;
                start(data, quality, run);
            }
            @Override public void onDenied() {
                if (run == generation) waitingPermission = false;
            }
        });
    }

    private void start(List<ThumbBean> data, int quality, int run) {
        progress = new ProgressDialog(activity);
        progress.setMessage(activity.getString(R.string.batch_download_preparing));
        progress.setCancelable(true);
        progress.setCanceledOnTouchOutside(false);
        progress.setButton(DialogInterface.BUTTON_NEGATIVE, "停止加入队列", (dialog, which) -> cancelByUser());
        progress.setOnCancelListener(dialog -> cancelByUser());
        progress.show();
        task = BatchDownloadHelper.downloadAll(activity, data, quality, new BatchDownloadHelper.Callback() {
            @Override public void onProgress(int done, int total) {
                if (active() && run == generation && progress != null) {
                    progress.setMessage(activity.getString(R.string.batch_download_progress, done, total));
                }
            }
            @Override public void onComplete(BatchDownloadHelper.Result result) {
                if (!active() || run != generation) return;
                task = null;
                dismissProgress();
                String message = "已加入队列 " + result.queued + " 张，已存在或正在下载 "
                        + result.skipped + " 张，失败 " + result.failed + " 张";
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void cancelByUser() {
        cancel();
        if (active()) Toast.makeText(activity, "已停止准备，已加入队列的任务继续下载", Toast.LENGTH_LONG).show();
    }

    public void cancel() {
        generation++;
        waitingPermission = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (chooser != null) {
            chooser.dismiss();
            chooser = null;
        }
        dismissProgress();
    }

    public void dispose() {
        disposed = true;
        cancel();
    }

    private void dismissProgress() {
        if (progress != null) {
            progress.dismiss();
            progress = null;
        }
    }

    private boolean active() { return !disposed && !activity.isFinishing() && !activity.isDestroyed(); }
}
