package com.ess.anime.wallpaper.website.parser;

import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.RedgifsConfig;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Redgifs API parser.
 *
 * Response format:
 * {"gifs": [{id, urls: {poster, thumbnail, hd, sd, vthumbnail}, tags: [], title}],
 *  "total": N}
 *
 * Thumbnails use urls.poster (static image). Detail view uses urls.hd (video/GIF).
 * If response contains error 401 (token expired), clear the cached token so the
 * next request fetches a fresh one.
 */
public class RedgifsParser extends HtmlParser {

    public RedgifsParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    private RedgifsConfig getRedgifsConfig() {
        return (RedgifsConfig) mWebsiteConfig;
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            String text = doc.text();
            // Detect auth error
            if (text.contains("\"code\":\"WrongSender\"") || text.contains("\"status\":401")) {
                getRedgifsConfig().clearToken();
                return thumbList;
            }
            JsonObject root = new JsonParser().parse(text).getAsJsonObject();
            JsonArray gifs = root.getAsJsonArray("gifs");
            if (gifs == null) {
                return thumbList;
            }
            for (int i = 0; i < gifs.size(); i++) {
                try {
                    JsonObject gif = gifs.get(i).getAsJsonObject();
                    String id = getAsString(gif, "id");
                    if (TextUtils.isEmpty(id)) {
                        continue;
                    }
                    JsonObject urls = gif.getAsJsonObject("urls");
                    if (urls == null) {
                        continue;
                    }
                    String poster = getAsString(urls, "poster");
                    String hd = getAsString(urls, "hd");
                    if (TextUtils.isEmpty(poster)) {
                        poster = getAsString(urls, "thumbnail");
                    }
                    if (TextUtils.isEmpty(poster)) {
                        continue;
                    }
                    if (TextUtils.isEmpty(hd)) {
                        hd = poster;
                    }

                    String title = getAsString(gif, "title");
                    ThumbBean bean = new ThumbBean(id, 0, 0, poster, "", id);
                    try {
                        ImageBean.ImageJsonBuilder builder = new ImageBean.ImageJsonBuilder();
                        builder.id(id);
                        if (!TextUtils.isEmpty(title)) {
                            builder.tags(title);
                        }
                        builder.fileUrl(hd);
                        builder.previewUrl(poster);
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

    @Override
    public java.util.List<com.ess.anime.wallpaper.bean.PoolListBean> getPoolListList(org.jsoup.nodes.Document doc) {
        return new java.util.ArrayList<>();
    }
}
