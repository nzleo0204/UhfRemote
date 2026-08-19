package com.leo.uhf.rfid.sdk.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 验证连接失败原因到用户可见文案的转换。
 */
public final class ReaderConnectionFailureTest {
    @Test
    public void bleTransportPhasesReportBluetoothFailure() {
        assertEquals(ReaderConnectionFailure.BLUETOOTH,
                ReaderConnectionFailure.from(ConnectionType.BLE, ConnectionPhase.CONNECTING));
        assertEquals(ReaderConnectionFailure.BLUETOOTH,
                ReaderConnectionFailure.from(ConnectionType.BLE,
                        ConnectionPhase.ENABLING_NOTIFICATIONS));
    }

    @Test
    public void handshakeAndWifiPhasesReportReaderFailure() {
        assertEquals(ReaderConnectionFailure.READER,
                ReaderConnectionFailure.from(ConnectionType.BLE,
                        ConnectionPhase.VERIFYING_MODULE));
        assertEquals(ReaderConnectionFailure.READER,
                ReaderConnectionFailure.from(ConnectionType.BLE,
                        ConnectionPhase.UPDATING_PARAMETERS));
        assertEquals(ReaderConnectionFailure.READER,
                ReaderConnectionFailure.from(ConnectionType.WIFI, ConnectionPhase.CONNECTING));
        assertEquals(ReaderConnectionFailure.READER,
                ReaderConnectionFailure.from(ConnectionType.SERIAL, ConnectionPhase.CONNECTING));
    }
}
