package com.leo.uhf.rfid.domain.inventory;

import com.leo.uhf.rfid.api.model.*;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按标签标识聚合盘点结果，并累计次数与信号强度信息。
 */
public final class InventoryAccumulator {
    private final Map<String, InventoryItem> items = new LinkedHashMap<>();
    private long totalReads;

    public synchronized void add(String id, String data, int rssi, int reportedCount, String chipModel) {
        String safeId = id == null ? "" : id;
        String safeData = data == null ? "" : data;
        String key = safeId + '|' + safeData;
        InventoryItem previous = items.get(key);
        long increment = Math.max(1, reportedCount);
        long count = previous == null ? increment : previous.getCount() + increment;
        String safeChip = chipModel == null ? "" : chipModel;
        if (safeChip.isEmpty() && previous != null) { safeChip = previous.getChipModel(); }
        items.put(key, new InventoryItem(safeId, safeData, rssi, count, safeChip));
        totalReads += increment;
    }

    public synchronized List<InventoryItem> snapshot() {
        return new ArrayList<>(items.values());
    }

    public synchronized long getTotalReads() { return totalReads; }

    public synchronized void clear() {
        items.clear();
        totalReads = 0;
    }
}
