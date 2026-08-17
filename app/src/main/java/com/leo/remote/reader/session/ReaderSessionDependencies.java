package com.leo.remote.reader.session;

import com.leo.remote.reader.android.AndroidMainThreadDispatcher;
import com.leo.remote.reader.persistence.MmkvReaderConnectionStore;
import com.leo.remote.reader.persistence.ReaderConfigCache;
import com.leo.remote.reader.persistence.ReaderConfigurationStore;
import com.leo.remote.reader.persistence.ReaderConnectionStore;
import com.leo.remote.reader.transport.BleTransport;
import com.leo.remote.reader.transport.ReaderBleTransport;
import com.leo.remote.reader.transport.ReaderWifiMonitor;
import com.leo.remote.reader.transport.WifiNetworkMonitor;

import android.app.Application;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

final class ReaderSessionDependencies {
    final Supplier<ExecutorService> sdkExecutorFactory;
    final ReaderMainThreadDispatcher mainThread;
    final Function<ReaderBleTransport.Listener, ReaderBleTransport> bleTransportFactory;
    final Function<ReaderWifiMonitor.Listener, ReaderWifiMonitor> wifiMonitorFactory;
    final ReaderConnectionStore connectionStore;
    final ReaderConfigurationStore configurationStore;
    final IntFunction<String> stringResolver;

    ReaderSessionDependencies(Supplier<ExecutorService> sdkExecutorFactory,
            ReaderMainThreadDispatcher mainThread,
            Function<ReaderBleTransport.Listener, ReaderBleTransport> bleTransportFactory,
            Function<ReaderWifiMonitor.Listener, ReaderWifiMonitor> wifiMonitorFactory,
            ReaderConnectionStore connectionStore,
            ReaderConfigurationStore configurationStore, IntFunction<String> stringResolver) {
        this.sdkExecutorFactory = sdkExecutorFactory;
        this.mainThread = mainThread;
        this.bleTransportFactory = bleTransportFactory;
        this.wifiMonitorFactory = wifiMonitorFactory;
        this.connectionStore = connectionStore;
        this.configurationStore = configurationStore;
        this.stringResolver = stringResolver;
    }

    static ReaderSessionDependencies production(Application application) {
        return new ReaderSessionDependencies(ReaderSessionDependencies::createSdkExecutor,
                new AndroidMainThreadDispatcher(), BleTransport::new,
                listener -> new WifiNetworkMonitor(application, listener),
                new MmkvReaderConnectionStore(), new ReaderConfigCache(), application::getString);
    }

    private static ExecutorService createSdkExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "uhf-sdk");
            thread.setDaemon(true);
            return thread;
        });
    }
}
