package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * Yukkuri (yukkuri.shiteitte.net): Danbooru-compatible site
 */
public class YukkuriConfig extends DanbooruConfig {

    @Override
    public String getWebsiteName() {
        return "Yukkuri";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_yukkuri;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_YUKKURI;
    }

    @Override
    public String getSavedImageHead() {
        return "Yukkuri-";
    }
}
