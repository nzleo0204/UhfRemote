package com.leo.remote.reader.model;

public final class InventoryItem {
    private final String id;
    private final String data;
    private final int rssi;
    private final long count;
    private final String chipModel;

    public InventoryItem(String id, String data, int rssi, long count, String chipModel) {
        this.id = id;
        this.data = data;
        this.rssi = rssi;
        this.count = count;
        this.chipModel = chipModel;
    }

    public String getId() { return id; }
    public String getData() { return data; }
    public int getRssi() { return rssi; }
    public long getCount() { return count; }
    public String getChipModel() { return chipModel; }
}
