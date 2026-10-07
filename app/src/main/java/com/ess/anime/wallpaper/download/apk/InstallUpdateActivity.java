package com.ess.anime.wallpaper.download.apk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.utils.SystemUtils;
import java.io.File;

/** 只有用户点击通知或手动选择更新后才进入；缓存 APK 同样重新核验。 */
public class InstallUpdateActivity extends Activity {
    private ApkBean apk;
    private boolean awaitingPermission;
    private boolean verifying;
    private boolean resumed;
    private ApkVerifier.Result pendingResult;

    public static Intent intent(Context context, ApkBean apk) {
        return new Intent(context, InstallUpdateActivity.class)
                .putExtra(Constants.APK_BEAN, apk).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        apk = getIntent().getParcelableExtra(Constants.APK_BEAN);
        TextView status = new TextView(this);
        status.setText("正在核验更新安装包…");
        status.setGravity(Gravity.CENTER);
        setContentView(status);
        verifyThenInstall();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (pendingResult != null) {
            ApkVerifier.Result result = pendingResult;
            pendingResult = null;
            finishVerification(result);
            return;
        }
        if (awaitingPermission) {
            awaitingPermission = false;
            if (canInstall()) verifyThenInstall();
            else {
                Toast.makeText(this, "需要允许本应用安装更新，下载文件已保留", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override protected void onPause() { resumed = false; super.onPause(); }
    private boolean canInstall() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }
    private void verifyThenInstall() {
        if (verifying) return;
        if (apk == null || !apk.isDownloadable()) { finish(); return; }
        verifying = true;
        new Thread(() -> {
            ApkVerifier.Result result = ApkVerifier.verify(this, new File(apk.localFilePath), apk);
            runOnUiThread(() -> {
                verifying = false;
                if (isFinishing() || isDestroyed()) return;
                if (!resumed) pendingResult = result;
                else finishVerification(result);
            });
        }, "verify-update-install").start();
    }
    private void finishVerification(ApkVerifier.Result result) {
        if (!result.valid) {
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
            finish();
        } else if (!canInstall()) {
            awaitingPermission = true;
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
        } else {
            Intent install = SystemUtils.installApk(this, new File(apk.localFilePath), false);
            if (install != null) startActivity(install);
            finish();
        }
    }
}
