package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * Sonohara (sonohara.donmai.us): official Danbooru sister site
 */
public class SonoharaConfig extends DanbooruConfig {

    @Override
    public String getWebsiteName() {
        return "Sonohara";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_sonohara;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_SONOHARA;
    }

    @Override
    public String getSavedImageHead() {
        return "Sonohara-";
    }

    @Override
    public com.ess.anime.wallpaper.website.parser.DanbooruParser getHtmlParser() {
        return new com.ess.anime.wallpaper.website.parser.DanbooruParser(this);
    }
}
