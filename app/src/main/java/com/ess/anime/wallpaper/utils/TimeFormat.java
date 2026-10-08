package com.ess.anime.wallpaper.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeFormat {

    // Format as 00:00:00
    public static String durationFormat(long msec) {
        msec = (long) (msec / 1000f);
        long hour = msec / 3600;
        long minute = msec % 3600 / 60;
        long second = msec % 3600 % 60;

        return hour == 0
                ? String.format(Locale.US, "%02d:%02d", minute, second)
                : String.format(Locale.US, "%02d:%02d:%02d", hour, minute, second);
    }

    // Format as 00:00:00.0
    public static String durationFormat2(long msec) {
        long millisecond = (long) (msec % 1000 / 100f);
        msec /= 1000;
        long hour = msec / 3600;
        long minute = msec % 3600 / 60;
        long second = msec % 3600 % 60;

        return hour == 0
                ? String.format(Locale.US, "%02d:%02d.%1d", minute, second, millisecond)
                : String.format(Locale.US, "%02d:%02d:%02d.%1d", hour, minute, second, millisecond);
    }

    // Format a date using the supplied format
    public static String dateFormat(long msec, String format) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.US);
        Date date = new Date(msec);
        return dateFormat.format(date);
    }

    // Convert a formatted string to a long timestamp in milliseconds
    public static long timeToMills(String time, String format) {
        return timeToMillsWithZone(time, format, TimeZone.getDefault());
    }

    // Convert a standard date-time string with a time zone to a long timestamp
    public static long timeToMillsWithZone(String time, String format, TimeZone timeZone) {
        return timeToMillsWithLocaleAndZone(time, format, Locale.getDefault(), timeZone);
    }

    // Convert a standard date-time string using an explicit locale and time zone to a long timestamp
    public static long timeToMillsWithLocaleAndZone(String time, String format, Locale locale, TimeZone timeZone) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(format, locale);
            dateFormat.setTimeZone(timeZone);
            Date date = dateFormat.parse(time);
            return date.getTime();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
