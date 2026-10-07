package com.ess.anime.wallpaper.http;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** 单次零重试请求，整体 20 秒 watchdog；不改变其他图站的 HTTP 配置。 */
final class UpdateMetadataNetwork implements UpdateCheckController.Network {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS).retryOnConnectionFailure(false).build();
    private final Handler watchdog = new Handler(Looper.getMainLooper());
    private final String url;
    private RequestState active;
    UpdateMetadataNetwork(String url) { this.url = url; }
    @Override public synchronized void fetch(UpdateCheckController.NetworkCallback callback) {
        RequestState state = new RequestState(client.newCall(new Request.Builder().url(url)
                .header("User-Agent", OkHttp.USER_AGENT).header("Cache-Control", "no-cache").build()), callback);
        active = state;
        watchdog.postDelayed(state.timeout, 20000);
        state.call.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException exception) { state.fail("网络请求失败"); }
            @Override public void onResponse(Call call, Response response) {
                try (Response closed = response) {
                    if (!closed.isSuccessful() || closed.body() == null) {
                        state.fail("更新服务器返回失败");
                        return;
                    }
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = closed.body().byteStream()) {
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = input.read(buffer)) != -1) {
                            if (bytes.size() + length > 65536) throw new IOException("更新信息过大");
                            bytes.write(buffer, 0, length);
                        }
                    }
                    if (state.completed.compareAndSet(false, true)) {
                        watchdog.removeCallbacks(state.timeout);
                        callback.onSuccess(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                    }
                } catch (IOException ignored) { state.fail("更新信息未能完整读取"); }
            }
        });
    }
    @Override public synchronized void cancel() {
        if (active != null) {
            active.completed.set(true);
            watchdog.removeCallbacks(active.timeout);
            active.call.cancel();
            active = null;
        }
    }
    private final class RequestState {
        final Call call;
        final UpdateCheckController.NetworkCallback callback;
        final AtomicBoolean completed = new AtomicBoolean();
        final Runnable timeout;
        RequestState(Call call, UpdateCheckController.NetworkCallback callback) {
            this.call = call;
            this.callback = callback;
            this.timeout = () -> { call.cancel(); fail("检查更新超时"); };
        }
        void fail(String message) {
            if (completed.compareAndSet(false, true)) {
                watchdog.removeCallbacks(timeout);
                callback.onFailure(message);
            }
        }
    }
}
