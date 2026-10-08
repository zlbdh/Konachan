package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.website.parser.HtmlParser;
import com.ess.anime.wallpaper.website.parser.WordPressParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for WordPress REST API sites such as misskon.com and 4khd.com
 *
 * Subclasses override getWebsiteName, getBaseUrl, getWebsiteLogoRes, getSavedImageHead,
 * and the baseUrl constant. URL construction uses getRestBase(); rest_route sites such as 4khd
 * override getRestBase() and getMediaUrl(int).
 */
public abstract class WordPressConfig extends WebsiteConfig<WordPressParser> {

    private WordPressParser mParser;

    /**
     * With two-level inheritance, such as MisskonConfig extending WordPressConfig,
     * generic reflection in the parent constructor cannot get a ParameterizedType and leaves parser null;
     * create it directly here to bypass reflection.
     */
    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new WordPressParser(this);
        }
        return mParser;
    }

    /**
     * WordPress REST API root; default format: {baseUrl}wp-json/wp/v2/
     * Sites such as 4khd.com that redirect /wp-json/ override this with rest_route
     */
    protected String getRestBase() {
        return getBaseUrl() + "wp-json/wp/v2/";
    }

    /**
     * Single-media API URL; featured_media is only an ID, so a second request retrieves source_url
     */
    public String getMediaUrl(int mediaId) {
        return getRestBase() + "media/" + mediaId;
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
        if (tagList == null) {
            tagList = new ArrayList<>();
        }
        StringBuilder tags = new StringBuilder();
        for (String tag : tagList) {
            if (!TextUtils.isEmpty(tag)) {
                tags.append(encodeTag(tag)).append(" ");
            }
        }
        // _embed=wp:featuredmedia includes the featured image URL in the posts response to avoid N+1 requests
        return getRestBase() + "posts?per_page=42&page=" + page
                + "&search=" + tags.toString().trim()
                + "&_embed=wp:featuredmedia";
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
        return getBaseUrl() + "?p=" + id;
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
