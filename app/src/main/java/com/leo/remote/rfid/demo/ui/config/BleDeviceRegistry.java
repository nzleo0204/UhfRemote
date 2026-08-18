package com.leo.remote.rfid.demo.ui.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BleDeviceRegistry<T> {
    static final class Entry<T> {
        final String name;
        final String address;
        final int rssi;
        final long lastSeen;
        final T value;

        Entry(String name, String address, int rssi, long lastSeen, T value) {
            this.name = name;
            this.address = address;
            this.rssi = rssi;
            this.lastSeen = lastSeen;
            this.value = value;
        }
    }

    private final Map<String, Entry<T>> entries = new LinkedHashMap<>();
    private long generation;

    long beginScan() {
        entries.clear();
        return ++generation;
    }

    void invalidate() {
        generation++;
    }

    boolean addOrUpdate(long callbackGeneration, String name, String address, int rssi,
            long lastSeen, T value) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedAddress = address == null ? "" : address.trim();
        if (callbackGeneration != generation || normalizedName.isEmpty()
                || normalizedAddress.isEmpty()) {
            return false;
        }
        entries.put(normalizedAddress.toUpperCase(Locale.US),
                new Entry<>(normalizedName, normalizedAddress, rssi, lastSeen, value));
        return true;
    }

    List<Entry<T>> snapshot(long callbackGeneration) {
        if (callbackGeneration != generation) {
            return List.of();
        }
        return new ArrayList<>(entries.values());
    }

    int size(long callbackGeneration) {
        return callbackGeneration == generation ? entries.size() : 0;
    }
}
