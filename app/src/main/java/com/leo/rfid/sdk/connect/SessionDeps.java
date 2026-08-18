package com.leo.rfid.sdk.connect;

import com.leo.rfid.sdk.connect.service.AndroidMainThreadDispatcher;
import com.leo.rfid.sdk.storage.MmkvReaderConnectionStore;
import com.leo.rfid.sdk.storage.ReaderConfigCache;
import com.leo.rfid.sdk.storage.ReaderConfigurationStore;
import com.leo.rfid.sdk.storage.ReaderConnectionStore;
import com.leo.rfid.sdk.connect.transport.BleTransport;
import com.leo.rfid.sdk.connect.transport.ReaderBleTransport;
import com.leo.rfid.sdk.connect.transport.ReaderWifiMonitor;
import com.leo.rfid.sdk.connect.transport.WifiNetworkMonitor;

import android.app.Application;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 集中保存 Reader 会话所需的可注入依赖，并提供生产环境装配入口。
 */
final class SessionDeps {
    /** 创建 SDK 串行执行器的工厂。 */
    final Supplier<ExecutorService> sdkExecutorFactory;
    /** 将状态通知切换到 Android 主线程的调度器。 */
    final MainThreadDispatcher mainThread;
    /** 创建 BLE 传输对象的工厂。 */
    final Function<ReaderBleTransport.Listener, ReaderBleTransport> bleTransportFactory;
    /** 创建 Wi-Fi 网络监视器的工厂。 */
    final Function<ReaderWifiMonitor.Listener, ReaderWifiMonitor> wifiMonitorFactory;
    final ReaderConnectionStore connectionStore;
    final ReaderConfigurationStore configurationStore;
    final Function<ReaderProgress, String> messageResolver;

    SessionDeps(Supplier<ExecutorService> sdkExecutorFactory,
            MainThreadDispatcher mainThread,
            Function<ReaderBleTransport.Listener, ReaderBleTransport> bleTransportFactory,
            Function<ReaderWifiMonitor.Listener, ReaderWifiMonitor> wifiMonitorFactory,
            ReaderConnectionStore connectionStore,
            ReaderConfigurationStore configurationStore,
            Function<ReaderProgress, String> messageResolver) {
        this.sdkExecutorFactory = sdkExecutorFactory;
        this.mainThread = mainThread;
        this.bleTransportFactory = bleTransportFactory;
        this.wifiMonitorFactory = wifiMonitorFactory;
        this.connectionStore = connectionStore;
        this.configurationStore = configurationStore;
        this.messageResolver = messageResolver;
    }

    static SessionDeps production(Application application,
            Function<ReaderProgress, String> messageResolver) {
        return new SessionDeps(SessionDeps::createSdkExecutor,
                new AndroidMainThreadDispatcher(), BleTransport::new,
                listener -> new WifiNetworkMonitor(application, listener),
                new MmkvReaderConnectionStore(), new ReaderConfigCache(), messageResolver);
    }

    private static ExecutorService createSdkExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "uhf-sdk");
            thread.setDaemon(true);
            return thread;
        });
    }
}
