package com.leo.uhf.core.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 提取异常链中的根因和可展示错误信息。
 */
public final class ThrowableUtils {
    private ThrowableUtils() {}

    @NonNull
    public static Throwable rootCause(@NonNull Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) { cause = cause.getCause(); }
        return cause;
    }

    @Nullable
    public static String rootMessage(@NonNull Throwable error) {
        return rootCause(error).getMessage();
    }
}
