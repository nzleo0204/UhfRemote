package com.leo.rfid.sdk.inventory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.leo.rfid.sdk.model.*;
import com.leo.rfid.sdk.connect.ReaderSessionManager;
import com.leo.rfid.sdk.tag.ChipModelFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

/**
 * 验证 RFID 领域模型的默认值和边界行为。
 */
public class ReaderDomainTest {

    @Test
    public void mapsRm70xxSubtypesExactly() {
        assertEquals(ModuleSubtype.R2000, ModuleSubtype.fromRawValue(0));
        assertEquals(ModuleSubtype.RM8011, ModuleSubtype.fromRawValue(1));
        assertEquals(ModuleSubtype.R2000_PLUS, ModuleSubtype.fromRawValue(3));
        assertEquals(ModuleSubtype.RM610, ModuleSubtype.fromRawValue(6));
        assertEquals(ModuleSubtype.UNKNOWN, ModuleSubtype.fromRawValue(2));
    }

    @Test
    public void exposesProtocolCapabilityMatrix() {
        assertEquals(4, ModuleSubtype.R2000.supportedProtocols().size());
        assertEquals(4, ModuleSubtype.R2000_PLUS.supportedProtocols().size());
        assertEquals(List.of(TagProtocol.ISO_18000_6C), List.copyOf(ModuleSubtype.RM8011.supportedProtocols()));
        assertEquals(List.of(TagProtocol.ISO_18000_6C, TagProtocol.GJB_7377_1),
                List.copyOf(ModuleSubtype.RM610.supportedProtocols()));
    }

    @Test
    public void protocolSdkValuesAreStable() {
        assertEquals(0, TagProtocol.ISO_18000_6C.getSdkValue());
        assertEquals(1, TagProtocol.ISO_18000_6B.getSdkValue());
        assertEquals(2, TagProtocol.GJB_7377_1.getSdkValue());
        assertEquals(3, TagProtocol.GB_T_29768.getSdkValue());
    }

    @Test
    public void classifiesEveryDisconnectReason() {
        assertFalse(DisconnectReason.NONE.isUnexpected());
        assertFalse(DisconnectReason.USER.isUnexpected());
        assertFalse(DisconnectReason.TRANSPORT_SWITCH.isUnexpected());
        assertFalse(DisconnectReason.CANCELED.isUnexpected());
        assertFalse(DisconnectReason.APP_EXIT.isUnexpected());
        assertTrue(DisconnectReason.LINK_LOST.isUnexpected());
        assertTrue(DisconnectReason.BLUETOOTH_OFF.isUnexpected());
        assertTrue(DisconnectReason.WIFI_LOST.isUnexpected());
        assertTrue(DisconnectReason.SDK_ERROR.isUnexpected());
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
    public void providesProtocolSpecificDefaultMaskOffsets() {
        assertEquals(0, ProtocolEncoding.defaultMaskOffsetBits(TagProtocol.ISO_18000_6C, 0));
        assertEquals(32, ProtocolEncoding.defaultMaskOffsetBits(TagProtocol.ISO_18000_6C, 1));
        assertEquals(0, ProtocolEncoding.defaultMaskOffsetBits(TagProtocol.ISO_18000_6C, 2));
        assertEquals(0, ProtocolEncoding.defaultMaskOffsetBits(TagProtocol.ISO_18000_6C, 3));
        assertEquals(0, ProtocolEncoding.defaultMaskOffsetBits(TagProtocol.ISO_18000_6B, 0));
        assertEquals(0, ProtocolEncoding.defaultMaskOffsetBits(TagProtocol.GJB_7377_1, 0));
        assertEquals(0, ProtocolEncoding.defaultMaskOffsetBits(TagProtocol.GB_T_29768, 0));
        assertEquals(0, ProtocolEncoding.defaultMaskOffsetBits(TagProtocol.GB_T_29768, 3));
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
    public void exposesRm8011PowerRangeAndPreservesHalfDbm() {
        assertEquals(200, Rm8011PowerLevels.maxTenthsDbm());
        assertEquals("14.5 dBm", Rm8011PowerLevels.format(145));
    }

    @Test
    public void selectsRm610PowerModeAndNonCmtLabels() {
        assertTrue(Rm610PowerLevels.isCmtVersion("RM610-CMT-01"));
        assertFalse(Rm610PowerLevels.isCmtVersion("RM610-01"));
        assertEquals(21, Rm610PowerLevels.cmtValues().length);
        assertEquals("20 dBm", Rm610PowerLevels.formatCmt(200));
        assertEquals("14 dBm", Rm610PowerLevels.formatNonCmt(5));
        assertEquals(5, Rm610PowerLevels.nonCmtIndex("14 dBm"));
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
    public void inventoryMaskMatchesOnlyTargetEpcAfterProtocolOffset() {
        InventoryMaskConfig mask = new InventoryMaskConfig(1, 32, 16,
                new byte[]{(byte) 0xE2, 0x00});
        InventoryItem target = new InventoryItem("E2003412", "", -50, 1, "");
        InventoryItem other = new InventoryItem("30003412", "", -50, 1, "");

        assertTrue(InventoryMaskMatcher.matches(mask, TagProtocol.ISO_18000_6C,
                InventoryArea.C_EPC_ONLY, 0, target));
        assertFalse(InventoryMaskMatcher.matches(mask, TagProtocol.ISO_18000_6C,
                InventoryArea.C_EPC_ONLY, 0, other));
    }

    @Test
    public void inventoryMaskMatchesNonByteAlignedBits() {
        InventoryMaskConfig mask = new InventoryMaskConfig(1, 36, 5,
                new byte[]{0x50});

        assertTrue(InventoryMaskMatcher.matches(mask, TagProtocol.ISO_18000_6C,
                InventoryArea.C_EPC_ONLY, 0,
                new InventoryItem("E500", "", -50, 1, "")));
        assertFalse(InventoryMaskMatcher.matches(mask, TagProtocol.ISO_18000_6C,
                InventoryArea.C_EPC_ONLY, 0,
                new InventoryItem("E400", "", -50, 1, "")));
    }

    @Test
    public void inventoryMaskUsesOnlyVisibleMatchingMemoryBank() {
        InventoryMaskConfig userMask = new InventoryMaskConfig(3, 24, 8,
                new byte[]{0x34});
        InventoryItem item = new InventoryItem("E200", "1234", -50, 1, "");

        assertTrue(InventoryMaskMatcher.matches(userMask, TagProtocol.ISO_18000_6C,
                InventoryArea.C_EPC_USER, 1, item));
        assertFalse(InventoryMaskMatcher.matches(userMask, TagProtocol.ISO_18000_6C,
                InventoryArea.C_EPC_TID, 1, item));
    }

    @Test
    public void inventoryMaskSelectionRestoresValueCapturedBeforeFirstApply() {
        InventoryMaskSelection selection = new InventoryMaskSelection();

        assertTrue(selection.capture(new int[]{1, 0, 3}));
        assertTrue(selection.capture(new int[]{1, 0, 2}));
        assertEquals(3, selection.restoreValue());

        selection.clear();
        assertTrue(selection.capture(new int[]{1, 0, 1}));
        assertEquals(1, selection.restoreValue());
    }

    @Test
    public void inventoryMaskSelectionRejectsMissingQueryValue() {
        InventoryMaskSelection selection = new InventoryMaskSelection();

        assertFalse(selection.capture(null));
        assertFalse(selection.capture(new int[]{1, 0}));
        assertFalse(selection.isCaptured());
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
    public void preservesRecognizedChipWhenLaterReportOmitsIt() {
        InventoryAccumulator accumulator = new InventoryAccumulator();
        accumulator.add("EPC1", "TID1", -60, 1, "芯片 A");
        accumulator.add("EPC1", "TID1", -58, 1, "");
        assertEquals("芯片 A", accumulator.snapshot().get(0).getChipModel());
    }

    @Test
    public void formatsChipModelForDifferentLocales() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.CHINA);
            assertEquals("中文型号", ChipModelFormatter.format(
                    new ReaderTag("EPC", "TID", -50, 0, 1,
                            "English model|中文型号", 0xE2801160)));
            assertEquals("未知(E2801160)", ChipModelFormatter.format(
                    new ReaderTag("EPC", "TID", -50, 0, 1, "", 0xE2801160)));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void fallsBackWhenLocalizedChipNameIsMissing() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.CHINA);
            assertEquals("English model", ChipModelFormatter.format(
                    new ReaderTag("EPC", "TID", -50, 0, 1, "English model|", 0)));
            Locale.setDefault(Locale.US);
            assertEquals("中文型号", ChipModelFormatter.format(
                    new ReaderTag("EPC", "TID", -50, 0, 1, "|中文型号", 0)));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void showsTidPrefixWhenSdkMetadataIsMissing() {
        assertEquals("未知(E2801160)", ChipModelFormatter.format(
                new ReaderTag("EPC", "任意数据", -50, 0, 1, "", 0xE2801160)));
        assertEquals("", ChipModelFormatter.format(
                new ReaderTag("EPC", "任意数据", -50, 0, 1, "", 0)));
    }

    @Test
    public void formatsSdkProvidedChipModel() {
        assertEquals("IMPINJ Monza M750", ChipModelFormatter.format(
                new ReaderTag("EPC", "任意数据", -50, 0, 1,
                        "IMPINJ Monza M750", 0xE2801160)));

        ReaderTag fudan = new ReaderTag("EPC", "任意数据", -50, 0, 1,
                "Fudan FM13UF011E|复旦 FM13UF011E", 0xE0040150);
        String result = ChipModelFormatter.format(fudan);
        assertTrue(result.equals("Fudan FM13UF011E") || result.equals("复旦 FM13UF011E"));
    }

    @Test
    public void exposesProtocolSpecificInventoryAreas() {
        assertEquals(4, InventoryArea.forProtocol(TagProtocol.ISO_18000_6C).size());
        assertEquals(2, InventoryArea.forProtocol(TagProtocol.ISO_18000_6B).size());
        assertEquals(3, InventoryArea.forProtocol(TagProtocol.GJB_7377_1).size());
        assertEquals(InventoryArea.B_UID_ONLY,
                InventoryArea.of(TagProtocol.ISO_18000_6B, 3));
        assertEquals("EPC/TID",
                InventoryArea.C_EPC_TID.getColumnHeader());
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

    @Test
    public void parameterUpdateIsVisibleAsInitializingUntilItCompletes() {
        ReaderState updating = new ReaderState.Builder()
                .phase(ConnectionPhase.UPDATING_PARAMETERS).build();

        assertFalse(updating.isConnected());
        assertTrue(updating.hasTransportLink());
        assertTrue(updating.isInitializing());
        assertEquals(ReaderConnectionStatus.CONNECTED, updating.getConnectionStatus());
    }

    @Test
    public void transportLinkAndOperationReadinessHaveDistinctSemantics() {
        ReaderState dataChannel = new ReaderState.Builder()
                .phase(ConnectionPhase.CONNECTING_DATA_CHANNEL).build();
        ReaderState verifying = new ReaderState.Builder()
                .phase(ConnectionPhase.VERIFYING_MODULE).build();
        ReaderState connected = new ReaderState.Builder()
                .phase(ConnectionPhase.CONNECTED).build();

        assertTrue(dataChannel.hasTransportLink());
        assertFalse(dataChannel.isInitializing());
        assertFalse(dataChannel.isConnected());
        assertTrue(verifying.hasTransportLink());
        assertTrue(verifying.isInitializing());
        assertFalse(verifying.isConnected());
        assertTrue(connected.hasTransportLink());
        assertFalse(connected.isInitializing());
        assertTrue(connected.isConnected());
        assertFalse(ReaderState.disconnected().hasTransportLink());
    }
}
