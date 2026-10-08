package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Eporner（eporner.com）- 官方 v2 API，无需 key
 * GET eporner.com/api/v2/video/search/?query={q}&page={p}&per_page=30&thumbsize=big
 * 注意：id 字段叫 "id" 而不是 "video_id"。
 */
public class EpornerConfig extends TubeConfig {

    @Override
    public String getWebsiteName() {
        return "Eporner";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_eporner;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_EPORNER;
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        return "https://www.eporner.com/api/v2/video/search/?query="
                + joinTags(tagList) + "&page=" + page + "&per_page=30&thumbsize=big";
    }

    @Override
    public String getVideoId(JsonObject video) {
        try {
            return video.get("id").getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String getSavedImageHead() {
        return "Eporner-";
    }
}
