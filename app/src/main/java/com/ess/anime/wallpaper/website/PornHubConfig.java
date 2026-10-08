package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;

import java.util.List;

/**
 * PornHub（pornhub.com）- 官方 webmasters API，无需 key
 * GET /webmasters/search?search={q}&page={p}&thumbsize=medium
 */
public class PornHubConfig extends TubeConfig {

    @Override
    public String getWebsiteName() {
        return "PornHub";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_pornhub;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_PORNHUB;
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        return "https://www.pornhub.com/webmasters/search?search="
                + joinTags(tagList) + "&page=" + page + "&thumbsize=medium";
    }

    @Override
    public String getSavedImageHead() {
        return "PornHub-";
    }
}
