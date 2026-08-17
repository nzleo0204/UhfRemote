package com.leo.remote.reader.session;

import com.leo.remote.reader.model.*;
import com.leo.remote.reader.persistence.*;
import com.leo.remote.reader.sdk.*;
import com.leo.remote.reader.sdk.FakeUhfSdkGateway;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public final class ReaderConfigurationManagerTest {
    private FakeUhfSdkGateway gateway;
    private MemoryStore store;
    private RecordingObserver observer;
    private ReaderConfigurationManager manager;

    @Before
    public void setUp() {
        gateway = new FakeUhfSdkGateway();
        store = new MemoryStore();
        observer = new RecordingObserver();
        ReaderStatePublisher publisher = new ReaderStatePublisher(Runnable::run);
        publisher.addObserver(observer);
        manager = new ReaderConfigurationManager(gateway, store, publisher);
        manager.restore(new ReaderConfiguration(200, 1, 0, 0, 0, true, 7));
    }

    @Test
    public void setPower_updatesCacheAndPublishesSnapshot() {
        assertEquals(0, manager.setPower(ModuleSubtype.R2000, 275));

        assertEquals(275, gateway.lastPower);
        assertEquals(275, manager.getConfiguration().powerTenthsDbm);
        assertEquals(275, store.configuration.powerTenthsDbm);
        assertEquals(275, observer.configuration.powerTenthsDbm);
    }

    @Test
    public void failedBlfWrite_keepsPreviousConfiguration() {
        gateway.blfStatus = 9;

        assertEquals(9, manager.setBlf(ModuleSubtype.R2000, 2));

        assertEquals(0, manager.getConfiguration().blfProfile);
        assertEquals(0, observer.publishCount);
    }

    @Test
    public void unsupportedInventoryMode_fallsBackToHighPerformance() {
        manager.setInventoryMode(ModuleSubtype.RM8011, 2);

        assertEquals(1, manager.getInventoryMode());
        assertEquals(1, manager.getConfiguration().inventoryMode);
    }

    private static final class MemoryStore implements ReaderConfigurationStore {
        private ReaderConfiguration configuration;
        private int selected;

        @Override
        public void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public ReaderConfiguration loadConfiguration(ModuleSubtype subtype) {
            return configuration;
        }

        @Override public void saveSelected(ModuleSubtype subtype, int selected) {
            this.selected = selected;
        }

        @Override public int loadSelected(ModuleSubtype subtype) { return selected; }
    }

    private static final class RecordingObserver implements ReaderObserver {
        private ReaderConfiguration configuration;
        private int publishCount;

        @Override
        public void onReaderConfigurationChanged(ReaderConfiguration configuration) {
            this.configuration = configuration;
            publishCount++;
        }
    }
}
