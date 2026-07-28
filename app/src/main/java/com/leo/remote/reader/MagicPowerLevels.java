package com.leo.remote.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MagicPowerLevels {
    private static final Pattern VERSION_NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private MagicPowerLevels() {}

    public static int[] forModule(String serial, String version) {
        String identity = (serial == null ? "" : serial).toUpperCase(Locale.ROOT);
        if (identity.contains("20DBM")) {
            return new int[]{130, 145, 155, 170, 185, 200};
        }
        if (identity.contains("30DBM")) {
            return parseVersion(version) >= 3.8
                    ? new int[]{100, 140, 170, 190, 210, 230, 240, 250, 260, 270, 280, 290, 300}
                    : integerRange(19, 30);
        }
        if (identity.contains("26DBM") && identity.contains("V1.0")) {
            return integerRange(15, 26);
        }
        return integerRange(0, 26);
    }

    private static int[] integerRange(int startDbm, int endDbm) {
        List<Integer> values = new ArrayList<>();
        for (int value = startDbm; value <= endDbm; value++) { values.add(value * 10); }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static double parseVersion(String version) {
        Matcher matcher = VERSION_NUMBER.matcher(version == null ? "" : version);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : -1;
    }
}
