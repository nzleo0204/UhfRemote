package com.leo.uhf.rfid.sdk.model;

/**
 * 定义对外发布的读写器连接状态。
 */
public enum ReaderConnectionStatus {
    NOT_CONNECTED,
    CONNECTED,
    DISCONNECTED,
    FAILED;

    public static ReaderConnectionStatus from(ReaderState state) {
        if (state.getPhase() == ConnectionPhase.CONNECTED
                || state.getPhase() == ConnectionPhase.UPDATING_PARAMETERS) {
            return CONNECTED;
        }
        if (state.getPhase() == ConnectionPhase.FAILED) {
            return FAILED;
        }
        if (state.getPhase() == ConnectionPhase.DISCONNECTED
                && state.getDisconnectReason().isUnexpected()) {
            return DISCONNECTED;
        }
        return NOT_CONNECTED;
    }
}
