package com.ess.anime.wallpaper.bean;

import com.ess.anime.wallpaper.R;

import java.util.ArrayList;

public class SearchBean {

    public int colorId;  // Tag color resource ID

    public ArrayList<String> tagList = new ArrayList<>();  // Includes the tag and all its aliases

    public SearchBean(String type) {
        // Values 0–6 (2 has not been observed) identify tag types and their colors
        switch (type) {
            case "0":
            case "2": // Value 2 has not been observed
            case "6": // Value 6 is also white for an unknown reason
                colorId = R.color.color_general;
                break;
            case "1":
                colorId = R.color.color_artist;
                break;
            case "3":
                colorId = R.color.color_copyright;
                break;
            case "4":
                colorId = R.color.color_character;
                break;
            case "5":
                colorId = R.color.color_circle;
                break;
        }
    }
}
