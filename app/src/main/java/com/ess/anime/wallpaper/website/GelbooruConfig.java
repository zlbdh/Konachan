package com.ess.anime.wallpaper.website;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.ess.anime.wallpaper.MyApp;
import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.website.parser.GelbooruParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GelbooruConfig extends WebsiteConfig<GelbooruParser> {

    // 内置兜底 key（匿名访问无数据，key 失效时用户可在设置中填入自己的）
    private final static String DEFAULT_API_KEY = "54cd6cb43f920687baaf9fe3748dd418735c802287d6614dafe1c6d8dafc3cd702525ef2653ba26193da00cec72aac63e2991d627a533f998ecd45d0d33baa3b";
    private final static String DEFAULT_USER_ID = "1827525";

    private String getApiKeySuffix() {
        String apiKey = DEFAULT_API_KEY;
        String userId = DEFAULT_USER_ID;
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MyApp.getInstance());
            String savedKey = sp.getString(Constants.GELBOORU_API_KEY, "");
            String savedUserId = sp.getString(Constants.GELBOORU_USER_ID, "");
            if (!TextUtils.isEmpty(savedKey) && !TextUtils.isEmpty(savedUserId)) {
                apiKey = savedKey.trim();
                userId = savedUserId.trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "&api_key=" + apiKey + "&user_id=" + userId;
    }

    @Override
    public String getWebsiteName() {
        return "Gelbooru";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_gelbooru;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_GELBOORU;
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
        for (String tag : tagList) {
            if (TextUtils.equals(tag, "order:random")) {
                tag = "sort:random";
            }
            tags.append(encodeTag(tag)).append("+");
        }

        return getBaseUrl() + "index.php?page=dapi&s=post&q=index&pid=" + (page - 1) + "&tags=" + tags + "&limit=42" + getApiKeySuffix();
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
        return getPostUrl(page, Collections.singletonList("sort:score"));
    }

    @Override
    public String getPostDetailUrl(String id) {
        return getBaseUrl() + "index.php?page=post&s=view&id=" + id;
    }

    @Override
    public String getCommentUrl(String id) {
        return getPostDetailUrl(id);
    }

    @Override
    public boolean hasPool() {
        return true;
    }

    @Override
    public String getPoolUrl(int page, String name) {
        return getBaseUrl() + "index.php?page=pool&s=list&pid=" + (page - 1) * 25;
    }

    @Override
    public String getPoolPostUrl(String linkToShow, int page) {
        return linkToShow;
    }

    @Override
    public boolean needReloadDetailByIdForPoolPost() {
        return true;
    }

    @Override
    public String getSavedImageHead() {
        return "Gelbooru-";
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
        return true;
    }

    @Override
    public String getSearchAutoCompleteUrl(String tag) {
        return getBaseUrl() + "index.php?page=autocomplete2&term=" + tag;
    }

    @Override
    public List<String> parseSearchAutoCompleteListFromNetwork(String promptResult, String search) {
        List<String> list = new ArrayList<>();
        try {
            JsonArray tagArray = new JsonParser().parse(promptResult).getAsJsonArray();
            for (int i = 0; i < tagArray.size(); i++) {
                JsonObject item = tagArray.get(i).getAsJsonObject();
                list.add(item.get("value").getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

}
