package com.ess.anime.wallpaper.model.helper;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;

import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PostBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.download.image.DownloadBean;
import com.ess.anime.wallpaper.download.image.DownloadImageManager;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.ui.activity.SettingActivity;
import com.ess.anime.wallpaper.website.WebsiteManager;
import com.lzy.okgo.model.Progress;
import com.lzy.okserver.OkDownload;
import com.lzy.okserver.download.DownloadTask;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;

/** Exercise a real public GitHub icon download through the batch entry point and download service; this does not test image-site connectivity. */
@RunWith(AndroidJUnit4.class)
public class DownloadPipelineTest {
    private static final String BASE = "https://raw.githubusercontent.com/zlbdh/Konachan/master/app/src/main/res/";
    private static final String[] URLS = {BASE + "mipmap-mdpi/ic_launcher.png", BASE + "mipmap-hdpi/ic_launcher.png"};
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final String token = "pipeline-test-" + UUID.randomUUID();
    private final List<DownloadBean> ownBeans = new ArrayList<>();
    private final List<BatchDownloadHelper.Task> batches = new ArrayList<>();
    private boolean ownsUrls;
    private Activity foreground;

    @Test(timeout = 60000)
    public void realBatchDownloadsPublishReadableMediaAndSkipSecondRun() throws Exception {
        assertTrue("This scoped-storage acceptance test requires Android 10 or later", Build.VERSION.SDK_INT >= 29);
        long deadline = SystemClock.elapsedRealtime() + 55000;
        try {
            // Keep the app in the foreground on Settings without triggering home-screen image-site requests.
            foreground = instrumentation.startActivitySync(new Intent(context, SettingActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            List<ThumbBean> thumbs = prepareFixtures();
            // Recover only the same public-icon task left by a failed test after validating its UUID filename.
            instrumentation.runOnMainSync(() -> {
                for (String url : URLS) {
                    Progress old = com.lzy.okgo.db.DownloadManager.getInstance().get(url);
                    if (old != null && old.filePath != null
                            && new File(old.filePath).getName().matches(".*pipeline-test-[0-9a-f-]+-[01]-Large(?:\\.png)?")) {
                        DownloadTask stale = OkDownload.getInstance().getTask(url);
                        if (stale != null) stale.remove(true);
                        else com.lzy.okgo.db.DownloadManager.getInstance().delete(url);
                        OkHttp.removeUrlFromDownloadQueue(url);
                    }
                }
            });
            for (String url : URLS) {
                assertFalse("Do not take over an existing download URL", OkHttp.isUrlInDownloadQueue(url));
                assertNull("Do not remove existing tasks", OkDownload.getInstance().getTask(url));
                assertNull("Do not remove existing resume records", com.lzy.okgo.db.DownloadManager.getInstance().get(url));
            }
            ownsUrls = true;
            BatchDownloadHelper.Result first = runBatch(thumbs, deadline);
            assertEquals(2, first.total);
            assertEquals(2, first.queued);
            assertEquals(0, first.skipped);
            assertEquals(0, first.failed);
            assertFalse(first.cancelled);
            AtomicBoolean duplicate = new AtomicBoolean(true);
            instrumentation.runOnMainSync(() -> duplicate.set(
                    DownloadImageManager.getInstance().enqueue(ownBeans.get(0))));
            assertFalse("Do not enqueue another copy of a running or completed task", duplicate.get());
            waitForRealFilesAndMedia(deadline);
            assertEquals("There must be exactly two database tasks in this test", 2, countOwnRecords());

            List<byte[]> downloaded = new ArrayList<>();
            for (DownloadBean bean : ownBeans) {
                File file = new File(bean.savePath);
                byte[] bytes = read(new FileInputStream(file));
                assertTrue("The public icon must be a nonempty PNG", bytes.length > 8);
                assertArrayEquals(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}, Arrays.copyOf(bytes, 8));
                assertArrayEquals("Published media must match the downloaded file", bytes,
                        read(context.getContentResolver().openInputStream(findOwnMedia(file.getName()))));
                downloaded.add(bytes);
            }
            File retained = new File(ownBeans.get(0).savePath);
            long modifiedBeforeRecovery = retained.lastModified();
            Uri missingPublished = findOwnMedia(retained.getName());
            context.getContentResolver().delete(missingPublished, null, null);
            com.ess.anime.wallpaper.download.image.DownloadTaskState.record(
                    ownBeans.get(0), false, "Simulate gallery publication failure");
            BatchDownloadHelper.Result recovery = runBatch(thumbs, deadline);
            assertEquals("A failed publication must be retryable", 1, recovery.queued);
            assertEquals(1, recovery.skipped);
            assertEquals(0, recovery.failed);
            waitForRealFilesAndMedia(deadline);
            assertEquals("Recovery must reuse the downloaded file", modifiedBeforeRecovery, retained.lastModified());
            BatchDownloadHelper.Result second = runBatch(thumbs, deadline);
            assertEquals(2, second.total);
            assertEquals(0, second.queued);
            assertEquals(2, second.skipped);
            assertEquals(0, second.failed);
            assertFalse(second.cancelled);
            assertEquals("A second batch must not add database tasks", 2, countOwnRecords());
            for (int i = 0; i < ownBeans.size(); i++) {
                File file = new File(ownBeans.get(i).savePath);
                assertArrayEquals("Skipping a download must not rewrite its file", downloaded.get(i), read(new FileInputStream(file)));
                assertNotNull("There must be exactly one app-owned media item with this name", findOwnMedia(file.getName()));
            }
        } finally {
            cleanOwnFixtures();
        }
    }

    private List<ThumbBean> prepareFixtures() {
        List<ThumbBean> thumbs = new ArrayList<>();
        String head = WebsiteManager.getInstance().getWebsiteConfig().getSavedImageHead();
        for (int i = 0; i < URLS.length; i++) {
            String id = token + "-" + i;
            PostBean post = new PostBean();
            post.id = id;
            post.fileUrl = URLS[i];
            post.sampleUrl = URLS[i];
            post.previewUrl = URLS[i];
            post.jpegWidth = post.jpegHeight = i == 0 ? 48 : 72;
            ImageBean image = new ImageBean();
            image.posts = new PostBean[]{post};
            ThumbBean thumb = new ThumbBean(id, post.jpegWidth, post.jpegHeight, URLS[i], "", URLS[i]);
            thumb.imageBean = image;
            thumb.tempPost = post;
            List<DownloadBean> choices = ImageDataHelper.makeDownloadChosenList(context, thumb, image, head);
            assertEquals(1, choices.size());
            assertTrue(new File(choices.get(0).savePath).getName().contains(token));
            ownBeans.add(choices.get(0));
            thumbs.add(thumb);
        }
        return thumbs;
    }

    private BatchDownloadHelper.Result runBatch(List<ThumbBean> thumbs, long deadline) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<BatchDownloadHelper.Result> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> batches.add(BatchDownloadHelper.downloadAll(context, thumbs, 1,
                new BatchDownloadHelper.Callback() {
                    @Override public void onProgress(int done, int total) { }
                    @Override public void onComplete(BatchDownloadHelper.Result value) {
                        result.set(value);
                        complete.countDown();
                    }
                })));
        assertTrue("Batch preparation must finish within the overall time limit", complete.await(remaining(deadline), TimeUnit.MILLISECONDS));
        assertNotNull(result.get());
        return result.get();
    }

    private void waitForRealFilesAndMedia(long deadline) throws Exception {
        while (remaining(deadline) > 0) {
            boolean allReady = true;
            for (DownloadBean bean : ownBeans) {
                DownloadTask task = OkDownload.getInstance().getTask(bean.downloadUrl);
                if (task != null && !OkHttp.isUrlInDownloadQueue(bean.downloadUrl))
                    assertNotEquals("The live download must not enter an error state: " + task.progress.exception,
                        Progress.ERROR, task.progress.status);
                File file = new File(bean.savePath);
                allReady &= file.isFile() && file.length() > 0 && task != null
                        && task.progress.status == Progress.FINISH
                        && !OkHttp.isUrlInDownloadQueue(bean.downloadUrl)
                        && findOwnMedia(file.getName()) != null;
            }
            if (allReady) return;
            Thread.sleep(100);
        }
        fail("The public GitHub icon did not finish downloading, saving, and gallery publication within 55 seconds");
    }

    private int countOwnRecords() {
        int count = 0;
        for (DownloadBean bean : DownloadImageManager.getInstance().getDownloadList()) {
            if (bean.savePath != null && new File(bean.savePath).getName().contains(token)) count++;
        }
        return count;
    }

    private Uri findOwnMedia(String name) {
        try (Cursor cursor = queryOwnMedia(name)) {
            assertNotNull(cursor);
            if (cursor.getCount() == 0) return null;
            assertEquals("Do not publish duplicate app-owned media with the same name", 1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("Clear pending only after publication finishes", 0, cursor.getInt(1));
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
        }
    }

    private Cursor queryOwnMedia(String name) {
        return context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_PENDING},
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH
                        + "=? AND " + MediaStore.MediaColumns.OWNER_PACKAGE_NAME + "=?",
                new String[]{name, Environment.DIRECTORY_PICTURES + "/Konachan/", context.getPackageName()}, null);
    }

    private void cleanOwnFixtures() {
        for (BatchDownloadHelper.Task batch : batches) batch.cancel();
        instrumentation.runOnMainSync(() -> {
            if (ownsUrls) for (DownloadBean bean : ownBeans) {
                DownloadTask task = OkDownload.getInstance().getTask(bean.downloadUrl);
                if (task != null && task.progress.filePath != null
                        && new File(task.progress.filePath).getName().contains(token)) task.remove(true);
                OkHttp.removeUrlFromDownloadQueue(bean.downloadUrl);
                DownloadImageManager.getInstance().remove(bean);
            }
        });
        for (DownloadBean bean : ownBeans) {
            File file = new File(bean.savePath);
            try (Cursor cursor = queryOwnMedia(file.getName())) {
                if (cursor != null) while (cursor.moveToNext()) {
                    context.getContentResolver().delete(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)), null, null);
                }
            }
            if (file.exists()) assertTrue("Delete only this test UUID file", file.delete());
            File temp = new File(Constants.IMAGE_TEMP, file.getName().substring(0, file.getName().lastIndexOf('.')));
            if (temp.exists()) assertTrue("Delete only this test UUID temporary file", temp.delete());
        }
        if (foreground != null) instrumentation.runOnMainSync(() -> foreground.finish());
    }

    private static long remaining(long deadline) { return Math.max(0, deadline - SystemClock.elapsedRealtime()); }

    private byte[] read(InputStream input) throws Exception {
        assertNotNull("The media stream must be readable", input);
        try (InputStream in = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }
}
