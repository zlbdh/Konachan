package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

/**
 * 4KHD (www.4khd.com): WordPress adult cosplay site
 *
 * Direct /wp-json/ requests return 301; use rest_route for the REST API.
 */
public class FourkhdConfig extends WordPressConfig {

    public final static String BASE_URL_FOURKHD = "https://www.4khd.com/";

    @Override
    protected String getRestBase() {
        return getBaseUrl() + "index.php?rest_route=/wp/v2/";
    }

    @Override
    public String getPostUrl(int page, java.util.List<String> tagList) {
        // In rest_route mode, join parameters with & rather than another ?
        if (tagList == null) {
            tagList = new java.util.ArrayList<>();
        }
        StringBuilder tags = new StringBuilder();
        for (String tag : tagList) {
            if (!android.text.TextUtils.isEmpty(tag)) {
                tags.append(encodeTag(tag)).append(" ");
            }
        }
        return getRestBase() + "posts&per_page=42&page=" + page
                + "&search=" + tags.toString().trim()
                + "&_embed=wp:featuredmedia";
    }

    @Override
    public String getMediaUrl(int mediaId) {
        return getRestBase() + "media/" + mediaId;
    }

    @Override
    public String getWebsiteName() {
        return "4KHD";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_fourkhd;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_FOURKHD;
    }

    @Override
    public String getSavedImageHead() {
        return "4KHD-";
    }
}
