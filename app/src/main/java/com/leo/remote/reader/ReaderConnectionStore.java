package com.leo.remote.reader;

interface ReaderConnectionStore {
    String getWifiAddress();
    void saveWifiAddress(String address);
}
