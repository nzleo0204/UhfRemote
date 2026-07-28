package com.leo.remote.reader;

public final class ReaderConfiguration {
    public final int powerTenthsDbm;
    public final int inventoryMode;
    public final int blfProfile;
    public final int session;
    public final int target;
    public final boolean dynamicQ;
    public final int qValue;

    public ReaderConfiguration(int powerTenthsDbm, int inventoryMode, int blfProfile,
            int session, int target, boolean dynamicQ, int qValue) {
        this.powerTenthsDbm = powerTenthsDbm;
        this.inventoryMode = inventoryMode;
        this.blfProfile = blfProfile;
        this.session = session;
        this.target = target;
        this.dynamicQ = dynamicQ;
        this.qValue = qValue;
    }
}
