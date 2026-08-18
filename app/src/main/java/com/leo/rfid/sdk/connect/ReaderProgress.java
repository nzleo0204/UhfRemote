package com.leo.rfid.sdk.connect;

/** Stable connection progress events exposed to a host application's presentation layer. */
public enum ReaderProgress {
    VERIFYING_MODULE("正在读取设备版本信息"),
    UPDATING_PARAMETERS("正在更新设备参数"),
    READING_POWER("正在读取功率"),
    READING_PROTOCOL("正在读取射频协议"),
    READING_SESSION("正在读取 Session"),
    READING_BLF("正在读取 BLF");

    private final String defaultMessage;

    ReaderProgress(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
