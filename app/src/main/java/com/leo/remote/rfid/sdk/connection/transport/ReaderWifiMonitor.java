package com.leo.remote.rfid.sdk.connection.transport;

import com.leo.remote.rfid.sdk.model.*;


public interface ReaderWifiMonitor {
    interface Listener { void onWifiLost(); }

    void start();
    boolean hasWifiNetwork();
}
