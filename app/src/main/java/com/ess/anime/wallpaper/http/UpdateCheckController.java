package com.ess.anime.wallpaper.http;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import com.ess.anime.wallpaper.download.apk.ApkBean;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Three-state network check result and valid cache; invalid responses must not replace the last valid cache. */
public final class UpdateCheckController {
    public enum Status { AVAILABLE, LATEST, FAILED }
    public static final class Result {
        public final Status status;
        public final ApkBean apk;
        public final String message;
        private Result(Status status, ApkBean apk, String message) {
            this.status = status;
            this.apk = apk;
            this.message = message;
        }
    }
    public interface Callback { void onComplete(Result result); }
    public interface NetworkCallback {
        void onSuccess(String json);
        void onFailure(String message);
    }
    public interface Network {
        void fetch(NetworkCallback callback);
        void cancel();
    }
    private final Context context;
    private final AtomicFile cache;
    private final int installedVersion;
    private final Network network;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long generation;
    private Callback pending;

    public UpdateCheckController(Context context, File cacheFile, int installedVersion) {
        this(context, cacheFile, installedVersion, new UpdateMetadataNetwork(FireBase.UPDATE_FILE_URL));
    }
    public UpdateCheckController(Context context, File cacheFile, int installedVersion, Network network) {
        this.context = context.getApplicationContext();
        this.cache = new AtomicFile(cacheFile);
        this.installedVersion = installedVersion;
        this.network = network;
    }
    public synchronized void check(Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Update checks require a callback");
        cancel();
        final long request;
        synchronized (this) {
            request = ++generation;
            pending = callback;
        }
        try {
            network.fetch(new NetworkCallback() {
                @Override public void onSuccess(String json) { complete(request, json, null); }
                @Override public void onFailure(String message) { complete(request, null, message); }
            });
        } catch (RuntimeException ignored) {
            complete(request, null, "Unable to start the check request");
        }
    }
    public synchronized void cancel() {
        Callback cancelled;
        synchronized (this) {
            generation++;
            cancelled = pending;
            pending = null;
        }
        network.cancel();
        if (cancelled != null) deliver(cancelled, failure("Check canceled"));
    }
    private void complete(long request, String json, String error) {
        Callback callback;
        Result result;
        synchronized (this) {
            if (request != generation || pending == null) return;
            callback = pending;
            pending = null;
            result = error != null ? failure(error) : parseAndCache(json);
        }
        deliver(callback, result);
    }
    private Result parseAndCache(String json) {
        ApkBean apk;
        try {
            apk = json == null || json.length() > 65536 ? null : ApkBean.parse(context, json);
        } catch (RuntimeException ignored) {
            apk = null;
        }
        if (apk == null) return failure("Invalid update information");
        FileOutputStream output = null;
        try {
            output = cache.startWrite();
            output.write(json.getBytes(StandardCharsets.UTF_8));
            cache.finishWrite(output);
        } catch (IOException ignored) {
            if (output != null) cache.failWrite(output);
            return failure("Unable to save update information");
        }
        return new Result(apk.versionCode > installedVersion ? Status.AVAILABLE : Status.LATEST, apk, "");
    }
    private static Result failure(String message) { return new Result(Status.FAILED, null, message); }
    private void deliver(Callback callback, Result result) { main.post(() -> callback.onComplete(result)); }
}
