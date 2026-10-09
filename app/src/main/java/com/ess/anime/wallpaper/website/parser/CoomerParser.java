package com.ess.anime.wallpaper.website.parser;

import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Coomer API parser.
 *
 * Response format:
 * {"count": N, "posts": [{id, service, title, published,
 *   file: {name, path}, attachments: [{name, path}]}]}
 *
 * Image URL: https://coomer.st/data{path}
 * Only image attachments are used (jpg/jpeg/png/gif/webp); videos are skipped.
 */
public class CoomerParser extends HtmlParser {

    private static final String DATA_BASE = "https://coomer.st/data";

    public CoomerParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    private static boolean isImage(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            JsonObject root = new JsonParser().parse(doc.text()).getAsJsonObject();
            JsonArray posts = root.getAsJsonArray("posts");
            if (posts == null) {
                return thumbList;
            }
            for (int i = 0; i < posts.size(); i++) {
                try {
                    JsonObject post = posts.get(i).getAsJsonObject();
                    String id = getAsString(post, "id");
                    String title = getAsString(post, "title");
                    if (TextUtils.isEmpty(title)) {
                        title = getAsString(post, "substring");
                    }

                    // Collect image paths from file + attachments
                    List<String> imagePaths = new ArrayList<>();
                    try {
                        JsonObject file = post.getAsJsonObject("file");
                        if (file != null) {
                            String name = getAsString(file, "name");
                            String path = getAsString(file, "path");
                            if (isImage(name) && !TextUtils.isEmpty(path)) {
                                imagePaths.add(path);
                            }
                        }
                    } catch (Exception ignore) {
                    }
                    try {
                        JsonArray attachments = post.getAsJsonArray("attachments");
                        if (attachments != null) {
                            for (int j = 0; j < attachments.size(); j++) {
                                JsonObject att = attachments.get(j).getAsJsonObject();
                                String name = getAsString(att, "name");
                                String path = getAsString(att, "path");
                                if (isImage(name) && !TextUtils.isEmpty(path)) {
                                    imagePaths.add(path);
                                }
                            }
                        }
                    } catch (Exception ignore) {
                    }

                    if (imagePaths.isEmpty() || TextUtils.isEmpty(id)) {
                        continue;
                    }

                    String thumbUrl = DATA_BASE + imagePaths.get(0);
                    ThumbBean bean = new ThumbBean(id, 0, 0, thumbUrl, "", id);
                    // Store all image URLs in ImageBean for detail view
                    try {
                        ImageBean.ImageJsonBuilder builder = new ImageBean.ImageJsonBuilder();
                        builder.id(id);
                        if (!TextUtils.isEmpty(title)) {
                            builder.tags(title);
                        }
                        builder.fileUrl(thumbUrl);
                        builder.previewUrl(thumbUrl);
                        List<String> pageUrls = new ArrayList<>();
                        for (String p : imagePaths) {
                            pageUrls.add(DATA_BASE + p);
                        }
                        builder.pageUrls(pageUrls);
                        bean.imageBean = ImageBean.getImageDetailFromJson(builder.build());
                    } catch (Exception ignore) {
                    }
                    thumbList.add(bean);
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return thumbList;
    }

    @Override
    public String getImageDetailJson(Document doc) {
        // Detail is already built in getThumbList; return empty as fallback
        return "";
    }

    private static String getAsString(JsonObject obj, String key) {
        try {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        } catch (Exception ignore) {
        }
        return "";
    }
}
