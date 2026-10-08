package com.ess.anime.wallpaper.pixiv.gif;

import com.ess.anime.wallpaper.MyApp;
import com.ess.anime.wallpaper.global.Constants;

import java.io.File;

public class PixivGifBean {

    public String id;

    public String thumbUrl;

    public String zipUrl;

    public float fps;

    public Long gifTaskId;

    private String gifSavedPath;

    public PixivGifBean(String id) {
        this.id = id;
    }

    // URL for retrieving fps and zipUrl
    public String getJsonUrl() {
        return "https://www.pixiv.net/ajax/illust/" + id + "/ugoira_meta?lang=zh";
    }

    // Pixiv downloads require a Referer request header
    public String getRefererUrl() {
        return "https://www.pixiv.net/artworks/" + id;
    }

    // Local ZIP cache path
    public String getZipCacheDirPath() {
        return MyApp.getInstance().getCacheDir() + File.separator + id;
    }

    // Local ZIP filename
    public String getZipFileName() {
        return id + ".zip";
    }

    // Output GIF path
    public String getGifSavedPath() {
        if (gifSavedPath == null) {
            gifSavedPath = Constants.IMAGE_DIR + File.separator + "Pixiv_" + id + "_" + System.currentTimeMillis() + ".gif";
        }
        return gifSavedPath;
    }


    /********************* Progress states *********************/
    public enum PixivDlState {
        CONNECT_PIXIV, DOWNLOAD_ZIP, EXTRACT_ZIP, MAKE_GIF, FINISH, CANCEL, NOT_GIF, ARTWORK_NOT_EXIST, NEED_LOGIN, LOGIN_EXPIRED
    }

    public PixivDlState state = PixivDlState.CONNECT_PIXIV;
    public float progress;
    public boolean isError;


    /***************************************************/
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PixivGifBean) {
            PixivGifBean pixivGifBean = (PixivGifBean) obj;
            return !(this.id == null || pixivGifBean.id == null) && this.id.equals(pixivGifBean.id);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
