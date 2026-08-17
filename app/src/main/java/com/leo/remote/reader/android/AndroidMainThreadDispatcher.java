package com.leo.remote.reader.android;

import com.leo.remote.reader.session.ReaderMainThreadDispatcher;

import android.os.Handler;
import android.os.Looper;

public final class AndroidMainThreadDispatcher implements ReaderMainThreadDispatcher {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void post(Runnable action) { handler.post(action); }
    @Override public void postDelayed(Runnable action, long delayMillis) {
        handler.postDelayed(action, delayMillis);
    }
    @Override public void removeCallbacks(Runnable action) { handler.removeCallbacks(action); }
    @Override public boolean isMainThread() { return Looper.myLooper() == Looper.getMainLooper(); }
}
