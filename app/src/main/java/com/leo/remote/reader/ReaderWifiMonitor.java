package com.leo.remote.reader;

interface ReaderWifiMonitor {
    interface Listener { void onWifiLost(); }

    void start();
    boolean hasWifiNetwork();
}
