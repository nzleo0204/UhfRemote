package com.leo.remote.rfid.sdk.tag;

import com.leo.remote.rfid.sdk.model.*;
import com.leo.remote.rfid.native_bridge.*;
import com.leo.remote.rfid.sdk.connection.*;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SingleTagReadFormatterTest {
    @Test
    public void exposesProtocolDefaults() {
        assertEquals(2, SingleTagReadFormatter.defaultLength(TagProtocol.ISO_18000_6C, 0));
        assertEquals(6, SingleTagReadFormatter.defaultLength(TagProtocol.ISO_18000_6C, 1));
        assertEquals(6, SingleTagReadFormatter.defaultLength(TagProtocol.ISO_18000_6C, 2));
        assertEquals(8, SingleTagReadFormatter.defaultLength(TagProtocol.ISO_18000_6C, 3));
        assertEquals(8, SingleTagReadFormatter.defaultLength(TagProtocol.ISO_18000_6B, 0));
        assertEquals(4, SingleTagReadFormatter.defaultLength(TagProtocol.GB_T_29768, 0));
    }

    @Test
    public void sixCUsesWordLengthAndKeepsIndependentFullEpc() {
        TagReadResult result = new TagReadResult(HexCodec.decode("00112233445566778899"),
                HexCodec.decode("E20034120123456789ABCDEF"), -45);

        SingleTagReadFormatter.Presentation presentation = SingleTagReadFormatter.format(
                result, TagProtocol.ISO_18000_6C, 3, 2);

        assertEquals("USER", presentation.bankLabel);
        assertEquals("00112233", presentation.dataHex);
        assertEquals("E20034120123456789ABCDEF", presentation.fullEpcHex);
        assertEquals(-45, presentation.rssi);
    }

    @Test
    public void sixBUsesByteLength() {
        TagReadResult result = new TagReadResult(HexCodec.decode("001122334455"), null, 0);

        SingleTagReadFormatter.Presentation presentation = SingleTagReadFormatter.format(
                result, TagProtocol.ISO_18000_6B, 0, 3);

        assertEquals("001122", presentation.dataHex);
    }

    @Test
    public void epcBankFallsBackToReturnedEpcAndTidFallsBackToFormatter() {
        TagReadResult epcResult = new TagReadResult(null,
                HexCodec.decode("E20034120123456789ABCDEF"), 0);
        TagReadResult tidResult = new TagReadResult(HexCodec.decode("E28011052000"),
                HexCodec.decode("300833B2DDD9014000000001"), 0, "", 0);

        SingleTagReadFormatter.Presentation epc = SingleTagReadFormatter.format(
                epcResult, TagProtocol.ISO_18000_6C, 1, 6);
        SingleTagReadFormatter.Presentation tid = SingleTagReadFormatter.format(
                tidResult, TagProtocol.ISO_18000_6C, 2, 6);

        assertEquals("E20034120123456789ABCDEF", epc.dataHex);
        assertEquals("未知(E2801105)", tid.chipModel);
    }
}
