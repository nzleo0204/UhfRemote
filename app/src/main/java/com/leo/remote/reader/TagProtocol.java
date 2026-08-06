package com.leo.remote.reader;

public enum TagProtocol {
    ISO_18000_6C(0),
    ISO_18000_6B(1),
    GJB_7377_1(2),
    GB_T_29768(3);

    private final int sdkValue;

    TagProtocol(int sdkValue) {
        this.sdkValue = sdkValue;
    }

    public int getSdkValue() {
        return sdkValue;
    }

    public String getDisplayName() {
        return switch (this) {
            case ISO_18000_6C -> "ISO 18000-6C";
            case ISO_18000_6B -> "ISO 18000-6B";
            case GJB_7377_1 -> "GJB_7377";
            case GB_T_29768 -> "GB_29768";
        };
    }
}
