package com.leo.remote.reader;

public final class ReaderTag {
    public final String id;
    public final String data;
    public final int rssi;
    public final int tagType;
    public final int count;

    public ReaderTag(String id, String data, int rssi, int tagType, int count) {
        this.id = id;
        this.data = data;
        this.rssi = rssi;
        this.tagType = tagType;
        this.count = count;
    }
}
