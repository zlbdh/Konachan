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
 * 成人视频 tube 站抽象基类（PornHub / RedTube / Eporner）
 *
 * 三家都是视频站，但官方公开 API 返回多尺寸缩略图数组，直接当图片浏览源。
 * 子类只需实现 getPostUrl / extractVideoItems / unwrapVideoItem / getVideoId，
 * 以及名称、域名、图标、保存前缀。
 */
public abstract class TubeConfig extends WebsiteConfig<TubeParser> {

    private TubeParser mParser;

    /**
     * 二级继承（PornHubConfig extends TubeConfig）时，
     * 父类构造函数的泛型反射拿不到 ParameterizedType，parser 会是 null，
     * 这里直接创建，绕过反射（同 WordPressConfig）。
     */
    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new TubeParser(this);
        }
        return mParser;
    }

    /**
     * 从 API 响应根 JSON 提取视频条目数组。
     * 默认 root.getAsJsonArray("videos")，三家目前都是这个结构。
     */
    public JsonArray extractVideoItems(JsonObject root) {
        try {
            return root.getAsJsonArray("videos");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解开单条视频对象。RedTube 是 videos[].video 双层结构，子类覆盖；
     * PornHub / Eporner 直接返回 item 本身。
     */
    public JsonObject unwrapVideoItem(JsonObject item) {
        return item;
    }

    /**
     * 视频唯一 id。PornHub/RedTube 用 video_id，Eporner 用 id。
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

    /** 搜索词 join 成 API 的 query 参数（空 tags 时返回空字符串） */
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
