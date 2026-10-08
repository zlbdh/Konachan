package com.ess.anime.wallpaper.bean;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class PoolPostBean implements Parcelable {

    public String id;  //(Purpose unknown)

    @SerializedName(value = "poolId", alternate = "pool_id")
    public String poolId;  //Album ID

    @SerializedName(value = "postId", alternate = "post_id")
    public String postId;  //Image ID

    public boolean active;  //(Purpose unknown; possibly indicates public visibility)

    public String sequence;  //Position within the album (a String that may contain NaN)

    @SerializedName(value = "nextPostId", alternate = "next_post_id")
    public String nextPostId;  //Next image ID

    @SerializedName(value = "prevPostId", alternate = "prev_post_id")
    public String prevPostId;  //Previous image ID

    protected PoolPostBean(Parcel in) {
        id = in.readString();
        poolId = in.readString();
        postId = in.readString();
        active = in.readByte() != 0;
        sequence = in.readString();
        nextPostId = in.readString();
        prevPostId = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(poolId);
        dest.writeString(postId);
        dest.writeByte((byte) (active ? 1 : 0));
        dest.writeString(sequence);
        dest.writeString(nextPostId);
        dest.writeString(prevPostId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PoolPostBean> CREATOR = new Creator<PoolPostBean>() {
        @Override
        public PoolPostBean createFromParcel(Parcel in) {
            return new PoolPostBean(in);
        }

        @Override
        public PoolPostBean[] newArray(int size) {
            return new PoolPostBean[size];
        }
    };
}
