package com.leo.remote.reader.model;

import android.util.Log;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** RM8011 power levels selected from the module serial and firmware version. */
public final class Rm8011PowerLevels {
    private static final String TAG = "Rm8011PowerLevels";
    private static final Pattern VERSION_NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final int[] TIER_20 = {130, 145, 155, 170, 185, 200};
    private static final int[] TIER_26_V10 = range(150, 260);
    private static final int[] TIER_26 = range(0, 260);
    private static final int[] TIER_30_OLD = range(190, 300);
    private static final int[] TIER_30_NEW = {
            100, 140, 170, 190, 210, 230, 240, 250, 260, 270, 280, 290, 300};
    private static final int[] TIER_FALLBACK = range(0, 200);

    private Rm8011PowerLevels() {}

    /** @deprecated RM8011 products use a continuous 0-20 dBm power range. */
    @Deprecated
    public static int[] levels(String moduleSerial, String moduleVersion) {
        String serial = moduleSerial == null ? "" : moduleSerial;
        if (serial.contains("RM-20dBm")) {
            return TIER_20.clone();
        }
        if (serial.contains("RM-26dBm")) {
            return (serial.contains("V1.0") ? TIER_26_V10 : TIER_26).clone();
        }
        if (serial.contains("RM-30dBm")) {
            return parseVersion(moduleVersion) >= 3.80d
                    ? TIER_30_NEW.clone() : TIER_30_OLD.clone();
        }
        if (serial.contains("30dBm") && serial.contains("V1.3.1")) {
            return TIER_30_OLD.clone();
        }
        warn("未识别的功率档位，回退 0-20dBm，serial=" + serial);
        return TIER_FALLBACK.clone();
    }

    /** RM8011 uses a continuous 0-20 dBm range in tenths of a dBm. */
    public static int maxTenthsDbm() { return 200; }

    /** Formats tenths of a dBm without truncating half-dBm values. */
    public static String format(int tenthsDbm) {
        return tenthsDbm % 10 == 0
                ? (tenthsDbm / 10) + " dBm"
                : String.format(Locale.US, "%.1f dBm", tenthsDbm / 10.0);
    }

    private static double parseVersion(String version) {
        if (version == null) { return 0d; }
        Matcher matcher = VERSION_NUMBER.matcher(version);
        if (!matcher.find()) {
            warn("固件版本解析失败: " + version);
            return 0d;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException error) {
            warn("固件版本解析失败: " + version + ": " + error.getMessage());
            return 0d;
        }
    }

    private static int[] range(int fromTenths, int toTenths) {
        int count = (toTenths - fromTenths) / 10 + 1;
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = fromTenths + i * 10;
        }
        return result;
    }

    private static void warn(String message) {
        try {
            Log.w(TAG, message);
        } catch (RuntimeException ignored) {
            // Android's unit-test Log stub may reject calls; selection remains deterministic.
        }
    }
}
