package com.leo.uhf.core.util;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 提供 RFID 显示值、长度和十六进制数据的格式化工具。
 */
public final class RfidFormat {
    private static final NumberFormat NUMBER = NumberFormat.getNumberInstance(Locale.US);

    private RfidFormat() {}

    public static String quantity(int value) {
        return NUMBER.format(value);
    }

    public static String time(long timestamp) {
        if (timestamp <= 0) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(timestamp));
    }
}
