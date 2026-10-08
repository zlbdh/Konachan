package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * AdultComixxx (adultcomixxx.com) - WordPress 成人 Cosplay 站
 */
public class AdultComixxxConfig extends WordPressConfig {

    @Override
    public String getWebsiteName() {
        return "AdultComixxx";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_adultcomixxx;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_ADULTCOMIXXX;
    }

    @Override
    public String getSavedImageHead() {
        return "AdultComixxx-";
    }
}
