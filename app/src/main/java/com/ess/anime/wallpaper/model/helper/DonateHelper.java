package com.ess.anime.wallpaper.model.helper;

import android.app.Activity;
import android.didikee.donate.AlipayDonate;
import android.didikee.donate.WeiXinDonate;
import android.graphics.BitmapFactory;
import android.widget.Toast;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.global.Constants;

import java.io.File;
import java.io.InputStream;

public class DonateHelper {

    /**
     * Alipay payment
     */
    public static void donateViaAlipay(Activity activity) {
        if (AlipayDonate.hasInstalledAlipayClient(activity)) {
            // The string in the payment QR code, such as https://qr.alipay.com/stx00187oxldjvyo3ofaw60,
            // gives payCode = stx00187oxldjvyo3ofaw60
            String payCode = "tsx08108dsd2cunrfgqsr64";
            AlipayDonate.startAlipayClient(activity, payCode);
        } else {
            Toast.makeText(activity, R.string.alipay_not_installed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Prepare a WeChat payment QR image in advance using the WeChat app
     */
    public static void donateViaWechat(Activity activity) {
        InputStream inputStream = activity.getResources().openRawResource(R.raw.donate_wechat);
        File qrImage = new File(Constants.IMAGE_DONATE, "donate_wechat.png");
        String qrPath = qrImage.getAbsolutePath();
        WeiXinDonate.saveDonateQrImage2SDCard(qrPath, BitmapFactory.decodeStream(inputStream));
        WeiXinDonate.donateViaWeiXin(activity, qrPath);
        Toast.makeText(activity, R.string.choose_qr_image_from_album, Toast.LENGTH_LONG).show();
    }

}
