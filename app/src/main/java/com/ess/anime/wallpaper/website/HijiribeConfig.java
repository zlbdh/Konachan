package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * Hijiribe (hijiribe.donmai.us): official Danbooru sister site
 */
public class HijiribeConfig extends DanbooruConfig {

    @Override
    public String getWebsiteName() {
        return "Hijiribe";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_hijiribe;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_HIJIRIBE;
    }

    @Override
    public String getSavedImageHead() {
        return "Hijiribe-";
    }

    @Override
    public com.ess.anime.wallpaper.website.parser.DanbooruParser getHtmlParser() {
        return new com.ess.anime.wallpaper.website.parser.DanbooruParser(this);
    }
}
