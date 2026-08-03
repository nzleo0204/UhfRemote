package com.leo.remote.reader;

import java.util.Locale;

/** Converts SDK chip metadata into the label shown in the inventory list. */
public final class ChipModelFormatter {
    private ChipModelFormatter() {}

    public static String format(ReaderTag tag) {
        if (tag == null) { return ""; }
        String model = tag.chipModel.trim();
        if (!model.isEmpty()) {
            String[] names = model.split("\\|", -1);
            String english = names[0].trim();
            if (names.length == 1) { return english; }
            String chinese = names[1].trim();
            return Locale.getDefault().getLanguage().startsWith("zh")
                    ? (chinese.isEmpty() ? english : chinese)
                    : (english.isEmpty() ? chinese : english);
        }
        return tag.tidPrefix == 0 ? ""
                : String.format(Locale.US, "未知(%08X)", tag.tidPrefix);
    }
}
