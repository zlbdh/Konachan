package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.MyApp;
import com.ess.anime.wallpaper.utils.FileUtils;
import com.ess.anime.wallpaper.website.parser.HtmlParser;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.util.List;

public abstract class WebsiteConfig<T extends HtmlParser> {

    public final static String BASE_URL_BAIDU = "https://baike.baidu.com/item/";
    public final static String BASE_URL_KONACHAN_S = "https://konachan.net/";
    public final static String BASE_URL_KONACHAN_E = "https://konachan.com/";
    public final static String BASE_URL_YANDE = "https://yande.re/";
    public final static String BASE_URL_DANBOORU = "https://danbooru.donmai.us/";
    public final static String BASE_URL_GELBOORU = "https://gelbooru.com/";
    public final static String BASE_URL_SANKAKU = "https://sankakuapi.com/v2/";  // https://chan.sankakucomplex.com/
    public final static String BASE_URL_XBOORU = "https://xbooru.com/";
    public final static String BASE_URL_TBIB = "https://tbib.org/";
    public final static String BASE_URL_E621 = "https://e621.net/";
    public final static String BASE_URL_RULE34 = "https://rule34.xxx/";
    public final static String BASE_URL_HYPNO = "https://hypnohub.net/";
    public final static String BASE_URL_YUKKURI = "https://yukkuri.shiteitte.net/";
    public final static String BASE_URL_AIBOORU = "https://aibooru.online/";
    public final static String BASE_URL_SONOHARA = "https://sonohara.donmai.us/";
    public final static String BASE_URL_HIJIRIBE = "https://hijiribe.donmai.us/";
    public final static String BASE_URL_MISSKON = "https://misskon.com/";
    public final static String BASE_URL_FOURKHD = "https://www.4khd.com/";
    public final static String BASE_URL_NUDECOSPLAYGIRLS = "https://nudecosplaygirls.com/";
    public final static String BASE_URL_COSPLAY3X = "https://cosplay3x.com/";
    public final static String BASE_URL_ASIANPINK = "https://asianpink.net/";
    public final static String BASE_URL_ADULTCOMIXXX = "https://adultcomixxx.com/";
    public final static String BASE_URL_CIVITAI = "https://civitai.com/";
    public final static String BASE_URL_PORNHUB = "https://www.pornhub.com/";
    public final static String BASE_URL_REDTUBE = "https://www.redtube.com/";
    public final static String BASE_URL_EPORNER = "https://www.eporner.com/";
    public final static String BASE_URL_NHENTAI = "https://nhentai.net/";
    public final static String BASE_URL_HITOMI = "https://hitomi.la/";

    public final static String TAG_JSON_URL_KONACHAN_S = "https://konachan.net/tag/summary.json";
    public final static String TAG_JSON_URL_KONACHAN_E = "https://konachan.com/tag/summary.json";
    public final static String TAG_JSON_URL_YANDE = "https://yande.re/tag/summary.json";

    public final static String[] BASE_URLS = {
            BASE_URL_KONACHAN_S, BASE_URL_KONACHAN_E, BASE_URL_YANDE, BASE_URL_DANBOORU,
            BASE_URL_GELBOORU, BASE_URL_SANKAKU,
            BASE_URL_XBOORU, BASE_URL_TBIB, BASE_URL_E621,
            BASE_URL_RULE34, BASE_URL_HYPNO, BASE_URL_YUKKURI,
            BASE_URL_AIBOORU, BASE_URL_SONOHARA, BASE_URL_HIJIRIBE,
            BASE_URL_MISSKON, BASE_URL_FOURKHD,
            BASE_URL_NUDECOSPLAYGIRLS, BASE_URL_COSPLAY3X, BASE_URL_ASIANPINK,
            BASE_URL_ADULTCOMIXXX, BASE_URL_CIVITAI, BASE_URL_PORNHUB,
            BASE_URL_REDTUBE, BASE_URL_EPORNER, BASE_URL_NHENTAI,
            BASE_URL_HITOMI
    };

    protected String mTagJson;

    private T mHtmlParser;

    public WebsiteConfig() {
        try {
            ParameterizedType type = (ParameterizedType) getClass().getGenericSuperclass();
            Class<T> clazz = (Class<T>) type.getActualTypeArguments()[0];
            Constructor constructor = clazz.getConstructor(WebsiteConfig.class);
            mHtmlParser = (T) constructor.newInstance(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Site name
    public abstract String getWebsiteName();

    // Site icon used in the menu
    public abstract int getWebsiteLogoRes();

    // Site content parser
    public HtmlParser getHtmlParser() {
        return mHtmlParser;
    }

    // Site domain
    public abstract String getBaseUrl();

    // Whether the site provides a dedicated search suggestion JSON file
    public abstract boolean hasTagJson();

    // Search suggestion JSON URL
    public abstract String getTagJsonUrl();

    // Store the tag JSON file
    public void saveTagJson(String key, String json) {
        synchronized (WebsiteConfig.class) {
            String dir = MyApp.getInstance().getFilesDir().getPath();
            String name = FileUtils.encodeMD5String(key);
            File file = new File(dir, name);
            FileUtils.stringToFile(json, file);
        }
    }

    // Get tag JSON content
    public String getTagJson() {
        synchronized (WebsiteConfig.class) {
            if (TextUtils.isEmpty(mTagJson)) {
                String dir = MyApp.getInstance().getFilesDir().getPath();
                String name = FileUtils.encodeMD5String(getTagJsonUrl());
                File file = new File(dir, name);
                if (file.exists() && file.isFile()) {
                    mTagJson = FileUtils.fileToString(file);
                }
            }
            return mTagJson == null ? "" : mTagJson;
        }
    }

    // Parse search suggestions from tag JSON
    public abstract List<String> parseSearchAutoCompleteListFromTagJson(String search);

    // Search images by tags
    public abstract String getPostUrl(int page, List<String> tagList);

    // URL-encode search tags; unencoded Chinese or special characters would create invalid URLs
    protected static String encodeTag(String tag) {
        try {
            return java.net.URLEncoder.encode(tag, "UTF-8");
        } catch (Exception e) {
            return tag;
        }
    }

    // Search daily rankings
    public abstract String getPopularDailyUrl(int year, int month, int day, int page);

    // Search weekly rankings
    public abstract String getPopularWeeklyUrl(int year, int month, int day, int page);

    // Search monthly rankings
    public abstract String getPopularMonthlyUrl(int year, int month, int day, int page);

    // Search all-time rankings
    public abstract String getPopularOverallUrl(int year, int month, int day, int page);

    // Get image details by ID
    public abstract String getPostDetailUrl(String id);

    // Get image comments by ID
    public abstract String getCommentUrl(String id);

    // Whether an album list is available
    public abstract boolean hasPool();

    // Search albums
    public abstract String getPoolUrl(int page, String name);

    // Search images within an album
    public abstract String getPoolPostUrl(String linkToShow, int page);

    // Whether parsing PoolPost requires another Post request by postId
    public abstract boolean needReloadDetailByIdForPoolPost();

    // Saved image filename prefix
    public abstract String getSavedImageHead();

    // Whether random browsing is supported
    public abstract boolean isSupportRandomPost();

    // Whether to disable Glide disk cache (for signed URLs that expire, e.g. tube sites)
    public boolean isDisableDiskCache() {
        return false;
    }

    // Whether advanced search is supported
    public abstract boolean isSupportAdvancedSearch();

    // Whether network-based search suggestions are supported
    public abstract boolean isSupportSearchAutoCompleteFromNetwork();

    // Dynamic search suggestion request URL
    public abstract String getSearchAutoCompleteUrl(String tag);

    // Parse search suggestions from the network response
    public abstract List<String> parseSearchAutoCompleteListFromNetwork(String promptResult, String search);

}
