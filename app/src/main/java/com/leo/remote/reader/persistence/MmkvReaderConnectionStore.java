package com.leo.remote.reader.persistence;

import com.leo.remote.reader.model.*;


import com.tencent.mmkv.MMKV;

public final class MmkvReaderConnectionStore implements ReaderConnectionStore {
    private static final String MMKV_ID = "reader_connection";
    private static final String KEY_WIFI_ADDRESS = "wifi_address";
    private final MMKV storage = MMKV.mmkvWithID(MMKV_ID);

    @Override
    public String getWifiAddress() {
        String value = storage == null ? null : storage.decodeString(KEY_WIFI_ADDRESS, "");
        return value == null ? "" : value;
    }

    @Override
    public void saveWifiAddress(String address) {
        if (storage != null) { storage.encode(KEY_WIFI_ADDRESS, address); }
    }
}
