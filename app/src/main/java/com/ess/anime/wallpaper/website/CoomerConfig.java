package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.CoomerParser;
import com.ess.anime.wallpaper.website.parser.HtmlParser;

import java.util.Collections;
import java.util.List;

/**
 * Coomer (coomer.st): OnlyFans/Fansly/Patreon leaks aggregator.
 *
 * API: GET https://coomer.st/api/v1/posts?limit=42&o={offset}
 * IMPORTANT: Must send "Accept: text/css" header, otherwise the API returns
 * a scrape warning instead of JSON (anti-bot measure).
 * Response: {"count": N, "posts": [{id, service, title, file: {path}, attachments: [{path, name}]}]}
 * Image URL: https://coomer.st/data{path} (redirects to n*.coomer.st CDN)
 *
 * Pagination: offset-based (o = (page-1) * limit).
 * No search API; tags are ignored for this site.
 */
public class CoomerConfig extends WebsiteConfig<CoomerParser> {

    public final static String BASE_URL_COOMER = "https://coomer.st/";

    private static final String API_BASE = "https://coomer.st/api/v1/posts";
    private static final int PAGE_LIMIT = 42;

    private CoomerParser mParser;

    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new CoomerParser(this);
        }
        return mParser;
    }

    @Override
    public String getWebsiteName() {
        return "Coomer";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_coomer;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_COOMER;
    }

    @Override
    public boolean hasTagJson() {
        return false;
    }

    @Override
    public String getTagJsonUrl() {
        return null;
    }

    @Override
    public boolean isSupportRandomPost() {
        return false;
    }

    @Override
    public boolean isSupportAdvancedSearch() {
        return false;
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        int offset = (page - 1) * PAGE_LIMIT;
        return API_BASE + "?limit=" + PAGE_LIMIT + "&o=" + offset;
    }

    @Override
    public List<String> parseSearchAutoCompleteListFromNetwork(String promptResult, String search) {
        return Collections.emptyList();
    }

    @Override
    public java.util.Map<String, String> getRequestHeaders() {
        java.util.Map<String, String> headers = super.getRequestHeaders();
        if (headers == null) {
            headers = new java.util.HashMap<>();
        }
        // Required by Coomer API anti-bot measure
        headers.put("Accept", "text/css");
        return headers;
    }
}
