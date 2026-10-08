package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Eporner (eporner.com): official v2 API, no key required
 * GET eporner.com/api/v2/video/search/?query={q}&page={p}&per_page=30&thumbsize=big
 * The ID field is "id", not "video_id".
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
