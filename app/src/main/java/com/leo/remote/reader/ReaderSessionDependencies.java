package com.leo.remote.reader;

import android.app.Application;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

final class ReaderSessionDependencies {
    final Supplier<ExecutorService> sdkExecutorFactory;
    final ReaderMainThreadDispatcher mainThread;
    final Function<ReaderBleTransport.Listener, ReaderBleTransport> bleTransportFactory;
    final Function<ReaderWifiMonitor.Listener, ReaderWifiMonitor> wifiMonitorFactory;
    final ReaderConnectionStore connectionStore;
    final ReaderConfigurationStore configurationStore;

    ReaderSessionDependencies(Supplier<ExecutorService> sdkExecutorFactory,
            ReaderMainThreadDispatcher mainThread,
            Function<ReaderBleTransport.Listener, ReaderBleTransport> bleTransportFactory,
            Function<ReaderWifiMonitor.Listener, ReaderWifiMonitor> wifiMonitorFactory,
            ReaderConnectionStore connectionStore,
            ReaderConfigurationStore configurationStore) {
        this.sdkExecutorFactory = sdkExecutorFactory;
        this.mainThread = mainThread;
        this.bleTransportFactory = bleTransportFactory;
        this.wifiMonitorFactory = wifiMonitorFactory;
        this.connectionStore = connectionStore;
        this.configurationStore = configurationStore;
    }

    static ReaderSessionDependencies production(Application application) {
        return new ReaderSessionDependencies(ReaderSessionDependencies::createSdkExecutor,
                new AndroidMainThreadDispatcher(), BleTransport::new,
                listener -> new WifiNetworkMonitor(application, listener),
                new MmkvReaderConnectionStore(), new ReaderConfigCache());
    }

    private static ExecutorService createSdkExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "uhf-sdk");
            thread.setDaemon(true);
            return thread;
        });
    }
}
