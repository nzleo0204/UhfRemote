package com.leo.remote.reader;

interface ReaderMainThreadDispatcher {
    void post(Runnable action);
    void postDelayed(Runnable action, long delayMillis);
    void removeCallbacks(Runnable action);
    boolean isMainThread();
}
