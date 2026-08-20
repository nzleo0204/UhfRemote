package com.leo.uhf.rfid.persistence;

import com.leo.uhf.rfid.api.model.*;

/**
 * 定义读写器连接信息的持久化接口。
 */
public interface ReaderConnectionStore {
    String getWifiAddress();
    void saveWifiAddress(String address);
}
