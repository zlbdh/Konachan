package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.HtmlParser;
import com.ess.anime.wallpaper.website.parser.RedgifsParser;

import java.util.Collections;
import java.util.List;

/**
 * Redgifs (redgifs.com): Adult GIFs/short videos.
 *
 * API:
 * 1. GET https://api.redgifs.com/v2/auth/temporary -> {"token": "..."} (free, no key needed)
 * 2. GET https://api.redgifs.com/v2/gifs/search?search_text={q}&count=42&page={p}
 *    Header: Authorization: Bearer {token}
 * Response: {"gifs": [{id, urls: {poster, thumbnail, hd, sd}, tags: []}], "total": N}
 *
 * Note: The token is device-bound. If search returns 401, RedgifsParser will
 * request a fresh token and retry once.
 *
 * Thumbnails use urls.poster (static image); detail view uses urls.hd.
 */
public class RedgifsConfig extends WebsiteConfig<RedgifsParser> {

    public final static String BASE_URL_REDGIFS = "https://www.redgifs.com/";

    public static final String API_AUTH = "https://api.redgifs.com/v2/auth/temporary";
    private static final String API_SEARCH = "https://api.redgifs.com/v2/gifs/search";
    private static final int PAGE_LIMIT = 42;

    private RedgifsParser mParser;
    private String mToken;
    private long mTokenTime;

    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new RedgifsParser(this);
        }
        return mParser;
    }

    @Override
    public String getWebsiteName() {
        return "Redgifs";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_redgifs;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_REDGIFS;
    }

    @Override
    public boolean hasTagJson() {
        return false;
    }

    @Override
    public String getTagJsonUrl() {
        return null;
    }

    @Override
    public boolean isSupportRandomPost() {
        return false;
    }

    @Override
    public boolean isSupportAdvancedSearch() {
        return false;
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        // Ensure we have a valid token (fetched synchronously; called from background thread)
        ensureToken();
        StringBuilder tags = new StringBuilder();
        if (tagList != null) {
            for (String tag : tagList) {
                if (tag != null && !tag.trim().isEmpty()) {
                    if (tags.length() > 0) {
                        tags.append(" ");
                    }
                    tags.append(tag.trim());
                }
            }
        }
        String query = tags.length() > 0 ? tags.toString() : "hottest";
        try {
            query = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception ignore) {
        }
        return API_SEARCH + "?search_text=" + query + "&count=" + PAGE_LIMIT + "&page=" + page;
    }

    /**
     * Fetch a fresh bearer token if none cached or expired.
     * Called from background thread; safe to do synchronous network.
     */
    private void ensureToken() {
        if (getToken() != null) {
            return;
        }
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(API_AUTH)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            okhttp3.Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                com.google.gson.JsonObject obj = new com.google.gson.JsonParser()
                        .parse(json).getAsJsonObject();
                if (obj.has("token")) {
                    setToken(obj.get("token").getAsString());
                }
            }
            if (response.body() != null) {
                response.body().close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> parseSearchAutoCompleteListFromTagJson(String search) {
        return Collections.emptyList();
    }

    /** Cached bearer token (valid ~24h). Synchronized for thread safety. */
    public synchronized String getToken() {
        // Refresh if older than 20 hours
        if (mToken == null || System.currentTimeMillis() - mTokenTime > 20L * 3600 * 1000) {
            mToken = null;
        }
        return mToken;
    }

    public synchronized void setToken(String token) {
        mToken = token;
        mTokenTime = System.currentTimeMillis();
    }

    public synchronized void clearToken() {
        mToken = null;
        mTokenTime = 0;
    }

    @Override
    public boolean isDisableDiskCache() {
        // GIF poster URLs are stable, but HD URLs may have signatures; keep disk cache on
        // for posters (parser uses poster for thumbs). Default false is fine.
        return false;
    }

    @Override
    public java.util.List<String> parseSearchAutoCompleteListFromNetwork(String promptResult, String search) {
        return java.util.Collections.emptyList();
    }

    @Override
    public String getSearchAutoCompleteUrl(String tag) {
        return null;
    }

    @Override
    public boolean isSupportSearchAutoCompleteFromNetwork() {
        return false;
    }

    @Override
    public String getSavedImageHead() {
        return "Redgifs-";
    }

    @Override
    public boolean hasTagJson() {
        return false;
    }

    @Override
    public String getTagJsonUrl() {
        return null;
    }

    @Override
    public List<String> parseSearchAutoCompleteListFromTagJson(String search) {
        return Collections.emptyList();
    }

    @Override
    public String getPopularDailyUrl(int year, int month, int day, int page) {
        return getPostUrl(page, null);
    }

    @Override
    public String getPopularWeeklyUrl(int year, int month, int day, int page) {
        return getPostUrl(page, null);
    }

    @Override
    public String getPopularMonthlyUrl(int year, int month, int day, int page) {
        return getPostUrl(page, null);
    }

    @Override
    public String getPopularOverallUrl(int year, int month, int day, int page) {
        return getPostUrl(page, null);
    }

    @Override
    public String getPostDetailUrl(String id) {
        return getBaseUrl();
    }

    @Override
    public String getCommentUrl(String id) {
        return null;
    }

    @Override
    public boolean hasPool() {
        return false;
    }

    @Override
    public String getPoolUrl(int page, String name) {
        return null;
    }

    @Override
    public String getPoolPostUrl(String linkToShow, int page) {
        return null;
    }

    @Override
    public boolean needReloadDetailByIdForPoolPost() {
        return false;
    }

    @Override
    public boolean isSupportRandomPost() {
        return false;
    }

    @Override
    public boolean isSupportAdvancedSearch() {
        return false;
    }
}
