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
    public final static String BASE_URL_COSBLAY = "https://cosblay.com/";
    public final static String BASE_URL_NUDECOSPLAYGIRLS = "https://nudecosplaygirls.com/";
    public final static String BASE_URL_COSPLAY3X = "https://cosplay3x.com/";
    public final static String BASE_URL_ASIANPINK = "https://asianpink.net/";
    public final static String BASE_URL_ADULTCOMIXXX = "https://adultcomixxx.com/";
    public final static String BASE_URL_CIVITAI = "https://civitai.com/";
    public final static String BASE_URL_PORNHUB = "https://www.pornhub.com/";
    public final static String BASE_URL_REDTUBE = "https://www.redtube.com/";
    public final static String BASE_URL_EPORNER = "https://www.eporner.com/";

    public final static String TAG_JSON_URL_KONACHAN_S = "https://konachan.net/tag/summary.json";
    public final static String TAG_JSON_URL_KONACHAN_E = "https://konachan.com/tag/summary.json";
    public final static String TAG_JSON_URL_YANDE = "https://yande.re/tag/summary.json";

    public final static String[] BASE_URLS = {
            BASE_URL_KONACHAN_S, BASE_URL_KONACHAN_E, BASE_URL_YANDE, BASE_URL_DANBOORU,
            BASE_URL_GELBOORU, BASE_URL_SANKAKU,
            BASE_URL_XBOORU, BASE_URL_TBIB, BASE_URL_E621,
            BASE_URL_RULE34, BASE_URL_HYPNO, BASE_URL_YUKKURI,
            BASE_URL_AIBOORU, BASE_URL_SONOHARA, BASE_URL_HIJIRIBE,
            BASE_URL_MISSKON, BASE_URL_FOURKHD, BASE_URL_COSBLAY,
            BASE_URL_NUDECOSPLAYGIRLS, BASE_URL_COSPLAY3X, BASE_URL_ASIANPINK,
            BASE_URL_ADULTCOMIXXX, BASE_URL_CIVITAI, BASE_URL_PORNHUB,
            BASE_URL_REDTUBE, BASE_URL_EPORNER
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

    // 网站名
    public abstract String getWebsiteName();

    // 用于menu icon的网站图标
    public abstract int getWebsiteLogoRes();

    // 爬虫解析器
    public HtmlParser getHtmlParser() {
        return mHtmlParser;
    }

    // 网站域名
    public abstract String getBaseUrl();

    // 是否有明确的搜索提示Json文件
    public abstract boolean hasTagJson();

    // 搜索提示的Json文件地址
    public abstract String getTagJsonUrl();

    // 存储TagJson文件
    public void saveTagJson(String key, String json) {
        synchronized (WebsiteConfig.class) {
            String dir = MyApp.getInstance().getFilesDir().getPath();
            String name = FileUtils.encodeMD5String(key);
            File file = new File(dir, name);
            FileUtils.stringToFile(json, file);
        }
    }

    // 获取TagJson内容
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

    // 从TagJson解析搜索提示
    public abstract List<String> parseSearchAutoCompleteListFromTagJson(String search);

    // 通过tags搜索图片
    public abstract String getPostUrl(int page, List<String> tagList);

    // 搜索标签 URL 编码（中文/特殊字符不编码会构造出非法 URL）
    protected static String encodeTag(String tag) {
        try {
            return java.net.URLEncoder.encode(tag, "UTF-8");
        } catch (Exception e) {
            return tag;
        }
    }

    // 搜索日榜图片
    public abstract String getPopularDailyUrl(int year, int month, int day, int page);

    // 搜索周榜图片
    public abstract String getPopularWeeklyUrl(int year, int month, int day, int page);

    // 搜索月榜图片
    public abstract String getPopularMonthlyUrl(int year, int month, int day, int page);

    // 搜索总榜图片
    public abstract String getPopularOverallUrl(int year, int month, int day, int page);

    // 通过id搜索图片详情
    public abstract String getPostDetailUrl(String id);

    // 通过id搜索图片评论
    public abstract String getCommentUrl(String id);

    // 是否有图集列表
    public abstract boolean hasPool();

    // 搜索图集
    public abstract String getPoolUrl(int page, String name);

    // 搜索图集中的图片
    public abstract String getPoolPostUrl(String linkToShow, int page);

    // 解析PoolPost之后是否需要根据postId重新进行一次Post查询
    public abstract boolean needReloadDetailByIdForPoolPost();

    // 保存图片名前缀
    public abstract String getSavedImageHead();

    // 是否支持随机看图
    public abstract boolean isSupportRandomPost();

    // 是否支持高级搜索
    public abstract boolean isSupportAdvancedSearch();

    // 是否支持网络解析搜索提示
    public abstract boolean isSupportSearchAutoCompleteFromNetwork();

    // 动态请求搜索提示的网址
    public abstract String getSearchAutoCompleteUrl(String tag);

    // 根据网络请求返回内容解析搜索提示
    public abstract List<String> parseSearchAutoCompleteListFromNetwork(String promptResult, String search);

}
