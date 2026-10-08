package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.website.parser.HitomiParser;
import com.ess.anime.wallpaper.website.parser.HtmlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hitomi.la (hitomi.la): gallery site using static CDN files as an API
 *
 * List: nozomi index (reverse-ordered array of four-byte little-endian gallery IDs) with Range pagination
 * Gallery details: https://ltn.gold-usergeneratedcontent.net/galleries/{id}.js
 * Images: https://{sub}.gold-usergeneratedcontent.net/images/{c}/{b}/{hash}.webp
 */
public class HitomiConfig extends WebsiteConfig<HitomiParser> {

    public final static String BASE_URL_HITOMI = "https://hitomi.la/";

    private static final String CDN_HOST = "ltn.gold-usergeneratedcontent.net";
    private static final String INDEX_URL =
            "https://" + CDN_HOST + "/n/index-all.nozomi";
    private static final int PAGE_SIZE = 42;

    private HitomiParser mParser;
    private int mCurrentPage = 1;
    private List<String> mCurrentTags = new ArrayList<>();

    /**
     * With two-level inheritance, generic reflection in the parent constructor cannot get a ParameterizedType;
     * create the parser directly here to bypass reflection.
     */
    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new HitomiParser(this);
        }
        return mParser;
    }

    /** Current request page; the Parser uses it for nozomi Range pagination */
    public int getCurrentPage() {
        return mCurrentPage;
    }

    public List<String> getCurrentTags() {
        return mCurrentTags;
    }

    public static String getIndexUrl() {
        return INDEX_URL;
    }

    /**
     * Build a tag search nozomi URL, e.g. n/tag/female:anal-all.nozomi
     * Tag format is "{type}:{name}"; the name part is URL-encoded, colon preserved.
     */
    public static String getTagNozomiUrl(String tag) {
        if (TextUtils.isEmpty(tag)) {
            return null;
        }
        String encoded = encodeTagForNozomi(tag.trim());
        if (TextUtils.isEmpty(encoded)) {
            return null;
        }
        return "https://" + CDN_HOST + "/n/tag/" + encoded + "-all.nozomi";
    }

    private static String encodeTagForNozomi(String tag) {
        try {
            // URLEncoder encodes space as '+', which is wrong in a path; also keep ':' unencoded
            String e = java.net.URLEncoder.encode(tag, "UTF-8").replace("+", "%20").replace("%3A", ":");
            return e;
        } catch (Exception ex) {
            return tag;
        }
    }

    /**
     * Nozomi URL for the current request: tag search nozomi when tags are present,
     * otherwise the global index. Both share the same format (4-byte big-endian IDs, newest first).
     */
    public String getNozomiUrl() {
        if (mCurrentTags != null && !mCurrentTags.isEmpty()) {
            String url = getTagNozomiUrl(mCurrentTags.get(0));
            if (!TextUtils.isEmpty(url)) {
                return url;
            }
        }
        return INDEX_URL;
    }

    public static String getGalleryJsUrl(int galleryId) {
        return "https://" + CDN_HOST + "/galleries/" + galleryId + ".js";
    }

    public static int getPageSize() {
        return PAGE_SIZE;
    }

    /**
     * Compute the image CDN subdomain from the gallery ID using a simplified Hitomi gg.js algorithm
     */
    public static String getSubdomain(int galleryId) {
        int o = galleryId % 2;
        return (char) ('a' + o) + "a";  // "aa" or "ba"
    }

    /**
     * Build a direct image URL from its hash using Hitomi gg.js full_path_from_hash
     * Format: images/{last digit}/{last two digits}/{full hash}.webp
     */
    public static String getImageUrl(int galleryId, String hash) {
        if (TextUtils.isEmpty(hash) || hash.length() < 3) {
            return "";
        }
        char last = hash.charAt(hash.length() - 1);
        String middle = hash.substring(hash.length() - 3, hash.length() - 1);
        return "https://" + getSubdomain(galleryId) + ".gold-usergeneratedcontent.net"
                + "/images/" + last + "/" + middle + "/" + hash + ".webp";
    }

    @Override
    public String getWebsiteName() {
        return "Hitomi";
    }

    @Override
    public int getWebsiteLogoRes() {
        return com.ess.anime.wallpaper.R.drawable.ic_website_hitomi;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_HITOMI;
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
    public String getPostUrl(int page, List<String> tagList) {
        // HitomiParser makes the actual request with OkHttp.execute and a Range header;
        // return a placeholder URL that quickly produces 404 here. The Parser ignores its Document.
        // PostFragment treats 404 as no search results rather than a network error.
        mCurrentPage = Math.max(1, page);
        mCurrentTags = tagList == null ? new ArrayList<>() : new ArrayList<>(tagList);
        return "https://" + CDN_HOST + "/hitomi-placeholder-page-" + mCurrentPage;
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
        return getBaseUrl() + "galleries/" + id + ".html";
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
    public String getSavedImageHead() {
        return "Hitomi-";
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
    public boolean isSupportSearchAutoCompleteFromNetwork() {
        return false;
    }

    @Override
    public String getSearchAutoCompleteUrl(String tag) {
        return null;
    }

    @Override
    public List<String> parseSearchAutoCompleteListFromNetwork(String promptResult, String search) {
        return Collections.emptyList();
    }
}
