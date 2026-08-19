package com.leo.uhf.rfid.ui.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * 验证 BLE 设备注册表的去重和排序行为。
 */
public class BleDeviceRegistryTest {
    @Test
    public void filtersUnnamedDevicesAndDeduplicatesByAddress() {
        BleDeviceRegistry<String> registry = new BleDeviceRegistry<>();
        long generation = registry.beginScan();
        assertFalse(registry.addOrUpdate(generation, "   ", "AA:BB", -70, 1, "unnamed"));
        assertTrue(registry.addOrUpdate(generation, "Reader", "aa:bb", -70, 2, "first"));
        assertTrue(registry.addOrUpdate(generation, "Reader", "AA:BB", -55, 3, "updated"));

        List<BleDeviceRegistry.Entry<String>> entries = registry.snapshot(generation);
        assertEquals(1, entries.size());
        assertEquals(-55, entries.get(0).rssi);
        assertEquals("updated", entries.get(0).value);
    }

    @Test
    public void preservesDiscoveryOrderAndRejectsStaleScanCallbacks() {
        BleDeviceRegistry<String> registry = new BleDeviceRegistry<>();
        long firstGeneration = registry.beginScan();
        registry.addOrUpdate(firstGeneration, "First", "00:01", -60, 1, "first");
        registry.addOrUpdate(firstGeneration, "Second", "00:02", -50, 2, "second");
        assertEquals("First", registry.snapshot(firstGeneration).get(0).name);

        long secondGeneration = registry.beginScan();
        assertFalse(registry.addOrUpdate(firstGeneration, "Stale", "00:03", -40, 3, "stale"));
        assertTrue(registry.addOrUpdate(secondGeneration, "Current", "00:04", -45, 4, "current"));
        assertEquals(1, registry.size(secondGeneration));
        assertEquals("Current", registry.snapshot(secondGeneration).get(0).name);
    }
}
