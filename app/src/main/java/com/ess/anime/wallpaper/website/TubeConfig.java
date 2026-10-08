package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.website.parser.HtmlParser;
import com.ess.anime.wallpaper.website.parser.TubeParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for adult video sites: PornHub, RedTube, and Eporner
 *
 * All three public APIs return thumbnails in multiple sizes, which are used as browsable image sources.
 * Subclasses implement getPostUrl, extractVideoItems, unwrapVideoItem, and getVideoId,
 * plus the name, host, icon, and saved-file prefix.
 */
public abstract class TubeConfig extends WebsiteConfig<TubeParser> {

    private TubeParser mParser;

    /**
     * With two-level inheritance, such as PornHubConfig extending TubeConfig,
     * generic reflection in the parent constructor cannot get a ParameterizedType and leaves parser null;
     * create it directly here, as in WordPressConfig, to bypass reflection.
     */
    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new TubeParser(this);
        }
        return mParser;
    }

    /**
     * Extract the video item array from the API response root JSON.
     * The default is root.getAsJsonArray("videos"), which all three currently use.
     */
    public JsonArray extractVideoItems(JsonObject root) {
        try {
            return root.getAsJsonArray("videos");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Unwrap one video object. RedTube nests it in videos[].video and overrides this method;
     * PornHub and Eporner return the item directly.
     */
    public JsonObject unwrapVideoItem(JsonObject item) {
        return item;
    }

    /**
     * Unique video ID: PornHub and RedTube use video_id; Eporner uses id.
     */
    public String getVideoId(JsonObject video) {
        try {
            return video.get("video_id").getAsString();
        } catch (Exception e) {
            return "";
        }
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
    public boolean isDisableDiskCache() {
        // Tube thumbnail URLs have 24-hour signatures, so expired disk-cache entries cannot load.
        return true;
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

    /** Join search terms into the API query parameter; return an empty string for empty tags */
    protected String joinTags(List<String> tagList) {
        if (tagList == null) {
            tagList = new ArrayList<>();
        }
        StringBuilder sb = new StringBuilder();
        for (String tag : tagList) {
            if (!TextUtils.isEmpty(tag)) {
                if (sb.length() > 0) {
                    sb.append("+");
                }
                sb.append(encodeTag(tag));
            }
        }
        return sb.toString();
    }
}
