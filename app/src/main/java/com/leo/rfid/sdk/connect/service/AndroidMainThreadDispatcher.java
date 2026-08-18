package com.leo.rfid.sdk.connect.service;

import com.leo.rfid.sdk.connect.MainThreadDispatcher;

import android.os.Handler;
import android.os.Looper;

/**
 * 基于 Android 主线程 Handler 的任务调度器。
 */
public final class AndroidMainThreadDispatcher implements MainThreadDispatcher {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void post(Runnable action) { handler.post(action); }
    @Override public void postDelayed(Runnable action, long delayMillis) {
        handler.postDelayed(action, delayMillis);
    }
    @Override public void removeCallbacks(Runnable action) { handler.removeCallbacks(action); }
    @Override public boolean isMainThread() { return Looper.myLooper() == Looper.getMainLooper(); }
}
