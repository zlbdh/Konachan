package com.ess.anime.wallpaper.ui.activity.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;

import com.just.agentweb.MiddlewareWebClientBase;

import androidx.annotation.NonNull;

public class HyperlinkActivity extends BaseWebActivity {

    private final static String HYPERLINK = "HYPERLINK";

    public static void launch(Context context, String hyperlink) {
        Intent intent = new Intent(context, HyperlinkActivity.class);
        intent.putExtra(HYPERLINK, hyperlink);
        context.startActivity(intent);
    }

    private String mHyperlink;

    @Override
    protected void init(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            mHyperlink = getIntent().getStringExtra(HYPERLINK);
        } else {
            mHyperlink = savedInstanceState.getString(HYPERLINK);
        }
        super.init(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(HYPERLINK, mHyperlink);
    }

    @Override
    CharSequence title() {
        return "";
    }

    @Override
    boolean showReceivedTitle() {
        return true;
    }

    @Override
    String webUrl() {
        return mHyperlink;
    }

    @Override
    MiddlewareWebClientBase customWebViewClient() {
        return new MiddlewareWebClientBase() {
            @SuppressLint("WebViewClientOnReceivedSslError")
            @Override
            public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
                // 接受所有网站的证书，忽略SSL错误，执行访问网页
                sslErrorHandler.proceed();
            }
        };
    }

    @Override
    void showHelpDialog() {
    }

    @Override
    boolean hasHelpDialog() {
        return false;
    }

}
