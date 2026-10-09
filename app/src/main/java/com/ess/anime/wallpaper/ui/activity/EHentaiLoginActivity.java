package com.ess.anime.wallpaper.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.website.EHentaiRequest;

/**
 * E-Hentai / ExHentai login via WebView.
 *
 * Opens the E-Hentai login page; when the URL leaves the login flow
 * (forums.e-hentai.org no longer shows act=Login), cookies are harvested
 * from CookieManager and injected into EHentaiRequest's persistent jar.
 *
 * Reference: JHenTai lib/src/network/eh_cookie_manager.dart
 */
public class EHentaiLoginActivity extends BaseActivity {

    private static final String LOGIN_URL =
            "https://forums.e-hentai.org/index.php?act=Login&CODE=01";

    private WebView mWebView;

    public static void launch(Context context) {
        context.startActivity(new Intent(context, EHentaiLoginActivity.class));
    }

    @Override
    protected int layoutRes() {
        return R.layout.activity_ehentai_login;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        setTitle("EHentai Login");
        mWebView = findViewById(R.id.webview);
        android.widget.Button btnImport = findViewById(R.id.btn_import_cookie);
        btnImport.setOnClickListener(v -> showImportCookieDialog());
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setDomStorageEnabled(true);
        mWebView.getSettings().setUserAgentString(EHentaiRequest.UA);
        CookieManager.getInstance().setAcceptCookie(true);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                checkLogin(url);
            }
        });
        mWebView.loadUrl(LOGIN_URL);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, "导入 Cookie（跳过人机验证）");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 1) {
            showImportCookieDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showImportCookieDialog() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("粘贴 Cookie 字符串，如：\nipb_member_id=123; ipb_pass_hash=abc...");
        et.setMinLines(4);
        et.setGravity(android.view.Gravity.TOP);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad, pad, pad);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("从浏览器导入 Cookie")
                .setMessage("在电脑/手机浏览器登录 e-hentai.org 后，按 F12 → Application → Cookies，复制 Cookie 字符串粘贴到这里。")
                .setView(et)
                .setPositiveButton("导入", (d, w) -> {
                    String cookies = et.getText().toString().trim();
                    if (!cookies.isEmpty()) {
                        com.ess.anime.wallpaper.website.EHentaiRequest req =
                                com.ess.anime.wallpaper.website.EHentaiRequest.getInstance(this);
                        req.injectCookies("e-hentai.org", cookies);
                        req.injectCookies("exhentai.org", cookies);
                        if (req.hasLoginCookies("e-hentai.org")) {
                            android.widget.Toast.makeText(this, "Cookie 导入成功", android.widget.Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            android.widget.Toast.makeText(this, "未找到登录 Cookie，请检查格式", android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        // 按钮文字加深，确保可见
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(android.graphics.Color.parseColor("#1976D2"));
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(android.graphics.Color.parseColor("#757575"));
    }

    private void checkLogin(String url) {
        try {
            // Still on the login page -> not logged in yet
            if (url != null && url.contains("act=Login")) {
                return;
            }
            String cookies = CookieManager.getInstance().getCookie("https://e-hentai.org/");
            if (cookies == null) {
                cookies = CookieManager.getInstance().getCookie("https://forums.e-hentai.org/");
            }
            EHentaiRequest req = EHentaiRequest.getInstance(this);
            boolean hadLogin = req.hasLoginCookies("e-hentai.org")
                    || req.hasLoginCookies("exhentai.org");
            if (cookies != null) {
                req.injectCookies("e-hentai.org", cookies);
                req.injectCookies("exhentai.org", cookies);
                req.injectCookies("forums.e-hentai.org", cookies);
            }
            boolean nowLogin = req.hasLoginCookies("e-hentai.org")
                    || req.hasLoginCookies("exhentai.org");
            if (nowLogin && !hadLogin) {
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
                finish();
            } else if (nowLogin) {
                finish();
            }
            // else: keep browsing, user may still be logging in
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
