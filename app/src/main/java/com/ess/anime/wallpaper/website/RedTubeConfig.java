package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * RedTube（redtube.com）- 官方公开 API，无需 key
 * GET api.redtube.com/?data=redtube.Videos.searchVideos&output=json&search={q}&page={p}
 * 注意：响应是 videos[].video 双层结构。
 */
public class RedTubeConfig extends TubeConfig {

    @Override
    public String getWebsiteName() {
        return "RedTube";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_redtube;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_REDTUBE;
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        return "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json&search="
                + joinTags(tagList) + "&page=" + page;
    }

    @Override
    public JsonObject unwrapVideoItem(JsonObject item) {
        try {
            return item.getAsJsonObject("video");
        } catch (Exception e) {
            return item;
        }
    }

    @Override
    public String getSavedImageHead() {
        return "RedTube-";
    }
}
