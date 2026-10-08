package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.website.parser.HtmlParser;
import com.ess.anime.wallpaper.website.parser.WordPressParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WordPress REST API 站点抽象基类（misskon.com、4khd.com 等 Cosplay 站）
 *
 * 子类只需覆盖 getWebsiteName / getBaseUrl / getWebsiteLogoRes / getSavedImageHead，
 * 以及 baseUrl 常量。URL 构造走 getRestBase()，4khd 这类 rest_route 形式的站点
 * 覆盖 getRestBase() 和 getMediaUrl(int) 即可。
 */
public abstract class WordPressConfig extends WebsiteConfig<WordPressParser> {

    private WordPressParser mParser;

    /**
     * 二级继承（MisskonConfig extends WordPressConfig）时，
     * 父类构造函数的泛型反射拿不到 ParameterizedType，parser 会是 null，
     * 这里直接创建，绕过反射。
     */
    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new WordPressParser(this);
        }
        return mParser;
    }

    /**
     * WP REST API 根地址，默认标准形式：{baseUrl}wp-json/wp/v2/
     * 4khd.com 这类直接 /wp-json/ 会 301 的站点，子类覆盖为 rest_route 形式
     */
    protected String getRestBase() {
        return getBaseUrl() + "wp-json/wp/v2/";
    }

    /**
     * 单个 media 的 API 地址（featured_media 只是 id，需要第二跳拿 source_url）
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
        // _embed=wp:featuredmedia 把首图直链直接嵌在 posts 响应里，避免 N+1 请求
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
