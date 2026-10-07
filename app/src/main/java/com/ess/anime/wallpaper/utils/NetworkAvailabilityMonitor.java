package com.ess.anime.wallpaper.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

/** 只观察网络恢复，不读取电话或设备身份信息；最低 API 24。 */
public final class NetworkAvailabilityMonitor {
    private NetworkAvailabilityMonitor() { }

    public static void observe(Context context, Runnable onConnected) {
        ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            manager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    onConnected.run();
                }
            });
        }
    }
}
