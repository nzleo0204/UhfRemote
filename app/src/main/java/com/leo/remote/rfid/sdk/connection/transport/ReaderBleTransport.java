package com.leo.remote.rfid.sdk.connection.transport;

import com.leo.remote.rfid.sdk.model.*;


import cn.wandersnail.ble.Device;

/**
 * 定义读写器 BLE 传输能力，供连接编排器与测试 Fake 使用。
 */
public interface ReaderBleTransport {
    interface Listener {
        void onPhase(long attemptId, ConnectionPhase phase, String message);
        void onReady(long attemptId);
        void onInboundData(long attemptId, byte[] data);
        void onDisconnected(long attemptId, String message, int errorCode,
                DisconnectReason reason);
    }

    void connect(Device target, long attemptId);
    void disconnect();
    void write(byte[] data);
}
