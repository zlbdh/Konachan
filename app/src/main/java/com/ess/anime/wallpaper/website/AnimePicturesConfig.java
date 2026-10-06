package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.AnimePicturesParser;

import java.util.ArrayList;
import java.util.List;

public class AnimePicturesConfig extends WebsiteConfig<AnimePicturesParser> {

    private final static String API_BASE_URL = "https://api.anime-pictures.net/";

    @Override
    public String getWebsiteName() {
        return "AnimePictures";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_animepictures;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_ANIME_PICTURES;
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
        if (tagList == null) {
            tagList = new ArrayList<>();
        }

        StringBuilder tags = new StringBuilder();
        String orderBy = null;
        for (String tag : tagList) {
            if (tag.startsWith("order:")) {
                orderBy = tag.substring("order:".length());
            } else {
                tags.append(tag).append("+");
            }
        }

        StringBuilder url = new StringBuilder(API_BASE_URL)
                .append("api/v3/posts?page=").append(page - 1)
                .append("&posts_per_page=42&lang=en");
        if (tags.length() > 0) {
            url.append("&search_tag=").append(tags);
        }
        if (orderBy != null && !orderBy.isEmpty()) {
            url.append("&order_by=").append(orderBy);
        }
        return url.toString();
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
        return getPostUrl(page, new ArrayList<String>());
    }

    @Override
    public String getPostDetailUrl(String id) {
        return API_BASE_URL + "api/v3/posts/" + id + "?lang=en";
    }

    @Override
    public String getCommentUrl(String id) {
        return getPostDetailUrl(id);
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
        return "AnimePictures-";
    }

    @Override
    public boolean isSupportRandomPost() {
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
        return null;
    }

}
