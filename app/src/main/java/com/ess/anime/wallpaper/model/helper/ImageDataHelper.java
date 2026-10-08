package com.ess.anime.wallpaper.model.helper;

import android.content.Context;
import android.text.TextUtils;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.PostBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.download.image.DownloadBean;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.utils.FileUtils;
import com.ess.anime.wallpaper.website.WebsiteManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageDataHelper {

    public static List<DownloadBean> makeDownloadChosenList(Context context, ThumbBean thumbBean, ImageBean imageBean) {
        String imgHead = WebsiteManager.getInstance().getWebsiteConfig().getSavedImageHead();
        return makeDownloadChosenList(context, thumbBean, imageBean, imgHead);
    }

    // Batch tasks use a prefix captured at startup so switching sites does not rename earlier tasks.
    public static List<DownloadBean> makeDownloadChosenList(Context context, ThumbBean thumbBean,
                                                           ImageBean imageBean, String imgHead) {
        List<DownloadBean> downloadList = new ArrayList<>();
        if (imageBean == null || imageBean.posts == null || imageBean.posts.length == 0
                || imageBean.posts[0] == null) return downloadList;
        PostBean postBean = imageBean.posts[0];
        File file;
        String desc;
        boolean exists;
        // Multi-page gallery (e.g. nhentai): download the currently viewed page
        if (imageBean.hasMultiPages()) {
            int total = imageBean.pageUrls.size();
            int page = Math.max(0, Math.min(imageBean.currentPage, total - 1));
            String pageUrl = imageBean.pageUrls.get(page);
            if (DownloadQualitySelector.isMediaUrl(pageUrl)) {
                desc = context.getString(R.string.dialog_download_large,
                        postBean.jpegWidth, postBean.jpegHeight,
                        FileUtils.computeFileSize(postBean.fileSize),
                        getFileExtension(pageUrl).toUpperCase());
                file = makeFileToSave(imgHead, postBean.id, "-Page" + (page + 1), pageUrl);
                exists = file.exists();
                if (exists) {
                    desc = context.getString(R.string.dialog_download_already, desc);
                }
                downloadList.add(new DownloadBean(1, pageUrl, postBean.fileSize,
                        context.getString(R.string.download_title_large, imgHead, postBean.id + "-p" + (page + 1)),
                        thumbBean.thumbUrl, file.getAbsolutePath(), exists, desc));
                return downloadList;
            }
        }
        // 0.Sample size
        if (DownloadQualitySelector.isMediaUrl(postBean.sampleUrl) && postBean.sampleFileSize != 0
                && !TextUtils.equals(postBean.fileUrl, postBean.sampleUrl)) {
            desc = context.getString(R.string.dialog_download_sample,
                    postBean.sampleWidth, postBean.sampleHeight,
                    FileUtils.computeFileSize(postBean.sampleFileSize),
                    getFileExtension(postBean.sampleUrl).toUpperCase());
            file = makeFileToSave(imgHead, postBean.id, "-Sample", postBean.sampleUrl);
            exists = file.exists();
            if (exists) {
                desc = context.getString(R.string.dialog_download_already, desc);
            }
            downloadList.add(new DownloadBean(0, postBean.sampleUrl, postBean.sampleFileSize,
                    context.getString(R.string.download_title_sample, imgHead, postBean.id), thumbBean.thumbUrl,
                    file.getAbsolutePath(), exists, desc));
        }

        // 1.Large size
        if (DownloadQualitySelector.isMediaUrl(postBean.fileUrl)) {
        desc = context.getString(R.string.dialog_download_large,
                postBean.jpegWidth, postBean.jpegHeight,
                FileUtils.computeFileSize(postBean.fileSize),
                getFileExtension(postBean.fileUrl).toUpperCase());
        file = makeFileToSave(imgHead, postBean.id, "-Large", postBean.fileUrl);
        exists = file.exists();
        if (exists) {
            desc = context.getString(R.string.dialog_download_already, desc);
        }
        downloadList.add(new DownloadBean(1, postBean.fileUrl, postBean.fileSize,
                context.getString(R.string.download_title_large, imgHead, postBean.id), thumbBean.thumbUrl,
                file.getAbsolutePath(), exists, desc));
        }

        // 2.Origin size
        if (DownloadQualitySelector.isMediaUrl(postBean.jpegUrl) && postBean.jpegFileSize != 0
                && !TextUtils.equals(postBean.fileUrl, postBean.jpegUrl)) {
            desc = context.getString(R.string.dialog_download_origin,
                    postBean.jpegWidth, postBean.jpegHeight,
                    FileUtils.computeFileSize(postBean.jpegFileSize),
                    getFileExtension(postBean.jpegUrl).toUpperCase());
            file = makeFileToSave(imgHead, postBean.id, "-Origin", postBean.jpegUrl);
            exists = file.exists();
            if (exists) {
                desc = context.getString(R.string.dialog_download_already, desc);
            }
            downloadList.add(new DownloadBean(2, postBean.jpegUrl, postBean.jpegFileSize,
                    context.getString(R.string.download_title_origin, imgHead, postBean.id), thumbBean.thumbUrl,
                    file.getAbsolutePath(), exists, desc));
        }
        return downloadList;
    }

    private static File makeFileToSave(String imgHead, String postId, String fileType, String url) {
        String extension = "." + getFileExtension(url);
//        url = url.substring(0, url.lastIndexOf(extension) + extension.length()).replaceAll(".com|.net", "");
//        String bitmapName = getImageHead() + FileUtils.encodeMD5String(url) + extension;
        // Use the filename format "site-image_id-image_size", for example Konachan-123456-Sample.jpg
        // This cannot recognize images downloaded before version 1.7 as already downloaded
        String bitmapName = imgHead + postId + fileType + extension;
        return new File(Constants.IMAGE_DIR, bitmapName);
    }

    private static String getFileExtension(String url) {
        String extension = FileUtils.getFileExtension(url);
        if (TextUtils.isEmpty(extension)) {
            extension = "jpg";
        }
        return extension;
    }

}
