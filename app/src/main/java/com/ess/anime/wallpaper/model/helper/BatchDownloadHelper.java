package com.ess.anime.wallpaper.model.helper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.download.image.DownloadBean;
import com.ess.anime.wallpaper.download.image.DownloadImageManager;
import com.ess.anime.wallpaper.website.WebsiteManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Map;

/**
 * 批量下载：把当前列表页的全部图片按所选清晰度加入下载队列。
 * 在后台线程串行抓取详情页（复用各站点的 HtmlParser），完成后切回主线程提示。
 */
public class BatchDownloadHelper {

    public interface Callback {
        void onProgress(int done, int total);
        void onComplete(int success, int total);
    }

    /**
     * @param quality 0=样图 1=大图 2=原图（对应 ImageDataHelper.makeDownloadChosenList 的下标）
     */
    public static void downloadAll(Context context, List<ThumbBean> thumbBeans, int quality, Callback callback) {
        final Context appContext = context.getApplicationContext();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            int success = 0;
            int total = thumbBeans.size();
            Map<String, String> headerMap = WebsiteManager.getInstance().getRequestHeaders();
            for (int i = 0; i < total; i++) {
                ThumbBean thumbBean = thumbBeans.get(i);
                try {
                    ImageBean imageBean = thumbBean.imageBean;
                    if (imageBean == null) {
                        // 同步抓取详情页
                        Document doc = Jsoup.connect(thumbBean.linkToShow)
                                .headers(headerMap)
                                .timeout(15000)
                                .get();
                        String json = WebsiteManager.getInstance()
                                .getWebsiteConfig()
                                .getHtmlParser()
                                .getImageDetailJson(doc);
                        imageBean = ImageBean.getImageDetailFromJson(json);
                    }
                    if (imageBean != null) {
                        List<DownloadBean> list = ImageDataHelper.makeDownloadChosenList(
                                appContext, thumbBean, imageBean);
                        if (quality >= 0 && quality < list.size()) {
                            DownloadBean bean = list.get(quality);
                            // 跳过已存在的文件（避免重复下载）
                            if (!bean.getFileExists()) {
                                DownloadImageManager.getInstance(appContext).addOrUpdate(bean);
                            }
                            success++;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final int done = i + 1;
                mainHandler.post(() -> callback.onProgress(done, total));
            }
            final int ok = success;
            mainHandler.post(() -> callback.onComplete(ok, total));
        }).start();
    }
}
