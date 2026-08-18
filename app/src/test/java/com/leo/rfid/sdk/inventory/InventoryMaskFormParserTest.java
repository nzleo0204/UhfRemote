package com.leo.rfid.sdk.inventory;

import com.leo.rfid.sdk.model.*;
import com.leo.rfid.sdk.storage.*;
import com.leo.rfid.sdk.bridge.*;
import com.leo.rfid.sdk.connect.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 验证盘点掩码表单的协议映射和输入校验。
 */
public final class InventoryMaskFormParserTest {
    @Test
    public void mapsBanksForAllProtocols() {
        assertEquals(3, parse(TagProtocol.ISO_18000_6C, 3, "0", "8", "AA").bank);
        assertEquals(0, parse(TagProtocol.ISO_18000_6B, 2, "0", "8", "AA").bank);
        assertEquals(1, parse(TagProtocol.GJB_7377_1, 0, "0", "8", "AA").bank);
        assertEquals(2, parse(TagProtocol.GB_T_29768, 2, "0", "8", "AA").bank);
    }

    @Test
    public void returnsDecodedMaskAndRequestedBitRange() {
        InventoryMaskConfig config = parse(TagProtocol.ISO_18000_6C, 1,
                "32", "12", "A1B2");

        assertEquals(32, config.offsetBits);
        assertEquals(12, config.lengthBits);
        assertArrayEquals(new byte[]{(byte) 0xA1, (byte) 0xB2}, config.getMask());
    }

    @Test
    public void validatesHexLengthAndProtocolLimits() {
        assertError(InventoryMaskFormParser.Error.HEX_INVALID,
                TagProtocol.ISO_18000_6C, "0", "8", "ABC");
        assertError(InventoryMaskFormParser.Error.LENGTH_EXCEEDS_DATA,
                TagProtocol.ISO_18000_6C, "0", "9", "AA");
        assertError(InventoryMaskFormParser.Error.SIX_B_LENGTH_NOT_BYTE_ALIGNED,
                TagProtocol.ISO_18000_6B, "0", "4", "AA");
        assertError(InventoryMaskFormParser.Error.OFFSET_OUT_OF_RANGE,
                TagProtocol.GJB_7377_1, "256", "8", "AA");
        assertError(InventoryMaskFormParser.Error.DATA_TOO_LONG,
                TagProtocol.ISO_18000_6C, "0", "8", "AA".repeat(65));
    }

    @Test
    public void rejectsMissingAndNegativeNumbers() {
        InventoryMaskFormParser.Result offset = InventoryMaskFormParser.parse(
                TagProtocol.ISO_18000_6C, 1, "-1", "8", "AA");
        InventoryMaskFormParser.Result length = InventoryMaskFormParser.parse(
                TagProtocol.ISO_18000_6C, 1, "0", "", "AA");

        assertFalse(offset.isSuccess());
        assertEquals(InventoryMaskFormParser.Error.OFFSET_INVALID, offset.getError());
        assertFalse(length.isSuccess());
        assertEquals(InventoryMaskFormParser.Error.LENGTH_INVALID, length.getError());
    }

    private static InventoryMaskConfig parse(TagProtocol protocol, int bank, String offset,
            String length, String hex) {
        InventoryMaskFormParser.Result result = InventoryMaskFormParser.parse(
                protocol, bank, offset, length, hex);
        assertTrue(result.isSuccess());
        return result.getConfig();
    }

    private static void assertError(InventoryMaskFormParser.Error expected,
            TagProtocol protocol, String offset, String length, String hex) {
        InventoryMaskFormParser.Result result = InventoryMaskFormParser.parse(
                protocol, 1, offset, length, hex);
        assertFalse(result.isSuccess());
        assertEquals(expected, result.getError());
    }
}
