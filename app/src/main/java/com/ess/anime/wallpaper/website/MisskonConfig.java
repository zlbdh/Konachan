package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * MissKon (misskon.com) - WordPress 成人 Cosplay 站
 */
public class MisskonConfig extends WordPressConfig {

    public final static String BASE_URL_MISSKON = "https://misskon.com/";

    @Override
    public String getWebsiteName() {
        return "MissKon";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_misskon;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_MISSKON;
    }

    @Override
    public String getSavedImageHead() {
        return "MissKon-";
    }
}
