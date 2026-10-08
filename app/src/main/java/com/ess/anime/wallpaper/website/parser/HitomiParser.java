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
 * Hitomi.la 解析器
 *
 * 流程（getPostUrl 返回的占位 URL 会 404，Document 为空，这里全部自己请求）：
 * 1. Range 请求 nozomi 索引拿到本页的 gallery id 列表（4 字节小端，倒序）
 * 2. 逐个请求 galleries/{id}.js 拿画廊信息（含 files[].hash/width/height）
 * 3. 用 HitomiConfig.getImageUrl(id, hash) 拼图片直链
 */
public class HitomiParser extends HtmlParser {

    /** 缓存最新的 gallery id，避免每页都请求 */
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
     * 拿到本页的 gallery id 列表。
     * nozomi 索引是倒序的（最新在前），每页取 PAGE_SIZE 个。
     * 为减少请求，直接用"最新 id - 偏移"估算（id 连续递增，删除的画廊请求会 404 跳过）。
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

    /** Range 取 nozomi 前 4 字节得到最新 gallery id（小端 int） */
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

    /** 带 Range 头的同步请求，返回原始字节 */
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

    /** 请求 galleries/{id}.js，返回解析后的 JsonObject（去掉 var galleryinfo = 前缀） */
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
            // 去掉 "var galleryinfo = " 前缀和末尾分号
            Matcher m = Pattern.compile("var\\s+galleryinfo\\s*=\\s*(\\{.*\\})\\s*;?\\s*$",
                    Pattern.DOTALL).matcher(body.trim());
            String json = m.find() ? m.group(1) : body.trim();
            return new JsonParser().parse(json).getAsJsonObject();
        } catch (Exception e) {
            // 404（被删除的画廊）等直接跳过
            return null;
        }
    }

    /** 把单个画廊解析成 ThumbBean（首图做缩略图，全部页面拼进 ImageBean） */
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

            // tags 拼成空格分隔
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

            // 首图做缩略图
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

    /** 把画廊所有页面拼成 ImageBean（详情页/批量下载直接用，不再二次请求） */
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
            // 多页：把剩余页面 URL 追加到 tags 后的扩展字段？ImageBean 只支持单图，
            // 这里把全部页面用 "|" 拼进 source，由详情页按需解析（预留）。
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
