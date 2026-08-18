package com.leo.remote.rfid.sdk.connection;

import com.leo.remote.rfid.sdk.model.*;
import com.leo.remote.rfid.sdk.persistence.*;
import com.leo.remote.rfid.native_bridge.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class ReaderStatePublisherTest {

    @Test
    public void addObserver_dispatchesInitialStateAndCanRemoveObserver() {
        ReaderStatePublisher publisher = new ReaderStatePublisher(Runnable::run);
        RecordingObserver observer = new RecordingObserver();

        publisher.addObserver(observer, () -> observer.initialDispatchCount++);

        assertEquals(1, observer.initialDispatchCount);
        assertEquals(1, publisher.getObserverCount());

        publisher.removeObserver(observer);
        publisher.publishState(ReaderState.disconnected());

        assertEquals(0, publisher.getObserverCount());
        assertNull(observer.state);
    }

    @Test
    public void publishMethods_forwardEveryReaderEvent() {
        ReaderStatePublisher publisher = new ReaderStatePublisher(Runnable::run);
        RecordingObserver observer = new RecordingObserver();
        ReaderState state = new ReaderState.Builder().phase(ConnectionPhase.CONNECTED).build();
        ReaderConfiguration configuration = new ReaderConfiguration(300, 1, 0, 0, 0, false, 4);
        ReaderTag tag = new ReaderTag("EPC", "DATA", -45, 0, 1);
        InventoryItem item = new InventoryItem("EPC", "DATA", -45, 2, "chip");
        InventoryMaskConfig mask = new InventoryMaskConfig(1, 32, 8, new byte[] {0x01});

        publisher.addObserver(observer);
        publisher.publishState(state);
        publisher.publishConfiguration(configuration);
        publisher.publishCurrentTag(tag);
        publisher.publishInventoryUpdate(Collections.singletonList(item), 2);
        publisher.publishMask(mask);
        publisher.publishSingleTagMask(mask);
        publisher.notifyUnexpectedDisconnect(DisconnectReason.WIFI_LOST);

        assertSame(state, observer.state);
        assertSame(configuration, observer.configuration);
        assertSame(tag, observer.tag);
        assertSame(item, observer.items.get(0));
        assertEquals(2, observer.totalReads);
        assertSame(mask, observer.inventoryMask);
        assertSame(mask, observer.singleTagMask);
        assertEquals(DisconnectReason.WIFI_LOST, observer.disconnectReason);
    }

    private static final class RecordingObserver implements ReaderObserver {
        private int initialDispatchCount;
        private ReaderState state;
        private ReaderConfiguration configuration;
        private ReaderTag tag;
        private List<InventoryItem> items;
        private long totalReads;
        private InventoryMaskConfig inventoryMask;
        private InventoryMaskConfig singleTagMask;
        private DisconnectReason disconnectReason;

        @Override
        public void onReaderStateChanged(ReaderState state) { this.state = state; }

        @Override
        public void onReaderConfigurationChanged(ReaderConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public void onCurrentTagChanged(ReaderTag tag) { this.tag = tag; }

        @Override
        public void onInventoryChanged(List<InventoryItem> items, long totalReads) {
            this.items = items;
            this.totalReads = totalReads;
        }

        @Override
        public void onInventoryMaskChanged(InventoryMaskConfig config) {
            inventoryMask = config;
        }

        @Override
        public void onSingleTagMaskChanged(InventoryMaskConfig config) {
            singleTagMask = config;
        }

        @Override
        public void onReaderUnexpectedDisconnect(DisconnectReason reason) {
            disconnectReason = reason;
        }
    }
}
