package com.leo.uhf.rfid.sdk.connection;

/**
 * 抽象主线程任务调度，便于生产环境和 JVM 测试替换实现。
 */
public interface MainThreadDispatcher {
    void post(Runnable action);
    void postDelayed(Runnable action, long delayMillis);
    void removeCallbacks(Runnable action);
    boolean isMainThread();
}
