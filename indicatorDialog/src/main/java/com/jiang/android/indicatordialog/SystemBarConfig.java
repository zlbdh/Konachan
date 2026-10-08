package com.jiang.android.indicatordialog;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ViewConfiguration;

import java.lang.reflect.Method;

/**
 * Created by jiang on 2017/5/26.
 */

/**
 * Describes system bar dimensions for the current device configuration: status bar, navigation bar, and ActionBar,
 * plus screen dimensions and related properties.
 */
public class SystemBarConfig {

    private static final String STATUS_BAR_HEIGHT_RES_NAME = "status_bar_height";
    private static final String NAV_BAR_HEIGHT_RES_NAME = "navigation_bar_height";
    private static final String NAV_BAR_HEIGHT_LANDSCAPE_RES_NAME = "navigation_bar_height_landscape";
    private static final String NAV_BAR_WIDTH_RES_NAME = "navigation_bar_width";
    private static final String SHOW_NAV_BAR_RES_NAME = "config_showNavigationBar";

    private final int mStatusBarHeight;
    private final int mActionBarHeight;
    private final boolean mHasNavigationBar;
    private final int mNavigationBarHeight;
    private final int mNavigationBarWidth;
    private final int mContentHeight;
    private final int mContentWidth;
    private final boolean mInPortrait;
    private final float mSmallestWidthDp;

    public SystemBarConfig(Activity activity) {
        Resources res = activity.getResources();
        mInPortrait = (res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
        mSmallestWidthDp = getSmallestWidthDp(activity);
        mStatusBarHeight = getInternalDimensionSize(res, STATUS_BAR_HEIGHT_RES_NAME);
        mActionBarHeight = getActionBarHeight(activity);
        mNavigationBarHeight = getNavigationBarHeight(activity);
        mNavigationBarWidth = getNavigationBarWidth(activity);
        mContentHeight = getContentHeight(activity);
        mContentWidth = getContentWidth(activity);
        mHasNavigationBar = (mNavigationBarHeight > 0);

    }

    // Android system properties can control navigation bar visibility; check whether those properties were overridden.
    // (Adding qemu.hw.mainkeys=1 at the end of build.prop hides the navigation bar)
    // Emulators also use this property.
    // A return value of "1" hides the navigation bar; "0" shows it.
    @TargetApi(19)
    private String getNavBarOverride() {
        String isNavBarOverride = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                Class c = Class.forName("android.os.SystemProperties");
                Method m = c.getDeclaredMethod("get", String.class);
                m.setAccessible(true);
                isNavBarOverride = (String) m.invoke(null, "qemu.hw.mainkeys");
            } catch (Throwable e) {
                isNavBarOverride = null;
            }
        }
        return isNavBarOverride;
    }

    //Get ActionBar height
    @TargetApi(14)
    private int getActionBarHeight(Context context) {
        int result = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            TypedValue tv = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true);
            result = TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
        }
        return result;
    }

    //Get navigation bar height
    @TargetApi(14)
    public int getNavigationBarHeight(Context context) {
        Resources res = context.getResources();
        int result = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (hasNavBar(context)) {
                String key;
                if (mInPortrait) {
                    key = NAV_BAR_HEIGHT_RES_NAME;
                } else {
                    key = NAV_BAR_HEIGHT_LANDSCAPE_RES_NAME;
                }
                return getInternalDimensionSize(res, key);
            }
        }
        return result;
    }

    //Get navigation bar width
    @TargetApi(14)
    private int getNavigationBarWidth(Context context) {
        Resources res = context.getResources();
        int result = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (hasNavBar(context)) {
                return getInternalDimensionSize(res, NAV_BAR_WIDTH_RES_NAME);
            }
        }
        return result;
    }

    //Check whether a navigation bar exists
    @TargetApi(14)
    private boolean hasNavBar(Context context) {
        Resources res = context.getResources();
        int resourceId = res.getIdentifier(SHOW_NAV_BAR_RES_NAME, "bool", "android");
        if (resourceId != 0) {
            boolean hasNav = res.getBoolean(resourceId);
            // Check whether system properties control the navigation bar.
            if ("1".equals(getNavBarOverride())) {
                hasNav = false;
            } else if ("0".equals(getNavBarOverride())) {
                hasNav = true;
            }
            return hasNav;
        } else {
            //Check whether the device has physical menu, back, or home buttons.
            return !ViewConfiguration.get(context).hasPermanentMenuKey();
        }
    }

    //Get the pixel value for a resource
    private int getInternalDimensionSize(Resources res, String key) {
        int result = 0;
        int resourceId = res.getIdentifier(key, "dimen", "android");
        if (resourceId > 0) {
            result = res.getDimensionPixelSize(resourceId);
        }
        return result;
    }

    //Get the smaller screen dimension in dp to determine whether the navigation bar appears at the bottom or right
    @TargetApi(17)
    private float getSmallestWidthDp(Activity activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        float widthDp;
        float heightDp;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            //On API 17+, measured dimensions include virtual navigation buttons; earlier APIs use reflection.
            //The resulting screen height includes the status and navigation bars
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            widthDp = metrics.widthPixels / metrics.density;
            heightDp = metrics.heightPixels / metrics.density;
        } else {
            //The resulting screen height includes the status bar but excludes the navigation bar
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            widthDp = metrics.widthPixels / metrics.density;
            heightDp = (metrics.heightPixels + getNavigationBarWidth(activity)) / metrics.density;
        }
        return Math.min(widthDp, heightDp);
    }

    //Get screen height excluding status and navigation bars
    private int getContentHeight(Activity activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels - getStatusBarHeight();
    }

    //Get screen width excluding the navigation bar
    private int getContentWidth(Activity activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels;
    }

    /**
     * Check whether the navigation bar is at the bottom or right
     *
     * @return true for bottom, false for right
     */
    public boolean isNavigationAtBottom() {
        return (mSmallestWidthDp >= 600 || mInPortrait);
    }

    /**
     * Get status bar height
     *
     * @return Status bar height in pixels
     */
    public int getStatusBarHeight() {
        return mStatusBarHeight;
    }

    /**
     * Get ActionBar height
     *
     * @return ActionBar height in pixels
     */
    public int getActionBarHeight() {
        return mActionBarHeight;
    }

    /**
     * Check whether the device has an on-screen navigation bar
     *
     * @return true if present, otherwise false
     */
    public boolean hasNavigtionBar() {
        return mHasNavigationBar;
    }

    /**
     * Get on-screen navigation bar height
     *
     * @return Navigation bar height in pixels, or zero if absent
     */
    public int getNavigationBarHeight() {
        return mNavigationBarHeight;
    }

    /**
     * Get on-screen navigation bar width when it is displayed vertically on the right
     *
     * @return Navigation bar width in pixels, or zero if absent
     */
    public int getNavigationBarWidth() {
        return mNavigationBarWidth;
    }

    /**
     * Get screen height excluding status and navigation bars
     *
     * @return Screen height in pixels, excluding status and navigation bars
     */
    public int getContentHeight() {
        return mContentHeight;
    }

    /**
     * Get screen width excluding the navigation bar
     *
     * @return Screen width in pixels, excluding the navigation bar
     */
    public int getContentWidth() {
        return mContentWidth;
    }

}