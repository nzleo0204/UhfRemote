package com.leo.remote.reader.session;

import com.leo.remote.reader.model.ReaderState;

/** Android service boundary used by the platform-neutral reader session. */
public interface ReaderConnectionServiceHost {
    void start();
    void stop();
    void update(ReaderState state);
}
