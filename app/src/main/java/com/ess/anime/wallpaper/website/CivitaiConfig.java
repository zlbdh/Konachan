package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.CivitaiParser;
import com.ess.anime.wallpaper.website.parser.HtmlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Civitai (civitai.com): AI-generated adult image site
 *
 * API: GET https://civitai.com/api/v1/images?limit=20&nsfw=true&sort=Newest
 * No key is required; anonymous requests can retrieve NSFW content.
 * Response: { items: [{id, url, width, height, nsfwLevel, username, meta:{prompt}, postId}],
 *         metadata: {nextCursor} }
 *
 * Pagination: Civitai uses metadata.nextCursor rather than a traditional page parameter.
 * Maintain a cursor list for sequential paging: mCursors.get(i) is the cursor for page i+2,
 * returned as nextCursor by page i+1. Reset the list for page=1, a new search, or refresh.
 * Nonsequential navigation is not supported: isSupportPageJump() returns false so the
 * "jump to page" button is hidden in PostFragment; without a cursor the API would
 * silently return page one, which is confusing.
 */
public class CivitaiConfig extends WebsiteConfig<CivitaiParser> {

    public final static String BASE_URL_CIVITAI = "https://civitai.com/";

    private static final String API_BASE = "https://civitai.com/api/v1/images";
    private static final int PAGE_LIMIT = 42;

    private CivitaiParser mParser;

    /** Paging cursors; mCursors.get(i) corresponds to page i+2 */
    private final List<String> mCursors = new ArrayList<>();

    /**
     * Create the Parser directly to bypass generic reflection in the parent constructor
     *(With two-level inheritance, superclass is not a ParameterizedType, so reflection cannot obtain the Parser type)
     */
    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new CivitaiParser(this);
        }
        return mParser;
    }

    @Override
    public String getWebsiteName() {
        return "Civitai";
    }

    @Override
    public int getWebsiteLogoRes() {
        // TODO Replace the placeholder AIBooru icon when the parent agent provides the final icon
        return R.drawable.ic_website_civitai;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_CIVITAI;
    }

    /**
     * Store nextCursor after parsing a page so the next request can use it.
     * Refreshing a page returns the same cursor; deduplicate it to prevent unlimited list growth.
     */
    public synchronized void saveNextCursor(String cursor) {
        if (TextUtils.isEmpty(cursor)) {
            return;
        }
        if (!mCursors.isEmpty()
                && TextUtils.equals(mCursors.get(mCursors.size() - 1), cursor)) {
            return;
        }
        mCursors.add(cursor);
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        if (tagList == null) {
            tagList = new ArrayList<>();
        }
        StringBuilder tags = new StringBuilder();
        for (String tag : tagList) {
            if (!TextUtils.isEmpty(tag)) {
                tags.append(encodeTag(tag)).append(" ");
            }
        }
        StringBuilder url = new StringBuilder(API_BASE)
                .append("?limit=").append(PAGE_LIMIT)
                .append("&nsfw=true&sort=Newest");
        String query = tags.toString().trim();
        if (!TextUtils.isEmpty(query)) {
            url.append("&query=").append(query);
        }
        synchronized (this) {
            if (page <= 1) {
                // Reset paging state for a new search or pull-to-refresh
                mCursors.clear();
            } else {
                int index = page - 2;
                if (index >= 0 && index < mCursors.size()) {
                    url.append("&cursor=").append(encodeTag(mCursors.get(index)));
                }
                // Omit the cursor parameter if it is missing during nonsequential navigation;
                // the API returns page one, as documented in the class comment
            }
        }
        return url.toString();
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
        return BASE_URL_CIVITAI + "images/" + id;
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
        return "Civitai-";
    }

    @Override
    public boolean isSupportRandomPost() {
        return false;
    }

    @Override
    public boolean isSupportPageJump() {
        // Cursor-based pagination: jumping to an arbitrary page has no
        // corresponding cursor, so the jump button is hidden in the UI.
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
