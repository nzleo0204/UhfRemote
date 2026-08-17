package com.leo.remote.reader.transport;

import com.leo.remote.reader.model.*;


public interface ReaderWifiMonitor {
    interface Listener { void onWifiLost(); }

    void start();
    boolean hasWifiNetwork();
}
