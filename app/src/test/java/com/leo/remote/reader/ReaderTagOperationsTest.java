package com.leo.remote.reader;

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
    public void readSingleTag_updatesCurrentTagAndPublishesIt() throws Exception {
        ReaderTag tag = new ReaderTag("EPC", "TID", -42, 0, 1);
        gateway.inventoryOnceResult = tag;

        assertSame(tag, operations.readSingleTag());
        assertSame(tag, operations.getCurrentTag());
        assertSame(tag, observer.tag);
    }

    @Test
    public void clearCurrentTag_alsoClearsIndependentSingleTagMask() throws Exception {
        gateway.inventoryOnceResult = new ReaderTag("EPC", "", -42, 0, 1);
        operations.readSingleTag();
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
        gateway.readResult = new byte[] {0x01, 0x02};
        gateway.writeStatus = 3;
        gateway.lockStatus = 4;
        gateway.killStatus = 5;

        assertArrayEquals(gateway.readResult, operations.read(TagProtocol.ISO_18000_6C,
                1, 0, 1, new byte[4]));
        assertEquals(3, operations.write(TagProtocol.ISO_18000_6C,
                1, 0, 1, new byte[4], new byte[2]));
        assertEquals(4, operations.lock(new byte[4], 1, 2));
        assertEquals(5, operations.kill(new byte[4], new byte[4]));
    }

    private static final class RecordingObserver implements ReaderObserver {
        private ReaderTag tag;
        private InventoryMaskConfig mask;

        @Override public void onCurrentTagChanged(ReaderTag tag) { this.tag = tag; }
        @Override public void onSingleTagMaskChanged(InventoryMaskConfig config) { mask = config; }
    }
}
