package com.leo.rfid.sdk.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 验证连接失败原因到用户可见文案的转换。
 */
public final class ReaderConnectionFailureTest {
    @Test
    public void bleTransportPhasesReportBluetoothFailure() {
        assertEquals(ReaderConnectionFailure.BLUETOOTH,
                ReaderConnectionFailure.from(TransportType.BLE, ConnectionPhase.CONNECTING));
        assertEquals(ReaderConnectionFailure.BLUETOOTH,
                ReaderConnectionFailure.from(TransportType.BLE,
                        ConnectionPhase.ENABLING_NOTIFICATIONS));
    }

    @Test
    public void handshakeAndWifiPhasesReportReaderFailure() {
        assertEquals(ReaderConnectionFailure.READER,
                ReaderConnectionFailure.from(TransportType.BLE,
                        ConnectionPhase.VERIFYING_MODULE));
        assertEquals(ReaderConnectionFailure.READER,
                ReaderConnectionFailure.from(TransportType.BLE,
                        ConnectionPhase.UPDATING_PARAMETERS));
        assertEquals(ReaderConnectionFailure.READER,
                ReaderConnectionFailure.from(TransportType.WIFI, ConnectionPhase.CONNECTING));
    }
}
