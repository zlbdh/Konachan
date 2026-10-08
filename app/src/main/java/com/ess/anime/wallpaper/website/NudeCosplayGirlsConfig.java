package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * NudeCosplayGirls (nudecosplaygirls.com) - WordPress 成人 Cosplay 站
 */
public class NudeCosplayGirlsConfig extends WordPressConfig {

    @Override
    public String getWebsiteName() {
        return "NudeCosplayGirls";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_nudecosplaygirls;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_NUDECOSPLAYGIRLS;
    }

    @Override
    public String getSavedImageHead() {
        return "NudeCosplayGirls-";
    }
}
