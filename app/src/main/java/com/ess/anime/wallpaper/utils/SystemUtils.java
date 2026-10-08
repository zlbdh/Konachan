package com.ess.anime.wallpaper.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.util.List;

import androidx.core.content.FileProvider;

/**
 * Use ActivityManager to inspect running Activities and Services
 * ActivityManager requires <uses-permission android:name="android.permission.GET_TASKS" />
 *
 * @author Zero
 */
public class SystemUtils {

    /**
     * Get the current Activity name; fully supported through Android 4.x (API 20).
     * On Android 5.0 (API 21) and later, only this app's Activity names are available
     *
     * @param context Context
     * @return Name of the currently running Activity
     */
    public static String getRunningActivityName(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
//		if (Build.VERSION.SDK_INT < 22) {
        return am.getRunningTasks(1).get(0).topActivity.getClassName();
//		}else{
//		}
    }

    /**
     * Get the current app package name; fully supported through Android 4.x (API 20).
     * On Android 5.0 (API 21) and later, only this app's Activities and the home screen are available
     *
     * @param context Context
     * @return Package name of the currently running app
     */
    public static String getRunningAppPackageName(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return am.getRunningTasks(1).get(0).topActivity.getPackageName();
    }

    /**
     * Check whether this app is in the foreground
     *
     * @param context Context
     * @return Whether this app is in the foreground
     */
    public static boolean isAppRunningForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> infos = activityManager.getRunningAppProcesses();
        // Enumerate processes
        for (ActivityManager.RunningAppProcessInfo info : infos) {
            if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                if (info.processName.equals(context.getApplicationInfo().processName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check whether a Service is running
     *
     * @param context Context
     * @param clazz   Target Service class
     * @return Whether the Service is running
     */
    public static boolean isServiceRunning(Context context, Class<? extends Service> clazz) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
        for (RunningServiceInfo info : services) {
            String className = info.service.getClassName();
            if (clazz.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get available device memory
     *
     * @param context Context
     * @return Available device memory
     */
    public static long getAvailMemory(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        MemoryInfo outInfo = new MemoryInfo();
        am.getMemoryInfo(outInfo);
        return outInfo.availMem;
    }

    /**
     * Get total device memory
     *
     * @param context Context
     * @return Total device memory
     */
    public static long getTotalMemory(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        MemoryInfo outInfo = new MemoryInfo();
        am.getMemoryInfo(outInfo);
        return outInfo.totalMem;
    }

    /**
     * Get the app version code
     *
     * @param context Context
     * @return App version code
     */
    public static int getVersionCode(Context context) {
        int versionCode = 0;
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            versionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }

    /**
     * Get the app version name
     *
     * @param context Context
     * @return App version name
     */
    public static String getVersionName(Context context) {
        String versionName = "";
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionName;
    }

    /**
     * Install an APK
     *
     * @param context Context
     * @param apkFile APK file
     * @param start   Whether to launch installation immediately
     * @return intent
     */
    public static Intent installApk(Context context, File apkFile, boolean start) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri contentUri = FileProvider.getUriForFile(context, getFileProviderAuthority(context), apkFile);
                intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }
            if (start) {
                context.startActivity(intent);
            }
            return intent;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getFileProviderAuthority(Context context) {
        return context.getPackageName() + ".fileprovider";
    }

    /**
     * Get the device Android ID
     *
     * @param context Context
     * @return Android Id
     */
    public static String getAndroidId(Context context) {
        return Settings.System.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Copy a string to the clipboard
     *
     * @param context Context
     * @param str     String
     */
    public static void setClipString(Context context, String str) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            ClipData clipData = ClipData.newPlainText("clip", str);
            manager.setPrimaryClip(clipData);
        }
    }

    /**
     * Get the current clipboard content
     *
     * @param context Context
     * @return Clipboard content
     */
    public static String getFirstClipString(Context context) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null && manager.hasPrimaryClip()) {
            ClipData clipData = manager.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0) {
                CharSequence text = clipData.getItemAt(0).getText();
                return text == null ? "" : text.toString();
            }
        }
        return "";
    }

    public static boolean isActivityActive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
