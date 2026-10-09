package com.ess.anime.wallpaper.website;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.ess.anime.wallpaper.ui.view.LollipopFixedWebView;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

/**
 * Loads E-Hentai / ExHentai pages through a hidden WebView.
 *
 * Both sites sit behind Cloudflare, which scores a client by its TLS ClientHello,
 * HTTP/2 settings and header set, not only by the User-Agent. OkHttp can copy a
 * browser's headers but not the rest, and a Chrome UA on a non-Chrome handshake is
 * itself a bot signal. A WebView is a real Chromium network stack: every layer of the
 * fingerprint agrees, clearance cookies stay in the browser cookie store, and
 * Cloudflare's JS challenges can run.
 *
 * One WebView per host, created lazily on the main thread and destroyed after a few
 * idle minutes. The first request (and any request that hits a challenge) is a
 * top-level navigation; once a same-origin page is loaded, later requests are
 * same-origin fetch() calls from that page, which are cheap and run in parallel.
 */
public class EHentaiWebFetcher {

    /** Non-2xx response; keeps the status and body for OkHttp.OkHttpCallback#onFailure. */
    public static class HttpException extends IOException {
        public final int code;
        public final String body;

        HttpException(int code, String body, String url) {
            super("HTTP " + code + " for " + url);
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    /** No usable WebView on this device (missing or updating WebView package). */
    public static class WebViewUnavailableException extends IOException {
        WebViewUnavailableException(Throwable cause) {
            super("WebView unavailable", cause);
        }
    }

    private static final String HOST_EHENTAI = "e-hentai.org";
    private static final String HOST_EXHENTAI = "exhentai.org";

    private static final long NAVIGATION_TIMEOUT_MS = 30_000;
    private static final long FETCH_TIMEOUT_MS = 30_000;
    // Long enough for a timed-out fetch() followed by a full navigation.
    private static final long WAIT_TIMEOUT_MS = FETCH_TIMEOUT_MS + NAVIGATION_TIMEOUT_MS + 15_000;
    private static final long IDLE_RELEASE_MS = 3 * 60_000;
    private static final int MAX_FETCH_ATTEMPTS = 3;
    // Chrome caps cookie lifetime at 400 days.
    private static final long MAX_COOKIE_AGE_SECONDS = 400L * 24 * 3600;

    private static final String BRIDGE = "EHFetchBridge";
    private static final String JS_FETCH = "(function(id,url,ref){"
            + "var o={credentials:'include'};if(ref){o.referrer=ref;}"
            + "fetch(url,o).then(function(r){return r.text().then(function(t){"
            + BRIDGE + ".onResult(id,r.status,r.headers.get('cf-mitigated')||'',t);});})"
            + ".catch(function(e){" + BRIDGE + ".onError(id,String(e));});"
            + "})(%s,%s,%s);";
    private static final String JS_SNAPSHOT = "(function(){"
            + "var n=performance.getEntriesByType?performance.getEntriesByType('navigation')[0]:null;"
            + "return {s:(n&&n.responseStatus)||0,u:location.href,"
            + "h:document.documentElement?document.documentElement.outerHTML:''};})()";

    private static volatile EHentaiWebFetcher sInstance;
    private static volatile boolean sUnavailable;

    private final Context mAppContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    // Main thread only.
    private final Map<String, Session> mSessions = new HashMap<>();
    // Main thread only: fetch() attempt id -> request.
    private final Map<String, Pending> mInFlight = new HashMap<>();

    private EHentaiWebFetcher(Context context) {
        mAppContext = context.getApplicationContext();
    }

    public static EHentaiWebFetcher getInstance(Context context) {
        if (sInstance == null) {
            synchronized (EHentaiWebFetcher.class) {
                if (sInstance == null) {
                    sInstance = new EHentaiWebFetcher(context);
                }
            }
        }
        return sInstance;
    }

    public static boolean isSupportedHost(String host) {
        return HOST_EHENTAI.equals(host) || HOST_EXHENTAI.equals(host);
    }

    /** False once creating a WebView has failed in this process. */
    public static boolean isAvailable() {
        return !sUnavailable;
    }

    /**
     * Blocking GET of an e-hentai.org / exhentai.org page. Must not be called on the main thread.
     *
     * @throws HttpException               non-2xx response, or a Cloudflare challenge that could not be passed
     * @throws WebViewUnavailableException no WebView could be created
     */
    public String fetch(String url, String referer) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("fetch() blocks; call it off the main thread");
        }
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null || !isSupportedHost(httpUrl.host())) {
            throw new IOException("Not an E-Hentai url: " + url);
        }
        Pending p = new Pending(httpUrl.host(), url, referer);
        mMainHandler.post(() -> start(p));
        try {
            if (!p.done.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                mMainHandler.post(() -> drop(p));
                throw new IOException("Timed out loading " + url);
            }
        } catch (InterruptedException e) {
            mMainHandler.post(() -> drop(p));
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted loading " + url);
        }
        if (p.error != null) {
            throw p.error;
        }
        return p.html;
    }

    /**
     * Copies cookies from EHentaiRequest's jar into the WebView cookie store.
     *
     * @param overwrite true for an explicit login / import; false only fills in names the
     *                  WebView store lacks, so values the site has refreshed there
     *                  (e.g. igneous) are kept
     */
    public static void copyCookiesToWebView(String host, List<Cookie> cookies, boolean overwrite) {
        if (!isSupportedHost(host) || cookies == null || sUnavailable) {
            return;
        }
        List<Cookie> copy = new ArrayList<>(cookies);
        Runnable task = () -> {
            try {
                CookieManager cm = CookieManager.getInstance();
                String url = "https://" + host + "/";
                Set<String> present = cookieNames(cm.getCookie(url));
                long now = System.currentTimeMillis();
                for (Cookie c : copy) {
                    String name = c.name();
                    // Cloudflare cookies belong to the client that earned them.
                    if (isCloudflareCookie(name) || (!overwrite && present.contains(name))) {
                        continue;
                    }
                    long maxAge = Math.min((c.expiresAt() - now) / 1000, MAX_COOKIE_AGE_SECONDS);
                    if (maxAge <= 0) {
                        continue;
                    }
                    if (overwrite) {
                        // Drop a host-only twin so the site never sees two values.
                        cm.setCookie(url, name + "=; Path=/; Max-Age=0");
                    }
                    cm.setCookie(url, name + "=" + c.value() + "; Domain=." + host
                            + "; Path=/; Max-Age=" + maxAge);
                    present.add(name);
                }
                if (!present.contains("nw")) {
                    // Skip the "Content Warning" interstitial on flagged galleries.
                    cm.setCookie(url, "nw=1; Domain=." + host + "; Path=/; Max-Age=" + MAX_COOKIE_AGE_SECONDS);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            new Handler(Looper.getMainLooper()).post(task);
        }
    }

    // ------------------------------------------------------------------
    // Request flow (main thread)
    // ------------------------------------------------------------------

    private void start(Pending p) {
        if (p.dropped) {
            return;
        }
        Session s;
        try {
            s = obtainSession(p.host);
        } catch (Throwable t) {
            // e.g. MissingWebViewPackageException while the WebView package is updating
            sUnavailable = true;
            complete(p, null, new WebViewUnavailableException(t));
            return;
        }
        p.session = s;
        mMainHandler.removeCallbacks(s.release);
        dispatch(p);
    }

    private void dispatch(Pending p) {
        Session s = p.session;
        if (s.navigating != null) {
            s.waiting.add(p);
        } else if (s.ready) {
            startFetch(p);
        } else {
            startNavigation(p);
        }
    }

    private void startFetch(Pending p) {
        p.fetchAttempts++;
        p.attemptId = UUID.randomUUID().toString();
        mInFlight.put(p.attemptId, p);
        String referer = isSameOrigin(p.referer, p.url) ? p.referer : "";
        p.session.webView.evaluateJavascript(String.format(JS_FETCH, JSONObject.quote(p.attemptId),
                JSONObject.quote(p.url), JSONObject.quote(referer)), null);
        schedule(p, FETCH_TIMEOUT_MS);
    }

    private void onFetchResult(String attemptId, int status, String mitigated, String body, String error) {
        Pending p = mInFlight.remove(attemptId);
        if (p == null) {
            return; // timed out, dropped, or superseded by a navigation
        }
        p.attemptId = null;
        boolean challenge = "challenge".equalsIgnoreCase(mitigated) || isChallengePage(body);
        if (error == null && !challenge) {
            if (status >= 400) {
                complete(p, null, new HttpException(status, body, p.url));
            } else {
                complete(p, body == null ? "" : body, null);
            }
            return;
        }
        // fetch() cannot run a challenge (and a failed fetch may be one): retry as a navigation.
        retryWithNavigation(p, challenge
                ? new HttpException(status > 0 ? status : 403, body, p.url)
                : new IOException(error + " loading " + p.url));
    }

    private void retryWithNavigation(Pending p, IOException cause) {
        if (p.navigated || p.fetchAttempts >= MAX_FETCH_ATTEMPTS || p.session.webView == null) {
            complete(p, null, cause);
            return;
        }
        p.session.ready = false;
        dispatch(p);
    }

    private void startNavigation(Pending p) {
        Session s = p.session;
        // fetch() calls still running belong to the page being replaced; re-issue them afterwards.
        Iterator<Pending> it = mInFlight.values().iterator();
        while (it.hasNext()) {
            Pending other = it.next();
            if (other.session == s) {
                it.remove();
                other.attemptId = null;
                cancelTimeout(other);
                s.waiting.add(other);
            }
        }
        p.navigated = true;
        s.navigating = p;
        s.ready = false;
        s.sawChallenge = false;
        Map<String, String> headers = new HashMap<>();
        if (!TextUtils.isEmpty(p.referer)) {
            headers.put("Referer", p.referer);
        }
        s.webView.loadUrl(p.url, headers);
        schedule(p, NAVIGATION_TIMEOUT_MS);
    }

    private void onSnapshot(Session s, WebView view, String value) {
        Pending p = s.navigating;
        if (p == null || s.webView != view) {
            return;
        }
        int status = 0;
        String location = "";
        String html = null;
        try {
            JsonObject o = new JsonParser().parse(value).getAsJsonObject();
            status = o.get("s").getAsInt();
            location = o.get("u").getAsString();
            html = o.get("h").getAsString();
        } catch (Exception e) {
            // "null" when the page was already being replaced (e.g. a challenge reloading)
        }
        HttpUrl loaded = HttpUrl.parse(location);
        if (html == null || loaded == null) {
            return; // not the final document yet; the next onPageFinished (or the timeout) decides
        }
        if (isChallengePage(html)) {
            // Cloudflare's script reloads the page once it passes; wait for the next onPageFinished.
            s.sawChallenge = true;
            return;
        }
        s.navigating = null;
        cancelTimeout(p);
        s.ready = s.host.equals(loaded.host());
        if (status >= 400) {
            complete(p, null, new HttpException(status, html, p.url));
        } else {
            complete(p, html, null);
        }
        flushWaiting(s);
    }

    private void failNavigation(Session s, IOException error) {
        Pending p = s.navigating;
        s.navigating = null;
        s.ready = false;
        if (s.webView != null) {
            s.webView.stopLoading();
        }
        if (p != null) {
            complete(p, null, error);
        }
        // Whatever blocked this navigation would block everything queued behind it.
        List<Pending> queued = new ArrayList<>(s.waiting);
        s.waiting.clear();
        for (Pending q : queued) {
            complete(q, null, error);
        }
    }

    private void flushWaiting(Session s) {
        List<Pending> queued = new ArrayList<>(s.waiting);
        s.waiting.clear();
        for (Pending q : queued) {
            if (!q.dropped) {
                dispatch(q);
            }
        }
        scheduleReleaseIfIdle(s);
    }

    private void schedule(Pending p, long delayMs) {
        cancelTimeout(p);
        p.timeout = () -> onTimeout(p);
        mMainHandler.postDelayed(p.timeout, delayMs);
    }

    private void cancelTimeout(Pending p) {
        if (p.timeout != null) {
            mMainHandler.removeCallbacks(p.timeout);
            p.timeout = null;
        }
    }

    private void onTimeout(Pending p) {
        p.timeout = null;
        Session s = p.session;
        if (s == null) {
            return;
        }
        if (s.navigating == p) {
            // Stuck on a challenge means it needs a human (e.g. a Turnstile checkbox).
            failNavigation(s, s.sawChallenge
                    ? new HttpException(403, "", p.url)
                    : new IOException("Timed out loading " + p.url));
        } else if (p.attemptId != null && mInFlight.remove(p.attemptId) != null) {
            p.attemptId = null;
            retryWithNavigation(p, new IOException("Timed out loading " + p.url));
        }
    }

    private void complete(Pending p, String html, IOException error) {
        cancelTimeout(p);
        if (p.done.getCount() == 0) {
            return;
        }
        p.html = html;
        p.error = error;
        p.done.countDown();
        scheduleReleaseIfIdle(p.session);
    }

    /** The caller stopped waiting (timeout / interrupt). */
    private void drop(Pending p) {
        p.dropped = true;
        Session s = p.session;
        if (s == null || s.navigating == p) {
            return; // a navigation finishes or times out on its own
        }
        cancelTimeout(p);
        if (p.attemptId != null) {
            mInFlight.remove(p.attemptId);
            p.attemptId = null;
        }
        s.waiting.remove(p);
        scheduleReleaseIfIdle(s);
    }

    private void scheduleReleaseIfIdle(Session s) {
        if (s == null || s.webView == null || s.navigating != null || !s.waiting.isEmpty()) {
            return;
        }
        for (Pending p : mInFlight.values()) {
            if (p.session == s) {
                return;
            }
        }
        mMainHandler.removeCallbacks(s.release);
        mMainHandler.postDelayed(s.release, IDLE_RELEASE_MS);
    }

    // ------------------------------------------------------------------
    // WebView sessions (main thread)
    // ------------------------------------------------------------------

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private Session obtainSession(String host) {
        Session s = mSessions.get(host);
        if (s != null) {
            return s;
        }
        WebView webView = new LollipopFixedWebView(mAppContext);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBlockNetworkImage(true);
        // The default User-Agent is kept on purpose: it matches this WebView's client hints,
        // TLS and HTTP/2 fingerprint, and EHentaiLoginActivity uses the same one, so a
        // Cloudflare clearance earned there by hand is valid here too.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        // Never attached to a window; give it a phone-sized viewport anyway.
        webView.layout(0, 0, 1080, 1920);
        s = new Session(host, webView);
        webView.setWebViewClient(new Client(s));
        webView.addJavascriptInterface(new Bridge(), BRIDGE);
        mSessions.put(host, s);
        return s;
    }

    private void destroySession(Session s, IOException error) {
        mMainHandler.removeCallbacks(s.release);
        if (mSessions.get(s.host) == s) {
            mSessions.remove(s.host);
        }
        WebView webView = s.webView;
        s.webView = null;
        s.ready = false;
        List<Pending> affected = new ArrayList<>(s.waiting);
        s.waiting.clear();
        if (s.navigating != null) {
            affected.add(s.navigating);
            s.navigating = null;
        }
        Iterator<Pending> it = mInFlight.values().iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.session == s) {
                it.remove();
                p.attemptId = null;
                affected.add(p);
            }
        }
        for (Pending p : affected) {
            complete(p, null, error);
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }

    private class Session {
        final String host;
        WebView webView;
        // A same-origin page is loaded, so fetch() can be used from it.
        boolean ready;
        // The request that owns the current top-level navigation.
        Pending navigating;
        // The current navigation has landed on a Cloudflare challenge at least once.
        boolean sawChallenge;
        final List<Pending> waiting = new ArrayList<>();
        final Runnable release = () -> destroySession(this, new IOException("WebView released"));

        Session(String host, WebView webView) {
            this.host = host;
            this.webView = webView;
        }
    }

    private static class Pending {
        final String host;
        final String url;
        final String referer;
        final CountDownLatch done = new CountDownLatch(1);
        volatile String html;
        volatile IOException error;
        volatile boolean dropped;
        // Main thread only.
        Session session;
        String attemptId;
        int fetchAttempts;
        boolean navigated;
        Runnable timeout;

        Pending(String host, String url, String referer) {
            this.host = host;
            this.url = url;
            this.referer = referer;
        }
    }

    private class Client extends WebViewClient {
        private final Session mSession;

        Client(Session session) {
            mSession = session;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (mSession.webView == view && mSession.navigating != null) {
                view.evaluateJavascript(JS_SNAPSHOT, value -> onSnapshot(mSession, view, value));
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame() && mSession.webView == view && mSession.navigating != null) {
                failNavigation(mSession, new IOException("Failed to load " + request.getUrl()
                        + ": " + error.getDescription()));
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // Returning true keeps the app alive when the renderer dies.
            if (mSession.webView == view) {
                destroySession(mSession, new IOException("WebView renderer gone"));
            } else {
                view.destroy();
            }
            return true;
        }
    }

    /** Called from the WebView's JavaBridge thread; hops back to the main thread. */
    private class Bridge {
        @JavascriptInterface
        public void onResult(String id, int status, String mitigated, String body) {
            mMainHandler.post(() -> onFetchResult(id, status, mitigated, body, null));
        }

        @JavascriptInterface
        public void onError(String id, String message) {
            mMainHandler.post(() -> onFetchResult(id, 0, "", null,
                    TextUtils.isEmpty(message) ? "fetch failed" : message));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Cloudflare's interstitial ("Just a moment...") instead of the requested page. */
    static boolean isChallengePage(String html) {
        return html != null && (html.contains("window._cf_chl_opt")
                || html.contains("<title>Just a moment...</title>")
                || html.contains("id=\"challenge-form\""));
    }

    private static boolean isSameOrigin(String a, String b) {
        HttpUrl ua = TextUtils.isEmpty(a) ? null : HttpUrl.parse(a);
        HttpUrl ub = TextUtils.isEmpty(b) ? null : HttpUrl.parse(b);
        return ua != null && ub != null && ua.scheme().equals(ub.scheme())
                && ua.host().equals(ub.host()) && ua.port() == ub.port();
    }

    private static boolean isCloudflareCookie(String name) {
        return name.startsWith("cf_") || name.startsWith("__cf") || "_cfuvid".equals(name);
    }

    private static Set<String> cookieNames(String cookieHeader) {
        Set<String> names = new HashSet<>();
        if (!TextUtils.isEmpty(cookieHeader)) {
            for (String pair : cookieHeader.split(";")) {
                int eq = pair.indexOf('=');
                String name = (eq >= 0 ? pair.substring(0, eq) : pair).trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }
}
