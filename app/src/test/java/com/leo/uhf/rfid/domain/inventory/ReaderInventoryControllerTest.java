package com.leo.uhf.rfid.domain.inventory;

import com.leo.uhf.rfid.api.model.*;
import com.leo.uhf.rfid.api.ReaderObserver;
import com.leo.uhf.rfid.persistence.*;
import com.leo.uhf.rfid.sdk.capability.*;
import com.leo.uhf.rfid.session.*;
import com.leo.uhf.rfid.sdk.linkage.FakeUhfSdkGateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * 验证盘点控制器的启动、停止和结果分发行为。
 */
public final class ReaderInventoryControllerTest {
    private FakeUhfSdkGateway gateway;
    private MemoryStore store;
    private RecordingObserver observer;
    private List<Runnable> scheduled;
    private ReaderInventoryController controller;

    @Before
    public void setUp() {
        gateway = new FakeUhfSdkGateway();
        store = new MemoryStore();
        observer = new RecordingObserver();
        scheduled = new ArrayList<>();
        ReaderStatePublisher publisher = new ReaderStatePublisher(Runnable::run);
        publisher.addObserver(observer);
        controller = new ReaderInventoryController(gateway, gateway, store, publisher,
                (callback, delay) -> scheduled.add(callback));
    }

    @Test
    public void tagBurst_schedulesOneMergedInventoryUpdate() {
        controller.onTag(new ReaderTag("EPC", "TID", -50, 0, 1));
        controller.onTag(new ReaderTag("EPC", "TID", -45, 0, 2));

        assertEquals(1, scheduled.size());
        scheduled.get(0).run();

        assertEquals(1, observer.items.size());
        assertEquals(3, observer.totalReads);
        assertEquals(-45, observer.items.get(0).getRssi());
    }

    @Test
    public void start_reappliesActiveMaskAndPassesMaskFlag() {
        controller.activateMask(new InventoryMaskConfig(1, 32, 8, new byte[] {0x01}),
                TagProtocol.ISO_18000_6C);

        assertEquals(0, controller.start(TagProtocol.ISO_18000_6C, ModuleSubtype.R2000,
                new ReaderConfiguration(200, 1, 0, 0, 0, true, 7), 1));

        assertEquals(1, gateway.lastInventoryMode);
        assertEquals(1, gateway.lastMaskFlag);
        assertTrue(controller.isMaskApplied());
    }

    @Test
    public void start_discardsMaskFromPreviousProtocol() {
        controller.activateMask(new InventoryMaskConfig(1, 32, 8, new byte[] {0x01}),
                TagProtocol.ISO_18000_6C);

        assertEquals(0, controller.start(TagProtocol.GJB_7377_1, ModuleSubtype.RM610,
                null, 1));

        assertNull(controller.getMask());
        assertEquals(0, gateway.lastMaskFlag);
    }

    @Test
    public void maskSelection_restoresValueCapturedBeforeFirstApply() {
        gateway.queryValues = new int[] {1, 0, 3};
        assertTrue(controller.captureSelection(ModuleSubtype.R2000));
        gateway.queryValues = new int[] {1, 0, 1};
        assertTrue(controller.captureSelection(ModuleSubtype.R2000));

        assertEquals(3, controller.restoreValue(ModuleSubtype.R2000));
        assertEquals(3, store.selected);
    }

    private static final class MemoryStore implements ReaderConfigurationStore {
        private int selected;
        @Override public void saveConfiguration(ModuleSubtype subtype,
                ReaderConfiguration configuration) {}
        @Override public ReaderConfiguration loadConfiguration(ModuleSubtype subtype) {
            return null;
        }
        @Override public void saveSelected(ModuleSubtype subtype, int selected) {
            this.selected = selected;
        }
        @Override public int loadSelected(ModuleSubtype subtype) { return selected; }
    }

    private static final class RecordingObserver implements ReaderObserver {
        private List<InventoryItem> items;
        private long totalReads;
        @Override public void onInventoryChanged(List<InventoryItem> items, long totalReads) {
            this.items = items;
            this.totalReads = totalReads;
        }
    }
}
