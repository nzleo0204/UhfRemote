package com.leo.uhf.rfid.sdk.connection;

/** 向宿主界面发布的稳定连接进度事件。 */
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

    /** 返回该进度事件的默认提示文案。 */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
