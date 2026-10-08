package com.ess.anime.wallpaper.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import com.google.android.material.internal.NavigationMenuPresenter;
import com.google.android.material.internal.NavigationMenuView;
import com.google.android.material.navigation.NavigationView;

import java.lang.reflect.Field;

import androidx.annotation.ColorInt;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Get device window properties
 *
 * @author Zero
 */
public class UIUtils {

    /**
     * Get screen width
     *
     * @param context Context
     * @return Screen width
     */
    public static int getScreenWidth(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

    /**
     * Get screen height
     *
     * @param context Context
     * @return Screen height
     */
    public static int getScreenHeight(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.heightPixels;
    }

    /**
     * Get status bar height
     *
     * @param context Context
     * @return Status bar height
     */
    public static int getStatusBarHeight(Context context) {
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return context.getResources().getDimensionPixelOffset(resourceId);
    }

    /**
     * Get ActionBar height
     *
     * @param context Context
     * @return ActionBar height
     */
    public static int getActionBarHeight(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true);
        return TypedValue.complexToDimensionPixelSize(typedValue.data, context.getResources().getDisplayMetrics());
    }

    /**
     * Get bottom navigation bar dimensions
     *
     * @param activity Activity context
     * @return Navigation bar dimensions as Point (width: point.x, height: point.y)
     */
    public static Point getNavigationBarSize(Activity activity) {
        Point appUsablePoint = getAppUsableScreenSize(activity);
        Point realScreenPoint = getRealScreenSize(activity);

        // navigation bar on the right
        if (appUsablePoint.x < realScreenPoint.x) {
            return new Point(realScreenPoint.x - appUsablePoint.x, appUsablePoint.y);
        }

        // navigation bar at the bottom
        if (appUsablePoint.y < realScreenPoint.y) {
            return new Point(appUsablePoint.x, realScreenPoint.y - appUsablePoint.y);
        }

        // navigation bar is not present
        return new Point();
    }

    /**
     * Get the usable screen dimensions
     *
     * @param context context
     * @return Usable screen dimensions as Point (width: point.x, height: point.y)
     */
    public static Point getAppUsableScreenSize(Context context) {
        // Use the application context to get the actual usable area
        context = context.getApplicationContext();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        return point;
    }

    /**
     * Get physical screen dimensions
     *
     * @param activity activity
     * @return Physical screen dimensions as Point (width: point.x, height: point.y)
     */
    public static Point getRealScreenSize(Activity activity) {
        WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point point = new Point();

        if (Build.VERSION.SDK_INT >= 17) {
            display.getRealSize(point);
        } else if (Build.VERSION.SDK_INT >= 16) {
            View decorView = activity.getWindow().getDecorView();
            point.x = decorView.getWidth();
            point.y = decorView.getHeight();
        }
        return point;
    }


    public static int dp2px(Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    public static int sp2px(Context context, float sp) {
        float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (sp * fontScale + 0.5f);
    }

    public static int px2dp(Context context, float px) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (px / scale + 0.5f);
    }

    public static int px2sp(Context context, float px) {
        float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (px / fontScale + 0.5f);
    }

    /**
     * Set background opacity
     *
     * @param activity Activity context
     * @param alpha    Opacity: 1.0 is fully opaque and 0.0 is fully transparent
     */
    public static void setBackgroundAlpha(Activity activity, float alpha) {
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.alpha = alpha;
        activity.getWindow().setAttributes(lp);
    }

    /**
     * Hide the status bar
     * Call from both onCreate and onWindowFocusChanged
     *
     * @param activity Activity context
     */
    public static void hideStatusBar(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View decorView = activity.getWindow().getDecorView();
            int option = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(option);
        }
    }

    /**
     * Hide virtual navigation buttons
     * Call from both onCreate and onWindowFocusChanged
     *
     * @param activity Activity context
     */
    public static void hideNavigationBar(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            View decorView = activity.getWindow().getDecorView();
            int option = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(option);
        }
    }

    /**
     * Get soft keyboard height
     *
     * @param view Current view
     * @return Soft keyboard height as an int
     */
    public static int getSoftInputHeight(View view) {
        Rect r = new Rect();
        view.getWindowVisibleDisplayFrame(r);
        int screenHeight = view.getRootView().getHeight();
        return screenHeight - (r.bottom - r.top);
    }

    /**
     * Show the soft keyboard
     *
     * @param context Context
     * @param view    EditText
     */
    public static void showSoftInput(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.
                getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm.isActive()) {
            imm.showSoftInput(view, 0);
        }
    }

    /**
     * Hide the soft keyboard
     *
     * @param activity Activity context
     */
    public static void closeSoftInput(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.
                getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm.isActive() && activity.getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /**
     * Toggle the soft keyboard
     *
     * @param context Context
     */
    public static void toggleSoftInput(Context context) {
        InputMethodManager imm = (InputMethodManager) context.
                getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm.isActive()) {
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT,
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /**
     * Prevent input-method memory leaks; call from onDestroy()
     *
     * @param destContext Context to release
     */
    public static void fixInputMethodManagerLeak(Context destContext) {
        if (destContext == null) {
            return;
        }

        InputMethodManager imm = (InputMethodManager) destContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }

        String[] params = new String[]{"mCurRootView", "mServedView", "mNextServedView"};
        Field f = null;
        Object obj_get = null;
        for (String param : params) {
            try {
                f = imm.getClass().getDeclaredField(param);
                if (!f.isAccessible()) {
                    f.setAccessible(true);
                }
                obj_get = f.get(imm);
                if (obj_get != null && obj_get instanceof View) {
                    View v_get = (View) obj_get;
                    if (v_get.getContext() == destContext) { // The context retained by InputMethodManager is the one being destroyed
                        f.set(imm, null); // Clear the reference to break the path from the GC root
                    } else {
                        // A different screen is active; leave its context alone and stop the loop to preserve existing behavior
                        break;
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /**
     * Change navigation drawer divider height and color
     *
     * @param navigationView Drawer NavigationView
     * @param color          Color
     * @param height         Height
     */
    public static void setNavigationMenuLineStyle(NavigationView navigationView, @ColorInt final int color, final int height) {
        try {
            Field fieldByPresenter = navigationView.getClass().getDeclaredField("presenter");
            fieldByPresenter.setAccessible(true);
            NavigationMenuPresenter menuPresenter = (NavigationMenuPresenter) fieldByPresenter.get(navigationView);
            Field fieldByMenuView = menuPresenter.getClass().getDeclaredField("menuView");
            fieldByMenuView.setAccessible(true);
            final NavigationMenuView menuView = (NavigationMenuView) fieldByMenuView.get(menuPresenter);
            menuView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(View view) {
                    RecyclerView.ViewHolder viewHolder = menuView.getChildViewHolder(view);
                    if (viewHolder != null && "SeparatorViewHolder".equals(viewHolder.getClass().getSimpleName()) && viewHolder.itemView != null) {
                        if (viewHolder.itemView instanceof FrameLayout) {
                            FrameLayout frameLayout = (FrameLayout) viewHolder.itemView;
                            View line = frameLayout.getChildAt(0);
                            line.setBackgroundColor(color);
                            line.getLayoutParams().height = height;
                        }
                    }
                }

                @Override
                public void onChildViewDetachedFromWindow(View view) {
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the Activity for a View, including on Android 4.x
     *
     * @param view View
     * @return Activity
     */
    public static Activity getActivityFromView(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static int[] getLocationInWindow(View view) {
        int[] location = new int[2];
        view.getLocationInWindow(location);
        return location;
    }

    public static boolean isLandscape(Activity activity) {
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isLandscapeReverse(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        boolean landReverse = rotation == Surface.ROTATION_270;
        return isLandscape(activity) && landReverse;
    }

    public static boolean isPortrait(Activity activity) {
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    public static boolean isPortraitReverse(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        boolean portraitReverse = rotation == Surface.ROTATION_180;
        return isPortrait(activity) && portraitReverse;
    }
}
