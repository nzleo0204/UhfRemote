package com.leo.remote.reader;

public enum ReaderConnectionStatus {
    NOT_CONNECTED,
    CONNECTED,
    DISCONNECTED,
    FAILED;

    public static ReaderConnectionStatus from(ReaderState state) {
        if (state.getPhase() == ConnectionPhase.CONNECTED) {
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
