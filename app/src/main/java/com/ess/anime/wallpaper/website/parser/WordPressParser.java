package com.ess.anime.wallpaper.website.parser;

import android.text.Html;
import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.ess.anime.wallpaper.website.WordPressConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * WordPress REST API parser for misskon.com and 4khd.com
 *
 * List requests include &_embed=wp:featuredmedia; read the featured image URL from _embedded,
 * falling back to a synchronous second request to the media endpoint.
 */
public class WordPressParser extends HtmlParser {

    public WordPressParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    private WordPressConfig getWpConfig() {
        return (WordPressConfig) mWebsiteConfig;
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            String json = doc.text();
            JsonArray items = new JsonParser().parse(json).getAsJsonArray();
            for (int i = 0; i < items.size(); i++) {
                try {
                    JsonObject post = items.get(i).getAsJsonObject();
                    String id = post.get("id").getAsString();

                    // Title with HTML tags removed
                    String title = "";
                    try {
                        String rendered = post.getAsJsonObject("title").get("rendered").getAsString();
                        title = Html.fromHtml(rendered).toString().trim();
                    } catch (Exception ignore) {
                    }

                    // Featured image: prefer _embedded, then synchronously request the media endpoint
                    String thumbUrl = "";
                    int mediaWidth = 0;
                    int mediaHeight = 0;
                    JsonObject media = getEmbeddedMedia(post);
                    if (media == null) {
                        media = fetchMedia(post);
                    }
                    if (media != null) {
                        try {
                            thumbUrl = media.get("source_url").getAsString();
                        } catch (Exception ignore) {
                        }
                        try {
                            JsonObject details = media.getAsJsonObject("media_details");
                            mediaWidth = details.get("width").getAsInt();
                            mediaHeight = details.get("height").getAsInt();
                        } catch (Exception ignore) {
                        }
                    }
                    if (TextUtils.isEmpty(thumbUrl)) {
                        // Skip entries without a direct URL to avoid a screen full of placeholders
                        continue;
                    }

                    String realSize = (mediaWidth > 0 && mediaHeight > 0)
                            ? mediaWidth + " x " + mediaHeight : "";
                    String linkToShow = mWebsiteConfig.getPostDetailUrl(id);
                    ThumbBean thumbBean = new ThumbBean(id, mediaWidth, mediaHeight,
                            thumbUrl, realSize, linkToShow);
                    // List data is sufficient to build ImageBean directly without another details request
                    thumbBean.imageBean = buildImageBean(id, title, thumbUrl, mediaWidth, mediaHeight);
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

    /** Get featured image information from _embedded['wp:featuredmedia'][0] */
    private JsonObject getEmbeddedMedia(JsonObject post) {
        try {
            JsonObject embedded = post.getAsJsonObject("_embedded");
            if (embedded == null) {
                return null;
            }
            JsonArray featured = embedded.getAsJsonArray("wp:featuredmedia");
            if (featured != null && featured.size() > 0) {
                return featured.get(0).getAsJsonObject();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /** Second request: synchronously retrieve source_url from the media endpoint when _embed did not include it */
    private JsonObject fetchMedia(JsonObject post) {
        try {
            JsonElement fm = post.get("featured_media");
            if (fm == null || fm.isJsonNull()) {
                return null;
            }
            int mediaId = fm.getAsInt();
            if (mediaId <= 0) {
                return null;
            }
            String url = getWpConfig().getMediaUrl(mediaId);
            okhttp3.Response response = OkHttp.execute(url, this);
            if (response != null && response.body() != null) {
                String body = response.body().string();
                response.close();
                return new JsonParser().parse(body).getAsJsonObject();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private ImageBean buildImageBean(String id, String title, String url, int width, int height) {
        ImageBean.ImageJsonBuilder builder = new ImageBean.ImageJsonBuilder();
        builder.id(id)
                .tags(title)
                .author(getWpConfig().getWebsiteName())
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
                .rating("e")
                .hasChildren("false")
                .parentId("");
        return ImageBean.getImageDetailFromJson(builder.build());
    }

    @Override
    public String getImageDetailJson(Document doc) {
        // Details are already built in getThumbList as thumbBean.imageBean; return empty as a fallback here
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
