package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.EHentaiParser;
import com.ess.anime.wallpaper.website.parser.HtmlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * E-Hentai (e-hentai.org): HTML scraping, no key required.
 *
 * List:   GET {base}/?f_search={q}&page={n}  (thumbnail view, div.glthumb)
 * Detail: GET {base}/g/{gid}/{token}/
 * Images: GET {base}/s/{imgtoken}/{gid}-{pagenum} -> img#img[src]
 *
 * With two-level inheritance (ExHentaiConfig extends this), the generic
 * reflection in WebsiteConfig's constructor cannot resolve the parser type,
 * so getHtmlParser() is overridden to create the parser directly.
 */
public class EHentaiConfig extends WebsiteConfig<EHentaiParser> {

    public final static String BASE_URL_EHENTAI = "https://e-hentai.org/";

    private EHentaiParser mParser;

    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new EHentaiParser(this);
        }
        return mParser;
    }

    /** EHentai request helper (cookies, headers). Subclasses may override the host. */
    public EHentaiRequest getRequest(android.content.Context context) {
        return EHentaiRequest.getInstance(context);
    }

    @Override
    public String getWebsiteName() {
        return "EHentai";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_ehentai;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_EHENTAI;
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
    public boolean isSupportSearchAutoCompleteFromNetwork() {
        return true;
    }

    @Override
    public String getSearchAutoCompleteUrl(String tag) {
        if (TextUtils.isEmpty(tag)) {
            return null;
        }
        return getBaseUrl() + "tagsuggest.php?text=" + encodeTag(tag.trim());
    }

    @Override
    public List<String> parseSearchAutoCompleteListFromNetwork(String promptResult, String search) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(promptResult)) {
            return result;
        }
        try {
            // tagsuggest.php returns a simple HTML/text list; extract quoted suggestions
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(promptResult);
            for (org.jsoup.nodes.Element el : doc.select("a")) {
                String t = el.text().trim();
                if (!TextUtils.isEmpty(t)) {
                    result.add(t);
                }
            }
            if (result.isEmpty()) {
                for (String line : promptResult.split("\n")) {
                    line = line.trim();
                    if (!TextUtils.isEmpty(line) && line.length() < 80) {
                        result.add(line);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        if (tagList == null) {
            tagList = new ArrayList<>();
        }
        StringBuilder q = new StringBuilder();
        for (String tag : tagList) {
            if (!TextUtils.isEmpty(tag)) {
                if (q.length() > 0) {
                    q.append(' ');
                }
                q.append(tag.trim());
            }
        }
        StringBuilder url = new StringBuilder(getBaseUrl());
        // E-Hentai page index is 0-based
        int p = Math.max(0, page - 1);
        if (q.length() == 0) {
            // homepage / popular
            if (p == 0) {
                return url.toString();
            }
            url.append("?page=").append(p);
        } else {
            url.append("?f_search=").append(encodeTag(q.toString()));
            if (p > 0) {
                url.append("&page=").append(p);
            }
        }
        return url.toString();
    }

    /** Ranklist URL: tl=15 (yesterday) / tl=11 (past month). */
    public String getRanklistUrl(boolean monthly, int page) {
        int p = Math.max(0, page - 1);
        String url = getBaseUrl() + "toplist.php?tl=" + (monthly ? "11" : "15");
        if (p > 0) {
            url += "&p=" + p;
        }
        return url;
    }

    @Override
    public String getPostDetailUrl(String id) {
        // id is "gid/token" as built by the parser's linkToShow
        if (!TextUtils.isEmpty(id) && id.contains("/")) {
            return getBaseUrl() + "g/" + id + "/";
        }
        return getBaseUrl();
    }

    @Override
    public String getPopularDailyUrl(int year, int month, int day, int page) {
        return getRanklistUrl(false, page);
    }

    @Override
    public String getPopularWeeklyUrl(int year, int month, int day, int page) {
        return getRanklistUrl(false, page);
    }

    @Override
    public String getPopularMonthlyUrl(int year, int month, int day, int page) {
        return getRanklistUrl(true, page);
    }

    @Override
    public String getPopularOverallUrl(int year, int month, int day, int page) {
        return getRanklistUrl(true, page);
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
        return "ehentai-";
    }

    @Override
    public boolean isSupportRandomPost() {
        return false;
    }

    @Override
    public boolean isSupportAdvancedSearch() {
        return true;
    }

    @Override
    public String getImageReferer() {
        return getBaseUrl();
    }
}
