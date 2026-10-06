package com.ess.anime.wallpaper.website.parser;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.PostBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * anime-pictures.net JSON API 解析器（api.anime-pictures.net/api/v3）。
 * 字段映射依据 Grabber 开源模型（Bionus/imgbrd-grabber）：
 * created_at=pubtime, preview_url=small_preview, sample_url=big_preview,
 * score=score_number, file_size=size。
 * 图片直链缺失时按站内规则由 md5 拼接：
 * opreviews.anime-pictures.net/{md5[0:3]}/{md5}_sp.png（缩略图）
 * opreviews.anime-pictures.net/{md5[0:3]}/{md5}_bp.png（中图）
 * oimages.anime-pictures.net/{md5[0:3]}/{md5}.{ext}（原图）
 */
public class AnimePicturesParser extends HtmlParser {

    public AnimePicturesParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            JsonObject root = parseBodyJson(doc);
            JsonArray posts = optArray(root, "posts");
            for (int i = 0; i < posts.size(); i++) {
                try {
                    JsonObject p = posts.get(i).getAsJsonObject();
                    String id = optString(p, "id");
                    String md5 = optString(p, "md5");
                    String ext = optString(p, "ext").replace(".", "");

                    String previewUrl = noWebpAvif(optString(p, "small_preview"));
                    String sampleUrl = noWebpAvif(optString(p, "big_preview"));
                    String fileUrl = optString(p, "file_url");
                    String[] urls = completeUrls(previewUrl, sampleUrl, fileUrl, md5, ext);
                    previewUrl = urls[0];
                    sampleUrl = urls[1];
                    fileUrl = urls[2];
                    if (previewUrl.isEmpty()) {
                        continue;
                    }

                    int realWidth = optInt(p, "width");
                    int realHeight = optInt(p, "height");
                    int thumbWidth, thumbHeight;
                    if (realWidth >= realHeight && realWidth > 0) {
                        thumbWidth = 720;
                        thumbHeight = (int) (realHeight / 1f / realWidth * thumbWidth);
                    } else if (realHeight > 0) {
                        thumbHeight = 720;
                        thumbWidth = (int) (realWidth / 1f / realHeight * thumbHeight);
                    } else {
                        thumbWidth = 360;
                        thumbHeight = 360;
                    }
                    String realSize = realWidth + " x " + realHeight;
                    String linkToShow = mWebsiteConfig.getPostDetailUrl(id);
                    ThumbBean thumbBean = new ThumbBean(id, thumbWidth, thumbHeight, previewUrl, realSize, linkToShow);
                    thumbBean.tempPost = parseTempPost(p, previewUrl, sampleUrl, fileUrl, ext);
                    thumbList.add(thumbBean);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return thumbList;
    }

    private PostBean parseTempPost(JsonObject p, String previewUrl, String sampleUrl, String fileUrl, String ext) {
        try {
            PostBean postBean = new PostBean();
            postBean.id = optString(p, "id");
            postBean.tags = "";
            postBean.creatorId = "";
            postBean.author = "";
            postBean.change = "";
            postBean.source = "";
            postBean.score = optInt(p, "score_number");
            postBean.md5 = optString(p, "md5");
            postBean.fileUrl = fileUrl;
            postBean.fileSize = optLong(p, "size");
            postBean.previewUrl = previewUrl;
            postBean.previewWidth = 0;
            postBean.previewHeight = 0;
            postBean.sampleUrl = sampleUrl.isEmpty() ? fileUrl : sampleUrl;
            postBean.sampleWidth = 0;
            postBean.sampleHeight = 0;
            postBean.sampleFileSize = -1;
            postBean.jpegUrl = fileUrl;
            postBean.jpegWidth = optInt(p, "width");
            postBean.jpegHeight = optInt(p, "height");
            postBean.jpegFileSize = postBean.fileSize;
            postBean.rating = "";
            postBean.hasChildren = false;
            postBean.parentId = "";
            postBean.status = "";
            postBean.width = postBean.jpegWidth;
            postBean.height = postBean.jpegHeight;
            postBean.createdTime = parseTime(optString(p, "pubtime"));
            return postBean;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public String getImageDetailJson(Document doc) {
        ImageBean.ImageJsonBuilder builder = new ImageBean.ImageJsonBuilder();
        try {
            JsonObject root = parseBodyJson(doc);
            JsonObject post = optObject(root, "post");

            String md5 = optString(post, "md5");
            String ext = optString(post, "ext").replace(".", "");
            String previewUrl = noWebpAvif(optString(post, "small_preview"));
            String sampleUrl = noWebpAvif(optString(post, "big_preview"));
            String[] urls = completeUrls(previewUrl, sampleUrl, "", md5, ext);
            previewUrl = urls[0];
            sampleUrl = urls[1];
            String fileUrl = urls[2];

            int width = optInt(post, "width");
            int height = optInt(post, "height");
            long size = optLong(post, "size");

            builder.id(optString(post, "id"))
                    .createdTime(String.valueOf(parseTime(optString(post, "pubtime"))))
                    .creatorId("")
                    .author("")
                    .change("")
                    .source("")
                    .parentId("")
                    .score(String.valueOf(optInt(post, "score_number")))
                    .md5(md5)
                    .fileSize(String.valueOf(size))
                    .fileUrl(fileUrl)
                    .previewUrl(previewUrl)
                    .previewWidth("0")
                    .previewHeight("0")
                    .sampleUrl(sampleUrl.isEmpty() ? fileUrl : sampleUrl)
                    .sampleWidth("0")
                    .sampleHeight("0")
                    .sampleFileSize("-1")
                    .jpegUrl(fileUrl)
                    .jpegWidth(String.valueOf(width))
                    .jpegHeight(String.valueOf(height))
                    .jpegFileSize(String.valueOf(size))
                    .rating("")
                    .hasChildren("false")
                    .parentId("")
                    .width(String.valueOf(width))
                    .height(String.valueOf(height))
                    .flagDetail("");

            // 分类标签：1=角色，3/5/6=版权，4=画师，其余=通用
            List<String> copyright = new ArrayList<>();
            List<String> character = new ArrayList<>();
            List<String> artist = new ArrayList<>();
            List<String> general = new ArrayList<>();
            StringBuilder allTags = new StringBuilder();
            JsonArray tags = optArray(root, "tags");
            for (int i = 0; i < tags.size(); i++) {
                try {
                    JsonObject tag = optObject(tags.get(i).getAsJsonObject(), "tag");
                    String name = optString(tag, "tag");
                    int type = optInt(tag, "type");
                    if (name.isEmpty()) {
                        continue;
                    }
                    allTags.append(name).append(" ");
                    if (type == 1) {
                        character.add(name);
                    } else if (type == 4) {
                        artist.add(name);
                    } else if (type == 3 || type == 5 || type == 6) {
                        copyright.add(name);
                    } else {
                        general.add(name);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            builder.tags(allTags.toString().trim());
            builder.addCopyrightTags(copyright.toArray(new String[0]));
            builder.addCharacterTags(character.toArray(new String[0]));
            builder.addArtistTags(artist.toArray(new String[0]));
            builder.addGeneralTags(general.toArray(new String[0]));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.build();
    }

    @Override
    public List<CommentBean> getCommentList(Document doc) {
        // v3 API 未暴露评论接口
        return new ArrayList<>();
    }

    @Override
    public List<PoolListBean> getPoolListList(Document doc) {
        return new ArrayList<>();
    }

    /**
     * 补全图片直链，返回 {preview, sample, file}
     */
    private String[] completeUrls(String previewUrl, String sampleUrl, String fileUrl, String md5, String ext) {
        if (previewUrl.isEmpty() && sampleUrl.isEmpty() && fileUrl.isEmpty() && !md5.isEmpty() && md5.length() >= 3) {
            String md5Part = md5.substring(0, 3) + "/" + md5;
            previewUrl = "https://opreviews.anime-pictures.net/" + md5Part + "_sp.png";
            sampleUrl = "https://opreviews.anime-pictures.net/" + md5Part + "_bp.png";
            fileUrl = "https://oimages.anime-pictures.net/" + md5Part + "." + ext;
        }
        if (sampleUrl.isEmpty() && !previewUrl.isEmpty()) {
            sampleUrl = previewUrl.replace("_cp.", "_bp.").replace("_sp.", "_bp.");
        }
        if (fileUrl.isEmpty() && !sampleUrl.isEmpty() && !ext.isEmpty()) {
            fileUrl = sampleUrl.replace("/cdn.", "/images.")
                    .replace("/opreviews.", "/oimages.")
                    .replace("/previews/", "/")
                    .replaceAll("_[scb]p\\.\\w{2,5}$", "." + ext);
        }
        return new String[]{withScheme(previewUrl), withScheme(sampleUrl), withScheme(fileUrl)};
    }

    private String withScheme(String url) {
        if (!url.isEmpty() && !url.startsWith("http")) {
            return "https:" + url;
        }
        return url;
    }

    private String noWebpAvif(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(\\.\\w{3,4})\\.(?:webp|avif)", "$1");
    }

    private long parseTime(String pubtime) {
        if (pubtime == null || pubtime.isEmpty()) {
            return 0;
        }
        try {
            // 纯数字：unix 时间戳
            if (pubtime.matches("\\d+")) {
                long t = Long.parseLong(pubtime);
                return t > 10000000000L ? t / 1000 : t;
            }
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(pubtime).getTime() / 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    private JsonObject parseBodyJson(Document doc) {
        String text = doc.body().text().trim();
        return new JsonParser().parse(text).getAsJsonObject();
    }

    private static String optString(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static int optInt(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsInt();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static long optLong(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsLong();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static JsonObject optObject(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && o.get(key).isJsonObject()) {
                return o.getAsJsonObject(key);
            }
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private static JsonArray optArray(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && o.get(key).isJsonArray()) {
                return o.getAsJsonArray(key);
            }
        } catch (Exception ignored) {
        }
        return new JsonArray();
    }
}
