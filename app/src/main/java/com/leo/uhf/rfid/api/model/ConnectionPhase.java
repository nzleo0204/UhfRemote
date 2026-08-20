package com.leo.uhf.rfid.api.model;

/**
 * 定义一次读写器连接流程所处的阶段。
 */
public enum ConnectionPhase {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    DISCOVERING_SERVICES,
    ENABLING_NOTIFICATIONS,
    CONNECTING_DATA_CHANNEL,
    VERIFYING_MODULE,
    UPDATING_PARAMETERS,
    CONNECTED,
    DISCONNECTING,
    FAILED
}
