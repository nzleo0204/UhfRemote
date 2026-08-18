package com.leo.remote.rfid.sdk.connection.transport;

import com.leo.remote.rfid.sdk.model.*;


import android.app.Application;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import androidx.annotation.NonNull;

/**
 * 使用 Android 网络回调监视 Wi-Fi 连接变化。
 */
public final class WifiNetworkMonitor implements ReaderWifiMonitor {

    private final ConnectivityManager connectivityManager;
    private final ConnectivityManager.NetworkCallback callback;
    private boolean registered;

    public WifiNetworkMonitor(Application application, ReaderWifiMonitor.Listener listener) {
        connectivityManager = application.getSystemService(ConnectivityManager.class);
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                networkCapabilitiesChanged(network);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network,
                    @NonNull NetworkCapabilities capabilities) {
                networkCapabilitiesChanged(network);
            }

            @Override
            public void onLost(@NonNull Network network) {
                if (!hasWifiNetwork()) {
                    listener.onWifiLost();
                }
            }

            private void networkCapabilitiesChanged(Network network) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities == null && !hasWifiNetwork()) { listener.onWifiLost(); }
            }
        };
    }

    public void start() {
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

    public boolean hasWifiNetwork() {
        if (connectivityManager == null) {
            return false;
        }
        Network active = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = active == null
                ? null : connectivityManager.getNetworkCapabilities(active);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
}
