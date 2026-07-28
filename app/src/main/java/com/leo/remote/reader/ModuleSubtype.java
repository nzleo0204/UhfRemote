package com.leo.remote.reader;

import java.util.EnumSet;
import java.util.Set;

public enum ModuleSubtype {
    R2000(0),
    MAGIC_RF(1),
    R2000_PLUS(3),
    RM100X(6),
    UNKNOWN(Integer.MIN_VALUE);

    private final int rawValue;

    ModuleSubtype(int rawValue) {
        this.rawValue = rawValue;
    }

    public int getRawValue() {
        return rawValue;
    }

    public static ModuleSubtype fromRawValue(int rawValue) {
        for (ModuleSubtype subtype : values()) {
            if (subtype != UNKNOWN && subtype.rawValue == rawValue) {
                return subtype;
            }
        }
        return UNKNOWN;
    }

    public Set<TagProtocol> supportedProtocols() {
        if (this == R2000 || this == R2000_PLUS) {
            return EnumSet.allOf(TagProtocol.class);
        }
        if (this == MAGIC_RF || this == RM100X) {
            return EnumSet.of(TagProtocol.ISO_18000_6C);
        }
        return EnumSet.noneOf(TagProtocol.class);
    }

    public boolean isR2000Style() {
        return this == R2000 || this == R2000_PLUS || this == RM100X;
    }

    public String getDisplayName() {
        return switch (this) {
            case R2000 -> "R2000";
            case MAGIC_RF -> "MagicRF";
            case R2000_PLUS -> "R2000Plus";
            case RM100X -> "RM100X";
            case UNKNOWN -> "未知";
        };
    }
}
