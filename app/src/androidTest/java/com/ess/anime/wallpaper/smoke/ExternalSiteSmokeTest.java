package com.ess.anime.wallpaper.smoke;

import android.os.SystemClock;

import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.website.Rule34Config;
import com.ess.anime.wallpaper.website.AnimePicturesConfig;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.*;

/**
 * 独立外网冒烟套件，必须显式传入 -e runExternalSites true；默认跳过外网请求。
 * 用户指定的 Rule34 / AnimePictures 各仅一条明确 safe 结果：列表、详情、缩略图前 16 字节。
 * 没有 safe 元数据的结果不请求任何图片；不保存、不显示媒体。
 * 使用现有 Config 生成 URL，不携带私人 key；HTTP 403/验证码/空结果/超时均真实失败。
 */
@RunWith(AndroidJUnit4.class)
public class ExternalSiteSmokeTest {
    @Before public void requireExplicitExternalOptIn() {
        org.junit.Assume.assumeTrue("未显式启用外网冒烟，不执行图站请求",
                "true".equals(InstrumentationRegistry.getArguments().getString("runExternalSites")));
    }

    @Test(timeout = 20000) public void rule34SafeListDetailAndThumbnail() throws Exception {
        smoke(new Rule34Config());
    }

    @Test(timeout = 20000) public void animePicturesSafeListDetailAndThumbnail() throws Exception {
        smoke(new AnimePicturesConfig());
    }

    private void smoke(WebsiteConfig config) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 19000;
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "图源冒烟限时");
            thread.setDaemon(true);
            return thread;
        });
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build();
        String name = config.getWebsiteName();
        try {
            // Rule34 由服务器筛选 safe；AnimePictures 在原始 API 元数据中严格筛选。
            String listUrl = config.getPostUrl(1, config instanceof Rule34Config
                    ? Collections.singletonList("rating:safe") : Collections.emptyList());
            String listBody = new String(fetch(client, watchdog, listUrl, config.getBaseUrl(),
                    name + "/列表", deadline, 1024 * 1024, false), StandardCharsets.UTF_8);
            Document listDocument = Jsoup.parse(listBody);
            List<ThumbBean> thumbs = config.getHtmlParser().getThumbList(listDocument);
            assertFalse(name + "/列表没有可解析结果", thumbs.isEmpty());
            String safeId = firstSafeId(config, listBody, listDocument);
            assertNotNull(name + "/API 未返回可确认的 safe 项，详情/媒体未验证", safeId);
            ThumbBean thumb = null;
            for (ThumbBean candidate : thumbs) {
                if (safeId.equals(candidate.id)) { thumb = candidate; break; }
            }
            assertNotNull(name + "/parser 未解析出 API 明确标记的首条 safe 项", thumb);
            assertNotNull(name + "/列表缺少 ID", thumb.id);
            assertNotNull(name + "/列表缺少详情地址", thumb.linkToShow);
            String detailBody = new String(fetch(client, watchdog, thumb.linkToShow, config.getBaseUrl(),
                    name + "/详情", deadline, 1024 * 1024, false), StandardCharsets.UTF_8);
            ImageBean image = ImageBean.getImageDetailFromJson(config.getHtmlParser()
                    .getImageDetailJson(Jsoup.parse(detailBody)));
            assertNotNull(name + "/详情无法解析 posts", image.posts);
            assertTrue(name + "/详情为空", image.posts.length > 0 && image.posts[0] != null);
            // 先证明详情 parser 本身能识别 ID，不能用 tempPost 的 ID 掩盖详情解析失败。
            assertTrue(name + "/详情 ID 与列表不一致", thumb.checkImageBelongs(image));
            if (config instanceof AnimePicturesConfig) {
                JsonObject root = new JsonParser().parse(detailBody).getAsJsonObject();
                assertTrue(name + "/详情未明确确认 safe，缩略图未验证", root.has("post")
                        && explicitSafe(root.getAsJsonObject("post")));
            }
            if (thumb.tempPost != null) image.posts[0].replaceDataIfNotNull(thumb.tempPost);
            assertTrue(name + "/详情未提供媒体地址", image.hasPostBean());
            byte[] signature = fetch(client, watchdog, thumb.thumbUrl, config.getBaseUrl(),
                    name + "/缩略图", deadline, 16, true);
            assertTrue(name + "/缩略图响应不是图片内容", imageSignature(signature));
        } finally {
            watchdog.shutdownNow();
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
        }
    }

    private byte[] fetch(OkHttpClient client, ScheduledExecutorService watchdog, String url,
                         String referer, String stage, long deadline, int maxBytes,
                         boolean thumbnail) throws Exception {
        long remaining = deadline - SystemClock.elapsedRealtime();
        assertTrue(stage + "超过 19 秒总预算", remaining > 0);
        Call call = client.newCall(new Request.Builder().url(OkHttp.convertSchemeToHttps(url))
                .header("User-Agent", OkHttp.USER_AGENT).header("Referer", referer).build());
        ScheduledFuture<?> timeout = watchdog.schedule(call::cancel, remaining, TimeUnit.MILLISECONDS);
        try (Response response = call.execute()) {
            assertEquals(stage + " HTTP 状态（不绕过验证码）", 200, response.code());
            assertNotNull(stage + "响应为空", response.body());
            if (thumbnail) {
                String type = response.header("Content-Type", "");
                assertTrue(stage + "响应类型错误，可能为验证页", type.startsWith("image/"));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream input = response.body().byteStream();
            byte[] buffer = new byte[Math.min(4096, maxBytes)];
            while (output.size() < maxBytes) {
                assertTrue(stage + "超过 19 秒总预算", SystemClock.elapsedRealtime() < deadline);
                int read = input.read(buffer, 0, Math.min(buffer.length, maxBytes - output.size()));
                if (read < 0) break;
                output.write(buffer, 0, read);
            }
            if (!thumbnail) assertTrue(stage + "元数据超出 1 MiB 上限", output.size() < maxBytes);
            assertTrue(stage + "内容为空", output.size() > 0);
            return output.toByteArray();
        } finally {
            timeout.cancel(false);
        }
    }

    private boolean safe(String rating) { return "s".equals(rating) || "safe".equals(rating); }

    private String firstSafeId(WebsiteConfig config, String body, Document document) {
        if (config instanceof Rule34Config) {
            for (Element post : document.getElementsByTag("post")) {
                if (safe(post.attr("rating"))) return post.attr("id");
            }
            return null;
        }
        JsonObject root = new JsonParser().parse(body).getAsJsonObject();
        if (!root.has("posts") || !root.get("posts").isJsonArray()) return null;
        JsonArray posts = root.getAsJsonArray("posts");
        for (JsonElement value : posts) {
            JsonObject post = value.getAsJsonObject();
            if (post.has("id") && explicitSafe(post)) return post.get("id").getAsString();
        }
        return null;
    }

    private boolean explicitSafe(JsonObject post) {
        // 只接受 API 显式字段。erotics 缺失/null/非数字不能当作 0 或 safe。
        if (post.has("rating") && !post.get("rating").isJsonNull()
                && safe(post.get("rating").getAsString())) return true;
        JsonElement erotics = post.get("erotics");
        return erotics != null && erotics.isJsonPrimitive()
                && erotics.getAsJsonPrimitive().isNumber() && erotics.getAsInt() == 0;
    }

    private boolean imageSignature(byte[] value) {
        if (value.length < 12) return false;
        return (value[0] == (byte) 0xff && value[1] == (byte) 0xd8)
                || (value[0] == (byte) 137 && value[1] == 80 && value[2] == 78 && value[3] == 71)
                || new String(value, 0, 4, StandardCharsets.US_ASCII).equals("GIF8")
                || (new String(value, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(value, 8, 4, StandardCharsets.US_ASCII).equals("WEBP"))
                || (new String(value, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")
                && new String(value, 8, 4, StandardCharsets.US_ASCII).equals("avif"));
    }
}
