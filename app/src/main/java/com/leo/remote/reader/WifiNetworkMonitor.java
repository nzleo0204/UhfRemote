package com.leo.remote.reader;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import androidx.annotation.NonNull;

final class WifiNetworkMonitor {
    interface Listener { void onWifiLost(); }

    private final ConnectivityManager connectivityManager;
    private final ConnectivityManager.NetworkCallback callback;
    private boolean registered;

    WifiNetworkMonitor(Application application, Listener listener) {
        connectivityManager = application.getSystemService(ConnectivityManager.class);
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                if (!hasWifiNetwork()) {
                    listener.onWifiLost();
                }
            }
        };
    }

    void start() {
        if (registered || connectivityManager == null) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        connectivityManager.registerNetworkCallback(request, callback);
        registered = true;
    }

    void stop() {
        if (!registered || connectivityManager == null) {
            return;
        }
        connectivityManager.unregisterNetworkCallback(callback);
        registered = false;
    }

    boolean hasWifiNetwork() {
        if (connectivityManager == null) {
            return false;
        }
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true;
            }
        }
        return false;
    }
}
