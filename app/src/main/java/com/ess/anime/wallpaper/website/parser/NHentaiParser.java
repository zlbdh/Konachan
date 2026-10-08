package com.ess.anime.wallpaper.website.parser;

import android.text.Html;
import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.NHentaiConfig;
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

/**
 * nhentai.net parser for the official v2 REST JSON API
 *
 * List: result array, one ThumbBean per gallery, using the API thumbnail path;
 * details: pages array, with the first full-size page as fileUrl. ImageBean is a single-image model,
 * so only the first page is displayed or downloaded; full-gallery reading requires app-level changes.
 */
public class NHentaiParser extends HtmlParser {

    /** Image CDN; i1 through i4 are available */
    private static final String IMG_HOST = "https://i3.nhentai.net/";

    public NHentaiParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    private NHentaiConfig getNHConfig() {
        return (NHentaiConfig) mWebsiteConfig;
    }

    private static String imgUrl(String path) {
        return IMG_HOST + path;
    }

    /** Safely read strings, accepting both numbers and strings */
    private static String optString(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull()) {
                return "";
            }
            return e.getAsString();
        } catch (Exception ex) {
            return "";
        }
    }

    private static int optInt(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull()) {
                return 0;
            }
            return e.getAsInt();
        } catch (Exception ex) {
            return 0;
        }
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            String json = doc.text();
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("result");
            if (items == null) {
                return thumbList;
            }
            for (int i = 0; i < items.size(); i++) {
                try {
                    JsonObject g = items.get(i).getAsJsonObject();
                    try {
                        if (g.has("blacklisted") && g.get("blacklisted").getAsBoolean()) {
                            continue;
                        }
                    } catch (Exception ignore) {
                    }

                    String id = optString(g, "id");
                    if (TextUtils.isEmpty(id)) {
                        continue;
                    }

                    String thumbPath = optString(g, "thumbnail");
                    if (TextUtils.isEmpty(thumbPath)) {
                        continue;
                    }
                    String thumbUrl = imgUrl(thumbPath);
                    int thumbWidth = optInt(g, "thumbnail_width");
                    int thumbHeight = optInt(g, "thumbnail_height");

                    int numPages = optInt(g, "num_pages");
                    String realSize = numPages > 0 ? numPages + "P" : "";

                    String linkToShow = mWebsiteConfig.getPostDetailUrl(id);
                    ThumbBean thumbBean = new ThumbBean(id, thumbWidth, thumbHeight,
                            thumbUrl, realSize, linkToShow);
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
            JsonObject g = new JsonParser().parse(json).getAsJsonObject();
            String id = optString(g, "id");

            // Title
            String title = "";
            try {
                JsonObject t = g.getAsJsonObject("title");
                title = optString(t, "english");
                if (TextUtils.isEmpty(title)) {
                    title = optString(t, "pretty");
                }
            } catch (Exception ignore) {
            }
            if (TextUtils.isEmpty(title)) {
                title = optString(g, "english_title");
            }
            if (!TextUtils.isEmpty(title)) {
                title = Html.fromHtml(title).toString().trim();
            }

            // First full-size page; path includes the actual extension, such as galleries/4226053/1.jpg
            String fileUrl = "";
            int width = 0;
            int height = 0;
            java.util.List<String> allPageUrls = new java.util.ArrayList<>();
            try {
                JsonArray pages = g.getAsJsonArray("pages");
                if (pages != null && pages.size() > 0) {
                    for (int i = 0; i < pages.size(); i++) {
                        try {
                            String p = optString(pages.get(i).getAsJsonObject(), "path");
                            if (!TextUtils.isEmpty(p)) {
                                allPageUrls.add(imgUrl(p));
                            }
                        } catch (Exception ignore) {
                        }
                    }
                    JsonObject p0 = pages.get(0).getAsJsonObject();
                    String path = optString(p0, "path");
                    if (!TextUtils.isEmpty(path)) {
                        fileUrl = imgUrl(path);
                    }
                    width = optInt(p0, "width");
                    height = optInt(p0, "height");
                }
            } catch (Exception ignore) {
            }

            // Cover image, used as the details thumbnail fallback
            String previewUrl = "";
            int previewWidth = 0;
            int previewHeight = 0;
            try {
                JsonObject thumb = g.getAsJsonObject("thumbnail");
                String path = optString(thumb, "path");
                if (!TextUtils.isEmpty(path)) {
                    previewUrl = imgUrl(path);
                }
                previewWidth = optInt(thumb, "width");
                previewHeight = optInt(thumb, "height");
            } catch (Exception ignore) {
            }
            if (TextUtils.isEmpty(previewUrl)) {
                previewUrl = fileUrl;
                previewWidth = width;
                previewHeight = height;
            }
            if (TextUtils.isEmpty(fileUrl)) {
                fileUrl = previewUrl;
            }

            // Tags grouped by type, plus artist: prefer the artist tag, then scanlator
            StringBuilder tags = new StringBuilder();
            StringBuilder artists = new StringBuilder();
            try {
                JsonArray tagArr = g.getAsJsonArray("tags");
                if (tagArr != null) {
                    for (int i = 0; i < tagArr.size(); i++) {
                        try {
                            JsonObject tag = tagArr.get(i).getAsJsonObject();
                            String name = optString(tag, "name");
                            String type = optString(tag, "type");
                            if (TextUtils.isEmpty(name)) {
                                continue;
                            }
                            tags.append(name).append(" ");
                            if ("artist".equals(type)) {
                                if (artists.length() > 0) {
                                    artists.append(", ");
                                }
                                artists.append(name);
                                builder.addArtistTags(name);
                            } else if ("character".equals(type)) {
                                builder.addCharacterTags(name);
                            } else if ("parody".equals(type)) {
                                builder.addCopyrightTags(name);
                            } else if ("group".equals(type)) {
                                builder.addCircleTags(name);
                            } else {
                                builder.addGeneralTags(name);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } catch (Exception ignore) {
            }

            String author = artists.length() > 0 ? artists.toString() : optString(g, "scanlator");
            if (TextUtils.isEmpty(author)) {
                author = getNHConfig().getWebsiteName();
            }

            // Upload time in epoch seconds
            String createdTime = "";
            try {
                JsonElement ue = g.get("upload_date");
                if (ue != null && !ue.isJsonNull()) {
                    long epoch = ue.getAsLong();
                    if (epoch > 0) {
                        createdTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(new Date(epoch * 1000));
                    }
                }
            } catch (Exception ignore) {
            }

            builder.id(id)
                    .tags(tags.toString().trim())
                    .createdTime(createdTime)
                    .author(author)
                    .source(getNHConfig().getGalleryWebUrl(id))
                    .score(optString(g, "num_favorites"))
                    .md5("")
                    .fileUrl(fileUrl)
                    .pageUrls(allPageUrls)
                    .width(String.valueOf(width))
                    .height(String.valueOf(height))
                    .fileSize("-1")
                    .previewUrl(previewUrl)
                    .previewWidth(String.valueOf(previewWidth))
                    .previewHeight(String.valueOf(previewHeight))
                    .sampleUrl(fileUrl)
                    .sampleWidth(String.valueOf(width))
                    .sampleHeight(String.valueOf(height))
                    .sampleFileSize("-1")
                    .jpegUrl(fileUrl)
                    .jpegWidth(String.valueOf(width))
                    .jpegHeight(String.valueOf(height))
                    .jpegFileSize("-1")
                    .rating("e")
                    .hasChildren("false")
                    .parentId("");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.build();
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
