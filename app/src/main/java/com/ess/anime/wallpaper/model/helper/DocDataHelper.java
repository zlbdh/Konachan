package com.ess.anime.wallpaper.model.helper;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannedString;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.utils.FileUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class DocDataHelper {

    private final static String TXT_SEARCH_MODE_ENGLISH = "search_mode.html";
    private final static String TXT_TAG_TYPE_DOC_ENGLISH = "tag_type_doc.html";
    private final static String TXT_ADVANCED_SEARCH_DOC_ENGLISH = "advanced_search_doc.html";

    // Search mode help displayed on the search screen
    public static ArrayList<String> getSearchModeDocumentList(Context context) {
        ArrayList<String> docList = new ArrayList<>();
        String fileName = TXT_SEARCH_MODE_ENGLISH;
        InputStream is = null;
        try {
            is = context.getAssets().open(fileName);
            String html = FileUtils.streamToString(is);
            Document document = Jsoup.parse(html);
            Elements modes = document.getElementsByTag("span");
            for (Element mode : modes) {
                docList.add(String.valueOf(Html.fromHtml(mode.html())));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return docList;
    }

    // Tag type documentation
    public static Spanned getTagTypeHelpDoc(Context context) {
        String fileName = TXT_TAG_TYPE_DOC_ENGLISH;
        InputStream is = null;
        try {
            is = context.getAssets().open(fileName);
            String html = FileUtils.streamToString(is);
            return Html.fromHtml(html);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return new SpannedString(context.getString(R.string.dialog_doc_lost));
    }

    // Advanced search documentation
    public static Spanned getAdvancedSearchDoc(Context context) {
        String fileName = TXT_ADVANCED_SEARCH_DOC_ENGLISH;
        InputStream is = null;
        try {
            is = context.getAssets().open(fileName);
            String html = FileUtils.streamToString(is);
            // The reflection hook is unavailable after the AndroidX migration; its SDK source is unclear.
//            return Html.fromHtml(html, null, new HtmlFontSizeTagHandler(context));
            return Html.fromHtml(html);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return new SpannedString(context.getString(R.string.dialog_doc_lost));
    }

}
