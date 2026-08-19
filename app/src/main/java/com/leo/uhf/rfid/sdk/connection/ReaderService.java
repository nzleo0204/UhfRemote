package com.leo.uhf.rfid.sdk.connection;

import com.leo.uhf.rfid.sdk.model.ReaderState;

/** 为与 Android 服务交互定义的读写器会话边界。 */
public interface ReaderService {
    void start();
    void stop();
    void update(ReaderState state);
}
