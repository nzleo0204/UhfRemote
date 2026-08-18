package com.leo.remote.rfid.sdk.persistence;

import com.leo.remote.rfid.sdk.model.*;

/**
 * 定义读写器连接信息的持久化接口。
 */
public interface ReaderConnectionStore {
    String getWifiAddress();
    void saveWifiAddress(String address);
}
