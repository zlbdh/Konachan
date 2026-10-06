package com.ess.anime.wallpaper.website.parser;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Wallhalla 新站解析器（2026-10 重写）
 * <p>
 * 老站（HTML 抓取，/new、/search）已下线，新站为前后端分离，
 * 列表与详情统一走公开 JSON 接口：
 * - 列表：GET /api/catalogue/recent?page=&limit=
 * - 精选：GET /api/catalogue/best?page=&limit=
 * - 详情：GET /api/catalogue/item/{uuid}
 * 图片地址规则：{base}{width}.webp，如 /storage/wallpapers/variants/{uuid}/1/480.webp
 */
public class WallhallaParser extends HtmlParser {

    public WallhallaParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            String json = doc.text();
            JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
            JsonArray items = jsonObject.getAsJsonArray("items");
            for (int i = 0; i < items.size(); i++) {
                try {
                    JsonObject item = items.get(i).getAsJsonObject();
                    String uuid = item.get("uuid").getAsString();
                    // 站内永久编号（数字），用作 id 以保证 ImageBean JSON 合法（build() 中 id 为裸拼接）
                    String id = String.valueOf(item.get("no").getAsInt());
                    int realWidth = item.get("width").getAsInt();
                    int realHeight = item.get("height").getAsInt();
                    String realSize = realWidth + " x " + realHeight;
                    int thumbWidth, thumbHeight;
                    if (realWidth >= realHeight) {
                        thumbWidth = 720;
                        thumbHeight = (int) (realHeight / 1f / realWidth * thumbWidth);
                    } else {
                        thumbHeight = 720;
                        thumbWidth = (int) (realWidth / 1f / realHeight * thumbHeight);
                    }
                    JsonObject images = item.getAsJsonObject("images");
                    String base = images.get("base").getAsString();
                    int thumbSize = images.getAsJsonArray("widths").get(0).getAsInt();
                    String thumbUrl = checkToWrapImageUrlHost(base + thumbSize + ".webp");
                    String linkToShow = mWebsiteConfig.getPostDetailUrl(uuid);
                    ThumbBean thumbBean = new ThumbBean(id, thumbWidth, thumbHeight, thumbUrl, realSize, linkToShow);
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

    @Override
    public String getImageDetailJson(Document doc) {
        ImageBean.ImageJsonBuilder builder = new ImageBean.ImageJsonBuilder();
        try {
            String json = doc.text();
            JsonObject item = new JsonParser().parse(json).getAsJsonObject().getAsJsonObject("item");

            // 用站内永久编号（数字）做 id，与 getThumbList 保持一致，且保证 ImageBean JSON 合法
            String id = String.valueOf(item.get("no").getAsInt());
            builder.id(id);

            // 时间：2026-10-01T17:41:27.187Z，注意 PostBean.createdTime 单位为 second
            String createdTime = String.valueOf(System.currentTimeMillis() / 1000);
            try {
                String createdAt = item.get("createdAt").getAsString();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(createdAt);
                if (date != null) {
                    createdTime = String.valueOf(date.getTime() / 1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            builder.createdTime(createdTime);

            // 作者可能为 null
            String author = getStringOrEmpty(item, "author");
            builder.creatorId(author).author(author);

            // 来源（如 Midjourney）
            builder.source(getStringOrEmpty(item, "source"));

            // 图片地址
            JsonObject images = item.getAsJsonObject("images");
            String base = images.get("base").getAsString();
            JsonArray widths = images.getAsJsonArray("widths");
            int thumbSize = widths.get(0).getAsInt();
            int fullSize = widths.get(widths.size() - 1).getAsInt();
            int midSize = widths.get(widths.size() / 2).getAsInt();
            String previewUrl = checkToWrapImageUrlHost(base + thumbSize + ".webp");
            String sampleUrl = checkToWrapImageUrlHost(base + midSize + ".webp");
            String fileUrl = checkToWrapImageUrlHost(base + fullSize + ".webp");
            String width = String.valueOf(item.get("width").getAsInt());
            String height = String.valueOf(item.get("height").getAsInt());
            String fileSize = "0";
            try {
                fileSize = String.valueOf(item.getAsJsonObject("original").get("bytes").getAsLong());
            } catch (Exception e) {
                e.printStackTrace();
            }
            builder.previewUrl(previewUrl)
                    .sampleUrl(sampleUrl)
                    .sampleWidth(String.valueOf(midSize))
                    .sampleHeight(String.valueOf((int) (item.get("height").getAsInt() / 1f / item.get("width").getAsInt() * midSize)))
                    .sampleFileSize("-1")
                    .fileUrl(fileUrl)
                    .fileSize(fileSize)
                    .jpegUrl(fileUrl)
                    .jpegWidth(width)
                    .jpegHeight(height)
                    .jpegFileSize(fileSize)
                    .width(width)
                    .height(height);

            // 该站为纯壁纸站，全部按 safe 处理
            builder.rating(Constants.RATING_S);

            // 防止为 null 的参数
            builder.score("0").md5("").parentId("");

            // 解析 tags
            StringBuilder tags = new StringBuilder();
            JsonArray tagArray = item.getAsJsonArray("tags");
            if (tagArray != null) {
                for (int i = 0; i < tagArray.size(); i++) {
                    try {
                        String tagName = tagArray.get(i).getAsString().trim().replace(" ", "_");
                        tags.append(tagName).append(" ");
                        builder.addGeneralTags(tagName);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            builder.tags(tags.toString().trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.build();
    }

    @Override
    public List<CommentBean> getCommentList(Document doc) {
        return null;
    }

    @Override
    public List<PoolListBean> getPoolListList(Document doc) {
        return null;
    }

    private String getStringOrEmpty(JsonObject obj, String key) {
        try {
            JsonElement e = obj.get(key);
            if (e != null && !e.isJsonNull()) {
                return e.getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String checkToWrapImageUrlHost(String url) {
        if (!url.startsWith("http")) {
            if (url.startsWith("/")) {
                url = url.substring(1);
            }
            url = mWebsiteConfig.getBaseUrl() + url;
        }
        return url;
    }
}
