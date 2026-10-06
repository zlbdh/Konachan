package com.ess.anime.wallpaper.website.parser;

import android.text.TextUtils;

import com.ess.anime.wallpaper.bean.PostBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.WebsiteConfig;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性式 dapi XML 解析器（Xbooru / TBIB 等站）。
 * 与 Gelbooru 不同，这些站的 dapi 把字段放在 <post> 标签的属性里（自闭合），
 * 而不是子元素，因此 GelbooruParser 无法解析。
 * 详情页 / 评论 / 图集沿用 Gelbooru 引擎的 HTML 结构，直接继承 GelbooruParser 的实现。
 */
public class AttrDapiParser extends GelbooruParser {

    public AttrDapiParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        Elements elements = doc.getElementsByTag("post");
        for (Element e : elements) {
            try {
                String id = e.attr("id").replaceAll("[^0-9]", "");
                int thumbWidth = parseInt(e.attr("preview_width"));
                int thumbHeight = parseInt(e.attr("preview_height"));
                String thumbUrl = e.attr("preview_url");
                if (!thumbUrl.startsWith("http")) {
                    thumbUrl = "https:" + thumbUrl;
                }
                String realSize = e.attr("width") + " x " + e.attr("height");
                String linkToShow = mWebsiteConfig.getPostDetailUrl(id);
                ThumbBean thumbBean = new ThumbBean(id, thumbWidth, thumbHeight, thumbUrl, realSize, linkToShow);
                thumbBean.tempPost = parseTempPost(e);
                thumbList.add(thumbBean);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return thumbList;
    }

    private PostBean parseTempPost(Element e) {
        try {
            PostBean postBean = new PostBean();
            postBean.id = e.attr("id");
            postBean.tags = e.attr("tags").trim();
            postBean.creatorId = e.attr("creator_id");
            postBean.change = e.attr("change");
            postBean.source = e.attr("source");
            postBean.score = parseInt(e.attr("score"));
            postBean.md5 = e.attr("md5");
            postBean.fileUrl = e.attr("file_url");
            postBean.fileSize = -1;
            postBean.previewUrl = e.attr("preview_url");
            postBean.previewWidth = parseInt(e.attr("preview_width"));
            postBean.previewHeight = parseInt(e.attr("preview_height"));
            postBean.sampleUrl = e.attr("sample_url");
            if (TextUtils.isEmpty(postBean.sampleUrl)) {
                postBean.sampleUrl = postBean.fileUrl;
            }
            postBean.sampleWidth = parseInt(e.attr("sample_width"));
            postBean.sampleHeight = parseInt(e.attr("sample_height"));
            postBean.sampleFileSize = -1;
            postBean.jpegUrl = e.attr("file_url");
            postBean.jpegWidth = parseInt(e.attr("width"));
            postBean.jpegHeight = parseInt(e.attr("height"));
            postBean.jpegFileSize = -1;
            postBean.rating = e.attr("rating");
            postBean.hasChildren = Boolean.parseBoolean(e.attr("has_children"));
            postBean.parentId = e.attr("parent_id");
            postBean.status = e.attr("status");
            postBean.width = parseInt(e.attr("width"));
            postBean.height = parseInt(e.attr("height"));
            return postBean;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
