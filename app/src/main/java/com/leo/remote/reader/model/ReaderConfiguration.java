package com.leo.remote.reader.model;

public final class ReaderConfiguration {
    public static final int DEFAULT_INVENTORY_WORD_LEN = 6;

    public static ReaderConfiguration defaultsFor(ModuleSubtype subtype) {
        if (subtype == ModuleSubtype.RM8011) {
            return new ReaderConfiguration(100, 1, 0, 0, 0, false, 7);
        }
        return new ReaderConfiguration(200, 1, 0, 0, 0, true, 7);
    }
    public static final int MAX_INVENTORY_WORD_LEN = 32;
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
    public final int inventoryArea;
    public final int inventoryAddress;
    public final int inventoryWordLen;

    public ReaderConfiguration(int powerTenthsDbm, int inventoryMode, int blfProfile,
            int session, int target, boolean dynamicQ, int qValue) {
        this(powerTenthsDbm, inventoryMode, blfProfile, session, target, dynamicQ, qValue,
                0, 15, 0, 1, 1, 0, 0, 0, DEFAULT_INVENTORY_WORD_LEN);
    }

    public ReaderConfiguration(int powerTenthsDbm, int inventoryMode, int blfProfile,
            int session, int target, boolean dynamicQ, int qValue, int qMinValue,
            int qMaxValue, int qRetryCount, int qThresholdMultiplier, int qToggleTarget,
            int qRepeatUntilNoTags) {
        this(powerTenthsDbm, inventoryMode, blfProfile, session, target, dynamicQ, qValue,
                qMinValue, qMaxValue, qRetryCount, qThresholdMultiplier, qToggleTarget,
                qRepeatUntilNoTags, 0, 0, DEFAULT_INVENTORY_WORD_LEN);
    }

    public ReaderConfiguration(int powerTenthsDbm, int inventoryMode, int blfProfile,
            int session, int target, boolean dynamicQ, int qValue, int qMinValue,
            int qMaxValue, int qRetryCount, int qThresholdMultiplier, int qToggleTarget,
            int qRepeatUntilNoTags, int inventoryArea, int inventoryAddress,
            int inventoryWordLen) {
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
        this.inventoryArea = inventoryArea;
        this.inventoryAddress = inventoryAddress;
        this.inventoryWordLen = inventoryWordLen;
    }
}
