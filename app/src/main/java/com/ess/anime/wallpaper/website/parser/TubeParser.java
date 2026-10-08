package com.ess.anime.wallpaper.website.parser;

import android.text.Html;
import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.TubeConfig;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * 成人视频 tube 站解析器（PornHub / RedTube / Eporner）
 *
 * 三家官方 API 都返回视频列表 + 多尺寸缩略图数组，直接当图片源浏览。
 * 缩略图策略：从 thumbs[] 里选面积最大的；取不到时用 thumb / default_thumb 兜底。
 * ImageBean 里 fileUrl/preview/sample 都用最大缩略图（视频站没有"原图"概念，
 * 最大缩略图即为可下载的最高质量）。
 */
public class TubeParser extends HtmlParser {

    public TubeParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    private TubeConfig getTubeConfig() {
        return (TubeConfig) mWebsiteConfig;
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            String json = doc.text();
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonArray items = getTubeConfig().extractVideoItems(root);
            if (items == null) {
                return thumbList;
            }
            for (int i = 0; i < items.size(); i++) {
                try {
                    JsonObject item = items.get(i).getAsJsonObject();
                    JsonObject video = getTubeConfig().unwrapVideoItem(item);
                    if (video == null) {
                        continue;
                    }

                    String id = getTubeConfig().getVideoId(video);
                    if (TextUtils.isEmpty(id)) {
                        continue;
                    }

                    String title = "";
                    try {
                        title = Html.fromHtml(optString(video, "title")).toString().trim();
                    } catch (Exception ignore) {
                    }

                    // 选最大缩略图
                    String thumbUrl = pickBestThumb(video);
                    if (TextUtils.isEmpty(thumbUrl)) {
                        continue;
                    }
                    int thumbWidth = mBestThumbWidth;
                    int thumbHeight = mBestThumbHeight;

                    String videoUrl = optString(video, "url");
                    String duration = optString(video, "duration");
                    if (TextUtils.isEmpty(duration)) {
                        duration = optString(video, "length_min");
                    }
                    String views = optString(video, "views");
                    String realSize = (thumbWidth > 0 && thumbHeight > 0)
                            ? thumbWidth + " x " + thumbHeight : "";
                    String linkToShow = TextUtils.isEmpty(videoUrl)
                            ? mWebsiteConfig.getPostDetailUrl(id) : videoUrl;

                    ThumbBean thumbBean = new ThumbBean(id, thumbWidth, thumbHeight,
                            thumbUrl, realSize, linkToShow);
                    thumbBean.imageBean = buildImageBean(id, title, videoUrl, duration,
                            views, thumbUrl, thumbWidth, thumbHeight);
                    thumbList.add(thumbBean);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return thumbList;
    }

    private int mBestThumbWidth;
    private int mBestThumbHeight;

    /** 从 thumbs[] 选面积最大的；取不到时用 thumb / default_thumb 兜底 */
    private String pickBestThumb(JsonObject video) {
        mBestThumbWidth = 0;
        mBestThumbHeight = 0;
        String bestUrl = "";
        int bestArea = 0;
        try {
            JsonArray thumbs = video.getAsJsonArray("thumbs");
            if (thumbs != null) {
                for (int i = 0; i < thumbs.size(); i++) {
                    try {
                        JsonObject t = thumbs.get(i).getAsJsonObject();
                        String src = optString(t, "src");
                        if (TextUtils.isEmpty(src)) {
                            continue;
                        }
                        int w = optInt(t, "width");
                        int h = optInt(t, "height");
                        int area = w * h;
                        if (area >= bestArea) {
                            bestArea = area;
                            bestUrl = src;
                            mBestThumbWidth = w;
                            mBestThumbHeight = h;
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
        } catch (Exception ignore) {
        }
        if (TextUtils.isEmpty(bestUrl)) {
            bestUrl = optString(video, "thumb");
        }
        if (TextUtils.isEmpty(bestUrl)) {
            bestUrl = optString(video, "default_thumb");
        }
        return bestUrl;
    }

    private ImageBean buildImageBean(String id, String title, String videoUrl,
                                     String duration, String views,
                                     String url, int width, int height) {
        ImageBean.ImageJsonBuilder builder = new ImageBean.ImageJsonBuilder();
        StringBuilder tags = new StringBuilder();
        if (!TextUtils.isEmpty(title)) {
            tags.append(title);
        }
        if (!TextUtils.isEmpty(duration)) {
            tags.append(" [").append(duration).append("]");
        }
        builder.id(id)
                .tags(tags.toString())
                .author(getTubeConfig().getWebsiteName())
                .source(videoUrl)
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
                .rating("e")
                .hasChildren("false")
                .parentId("");
        return ImageBean.getImageDetailFromJson(builder.build());
    }

    /** 安全取字符串（兼容数字/布尔类型） */
    private static String optString(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull()) {
                return "";
            }
            if (e.isJsonPrimitive()) {
                return e.getAsString();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 安全取 int（兼容 "240" 字符串和 240 数字两种形式） */
    private static int optInt(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull()) {
                return 0;
            }
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) {
                return p.getAsInt();
            }
            return Integer.parseInt(p.getAsString().trim());
        } catch (Exception e) {
            return 0;
        }
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
}
