package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * AIBooru (aibooru.online): Danbooru-compatible site for AI-generated images
 */
public class AIbooruConfig extends DanbooruConfig {

    @Override
    public String getWebsiteName() {
        return "AIBooru";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_aibooru;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_AIBOORU;
    }

    @Override
    public String getSavedImageHead() {
        return "AIBooru-";
    }
}
