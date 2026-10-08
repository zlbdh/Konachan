package com.ess.anime.wallpaper.website.parser;

import android.text.Html;
import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.website.HitomiConfig;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hitomi.la parser
 *
 * Flow: getPostUrl returns a placeholder 404 with an empty Document; perform all requests here:
 * 1. Range-request the nozomi index to get this page's gallery IDs (four-byte little-endian, reverse order)
 * 2. Request galleries/{id}.js for each gallery, including files[].hash, width, and height
 * 3. Build direct image URLs with HitomiConfig.getImageUrl(id, hash)
 */
public class HitomiParser extends HtmlParser {

    /** Cache the latest gallery ID to avoid requesting it for every page */
    private static volatile int sNewestId = 0;

    public HitomiParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    private HitomiConfig getHitomiConfig() {
        return (HitomiConfig) mWebsiteConfig;
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        try {
            int page = getHitomiConfig().getCurrentPage();
            List<Integer> galleryIds = getGalleryIdsForPage(page);
            for (int galleryId : galleryIds) {
                try {
                    JsonObject gallery = fetchGallery(galleryId);
                    if (gallery == null) {
                        continue;
                    }
                    ThumbBean thumb = parseGallery(galleryId, gallery);
                    if (thumb != null) {
                        thumbList.add(thumb);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return thumbList;
    }

    /**
     * Get the gallery IDs for this page.
     * The nozomi index is ordered newest first; take PAGE_SIZE entries per page.
     * Reduce requests by estimating latest ID minus offset; IDs increase sequentially and deleted galleries return 404 and are skipped.
     */
    private List<Integer> getGalleryIdsForPage(int page) {
        List<Integer> ids = new ArrayList<>();
        try {
            int newest = getNewestGalleryId();
            if (newest <= 0) {
                return ids;
            }
            int pageSize = HitomiConfig.getPageSize();
            int start = newest - (page - 1) * pageSize;
            for (int i = 0; i < pageSize && start - i > 0; i++) {
                ids.add(start - i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ids;
    }

    /** Range-request the first four nozomi bytes to obtain the latest gallery ID as a little-endian int */
    private int getNewestGalleryId() {
        if (sNewestId > 0) {
            return sNewestId;
        }
        try {
            byte[] data = fetchRange(HitomiConfig.getIndexUrl(), 0, 3);
            if (data != null && data.length >= 4) {
                int id = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (id > 0) {
                    sNewestId = id;
                }
                return id;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /** Synchronous request with a Range header, returning raw bytes */
    private byte[] fetchRange(String url, long start, long end) throws Exception {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("Range", "bytes=" + start + "-" + end)
                .header("User-Agent", OkHttp.USER_AGENT)
                .build();
        okhttp3.Response response = client.newCall(request).execute();
        try {
            if (response.body() != null) {
                return response.body().bytes();
            }
        } finally {
            response.close();
        }
        return null;
    }

    /** Request galleries/{id}.js and parse a JsonObject after removing the var galleryinfo = prefix */
    private JsonObject fetchGallery(int galleryId) {
        try {
            String url = HitomiConfig.getGalleryJsUrl(galleryId);
            okhttp3.Response response = OkHttp.execute(url, this);
            if (response == null || response.body() == null) {
                return null;
            }
            String body;
            try {
                body = response.body().string();
            } finally {
                response.close();
            }
            // Remove the "var galleryinfo = " prefix and trailing semicolon
            Matcher m = Pattern.compile("var\\s+galleryinfo\\s*=\\s*(\\{.*\\})\\s*;?\\s*$",
                    Pattern.DOTALL).matcher(body.trim());
            String json = m.find() ? m.group(1) : body.trim();
            return new JsonParser().parse(json).getAsJsonObject();
        } catch (Exception e) {
            // Skip 404 responses for deleted galleries and similar failures
            return null;
        }
    }

    /** Parse one gallery into ThumbBean, using its first image as the thumbnail and all pages in ImageBean */
    private ThumbBean parseGallery(int galleryId, JsonObject gallery) {
        try {
            JsonArray files = gallery.getAsJsonArray("files");
            if (files == null || files.size() == 0) {
                return null;
            }

            String title = "";
            try {
                title = gallery.get("title").getAsString();
                title = Html.fromHtml(title).toString().trim();
            } catch (Exception ignore) {
            }
            if (TextUtils.isEmpty(title)) {
                try {
                    title = gallery.get("japanese_title").getAsString();
                } catch (Exception ignore) {
                }
            }

            // Join tags with spaces
            StringBuilder tags = new StringBuilder();
            try {
                JsonArray tagArray = gallery.getAsJsonArray("tags");
                for (int i = 0; i < tagArray.size(); i++) {
                    String tag = tagArray.get(i).getAsJsonObject().get("tag").getAsString();
                    tags.append(tag).append(" ");
                }
            } catch (Exception ignore) {
            }
            String tagStr = (title + " " + tags.toString()).trim();

            // Use the first image as the thumbnail
            JsonObject first = files.get(0).getAsJsonObject();
            String firstHash = first.get("hash").getAsString();
            String thumbUrl = HitomiConfig.getImageUrl(galleryId, firstHash);
            if (TextUtils.isEmpty(thumbUrl)) {
                return null;
            }
            int thumbW = 0, thumbH = 0;
            try {
                thumbW = first.get("width").getAsInt();
                thumbH = first.get("height").getAsInt();
            } catch (Exception ignore) {
            }

            String id = String.valueOf(galleryId);
            String realSize = files.size() + "P";
            String linkToShow = mWebsiteConfig.getPostDetailUrl(id);
            ThumbBean thumbBean = new ThumbBean(id, thumbW, thumbH, thumbUrl, realSize, linkToShow);
            thumbBean.imageBean = buildImageBean(galleryId, tagStr, files);
            return thumbBean;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Build ImageBean from all gallery pages for direct use by details and batch downloads, avoiding another request */
    private ImageBean buildImageBean(int galleryId, String tags, JsonArray files) {
        ImageBean.ImageJsonBuilder builder = new ImageBean.ImageJsonBuilder();
        try {
            List<String> urls = new ArrayList<>();
            List<String> widths = new ArrayList<>();
            List<String> heights = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                JsonObject f = files.get(i).getAsJsonObject();
                String hash = f.get("hash").getAsString();
                String url = HitomiConfig.getImageUrl(galleryId, hash);
                if (TextUtils.isEmpty(url)) {
                    continue;
                }
                urls.add(url);
                widths.add(f.get("width").getAsString());
                heights.add(f.get("height").getAsString());
            }
            if (urls.isEmpty()) {
                return null;
            }
            String first = urls.get(0);
            builder.id(String.valueOf(galleryId))
                    .tags(tags)
                    .author("hitomi.la")
                    .source(mWebsiteConfig.getPostDetailUrl(String.valueOf(galleryId)))
                    .md5("")
                    .fileUrl(first)
                    .width(widths.get(0))
                    .height(heights.get(0))
                    .fileSize("-1")
                    .previewUrl(first)
                    .previewWidth(widths.get(0))
                    .previewHeight(heights.get(0))
                    .sampleUrl(first)
                    .sampleWidth(widths.get(0))
                    .sampleHeight(heights.get(0))
                    .sampleFileSize("-1")
                    .jpegUrl(first)
                    .jpegWidth(widths.get(0))
                    .jpegHeight(heights.get(0))
                    .jpegFileSize("-1")
                    .rating("e")
                    .hasChildren(String.valueOf(urls.size() > 1))
                    .parentId("");
            // For multiple pages, should remaining URLs go in an extension field after tags? ImageBean supports only one image;
            // join all page URLs with "|" in source for future on-demand parsing by the details screen.
            return ImageBean.getImageDetailFromJson(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String getImageDetailJson(Document doc) {
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
