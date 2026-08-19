package com.leo.uhf.rfid.sdk.model;

import java.util.Locale;

/** RM8011 continuous power range helpers. */
public final class Rm8011PowerLevels {
    private Rm8011PowerLevels() {}

    /** RM8011 uses a continuous 0-20 dBm range in tenths of a dBm. */
    public static int maxTenthsDbm() { return 200; }

    /** Formats tenths of a dBm without truncating half-dBm values. */
    public static String format(int tenthsDbm) {
        return tenthsDbm % 10 == 0
                ? (tenthsDbm / 10) + " dBm"
                : String.format(Locale.US, "%.1f dBm", tenthsDbm / 10.0);
    }
}
