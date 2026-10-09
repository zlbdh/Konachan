package com.ess.anime.wallpaper.website.parser;

import android.text.Html;

import com.ess.anime.wallpaper.bean.CommentBean;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared by Konachan and Yande.re
 */
public class GeneralParser extends HtmlParser {

    public GeneralParser(WebsiteConfig websiteConfig) {
        super(websiteConfig);
    }

    @Override
    public List<ThumbBean> getThumbList(Document doc) {
        List<ThumbBean> thumbList = new ArrayList<>();
        Element list = doc.getElementById("post-list-posts");
        if (list == null) {
            return thumbList;
        }

        Elements elements = list.getElementsByTag("li");
        for (Element e : elements) {
            try {
                String id = e.attr("id").replaceAll("[^0-9]", "");
                Element img = e.getElementsByTag("img").first();
                int thumbWidth = Integer.parseInt(img.attr("width")) * 2;
                int thumbHeight = Integer.parseInt(img.attr("height")) * 2;
                String thumbUrl = img.attr("src");
                if (thumbUrl.contains("deleted-preview")) {
                    continue;
                } else if (!thumbUrl.startsWith("http")) {
                    thumbUrl = "https:" + thumbUrl;
                }
                String realSize = "";
                Elements directLink = e.getElementsByClass("directlink-res");
                if (!directLink.isEmpty()) {
                    realSize = directLink.first().ownText();
                }
                String linkToShow = mWebsiteConfig.getPostDetailUrl(id);
                thumbList.add(new ThumbBean(id, thumbWidth, thumbHeight, thumbUrl, realSize, linkToShow));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return thumbList;
    }

    @Override
    public String getImageDetailJson(Document doc) {
        try {
            Element div = doc.getElementById("post-view");
            String json = div.getElementsByTag("script").get(0).html();
            json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
            json = json.replace("\\/", "/");
            // The URL formats differ between the two Konachan modes
            if (!json.contains("http://konachan") && !json.contains("https://konachan")) {
                json = json.replace("//konachan", "https://konachan");
            }
            // Normalize the final "votes":[] to "votes":{} for Lolibooru
            // TODO votes has always been empty so far; revisit this replacement if a site starts returning votes
            json = json.replace("\"votes\":[]", "\"votes\":{}");
            return json;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public List<CommentBean> getCommentList(Document doc) {
        List<CommentBean> commentList = new ArrayList<>();
        Elements elements = doc.getElementsByClass("comment avatar-container");
        for (Element e : elements) {
            try {
                String id = "#" + e.attr("id");
                String author = e.getElementsByTag("h6").first().text();
                String date = e.getElementsByClass("date").first().attr("title");
                String avatar = "";
                Elements avatars = e.getElementsByClass("avatar");
                if (!avatars.isEmpty()) {
                    avatar = avatars.first().attr("src");
                    if (!avatar.startsWith("http")) {
                        avatar = avatar.startsWith("//") ? "https:" + avatar
                                : mWebsiteConfig.getBaseUrl() + avatar;
                    }
                }
                Element body = e.getElementsByClass("body").first();
                Elements blockquote = body.select("blockquote");
                CharSequence quote = Html.fromHtml(blockquote.select("div").html());
                blockquote.remove();
                CharSequence comment = Html.fromHtml(body.html());
                commentList.add(new CommentBean(id, author, date, avatar, quote, comment));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return commentList;
    }

    @Override
    public List<PoolListBean> getPoolListList(Document doc) {
        //Parse the preview image and ID
        List<PoolListBean> poolList = new ArrayList<>();
        PoolListBean poolListBean = new PoolListBean();
        Elements eleScripts = doc.getElementsByTag("script");
        for (Element script : eleScripts) {
            String text = script.html().trim();
            if (text.startsWith("var thumb = $(\"hover-thumb\");")) {
                String[] texts = text.split("\n");
                for (String line : texts) {
                    line = line.trim();
                    if (line.startsWith("Post.register")) {
                        poolListBean = new PoolListBean();
                        line = line.substring(line.indexOf("{"), line.lastIndexOf(")"));
                        JsonObject json = new JsonParser().parse(line).getAsJsonObject();
                        if (json.has("sample_url") && !json.get("sample_url").isJsonNull()) {
                            String thumbUrl = json.get("sample_url").getAsString();
                            thumbUrl = thumbUrl.replace("\\/", "/");
                            if (!thumbUrl.startsWith("http")) {
                                thumbUrl = "https:" + thumbUrl;
                            }
                            poolListBean.thumbUrl = thumbUrl;
                        }
                    } else if (line.startsWith("var hover_row = $")) {
                        line = line.substring(line.indexOf("\"") + 1, line.lastIndexOf("\""));
                        poolListBean.id = line;
                        poolList.add(poolListBean);
                    }
                }
            }
        }

        //Parse details by ID
        for (PoolListBean pool : poolList) {
            Element poolDetail = doc.getElementById(pool.id);
            Elements tds = poolDetail.getElementsByTag("td");
            for (int index = 0; index < tds.size(); index++) {
                Element td = tds.get(index);
                switch (index) {
                    case 0:
                        String linkToShow = td.getElementsByTag("a").first().attr("href");
                        linkToShow = mWebsiteConfig.getBaseUrl() + linkToShow.substring(1);
                        pool.linkToShow = linkToShow;
                        pool.name = td.text();
                        break;
                    case 1:
                        pool.creator = td.text();
                        break;
                    case 2:
                        pool.postCount = td.text();
                        break;
                    case 3:
                        pool.createTime = td.text();
                        break;
                    case 4:
                        pool.updateTime = td.text();
                        break;
                }
            }
        }
        return poolList;
    }

}
