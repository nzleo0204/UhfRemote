package com.leo.remote.reader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ReaderDomainTest {

    @Test
    public void mapsRm70xxSubtypesExactly() {
        assertEquals(ModuleSubtype.R2000, ModuleSubtype.fromRawValue(0));
        assertEquals(ModuleSubtype.MAGIC_RF, ModuleSubtype.fromRawValue(1));
        assertEquals(ModuleSubtype.R2000_PLUS, ModuleSubtype.fromRawValue(3));
        assertEquals(ModuleSubtype.RM100X, ModuleSubtype.fromRawValue(6));
        assertEquals(ModuleSubtype.UNKNOWN, ModuleSubtype.fromRawValue(2));
    }

    @Test
    public void exposesProtocolCapabilityMatrix() {
        assertEquals(4, ModuleSubtype.R2000.supportedProtocols().size());
        assertEquals(4, ModuleSubtype.R2000_PLUS.supportedProtocols().size());
        assertEquals(List.of(TagProtocol.ISO_18000_6C), List.copyOf(ModuleSubtype.MAGIC_RF.supportedProtocols()));
        assertEquals(List.of(TagProtocol.ISO_18000_6C, TagProtocol.GJB_7377_1),
                List.copyOf(ModuleSubtype.RM100X.supportedProtocols()));
    }

    @Test
    public void protocolSdkValuesAreStable() {
        assertEquals(0, TagProtocol.ISO_18000_6C.getSdkValue());
        assertEquals(1, TagProtocol.ISO_18000_6B.getSdkValue());
        assertEquals(2, TagProtocol.GJB_7377_1.getSdkValue());
        assertEquals(3, TagProtocol.GB_T_29768.getSdkValue());
    }

    @Test
    public void validatesAndRoundTripsHex() {
        byte[] bytes = HexCodec.decode("E2 80 11 60");
        assertArrayEquals(new byte[]{(byte) 0xE2, (byte) 0x80, 0x11, 0x60}, bytes);
        assertEquals("E2801160", HexCodec.encode(bytes, bytes.length));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOddHex() { HexCodec.decode("ABC"); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonHex() { HexCodec.decode("00XZ"); }

    @Test
    public void packsProtocolSpecificAddressHighByte() {
        assertEquals(0x1234, ProtocolEncoding.encodeAddress(TagProtocol.ISO_18000_6C, 0x1234, 7));
        assertEquals(0x07001234, ProtocolEncoding.encodeAddress(TagProtocol.ISO_18000_6B, 0x1234, 7));
        assertEquals(0x07001234, ProtocolEncoding.encodeAddress(TagProtocol.GJB_7377_1, 0x1234, 7));
        assertEquals(0x07001234, ProtocolEncoding.encodeAddress(TagProtocol.GB_T_29768, 0x1234, 7));
    }

    @Test
    public void encodesGbBanksAndProtocolMaskOffsets() {
        assertEquals(3, ProtocolEncoding.encodeBank(TagProtocol.GJB_7377_1, 3, 5));
        assertEquals(0x10, ProtocolEncoding.encodeBank(TagProtocol.GB_T_29768, 1, 5));
        assertEquals(0x35, ProtocolEncoding.encodeBank(TagProtocol.GB_T_29768, 3, 5));
        assertEquals(32, ProtocolEncoding.encodeMaskOffset(TagProtocol.ISO_18000_6C, 32));
        assertEquals(0x07000000, ProtocolEncoding.encodeMaskOffset(TagProtocol.GB_T_29768, 7));
    }

    @Test
    public void derivesWriteLengthInProtocolUnits() {
        byte[] fourBytes = new byte[4];
        assertEquals(2, ProtocolEncoding.writeLength(TagProtocol.ISO_18000_6C, fourBytes));
        assertEquals(4, ProtocolEncoding.writeLength(TagProtocol.ISO_18000_6B, fourBytes));
        assertEquals(2, ProtocolEncoding.writeLength(TagProtocol.GJB_7377_1, fourBytes));
        assertEquals(2, ProtocolEncoding.writeLength(TagProtocol.GB_T_29768, fourBytes));
    }

    @Test
    public void exposesMagicRfDiscretePowerLevelsInTenthsOfDbm() {
        int[] expected = new int[21];
        for (int i = 0; i <= 20; i++) { expected[i] = i * 10; }
        assertArrayEquals(expected, MagicPowerLevels.levels());
    }

    @Test
    public void inventoryMaskOwnsItsByteArray() {
        byte[] source = {(byte) 0xE2, (byte) 0x80, 0x11, 0x60};
        InventoryMaskConfig config = new InventoryMaskConfig(1, 32, 32, source);
        source[0] = 0;
        byte[] returned = config.getMask();
        returned[1] = 0;
        assertArrayEquals(new byte[]{(byte) 0xE2, (byte) 0x80, 0x11, 0x60}, config.getMask());
        assertEquals(4, config.getMaskByteLength());
    }

    @Test(expected = IllegalArgumentException.class)
    public void inventoryMaskRejectsLengthBeyondProvidedBits() {
        new InventoryMaskConfig(1, 32, 17, new byte[2]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void inventoryMaskRejectsUnknownBank() {
        new InventoryMaskConfig(4, 0, 8, new byte[1]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void inventoryMaskRejectsOffsetBeyondSdkRange() {
        new InventoryMaskConfig(1, 0x01000000, 8, new byte[1]);
    }

    @Test
    public void aggregatesByIdAndAdditionalData() {
        InventoryAccumulator accumulator = new InventoryAccumulator();
        accumulator.add("EPC1", "TID1", -60, 1, "chip");
        accumulator.add("EPC1", "TID1", -55, 2, "chip");
        accumulator.add("EPC1", "TID2", -70, 1, "");
        List<InventoryItem> items = accumulator.snapshot();
        assertEquals(2, items.size());
        assertEquals(4, accumulator.getTotalReads());
        assertEquals(3, items.get(0).getCount());
        assertEquals(-55, items.get(0).getRssi());
    }

    @Test
    public void validatesIpv4WithoutDns() {
        assertTrue(ReaderSessionManager.isValidIpv4("192.168.1.20"));
        assertFalse(ReaderSessionManager.isValidIpv4("256.1.1.1"));
        assertFalse(ReaderSessionManager.isValidIpv4("192.168.001.2"));
        assertFalse(ReaderSessionManager.isValidIpv4("reader.local"));
    }

    @Test
    public void mapsReaderConnectionStatusFromGlobalState() {
        assertEquals(ReaderConnectionStatus.NOT_CONNECTED,
                ReaderState.disconnected().getConnectionStatus());
        assertEquals(ReaderConnectionStatus.CONNECTED, new ReaderState.Builder()
                .phase(ConnectionPhase.CONNECTED).build().getConnectionStatus());
        assertEquals(ReaderConnectionStatus.DISCONNECTED, new ReaderState.Builder()
                .phase(ConnectionPhase.DISCONNECTED).disconnectReason(DisconnectReason.LINK_LOST)
                .build().getConnectionStatus());
        assertEquals(ReaderConnectionStatus.FAILED, new ReaderState.Builder()
                .phase(ConnectionPhase.FAILED).disconnectReason(DisconnectReason.SDK_ERROR)
                .build().getConnectionStatus());
        assertEquals(ReaderConnectionStatus.NOT_CONNECTED, new ReaderState.Builder()
                .phase(ConnectionPhase.DISCONNECTED).disconnectReason(DisconnectReason.USER)
                .build().getConnectionStatus());
    }
}
