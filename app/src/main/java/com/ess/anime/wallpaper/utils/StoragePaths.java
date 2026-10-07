package com.ess.anime.wallpaper.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;

/** 自有文件路径：Android 10 起使用系统提供的应用目录，旧公共文件保持原位。 */
public final class StoragePaths {

    private StoragePaths() {
    }

    public static File imageDirectory(Context context) {
        return usesScopedStorage() ? new File(filesRoot(context), "Konachan/konachan")
                : legacyDirectory("konachan");
    }

    public static File temporaryDirectory(Context context) {
        if (!usesScopedStorage()) {
            return legacyDirectory("temp");
        }
        File cache = context.getExternalCacheDir();
        return new File(cache != null ? cache : context.getCacheDir(), "Konachan/temp");
    }

    public static File donationDirectory(Context context) {
        return usesScopedStorage() ? new File(filesRoot(context), "Konachan/donate")
                : legacyDirectory("donate");
    }

    public static boolean usesScopedStorage() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    private static File filesRoot(Context context) {
        File external = context.getExternalFilesDir(null);
        return external != null ? external : context.getFilesDir();
    }

    private static File legacyDirectory(String child) {
        return new File(Environment.getExternalStorageDirectory(), "Konachan/" + child);
    }
}
