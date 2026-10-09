package com.ess.anime.wallpaper.global;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Locale;

/**
 * App language (locale) switching helper.
 * Supports: System default, English, Simplified Chinese.
 */
public final class LocaleHelper {

    public static final String LANG_SYSTEM = "system";
    public static final String LANG_EN = "en";
    public static final String LANG_ZH_CN = "zh-CN";

    private static final String PREF_KEY = "app_language";

    private LocaleHelper() {
    }

    public static String getSavedLanguage(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(PREF_KEY, LANG_SYSTEM);
    }

    public static void saveLanguage(Context context, String lang) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(PREF_KEY, lang).apply();
    }

    /**
     * Wrap context with the saved locale. Call in Activity.attachBaseContext().
     */
    public static Context wrap(Context context) {
        String lang = getSavedLanguage(context);
        Locale locale = toLocale(lang);
        if (locale == null) {
            return context; // system default
        }
        return updateResources(context, locale);
    }

    /**
     * Apply saved locale to application resources. Call in Application.onCreate()
     * before any UI is inflated.
     */
    public static void applyToApplication(Context appContext) {
        String lang = getSavedLanguage(appContext);
        Locale locale = toLocale(lang);
        if (locale == null) {
            return;
        }
        updateResources(appContext, locale);
    }

    private static Locale toLocale(String lang) {
        if (LANG_EN.equals(lang)) {
            return Locale.ENGLISH;
        } else if (LANG_ZH_CN.equals(lang)) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        return null; // system
    }

    private static Context updateResources(Context context, Locale locale) {
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }

    public static String getDisplayName(Context context, String lang) {
        if (LANG_EN.equals(lang)) {
            return "English";
        } else if (LANG_ZH_CN.equals(lang)) {
            return "简体中文";
        }
        return context.getString(
                context.getResources().getIdentifier("language_system", "string", context.getPackageName()));
    }
}
