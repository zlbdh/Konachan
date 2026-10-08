package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * Cosblay (cosblay.com) - WordPress 成人 Cosplay 站
 */
public class CosblayConfig extends WordPressConfig {

    @Override
    public String getWebsiteName() {
        return "Cosblay";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_cosblay;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_COSBLAY;
    }

    @Override
    public String getSavedImageHead() {
        return "Cosblay-";
    }
}
