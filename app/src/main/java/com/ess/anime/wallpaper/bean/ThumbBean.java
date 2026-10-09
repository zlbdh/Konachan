package com.ess.anime.wallpaper.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.volley.Request;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.http.HandlerFuture;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.website.WebsiteManager;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;

import java.util.Map;

public class ThumbBean implements Parcelable {

    public String id;
    public int thumbWidth;
    public int thumbHeight;
    public String thumbUrl;
    public String realSize;
    public String linkToShow;
    public ImageBean imageBean;
    public boolean needPreloadImageDetail = true; // Some sites, such as Wallhaven, rate-limit API requests, so avoid preloading image details

    //Temporarily store partial image information
    //Some sites, such as Gelbooru, provide partial image information when parsing ThumbBean
    //After parsing ImageBean, merge the temporary information into imageBean.posts[0]
    public PostBean tempPost;

    public ThumbBean(String id, int thumbWidth, int thumbHeight, String thumbUrl, String realSize, String linkToShow) {
        this.id = id;
        this.thumbWidth = thumbWidth;
        this.thumbHeight = thumbHeight;
        this.thumbUrl = thumbUrl;
        this.realSize = realSize;
        this.linkToShow = linkToShow;
    }

    // Check whether the ImageBean belongs to this ThumbBean
    public boolean checkImageBelongs(ImageBean imageBean) {
        try {
            return TextUtils.equals(id, imageBean.posts[0].id);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // After parsing ImageBean, merge tempPost into imageBean.posts[0]
    public synchronized void checkToReplacePostData() {
        try {
            if (tempPost != null && imageBean != null
                    && imageBean.posts != null && imageBean.posts.length > 0) {
                PostBean postBean = imageBean.posts[0];
                if (postBean != null) {
                    postBean.replaceDataIfNotNull(tempPost);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get image details
    public void getImageDetailIfNeed(String httpTag) {
        if (imageBean == null) {
            String url = linkToShow;
            Map<String, String> headerMap = WebsiteManager.getInstance().getRequestHeaders();
            final int[] attempts = {0};
            OkHttp.OkHttpCallback callback = new OkHttp.OkHttpCallback() {
                @Override
                public void onFailure(int errorCode, String errorMessage) {
                    // Retry at most twice; a permanently broken URL must not loop forever.
                    if (++attempts[0] <= 2) {
                        OkHttp.connect(url, httpTag, headerMap, this, Request.Priority.NORMAL);
                    }
                }

                @Override
                public void onSuccessful(String body) {
                    HandlerFuture.ofWork(body)
                            .applyThen(body1 -> {
                                return WebsiteManager.getInstance()
                                        .getWebsiteConfig()
                                        .getHtmlParser()
                                        .getImageDetailJson(Jsoup.parse(body1));
                            })
                            .runOn(HandlerFuture.IO.UI)
                            .applyThen(json -> {
                                // Notify PostFragment, PoolFragment, ImageFragment, and DetailFragment
                                EventBus.getDefault().post(new MsgBean(Constants.GET_IMAGE_DETAIL, json));
                            });
                }
            };
            OkHttp.connect(url, httpTag, headerMap, callback, Request.Priority.NORMAL);
        }
    }

    protected ThumbBean(Parcel in) {
        id = in.readString();
        thumbWidth = in.readInt();
        thumbHeight = in.readInt();
        thumbUrl = in.readString();
        realSize = in.readString();
        linkToShow = in.readString();
        imageBean = in.readParcelable(ImageBean.class.getClassLoader());
        needPreloadImageDetail = in.readByte() != 0;
        tempPost = in.readParcelable(PostBean.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeInt(thumbWidth);
        dest.writeInt(thumbHeight);
        dest.writeString(thumbUrl);
        dest.writeString(realSize);
        dest.writeString(linkToShow);
        dest.writeParcelable(imageBean, flags);
        dest.writeByte((byte) (needPreloadImageDetail ? 1 : 0));
        dest.writeParcelable(tempPost, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ThumbBean> CREATOR = new Creator<ThumbBean>() {
        @Override
        public ThumbBean createFromParcel(Parcel in) {
            return new ThumbBean(in);
        }

        @Override
        public ThumbBean[] newArray(int size) {
            return new ThumbBean[size];
        }
    };

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ThumbBean) {
            ThumbBean thumbBean = (ThumbBean) obj;
            return !(this.linkToShow == null || thumbBean.linkToShow == null) && this.linkToShow.equals(thumbBean.linkToShow);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return linkToShow == null ? 0 : linkToShow.hashCode();
    }
}
