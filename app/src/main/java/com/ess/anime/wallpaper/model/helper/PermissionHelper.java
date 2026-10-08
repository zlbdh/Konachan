package com.ess.anime.wallpaper.model.helper;

import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.WindowManager;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.ui.view.CustomDialog;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class PermissionHelper {

    public final static int REQ_CODE_PERMISSION = 1000;

    /**
     * Check whether the component has a permission
     *
     * @param context     Context
     * @param permissions Permissions to check
     * @return Whether the permission is granted
     */
    public static boolean hasPermissions(Context context, @NonNull String... permissions) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
//        for (String permission : permissions) {
//            int result = ContextCompat.checkSelfPermission(context, permission);
//            if (result == PackageManager.PERMISSION_DENIED) return false;
//
//            String op = AppOpsManagerCompat.permissionToOp(permission);
//            if (TextUtils.isEmpty(op)) continue;
//            result = AppOpsManagerCompat.noteProxyOp(context, op, context.getPackageName());
//            if (result != AppOpsManagerCompat.MODE_ALLOWED) return false;
//
//        }
//        return true;
        return AndPermission.hasPermissions(context, permissions);
    }

    /**
     * Check whether the app lacks required permissions
     *
     * @param context     Context
     * @param permissions Required permissions
     * @return Whether any required permission is missing
     */
    public static List<String> lackPermissions(Context context, String... permissions) {
        ArrayList<String> lacks = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermissions(context, permission)) {
                lacks.add(permission);
            }
        }
        return lacks;
    }

    /**
     * Check permissions
     * Override onActivityResult in the Activity, handle REQ_CODE_PERMISSION, and call hasPermissions() again
     *
     * @param context     Context
     * @param dialogMsg   Permission request dialog message
     * @param listener    Event listener
     * @param permissions Permission group to check
     */
    public static void checkPermissions(Context context, String dialogTitle, String dialogMsg, RequestListener listener, String... permissions) {
        if (AndPermission.hasPermissions(context, permissions)) {
            if (listener != null) {
                listener.onGranted();
            }
        } else {
            CustomDialog.showRequestPermissionDialog(context, dialogTitle, dialogMsg, new CustomDialog.SimpleDialogActionListener() {
                @Override
                public void onPositive() {
                    super.onPositive();
                    if (!hasAlwaysDeniedPermission(context, permissions)) {
                        AndPermission.with(context)
                                .runtime()
                                .permission(permissions)
                                .onGranted(data -> {
                                    if (listener != null) {
                                        listener.onGranted();
                                    }
                                })
                                .onDenied(data -> {
                                    if (listener != null) {
                                        listener.onDenied();
                                    }
                                }).start();
                    } else {
                        AndPermission.with(context)
                                .runtime()
                                .setting()
                                .start(REQ_CODE_PERMISSION);
                    }
                }

                @Override
                public void onNegative() {
                    super.onNegative();
                    if (listener != null) {
                        listener.onDenied();
                    }
                }
            });
        }
    }

    /**
     * Whether permissions have been permanently denied
     *
     * @param context     Context
     * @param permissions Permission group
     * @return Whether permission was permanently denied
     */
    public static boolean hasAlwaysDeniedPermission(Context context, String... permissions) {
        List<String> lackList = lackPermissions(context, permissions);
        if (lackList.isEmpty()) {
            return false;
        }

        String permission = lackList.get(0);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean firstTime = preferences.getBoolean(permission, true);
        if (firstTime) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(permission, false);
            editor.apply();
            return false;
        }
        return AndPermission.hasAlwaysDeniedPermission(context, permission);
    }

    /**
     * Check overlay permission
     *
     * @param context Context
     * @return Whether overlay permission is granted
     */
    public static boolean hasOverlayPermission(Context context) {
        return canDrawOverlays(context) && tryDisplayDialog(context);
    }

    private static boolean tryDisplayDialog(Context context) {
        Dialog dialog = new Dialog(context);
        int overlay = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int alertWindow = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? overlay : alertWindow;
        dialog.getWindow().setType(windowType);
        try {
            dialog.show();
        } catch (Exception e) {
            return false;
        } finally {
            if (dialog.isShowing()) dialog.dismiss();
        }
        return true;
    }

    private static boolean canDrawOverlays(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.M) {
                return Settings.canDrawOverlays(context);
            }
            return reflectionOps(context, "OP_SYSTEM_ALERT_WINDOW");
        }
        return true;
    }

    private static boolean reflectionOps(Context context, String opFieldName) {
        int uid = context.getApplicationInfo().uid;
        try {
            Class<AppOpsManager> appOpsClass = AppOpsManager.class;
            Method method = appOpsClass.getMethod("checkOpNoThrow", Integer.TYPE, Integer.TYPE, String.class);
            Field opField = appOpsClass.getDeclaredField(opFieldName);
            int opValue = (int) opField.get(Integer.class);
            AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int result = (int) method.invoke(appOpsManager, opValue, uid, context.getApplicationContext().getPackageName());
            return result == AppOpsManager.MODE_ALLOWED || result == 4;
        } catch (Throwable e) {
            return true;
        }
    }

    public static void checkStoragePermissions(Context context, RequestListener listener) {
        // App-owned downloads, caches, and favorites need no permission to read other apps' media under scoped storage.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (listener != null) listener.onGranted();
            return;
        }
        String title = context.getString(R.string.dialog_permission_rationale_title);
        String msg = context.getString(R.string.dialog_permission_rationale_msg);
        checkPermissions(context, title, msg, listener, getStoragePermissions());
    }

    /**
     * Permission gate for app-owned storage; Android 10+ requires no storage permission for owned directories and MediaStore items.
     */
    public static String[] getStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new String[0];
        } else {
            return Permission.Group.STORAGE;
        }
    }

    public static boolean hasStoragePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        return hasPermissions(context, getStoragePermissions());
    }

    /** Use only for entry points that read other apps' media; do not gate writes to app-owned downloads with this check. */
    public static String[] getExternalMediaReadPermissions(boolean includeVideo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return includeVideo ? new String[]{android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO}
                    : new String[]{android.Manifest.permission.READ_MEDIA_IMAGES};
        }
        return new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    public static void checkExternalMediaReadPermissions(Context context, boolean includeVideo,
                                                        RequestListener listener) {
        checkPermissions(context, context.getString(R.string.dialog_permission_rationale_title),
                context.getString(R.string.dialog_permission_rationale_msg), listener,
                getExternalMediaReadPermissions(includeVideo));
    }

    public interface RequestListener {
        void onGranted();

        void onDenied();
    }

    public static class SimpleRequestListener implements RequestListener {

        @Override
        public void onGranted() {
        }

        @Override
        public void onDenied() {
        }
    }
}
