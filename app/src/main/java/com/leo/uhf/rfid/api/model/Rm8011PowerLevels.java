package com.leo.uhf.rfid.api.model;

import java.util.Locale;

/** 提供 RM8011 旗连模块的连续功率范围和显示格式。 */
public final class Rm8011PowerLevels {
    private Rm8011PowerLevels() {}

    /** RM8011 使用 0 到 20 dBm 的连续功率范围，单位为 0.1 dBm。 */
    public static int maxTenthsDbm() { return 200; }

    /** 格式化功率值，保留半 dBm 等小数功率，不做截断。 */
    public static String format(int tenthsDbm) {
        return tenthsDbm % 10 == 0
                ? (tenthsDbm / 10) + " dBm"
                : String.format(Locale.US, "%.1f dBm", tenthsDbm / 10.0);
    }
}
