package com.leo.remote.rfid.sdk.connection;

import com.leo.remote.rfid.sdk.inventory.ReaderInventoryController;
import com.leo.remote.rfid.sdk.model.*;
import com.leo.remote.rfid.sdk.persistence.ReaderConfigurationStore;
import com.leo.remote.rfid.sdk.persistence.ReaderConnectionStore;
import com.leo.remote.rfid.native_bridge.*;
import com.leo.remote.rfid.sdk.tag.ReaderTagOperations;
import com.leo.remote.rfid.sdk.connection.transport.ReaderBleTransport;
import com.leo.remote.rfid.sdk.connection.transport.ReaderWifiMonitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cn.wandersnail.ble.Device;
import com.leo.remote.R;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns connection attempts, transports, handshake, heartbeat, and disconnect cleanup. */
final class ReaderConnectionOrchestrator {
    static final int WIFI_PORT = 1200;
    private static final Logger LOGGER = Logger.getLogger("UhfReader");

    private final ReaderTransportGateway transportGateway;
    private final ReaderConfigurationGateway configurationGateway;
    private final ReaderInventoryGateway inventoryGateway;
    private final ReaderConfigurationStore configStore;
    private final ReaderConnectionStore connectionStore;
    private final ReaderConfigurationManager configurationManager;
    private final ReaderTagOperations tagOperations;
    private final ReaderInventoryController inventoryController;
    private final ReaderConnectionManager connectionManager;
    private final ReaderCommandExecutor commandExecutor;
    private final ReaderMainThreadDispatcher mainThread;
    private final ReaderBleTransport bleTransport;
    private final ReaderWifiMonitor wifiMonitor;
    private final Runnable startService;
    private final Runnable stopService;
    private final IntFunction<String> stringResolver;
    private final Runnable wifiHeartbeat = this::runWifiHeartbeat;
    private final Object sdkLock = new Object();
    private volatile boolean sdkInitialized;

    ReaderConnectionOrchestrator(ReaderTransportGateway transportGateway,
            ReaderConfigurationGateway configurationGateway,
            ReaderInventoryGateway inventoryGateway,
            ReaderConfigurationStore configStore,
            ReaderConnectionStore connectionStore,
            ReaderConfigurationManager configurationManager,
            ReaderTagOperations tagOperations,
            ReaderInventoryController inventoryController,
            ReaderConnectionManager connectionManager,
            ReaderCommandExecutor commandExecutor,
            ReaderSessionDependencies dependencies,
            Runnable startService, Runnable stopService) {
        this.transportGateway = transportGateway;
        this.configurationGateway = configurationGateway;
        this.inventoryGateway = inventoryGateway;
        this.configStore = configStore;
        this.connectionStore = connectionStore;
        this.configurationManager = configurationManager;
        this.tagOperations = tagOperations;
        this.inventoryController = inventoryController;
        this.connectionManager = connectionManager;
        this.commandExecutor = commandExecutor;
        mainThread = dependencies.mainThread;
        stringResolver = dependencies.stringResolver;
        this.startService = startService;
        this.stopService = stopService;
        bleTransport = dependencies.bleTransportFactory.apply(new ReaderBleTransport.Listener() {
            @Override
            public void onPhase(long attemptId, ConnectionPhase phase, String message) {
                if (isCurrent(attemptId)) {
                    publish(currentState().buildUpon().phase(phase).message(message)
                            .errorCode(0).build());
                }
            }

            @Override
            public void onReady(long attemptId) {
                if (!isCurrent(attemptId)) { return; }
                LOGGER.info("BLE data channel ready, starting handshake");
                publish(currentState().buildUpon().phase(ConnectionPhase.CONNECTING_DATA_CHANNEL)
                        .message("BLE 数据通道已建立").build());
                commandExecutor.execute(() -> performHandshake(attemptId, TransportType.BLE));
            }

            @Override
            public void onInboundData(long attemptId, byte[] data) {
                if (isCurrent(attemptId) && data != null && data.length > 0) {
                    transportGateway.pushRemoteData(data);
                }
            }

            @Override
            public void onDisconnected(long attemptId, String message, int errorCode,
                    DisconnectReason reason) {
                if (isCurrent(attemptId)
                        && currentState().getTransport() == TransportType.BLE) {
                    handleConnectionLost(message, errorCode, reason);
                }
            }
        });
        wifiMonitor = dependencies.wifiMonitorFactory.apply(() -> {
            if (currentState().getTransport() == TransportType.WIFI
                    && currentState().isConnected()) {
                handleConnectionLost("Wi-Fi network lost", -20, DisconnectReason.WIFI_LOST);
            }
        });
        wifiMonitor.start();
    }

    ReaderState currentState() { return connectionManager.getState(); }
    boolean isPendingDisconnectAlert() { return connectionManager.isPendingDisconnectAlert(); }
    DisconnectReason getLastUnexpectedReason() {
        return connectionManager.getLastUnexpectedReason();
    }
    void acknowledgeDisconnect() { connectionManager.acknowledgeDisconnect(); }
    boolean isConnectionFailureAcknowledged(ReaderState state) {
        return connectionManager.isConnectionFailureAcknowledged(state);
    }
    void acknowledgeConnectionFailure(ReaderState state) {
        connectionManager.acknowledgeConnectionFailure(state);
    }
    void publish(ReaderState state) { connectionManager.publish(state); }

    void connectWifi(@NonNull String address) {
        String normalized = address.trim();
        ReaderState previousState = currentState();
        long generation = connectionManager.beginAttempt();
        if (!isValidIpv4(normalized)) {
            clearSessionData();
            publish(new ReaderState.Builder().transport(TransportType.WIFI)
                    .phase(ConnectionPhase.FAILED).device("Wi-Fi reader", normalized)
                    .message("Invalid reader IP address").errorCode(-21)
                    .connectionFailure(ReaderConnectionFailure.READER)
                    .disconnectReason(DisconnectReason.SDK_ERROR).build());
            commandExecutor.execute(() -> disconnectTransport(previousState.getTransport(),
                    previousState.isInventoryRunning()));
            return;
        }
        startService.run();
        clearSessionData();
        publish(new ReaderState.Builder().transport(TransportType.WIFI)
                .phase(ConnectionPhase.CONNECTING).device("Wi-Fi reader", normalized)
                .message("正在连接读写器").build());
        commandExecutor.execute(() -> {
            try {
                disconnectTransport(previousState.getTransport(), previousState.isInventoryRunning());
                if (!isCurrent(generation)) { return; }
                ensureSdkInitialized();
                if (!wifiMonitor.hasWifiNetwork()) {
                    throw new ReaderException("Wi-Fi is not connected", -22);
                }
                transportGateway.setTransport(TransportType.WIFI);
                transportGateway.useRm70xx();
                int status = transportGateway.connectNetwork(normalized, WIFI_PORT);
                if (status != 0) {
                    throw new ReaderException("Reader network connection failed", status);
                }
                if (!isCurrent(generation)) {
                    transportGateway.closeNetwork();
                    return;
                }
                connectionStore.saveWifiAddress(normalized);
                performHandshake(generation, TransportType.WIFI);
            } catch (ReaderException error) {
                disconnectTransport(TransportType.WIFI, false);
                if (isCurrent(generation)) {
                    publishFailure(TransportType.WIFI, error.getMessage(), error.getErrorCode(),
                            DisconnectReason.SDK_ERROR);
                }
            }
        });
    }

    void connectBle(@NonNull Device device) {
        LOGGER.info("connect BLE name=" + device.getName() + " address=" + device.getAddress());
        ReaderState previousState = currentState();
        long generation = connectionManager.beginAttempt();
        clearSessionData();
        publish(new ReaderState.Builder().transport(TransportType.BLE)
                .phase(ConnectionPhase.CONNECTING).device(device.getName(), device.getAddress())
                .message("正在连接蓝牙设备").build());
        startService.run();
        commandExecutor.execute(() -> {
            try {
                disconnectTransport(previousState.getTransport(), previousState.isInventoryRunning());
                if (!isCurrent(generation)) { return; }
                ensureSdkInitialized();
                transportGateway.setTransport(TransportType.BLE);
                transportGateway.useRm70xx();
                transportGateway.setOutboundDataListener(bleTransport::write);
                mainThread.post(() -> {
                    if (isCurrent(generation)) { bleTransport.connect(device, generation); }
                });
            } catch (ReaderException error) {
                disconnectTransport(TransportType.BLE, false);
                if (isCurrent(generation)) {
                    publishFailure(TransportType.BLE, error.getMessage(), error.getErrorCode(),
                            DisconnectReason.SDK_ERROR);
                }
            }
        });
    }

    void disconnect(@NonNull DisconnectReason reason) {
        ConnectionPhase phase = currentState().getPhase();
        if (phase == ConnectionPhase.DISCONNECTED || phase == ConnectionPhase.FAILED) {
            connectionManager.beginAttempt();
            clearSessionData();
            if (currentState().getTransport() != TransportType.NONE
                    || currentState().getDisconnectReason() != DisconnectReason.NONE) {
                publish(new ReaderState.Builder().disconnectReason(reason).build());
            }
            stopService.run();
            return;
        }
        long generation = connectionManager.beginAttempt();
        publish(currentState().buildUpon().phase(ConnectionPhase.DISCONNECTING)
                .inventoryRunning(false).message("正在断开连接").build());
        commandExecutor.execute(() -> {
            disconnectTransport(currentState().getTransport(), currentState().isInventoryRunning());
            clearSessionData();
            if (isCurrent(generation)) {
                publish(new ReaderState.Builder().disconnectReason(reason).build());
            }
            stopService.run();
        });
    }

    void initializeNativeAtApplication() {
        synchronized (sdkLock) {
            if (sdkInitialized) { return; }
            int status = transportGateway.initialize();
            LOGGER.info("UHF SDK initialize status=" + status + " source=Application");
            if (status == 0) {
                transportGateway.useRm70xx();
                sdkInitialized = true;
            }
        }
    }

    void handleConnectionLost(String message, int errorCode, DisconnectReason reason) {
        ReaderState lostState = currentState();
        TransportType transport = lostState.getTransport();
        if (reason.isUnexpected() && lostState.isConnected()) {
            handleUnexpectedDisconnect(message, errorCode, reason);
            commandExecutor.execute(() -> disconnectTransport(transport, false));
            return;
        }
        long generation = connectionManager.beginAttempt();
        commandExecutor.execute(() -> {
            disconnectTransport(transport, lostState.isInventoryRunning());
            clearSessionData();
            if (isCurrent(generation)) {
                publishFailure(transport, message, errorCode, reason);
            }
        });
    }

    void handleUnexpectedDisconnect(@NonNull DisconnectReason reason) {
        handleUnexpectedDisconnect("Reader connection lost", -63, reason);
    }

    private void handleUnexpectedDisconnect(String message, int errorCode,
            DisconnectReason reason) {
        if (!reason.isUnexpected()) { return; }
        connectionManager.beginAttempt();
        mainThread.removeCallbacks(wifiHeartbeat);
        commandExecutor.failPending(new ReaderException(message, errorCode));
        inventoryController.discardMask();
        clearSessionData();
        connectionManager.publishUnexpectedDisconnect(currentState().buildUpon()
                .phase(ConnectionPhase.DISCONNECTED).inventoryRunning(false)
                .message(message).errorCode(errorCode).disconnectReason(reason).build(), reason);
    }

    private void performHandshake(long generation, TransportType attemptTransport) {
        if (!isCurrent(generation)) {
            disconnectTransport(attemptTransport, false);
            return;
        }
        LOGGER.info("RM70XX handshake started generation=" + generation
                + " transport=" + attemptTransport + " address=" + currentState().getAddress());
        if (!connectionManager.publishIfCurrent(generation, currentState().buildUpon()
                .phase(ConnectionPhase.VERIFYING_MODULE)
                .message(stringResolver.apply(R.string.reader_verifying_detail)).build())) {
            disconnectTransport(attemptTransport, false);
            return;
        }
        try {
            ReaderHandshake.Result result = ReaderHandshake.perform(transportGateway,
                    configurationGateway, inventoryGateway, configStore, resourceId -> {
                        if (!isCurrent(generation)) { return; }
                        ConnectionPhase phase = resourceId == R.string.handshake_updating_params
                                || currentState().getPhase() == ConnectionPhase.UPDATING_PARAMETERS
                                ? ConnectionPhase.UPDATING_PARAMETERS
                                : ConnectionPhase.VERIFYING_MODULE;
                        connectionManager.publishIfCurrent(generation, currentState().buildUpon()
                                .phase(phase).message(stringResolver.apply(resourceId)).build());
                    });
            if (!isCurrent(generation)) {
                disconnectTransport(attemptTransport, false);
                return;
            }
            ReaderModuleInfo info = result.moduleInfo;
            configurationManager.restore(result.configuration);
            if (!isCurrent(generation)) {
                configurationManager.clear();
                disconnectTransport(attemptTransport, false);
                return;
            }
            if (inventoryController.getMask() != null
                    && inventoryController.getMaskProtocol() != TagProtocol.ISO_18000_6C) {
                inventoryController.discardMask();
            }
            boolean published = connectionManager.publishIfCurrent(generation,
                    currentState().buildUpon().phase(ConnectionPhase.CONNECTED)
                            .moduleSubtype(info.subtype, info.rawSubtype)
                            .protocol(TagProtocol.ISO_18000_6C)
                            .versions(info.boardSerial, info.boardVersion,
                                    info.moduleSerial, info.moduleVersion)
                            .message("").errorCode(0).inventoryRunning(false).build());
            if (!published) {
                configurationManager.clear();
                disconnectTransport(attemptTransport, false);
                return;
            }
            connectionManager.clearUnexpectedDisconnect();
            configurationManager.publishCurrent();
            if (attemptTransport == TransportType.WIFI) { scheduleWifiHeartbeat(generation); }
        } catch (ReaderException error) {
            LOGGER.log(Level.SEVERE, "RM70XX handshake failed code=" + error.getErrorCode()
                    + " message=" + error.getMessage(), error);
            disconnectTransport(attemptTransport, false);
            if (isCurrent(generation)) {
                publishFailure(attemptTransport, error.getMessage(), error.getErrorCode(),
                        DisconnectReason.SDK_ERROR);
            }
        }
    }

    private void ensureSdkInitialized() throws ReaderException {
        synchronized (sdkLock) {
            if (sdkInitialized) { return; }
            int status = transportGateway.initialize();
            if (status != 0) {
                throw new ReaderException("Unable to initialize UHF SDK", status);
            }
            transportGateway.useRm70xx();
            sdkInitialized = true;
        }
    }

    private void disconnectTransport(TransportType transport, boolean inventoryRunning) {
        mainThread.removeCallbacks(wifiHeartbeat);
        if (inventoryRunning) { inventoryGateway.stopInventory(); }
        if (transport == TransportType.WIFI) { transportGateway.closeNetwork(); }
        if (transport == TransportType.BLE) { disconnectBleTransportAndWait(); }
        transportGateway.setOutboundDataListener(null);
    }

    private void disconnectBleTransportAndWait() {
        if (mainThread.isMainThread()) {
            bleTransport.disconnect();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mainThread.post(() -> {
            try { bleTransport.disconnect(); }
            finally { latch.countDown(); }
        });
        try { latch.await(2, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    private void clearSessionData() {
        configurationManager.clear();
        tagOperations.clearCurrentTag();
    }

    private boolean isCurrent(long generation) {
        return connectionManager.isCurrent(generation);
    }

    private void publishFailure(TransportType transport, String message, int errorCode,
            DisconnectReason reason) {
        ReaderState previous = currentState();
        publish(previous.buildUpon().transport(transport).phase(ConnectionPhase.FAILED)
                .message(message).errorCode(errorCode)
                .connectionFailure(ReaderConnectionFailure.from(transport, previous.getPhase()))
                .disconnectReason(reason).inventoryRunning(false).build());
        stopService.run();
    }

    private void scheduleWifiHeartbeat(long generation) {
        if (!isCurrent(generation)) { return; }
        mainThread.removeCallbacks(wifiHeartbeat);
        mainThread.postDelayed(wifiHeartbeat, 8_000);
    }

    private void runWifiHeartbeat() {
        if (!currentState().isConnected()
                || currentState().getTransport() != TransportType.WIFI) { return; }
        long generation = connectionManager.getGeneration();
        commandExecutor.execute(() -> {
            if (!isCurrent(generation) || currentState().isInventoryRunning()) {
                scheduleWifiHeartbeat(generation);
                return;
            }
            try {
                transportGateway.readModuleInfo();
                scheduleWifiHeartbeat(generation);
            } catch (ReaderException error) {
                handleConnectionLost("Wi-Fi reader heartbeat failed", error.getErrorCode(),
                        DisconnectReason.SDK_ERROR);
            }
        });
    }

    static boolean isValidIpv4(@Nullable String value) {
        if (value == null) { return false; }
        String[] parts = value.trim().split("\\.", -1);
        if (parts.length != 4) { return false; }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) { return false; }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) { return false; }
            }
            int number = Integer.parseInt(part);
            if (number > 255 || (part.length() > 1 && part.charAt(0) == '0')) { return false; }
        }
        return true;
    }
}
