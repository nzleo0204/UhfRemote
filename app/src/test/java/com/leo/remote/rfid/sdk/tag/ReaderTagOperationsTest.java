package com.leo.remote.rfid.sdk.tag;

import com.leo.remote.rfid.sdk.model.*;
import com.leo.remote.rfid.native_bridge.*;
import com.leo.remote.rfid.sdk.connection.*;
import com.leo.remote.rfid.native_bridge.FakeUhfSdkGateway;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Before;
import org.junit.Test;

public final class ReaderTagOperationsTest {
    private FakeUhfSdkGateway gateway;
    private RecordingObserver observer;
    private ReaderTagOperations operations;

    @Before
    public void setUp() {
        gateway = new FakeUhfSdkGateway();
        observer = new RecordingObserver();
        ReaderStatePublisher publisher = new ReaderStatePublisher(Runnable::run);
        publisher.addObserver(observer);
        operations = new ReaderTagOperations(gateway, publisher);
    }

    @Test
    public void clearCurrentTag_alsoClearsIndependentSingleTagMask() throws Exception {
        gateway.readResult = new TagReadResult(new byte[] {0x01},
                new byte[] {0x30, 0x40}, -42);
        operations.read(TagProtocol.ISO_18000_6C, 1, 0, 1, new byte[4]);
        operations.setSingleTagMask(new InventoryMaskConfig(1, 32, 8,
                new byte[] {0x01}));

        operations.clearCurrentTag();

        assertNull(operations.getCurrentTag());
        assertNull(operations.getSingleTagMask());
        assertNull(observer.tag);
        assertNull(observer.mask);
    }

    @Test
    public void sdkOperations_returnGatewayResults() throws Exception {
        gateway.readResult = new TagReadResult(new byte[] {0x01, 0x02},
                new byte[] {0x30, 0x40}, -47);
        gateway.writeStatus = 3;
        gateway.lockStatus = 4;
        gateway.killStatus = 5;

        TagReadResult read = operations.read(TagProtocol.ISO_18000_6C,
                1, 0, 1, new byte[4]);
        assertArrayEquals(gateway.readResult.getData(), read.getData());
        assertArrayEquals(gateway.readResult.getEpc(), read.getEpc());
        assertEquals(gateway.readResult.getRssi(), read.getRssi());
        assertEquals(3, operations.write(TagProtocol.ISO_18000_6C,
                1, 0, 1, new byte[4], new byte[2]));
        assertEquals(4, operations.lock(new byte[4], 1, 2));
        assertEquals(5, operations.kill(new byte[4], new byte[4]));
    }

    @Test
    public void read_withEpc_updatesCurrentTagAndPublishesIt() throws Exception {
        gateway.readResult = new TagReadResult(new byte[] {0x11},
                new byte[] {0x30, 0x40}, -47, "Impinj Monza 4QT", 0xE2003412);

        operations.read(TagProtocol.ISO_18000_6C, 1, 0, 2, new byte[4]);

        assertEquals("3040", operations.getCurrentTag().id);
        assertEquals(-47, operations.getCurrentTag().rssi);
        assertEquals("Impinj Monza 4QT", operations.getCurrentTag().chipModel);
        assertEquals(0xE2003412, operations.getCurrentTag().tidPrefix);
        assertEquals("3040", observer.tag.id);
    }

    @Test
    public void read_withoutEpc_keepsExistingCurrentTag() throws Exception {
        gateway.readResult = new TagReadResult(new byte[] {0x30, 0x40},
                new byte[] {0x30, 0x40}, -42);
        operations.read(TagProtocol.ISO_18000_6C, 1, 0, 1, new byte[4]);
        ReaderTag tag = operations.getCurrentTag();
        gateway.readResult = new TagReadResult(new byte[] {0x11}, null, -47);

        operations.read(TagProtocol.ISO_18000_6C, 1, 0, 3, new byte[4]);

        assertSame(tag, operations.getCurrentTag());
        assertSame(tag, observer.tag);
    }

    @Test
    public void read_epcBankWithoutSeparateEpc_usesReadDataAsCurrentTag() throws Exception {
        gateway.readResult = new TagReadResult(new byte[] {0x30, 0x40}, null, -47);

        operations.read(TagProtocol.ISO_18000_6C, 1, 2, 1, new byte[4]);

        assertEquals("3040", operations.getCurrentTag().id);
        assertEquals("3040", observer.tag.id);
    }

    @Test
    public void read_epcBank_preservesSeparateFullEpcAsCurrentTag() throws Exception {
        gateway.readResult = new TagReadResult(
                new byte[] {0x30, 0x40},
                new byte[] {0x30, 0x40, 0x50, 0x60},
                -47);

        operations.read(TagProtocol.ISO_18000_6C, 6, 2, 1, new byte[4]);

        assertEquals("30405060", operations.getCurrentTag().id);
        assertEquals("30405060", observer.tag.id);
    }

    private static final class RecordingObserver implements ReaderObserver {
        private ReaderTag tag;
        private InventoryMaskConfig mask;

        @Override public void onCurrentTagChanged(ReaderTag tag) { this.tag = tag; }
        @Override public void onSingleTagMaskChanged(InventoryMaskConfig config) { mask = config; }
    }
}
