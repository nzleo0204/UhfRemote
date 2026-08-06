package com.leo.remote.reader;

import java.util.EnumSet;
import java.util.Set;

public enum ModuleSubtype {
    R2000(0),
    RM8011(1),
    R2000_PLUS(3),
    RM610(6),
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
        if (this == RM8011) {
            // RM8011 仅支持 ISO 18000-6C
            return EnumSet.of(TagProtocol.ISO_18000_6C);
        }
        if (this == RM610) {
            // RM610 支持 6C + GJB 7377.1
            return EnumSet.of(TagProtocol.ISO_18000_6C, TagProtocol.GJB_7377_1);
        }
        return EnumSet.noneOf(TagProtocol.class);
    }

    public boolean isR2000Style() {
        return this == R2000 || this == R2000_PLUS || this == RM610;
    }

    /** Single and low-power inventory are available only on R2000 modules. */
    public boolean supportsInventoryModeSwitch() {
        return this == R2000 || this == R2000_PLUS;
    }

    public String getDisplayName() {
        return switch (this) {
            case R2000 -> "R2000";
            case RM8011 -> "RM8011";
            case R2000_PLUS -> "R2000Plus";
            case RM610 -> "RM610";
            case UNKNOWN -> "未知";
        };
    }
}
