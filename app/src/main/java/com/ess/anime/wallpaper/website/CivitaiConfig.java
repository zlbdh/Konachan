package com.ess.anime.wallpaper.website;

import android.text.TextUtils;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.CivitaiParser;
import com.ess.anime.wallpaper.website.parser.HtmlParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Civitai (civitai.com) - AI 色图站
 *
 * API: GET https://civitai.com/api/v1/images?limit=20&nsfw=true&sort=Newest
 * 无需 key，匿名可拿 NSFW 内容。
 * 响应：{ items: [{id, url, width, height, nsfwLevel, username, meta:{prompt}, postId}],
 *         metadata: {nextCursor} }
 *
 * 分页说明：Civitai 用 cursor 分页（metadata.nextCursor），不支持传统 page 参数。
 * 本类维护一个 cursor 列表实现顺序翻页：mCursors.get(i) 是请求第 i+2 页时要带的
 * cursor（即第 i+1 页响应返回的 nextCursor）。page=1（新搜索/刷新）时清空重置。
 * 跳页、回退重进等非顺序场景会退化为无 cursor 请求（API 返回第一页），属已知限制。
 */
public class CivitaiConfig extends WebsiteConfig<CivitaiParser> {

    public final static String BASE_URL_CIVITAI = "https://civitai.com/";

    private static final String API_BASE = "https://civitai.com/api/v1/images";
    private static final int PAGE_LIMIT = 42;

    private CivitaiParser mParser;

    /** 分页 cursor 列表，mCursors.get(i) 对应第 i+2 页的 cursor */
    private final List<String> mCursors = new ArrayList<>();

    /**
     * 直接创建 Parser，绕过父类构造函数的泛型反射
     *（二级继承时 superclass 不是 ParameterizedType，反射拿不到 Parser 类型）
     */
    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new CivitaiParser(this);
        }
        return mParser;
    }

    @Override
    public String getWebsiteName() {
        return "Civitai";
    }

    @Override
    public int getWebsiteLogoRes() {
        // TODO: 正式图标由 parent 生成后替换（当前用 AIBooru 图标占位）
        return R.drawable.ic_website_civitai;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_CIVITAI;
    }

    /**
     * Parser 解析完一页后回填 nextCursor，供下一页翻页使用。
     * 刷新同一页会返回相同 cursor，做去重避免列表无限增长。
     */
    public synchronized void saveNextCursor(String cursor) {
        if (TextUtils.isEmpty(cursor)) {
            return;
        }
        if (!mCursors.isEmpty()
                && TextUtils.equals(mCursors.get(mCursors.size() - 1), cursor)) {
            return;
        }
        mCursors.add(cursor);
    }

    @Override
    public String getPostUrl(int page, List<String> tagList) {
        if (tagList == null) {
            tagList = new ArrayList<>();
        }
        StringBuilder tags = new StringBuilder();
        for (String tag : tagList) {
            if (!TextUtils.isEmpty(tag)) {
                tags.append(encodeTag(tag)).append(" ");
            }
        }
        StringBuilder url = new StringBuilder(API_BASE)
                .append("?limit=").append(PAGE_LIMIT)
                .append("&nsfw=true&sort=Newest");
        String query = tags.toString().trim();
        if (!TextUtils.isEmpty(query)) {
            url.append("&query=").append(query);
        }
        synchronized (this) {
            if (page <= 1) {
                // 新搜索 / 下拉刷新：重置分页状态
                mCursors.clear();
            } else {
                int index = page - 2;
                if (index >= 0 && index < mCursors.size()) {
                    url.append("&cursor=").append(encodeTag(mCursors.get(index)));
                }
                // cursor 缺失（非顺序翻页）时不带 cursor 参数，
                // API 会返回第一页，属已知限制，见类注释
            }
        }
        return url.toString();
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
        return BASE_URL_CIVITAI + "images/" + id;
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
        return "Civitai-";
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
