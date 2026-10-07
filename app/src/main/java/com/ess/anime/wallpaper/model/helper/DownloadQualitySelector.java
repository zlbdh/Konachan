package com.ess.anime.wallpaper.model.helper;

import com.ess.anime.wallpaper.download.image.DownloadBean;

import java.net.URI;
import java.util.List;

/** 清晰度是 DownloadBean.type，不是动态候选列表的位置。 */
public final class DownloadQualitySelector {
    private DownloadQualitySelector() { }

    public static DownloadBean select(List<DownloadBean> candidates, int quality) {
        if (quality < 0 || quality > 2) return null;
        DownloadBean selected = find(candidates, quality);
        if (selected != null) return selected;
        // 多数图站没有独立原图/样图，其唯一的大图就是可用的原始媒体。
        selected = find(candidates, 1);
        if (selected == null) selected = find(candidates, 2);
        return selected != null ? selected : find(candidates, 0);
    }

    private static DownloadBean find(List<DownloadBean> candidates, int type) {
        for (DownloadBean bean : candidates) {
            if (bean != null && bean.type == type && isMediaUrl(bean.downloadUrl)) return bean;
        }
        return null;
    }

    static boolean isMediaUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
