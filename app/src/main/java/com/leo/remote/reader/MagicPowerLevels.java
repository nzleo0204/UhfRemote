package com.leo.remote.reader;

/** MagicRF module power levels. */
public final class MagicPowerLevels {
    private MagicPowerLevels() {}

    /** Returns fixed integer levels from 0 to 20 dBm in tenths of a dBm. */
    public static int[] levels() {
        int[] result = new int[21];
        for (int i = 0; i <= 20; i++) {
            result[i] = i * 10;
        }
        return result;
    }
}
