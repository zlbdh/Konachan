package com.ess.anime.wallpaper.model.helper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PostBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.download.image.DownloadBean;
import com.ess.anime.wallpaper.download.image.DownloadImageManager;
import com.ess.anime.wallpaper.download.image.DownloadTaskState;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.ess.anime.wallpaper.website.WebsiteManager;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.request.GetRequest;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Response;

/** 串行准备媒体信息，并在主线程真正提交下载；完成只表示准备和入队结束。 */
public final class BatchDownloadHelper {
    public interface Callback {
        void onProgress(int done, int total);
        void onComplete(Result result);
    }

    public static final class Result {
        public final int queued, skipped, failed, total;
        public final boolean cancelled;
        Result(int queued, int skipped, int failed, int total, boolean cancelled) {
            this.queued = queued;
            this.skipped = skipped;
            this.failed = failed;
            this.total = total;
            this.cancelled = cancelled;
        }
    }

    public static final class Task {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private Thread worker;
        public void cancel() {
            cancelled.set(true);
            OkGo.getInstance().cancelTag(this);
            if (worker != null) worker.interrupt();
        }
        public boolean isCancelled() { return cancelled.get(); }
    }

    interface DetailLoader { String load(ThumbBean thumb, Task task) throws Exception; }
    interface DownloadStarter { boolean enqueue(DownloadBean bean); }
    private enum Outcome { QUEUED, SKIPPED, FAILED }

    public static Task downloadAll(Context context, List<ThumbBean> thumbs, int quality, Callback callback) {
        WebsiteConfig website;
        Map<String, String> headers;
        // 与切站使用同一个锁，保证解析器、请求头和保存前缀来自同一图源。
        synchronized (WebsiteManager.class) {
            WebsiteManager manager = WebsiteManager.getInstance();
            website = manager.getWebsiteConfig();
            headers = new HashMap<>(manager.getRequestHeaders());
        }
        return start(context, thumbs, quality, website,
                (thumb, task) -> loadBody(thumb, task, headers),
                bean -> DownloadImageManager.getInstance().enqueue(bean), callback);
    }

    static Task start(Context context, List<ThumbBean> thumbs, int quality, WebsiteConfig website,
                      DetailLoader loader, DownloadStarter starter, Callback callback) {
        Context appContext = context.getApplicationContext();
        List<ThumbBean> snapshot = new ArrayList<>(thumbs);
        String savedHead = website.getSavedImageHead();
        Handler main = new Handler(Looper.getMainLooper());
        Task task = new Task();
        task.worker = new Thread(() -> {
            int queued = 0, skipped = 0, failed = 0, done = 0;
            for (ThumbBean thumb : snapshot) {
                if (task.isCancelled()) break;
                try {
                    ImageBean image = resolveImage(thumb, website, null);
                    // 列表已携带直链时直接使用，不重复访问所有详情页。
                    if (!hasMedia(image)) image = resolveImage(thumb, website, loader.load(thumb, task));
                    if (task.isCancelled()) break;
                    DownloadBean bean = DownloadQualitySelector.select(
                            ImageDataHelper.makeDownloadChosenList(appContext, thumb, image, savedHead), quality);
                    if (bean == null) throw new IOException("图源未提供可下载的媒体地址");
                    Outcome outcome = enqueueOnMain(bean, task, main, starter);
                    if (task.isCancelled()) break;
                    if (outcome == Outcome.QUEUED) queued++;
                    else if (outcome == Outcome.SKIPPED) skipped++;
                    else failed++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    task.cancel();
                    break;
                } catch (Exception e) {
                    if (task.isCancelled()) break;
                    // 不打印请求 URL、请求头或响应内容，避免凭据进入日志。
                    failed++;
                }
                final int completed = ++done;
                main.post(() -> { if (!task.isCancelled()) callback.onProgress(completed, snapshot.size()); });
            }
            Result result = new Result(queued, skipped, failed, snapshot.size(), task.isCancelled());
            main.post(() -> callback.onComplete(result));
        }, "批量下载准备");
        task.worker.start();
        return task;
    }

    private static String loadBody(ThumbBean thumb, Task task, Map<String, String> headers) throws Exception {
        GetRequest<String> request = OkGo.<String>get(OkHttp.convertSchemeToHttps(thumb.linkToShow))
                .tag(task).headers("User-Agent", OkHttp.USER_AGENT);
        for (Map.Entry<String, String> header : headers.entrySet()) request.headers(header.getKey(), header.getValue());
        try (Response response = request.execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("详情请求失败");
            // JSON 和 HTML 均按原始 body 获取，避开 Jsoup HTTP 客户端的 MIME 限制。
            return response.body().string();
        }
    }

    static ImageBean resolveImage(ThumbBean thumb, WebsiteConfig website, String rawBody) {
        ImageBean source = thumb.imageBean;
        if (rawBody != null) source = ImageBean.getImageDetailFromJson(website.getHtmlParser()
                .getImageDetailJson(Jsoup.parse(rawBody)));
        PostBean post = new PostBean();
        if (source != null && source.posts != null && source.posts.length > 0 && source.posts[0] != null) {
            post.replaceDataIfNotNull(source.posts[0]);
        }
        // 使用副本，避免后台任务修改 UI 线程正在预加载的 ThumbBean。
        if (thumb.tempPost != null) post.replaceDataIfNotNull(thumb.tempPost);
        if (TextUtils.isEmpty(post.id)) post.id = thumb.id;
        if (!TextUtils.equals(thumb.id, post.id)) throw new IllegalArgumentException("详情不属于该图片");
        if (!DownloadQualitySelector.isMediaUrl(post.sampleUrl)) {
            post.sampleUrl = DownloadQualitySelector.isMediaUrl(post.fileUrl) ? post.fileUrl : post.jpegUrl;
        }
        ImageBean merged = new ImageBean();
        merged.posts = new PostBean[]{post};
        return merged;
    }

    private static boolean hasMedia(ImageBean image) {
        if (image == null || image.posts == null || image.posts.length == 0 || image.posts[0] == null) return false;
        PostBean post = image.posts[0];
        return DownloadQualitySelector.isMediaUrl(post.fileUrl)
                || DownloadQualitySelector.isMediaUrl(post.sampleUrl)
                || DownloadQualitySelector.isMediaUrl(post.jpegUrl);
    }

    private static Outcome enqueueOnMain(DownloadBean bean, Task task, Handler main,
                                         DownloadStarter starter) throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Outcome> outcome = new AtomicReference<>(Outcome.FAILED);
        main.post(() -> {
            try {
                if (task.isCancelled()) return;
                if (DownloadTaskState.isFinishedFile(bean) || OkHttp.isUrlInDownloadQueue(bean.downloadUrl)) {
                    outcome.set(Outcome.SKIPPED);
                } else if (starter.enqueue(bean)) outcome.set(Outcome.QUEUED);
            } catch (RuntimeException ignored) {
                outcome.set(Outcome.FAILED);
            } finally {
                completed.countDown();
            }
        });
        completed.await();
        return outcome.get();
    }
}
