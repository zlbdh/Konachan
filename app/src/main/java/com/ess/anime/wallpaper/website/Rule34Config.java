package com.ess.anime.wallpaper.website;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.ess.anime.wallpaper.MyApp;
import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.website.parser.AttrDapiParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Rule34Config extends WebsiteConfig<AttrDapiParser> {

    // rule34.xxx 的 dapi 必须走专用 API 域名（www 域名会返回 Missing authentication）
    private final static String API_BASE_URL = "https://api.rule34.xxx/";

    // Rule34 API 必须鉴权（api.rule34.xxx 注册账号后在 https://rule34.xxx/index.php?page=account&s=options 获取）
    private String getApiKeySuffix() {
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MyApp.getInstance());
            String apiKey = sp.getString(Constants.RULE34_API_KEY, "").trim();
            String userId = sp.getString(Constants.RULE34_USER_ID, "").trim();
            if (!TextUtils.isEmpty(apiKey) && !TextUtils.isEmpty(userId)) {
                return "&api_key=" + apiKey + "&user_id=" + userId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String getWebsiteName() {
        return "Rule34";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_rule34;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_RULE34;
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
            tags.append(tag).append("+");
        }

        return API_BASE_URL + "index.php?page=dapi&s=post&q=index&pid=" + (page - 1) + "&tags=" + tags + "&limit=42" + getApiKeySuffix();
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
        return "Rule34-";
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
