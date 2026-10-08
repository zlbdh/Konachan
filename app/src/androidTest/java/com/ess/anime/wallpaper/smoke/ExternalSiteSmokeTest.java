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
 * Independent external-network smoke suite; requires explicit -e runExternalSites true. External requests are skipped by default.
 * For each user-requested site, Rule34 and AnimePictures, inspect only one explicitly safe result: list, details, and the first 16 thumbnail bytes.
 * Do not request images without safe metadata; do not save or display media.
 * Generate URLs with the existing Config without private keys; HTTP 403, challenges, empty results, and timeouts are real failures.
 */
@RunWith(AndroidJUnit4.class)
public class ExternalSiteSmokeTest {
    @Before public void requireExplicitExternalOptIn() {
        org.junit.Assume.assumeTrue("External smoke testing was not explicitly enabled; skipping image-site requests",
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
            Thread thread = new Thread(runnable, "Image-site smoke test time limit");
            thread.setDaemon(true);
            return thread;
        });
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false).build();
        String name = config.getWebsiteName();
        try {
            // Rule34 filters safe content on the server; AnimePictures is filtered strictly using the original API metadata.
            String listUrl = config.getPostUrl(1, config instanceof Rule34Config
                    ? Collections.singletonList("rating:safe") : Collections.emptyList());
            String listBody = new String(fetch(client, watchdog, listUrl, config.getBaseUrl(),
                    name + "/list", deadline, 1024 * 1024, false), StandardCharsets.UTF_8);
            Document listDocument = Jsoup.parse(listBody);
            List<ThumbBean> thumbs = config.getHtmlParser().getThumbList(listDocument);
            assertFalse(name + "/list has no parseable results", thumbs.isEmpty());
            String safeId = firstSafeId(config, listBody, listDocument);
            assertNotNull(name + "/API returned no confirmed safe item; details and media were not verified", safeId);
            ThumbBean thumb = null;
            for (ThumbBean candidate : thumbs) {
                if (safeId.equals(candidate.id)) { thumb = candidate; break; }
            }
            assertNotNull(name + "/parser did not return the first item explicitly marked safe by the API", thumb);
            assertNotNull(name + "/list is missing an ID", thumb.id);
            assertNotNull(name + "/list is missing a details URL", thumb.linkToShow);
            String detailBody = new String(fetch(client, watchdog, thumb.linkToShow, config.getBaseUrl(),
                    name + "/details", deadline, 1024 * 1024, false), StandardCharsets.UTF_8);
            ImageBean image = ImageBean.getImageDetailFromJson(config.getHtmlParser()
                    .getImageDetailJson(Jsoup.parse(detailBody)));
            assertNotNull(name + "/details posts could not be parsed", image.posts);
            assertTrue(name + "/details are empty", image.posts.length > 0 && image.posts[0] != null);
            // First prove that the details parser recognizes the ID; do not hide a parsing failure with the tempPost ID.
            assertTrue(name + "/details ID does not match the list", thumb.checkImageBelongs(image));
            if (config instanceof AnimePicturesConfig) {
                JsonObject root = new JsonParser().parse(detailBody).getAsJsonObject();
                assertTrue(name + "/details did not explicitly confirm safe content; thumbnail was not verified", root.has("post")
                        && explicitSafe(root.getAsJsonObject("post")));
            }
            if (thumb.tempPost != null) image.posts[0].replaceDataIfNotNull(thumb.tempPost);
            assertTrue(name + "/details did not provide a media URL", image.hasPostBean());
            byte[] signature = fetch(client, watchdog, thumb.thumbUrl, config.getBaseUrl(),
                    name + "/thumbnail", deadline, 16, true);
            assertTrue(name + "/thumbnail response is not image content", imageSignature(signature));
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
        assertTrue(stage + "Exceeded the 19-second total time budget", remaining > 0);
        Call call = client.newCall(new Request.Builder().url(OkHttp.convertSchemeToHttps(url))
                .header("User-Agent", OkHttp.USER_AGENT).header("Referer", referer).build());
        ScheduledFuture<?> timeout = watchdog.schedule(call::cancel, remaining, TimeUnit.MILLISECONDS);
        try (Response response = call.execute()) {
            assertEquals(stage + " HTTP status (do not bypass challenges)", 200, response.code());
            assertNotNull(stage + "Empty response", response.body());
            if (thumbnail) {
                String type = response.header("Content-Type", "");
                assertTrue(stage + "Incorrect response type; this may be a verification page", type.startsWith("image/"));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream input = response.body().byteStream();
            byte[] buffer = new byte[Math.min(4096, maxBytes)];
            while (output.size() < maxBytes) {
                assertTrue(stage + "Exceeded the 19-second total time budget", SystemClock.elapsedRealtime() < deadline);
                int read = input.read(buffer, 0, Math.min(buffer.length, maxBytes - output.size()));
                if (read < 0) break;
                output.write(buffer, 0, read);
            }
            if (!thumbnail) assertTrue(stage + "Metadata exceeds the 1 MiB limit", output.size() < maxBytes);
            assertTrue(stage + "Empty content", output.size() > 0);
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
        // Accept only explicit API fields. Missing, null, or nonnumeric erotics values must not be treated as zero or safe.
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
