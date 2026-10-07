package com.ess.anime.wallpaper.model.helper;

import android.content.Context;
import android.os.Looper;

import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PostBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.download.image.DownloadBean;
import com.ess.anime.wallpaper.download.image.DownloadTaskState;
import com.ess.anime.wallpaper.http.OkHttp;
import com.lzy.okgo.db.DownloadManager;
import com.lzy.okgo.model.Progress;
import com.lzy.okserver.OkDownload;
import com.lzy.okserver.download.DownloadTask;
import com.ess.anime.wallpaper.website.E621Config;
import com.ess.anime.wallpaper.website.GelbooruConfig;
import com.ess.anime.wallpaper.website.KonachanSConfig;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BatchDownloadHelperTest {
    private ThumbBean thumb(String id) {
        return new ThumbBean(id, 10, 10, "https://example.com/preview.jpg", "10 x 10",
                "https://example.com/details/" + id);
    }

    @Test public void rawJsonDetailAndApiMediaProduceDownloadablePost() {
        ThumbBean thumb = thumb("7");
        String body = "{\"post\":{\"id\":7,\"file\":{\"url\":\"https://example.com/full.jpg\","
                + "\"width\":100,\"height\":80,\"size\":32},\"sample\":{},\"tags\":{}}}";
        ImageBean image = BatchDownloadHelper.resolveImage(thumb, new E621Config(), body);
        assertEquals("7", image.posts[0].id);
        assertEquals("https://example.com/full.jpg", image.posts[0].fileUrl);
        assertTrue(image.hasPostBean());
    }

    @Test public void gelbooruTempPostFillsUrlsMissingFromHtmlDetail() {
        ThumbBean thumb = thumb("7");
        PostBean temporary = new PostBean();
        temporary.id = "7";
        temporary.fileUrl = "https://example.com/original.jpg";
        temporary.sampleUrl = "https://example.com/sample.jpg";
        temporary.sampleFileSize = -1;
        temporary.jpegUrl = temporary.fileUrl;
        temporary.jpegFileSize = -1;
        thumb.tempPost = temporary;
        ImageBean image = BatchDownloadHelper.resolveImage(thumb, new GelbooruConfig(),
                "<ul><li>Id: 7</li></ul>");
        assertEquals(temporary.fileUrl, image.posts[0].fileUrl);
        assertEquals(temporary.sampleUrl, image.posts[0].sampleUrl);
        assertTrue(image.hasPostBean());
        assertNull(thumb.imageBean);
    }

    @Test public void frozenPrefixDoesNotDependOnCurrentWebsite() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ThumbBean thumb = thumb("7");
        thumb.tempPost = new PostBean();
        thumb.tempPost.id = "7";
        thumb.tempPost.fileUrl = "https://example.com/full.jpg";
        thumb.tempPost.sampleUrl = thumb.tempPost.fileUrl;
        ImageBean image = BatchDownloadHelper.resolveImage(thumb, new KonachanSConfig(), null);
        DownloadBean bean = ImageDataHelper.makeDownloadChosenList(context, thumb, image, "冻结图源-").get(0);
        assertTrue(bean.savePath.endsWith("冻结图源-7-Large.jpg"));
    }

    @Test public void cancellationDuringDetailPreparationStartsNoDownload() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        AtomicReference<BatchDownloadHelper.Result> result = new AtomicReference<>();
        BatchDownloadHelper.Task task = BatchDownloadHelper.start(context,
                Collections.singletonList(thumb("7")), 1, new KonachanSConfig(),
                (thumb, runningTask) -> {
                    loading.countDown();
                    new CountDownLatch(1).await();
                    return "";
                }, bean -> { started.incrementAndGet(); return true; }, callback(result, complete));
        assertTrue(loading.await(5, TimeUnit.SECONDS));
        task.cancel();
        assertTrue(complete.await(5, TimeUnit.SECONDS));
        assertEquals(0, started.get());
        assertTrue(result.get().cancelled);
        assertEquals(0, result.get().queued);
    }

    @Test public void availableListMediaAvoidsExtraDetailRequestsAndStartsOnMainThread() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ThumbBean thumb = thumb("8");
        thumb.tempPost = new PostBean();
        thumb.tempPost.id = "8";
        thumb.tempPost.fileUrl = "https://example.com/batch-test-unique.jpg";
        thumb.tempPost.sampleUrl = thumb.tempPost.fileUrl;
        AtomicInteger loaded = new AtomicInteger();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<BatchDownloadHelper.Result> result = new AtomicReference<>();
        BatchDownloadHelper.start(context, Collections.singletonList(thumb), 2,
                new KonachanSConfig(),
                (item, task) -> { loaded.incrementAndGet(); return ""; }, bean -> {
                    assertSame(Looper.getMainLooper(), Looper.myLooper());
                    assertEquals(1, bean.type);
                    return true;
                }, callback(result, complete));
        assertTrue(complete.await(5, TimeUnit.SECONDS));
        assertEquals(0, loaded.get());
        assertEquals(1, result.get().queued);
    }

    @Test public void missingListMediaFetchesJsonBodyAndQueuesResolvedUrl() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<BatchDownloadHelper.Result> result = new AtomicReference<>();
        AtomicReference<String> queuedUrl = new AtomicReference<>();
        String url = "https://example.com/raw-json-batch.jpg";
        String json = "{\"post\":{\"id\":9,\"file\":{\"url\":\"" + url
                + "\",\"width\":100,\"height\":80,\"size\":32},\"tags\":{}}}";
        BatchDownloadHelper.start(context, Collections.singletonList(thumb("9")), 2,
                new E621Config(), (item, task) -> json,
                bean -> { queuedUrl.set(bean.downloadUrl); return true; }, callback(result, complete));
        assertTrue(complete.await(5, TimeUnit.SECONDS));
        assertEquals(url, queuedUrl.get());
        assertEquals(1, result.get().queued);
        assertEquals(0, result.get().failed);
    }

    @Test public void existingFileAndRunningUrlAreSkippedWithoutBeingCountedAsQueued() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ThumbBean thumb = thumb("fixture-" + System.nanoTime());
        thumb.tempPost = new PostBean();
        thumb.tempPost.id = thumb.id;
        thumb.tempPost.fileUrl = "https://example.com/" + thumb.id + ".jpg";
        thumb.tempPost.sampleUrl = thumb.tempPost.fileUrl;
        ImageBean image = BatchDownloadHelper.resolveImage(thumb, new KonachanSConfig(), null);
        File file = new File(ImageDataHelper.makeDownloadChosenList(context, thumb, image,
                new KonachanSConfig().getSavedImageHead()).get(0).savePath);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        assertTrue(file.createNewFile());
        try {
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(1); }
            assertSkipped(context, thumb);
            assertEquals(1, file.length());
            assertTrue(file.delete());
            OkHttp.addUrlToDownloadQueue(thumb.tempPost.fileUrl);
            assertSkipped(context, thumb);
        } finally {
            if (file.exists()) assertTrue(file.delete());
            OkHttp.removeUrlFromDownloadQueue(thumb.tempPost.fileUrl);
        }
    }

    private void assertSkipped(Context context, ThumbBean thumb) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<BatchDownloadHelper.Result> result = new AtomicReference<>();
        AtomicInteger started = new AtomicInteger();
        BatchDownloadHelper.start(context, Collections.singletonList(thumb), 1, new KonachanSConfig(),
                (item, task) -> { throw new AssertionError("已有媒体无需请求详情"); },
                bean -> { started.incrementAndGet(); return true; }, callback(result, complete));
        assertTrue(complete.await(5, TimeUnit.SECONDS));
        assertEquals(0, started.get());
        assertEquals(0, result.get().queued);
        assertEquals(1, result.get().skipped);
    }

    @Test public void existingFileWithPersistedErrorIsRetriedInsteadOfCountedAsFinished() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ThumbBean thumb = thumb("publish-error-" + java.util.UUID.randomUUID());
        thumb.tempPost = new PostBean();
        thumb.tempPost.id = thumb.id;
        thumb.tempPost.fileUrl = "https://example.com/" + thumb.id + ".jpg";
        thumb.tempPost.sampleUrl = thumb.tempPost.fileUrl;
        ImageBean image = BatchDownloadHelper.resolveImage(thumb, new KonachanSConfig(), null);
        DownloadBean bean = ImageDataHelper.makeDownloadChosenList(context, thumb, image,
                new KonachanSConfig().getSavedImageHead()).get(0);
        File file = new File(bean.savePath);
        assertNull("不能覆盖已有断点记录", DownloadManager.getInstance().get(bean.downloadUrl));
        assertNull("不能清理已有任务", OkDownload.getInstance().getTask(bean.downloadUrl));
        assertFalse(file.exists());
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        assertTrue(file.createNewFile());
        try {
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(1); }
            DownloadTaskState.record(bean, false, "测试相册发布失败");
            assertEquals(Progress.ERROR, DownloadManager.getInstance().get(bean.downloadUrl).status);
            assertFalse("已有文件不能掩盖持久化错误", DownloadTaskState.isFinishedFile(bean));
            AtomicInteger started = new AtomicInteger();
            CountDownLatch complete = new CountDownLatch(1);
            AtomicReference<BatchDownloadHelper.Result> result = new AtomicReference<>();
            BatchDownloadHelper.start(context, Collections.singletonList(thumb), 1, new KonachanSConfig(),
                    (item, task) -> { throw new AssertionError("已有媒体无需请求详情"); },
                    item -> { started.incrementAndGet(); return true; }, callback(result, complete));
            assertTrue(complete.await(5, TimeUnit.SECONDS));
            assertEquals(1, started.get());
            assertEquals(1, result.get().queued);
            assertEquals(0, result.get().skipped);
            assertEquals(0, result.get().failed);
            assertEquals("入队不能伪造最终成功状态", Progress.ERROR,
                    DownloadManager.getInstance().get(bean.downloadUrl).status);
            assertEquals("重试入队不应改写已落盘内容", 1, file.length());
        } finally {
            DownloadTask ownTask = OkDownload.getInstance().getTask(bean.downloadUrl);
            if (ownTask != null) ownTask.remove(false);
            DownloadManager.getInstance().delete(bean.downloadUrl);
            if (file.exists()) assertTrue(file.delete());
        }
    }

    private BatchDownloadHelper.Callback callback(AtomicReference<BatchDownloadHelper.Result> result,
                                                 CountDownLatch completed) {
        return new BatchDownloadHelper.Callback() {
            @Override public void onProgress(int done, int total) { }
            @Override public void onComplete(BatchDownloadHelper.Result value) {
                result.set(value);
                completed.countDown();
            }
        };
    }
}
