package com.leo.remote.rfid.sdk.connection;

import com.leo.remote.rfid.sdk.connection.service.AndroidMainThreadDispatcher;
import com.leo.remote.rfid.sdk.persistence.MmkvReaderConnectionStore;
import com.leo.remote.rfid.sdk.persistence.ReaderConfigCache;
import com.leo.remote.rfid.sdk.persistence.ReaderConfigurationStore;
import com.leo.remote.rfid.sdk.persistence.ReaderConnectionStore;
import com.leo.remote.rfid.sdk.connection.transport.BleTransport;
import com.leo.remote.rfid.sdk.connection.transport.ReaderBleTransport;
import com.leo.remote.rfid.sdk.connection.transport.ReaderWifiMonitor;
import com.leo.remote.rfid.sdk.connection.transport.WifiNetworkMonitor;

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
    final Function<ReaderProgress, String> messageResolver;

    ReaderSessionDependencies(Supplier<ExecutorService> sdkExecutorFactory,
            ReaderMainThreadDispatcher mainThread,
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

    static ReaderSessionDependencies production(Application application,
            Function<ReaderProgress, String> messageResolver) {
        return new ReaderSessionDependencies(ReaderSessionDependencies::createSdkExecutor,
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
