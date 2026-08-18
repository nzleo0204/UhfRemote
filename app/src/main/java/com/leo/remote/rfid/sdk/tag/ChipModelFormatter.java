package com.leo.remote.rfid.sdk.tag;

import com.leo.remote.rfid.sdk.model.*;


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

    /**
     * 从 TID 十六进制字符串中提取芯片型号。
     *
     * @param tidHex TID 的十六进制字符串
     * @return 芯片型号，如果无法识别则返回空字符串
     */
    public static String formatFromTid(String tidHex) {
        if (tidHex == null || tidHex.length() < 8) {
            return "";
        }

        try {
            // TID 前 4 字节（8 个十六进制字符）
            String prefix = tidHex.substring(0, 8).toUpperCase(Locale.US);
            int tidPrefix = (int) Long.parseLong(prefix, 16);

            // 创建临时 ReaderTag 用于格式化
            ReaderTag tempTag = new ReaderTag("", "", 0, 0, 0, "", tidPrefix);
            return format(tempTag);
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
