package com.leo.remote.rfid.sdk.connection;

import com.leo.remote.rfid.sdk.model.ReaderState;

/** Android service boundary used by the platform-neutral reader session. */
public interface ReaderConnectionServiceHost {
    void start();
    void stop();
    void update(ReaderState state);
}
