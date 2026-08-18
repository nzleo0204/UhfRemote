package com.leo.rfid.sdk.model;

/**
 * 表示读写器 SDK 操作失败，并保留底层返回码。
 */
public final class ReaderException extends Exception {
    private final int errorCode;

    public ReaderException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
