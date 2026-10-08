package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.HtmlParser;
import com.ess.anime.wallpaper.website.parser.NHentaiParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * nhentai.net - 官方 v2 REST API，无需 key
 *
 * 注意：实际 API 为新版紧凑格式（已实测，非旧版 images.pages 格式）：
 * - 列表：GET /api/v2/galleries?page=N → {result:[...], num_pages}
 *   单画廊：{id, media_id, english_title, thumbnail:"galleries/{mid}/thumb.jpg.webp",
 *            thumbnail_width/height, num_pages, num_favorites, tag_ids, blacklisted}
 * - 按 tag：GET /api/v2/galleries/tagged?tag_id={id}&page=N（同上格式）
 * - 搜索：GET /api/v2/search?query={q}&page=N（同上格式）
 * - 详情：GET /api/v2/galleries/{id} → {title:{english,pretty}, cover, thumbnail,
 *            scanlator, upload_date(epoch秒), tags:[{id,type,name}], num_pages,
 *            pages:[{number, path:"galleries/{mid}/N.jpg", width, height, ...}]}
 * - 直链：https://i{N}.nhentai.net/{path}，N 取 1-4（path 自带真实扩展名）
 */
public class NHentaiConfig extends WebsiteConfig<NHentaiParser> {

    public final static String BASE_URL_NHENTAI = "https://nhentai.net/";

    private NHentaiParser mParser;

    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new NHentaiParser(this);
        }
        return mParser;
    }

    @Override
    public String getWebsiteName() {
        return "nhentai";
    }

    @Override
    public int getWebsiteLogoRes() {
        // parent 接线时生成 ic_website_nhentai 图标
        return R.drawable.ic_website_nhentai;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_NHENTAI;
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
        String tag = null;
        for (String t : tagList) {
            if (!TextUtils.isEmpty(t)) {
                tag = t.trim();
                break;
            }
        }
        if (TextUtils.isEmpty(tag)) {
            return getBaseUrl() + "api/v2/galleries?page=" + page;
        }
        if (tag.matches("\\d+")) {
            // 纯数字视为 tag id，走 tagged 接口
            return getBaseUrl() + "api/v2/galleries/tagged?tag_id=" + tag + "&page=" + page;
        }
        return getBaseUrl() + "api/v2/search?query=" + encodeTag(tag) + "&page=" + page;
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
        // App 用 linkToShow 拉取详情，这里给 API 地址（parser 按 JSON 解析）
        return getBaseUrl() + "api/v2/galleries/" + id;
    }

    /** 网页版画廊地址（source 字段 / 用户查看用） */
    public String getGalleryWebUrl(String id) {
        return getBaseUrl() + "g/" + id + "/";
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
        return "nhentai-";
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
