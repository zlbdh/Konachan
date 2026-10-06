package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.WallhallaParser;

import java.util.List;

public class WallhallaConfig extends WebsiteConfig<WallhallaParser> {

    @Override
    public String getWebsiteName() {
        return "Wallhalla";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_wallhalla;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_WALLHALLA;
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
    public void saveTagJson(String key, String json) {
    }

    @Override
    public String getTagJson() {
        return null;
    }

    @Override
    public List<String> parseSearchAutoCompleteListFromTagJson(String search) {
        return null;
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        // 2026-10：老站（/new、/search）已下线，新站为前后端分离，无搜索接口、无服务端随机，
        // 统一返回 recent 列表接口，由 WallhallaParser 解析 JSON
        return getBaseUrl() + "api/catalogue/recent?page=" + page + "&limit=20";
    }

    @Override
    public String getPopularDailyUrl(int year, int month, int day, int page) {
        return null;
    }

    @Override
    public String getPopularWeeklyUrl(int year, int month, int day, int page) {
        return null;
    }

    @Override
    public String getPopularMonthlyUrl(int year, int month, int day, int page) {
        return null;
    }

    @Override
    public String getPopularOverallUrl(int year, int month, int day, int page) {
        // 2026-10：老站 /toplist 已下线，新站精选接口为 /api/catalogue/best
        return getBaseUrl() + "api/catalogue/best?page=" + page + "&limit=20";
    }

    @Override
    public String getPostDetailUrl(String id) {
        // 2026-10：详情走公开 JSON 接口，id 为图片 uuid
        return getBaseUrl() + "api/catalogue/item/" + id;
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
        return "Wallhalla-";
    }

    @Override
    public boolean isSupportRandomPost() {
        // 2026-10：新站无服务端随机接口（/random 为前端本地洗牌），暂不支持
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
        return null;
    }
}
