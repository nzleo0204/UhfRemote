package com.leo.uhf.rfid.sdk.model;

import java.util.Locale;

/** RM610 power representation differs between CMT and non-CMT modules. */
public final class Rm610PowerLevels {
    private static final String[] NON_CMT_LABELS = {
            "-1 dBm", "02 dBm", "05 dBm", "08 dBm",
            "11 dBm", "14 dBm", "17 dBm", "20 dBm"};

    private Rm610PowerLevels() {}

    public static boolean isCmtVersion(String moduleSerial) {
        return moduleSerial != null
                && moduleSerial.toLowerCase(Locale.ROOT).contains("cmt");
    }

    /** CMT SDK values are tenths of a dBm. */
    public static int[] cmtValues() {
        int[] values = new int[21];
        for (int i = 0; i <= 20; i++) {
            values[i] = i * 10;
        }
        return values;
    }

    public static String formatCmt(int tenthsDbm) {
        return Rm8011PowerLevels.format(tenthsDbm);
    }

    public static String[] nonCmtLabels() {
        return NON_CMT_LABELS.clone();
    }

    public static String formatNonCmt(int index) {
        return index >= 0 && index < NON_CMT_LABELS.length
                ? NON_CMT_LABELS[index] : "未知(" + index + ")";
    }

    public static int nonCmtIndex(String label) {
        for (int i = 0; i < NON_CMT_LABELS.length; i++) {
            if (NON_CMT_LABELS[i].equals(label)) { return i; }
        }
        return -1;
    }
}
