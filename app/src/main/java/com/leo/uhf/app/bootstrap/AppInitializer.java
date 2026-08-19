package com.leo.uhf.app.bootstrap;

import android.app.Application;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import cn.wandersnail.ble.EasyBLE;
import cn.wandersnail.ble.ScanConfiguration;
import cn.wandersnail.ble.ScannerType;
import cn.wandersnail.commons.poster.ThreadMode;
import com.hjq.bar.TitleBar;
import com.hjq.core.manager.ActivityManager;
import com.hjq.http.EasyConfig;
import com.hjq.toast.Toaster;
import com.leo.uhf.R;
import com.leo.uhf.app.composition.RepositoryProvider;
import com.leo.uhf.app.crash.CrashHandler;
import com.leo.uhf.app.navigation.AppNavigator;
import com.leo.uhf.business.common.data.BusinessRepositories;
import com.leo.uhf.business.common.navigation.BusinessNavigation;
import com.leo.uhf.core.network.http.model.RequestHandler;
import com.leo.uhf.core.network.http.model.RequestServer;
import com.leo.uhf.core.util.AppConfig;
import com.leo.uhf.core.util.DebugLoggerTree;
import com.leo.uhf.core.util.MaterialHeader;
import com.leo.uhf.core.util.SmartBallPulseFooter;
import com.leo.uhf.core.util.ThemeModeManager;
import com.leo.uhf.core.util.TitleBarStyle;
import com.leo.uhf.core.util.ToastInterceptor;
import com.leo.uhf.core.util.ToastStyle;
import com.leo.uhf.rfid.sdk.connection.ReaderSessionManager;
import com.leo.uhf.rfid.sdk.connection.ReaderProgress;
import com.leo.uhf.rfid.sdk.connection.service.ReaderServiceNotificationConfig;
import com.leo.uhf.app.MainActivity;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import com.tencent.bugly.library.Bugly;
import com.tencent.bugly.library.BuglyBuilder;
import com.tencent.mmkv.MMKV;
import okhttp3.OkHttpClient;
import timber.log.Timber;

/** 负责应用依赖装配和第三方 SDK 初始化。 */
public final class AppInitializer {
    private AppInitializer() {}

    public static void initializeApplication(@NonNull Application application) {
        MMKV.initialize(application);
        ThemeModeManager.applyStoredMode();
        BusinessRepositories.initialize(new RepositoryProvider());
        BusinessNavigation.initialize(new AppNavigator());

        ScanConfiguration scanConfiguration = new ScanConfiguration()
                .setScannerType(ScannerType.LE)
                .setScanPeriodMillis(15_000)
                .setOnlyAcceptBleDevice(true)
                .setAcceptSysConnectedDevice(true);
        EasyBLE easyBle = EasyBLE.getBuilder()
                .setScanConfiguration(scanConfiguration)
                .setMethodDefaultThreadMode(ThreadMode.BACKGROUND)
                .build();
        easyBle.setLogEnabled(AppConfig.isDebug());
        easyBle.initialize(application);

        if (AppConfig.isLogEnable()) {
            Timber.plant(new DebugLoggerTree());
        }
        TitleBar.setGlobalStyle(new TitleBarStyle());
        SmartRefreshLayout.setDefaultRefreshHeaderCreator((context, layout) ->
                new MaterialHeader(context).setColorSchemeColors(
                        ContextCompat.getColor(context, R.color.common_accent_color)));
        SmartRefreshLayout.setDefaultRefreshFooterCreator(
                (context, layout) -> new SmartBallPulseFooter(context));
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

    public static void initializeReader(@NonNull Application application) {
        ReaderSessionManager.initialize(application, progress -> application.getString(
                readerProgressText(progress)), new ReaderServiceNotificationConfig(
                R.mipmap.launcher_ic,
                application.getString(R.string.reader_service_channel_name),
                application.getString(R.string.reader_service_connected),
                application.getString(R.string.reader_service_connecting),
                application.getString(R.string.reader_service_disconnected),
                application.getString(R.string.reader_service_action_disconnect),
                new android.content.Intent(application, MainActivity.class)));
    }

    private static int readerProgressText(ReaderProgress progress) {
        return switch (progress) {
            case VERIFYING_MODULE -> R.string.reader_verifying_detail;
            case UPDATING_PARAMETERS -> R.string.handshake_updating_params;
            case READING_POWER -> R.string.handshake_reading_power;
            case READING_PROTOCOL -> R.string.handshake_reading_protocol;
            case READING_SESSION -> R.string.handshake_reading_session;
            case READING_BLF -> R.string.handshake_reading_blf;
        };
    }
}
