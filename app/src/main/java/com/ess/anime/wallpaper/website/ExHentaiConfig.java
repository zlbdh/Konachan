package com.ess.anime.wallpaper.website;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.parser.EHentaiParser;
import com.ess.anime.wallpaper.website.parser.HtmlParser;

/**
 * ExHentai (exhentai.org): same engine as E-Hentai, login required.
 *
 * Second-level inheritance: getHtmlParser() is overridden here as well
 * because WebsiteConfig's generic reflection cannot resolve the parser
 * type through two levels of inheritance.
 */
public class ExHentaiConfig extends EHentaiConfig {

    public final static String BASE_URL_EXHENTAI = "https://exhentai.org/";

    private EHentaiParser mParser;

    @Override
    public HtmlParser getHtmlParser() {
        if (mParser == null) {
            mParser = new EHentaiParser(this);
        }
        return mParser;
    }

    @Override
    public String getWebsiteName() {
        return "ExHentai";
    }

    @Override
    public int getWebsiteLogoRes() {
        return R.drawable.ic_website_exhentai;
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL_EXHENTAI;
    }

    @Override
    public String getSavedImageHead() {
        return "exhentai-";
    }
}
