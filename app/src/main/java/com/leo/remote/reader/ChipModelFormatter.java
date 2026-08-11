package com.leo.remote.reader;

import java.util.Locale;

/**
 * Formats chip metadata recognized by the UHF SDK.
 *
 * <p>Chip recognition belongs to the SDK. This class only selects a localized name and falls
 * back to the SDK-provided TID prefix when the model is unknown.</p>
 */
public final class ChipModelFormatter {
    private ChipModelFormatter() {}

    public static String format(ReaderTag tag) {
        if (tag == null) { return ""; }
        String model = tag.chipModel.trim();
        if (!model.isEmpty()) {
            return formatBilingualModel(model);
        }
        return tag.tidPrefix == 0 ? ""
                : String.format(Locale.US, "未知(%08X)", tag.tidPrefix);
    }

    private static String formatBilingualModel(String model) {
        String[] names = model.split("\\|", -1);
        String english = names[0].trim();
        if (names.length == 1) { return english; }
        String chinese = names[1].trim();
        return Locale.getDefault().getLanguage().startsWith("zh")
                ? (chinese.isEmpty() ? english : chinese)
                : (english.isEmpty() ? chinese : english);
    }
}
