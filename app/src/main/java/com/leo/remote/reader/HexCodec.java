package com.leo.remote.reader;

import java.util.Locale;

public final class HexCodec {

    private HexCodec() {}

    public static byte[] decode(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || (normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("HEX data must contain a non-empty even number of characters");
        }
        byte[] result = new byte[normalized.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(normalized.charAt(i * 2), 16);
            int low = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid HEX data");
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    public static String encode(byte[] value, int length) {
        if (value == null || length <= 0) {
            return "";
        }
        int actualLength = Math.min(length, value.length);
        StringBuilder builder = new StringBuilder(actualLength * 2);
        for (int i = 0; i < actualLength; i++) {
            builder.append(String.format(java.util.Locale.US, "%02X", value[i] & 0xFF));
        }
        return builder.toString();
    }
}
