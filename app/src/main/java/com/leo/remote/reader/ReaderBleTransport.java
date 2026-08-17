package com.leo.remote.reader;

import cn.wandersnail.ble.Device;

interface ReaderBleTransport {
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
