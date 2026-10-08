package com.ess.anime.wallpaper.website.parser;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.PostBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * e621.net JSON API parser.
 * Lists, details, and comments use JSON endpoints; recover JSON from body text after Jsoup.parse.
 * e621 requires a User-Agent; the app already sends a browser User-Agent globally.
 */
public class E621Parser extends HtmlParser {

    public E621Parser(WebsiteConfig websiteConfig) {
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
                    JsonObject post = posts.get(i).getAsJsonObject();
                    String id = optString(post, "id");
                    JsonObject file = optObject(post, "file");
                    JsonObject preview = optObject(post, "preview");
                    String thumbUrl = optString(preview, "url");
                    if (thumbUrl.isEmpty()) {
                        continue;
                    }
                    int realWidth = optInt(file, "width");
                    int realHeight = optInt(file, "height");
                    int thumbWidth, thumbHeight;
                    if (realWidth >= realHeight && realWidth > 0) {
                        thumbWidth = 720;
                        thumbHeight = (int) (realHeight / 1f / realWidth * thumbWidth);
                    } else if (realHeight > 0) {
                        thumbHeight = 720;
                        thumbWidth = (int) (realWidth / 1f / realHeight * thumbHeight);
                    } else {
                        thumbWidth = optInt(preview, "width");
                        thumbHeight = optInt(preview, "height");
                    }
                    String realSize = realWidth + " x " + realHeight;
                    String linkToShow = mWebsiteConfig.getPostDetailUrl(id);
                    ThumbBean thumbBean = new ThumbBean(id, thumbWidth, thumbHeight, thumbUrl, realSize, linkToShow);
                    thumbBean.tempPost = parseTempPost(post);
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

    private PostBean parseTempPost(JsonObject post) {
        try {
            PostBean postBean = new PostBean();
            postBean.id = optString(post, "id");
            postBean.tags = joinTags(post);
            postBean.creatorId = optString(post, "uploader_id");
            postBean.author = optString(post, "uploader_name");
            postBean.change = optString(post, "change_seq");
            JsonArray sources = optArray(post, "sources");
            postBean.source = sources.size() > 0 ? sources.get(0).getAsString() : "";
            postBean.score = optInt(optObject(post, "score"), "total");
            JsonObject file = optObject(post, "file");
            postBean.md5 = optString(file, "md5");
            postBean.fileUrl = optString(file, "url");
            postBean.fileSize = optLong(file, "size");
            JsonObject preview = optObject(post, "preview");
            postBean.previewUrl = optString(preview, "url");
            postBean.previewWidth = optInt(preview, "width");
            postBean.previewHeight = optInt(preview, "height");
            JsonObject sample = optObject(post, "sample");
            postBean.sampleUrl = optString(sample, "url");
            if (postBean.sampleUrl.isEmpty()) {
                postBean.sampleUrl = postBean.fileUrl;
            }
            postBean.sampleWidth = optInt(sample, "width");
            postBean.sampleHeight = optInt(sample, "height");
            postBean.sampleFileSize = -1;
            postBean.jpegUrl = postBean.fileUrl;
            postBean.jpegWidth = optInt(file, "width");
            postBean.jpegHeight = optInt(file, "height");
            postBean.jpegFileSize = postBean.fileSize;
            postBean.rating = optString(post, "rating");
            JsonObject rel = optObject(post, "relationships");
            postBean.hasChildren = optArray(rel, "children").size() > 0;
            postBean.parentId = optString(rel, "parent_id");
            postBean.status = "";
            postBean.width = postBean.jpegWidth;
            postBean.height = postBean.jpegHeight;
            postBean.createdTime = parseTime(optString(post, "created_at"));
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
            JsonObject post = root.has("post") ? root.getAsJsonObject("post") : root;

            builder.id(optString(post, "id"))
                    .tags(joinTags(post))
                    .createdTime(String.valueOf(parseTime(optString(post, "created_at"))))
                    .creatorId(optString(post, "uploader_id"))
                    .author(optString(post, "uploader_name"))
                    .change(optString(post, "change_seq"));

            JsonArray sources = optArray(post, "sources");
            builder.source(sources.size() > 0 ? sources.get(0).getAsString() : "")
                    .parentId("");

            JsonObject score = optObject(post, "score");
            builder.score(String.valueOf(optInt(score, "total")));

            JsonObject file = optObject(post, "file");
            String fileUrl = optString(file, "url");
            builder.md5(optString(file, "md5"))
                    .fileSize(String.valueOf(optLong(file, "size")))
                    .fileUrl(fileUrl);

            JsonObject preview = optObject(post, "preview");
            builder.previewUrl(optString(preview, "url"))
                    .previewWidth(String.valueOf(optInt(preview, "width")))
                    .previewHeight(String.valueOf(optInt(preview, "height")));

            JsonObject sample = optObject(post, "sample");
            String sampleUrl = optString(sample, "url");
            if (sampleUrl.isEmpty()) {
                sampleUrl = fileUrl;
            }
            builder.sampleUrl(sampleUrl)
                    .sampleWidth(String.valueOf(optInt(sample, "width")))
                    .sampleHeight(String.valueOf(optInt(sample, "height")))
                    .sampleFileSize("-1")
                    .jpegUrl(fileUrl)
                    .jpegWidth(String.valueOf(optInt(file, "width")))
                    .jpegHeight(String.valueOf(optInt(file, "height")))
                    .jpegFileSize(String.valueOf(optLong(file, "size")))
                    .rating(optString(post, "rating"));

            JsonObject rel = optObject(post, "relationships");
            builder.hasChildren(String.valueOf(optArray(rel, "children").size() > 0))
                    .parentId(optString(rel, "parent_id"))
                    .width(String.valueOf(optInt(file, "width")))
                    .height(String.valueOf(optInt(file, "height")))
                    .flagDetail(optString(post, "description"));

            // Categorized tags
            JsonObject tags = optObject(post, "tags");
            builder.addCopyrightTags(toStringArray(optArray(tags, "copyright")));
            builder.addCharacterTags(toStringArray(optArray(tags, "character")));
            builder.addArtistTags(toStringArray(optArray(tags, "artist")));
            List<String> general = new ArrayList<>();
            for (String key : new String[]{"general", "species", "meta", "lore", "contributor"}) {
                JsonArray arr = optArray(tags, key);
                for (int i = 0; i < arr.size(); i++) {
                    general.add(arr.get(i).getAsString());
                }
            }
            builder.addGeneralTags(general.toArray(new String[0]));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.build();
    }

    @Override
    public List<CommentBean> getCommentList(Document doc) {
        List<CommentBean> commentList = new ArrayList<>();
        try {
            String text = doc.body().text().trim();
            JsonElement el = new JsonParser().parse(text);
            JsonArray array = el.isJsonArray() ? el.getAsJsonArray()
                    : optArray(el.getAsJsonObject(), "comments");
            for (int i = 0; i < array.size(); i++) {
                try {
                    JsonObject c = array.get(i).getAsJsonObject();
                    String id = optString(c, "id");
                    String author = optString(c, "creator_name");
                    String date = optString(c, "created_at");
                    String body = optString(c, "body");
                    commentList.add(new CommentBean(id, author, date, "", null, body));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return commentList;
    }

    @Override
    public List<PoolListBean> getPoolListList(Document doc) {
        return new ArrayList<>();
    }

    private String joinTags(JsonObject post) {
        StringBuilder sb = new StringBuilder();
        JsonObject tags = optObject(post, "tags");
        for (String key : new String[]{"general", "artist", "contributor", "copyright",
                "character", "species", "invalid", "meta", "lore"}) {
            JsonArray arr = optArray(tags, key);
            for (int i = 0; i < arr.size(); i++) {
                sb.append(arr.get(i).getAsString()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private long parseTime(String iso) {
        // Format: 2026-10-06T13:56:20.603-04:00
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
            return sdf.parse(iso).getTime() / 1000;
        } catch (Exception e) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
                return sdf.parse(iso).getTime() / 1000;
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    private JsonObject parseBodyJson(Document doc) {
        String text = doc.body().text().trim();
        return new JsonParser().parse(text).getAsJsonObject();
    }

    private String[] toStringArray(JsonArray arr) {
        String[] result = new String[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.get(i).getAsString();
        }
        return result;
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
