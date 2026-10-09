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
