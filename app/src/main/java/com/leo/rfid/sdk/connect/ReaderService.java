package com.leo.rfid.sdk.connect;

import com.leo.rfid.sdk.model.ReaderState;

/** Android service boundary used by the platform-neutral reader session. */
public interface ReaderService {
    void start();
    void stop();
    void update(ReaderState state);
}
