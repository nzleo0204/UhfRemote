package com.leo.remote.reader.persistence;

import com.leo.remote.reader.model.*;


public interface ReaderConnectionStore {
    String getWifiAddress();
    void saveWifiAddress(String address);
}
