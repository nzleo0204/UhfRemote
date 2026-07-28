package com.leo.remote.reader;

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
