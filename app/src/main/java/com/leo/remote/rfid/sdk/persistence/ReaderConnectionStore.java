package com.leo.remote.rfid.sdk.persistence;

import com.leo.remote.rfid.sdk.model.*;


public interface ReaderConnectionStore {
    String getWifiAddress();
    void saveWifiAddress(String address);
}
