package com.leo.uhf.rfid.api.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 定义读写器射频模块型号及其协议能力。
 */
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

    /**
     * 判断当前模块是否为旗连（老 Demo 中称为 MagicRF）模块。
     *
     * <p>RM8011 是当前项目对 Linkage 类型值 1 的兼容命名，不能改成另一个
     * 数值，否则会破坏既有配置和 SDK 解析模式。</p>
     */
    public boolean isQilian() {
        return this == RM8011;
    }

    /** 返回客户和诊断界面使用的模块名称。 */
    public String getDisplayName() {
        return switch (this) {
            case R2000 -> "R2000";
            case RM8011 -> "旗连（RM8011 / MagicRF）";
            case R2000_PLUS -> "R2000Plus";
            case RM610 -> "RM610";
            case UNKNOWN -> "未知";
        };
    }

    /** 返回模块就绪所需的默认等待时间，用户仍可在串口配置中覆盖。 */
    public int getDefaultSerialReadyDelayMs() {
        return isQilian() ? 3000 : 500;
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
            // RM610 支持 ISO 18000-6C + GJB_7377
            return EnumSet.of(TagProtocol.ISO_18000_6C, TagProtocol.GJB_7377_1);
        }
        return EnumSet.noneOf(TagProtocol.class);
    }

    public boolean isR2000Style() {
        return this == R2000 || this == R2000_PLUS || this == RM610;
    }

    /** 判断当前模块是否支持单次盘点和低功耗盘点模式。 */
    public boolean supportsInventoryModeSwitch() {
        return this == R2000 || this == R2000_PLUS;
    }

}
