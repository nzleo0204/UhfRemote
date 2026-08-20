package com.leo.uhf.rfid.transport.wifi;

import com.leo.uhf.rfid.api.model.*;

/**
 * 监听 Wi-Fi 网络可用性，为读写器网络连接提供生命周期事件。
 */
public interface ReaderWifiMonitor {
    interface Listener { void onWifiLost(); }

    void start();
    boolean hasWifiNetwork();
}
