package com.leo.uhf.business.common.navigation;

import androidx.annotation.NonNull;

/** Process-wide navigation entry configured by the application composition root. */
public final class BusinessNavigation {
    private static volatile BusinessNavigator navigator;

    private BusinessNavigation() {}

    public static void initialize(@NonNull BusinessNavigator appNavigator) {
        navigator = appNavigator;
    }

    @NonNull
    public static BusinessNavigator get() {
        BusinessNavigator current = navigator;
        if (current == null) {
            throw new IllegalStateException("Business navigation is not initialized");
        }
        return current;
    }
}
