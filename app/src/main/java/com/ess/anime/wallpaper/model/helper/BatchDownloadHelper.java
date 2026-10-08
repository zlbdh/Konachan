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

/** Prepare media metadata sequentially and submit downloads on the main thread; completion means preparation and queueing have ended. */
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
        // Use the same lock as site switching so the parser, headers, and saved-file prefix come from one source.
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
                    // Use direct media links already present in the list without requesting every details page again.
                    if (!hasMedia(image)) image = resolveImage(thumb, website, loader.load(thumb, task));
                    if (task.isCancelled()) break;
                    DownloadBean bean = DownloadQualitySelector.select(
                            ImageDataHelper.makeDownloadChosenList(appContext, thumb, image, savedHead), quality);
                    if (bean == null) throw new IOException("The site did not provide a downloadable media URL");
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
                    // Do not log request URLs, headers, or response bodies to keep credentials out of logs.
                    failed++;
                }
                final int completed = ++done;
                main.post(() -> { if (!task.isCancelled()) callback.onProgress(completed, snapshot.size()); });
            }
            Result result = new Result(queued, skipped, failed, snapshot.size(), task.isCancelled());
            main.post(() -> callback.onComplete(result));
        }, "Preparing batch downloads");
        task.worker.start();
        return task;
    }

    private static String loadBody(ThumbBean thumb, Task task, Map<String, String> headers) throws Exception {
        GetRequest<String> request = OkGo.<String>get(OkHttp.convertSchemeToHttps(thumb.linkToShow))
                .tag(task).headers("User-Agent", OkHttp.USER_AGENT);
        for (Map.Entry<String, String> header : headers.entrySet()) request.headers(header.getKey(), header.getValue());
        try (Response response = request.execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Details request failed");
            // Fetch both JSON and HTML as raw response bodies to avoid Jsoup HTTP client MIME restrictions.
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
        // Use a copy so background tasks do not modify the ThumbBean being preloaded on the UI thread.
        if (thumb.tempPost != null) post.replaceDataIfNotNull(thumb.tempPost);
        if (TextUtils.isEmpty(post.id)) post.id = thumb.id;
        if (!TextUtils.equals(thumb.id, post.id)) throw new IllegalArgumentException("The details do not belong to this image");
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
