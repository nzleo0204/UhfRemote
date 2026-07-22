package com.leo.remote.manager;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 全局屏幕方向管理：平板固定横屏，手机固定竖屏。
 */
public final class OrientationManager implements Application.ActivityLifecycleCallbacks {

    private static final int TABLET_SMALLEST_WIDTH_DP = 600;

    private final boolean mTabletDevice;
    private final int mRequestedOrientation;

    private OrientationManager(@NonNull Application application) {
        mTabletDevice = isTabletDevice(application.getResources().getConfiguration(),
                application.getResources().getDisplayMetrics());
        mRequestedOrientation = mTabletDevice ?
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    public static void register(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new OrientationManager(application));
    }

    public boolean isTabletDevice() {
        return mTabletDevice;
    }

    private static boolean isTabletDevice(@NonNull Configuration configuration,
                                          @NonNull DisplayMetrics displayMetrics) {
        if (configuration.smallestScreenWidthDp != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
            return configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP;
        }
        float smallestWidthDp = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels) /
                displayMetrics.density;
        return smallestWidthDp >= TABLET_SMALLEST_WIDTH_DP;
    }

    private void applyOrientation(@NonNull Activity activity) {
        if (activity.getRequestedOrientation() == mRequestedOrientation) {
            return;
        }
        activity.setRequestedOrientation(mRequestedOrientation);
    }

    @Override
    public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        applyOrientation(activity);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        applyOrientation(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        applyOrientation(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
