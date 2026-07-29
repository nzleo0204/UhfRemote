package com.leo.remote.util;

import android.content.Context;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import com.leo.remote.R;
import com.tencent.mmkv.MMKV;

public final class ThemeModeManager {
    public static final int MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    public static final int MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES;

    private static final String MMKV_ID = "theme_config";
    private static final String KEY_THEME_MODE = "theme_mode";

    private ThemeModeManager() {}

    public static void applyStoredMode() {
        AppCompatDelegate.setDefaultNightMode(getStoredMode());
    }

    public static void setMode(int mode) {
        MMKV.mmkvWithID(MMKV_ID).encode(KEY_THEME_MODE, mode);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static int getStoredMode() {
        return MMKV.mmkvWithID(MMKV_ID).decodeInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    public static boolean isLightTheme(@NonNull Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode != Configuration.UI_MODE_NIGHT_YES;
    }

    @StringRes
    public static int getModeNameRes(int mode) {
        if (mode == MODE_LIGHT) {
            return R.string.theme_mode_light;
        }
        if (mode == MODE_DARK) {
            return R.string.theme_mode_dark;
        }
        return R.string.theme_mode_system;
    }
}
