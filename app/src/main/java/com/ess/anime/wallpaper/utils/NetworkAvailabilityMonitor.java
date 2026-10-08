package com.ess.anime.wallpaper.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

/** Observe only network recovery; do not read phone or device identifiers. Requires API 24 or later. */
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
