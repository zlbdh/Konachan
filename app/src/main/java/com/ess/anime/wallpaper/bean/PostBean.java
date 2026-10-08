package com.ess.anime.wallpaper.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.ess.anime.wallpaper.utils.StringUtils;
import com.google.gson.annotations.SerializedName;

public class PostBean implements Parcelable {

    public String id;  //Konachan image ID

    public String tags;  //Image tags

    @SerializedName(value = "createdTime", alternate = "created_at")
    public long createdTime;  //Upload time (timestamp in seconds)

    @SerializedName(value = "creatorId", alternate = "creator_id")
    public String creatorId;  //Uploader ID

    public String author;  //Uploader username

    public String change;  //(Purpose unknown)

    public String source;  //Image source URL

    public int score;  //Image score

    public String md5;  //MD5 hash

    @SerializedName(value = "fileSize", alternate = "file_size")
    public long fileSize;  //File size displayed on the large-image page, also used as the original-image fallback

    @SerializedName(value = "fileUrl", alternate = "file_url")
    public String fileUrl;  //Image URL displayed on the large-image page, also used as the original-image fallback

    @SerializedName(value = "isShownInIndex", alternate = "is_shown_in_index")
    public boolean isShownInIndex;  //(Purpose unknown)

    @SerializedName(value = "previewUrl", alternate = "preview_url")
    public String previewUrl;  //Thumbnail URL

    @SerializedName(value = "previewWidth", alternate = "preview_width")
    public int previewWidth;  //Proportional thumbnail width

    @SerializedName(value = "previewHeight", alternate = "preview_height")
    public int previewHeight;  //Proportional thumbnail height

    @SerializedName(value = "actualPreviewWidth", alternate = "actual_preview_width")
    public int actualPreviewWidth;  //Actual thumbnail width

    @SerializedName(value = "actualPreviewHeight", alternate = "actual_preview_height")
    public int actualPreviewHeight;  //Actual thumbnail height

    @SerializedName(value = "sampleUrl", alternate = "sample_url")
    public String sampleUrl;  //Sample image URL

    @SerializedName(value = "sampleWidth", alternate = "sample_width")
    public int sampleWidth;  //Actual sample width

    @SerializedName(value = "sampleHeight", alternate = "sample_height")
    public int sampleHeight;  //Actual sample height

    @SerializedName(value = "sampleFileSize", alternate = "sample_file_size")
    public long sampleFileSize;  //Sample file size

    @SerializedName(value = "jpegUrl", alternate = "jpeg_url")
    public String jpegUrl;  //Original image URL

    @SerializedName(value = "jpegWidth", alternate = "jpeg_width")
    public int jpegWidth;  //Actual original width

    @SerializedName(value = "jpegHeight", alternate = "jpeg_height")
    public int jpegHeight;  //Actual original height

    @SerializedName(value = "jpegFileSize", alternate = "jpeg_file_size")
    public long jpegFileSize;  //Original file size; use fileSize above when this value is zero

    public String rating;  //Content rating: s (safe_mode), e (R18), q (questionable)

    @SerializedName(value = "hasChildren", alternate = "has_children")
    public boolean hasChildren;  //Whether related child images exist

    @SerializedName(value = "parentId", alternate = "parent_id")
    public String parentId;  //Related parent image ID for variants with different backgrounds or decorations

    public String status;  //(Purpose unknown)

    public int width;  //Image width (uses jpegWidth)

    public int height;  //Image height (uses jpegHeight)

    @SerializedName(value = "isHeld", alternate = "is_held")
    public boolean isHeld;  //(Purpose unknown)

    @SerializedName(value = "framesPendingString", alternate = "frames_pending_string")
    public String framesPendingString;  //(Purpose unknown)

    @SerializedName(value = "framesPending", alternate = "frames_pending")
    public Object[] framesPending;  //(Purpose unknown; array with an unknown element type)

    @SerializedName(value = "framesString", alternate = "frames_string")
    public String framesString;  //(Purpose unknown)

    public Object[] frames;  //(Purpose unknown; array with an unknown element type)

    @SerializedName(value = "flagDetail", alternate = "flag_detail")
    public String flagDetail;  //(Purpose unknown; this key appears only in some records)

    public PostBean() {
    }

    protected PostBean(Parcel in) {
        id = in.readString();
        tags = in.readString();
        createdTime = in.readLong();
        creatorId = in.readString();
        author = in.readString();
        change = in.readString();
        source = in.readString();
        score = in.readInt();
        md5 = in.readString();
        fileSize = in.readLong();
        fileUrl = in.readString();
        isShownInIndex = in.readByte() != 0;
        previewUrl = in.readString();
        previewWidth = in.readInt();
        previewHeight = in.readInt();
        actualPreviewWidth = in.readInt();
        actualPreviewHeight = in.readInt();
        sampleUrl = in.readString();
        sampleWidth = in.readInt();
        sampleHeight = in.readInt();
        sampleFileSize = in.readLong();
        jpegUrl = in.readString();
        jpegWidth = in.readInt();
        jpegHeight = in.readInt();
        jpegFileSize = in.readLong();
        rating = in.readString();
        hasChildren = in.readByte() != 0;
        parentId = in.readString();
        status = in.readString();
        width = in.readInt();
        height = in.readInt();
        isHeld = in.readByte() != 0;
        framesPendingString = in.readString();
        framesString = in.readString();
        flagDetail = in.readString();
    }

    /**
     * Replace existing values with non-null, nonzero data from newPost
     *
     * @param newPost
     */
    public void replaceDataIfNotNull(PostBean newPost) {
        if (newPost == null) {
            return;
        }

        if (!TextUtils.isEmpty(newPost.id)) {
            id = newPost.id;
        }
        if (!TextUtils.isEmpty(newPost.tags)) {
            tags = newPost.tags;
        }
        if (newPost.createdTime != 0) {
            createdTime = newPost.createdTime;
        }
        if (!TextUtils.isEmpty(newPost.creatorId)) {
            creatorId = newPost.creatorId;
        }
        if (!TextUtils.isEmpty(newPost.author)) {
            author = newPost.author;
        }
        if (!TextUtils.isEmpty(newPost.change)) {
            change = newPost.change;
        }
        if (!TextUtils.isEmpty(newPost.source)) {
            source = newPost.source;
        }
        if (newPost.score != 0) {
            score = newPost.score;
        }
        if (!TextUtils.isEmpty(newPost.md5)) {
            md5 = newPost.md5;
        }
        if (newPost.fileSize != 0) {
            fileSize = newPost.fileSize;
        }
        if (!TextUtils.isEmpty(newPost.fileUrl)) {
            fileUrl = newPost.fileUrl;
        }
        if (newPost.isShownInIndex) {
            isShownInIndex = newPost.isShownInIndex;
        }
        if (!TextUtils.isEmpty(newPost.previewUrl)) {
            previewUrl = newPost.previewUrl;
        }
        if (newPost.previewWidth != 0) {
            previewWidth = newPost.previewWidth;
        }
        if (newPost.previewHeight != 0) {
            previewHeight = newPost.previewHeight;
        }
        if (newPost.actualPreviewWidth != 0) {
            actualPreviewWidth = newPost.actualPreviewWidth;
        }
        if (newPost.actualPreviewHeight != 0) {
            actualPreviewHeight = newPost.actualPreviewHeight;
        }
        if (!TextUtils.isEmpty(newPost.sampleUrl)) {
            sampleUrl = newPost.sampleUrl;
        }
        if (newPost.sampleWidth != 0) {
            sampleWidth = newPost.sampleWidth;
        }
        if (newPost.sampleHeight != 0) {
            sampleHeight = newPost.sampleHeight;
        }
        if (newPost.sampleFileSize != 0) {
            sampleFileSize = newPost.sampleFileSize;
        }
        if (!TextUtils.isEmpty(newPost.jpegUrl)) {
            jpegUrl = newPost.jpegUrl;
        }
        if (newPost.jpegWidth != 0) {
            jpegWidth = newPost.jpegWidth;
        }
        if (newPost.jpegHeight != 0) {
            jpegHeight = newPost.jpegHeight;
        }
        if (newPost.jpegFileSize != 0) {
            jpegFileSize = newPost.jpegFileSize;
        }
        if (!TextUtils.isEmpty(newPost.rating)) {
            rating = newPost.rating;
        }
        if (newPost.hasChildren) {
            hasChildren = newPost.hasChildren;
        }
        if (!TextUtils.isEmpty(newPost.parentId)) {
            parentId = newPost.parentId;
        }
        if (!TextUtils.isEmpty(newPost.status)) {
            status = newPost.status;
        }
        if (newPost.width != 0) {
            width = newPost.width;
        }
        if (newPost.height != 0) {
            height = newPost.height;
        }
        if (newPost.isHeld) {
            isHeld = newPost.isHeld;
        }
        if (!TextUtils.isEmpty(newPost.framesPendingString)) {
            framesPendingString = newPost.framesPendingString;
        }
        if (!TextUtils.isEmpty(newPost.framesString)) {
            framesString = newPost.framesString;
        }
        if (!TextUtils.isEmpty(newPost.flagDetail)) {
            flagDetail = newPost.flagDetail;
        }
    }

    /**
     * Get the image URL with the smallest fileSize for faster previews
     *
     * @return
     */
    public String getMinSizeImageUrl() {
        String url = fileUrl;
        if (!StringUtils.isURL(url)) {
            url = sampleUrl;
        }
        if (!StringUtils.isURL(url)) {
            url = jpegUrl;
        }
        if (sampleFileSize != 0 && StringUtils.isURL(sampleUrl) && !TextUtils.equals(sampleUrl, url) && sampleFileSize <= fileSize) {
            url = sampleUrl;
            if (jpegFileSize != 0 && StringUtils.isURL(jpegUrl) && !TextUtils.equals(jpegUrl, url) && jpegFileSize < sampleFileSize) {
                url = jpegUrl;
            }
        } else if (jpegFileSize != 0 && StringUtils.isURL(jpegUrl)  && !TextUtils.equals(jpegUrl, url) && jpegFileSize < fileSize) {
            url = jpegUrl;
        }
        return url;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(tags);
        dest.writeLong(createdTime);
        dest.writeString(creatorId);
        dest.writeString(author);
        dest.writeString(change);
        dest.writeString(source);
        dest.writeInt(score);
        dest.writeString(md5);
        dest.writeLong(fileSize);
        dest.writeString(fileUrl);
        dest.writeByte((byte) (isShownInIndex ? 1 : 0));
        dest.writeString(previewUrl);
        dest.writeInt(previewWidth);
        dest.writeInt(previewHeight);
        dest.writeInt(actualPreviewWidth);
        dest.writeInt(actualPreviewHeight);
        dest.writeString(sampleUrl);
        dest.writeInt(sampleWidth);
        dest.writeInt(sampleHeight);
        dest.writeLong(sampleFileSize);
        dest.writeString(jpegUrl);
        dest.writeInt(jpegWidth);
        dest.writeInt(jpegHeight);
        dest.writeLong(jpegFileSize);
        dest.writeString(rating);
        dest.writeByte((byte) (hasChildren ? 1 : 0));
        dest.writeString(parentId);
        dest.writeString(status);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeByte((byte) (isHeld ? 1 : 0));
        dest.writeString(framesPendingString);
        dest.writeString(framesString);
        dest.writeString(flagDetail);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PostBean> CREATOR = new Creator<PostBean>() {
        @Override
        public PostBean createFromParcel(Parcel in) {
            return new PostBean(in);
        }

        @Override
        public PostBean[] newArray(int size) {
            return new PostBean[size];
        }
    };
}
