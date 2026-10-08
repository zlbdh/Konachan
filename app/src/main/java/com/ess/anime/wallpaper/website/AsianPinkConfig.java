package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * AsianPink (asianpink.net) - WordPress 成人 Cosplay 站
 */
public class AsianPinkConfig extends WordPressConfig {

    @Override
    public String getWebsiteName() {
        return "AsianPink";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_asianpink;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_ASIANPINK;
    }

    @Override
    public String getSavedImageHead() {
        return "AsianPink-";
    }
}
