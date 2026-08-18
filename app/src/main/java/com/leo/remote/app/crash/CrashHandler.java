package com.leo.remote.app.crash;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.leo.remote.app.restart.RestartActivity;
import com.leo.remote.core.util.AppConfig;

/** Routes uncaught application errors to the crash or restart flow. */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String CRASH_FILE_NAME = "crash_config";
    private static final String KEY_CRASH_TIME = "key_crash_time";

    public static void register(Application application) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(application));
    }

    private final Application application;
    private final Thread.UncaughtExceptionHandler nextHandler;

    private CrashHandler(Application application) {
        this.application = application;
        nextHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (nextHandler != null && getClass().getName().equals(nextHandler.getClass().getName())) {
            throw new IllegalStateException("CrashHandler has already been registered");
        }
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        SharedPreferences preferences = application.getSharedPreferences(
                CRASH_FILE_NAME, Context.MODE_PRIVATE);
        long currentCrashTime = System.currentTimeMillis();
        long lastCrashTime = preferences.getLong(KEY_CRASH_TIME, 0);
        preferences.edit().putLong(KEY_CRASH_TIME, currentCrashTime).commit();

        if (AppConfig.isDebug()) {
            if (currentCrashTime - lastCrashTime > 5_000) {
                CrashActivity.start(application, throwable);
            }
        } else if (currentCrashTime - lastCrashTime > 300_000) {
            RestartActivity.start(application);
        }

        if (nextHandler != null
                && !nextHandler.getClass().getName().startsWith("com.android.internal.os")) {
            nextHandler.uncaughtException(thread, throwable);
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }
}
