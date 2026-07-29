package com.leo.remote.manager;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.hjq.bar.TitleBar;
import com.hjq.core.manager.ActivityManager;
import com.hjq.http.EasyConfig;
import com.hjq.toast.Toaster;
import com.leo.remote.R;
import com.leo.remote.http.model.RequestHandler;
import com.leo.remote.http.model.RequestServer;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.util.AppConfig;
import com.leo.remote.util.CrashHandler;
import com.leo.remote.util.DebugLoggerTree;
import com.leo.remote.util.MaterialHeader;
import com.leo.remote.util.SmartBallPulseFooter;
import com.leo.remote.util.ThemeModeManager;
import com.leo.remote.util.TitleBarStyle;
import com.leo.remote.util.ToastInterceptor;
import com.leo.remote.util.ToastStyle;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import com.tencent.bugly.library.Bugly;
import com.tencent.bugly.library.BuglyBuilder;
import com.tencent.mmkv.MMKV;
import okhttp3.OkHttpClient;
import timber.log.Timber;

public final class InitManager {
    private static final String AGREE_PRIVACY_NAME = "agree_privacy_config";
    private static final String KEY_AGREE_PRIVACY_RESULT = "key_agree_privacy_result";

    private InitManager() {}

    public static boolean isAgreePrivacy(@NonNull Context context) {
        SharedPreferences preferences = context.getSharedPreferences(AGREE_PRIVACY_NAME, Context.MODE_PRIVATE);
        return preferences.getBoolean(KEY_AGREE_PRIVACY_RESULT, false);
    }

    public static void setAgreePrivacy(@NonNull Context context, boolean result) {
        SharedPreferences preferences = context.getSharedPreferences(AGREE_PRIVACY_NAME, Context.MODE_PRIVATE);
        preferences.edit().putBoolean(KEY_AGREE_PRIVACY_RESULT, result).apply();
    }

    public static void preInitSdk(@NonNull Application application) {
        MMKV.initialize(application);
        ThemeModeManager.applyStoredMode();

        if (AppConfig.isLogEnable()) {
            Timber.plant(new DebugLoggerTree());
        }
        TitleBar.setGlobalStyle(new TitleBarStyle());
        SmartRefreshLayout.setDefaultRefreshHeaderCreator((context, layout) ->
                new MaterialHeader(context).setColorSchemeColors(
                        ContextCompat.getColor(context, R.color.common_accent_color)));
        SmartRefreshLayout.setDefaultRefreshFooterCreator((context, layout) -> new SmartBallPulseFooter(context));
        SmartRefreshLayout.setDefaultRefreshInitializer((context, layout) -> layout
                .setEnableHeaderTranslationContent(true)
                .setEnableFooterTranslationContent(true)
                .setEnableFooterFollowWhenNoMoreData(true)
                .setEnableLoadMoreWhenContentNotFull(false)
                .setEnableOverScrollDrag(false));

        Toaster.init(application, new ToastStyle());
        Toaster.setDebugMode(AppConfig.isDebug());
        Toaster.setInterceptor(new ToastInterceptor());
        CrashHandler.register(application);

        BuglyBuilder builder = new BuglyBuilder(AppConfig.getBuglyId(), AppConfig.getBuglyKey());
        builder.debugMode = AppConfig.isDebug();
        Bugly.init(application, builder);
        ActivityManager.getInstance().init(application);

        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        EasyConfig.with(okHttpClient)
                .setLogEnabled(AppConfig.isLogEnable())
                .setServer(new RequestServer())
                .setHandler(new RequestHandler(application))
                .setRetryCount(1)
                .into();
    }

    public static void initSdk(@NonNull Application application) {
        ReaderSessionManager.initialize(application);
    }
}
