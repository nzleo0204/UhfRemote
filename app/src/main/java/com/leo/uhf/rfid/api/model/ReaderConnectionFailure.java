package com.leo.uhf.rfid.api.model;

/**
 * 定义面向用户展示的连接失败类别。
 */
public enum ReaderConnectionFailure {
    NONE,
    BLUETOOTH,
    READER;

    public static ReaderConnectionFailure from(ConnectionType transport, ConnectionPhase failedPhase) {
        return transport == ConnectionType.BLE
                && failedPhase != ConnectionPhase.VERIFYING_MODULE
                && failedPhase != ConnectionPhase.UPDATING_PARAMETERS
                ? BLUETOOTH : READER;
    }
}
