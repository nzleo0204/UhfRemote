package com.leo.uhf.rfid.persistence;

import androidx.annotation.Nullable;

import com.leo.uhf.rfid.transport.serial.SerialConfig;

/** 定义串口连接参数的持久化读写能力。 */
public interface SerialConfigStore {
    /** 返回最近一次保存的串口配置，没有配置时返回 null。 */
    @Nullable
    SerialConfig load();

    /** 保存下一次连接使用的串口配置。 */
    void save(SerialConfig config);
}
