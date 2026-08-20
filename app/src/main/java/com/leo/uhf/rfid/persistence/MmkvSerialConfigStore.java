package com.leo.uhf.rfid.persistence;

import androidx.annotation.Nullable;

import com.leo.uhf.rfid.transport.serial.SerialConfig;
import com.leo.uhf.rfid.api.model.ModuleSubtype;
import com.tencent.mmkv.MMKV;

/** 使用 MMKV 保存最近一次串口连接参数。 */
public final class MmkvSerialConfigStore implements SerialConfigStore {
    private static final String MMKV_ID = "reader_serial_connection";
    private static final String KEY_PORT = "port";
    private static final String KEY_BAUD = "baud";
    private static final String KEY_MODULE = "module";
    private static final String KEY_DELAY = "delay";

    private final MMKV storage;

    public MmkvSerialConfigStore() {
        storage = MMKV.mmkvWithID(MMKV_ID);
    }

    @Nullable
    @Override
    public SerialConfig load() {
        if (!storage.contains(KEY_PORT)) {
            return null;
        }
        try {
            ModuleSubtype subtype = ModuleSubtype.valueOf(
                    storage.decodeString(KEY_MODULE, ModuleSubtype.R2000.name()));
            return new SerialConfig(
                    storage.decodeString(KEY_PORT, SerialConfig.DEFAULT_PORT_PATH),
                    storage.decodeInt(KEY_BAUD, SerialConfig.DEFAULT_BAUD_RATE),
                    subtype,
                    storage.decodeInt(KEY_DELAY, SerialConfig.DEFAULT_POWER_DELAY_MS));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    @Override
    public void save(SerialConfig config) {
        storage.encode(KEY_PORT, config.portPath);
        storage.encode(KEY_BAUD, config.baudRate);
        storage.encode(KEY_MODULE, config.moduleSubtype.name());
        storage.encode(KEY_DELAY, config.powerDelayMs);
    }
}
