package com.ess.anime.wallpaper.bean;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class PoolBean implements Parcelable {

    public String id;  //Album ID

    public String name;  //Album name

    @SerializedName(value = "createdTime", alternate = "created_at")
    public String createdTime;  //Creation time (format: 2016-12-05T12:01:05.115Z)

    @SerializedName(value = "updatedTime", alternate = "updated_at")
    public String updatedTime;  //Last update time (format: 2016-12-05T12:03:05.979Z)

    @SerializedName(value = "userID", alternate = "user_id")
    public String userID;  //User ID

    @SerializedName(value = "isPublic", alternate = "is_public")
    public boolean isPublic;  //Whether it is public

    @SerializedName(value = "postCount", alternate = "post_count")
    public int postCount;  //Number of images in the album

    public String description;  //Album description

    public PoolBean() {
    }

    protected PoolBean(Parcel in) {
        id = in.readString();
        name = in.readString();
        createdTime = in.readString();
        updatedTime = in.readString();
        userID = in.readString();
        isPublic = in.readByte() != 0;
        postCount = in.readInt();
        description = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(createdTime);
        dest.writeString(updatedTime);
        dest.writeString(userID);
        dest.writeByte((byte) (isPublic ? 1 : 0));
        dest.writeInt(postCount);
        dest.writeString(description);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PoolBean> CREATOR = new Creator<PoolBean>() {
        @Override
        public PoolBean createFromParcel(Parcel in) {
            return new PoolBean(in);
        }

        @Override
        public PoolBean[] newArray(int size) {
            return new PoolBean[size];
        }
    };
}
