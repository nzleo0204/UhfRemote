package com.leo.remote.reader;

public enum TagProtocol {
    ISO_18000_6C(0, "6C"),
    ISO_18000_6B(1, "6B"),
    GJB_7377_1(2, "GJB 7377.1"),
    GB_T_29768(3, "GB/T 29768");

    private final int sdkValue;
    private final String displayName;

    TagProtocol(int sdkValue, String displayName) {
        this.sdkValue = sdkValue;
        this.displayName = displayName;
    }

    public int getSdkValue() {
        return sdkValue;
    }

    public String getDisplayName() {
        return displayName;
    }
}
