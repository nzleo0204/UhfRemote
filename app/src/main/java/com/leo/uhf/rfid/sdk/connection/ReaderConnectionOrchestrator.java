package com.leo.uhf.rfid.sdk.connection;

import com.leo.uhf.rfid.sdk.inventory.ReaderInventoryController;
import com.leo.uhf.rfid.sdk.config.ReaderConfigurationManager;
import com.leo.uhf.rfid.sdk.model.*;
import com.leo.uhf.rfid.sdk.storage.ReaderConfigurationStore;
import com.leo.uhf.rfid.sdk.storage.ReaderConnectionStore;
import com.leo.uhf.rfid.sdk.bridge.*;
import com.leo.uhf.rfid.sdk.tag.ReaderTagOperations;
import com.leo.uhf.rfid.sdk.connection.bluetooth.ReaderBleTransport;
import com.leo.uhf.rfid.sdk.connection.wifi.ReaderWifiMonitor;
import com.leo.uhf.rfid.sdk.connection.serial.SerialConfig;
import com.leo.uhf.rfid.sdk.connection.serial.DelayPowerController;
import com.leo.uhf.rfid.sdk.connection.serial.SerialPowerController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cn.wandersnail.ble.Device;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 编排连接代次、传输通道、握手、心跳和断开清理。
 */
final class ReaderConnectionOrchestrator {
    static final int WIFI_PORT = 1200;
    private static final Logger LOGGER = Logger.getLogger("UhfReader");

    private final ReaderTransportGateway transportGateway;
    private final ReaderConfigurationGateway configurationGateway;
    private final InventoryBridge inventoryGateway;
    private final ReaderConfigurationStore configStore;
    private final ReaderConnectionStore connectionStore;
    private final ReaderConfigurationManager configurationManager;
    private final ReaderTagOperations tagOperations;
    private final ReaderInventoryController inventoryController;
    private final ReaderConnectionManager connectionManager;
    private final ReaderCommandExecutor commandExecutor;
    private final MainThreadDispatcher mainThread;
    private final Function<ReaderProgress, String> messageResolver;
    private final ReaderBleTransport bleTransport;
    private final ReaderWifiMonitor wifiMonitor;
    private final Runnable startService;
    private final Runnable stopService;
    private volatile SerialPowerController serialPowerController;
    private final Runnable wifiHeartbeat = this::runWifiHeartbeat;
    private final Object sdkLock = new Object();
    private volatile boolean sdkInitialized;

    ReaderConnectionOrchestrator(ReaderTransportGateway transportGateway,
            ReaderConfigurationGateway configurationGateway,
            InventoryBridge inventoryGateway,
            ReaderConfigurationStore configStore,
            ReaderConnectionStore connectionStore,
            ReaderConfigurationManager configurationManager,
            ReaderTagOperations tagOperations,
            ReaderInventoryController inventoryController,
            ReaderConnectionManager connectionManager,
            ReaderCommandExecutor commandExecutor,
            SessionDeps dependencies,
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
        messageResolver = dependencies.messageResolver;
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
                commandExecutor.execute(() -> performHandshake(attemptId, ConnectionType.BLE));
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
                        && currentState().getTransport() == ConnectionType.BLE) {
                    handleConnectionLost(message, errorCode, reason);
                }
            }
        });
        wifiMonitor = dependencies.wifiMonitorFactory.apply(() -> {
            if (currentState().getTransport() == ConnectionType.WIFI
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
            publish(new ReaderState.Builder().transport(ConnectionType.WIFI)
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
        publish(new ReaderState.Builder().transport(ConnectionType.WIFI)
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
                transportGateway.setTransport(ConnectionType.WIFI);
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
                performHandshake(generation, ConnectionType.WIFI);
            } catch (ReaderException | RuntimeException error) {
                disconnectTransport(ConnectionType.WIFI, false);
                if (isCurrent(generation)) {
                    publishFailure(ConnectionType.WIFI, error.getMessage(), connectionErrorCode(error, -23),
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
        publish(new ReaderState.Builder().transport(ConnectionType.BLE)
                .phase(ConnectionPhase.CONNECTING).device(device.getName(), device.getAddress())
                .message("正在连接蓝牙设备").build());
        startService.run();
        commandExecutor.execute(() -> {
            try {
                disconnectTransport(previousState.getTransport(), previousState.isInventoryRunning());
                if (!isCurrent(generation)) { return; }
                ensureSdkInitialized();
                transportGateway.setTransport(ConnectionType.BLE);
                transportGateway.useRm70xx();
                transportGateway.setOutboundDataListener(bleTransport::write);
                mainThread.post(() -> {
                    if (isCurrent(generation)) { bleTransport.connect(device, generation); }
                });
            } catch (ReaderException | RuntimeException error) {
                disconnectTransport(ConnectionType.BLE, false);
                if (isCurrent(generation)) {
                    publishFailure(ConnectionType.BLE, error.getMessage(), connectionErrorCode(error, -24),
                            DisconnectReason.SDK_ERROR);
                }
            }
        });
    }

    void connectSerial(@NonNull SerialConfig config) {
        connectSerial(config, new DelayPowerController(config.powerDelayMs));
    }

    void connectSerial(@NonNull SerialConfig config,
            @NonNull SerialPowerController powerController) {
        ReaderState previousState = currentState();
        long generation = connectionManager.beginAttempt();
        clearSessionData();
        publish(new ReaderState.Builder().transport(ConnectionType.SERIAL)
                .phase(ConnectionPhase.CONNECTING).device("串口读写器", config.portPath)
                .moduleSubtype(config.moduleSubtype, config.moduleSubtype.getRawValue())
                .message("正在连接串口设备").build());
        startService.run();
        commandExecutor.execute(() -> {
            boolean serialOpened = false;
            try {
                disconnectTransport(previousState.getTransport(), previousState.isInventoryRunning());
                if (!isCurrent(generation)) { return; }
                ensureSdkInitialized();
                transportGateway.setTransport(ConnectionType.SERIAL);
                transportGateway.setModuleSubtype(config.moduleSubtype);
                powerController.powerOn();
                waitForSerialPower(powerController);
                if (!isCurrent(generation)) {
                    powerController.powerOff();
                    return;
                }
                int status = transportGateway.openSerial(config.portPath, config.baudRate);
                if (status != 0) {
                    throw new ReaderException("串口打开失败", status);
                }
                serialOpened = true;
                serialPowerController = powerController;
                if (!isCurrent(generation)) {
                    disconnectTransport(ConnectionType.SERIAL, false);
                    return;
                }
                publish(currentState().buildUpon().phase(ConnectionPhase.CONNECTING_DATA_CHANNEL)
                        .message("串口数据通道已建立").build());
                performHandshake(generation, ConnectionType.SERIAL);
            } catch (ReaderException | java.io.IOException | RuntimeException error) {
                if (serialOpened) { transportGateway.closeSerial(); }
                powerController.powerOff();
                if (serialPowerController == powerController) {
                    serialPowerController = null;
                }
                if (isCurrent(generation)) {
                    publishFailure(ConnectionType.SERIAL, error.getMessage(),
                            connectionErrorCode(error, -65),
                            DisconnectReason.SDK_ERROR);
                }
            }
        });
    }

    private void waitForSerialPower(@NonNull SerialPowerController powerController)
            throws ReaderException {
        try {
            Thread.sleep(powerController.getDelayAfterPowerOn());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ReaderException("串口上电等待被中断", -67);
        }
    }

    void disconnect(@NonNull DisconnectReason reason) {
        ConnectionPhase phase = currentState().getPhase();
        if (phase == ConnectionPhase.DISCONNECTED || phase == ConnectionPhase.FAILED) {
            connectionManager.beginAttempt();
            clearSessionData();
            if (currentState().getTransport() != ConnectionType.NONE
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
        ConnectionType transport = lostState.getTransport();
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

    private void performHandshake(long generation, ConnectionType attemptTransport) {
        if (!isCurrent(generation)) {
            disconnectTransport(attemptTransport, false);
            return;
        }
        LOGGER.info("Reader handshake started generation=" + generation
                + " transport=" + attemptTransport + " address=" + currentState().getAddress());
        if (!connectionManager.publishIfCurrent(generation, currentState().buildUpon()
                .phase(ConnectionPhase.VERIFYING_MODULE)
                .message(resolveMessage(ReaderProgress.VERIFYING_MODULE)).build())) {
            disconnectTransport(attemptTransport, false);
            return;
        }
        try {
            ReaderHandshake.Result result = ReaderHandshake.perform(transportGateway,
                    configurationGateway, inventoryGateway, configStore, resourceId -> {
                        if (!isCurrent(generation)) { return; }
                        ConnectionPhase phase = resourceId == ReaderProgress.UPDATING_PARAMETERS
                                || currentState().getPhase() == ConnectionPhase.UPDATING_PARAMETERS
                                ? ConnectionPhase.UPDATING_PARAMETERS
                                : ConnectionPhase.VERIFYING_MODULE;
                        connectionManager.publishIfCurrent(generation, currentState().buildUpon()
                                .phase(phase).message(resolveMessage(resourceId)).build());
                    }, attemptTransport);
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
            if (attemptTransport == ConnectionType.WIFI) { scheduleWifiHeartbeat(generation); }
        } catch (ReaderException error) {
            LOGGER.log(Level.SEVERE, "Reader handshake failed code=" + error.getErrorCode()
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

    private void disconnectTransport(ConnectionType transport, boolean inventoryRunning) {
        mainThread.removeCallbacks(wifiHeartbeat);
        if (inventoryRunning) { inventoryGateway.stopInventory(); }
        if (transport == ConnectionType.WIFI) { transportGateway.closeNetwork(); }
        if (transport == ConnectionType.BLE) { disconnectBleTransportAndWait(); }
        if (transport == ConnectionType.SERIAL) {
            transportGateway.closeSerial();
            SerialPowerController powerController = serialPowerController;
            serialPowerController = null;
            if (powerController != null) { powerController.powerOff(); }
        }
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

    private String resolveMessage(ReaderProgress progress) {
        String message = messageResolver.apply(progress);
        return message == null ? progress.getDefaultMessage() : message;
    }

    private void publishFailure(ConnectionType transport, String message, int errorCode,
            DisconnectReason reason) {
        ReaderState previous = currentState();
        publish(previous.buildUpon().transport(transport).phase(ConnectionPhase.FAILED)
                .message(message).errorCode(errorCode)
                .connectionFailure(ReaderConnectionFailure.from(transport, previous.getPhase()))
                .disconnectReason(reason).inventoryRunning(false).build());
        stopService.run();
    }

    /** 将 Native/适配器运行时异常转换为稳定的连接错误码。 */
    private static int connectionErrorCode(Throwable error, int fallback) {
        return error instanceof ReaderException reader ? reader.getErrorCode() : fallback;
    }

    private void scheduleWifiHeartbeat(long generation) {
        if (!isCurrent(generation)) { return; }
        mainThread.removeCallbacks(wifiHeartbeat);
        mainThread.postDelayed(wifiHeartbeat, 8_000);
    }

    private void runWifiHeartbeat() {
        if (!currentState().isConnected()
                || currentState().getTransport() != ConnectionType.WIFI) { return; }
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
