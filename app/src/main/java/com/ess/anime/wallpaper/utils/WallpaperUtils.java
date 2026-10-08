package com.ess.anime.wallpaper.utils;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;

import com.ess.anime.wallpaper.R;
import com.qmuiteam.qmui.util.QMUIDeviceHelper;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


public class WallpaperUtils {

    @RequiresApi(api = Build.VERSION_CODES.N)
    @IntDef({FLAG_HOME_SCREEN, FLAG_LOCK_SCREEN, FLAG_BOTH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface WallpaperFlag {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public final static int FLAG_HOME_SCREEN = WallpaperManager.FLAG_SYSTEM;
    @RequiresApi(api = Build.VERSION_CODES.N)
    public final static int FLAG_LOCK_SCREEN = WallpaperManager.FLAG_LOCK;
    @RequiresApi(api = Build.VERSION_CODES.N)
    public final static int FLAG_BOTH = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;

    /**
     * Set wallpaper directly with a selectable destination (Android 7.0+)
     *
     * @param context  Context
     * @param filePath Image file path
     * @param flag     Wallpaper destination: {@link #FLAG_HOME_SCREEN}, {@link #FLAG_LOCK_SCREEN}, or {@link #FLAG_BOTH}.
     * @return Whether the operation succeeded
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean setWallpaperDirectly(Context context, String filePath, @WallpaperFlag int flag) {
        if (context == null || TextUtils.isEmpty(filePath)) {
            return false;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        try {
            // TODO Lock-screen wallpaper does not apply on Xiaomi and Huawei devices
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            int result = wallpaperManager.setBitmap(bitmap, null, true, flag);
            return result != 0;
        } catch (IOException e1) {
            e1.printStackTrace();
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        return false;
    }

    /**
     * Set home-screen and lock-screen wallpaper directly on all supported versions
     *
     * @param context  Context
     * @param filePath Image file path
     * @return Whether the operation succeeded
     */
    public static boolean setWallpaperDirectly(Context context, String filePath) {
        if (context == null || TextUtils.isEmpty(filePath)) {
            return false;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            wallpaperManager.setBitmap(bitmap);
            return true;
        } catch (IOException e1) {
            e1.printStackTrace();
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        return false;
    }

    /**
     * Use a system app to set wallpaper
     *
     * @param activity Activity context
     * @param uri      Image URI
     */
    public static void setWallpaperBySystemApp(Activity activity, Uri uri) {
        if (!SystemUtils.isActivityActive(activity) || uri == null) {
            return;
        }

        Intent intent;
        try {
            if (QMUIDeviceHelper.isHuawei()) {
                ComponentName componentName = new ComponentName("com.android.gallery3d",
                        "com.android.gallery3d.app.Wallpaper");
                intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra("mimeType", "video/*;image/*");
                intent.setData(uri);
                intent.setComponent(componentName);
                activity.startActivity(intent);
                return;
            } else if (QMUIDeviceHelper.isMIUI()) {
                ComponentName componentName = new ComponentName("com.android.thememanager",
                        "com.android.thememanager.activity.WallpaperDetailActivity");
                intent = new Intent("miui.intent.action.START_WALLPAPER_DETAIL");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra("mimeType", "video/*;image/*");
                intent.setData(uri);
                intent.setComponent(componentName);
                activity.startActivity(intent);
                return;
//            } else {
//                intent = WallpaperManager.getInstance(activity).getCropAndSetWallpaperIntent(uri);
//                activity.startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            intent = new Intent(Intent.ACTION_ATTACH_DATA);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra("mimeType", "video/*;image/*");
            intent.setData(uri);
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.action_wallpaper)));
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(activity, R.string.cannot_set_as_wallpaper, Toast.LENGTH_SHORT).show();
        }
    }

}
