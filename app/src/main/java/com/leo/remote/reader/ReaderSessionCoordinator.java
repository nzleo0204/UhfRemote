package com.leo.remote.reader;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import cn.wandersnail.ble.Device;
import com.tencent.mmkv.MMKV;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * RFID Reader 会话编排器，协调连接、配置、盘点和标签操作。
 *
 * <p>所有 UHF SDK 调用串行运行在 {@code uhf-sdk} 线程；观察者回调切回主线程。
 * 连接代次用于丢弃过期异步结果，公开状态字段使用 {@code volatile} 保证跨线程可见性。
 * 对外 API 由 {@link ReaderSessionManager} 门面提供。</p>
 */
@SuppressLint("LogNotTimber")
final class ReaderSessionCoordinator {
    private static final String TAG = "UhfReader";
    public static final int WIFI_PORT = 1200;
    private static final String MMKV_ID = "reader_connection";
    private static final String KEY_WIFI_ADDRESS = "wifi_address";
    private static volatile ReaderSessionCoordinator instance;

    private final UhfSdkGateway gateway;
    private final Application application;
    private volatile ExecutorService sdkExecutor;
    private final Handler mainHandler;
    private final ReaderStatePublisher statePublisher;
    private final CopyOnWriteArraySet<CompletableFuture<?>> pendingOperations = new CopyOnWriteArraySet<>();
    private final BleTransport bleTransport;
    private final WifiNetworkMonitor wifiMonitor;
    private final MMKV storage;
    private final ReaderConfigCache configCache;
    private final ReaderConfigurationManager configurationManager;
    private final ReaderTagOperations tagOperations;
    private final ReaderInventoryController inventoryController;
    private final ReaderConnectionManager connectionManager;

    private final Runnable wifiHeartbeat = this::runWifiHeartbeat;
    private volatile boolean sdkInitialized;
    private volatile boolean shuttingDown;
    private final Object serviceLock = new Object();
    private volatile ReaderConnectionService connectionService;

    private static ExecutorService createSdkExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "uhf-sdk");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static void initialize(@NonNull Application application) {
        ReaderSessionCoordinator manager = getInstance(application);
        manager.initializeNativeAtApplication();
    }

    public static ReaderSessionCoordinator getInstance(@NonNull Application application) {
        if (instance == null) {
            synchronized (ReaderSessionCoordinator.class) {
                if (instance == null) {
                    instance = new ReaderSessionCoordinator(application, new NativeUhfSdkGateway());
                }
            }
        }
        return instance;
    }

    static ReaderSessionCoordinator createForTest(Application application, UhfSdkGateway gateway) {
        return new ReaderSessionCoordinator(application, gateway);
    }

    private ReaderSessionCoordinator(Application application, UhfSdkGateway gateway) {
        this.application = application;
        this.gateway = gateway;
        sdkExecutor = createSdkExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        statePublisher = new ReaderStatePublisher();
        connectionManager = new ReaderConnectionManager(statePublisher,
                this::updateConnectionService);
        storage = MMKV.mmkvWithID(MMKV_ID);
        configCache = new ReaderConfigCache();
        configurationManager = new ReaderConfigurationManager(gateway, configCache,
                statePublisher);
        tagOperations = new ReaderTagOperations(gateway, statePublisher);
        inventoryController = new ReaderInventoryController(gateway, configCache, statePublisher,
                (callback, delayMillis) -> mainHandler.postDelayed(callback, delayMillis));
        bleTransport = new BleTransport(new BleTransport.Listener() {
            @Override
            public void onPhase(long attemptId, ConnectionPhase phase, String message) {
                if (isCurrentConnection(attemptId)) {
                    publish(currentState().buildUpon().phase(phase).message(message).errorCode(0).build());
                }
            }

            @Override
            public void onReady(long attemptId) {
                if (!isCurrentConnection(attemptId)) { return; }
                Log.i(TAG, "BLE data channel ready, starting handshake");
                publish(currentState().buildUpon().phase(ConnectionPhase.CONNECTING_DATA_CHANNEL)
                        .message("BLE 数据通道已建立").build());
                sdkExecutor.execute(() -> performHandshake(attemptId));
            }

            @Override
            public void onInboundData(long attemptId, byte[] data) {
                if (isCurrentConnection(attemptId)) {
                    if (data != null && data.length > 0) { gateway.pushRemoteData(data); }
                }
            }

            @Override
            public void onDisconnected(long attemptId, String message, int errorCode,
                    DisconnectReason reason) {
                if (isCurrentConnection(attemptId) && currentState().getTransport() == TransportType.BLE) {
                    handleConnectionLost(message, errorCode, reason);
                }
            }
        });
        wifiMonitor = new WifiNetworkMonitor(application,
                () -> {
                    if (currentState().getTransport() == TransportType.WIFI && currentState().isConnected()) {
                        handleConnectionLost("Wi-Fi network lost", -20, DisconnectReason.WIFI_LOST);
                    }
                });
        gateway.setInventoryListener(tag -> {
            if (!currentState().isConnected() || !currentState().isInventoryRunning()) { return; }
            inventoryController.onTag(tag);
        });
        gateway.setInventoryStopListener(this::handleInventoryStopped);
        wifiMonitor.start();
    }

    public ReaderState getState() { return currentState(); }
    public ReaderConfiguration getConfiguration() { return configurationManager.getConfiguration(); }
    public ReaderTag getCurrentTag() { return tagOperations.getCurrentTag(); }
    public int getInventoryMode() { return configurationManager.getInventoryMode(); }
    public boolean isPendingDisconnectAlert() {
        return connectionManager.isPendingDisconnectAlert();
    }
    public DisconnectReason getLastUnexpectedReason() {
        return connectionManager.getLastUnexpectedReason();
    }
    public void acknowledgeDisconnect() { connectionManager.acknowledgeDisconnect(); }

    private ReaderState currentState() {
        return connectionManager.getState();
    }

    public void addObserver(@NonNull ReaderObserver observer) {
        statePublisher.addObserver(observer, () -> {
            observer.onReaderStateChanged(currentState());
            ReaderConfiguration configuration = configurationManager.getConfiguration();
            if (configuration != null) { observer.onReaderConfigurationChanged(configuration); }
            ReaderTag currentTag = tagOperations.getCurrentTag();
            if (currentTag != null) { observer.onCurrentTagChanged(currentTag); }
            observer.onInventoryMaskChanged(inventoryController.getMask());
            observer.onSingleTagMaskChanged(tagOperations.getSingleTagMask());
            observer.onInventoryChanged(inventoryController.snapshot(),
                    inventoryController.getTotalReads());
        });
    }

    public void removeObserver(@NonNull ReaderObserver observer) {
        statePublisher.removeObserver(observer);
    }

    public String getSavedWifiAddress() {
        String value = storage == null ? null : storage.decodeString(KEY_WIFI_ADDRESS, "");
        return value == null ? "" : value;
    }

    public static boolean isValidIpv4(@Nullable String value) {
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

    public void connectWifi(@NonNull String address) {
        String normalized = address.trim();
        ReaderState previousState = currentState();
        long generation = connectionManager.beginAttempt();
        if (!isValidIpv4(normalized)) {
            configurationManager.clear();
            clearCurrentTag();
            publish(new ReaderState.Builder().transport(TransportType.WIFI).phase(ConnectionPhase.FAILED)
                    .device("Wi-Fi reader", normalized).message("Invalid reader IP address")
                    .errorCode(-21).disconnectReason(DisconnectReason.SDK_ERROR).build());
            sdkExecutor.execute(() -> disconnectTransportInternal(previousState.getTransport(),
                    previousState.isInventoryRunning()));
            return;
        }
        startConnectionService();
        configurationManager.clear();
        clearCurrentTag();
        publish(new ReaderState.Builder().transport(TransportType.WIFI)
                .phase(ConnectionPhase.CONNECTING).device("Wi-Fi reader", normalized)
                .message("Connecting to reader").build());
        sdkExecutor.execute(() -> {
            try {
                disconnectTransportInternal(previousState.getTransport(), previousState.isInventoryRunning());
                if (!isCurrentConnection(generation)) { return; }
                ensureSdkInitialized();
                if (!wifiMonitor.hasWifiNetwork()) {
                    throw new ReaderException("Wi-Fi is not connected", -22);
                }
                gateway.setTransport(TransportType.WIFI);
                gateway.useRm70xx();
                int status = gateway.connectNetwork(normalized, WIFI_PORT);
                if (status != 0) {
                    throw new ReaderException("Reader network connection failed", status);
                }
                if (!isCurrentConnection(generation)) {
                    gateway.closeNetwork();
                    return;
                }
                if (storage != null) { storage.encode(KEY_WIFI_ADDRESS, normalized); }
                performHandshake(generation);
            } catch (ReaderException error) {
                disconnectTransportInternal();
                if (isCurrentConnection(generation)) {
                    publishFailure(TransportType.WIFI, error.getMessage(), error.getErrorCode(),
                            DisconnectReason.SDK_ERROR);
                }
            }
        });
    }

    public void connectBle(@NonNull Device device) {
        Log.i(TAG, "connect BLE name=" + device.getName() + " address=" + device.getAddress());
        ReaderState previousState = currentState();
        long generation = connectionManager.beginAttempt();
        configurationManager.clear();
        clearCurrentTag();
        publish(new ReaderState.Builder().transport(TransportType.BLE)
                .phase(ConnectionPhase.CONNECTING).device(device.getName(), device.getAddress())
                .message("Connecting to BLE device").build());
        startConnectionService();
        sdkExecutor.execute(() -> {
            try {
                disconnectTransportInternal(previousState.getTransport(), previousState.isInventoryRunning());
                if (!isCurrentConnection(generation)) { return; }
                ensureSdkInitialized();
                gateway.setTransport(TransportType.BLE);
                gateway.useRm70xx();
                gateway.setOutboundDataListener(bleTransport::write);
                mainHandler.post(() -> {
                    if (isCurrentConnection(generation)) { bleTransport.connect(device, generation); }
                });
            } catch (ReaderException error) {
                disconnectTransportInternal(TransportType.BLE, false);
                if (isCurrentConnection(generation)) {
                    publishFailure(TransportType.BLE, error.getMessage(), error.getErrorCode(),
                            DisconnectReason.SDK_ERROR);
                }
            }
        });
    }

    public void disconnect() {
        disconnect(DisconnectReason.USER);
    }

    public void disconnect(@NonNull DisconnectReason reason) {
        ConnectionPhase phase = currentState().getPhase();
        if (phase == ConnectionPhase.DISCONNECTED || phase == ConnectionPhase.FAILED) {
            connectionManager.beginAttempt();
            configurationManager.clear();
            clearCurrentTag();
            if (currentState().getTransport() != TransportType.NONE || currentState().getDisconnectReason() != DisconnectReason.NONE) {
                publish(new ReaderState.Builder().disconnectReason(reason).build());
            }
            stopConnectionService();
            return;
        }
        long generation = connectionManager.beginAttempt();
        publish(currentState().buildUpon().phase(ConnectionPhase.DISCONNECTING)
                .inventoryRunning(false).message("Disconnecting").build());
        sdkExecutor.execute(() -> {
            disconnectTransportInternal();
            configurationManager.clear();
            clearCurrentTag();
            if (isCurrentConnection(generation)) {
                publish(new ReaderState.Builder().disconnectReason(reason).build());
            }
            stopConnectionService();
        });
    }

    public void shutdown() {
        shuttingDown = true;
        releaseNative();
    }

    /** Releases native resources while leaving this session reusable in the live process. */
    public void releaseNative() {
        connectionManager.beginAttempt();
        ExecutorService executor = sdkExecutor;
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable release = () -> {
            disconnectTransportInternal();
            if (sdkInitialized) {
                gateway.deinitialize();
                sdkInitialized = false;
                Log.i(TAG, "UHF SDK deinitialized source=explicit release");
            }
            future.complete(null);
        };
        if (Thread.currentThread().getName().equals("uhf-sdk")) {
            release.run();
        } else {
            try { executor.execute(release); }
            catch (RuntimeException error) { release.run(); }
            try { future.get(5000, TimeUnit.MILLISECONDS); }
            catch (Exception ignored) { Log.w(TAG, "Timed out releasing UHF SDK", ignored); }
        }
        executor.shutdownNow();
        sdkExecutor = createSdkExecutor();
        stopConnectionService();
    }

    public CompletableFuture<Integer> setProtocol(@NonNull TagProtocol protocol) {
        return submitConnected(() -> {
            if (!currentState().getModuleSubtype().supportedProtocols().contains(protocol)) {
                throw new ReaderException("Protocol is not supported by this module", -30);
            }
            int status = stopInventoryInternal();
            if (status != 0) { return status; }
            if (inventoryController.getMask() != null) {
                status = monitorSdkStatus(gateway.clearInventoryMask(currentState().getProtocol(),
                        currentState().getModuleSubtype(), inventoryMaskRestoreValue()));
                Log.i(TAG, "clear inventory mask before protocol switch status=" + status);
                if (status != 0) { return status; }
                inventoryController.discardMask();
            }
            status = monitorSdkStatus(gateway.setProtocol(protocol));
            ReaderConfiguration current = configurationManager.getConfiguration();
            InventoryArea mappedArea = InventoryArea.of(protocol,
                    current == null ? 0 : current.inventoryArea);
            int mappedAddress = current == null || mappedArea.isBaseOnly()
                    ? 0 : current.inventoryAddress;
            int mappedLength = current == null || mappedArea.isBaseOnly()
                    ? 0 : current.inventoryWordLen;
            if (status == 0) {
                status = gateway.applyInventoryParams(protocol, mappedArea.getValue(),
                        mappedAddress, mappedLength);
            }
            status = monitorSdkStatus(status);
            if (status == 0) {
                clearCurrentTag();
                if (current != null) {
                    configurationManager.updateProtocolArea(currentState().getModuleSubtype(),
                            mappedArea.getValue(), mappedAddress, mappedLength);
                }
                inventoryController.resetInventory();
                publish(currentState().buildUpon().protocol(protocol).inventoryRunning(false).build());
                configurationManager.publishCurrent();
                inventoryController.publishInventory();
            }
            return status;
        });
    }

    public void setInventoryMode(int mode) {
        ModuleSubtype subtype = currentState().getModuleSubtype();
        int effectiveMode = configurationManager.setInventoryMode(subtype, mode);
        if (mode >= 0 && mode <= 2 && effectiveMode != mode) {
            Log.w(TAG, "inventory mode " + mode + " unsupported on " + subtype
                    + "; fall back to high performance");
        }
    }

    public CompletableFuture<Integer> setInventoryArea(int area, int address, int wordLen) {
        if (area < 0 || area > 3 || address < 0 || wordLen < 0
                || wordLen > ReaderConfiguration.MAX_INVENTORY_WORD_LEN) {
            return CompletableFuture.completedFuture(-31);
        }
        return submitConnected(() -> {
            return monitorSdkStatus(configurationManager.setInventoryArea(
                    currentState().getModuleSubtype(), area, address, wordLen));
        });
    }

    public CompletableFuture<Integer> refreshConfiguration() {
        return submitConnected(() -> {
            configurationManager.refresh(currentState().getModuleSubtype());
            return 0;
        }, false);
    }

    public CompletableFuture<Integer> setPower(int powerTenthsDbm) {
        return submitConnected(() -> configurationManager.setPower(
                currentState().getModuleSubtype(), powerTenthsDbm), false);
    }

    public CompletableFuture<Integer> setBlf(int profile) {
        return submitConnected(() -> monitorSdkStatus(configurationManager.setBlf(
                currentState().getModuleSubtype(), profile)));
    }

    /** Changes only Session; Target and the handshake Sel value are preserved. */
    public CompletableFuture<Integer> setSession(int session) {
        if (session < 0 || session > 3) {
            return CompletableFuture.completedFuture(-31);
        }
        return submitConnected(() -> {
            ModuleSubtype subtype = currentState().getModuleSubtype();
            ReaderConfiguration configuration = configurationManager.getConfiguration();
            int target = configuration.target;
            int selected = configCache.loadSelected(subtype);
            int status = monitorSdkStatus(configurationManager.applySession(
                    subtype, session, selected));
            Log.i(TAG, "setSession S" + session + " target=" + target
                    + " selected=" + selected + " status=" + status);
            if (status == 0 && inventoryController.isMaskApplied()) {
                gateway.applyInventoryMask(currentState().getProtocol(), subtype,
                        inventoryController.getMask());
            }
            if (status == 0) {
                configurationManager.commitSession(subtype, session);
            }
            return status;
        });
    }

    public CompletableFuture<Integer> setQ(boolean dynamic, int qValue, int minQValue,
            int maxQValue, int retryCount, int thresholdMultiplier) {
        return submitConnected(() -> {
            return monitorSdkStatus(configurationManager.setQ(currentState().getModuleSubtype(), dynamic,
                    qValue, minQValue, maxQValue, retryCount, thresholdMultiplier));
        });
    }

    public CompletableFuture<Integer> startInventory() {
        return submitConnected(this::startInventoryInternal);
    }

    /** Starts inventory on the SDK executor with the current mask flag. */
    private int startInventoryInternal() {
        int status = inventoryController.start(currentState().getProtocol(), currentState().getModuleSubtype(),
                configurationManager.getConfiguration(), configurationManager.getInventoryMode());
        status = monitorSdkStatus(status);
        if (status == 0) {
            publish(currentState().buildUpon().inventoryRunning(true).build());
        }
        return status;
    }

    public CompletableFuture<Integer> stopInventory() {
        return submitConnected(this::stopInventoryInternal);
    }

    public void clearInventory() {
        inventoryController.clearInventory();
    }

    public List<InventoryItem> getInventorySnapshot() { return inventoryController.snapshot(); }

    /** Applies a manual inventory mask without changing power, BLF, or Q settings. */
    public CompletableFuture<Integer> applyInventoryMask(@NonNull InventoryMaskConfig config) {
        return submitConnected(() -> {
            TagProtocol protocol = currentState().getProtocol();
            boolean wasRunning = currentState().isInventoryRunning();
            if (wasRunning) {
                int stopStatus = stopInventoryInternal();
                if (stopStatus != 0) { return stopStatus; }
            }
            ModuleSubtype subtype = currentState().getModuleSubtype();
            boolean capturedNow = !inventoryController.isSelectionCaptured();
            if (capturedNow && !captureInventoryMaskSelection(subtype)) {
                if (wasRunning) { startInventoryInternal(); }
                return -45;
            }
            int status = gateway.applyInventoryMask(protocol, subtype, config);
            if (status != 0 && capturedNow) {
                gateway.clearInventoryMask(protocol, subtype, inventoryMaskRestoreValue());
                inventoryController.clearSelection();
            }
            status = monitorSdkStatus(status);
            Log.i(TAG, "applyInventoryMask status=" + status + " bank=" + config.bank
                    + " offsetBits=" + config.offsetBits + " lengthBits=" + config.lengthBits);
            if (status == 0) {
                inventoryController.activateMask(config, protocol);
            }
            if (wasRunning) {
                int restartStatus = startInventoryInternal();
                if (status == 0 && restartStatus != 0) { status = restartStatus; }
            }
            return status;
        });
    }

    /** Clears only the active inventory Select criteria. */
    public CompletableFuture<Integer> clearInventoryMask() {
        if (inventoryController.getMask() == null) {
            return CompletableFuture.completedFuture(0);
        }
        if (!currentState().isConnected()) {
            inventoryController.discardMask();
            return CompletableFuture.completedFuture(0);
        }
        return submitConnected(() -> {
            TagProtocol maskProtocol = inventoryController.getMaskProtocol();
            TagProtocol protocol = maskProtocol == null ? currentState().getProtocol() : maskProtocol;
            boolean wasRunning = currentState().isInventoryRunning();
            if (wasRunning) {
                int stopStatus = stopInventoryInternal();
                if (stopStatus != 0) { return stopStatus; }
            }
            int status = monitorSdkStatus(gateway.clearInventoryMask(protocol,
                    currentState().getModuleSubtype(), inventoryMaskRestoreValue()));
            Log.i(TAG, "clearInventoryMask status=" + status);
            if (status == 0) {
                configCache.saveSelected(currentState().getModuleSubtype(), inventoryMaskRestoreValue());
                inventoryController.discardMask();
            }
            if (wasRunning) {
                int restartStatus = startInventoryInternal();
                if (status == 0 && restartStatus != 0) { status = restartStatus; }
            }
            return status;
        });
    }

    public boolean hasInventoryMask() {
        return inventoryController.getMask() != null;
    }

    @Nullable
    public InventoryMaskConfig getInventoryMask() {
        return inventoryController.getMask();
    }

    private boolean captureInventoryMaskSelection(ModuleSubtype subtype) {
        return inventoryController.captureSelection(subtype);
    }

    private int inventoryMaskRestoreValue() {
        return inventoryController.restoreValue(currentState().getModuleSubtype());
    }

    private int readSelectedForTemporaryMask(ModuleSubtype subtype) throws ReaderException {
        try {
            int[] queryValues = gateway.getQueryValues(subtype);
            if (queryValues == null || queryValues.length < 3) {
                throw new ReaderException("Unable to read Query Sel", -45);
            }
            return queryValues[2];
        } catch (RuntimeException error) {
            throw new ReaderException("Unable to read Query Sel", -45);
        }
    }

    public void setSingleTagMask(@Nullable InventoryMaskConfig config) {
        tagOperations.setSingleTagMask(config);
    }

    @Nullable
    public InventoryMaskConfig getSingleTagMask() {
        return tagOperations.getSingleTagMask();
    }

    /**
     * 读取当前目标标签的数据。
     *
     * <p>如果配置了单标签掩码，将应用掩码后读取；否则直接读取。
     */
    public CompletableFuture<TagReadResult> readCurrentTag(TagProtocol protocol, int length,
            int address, int bank, byte[] password) {
        return submitConnected(() -> {
            int status = stopInventoryInternal();
            if (status != 0) { throw new ReaderException("Unable to stop inventory", status); }

            TagProtocol currentProtocol = currentState().getProtocol();
            if (protocol != currentProtocol) {
                throw new ReaderException("Reader protocol changed before read", -41);
            }
            ModuleSubtype subtype = currentState().getModuleSubtype();
            InventoryMaskConfig singleTagMask = tagOperations.getSingleTagMask();
            InventoryMaskConfig inventoryMaskToRestore = inventoryController.getMask();
            boolean needRestoreInventoryMask = false;
            int selectedBeforeSingleMask = 0;
            boolean selectedBeforeSingleMaskCaptured = false;

            try {
                if (inventoryMaskToRestore != null) {
                    status = monitorSdkStatus(gateway.clearInventoryMask(currentProtocol,
                            subtype, inventoryMaskRestoreValue()));
                    if (status != 0) {
                        throw new ReaderException("Unable to clear inventory mask", status);
                    }
                    inventoryController.setMaskApplied(false);
                    needRestoreInventoryMask = true;
                }

                if (singleTagMask != null) {
                    selectedBeforeSingleMask = inventoryMaskToRestore == null
                            ? readSelectedForTemporaryMask(subtype)
                            : inventoryMaskRestoreValue();
                    selectedBeforeSingleMaskCaptured = true;
                    status = monitorSdkStatus(gateway.applyInventoryMask(currentProtocol,
                            subtype, singleTagMask));
                    if (status != 0) {
                        throw new ReaderException("Unable to apply single tag mask", status);
                    }
                }

                return tagOperations.read(protocol, length, address, bank, password);
            } finally {
                if (needRestoreInventoryMask && inventoryMaskToRestore != null) {
                    int restoreStatus = gateway.applyInventoryMask(currentProtocol, subtype,
                            inventoryMaskToRestore);
                    inventoryController.setMaskApplied(restoreStatus == 0);
                    if (restoreStatus != 0) {
                        throw new ReaderException("Unable to restore inventory mask", restoreStatus);
                    }
                } else if (singleTagMask != null && selectedBeforeSingleMaskCaptured) {
                    int clearStatus = gateway.clearTargetMask(currentProtocol, subtype,
                            selectedBeforeSingleMask);
                    if (clearStatus != 0) {
                        throw new ReaderException("Unable to clear single-tag mask", clearStatus);
                    }
                }
            }
        }, false);
    }

    public CompletableFuture<ReaderTag> readSingleTag() {
        return submitConnected(() -> {
            int status = stopInventoryInternal();
            if (status != 0) { throw new ReaderException("Unable to stop inventory", status); }
            return tagOperations.readSingleTag();
        });
    }

    public CompletableFuture<TagReadResult> readCurrentTag(int length, int address, int bank,
            byte[] password) {
        TagProtocol protocol = currentState().getProtocol();
        return withTargetMask(() -> tagOperations.read(protocol, length, address, bank, password));
    }

    public CompletableFuture<Integer> writeCurrentTag(int length, int address, int bank,
            byte[] password, byte[] data) {
        TagProtocol protocol = currentState().getProtocol();
        return withTargetMask(() -> monitorSdkStatus(
                tagOperations.write(protocol, length, address, bank, password, data)));
    }

    public CompletableFuture<Integer> lockCurrentTag(byte[] password, int bank, int policy) {
        return with6cTargetMask(() -> monitorSdkStatus(
                tagOperations.lock(password, bank, policy)));
    }

    public CompletableFuture<Integer> killCurrentTag(byte[] accessPassword, byte[] killPassword) {
        return with6cTargetMask(() -> monitorSdkStatus(
                tagOperations.kill(accessPassword, killPassword)));
    }

    private <T> CompletableFuture<T> with6cTargetMask(Callable<T> operation) {
        if (currentState().getProtocol() != TagProtocol.ISO_18000_6C) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new ReaderException("Operation is only supported for ISO 18000-6C", -41));
            return future;
        }
        return withTargetMask(operation);
    }

    private <T> CompletableFuture<T> withTargetMask(Callable<T> operation) {
        return submitConnected(() -> {
            if (tagOperations.getCurrentTag() == null) {
                throw new ReaderException("Read a target tag first", -40);
            }
            int status = stopInventoryInternal();
            if (status != 0) { throw new ReaderException("Unable to stop inventory", status); }
            TagProtocol protocol = currentState().getProtocol();
            InventoryMaskConfig maskToRestore = inventoryController.getMask();
            if (maskToRestore != null) {
                status = monitorSdkStatus(gateway.clearInventoryMask(protocol,
                        currentState().getModuleSubtype(), inventoryMaskRestoreValue()));
                if (status != 0) { throw new ReaderException("Unable to clear inventory mask", status); }
                inventoryController.setMaskApplied(false);
            }
            InventoryMaskConfig activeMask = tagOperations.getSingleTagMask();
            int selectedBeforeTargetMask = 0;
            if (activeMask != null) {
                selectedBeforeTargetMask = maskToRestore == null
                        ? readSelectedForTemporaryMask(currentState().getModuleSubtype())
                        : inventoryMaskRestoreValue();
            }
            try {
                if (activeMask != null) {
                    status = monitorSdkStatus(gateway.applyInventoryMask(protocol,
                            currentState().getModuleSubtype(), activeMask));
                    if (status != 0) {
                        throw new ReaderException("Unable to set single-tag mask", status);
                    }
                }
                return operation.call();
            } finally {
                if (maskToRestore != null) {
                    int restoreStatus = monitorSdkStatus(gateway.applyInventoryMask(protocol,
                            currentState().getModuleSubtype(), maskToRestore));
                    inventoryController.setMaskApplied(restoreStatus == 0);
                    if (restoreStatus != 0) {
                        throw new ReaderException("Unable to restore inventory mask", restoreStatus);
                    }
                } else if (activeMask != null) {
                    int clearStatus = monitorSdkStatus(gateway.clearTargetMask(protocol,
                            currentState().getModuleSubtype(), selectedBeforeTargetMask));
                    if (clearStatus != 0) {
                        throw new ReaderException("Unable to clear single-tag mask", clearStatus);
                    }
                }
            }
        });
    }

    private void performHandshake(long generation) {
        if (!isCurrentConnection(generation)) { return; }
        Log.i(TAG, "RM70XX handshake started generation=" + generation
                + " transport=" + currentState().getTransport() + " address=" + currentState().getAddress());
        publish(currentState().buildUpon().phase(ConnectionPhase.VERIFYING_MODULE)
                .message("Verifying RM70XX module").build());
        try {
            ReaderHandshake.Result result = ReaderHandshake.perform(gateway, configCache, resourceId ->
                    publish(currentState().buildUpon().phase(ConnectionPhase.VERIFYING_MODULE)
                            .message(application.getString(resourceId)).build()));
            if (!isCurrentConnection(generation)) { return; }
            ReaderModuleInfo info = result.moduleInfo;
            configurationManager.restore(result.configuration);
            if (inventoryController.getMask() != null
                    && inventoryController.getMaskProtocol() != TagProtocol.ISO_18000_6C) {
                inventoryController.discardMask();
                Log.i(TAG, "discard inventory mask because handshake restored 6C protocol");
            }
            Log.i(TAG, "RM70XX handshake succeeded subtype=" + info.subtype
                    + " rawSubtype=" + info.rawSubtype + " boardSerial=" + info.boardSerial
                    + " boardVersion=" + info.boardVersion + " moduleSerial=" + info.moduleSerial
                    + " moduleVersion=" + info.moduleVersion);
            publish(currentState().buildUpon().phase(ConnectionPhase.CONNECTED)
                    .moduleSubtype(info.subtype, info.rawSubtype)
                    .protocol(TagProtocol.ISO_18000_6C)
                    .versions(info.boardSerial, info.boardVersion, info.moduleSerial, info.moduleVersion)
                    .message("").errorCode(0).inventoryRunning(false).build());
            connectionManager.clearUnexpectedDisconnect();
            configurationManager.publishCurrent();
            if (currentState().getTransport() == TransportType.WIFI) { scheduleWifiHeartbeat(generation); }
        } catch (ReaderException error) {
            Log.e(TAG, "RM70XX handshake failed code=" + error.getErrorCode()
                    + " message=" + error.getMessage(), error);
            disconnectTransportInternal();
            if (isCurrentConnection(generation)) {
                publishFailure(currentState().getTransport(), error.getMessage(), error.getErrorCode(),
                        DisconnectReason.SDK_ERROR);
            }
        }
    }

    private void ensureSdkInitialized() throws ReaderException {
        synchronized (serviceLock) {
            if (sdkInitialized) { return; }
            if (sdkExecutor.isShutdown()) { sdkExecutor = createSdkExecutor(); }
            int status = gateway.initialize();
            Log.i(TAG, "UHF SDK lazy initialize status=" + status + " source=session");
            if (status != 0) {
                throw new ReaderException("Unable to initialize UHF SDK", status);
            }
            gateway.useRm70xx();
            sdkInitialized = true;
        }
    }

    private void initializeNativeAtApplication() {
        synchronized (serviceLock) {
            if (sdkInitialized) { return; }
            int status = gateway.initialize();
            Log.i(TAG, "UHF SDK initialize status=" + status + " source=Application");
            if (status == 0) {
                gateway.useRm70xx();
                sdkInitialized = true;
            }
        }
    }

    private void startConnectionService() {
        try {
            Intent intent = new Intent(application, ReaderConnectionService.class)
                    .setAction(ReaderConnectionService.ACTION_START);
            ContextCompat.startForegroundService(application, intent);
        } catch (Throwable error) {
            Log.e(TAG, "Unable to start reader connection service", error);
        }
    }

    private void stopConnectionService() {
        application.stopService(new Intent(application, ReaderConnectionService.class));
    }

    void onConnectionServiceCreated(@NonNull ReaderConnectionService service) {
        synchronized (serviceLock) {
            connectionService = service;
        }
        service.updateReaderState(currentState());
        Log.i(TAG, "reader connection foreground service created");
    }

    void onConnectionServiceDestroyed(@NonNull ReaderConnectionService service) {
        synchronized (serviceLock) {
            if (connectionService == service) { connectionService = null; }
        }
        Log.i(TAG, "reader connection foreground service destroyed");
    }

    private int stopInventoryInternal() {
        if (!currentState().isInventoryRunning()) { return 0; }
        int status = monitorSdkStatus(inventoryController.stop(currentState().isInventoryRunning()));
        if (status == 0) { publish(currentState().buildUpon().inventoryRunning(false).build()); }
        return status;
    }

    /** 模块自行结束盘点时同步会话状态，避免单次模式按钮停在“停止”。 */
    private void handleInventoryStopped(int status) {
        mainHandler.post(() -> {
            if (currentState().isInventoryRunning()) {
                Log.i(TAG, "inventory stopped by reader status=" + status);
                publish(currentState().buildUpon().inventoryRunning(false).build());
            }
        });
    }

    private void disconnectTransportInternal() {
        disconnectTransportInternal(currentState().getTransport(), currentState().isInventoryRunning());
    }

    private void disconnectTransportInternal(TransportType transport, boolean inventoryRunning) {
        mainHandler.removeCallbacks(wifiHeartbeat);
        if (inventoryRunning) { gateway.stopInventory(); }
        if (transport == TransportType.WIFI) { gateway.closeNetwork(); }
        if (transport == TransportType.BLE) {
            disconnectBleTransportAndWait();
        }
        gateway.setOutboundDataListener(null);
    }

    private void disconnectBleTransportAndWait() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            bleTransport.disconnect();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(() -> {
            try { bleTransport.disconnect(); }
            finally { latch.countDown(); }
        });
        try { latch.await(2, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    private void handleConnectionLost(String message, int errorCode, DisconnectReason reason) {
        if (shuttingDown) { return; }
        ReaderState lostState = currentState();
        TransportType transport = lostState.getTransport();
        if (reason.isUnexpected() && lostState.getPhase() != ConnectionPhase.DISCONNECTED
                && lostState.getPhase() != ConnectionPhase.FAILED) {
            handleUnexpectedDisconnect(message, errorCode, reason);
            sdkExecutor.execute(() -> disconnectTransportInternal(transport, false));
            return;
        }
        long generation = connectionManager.beginAttempt();
        sdkExecutor.execute(() -> {
            disconnectTransportInternal(transport, lostState.isInventoryRunning());
            configurationManager.clear();
            clearCurrentTag();
            if (isCurrentConnection(generation)) {
                publishFailure(transport, message, errorCode, reason);
            }
        });
    }

    /** Forces local state to stopped before any dead-link cleanup can block on native calls. */
    void handleUnexpectedDisconnect(@NonNull DisconnectReason reason) {
        handleUnexpectedDisconnect("Reader connection lost", -63, reason);
    }

    private void handleUnexpectedDisconnect(String message, int errorCode,
            @NonNull DisconnectReason reason) {
        if (!reason.isUnexpected()) { return; }
        connectionManager.beginAttempt();
        mainHandler.removeCallbacks(wifiHeartbeat);
        ReaderException failure = new ReaderException(message, errorCode);
        for (CompletableFuture<?> pending : pendingOperations) {
            pending.completeExceptionally(failure);
        }
        inventoryController.discardMask();
        configurationManager.clear();
        clearCurrentTag();
        connectionManager.publishUnexpectedDisconnect(currentState().buildUpon()
                .phase(ConnectionPhase.DISCONNECTED).inventoryRunning(false)
                .message(message).errorCode(errorCode).disconnectReason(reason).build(), reason);
    }

    private boolean isCurrentConnection(long generation) {
        return connectionManager.isCurrent(generation);
    }

    private void clearCurrentTag() {
        tagOperations.clearCurrentTag();
    }

    private <T> CompletableFuture<T> submitConnected(Callable<T> operation) {
        return submitConnected(operation, true);
    }

    private <T> CompletableFuture<T> submitConnected(Callable<T> operation,
            boolean disconnectOnReaderError) {
        CompletableFuture<T> future = new CompletableFuture<>();
        pendingOperations.add(future);
        future.whenComplete((value, error) -> pendingOperations.remove(future));
        try {
            sdkExecutor.execute(() -> {
                try {
                    if (!currentState().isConnected()) {
                        throw new ReaderException("Reader is not connected", -50);
                    }
                    future.complete(operation.call());
                } catch (Throwable error) {
                    if (disconnectOnReaderError && error instanceof ReaderException readerError
                            && readerError.getErrorCode() != -40 && readerError.getErrorCode() != -50
                            && readerError.getErrorCode() != -41) {
                        handleConnectionLost("Reader operation failed", readerError.getErrorCode(),
                                DisconnectReason.SDK_ERROR);
                    }
                    future.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    private void publishFailure(TransportType transport, String message, int errorCode,
            DisconnectReason reason) {
        Log.e(TAG, "connection failed transport=" + transport + " code=" + errorCode
                + " address=" + currentState().getAddress() + " message=" + message);
        publish(currentState().buildUpon().transport(transport).phase(ConnectionPhase.FAILED)
                .message(message).errorCode(errorCode).disconnectReason(reason)
                .inventoryRunning(false).build());
        stopConnectionService();
    }

    private int monitorSdkStatus(int status) {
        if (status != 0 && currentState().isConnected()) {
            handleConnectionLost("Reader SDK returned an error", status, DisconnectReason.SDK_ERROR);
        }
        return status;
    }

    private void scheduleWifiHeartbeat(long generation) {
        if (!isCurrentConnection(generation)) { return; }
        mainHandler.removeCallbacks(wifiHeartbeat);
        mainHandler.postDelayed(wifiHeartbeat, 8_000);
    }

    private void runWifiHeartbeat() {
        if (!currentState().isConnected() || currentState().getTransport() != TransportType.WIFI) { return; }
        long generation = connectionManager.getGeneration();
        sdkExecutor.execute(() -> {
            if (!isCurrentConnection(generation) || currentState().isInventoryRunning()) {
                scheduleWifiHeartbeat(generation);
                return;
            }
            try {
                gateway.readModuleInfo();
                scheduleWifiHeartbeat(generation);
            } catch (ReaderException error) {
                handleConnectionLost("Wi-Fi reader heartbeat failed", error.getErrorCode(),
                        DisconnectReason.SDK_ERROR);
            }
        });
    }

    private void publish(ReaderState updated) {
        connectionManager.publish(updated);
    }

    private void updateConnectionService(ReaderState updated) {
        ReaderConnectionService service = connectionService;
        if (service != null) { service.updateReaderState(updated); }
    }

}
