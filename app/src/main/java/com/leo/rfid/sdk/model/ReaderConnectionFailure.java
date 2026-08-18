package com.leo.rfid.sdk.model;

/**
 * 定义面向用户展示的连接失败类别。
 */
public enum ReaderConnectionFailure {
    NONE,
    BLUETOOTH,
    READER;

    public static ReaderConnectionFailure from(TransportType transport, ConnectionPhase failedPhase) {
        return transport == TransportType.BLE
                && failedPhase != ConnectionPhase.VERIFYING_MODULE
                && failedPhase != ConnectionPhase.UPDATING_PARAMETERS
                ? BLUETOOTH : READER;
    }
}
