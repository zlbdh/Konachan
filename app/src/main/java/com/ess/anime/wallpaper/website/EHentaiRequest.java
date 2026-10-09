package com.ess.anime.wallpaper.website;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.ess.anime.wallpaper.http.OkHttp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * E-Hentai / ExHentai network layer.
 *
 * Reference: JHenTai (Flutter, Apache 2.0) lib/src/network/eh_request.dart
 * Only the core 12 interfaces are ported; HTML parsing lives in EHentaiParser.
 *
 * Cookies (ipb_member_id / ipb_pass_hash) are persisted in SharedPreferences
 * so login survives process restarts.
 *
 * Pages are loaded by EHentaiWebFetcher (a hidden WebView) so Cloudflare sees a real
 * browser; the OkHttp client below is only the fallback when no WebView is available.
 * The jar stays the source of truth for login cookies and is mirrored into the
 * WebView cookie store.
 */
public class EHentaiRequest {

    /** User-Agent of the OkHttp fallback only; the WebView keeps its own. */
    public static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final String PREF_COOKIES = "eh_cookies";

    private static volatile EHentaiRequest sInstance;

    private static final ExecutorService sAsyncExecutor = Executors.newFixedThreadPool(3);
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    // Pending enqueue() calls; removing one cancels its callback.
    private static final List<AsyncCall> sAsyncCalls = new ArrayList<>();

    private final OkHttpClient mClient;
    private final Context mAppContext;
    // Hosts whose jar cookies were copied into the WebView store in this process.
    private final Set<String> mWebViewCookieHosts = Collections.synchronizedSet(new HashSet<>());

    private EHentaiRequest(Context context) {
        mAppContext = context.getApplicationContext();
        mClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cookieJar(new PersistentCookieJar())
                .build();
    }

    public static EHentaiRequest getInstance(Context context) {
        if (sInstance == null) {
            synchronized (EHentaiRequest.class) {
                if (sInstance == null) {
                    sInstance = new EHentaiRequest(context);
                }
            }
        }
        return sInstance;
    }

    // ------------------------------------------------------------------
    // Cookie persistence
    // ------------------------------------------------------------------

    private class PersistentCookieJar implements CookieJar {
        private final Map<String, List<Cookie>> mMemory = new HashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            if (cookies == null || cookies.isEmpty()) {
                return;
            }
            String host = url.host();
            List<Cookie> existing = mMemory.get(host);
            if (existing == null) {
                existing = new ArrayList<>();
                mMemory.put(host, existing);
            }
            for (Cookie c : cookies) {
                // replace same-name cookie
                for (int i = 0; i < existing.size(); i++) {
                    if (TextUtils.equals(existing.get(i).name(), c.name())) {
                        existing.remove(i);
                        break;
                    }
                }
                existing.add(c);
            }
            persistCookies(host, existing);
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            String host = url.host();
            List<Cookie> cookies = mMemory.get(host);
            if (cookies == null) {
                cookies = loadPersistedCookies(host);
                if (!cookies.isEmpty()) {
                    mMemory.put(host, cookies);
                }
            }
            List<Cookie> valid = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Cookie c : cookies) {
                if (c.expiresAt() >= now) {
                    valid.add(c);
                }
            }
            return valid;
        }
    }

    private void persistCookies(String host, List<Cookie> cookies) {
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mAppContext);
            StringBuilder sb = new StringBuilder();
            for (Cookie c : cookies) {
                if (sb.length() > 0) {
                    sb.append(';');
                }
                sb.append(c.name()).append('=').append(c.value())
                        .append('|').append(c.expiresAt())
                        .append('|').append(c.domain())
                        .append('|').append(c.path());
            }
            sp.edit().putString(PREF_COOKIES + "_" + host, sb.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Cookie> loadPersistedCookies(String host) {
        List<Cookie> result = new ArrayList<>();
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mAppContext);
            String raw = sp.getString(PREF_COOKIES + "_" + host, null);
            if (TextUtils.isEmpty(raw)) {
                return result;
            }
            for (String part : raw.split(";")) {
                try {
                    String[] kv = part.split("=", 2);
                    String[] meta = kv[1].split("\\|", 4);
                    Cookie.Builder b = new Cookie.Builder()
                            .name(kv[0]).value(meta[0])
                            .expiresAt(Long.parseLong(meta[1]));
                    if (meta.length > 2 && !TextUtils.isEmpty(meta[2])) {
                        b.domain(meta[2]);
                    } else {
                        b.domain(host);
                    }
                    b.path(meta.length > 3 ? meta[3] : "/");
                    result.add(b.build());
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /** Inject cookies captured from a WebView login (see EHentaiLoginActivity). */
    public void injectCookies(String host, String cookieHeader) {
        if (TextUtils.isEmpty(cookieHeader)) {
            return;
        }
        try {
            HttpUrl url = HttpUrl.parse("https://" + host + "/");
            List<Cookie> cookies = new ArrayList<>();
            long expires = System.currentTimeMillis() + 365L * 24 * 3600 * 1000;
            for (String pair : cookieHeader.split(";")) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length == 2 && !TextUtils.isEmpty(kv[0].trim())) {
                    cookies.add(new Cookie.Builder()
                            .name(kv[0].trim()).value(kv[1].trim())
                            .domain(host).path("/").expiresAt(expires).build());
                }
            }
            if (url != null && !cookies.isEmpty()) {
                mClient.cookieJar().saveFromResponse(url, cookies);
                // An explicit login / import replaces what the WebView client holds.
                EHentaiWebFetcher.copyCookiesToWebView(host, cookies, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean hasLoginCookies(String host) {
        try {
            HttpUrl url = HttpUrl.parse("https://" + host + "/");
            List<Cookie> cookies = mClient.cookieJar().loadForRequest(url);
            boolean hasId = false, hasHash = false;
            for (Cookie c : cookies) {
                if ("ipb_member_id".equals(c.name())) hasId = true;
                if ("ipb_pass_hash".equals(c.name())) hasHash = true;
            }
            return hasId && hasHash;
        } catch (Exception e) {
            return false;
        }
    }

    public void clearCookies() {
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mAppContext);
            sp.edit().remove(PREF_COOKIES + "_e-hentai.org")
                    .remove(PREF_COOKIES + "_exhentai.org")
                    .remove(PREF_COOKIES + "_forums.e-hentai.org").apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Low-level helpers
    // ------------------------------------------------------------------

    private Map<String, String> defaultHeaders(String referer) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        h.put("Accept-Language", "en-US,en;q=0.9");
        if (!TextUtils.isEmpty(referer)) {
            h.put("Referer", referer);
        }
        return h;
    }

    /**
     * Blocking GET; call it off the main thread. e-hentai.org / exhentai.org pages are
     * loaded by EHentaiWebFetcher; OkHttp is only used when no WebView is available.
     */
    public String get(String url, String referer) throws IOException {
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl != null && EHentaiWebFetcher.isSupportedHost(httpUrl.host())
                && EHentaiWebFetcher.isAvailable()) {
            if (mWebViewCookieHosts.add(httpUrl.host())) {
                // Logins saved before the WebView client existed are only in the jar.
                EHentaiWebFetcher.copyCookiesToWebView(httpUrl.host(),
                        mClient.cookieJar().loadForRequest(httpUrl), false);
            }
            try {
                return EHentaiWebFetcher.getInstance(mAppContext).fetch(url, referer);
            } catch (EHentaiWebFetcher.WebViewUnavailableException e) {
                e.printStackTrace();
            }
        }
        return getWithOkHttp(url, referer);
    }

    private String getWithOkHttp(String url, String referer) throws IOException {
        Request.Builder b = new Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : defaultHeaders(referer).entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        Response resp = mClient.newCall(b.build()).execute();
        try {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new EHentaiWebFetcher.HttpException(resp.code(), body, url);
            }
            return body;
        } finally {
            resp.close();
        }
    }

    // ------------------------------------------------------------------
    // Async entry for OkHttp.connect()
    // ------------------------------------------------------------------

    /** True for e-hentai.org / exhentai.org pages; OkHttp.connect() hands these to {@link #enqueue}. */
    public static boolean isEHentaiUrl(String url) {
        HttpUrl httpUrl = url == null ? null : HttpUrl.parse(url);
        return httpUrl != null && EHentaiWebFetcher.isSupportedHost(httpUrl.host());
    }

    /**
     * Async {@link #get} with OkHttp.connect() semantics: the callback runs on the main
     * thread, onFailure gets the HTTP status (-1 for network errors), and nothing is
     * delivered after {@link #cancel} with the same tag.
     */
    public void enqueue(String url, Object tag, OkHttp.OkHttpCallback callback) {
        AsyncCall call = new AsyncCall(tag);
        synchronized (sAsyncCalls) {
            sAsyncCalls.add(call);
        }
        sAsyncExecutor.execute(() -> {
            String body = null;
            int errorCode = -1;
            String errorBody = "";
            try {
                body = get(url, null);
            } catch (EHentaiWebFetcher.HttpException e) {
                errorCode = e.code;
                errorBody = e.body;
            } catch (Exception e) {
                e.printStackTrace();
            }
            String result = body;
            int code = errorCode;
            String failure = errorBody;
            sMainHandler.post(() -> {
                synchronized (sAsyncCalls) {
                    if (!sAsyncCalls.remove(call)) {
                        return; // cancelled
                    }
                }
                if (result != null) {
                    callback.onSuccessful(result);
                } else {
                    callback.onFailure(code, failure);
                }
            });
        });
    }

    /** Drops pending {@link #enqueue} callbacks for this tag (called from OkHttp.cancel()). */
    public static void cancel(Object tag) {
        synchronized (sAsyncCalls) {
            Iterator<AsyncCall> it = sAsyncCalls.iterator();
            while (it.hasNext()) {
                if (Objects.equals(it.next().tag, tag)) {
                    it.remove();
                }
            }
        }
    }

    private static class AsyncCall {
        final Object tag;

        AsyncCall(Object tag) {
            this.tag = tag;
        }
    }

    public String post(String url, Map<String, String> params, String referer) throws IOException {
        FormBody.Builder fb = new FormBody.Builder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                fb.add(e.getKey(), e.getValue());
            }
        }
        RequestBody body = fb.build();
        Request.Builder b = new Request.Builder().url(url).post(body);
        for (Map.Entry<String, String> e : defaultHeaders(referer).entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        Response resp = mClient.newCall(b.build()).execute();
        try {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " for " + url);
            }
            return resp.body() != null ? resp.body().string() : "";
        } finally {
            resp.close();
        }
    }

    public String postJson(String url, String json, String referer) throws IOException {
        RequestBody body = RequestBody.create(
                okhttp3.MediaType.parse("application/json; charset=utf-8"), json);
        Request.Builder b = new Request.Builder().url(url).post(body);
        for (Map.Entry<String, String> e : defaultHeaders(referer).entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        Response resp = mClient.newCall(b.build()).execute();
        try {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " for " + url);
            }
            return resp.body() != null ? resp.body().string() : "";
        } finally {
            resp.close();
        }
    }
}
