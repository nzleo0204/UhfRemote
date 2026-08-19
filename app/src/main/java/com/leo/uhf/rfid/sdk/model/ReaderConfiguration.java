package com.leo.uhf.rfid.sdk.model;

/**
 * 保存读写器运行参数的不可变快照。
 */
public final class ReaderConfiguration {
    /** 默认盘点数据长度，单位为 Word。 */
    public static final int DEFAULT_INVENTORY_WORD_LEN = 6;

    public static ReaderConfiguration defaultsFor(ModuleSubtype subtype) {
        if (subtype == ModuleSubtype.RM8011) {
            return new ReaderConfiguration(100, 1, 0, 0, 0, false, 7);
        }
        return new ReaderConfiguration(200, 1, 0, 0, 0, true, 7);
    }
    /** 盘点数据允许读取的最大长度，单位为 Word。 */
    public static final int MAX_INVENTORY_WORD_LEN = 32;
    /** 发射功率，单位为 0.1 dBm。 */
    public final int powerTenthsDbm;
    /** 盘点模式对应的 SDK 参数值。 */
    public final int inventoryMode;
    /** BLF 配置档位。 */
    public final int blfProfile;
    /** Gen2 Session 参数。 */
    public final int session;
    /** Gen2 Target 参数。 */
    public final int target;
    /** 是否启用动态 Q。 */
    public final boolean dynamicQ;
    /** 固定 Q 值或动态 Q 初始值。 */
    public final int qValue;
    public final int qMinValue;
    public final int qMaxValue;
    public final int qRetryCount;
    public final int qThresholdMultiplier;
    public final int qToggleTarget;
    public final int qRepeatUntilNoTags;
    /** 盘点时读取的标签存储区。 */
    public final int inventoryArea;
    /** 盘点读取起始地址，单位由协议定义。 */
    public final int inventoryAddress;
    /** 盘点读取长度，单位为 Word。 */
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
