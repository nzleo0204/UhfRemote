package com.leo.remote.rfid.sdk.model;

/** Snapshot of the SDK's fixed or dynamic Q configuration. */
public final class ReaderQParams {
    public final boolean dynamic;
    public final int qValue;
    public final int minQ;
    public final int maxQ;
    public final int retryCount;
    public final int thresholdMultiplier;
    public final int toggleTarget;
    public final int repeatUntilNoTags;

    private ReaderQParams(boolean dynamic, int qValue, int minQ, int maxQ, int retryCount,
            int thresholdMultiplier, int toggleTarget, int repeatUntilNoTags) {
        this.dynamic = dynamic;
        this.qValue = qValue;
        this.minQ = minQ;
        this.maxQ = maxQ;
        this.retryCount = retryCount;
        this.thresholdMultiplier = thresholdMultiplier;
        this.toggleTarget = toggleTarget;
        this.repeatUntilNoTags = repeatUntilNoTags;
    }

    public static ReaderQParams fixed(int qValue, int retryCount, int toggleTarget,
            int repeatUntilNoTags) {
        return new ReaderQParams(false, qValue, 0, 15, retryCount, 1,
                toggleTarget, repeatUntilNoTags);
    }

    public static ReaderQParams dynamic(int startQ, int minQ, int maxQ, int retryCount,
            int thresholdMultiplier, int toggleTarget) {
        return new ReaderQParams(true, startQ, minQ, maxQ, retryCount,
                thresholdMultiplier, toggleTarget, 0);
    }
}
