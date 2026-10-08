package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * Cosplay3X (cosplay3x.com): WordPress adult cosplay site
 */
public class Cosplay3XConfig extends WordPressConfig {

    @Override
    public String getWebsiteName() {
        return "Cosplay3X";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_cosplay3x;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_COSPLAY3X;
    }

    @Override
    public String getSavedImageHead() {
        return "Cosplay3X-";
    }
}
