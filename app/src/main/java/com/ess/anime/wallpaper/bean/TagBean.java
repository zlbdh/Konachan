package com.ess.anime.wallpaper.bean;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class TagBean implements Parcelable {

    public List<String> copyright = new ArrayList<>();  //Copyright, #DD00DD

    public List<String> character = new ArrayList<>();  //Character name, #00AA00

    public List<String> artist = new ArrayList<>();     //Artist of this work, not the original official artist, #CCCC00

    public List<String> circle = new ArrayList<>();     //Circle or collective copyright, #00BBBB

    public List<String> style = new ArrayList<>();      //Distinctive style such as _vocaloid, #FF2020

    public List<String> general = new ArrayList<>();    //General description, #FFFFFF (#EE8887)

    public TagBean() {
    }

    public TagBean(JsonObject tagArray) {
        if (tagArray == null) {
            return;
        }
        for (Entry<String, JsonElement> entry : tagArray.entrySet()) {
            String key = entry.getKey();
            JsonElement element = entry.getValue();
            // Skip non-primitive values instead of crashing and discarding the whole bean
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            String value;
            try {
                value = element.getAsString();
            } catch (Exception ignored) {
                continue;
            }
            switch (value) {
                case "copyright":
                    copyright.add(key);
                    break;
                case "character":
                    character.add(key);
                    break;
                case "artist":
                    artist.add(key);
                    break;
                case "circle":
                    circle.add(key);
                    break;
                case "style":
                    style.add(key);
                    break;
                case "general":
                    general.add(key);
                    break;
            }
        }
    }

    protected TagBean(Parcel in) {
        copyright = in.createStringArrayList();
        character = in.createStringArrayList();
        artist = in.createStringArrayList();
        circle = in.createStringArrayList();
        style = in.createStringArrayList();
        general = in.createStringArrayList();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(copyright);
        dest.writeStringList(character);
        dest.writeStringList(artist);
        dest.writeStringList(circle);
        dest.writeStringList(style);
        dest.writeStringList(general);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<TagBean> CREATOR = new Creator<TagBean>() {
        @Override
        public TagBean createFromParcel(Parcel in) {
            return new TagBean(in);
        }

        @Override
        public TagBean[] newArray(int size) {
            return new TagBean[size];
        }
    };
}
