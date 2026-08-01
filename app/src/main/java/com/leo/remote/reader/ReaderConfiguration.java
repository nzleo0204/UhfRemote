package com.leo.remote.reader;

public final class ReaderConfiguration {
    public final int powerTenthsDbm;
    public final int inventoryMode;
    public final int blfProfile;
    public final int session;
    public final int target;
    public final boolean dynamicQ;
    public final int qValue;
    public final int qMinValue;
    public final int qMaxValue;
    public final int qRetryCount;
    public final int qThresholdMultiplier;
    public final int qToggleTarget;
    public final int qRepeatUntilNoTags;

    public ReaderConfiguration(int powerTenthsDbm, int inventoryMode, int blfProfile,
            int session, int target, boolean dynamicQ, int qValue) {
        this(powerTenthsDbm, inventoryMode, blfProfile, session, target, dynamicQ, qValue,
                0, 15, 0, 1, 1, 0);
    }

    public ReaderConfiguration(int powerTenthsDbm, int inventoryMode, int blfProfile,
            int session, int target, boolean dynamicQ, int qValue, int qMinValue,
            int qMaxValue, int qRetryCount, int qThresholdMultiplier, int qToggleTarget,
            int qRepeatUntilNoTags) {
        this.powerTenthsDbm = powerTenthsDbm;
        this.inventoryMode = inventoryMode;
        this.blfProfile = blfProfile;
        this.session = session;
        this.target = target;
        this.dynamicQ = dynamicQ;
        this.qValue = qValue;
        this.qMinValue = qMinValue;
        this.qMaxValue = qMaxValue;
        this.qRetryCount = qRetryCount;
        this.qThresholdMultiplier = qThresholdMultiplier;
        this.qToggleTarget = qToggleTarget;
        this.qRepeatUntilNoTags = qRepeatUntilNoTags;
    }
}
