package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.website.parser.HitomiParser;
import com.ess.anime.wallpaper.website.parser.HtmlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hitomi.la (hitomi.la) - 大型本子站，CDN 静态文件 API
 *
 * 列表：nozomi 索引 (4 字节小端 gallery id 数组，倒序) + Range 分页
 * 画廊详情：https://ltn.gold-usergeneratedcontent.net/galleries/{id}.js
 * 图片：https://{sub}.gold-usergeneratedcontent.net/images/{c}/{b}/{hash}.webp
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
     * 二级继承时父类构造函数的泛型反射拿不到 ParameterizedType，
     * 这里直接创建 parser，绕过反射。
     */
    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new HitomiParser(this);
        }
        return mParser;
    }

    /** 当前请求的页码（Parser 用它做 nozomi Range 分页） */
    public int getCurrentPage() {
        return mCurrentPage;
    }

    public List<String> getCurrentTags() {
        return mCurrentTags;
    }

    public static String getIndexUrl() {
        return INDEX_URL;
    }

    public static String getGalleryJsUrl(int galleryId) {
        return "https://" + CDN_HOST + "/galleries/" + galleryId + ".js";
    }

    public static int getPageSize() {
        return PAGE_SIZE;
    }

    /**
     * 根据 gallery id 算图片 CDN 子域名（hitomi gg.js 算法简化版）
     */
    public static String getSubdomain(int galleryId) {
        int o = galleryId % 2;
        return (char) ('a' + o) + "a";  // "aa" 或 "ba"
    }

    /**
     * 根据 hash 拼图片直链（hitomi gg.js full_path_from_hash）
     * 格式：images/{末1位}/{末2位}/{完整hash}.webp
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
        // 真实请求由 HitomiParser 用 OkHttp.execute + Range 头完成，
        // 这里返回一个会快速 404 的占位 URL（Parser 会忽略它的 Document）。
        // 404 在 PostFragment 里被当作"无搜索结果"处理，不会弹网络错误。
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
