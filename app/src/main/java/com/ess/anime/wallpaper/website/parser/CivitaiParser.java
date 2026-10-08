package com.ess.anime.wallpaper.website.parser;

import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.CivitaiConfig;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Civitai API 解析器
 *
 * 响应格式：
 * { items: [{id, url（image.civitai.com 直链）, width, height,
 *            nsfwLevel, username, postId, meta:{prompt}}],
 *   metadata: {nextCursor} }
 *
 * url 即原图直链，无需第二跳；解析完把 metadata.nextCursor 回填给
 * CivitaiConfig，供下一页翻页使用。
 */
public class CivitaiParser extends HtmlParser {

    public CivitaiParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    private CivitaiConfig getCivitaiConfig() {
        return (CivitaiConfig) mWebsiteConfig;
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            JsonObject root = new JsonParser().parse(doc.text()).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) {
                return thumbList;
            }
            for (int i = 0; i < items.size(); i++) {
                try {
                    JsonObject item = items.get(i).getAsJsonObject();
                    String id = getAsString(item, "id");
                    String url = getAsString(item, "url");
                    if (TextUtils.isEmpty(id) || TextUtils.isEmpty(url)) {
                        continue;
                    }
                    int width = getAsInt(item, "width", 0);
                    int height = getAsInt(item, "height", 0);
                    String author = getAsString(item, "username");
                    String nsfwLevel = getAsString(item, "nsfwLevel");

                    // prompt 藏在 meta 对象里，拿不到也不影响展示
                    String prompt = "";
                    try {
                        JsonObject meta = item.getAsJsonObject("meta");
                        if (meta != null) {
                            prompt = getAsString(meta, "prompt");
                        }
                    } catch (Exception ignore) {
                    }

                    String realSize = (width > 0 && height > 0)
                            ? width + " x " + height : "";
                    String linkToShow = mWebsiteConfig.getPostDetailUrl(id);
                    ThumbBean thumbBean = new ThumbBean(id, width, height,
                            url, realSize, linkToShow);
                    // 列表数据已足够，直接拼出 ImageBean，详情页不再二次请求
                    thumbBean.imageBean = buildImageBean(id, url, width, height,
                            author, prompt, nsfwLevel);
                    thumbList.add(thumbBean);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // 回填 nextCursor 供下一页使用
            try {
                JsonObject metadata = root.getAsJsonObject("metadata");
                if (metadata != null) {
                    getCivitaiConfig().saveNextCursor(getAsString(metadata, "nextCursor"));
                }
            } catch (Exception ignore) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return thumbList;
    }

    private ImageBean buildImageBean(String id, String url, int width, int height,
                                     String author, String prompt, String nsfwLevel) {
        ImageBean.ImageJsonBuilder builder = new ImageBean.ImageJsonBuilder();
        builder.id(id)
                .tags(prompt == null ? "" : prompt)
                .author(author == null ? "" : author)
                .source(mWebsiteConfig.getPostDetailUrl(id))
                .md5("")
                .fileUrl(url)
                .width(String.valueOf(width))
                .height(String.valueOf(height))
                .fileSize("-1")
                .previewUrl(url)
                .previewWidth(String.valueOf(width))
                .previewHeight(String.valueOf(height))
                .sampleUrl(url)
                .sampleWidth(String.valueOf(width))
                .sampleHeight(String.valueOf(height))
                .sampleFileSize("-1")
                .jpegUrl(url)
                .jpegWidth(String.valueOf(width))
                .jpegHeight(String.valueOf(height))
                .jpegFileSize("-1")
                .rating("e")  // 本站只拉 nsfw=true 的内容
                .hasChildren("false")
                .parentId("");
        return ImageBean.getImageDetailFromJson(builder.build());
    }

    @Override
    public String getImageDetailJson(Document doc) {
        // 详情数据已在 getThumbList 里拼好（thumbBean.imageBean），这里兜底返回空
        return "";
    }

    @Override
    public List<CommentBean> getCommentList(Document doc) {
        return new ArrayList<>();
    }

    @Override
    public List<PoolListBean> getPoolListList(Document doc) {
        return new ArrayList<>();
    }

    /** 容错取字符串：兼容数字/布尔/null */
    private static String getAsString(JsonObject obj, String key) {
        try {
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) {
                return "";
            }
            if (e.isJsonPrimitive()) {
                return e.getAsString();
            }
            return "";
        } catch (Exception ignore) {
            return "";
        }
    }

    /** 容错取整数 */
    private static int getAsInt(JsonObject obj, String key, int defValue) {
        try {
            JsonElement e = obj.get(key);
            if (e == null || e.isJsonNull()) {
                return defValue;
            }
            return e.getAsInt();
        } catch (Exception ignore) {
            return defValue;
        }
    }
}
