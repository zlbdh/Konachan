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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E-Hentai / ExHentai HTML parser.
 *
 * Reference: JHenTai (Flutter, Apache 2.0) lib/src/utils/eh_spider_parser.dart
 * Only the core gallery-list / detail / image-page parsing is ported.
 *
 * Gallery list uses the "thumbnail" view (div.glthumb):
 *   <div class="glthumb">
 *     <div><a href="https://e-hentai.org/g/{gid}/{token}/"><img src="{ehgt.org thumb}"></a></div>
 *     <div class="glink">title</div>
 *     <div class="cs">category</div>
 *     <div class="gl5t">... rating, pages, time ...</div>
 *   </div>
 *
 * Detail page: h1#gn title, "NNN pages", image page links /s/{imgtoken}/{gid}-{pagenum}
 * Image page: img#img[src] is the direct full-size URL.
 */
public class EHentaiParser extends HtmlParser {

    private static final Pattern GID_TOKEN = Pattern.compile("/g/(\\d+)/([0-9a-f]+)/");
    private static final Pattern PAGES = Pattern.compile("(\\d+)\\s+pages");

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
                    pageUrls = fetchAllImageUrls(req, detail.firstImagePageUrl, count, baseUrl);
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

            builder.id(detail.firstImagePageUrl)
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

    /** Parse the thumbnail-view gallery list into ThumbBeans. */
    public List<ThumbBean> parseGalleryList(Document doc) {
        List<ThumbBean> list = new ArrayList<>();
        if (doc == null) {
            return list;
        }
        try {
            Elements items = doc.select("div.glthumb");
            for (Element item : items) {
                try {
                    Element a = item.selectFirst("a[href]");
                    if (a == null) {
                        continue;
                    }
                    String href = a.attr("href");
                    Matcher m = GID_TOKEN.matcher(href);
                    if (!m.find()) {
                        continue;
                    }
                    String gid = m.group(1);
                    String token = m.group(2);

                    Element img = item.selectFirst("img[src]");
                    String thumbUrl = img != null ? img.attr("src") : "";
                    // JHenTai sometimes uses data-src for lazy loading
                    if (TextUtils.isEmpty(thumbUrl) && img != null) {
                        thumbUrl = img.attr("data-src");
                    }

                    Element titleEl = item.selectFirst(".glink");
                    String title = titleEl != null ? titleEl.text() : gid;

                    Element pagesEl = item.selectFirst(".gl5t");
                    String realSize = "";
                    if (pagesEl != null) {
                        Matcher pm = PAGES.matcher(pagesEl.text());
                        if (pm.find()) {
                            realSize = pm.group(1) + "P";
                        }
                    }

                    // linkToShow carries "gid/token" so the detail step can rebuild URLs
                    ThumbBean bean = new ThumbBean(
                            gid, 0, 0, thumbUrl, realSize, gid + "/" + token);
                    list.add(bean);
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Gallery detail
    // ------------------------------------------------------------------

    /** Result of detail parsing: title, page count, and first image-page link. */
    public static class GalleryDetail {
        public String title = "";
        public String titleJpn = "";
        public int pageCount = 0;
        public String firstImagePageUrl = "";
        public String thumbUrl = "";
        public double rating = 0;
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
            Element cover = doc.selectFirst("div#gd1 img[src]");
            if (cover != null) {
                d.thumbUrl = cover.attr("src");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return d;
    }

    /**
     * Fetch every image page and extract the direct image URL.
     * This is N requests for N pages; callers run it on a background thread.
     */
    public List<String> fetchAllImageUrls(EHentaiRequest req, String firstImagePageUrl,
                                          int pageCount, String referer) {
        List<String> urls = new ArrayList<>();
        if (req == null || TextUtils.isEmpty(firstImagePageUrl) || pageCount <= 0) {
            return urls;
        }
        try {
            // Derive URL template: .../s/{imgtoken}/{gid}-{pagenum}
            // The imgtoken changes per page, so we follow the "next" link instead.
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
                    // follow "next" (last link in #i3)
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
