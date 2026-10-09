package com.ess.anime.wallpaper.http;


import android.app.Application;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.ess.anime.wallpaper.MyApp;
import com.ess.anime.wallpaper.download.BaseDownloadProgressListener;
import com.ess.anime.wallpaper.website.EHentaiRequest;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.db.DownloadManager;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.request.GetRequest;
import com.lzy.okgo.utils.IOUtils;
import com.lzy.okserver.OkDownload;
import com.lzy.okserver.download.DownloadListener;
import com.lzy.okserver.download.DownloadTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class OkHttp {

    public final static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_2) AppleWebKit / 537.36(KHTML, like Gecko) Chrome  47.0.2526.106 Safari / 537.36";

    // URLs tracked to prevent duplicate requests
    private final static List<String> sUrlInQueueList = new ArrayList<>();

    // URLs tracked to prevent duplicate progress listeners
    private final static HashMap<String, BaseDownloadProgressListener> sUrlInListenerMap = new HashMap<>();

    // Add a URL to duplicate-request tracking
    public static void addUrlToDownloadQueue(String url) {
        synchronized (sUrlInQueueList) {
            sUrlInQueueList.add(url);
        }
    }

    // Remove the URL after a successful request so it can be requested again later
    public static void removeUrlFromDownloadQueue(String url) {
        synchronized (sUrlInQueueList) {
            sUrlInQueueList.remove(url);
        }
    }

    // Check whether this URL has an active request
    public static boolean isUrlInDownloadQueue(String url) {
        synchronized (sUrlInQueueList) {
            return sUrlInQueueList.contains(url);
        }
    }

    // Add a URL to duplicate-progress-listener tracking
    public static void addUrlToProgressListener(String url, BaseDownloadProgressListener listener) {
        synchronized (sUrlInListenerMap) {
            sUrlInListenerMap.put(url, listener);
        }
    }

    // Check whether this URL already has a progress listener
    public static boolean isUrlInProgressListener(String url) {
        synchronized (sUrlInListenerMap) {
            return sUrlInListenerMap.containsKey(url);
        }
    }

    // Get the progress listener for this URL
    public static BaseDownloadProgressListener getProgressListener(String url) {
        synchronized (sUrlInListenerMap) {
            return sUrlInListenerMap.get(url);
        }
    }

    private static RequestQueue sRequestQueue;

    // Initialize global configuration
    public static void initHttpConfig(Application application) {
        // Use OkGo for synchronous requests and file downloads
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(15, TimeUnit.SECONDS);
        builder.writeTimeout(15, TimeUnit.SECONDS);
        builder.connectTimeout(15, TimeUnit.SECONDS);
        OkGo.getInstance().init(application)
                .setOkHttpClient(builder.build())
                .setRetryCount(0);

        // Use Volley for asynchronous requests
        sRequestQueue = Volley.newRequestQueue(application);

        // Restore resumable download records at each initialization
        OkDownload.restore(DownloadManager.getInstance().getAll());
    }

    // Asynchronous network request
    public static void connect(String url, Object tag, OkHttpCallback callback) {
        connect(url, tag, callback, Request.Priority.NORMAL);
    }

    // Asynchronous network request with priority
    public static void connect(String url, Object tag, OkHttpCallback callback, Request.Priority priority) {
        connect(url, tag, null, callback, priority);
    }

    public static void connect(String url, Object tag, Map<String, String> headerMap, OkHttpCallback callback, Request.Priority priority) {
        if (EHentaiRequest.isEHentaiUrl(url)) {
            // E-Hentai / ExHentai are behind Cloudflare and need the EH login cookies:
            // load them through the WebView-backed client instead of Volley
            EHentaiRequest.getInstance(MyApp.getInstance()).enqueue(url, tag, callback);
            return;
        }
        connectWithRetry(url, tag, headerMap, callback, priority, 0);
    }

    // Asynchronous request with retries: up to two retries, one second apart
    private static final int MAX_RETRY = 2;
    private static void connectWithRetry(String url, Object tag, Map<String, String> headerMap,
                                         OkHttpCallback callback, Request.Priority priority, int retryCount) {
        PriorityStringRequest request = new PriorityStringRequest(
                convertSchemeToHttps(url),
                callback::onSuccessful,
                error -> {
                    if (retryCount < MAX_RETRY) {
                        // Retry after one second
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                () -> connectWithRetry(url, tag, headerMap, callback, priority, retryCount + 1), 1000);
                    } else if (error.networkResponse != null) {
                        callback.onFailure(error.networkResponse.statusCode, new String(error.networkResponse.data));
                    } else {
                        callback.onFailure(-1, "");
                    }
                });
        request.setTag(tag);
        request.addHeaders(headerMap);
        request.setPriority(priority);
        sRequestQueue.add(request);
    }

    // Synchronous GET request
    // Use OkGo because Volley cancellation does not work for synchronous requests
    public static okhttp3.Response execute(String url, Object tag) throws Exception {
        return OkGo.<String>get(convertSchemeToHttps(url))
                .tag(tag)
                .execute();
    }

    // Resumable file download
    public static void startDownloadFile(String url, String dirPath, String fileName, Map<String, String> headerMap, DownloadListener listener) {
        String tag = (String) listener.tag;
        DownloadTask task = OkDownload.getInstance().getTask(tag);
        if (task != null) {
            Progress progress = task.progress;
            if (progress == null || !new File(progress.filePath).exists()) {
                cancelDownloadFile(tag);
                task = null;
            }
        }
        if (task == null) {
            cancelDownloadFile(tag);
            GetRequest<File> request = OkGo.get(url);
            if (headerMap != null) {
                for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                    request.headers(entry.getKey(), entry.getValue());
                }
            }
            task = OkDownload.request(tag, request)
                    .folder(dirPath)
                    .fileName(fileName)
                    .save();
        }
        task.register(listener);
        task.start();
    }

    // Cancel the file download
    public static void cancelDownloadFile(String tag) {
        DownloadTask task = OkDownload.getInstance().getTask(tag);
        if (task != null) {
            task.remove(true);
        } else {
            Progress progress = DownloadManager.getInstance().get(tag);
            if (progress != null) {
                IOUtils.delFileOrFolder(progress.filePath);
            }
            DownloadManager.getInstance().delete(tag);
        }
    }

    // Convert HTTP URLs to HTTPS when needed
    public static String convertSchemeToHttps(String url) {
        return url.replace("http://", "https://");
    }

    // Cancel the request
    public static void cancel(Object tag) {
        sRequestQueue.cancelAll(tag);
        OkGo.getInstance().cancelTag(tag);
        EHentaiRequest.cancel(tag);
    }

    /***********************  callback  ***********************/

    public interface OkHttpCallback {
        void onFailure(int errorCode, String errorMessage);

        void onSuccessful(String body);
    }

}
