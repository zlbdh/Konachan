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

/** Batch download UI and lifecycle for list screens. Canceling stops further queueing; downloads already started continue. */
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
        // Read the default quality setting: -1 asks each time; otherwise use it directly
        int defaultQuality = android.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                .getInt(com.ess.anime.wallpaper.global.Constants.BATCH_DOWNLOAD_QUALITY, -1);
        if (defaultQuality >= 0 && defaultQuality <= 2) {
            requestDownload(snapshot, defaultQuality);
            return;
        }
        String[] qualities = {activity.getString(R.string.batch_download_sample),
                activity.getString(R.string.batch_download_large),
                activity.getString(R.string.batch_download_original)};
        chooser = new AlertDialog.Builder(activity)
                .setTitle("Download All Loaded Images (" + snapshot.size() + " images)")
                .setItems(qualities, (dialog, quality) -> requestDownload(snapshot, quality))
                .setNegativeButton(R.string.dialog_download_cancel, null).create();
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
        progress.setButton(DialogInterface.BUTTON_NEGATIVE, "Stop Queueing", (dialog, which) -> cancelByUser());
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
                String message = "Queued: " + result.queued + " images; already saved or downloading: "
                        + result.skipped + " images; failed: " + result.failed + " images";
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void cancelByUser() {
        cancel();
        if (active()) Toast.makeText(activity, "Preparation stopped. Queued downloads will continue.", Toast.LENGTH_LONG).show();
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
