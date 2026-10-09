package com.ess.anime.wallpaper.website.parser;

import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.EHentaiRequest;
import com.ess.anime.wallpaper.website.WebsiteConfig;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E-Hentai / ExHentai HTML parser.
 *
 * Reference: JHenTai (Flutter, Apache 2.0) lib/src/utils/eh_spider_parser.dart
 * Only the core gallery-list / detail / image-page parsing is ported.
 *
 * Gallery list, default "Compact" view (table.itg.gltc, one gallery per row):
 *   <tr>
 *     <td class="gl2c"><div class="glthumb"><div><img data-src="{ehgt.org thumb}" src="data:..."></div>
 *       ... <div>NN pages</div></div></td>
 *     <td class="gl3c glname"><a href="https://e-hentai.org/g/{gid}/{token}/"><div class="glink">title</div></a></td>
 *   </tr>
 * Minimal / Extended views are tables too; "Thumbnail" view uses div.itg > div.gl1t.
 *
 * Detail page: h1#gn title, "NNN pages", image page links /s/{imgtoken}/{gid}-{pagenum}
 * Image page: img#img[src] is the direct full-size URL.
 */
public class EHentaiParser extends HtmlParser {

    private static final Pattern GID_TOKEN = Pattern.compile("/g/(\\d+)/([0-9a-f]+)/");
    // "1 page" / "24 pages"
    private static final Pattern PAGES = Pattern.compile("(\\d+)\\s+pages?");
    private static final Pattern GID_VAR = Pattern.compile("var gid\\s*=\\s*(\\d+);");
    private static final Pattern IMAGE_PAGE_GID = Pattern.compile("/s/[0-9a-f]+/(\\d+)-\\d+");
    private static final Pattern CSS_URL = Pattern.compile("url\\(['\"]?([^'\")]+)['\"]?\\)");
    // Image pages fetched at once when a gallery opens; kept low so it reads like a browser.
    private static final int IMAGE_PAGE_CONCURRENCY = 4;

    public EHentaiParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    // ------------------------------------------------------------------
    // HtmlParser interface
    // ------------------------------------------------------------------

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        return parseGalleryList(doc);
    }

    @Override
    public String getImageDetailJson(Document doc) {
        com.ess.anime.wallpaper.bean.ImageBean.ImageJsonBuilder builder =
                new com.ess.anime.wallpaper.bean.ImageBean.ImageJsonBuilder();
        try {
            GalleryDetail detail = parseGalleryDetail(doc);
            if (TextUtils.isEmpty(detail.title) && detail.pageCount <= 0) {
                return builder.build();
            }

            // Fetch all image pages for the full pageUrls list.
            // This runs on a background thread (HandlerFuture.IO).
            List<String> pageUrls = new ArrayList<>();
            try {
                android.content.Context ctx =
                        com.ess.anime.wallpaper.MyApp.getInstance();
                if (ctx != null && !TextUtils.isEmpty(detail.firstImagePageUrl)
                        && detail.pageCount > 0) {
                    EHentaiRequest req = EHentaiRequest.getInstance(ctx);
                    String baseUrl = mWebsiteConfig != null
                            ? ((com.ess.anime.wallpaper.website.EHentaiConfig) mWebsiteConfig).getBaseUrl()
                            : "https://e-hentai.org/";
                    // Cap at 500 pages to avoid runaway requests on huge galleries
                    int count = Math.min(detail.pageCount, 500);
                    if (detail.allImagePageUrls != null && !detail.allImagePageUrls.isEmpty()) {
                        // Parallel: use thumbnail links collected from div#gdt
                        List<String> parallelUrls = detail.allImagePageUrls.size() > count
                                ? detail.allImagePageUrls.subList(0, count)
                                : detail.allImagePageUrls;
                        pageUrls = fetchImageUrlsParallel(req, parallelUrls, baseUrl);
                    } else {
                        // Fallback: sequential "next" chain
                        pageUrls = fetchAllImageUrls(req, detail.firstImagePageUrl, count, baseUrl);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            String title = detail.title;
            if (!TextUtils.isEmpty(detail.titleJpn)) {
                title = title + "\n" + detail.titleJpn;
            }

            String fileUrl = pageUrls.isEmpty() ? detail.thumbUrl : pageUrls.get(0);
            String previewUrl = TextUtils.isEmpty(detail.thumbUrl) ? fileUrl : detail.thumbUrl;

            // The id must equal ThumbBean.id (the gid) or ThumbBean.checkImageBelongs()
            // never matches and the detail screen keeps waiting.
            builder.id(TextUtils.isEmpty(detail.gid) ? detail.firstImagePageUrl : detail.gid)
                    .author("")
                    .tags("")
                    .fileUrl(fileUrl)
                    .previewUrl(previewUrl)
                    .pageUrls(pageUrls);
            // Store title via source field hack: ImageJsonBuilder has no title setter,
            // so we rely on the JSON "title" key if supported; otherwise tags carry it.
            return builder.build();
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

    // ------------------------------------------------------------------
    // Gallery list
    // ------------------------------------------------------------------

    /**
     * Parse a gallery list (any view mode) into ThumbBeans. The thumbnail block (div.glthumb)
     * holds no gallery link in Compact view, so each row / card is matched by its
     * /g/{gid}/{token}/ link instead.
     */
    public List<ThumbBean> parseGalleryList(Document doc) {
        List<ThumbBean> list = new ArrayList<>();
        if (doc == null) {
            return list;
        }
        try {
            Elements items = doc.select("table.itg > tbody > tr, div.itg > div.gl1t");
            Set<String> seen = new HashSet<>();
            for (Element item : items) {
                try {
                    Matcher m = null;
                    for (Element a : item.select("a[href]")) {
                        Matcher candidate = GID_TOKEN.matcher(a.attr("href"));
                        if (candidate.find()) {
                            m = candidate;
                            break;
                        }
                    }
                    // Header rows have no gallery link
                    if (m == null || !seen.add(m.group(1))) {
                        continue;
                    }
                    String gid = m.group(1);
                    String token = m.group(2);

                    // Note: title not stored (ThumbBean has no title field;
                    // detail page shows title via ImageBean)

                    String realSize = "";
                    for (Element info : item.select(".gl4c, .gl5t, .gl3e, .glthumb")) {
                        Matcher pm = PAGES.matcher(info.text());
                        if (pm.find()) {
                            realSize = pm.group(1) + "P";
                            break;
                        }
                    }

                    // linkToShow must be the absolute detail URL: consumers
                    // (ThumbBean.getImageDetailIfNeed, BatchDownloadHelper) fetch it directly.
                    ThumbBean bean = new ThumbBean(
                            gid, 0, 0, findThumbUrl(item), realSize,
                            mWebsiteConfig.getPostDetailUrl(gid + "/" + token));
                    // Loading a gallery's details fetches all of its image pages; doing that for
                    // every list item up front is a request burst that gets the client
                    // rate-limited. Details load when the gallery is opened instead.
                    bean.needPreloadImageDetail = false;
                    list.add(bean);
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Thumbnail URL of a list item; lazily loaded rows keep it in data-src. */
    private static String findThumbUrl(Element item) {
        Element img = item.selectFirst(".glthumb img, .gl3t img, .gl1e img");
        if (img == null) {
            return "";
        }
        String url = img.attr("data-src");
        if (TextUtils.isEmpty(url)) {
            url = img.attr("src");
        }
        return url.startsWith("data:") ? "" : url;
    }

    // ------------------------------------------------------------------
    // Gallery detail
    // ------------------------------------------------------------------

    /** Result of detail parsing: title, page count, and first image-page link. */
    public static class GalleryDetail {
        public String gid = "";
        public String title = "";
        public String titleJpn = "";
        public int pageCount = 0;
        public String firstImagePageUrl = "";
        public String thumbUrl = "";
        public double rating = 0;
        /** All image-page URLs found in div#gdt (may be fewer than pageCount if thumbnails are paginated). */
        public List<String> allImagePageUrls = new ArrayList<>();
    }

    public GalleryDetail parseGalleryDetail(Document doc) {
        GalleryDetail d = new GalleryDetail();
        if (doc == null) {
            return d;
        }
        try {
            Element h1 = doc.selectFirst("h1#gn");
            if (h1 != null) {
                d.title = h1.text();
            }
            Element h2 = doc.selectFirst("h1#gj");
            if (h2 != null) {
                d.titleJpn = h2.text();
            }
            Element pagesEl = doc.selectFirst("div#gdd td.gdt2");
            if (pagesEl == null) {
                // fallback: search whole doc text
                Matcher m = PAGES.matcher(doc.text());
                if (m.find()) {
                    d.pageCount = safeInt(m.group(1));
                }
            } else {
                // gdd table: find the row whose gdt1 text is "Length:"
                Elements rows = doc.select("div#gdd tr");
                for (Element row : rows) {
                    Element k = row.selectFirst("td.gdt1");
                    Element v = row.selectFirst("td.gdt2");
                    if (k != null && v != null && k.text().contains("Length")) {
                        Matcher m = PAGES.matcher(v.text());
                        if (m.find()) {
                            d.pageCount = safeInt(m.group(1));
                        }
                        break;
                    }
                }
            }
            Element firstImg = doc.selectFirst("div#gdt a[href]");
            if (firstImg != null) {
                d.firstImagePageUrl = firstImg.attr("href");
            }
            // Collect all image-page URLs for parallel fetching (avoids sequential "next" chain)
            try {
                Elements thumbLinks = doc.select("div#gdt a[href]");
                for (Element a : thumbLinks) {
                    String href = a.attr("href");
                    if (!TextUtils.isEmpty(href) && !d.allImagePageUrls.contains(href)) {
                        d.allImagePageUrls.add(href);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (Element script : doc.select("script")) {
                Matcher gm = GID_VAR.matcher(script.data());
                if (gm.find()) {
                    d.gid = gm.group(1);
                    break;
                }
            }
            if (TextUtils.isEmpty(d.gid)) {
                Matcher gm = IMAGE_PAGE_GID.matcher(d.firstImagePageUrl);
                if (gm.find()) {
                    d.gid = gm.group(1);
                }
            }
            Element cover = doc.selectFirst("div#gd1 img[src]");
            if (cover != null) {
                d.thumbUrl = cover.attr("src");
            } else {
                // Current layout draws the cover as a CSS background:
                // <div id="gd1"><div style="... background:transparent url(https://ehgt.org/...) ..."></div></div>
                Element coverDiv = doc.selectFirst("div#gd1 div[style]");
                Matcher um = coverDiv != null ? CSS_URL.matcher(coverDiv.attr("style")) : null;
                if (um != null && um.find()) {
                    d.thumbUrl = um.group(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return d;
    }

    /**
     * Fetch every image page and extract the direct image URL.
     * Sequential fallback: follows each page's "next" link starting from the first image page.
     * This is N requests for N pages; callers run it on a background thread.
     */
    public List<String> fetchAllImageUrls(EHentaiRequest req, String firstImagePageUrl,
                                          int pageCount, String referer) {
        List<String> urls = new ArrayList<>();
        if (req == null || TextUtils.isEmpty(firstImagePageUrl) || pageCount <= 0) {
            return urls;
        }
        // Fallback: sequential "next" chain when we only have the first URL.
        // (Parallel path is used when caller provides the full URL list.)
        try {
            String url = firstImagePageUrl;
            for (int i = 0; i < pageCount; i++) {
                try {
                    String html = req.get(url, referer);
                    Document doc = Jsoup.parse(html);
                    Element img = doc.selectFirst("img#img[src]");
                    if (img != null) {
                        String src = img.attr("src");
                        if (!TextUtils.isEmpty(src)) {
                            urls.add(src);
                        }
                    }
                    Element next = doc.selectFirst("div#i3 a:last-child[href]");
                    if (next == null) {
                        break;
                    }
                    String nextUrl = next.attr("href");
                    if (TextUtils.isEmpty(nextUrl) || nextUrl.equals(url)) {
                        break;
                    }
                    url = nextUrl;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return urls;
    }

    /**
     * Parallel version: fetch all image-page URLs concurrently (IMAGE_PAGE_CONCURRENCY threads).
     * @param imagePageUrls list of image-page URLs in page order
     * @return direct image URLs in the same order; failed pages are skipped
     */
    public List<String> fetchImageUrlsParallel(EHentaiRequest req, List<String> imagePageUrls,
                                               String referer) {
        List<String> result = new ArrayList<>();
        if (req == null || imagePageUrls == null || imagePageUrls.isEmpty()) {
            return result;
        }
        final int n = imagePageUrls.size();
        final String[] out = new String[n]; // index = page order; thread-safe via distinct indices
        final CountDownLatch latch = new CountDownLatch(n);
        ExecutorService executor = Executors.newFixedThreadPool(IMAGE_PAGE_CONCURRENCY);
        try {
            for (int i = 0; i < n; i++) {
                final int index = i;
                final String pageUrl = imagePageUrls.get(i);
                executor.submit(() -> {
                    try {
                        String imgUrl = fetchSingleImageUrl(req, pageUrl, referer);
                        if (!TextUtils.isEmpty(imgUrl)) {
                            out[index] = imgUrl;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            // Total timeout: 5 minutes
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
        for (String s : out) {
            if (!TextUtils.isEmpty(s)) {
                result.add(s);
            }
        }
        return result;
    }

    /** Fetch one image page and extract the direct image URL, with 2 retries. */
    private String fetchSingleImageUrl(EHentaiRequest req, String pageUrl, String referer) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String html = req.get(pageUrl, referer);
                if (TextUtils.isEmpty(html)) {
                    continue;
                }
                Document doc = Jsoup.parse(html);
                Element img = doc.selectFirst("img#img[src]");
                if (img != null) {
                    String src = img.attr("src");
                    if (!TextUtils.isEmpty(src)) {
                        return src;
                    }
                }
                // Empty src on last attempt -> give up
                if (attempt == 2) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // retry on next loop iteration
            }
        }
        return "";
    }

    /** Extract the direct image URL from a single image page document. */
    public String parseImagePage(Document doc) {
        if (doc == null) {
            return "";
        }
        try {
            Element img = doc.selectFirst("img#img[src]");
            return img != null ? img.attr("src") : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Ranklist / tag suggest
    // ------------------------------------------------------------------

    /** Ranklist pages reuse the gallery list layout. */
    public List<ThumbBean> parseRanklist(Document doc) {
        return parseGalleryList(doc);
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
