package com.leo.remote.reader;

/** User-facing category for a failed connection attempt. */
public enum ReaderConnectionFailure {
    NONE,
    BLUETOOTH,
    READER;

    static ReaderConnectionFailure from(TransportType transport, ConnectionPhase failedPhase) {
        return transport == TransportType.BLE
                && failedPhase != ConnectionPhase.VERIFYING_MODULE
                && failedPhase != ConnectionPhase.UPDATING_PARAMETERS
                ? BLUETOOTH : READER;
    }
}
