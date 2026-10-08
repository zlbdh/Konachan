package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.HtmlParser;
import com.ess.anime.wallpaper.website.parser.NHentaiParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * nhentai.net: official v2 REST API, no key required
 *
 * The API uses a verified compact format rather than the old images.pages format:
 * - List: GET /api/v2/galleries?page=N → {result:[...], num_pages}
 *   Single gallery: {id, media_id, english_title, thumbnail:"galleries/{mid}/thumb.jpg.webp",
 *            thumbnail_width/height, num_pages, num_favorites, tag_ids, blacklisted}
 * - By tag: GET /api/v2/galleries/tagged?tag_id={id}&page=N, using the same format
 * - Search: GET /api/v2/search?query={q}&page=N, using the same format
 * - Details: GET /api/v2/galleries/{id} → {title:{english,pretty}, cover, thumbnail,
 *            scanlator, upload_date (epoch seconds), tags:[{id,type,name}], num_pages,
 *            pages:[{number, path:"galleries/{mid}/N.jpg", width, height, ...}]}
 * - Direct URL: https://i{N}.nhentai.net/{path}, where N is 1–4 and path includes the actual extension
 */
public class NHentaiConfig extends WebsiteConfig<NHentaiParser> {

    public final static String BASE_URL_NHENTAI = "https://nhentai.net/";

    private NHentaiParser mParser;

    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new NHentaiParser(this);
        }
        return mParser;
    }

    @Override
    public String getWebsiteName() {
        return "nhentai";
    }

    @Override
    public int getWebsiteLogoRes() {
        // The parent agent creates the ic_website_nhentai icon during integration
        return R.drawable.ic_website_nhentai;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_NHENTAI;
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
    public List<String> parseSearchAutoCompleteListFromTagJson(String search) {
        return Collections.emptyList();
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        if (tagList == null) {
            tagList = new ArrayList<>();
        }
        String tag = null;
        for (String t : tagList) {
            if (!TextUtils.isEmpty(t)) {
                tag = t.trim();
                break;
            }
        }
        if (TextUtils.isEmpty(tag)) {
            return getBaseUrl() + "api/v2/galleries?page=" + page;
        }
        if (tag.matches("\\d+")) {
            // Treat a numeric query as a tag ID and use the tagged endpoint
            return getBaseUrl() + "api/v2/galleries/tagged?tag_id=" + tag + "&page=" + page;
        }
        return getBaseUrl() + "api/v2/search?query=" + encodeTag(tag) + "&page=" + page;
    }

    @Override
    public String getPopularDailyUrl(int year, int month, int day, int page) {
        return getPostUrl(page, null);
    }

    @Override
    public String getPopularWeeklyUrl(int year, int month, int day, int page) {
        return getPostUrl(page, null);
    }

    @Override
    public String getPopularMonthlyUrl(int year, int month, int day, int page) {
        return getPostUrl(page, null);
    }

    @Override
    public String getPopularOverallUrl(int year, int month, int day, int page) {
        return getPostUrl(page, null);
    }

    @Override
    public String getPostDetailUrl(String id) {
        // The app retrieves details through linkToShow; return an API URL because the parser expects JSON
        return getBaseUrl() + "api/v2/galleries/" + id;
    }

    /** Web gallery URL for the source field and user viewing */
    public String getGalleryWebUrl(String id) {
        return getBaseUrl() + "g/" + id + "/";
    }

    @Override
    public String getCommentUrl(String id) {
        return null;
    }

    @Override
    public boolean hasPool() {
        return false;
    }

    @Override
    public String getPoolUrl(int page, String name) {
        return null;
    }

    @Override
    public String getPoolPostUrl(String linkToShow, int page) {
        return null;
    }

    @Override
    public boolean needReloadDetailByIdForPoolPost() {
        return false;
    }

    @Override
    public String getSavedImageHead() {
        return "nhentai-";
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
    public boolean isSupportSearchAutoCompleteFromNetwork() {
        return false;
    }

    @Override
    public String getSearchAutoCompleteUrl(String tag) {
        return null;
    }

    @Override
    public List<String> parseSearchAutoCompleteListFromNetwork(String promptResult, String search) {
        return Collections.emptyList();
    }
}
