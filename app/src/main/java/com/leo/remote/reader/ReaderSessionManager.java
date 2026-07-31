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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@SuppressLint("LogNotTimber")
public final class ReaderSessionManager {
    private static final String TAG = "UhfReader";
    public static final int WIFI_PORT = 1200;
    private static final String MMKV_ID = "reader_connection";
    private static final String KEY_WIFI_ADDRESS = "wifi_address";
    private static volatile ReaderSessionManager instance;

    private final UhfSdkGateway gateway;
    private final Application application;
    private volatile ExecutorService sdkExecutor;
    private final Handler mainHandler;
    private final CopyOnWriteArraySet<ReaderObserver> observers = new CopyOnWriteArraySet<>();
    private final InventoryAccumulator inventory = new InventoryAccumulator();
    private final AtomicBoolean inventoryUpdateScheduled = new AtomicBoolean();
    private final AtomicLong connectionGeneration = new AtomicLong();
    private final CopyOnWriteArraySet<CompletableFuture<?>> pendingOperations = new CopyOnWriteArraySet<>();
    private final BleTransport bleTransport;
    private final WifiNetworkMonitor wifiMonitor;
    private final MMKV storage;

    private volatile ReaderState state = ReaderState.disconnected();
    private volatile ReaderConfiguration configuration;
    private volatile ReaderTag currentTag;
    private volatile InventoryMaskConfig inventoryMask;
    private volatile TagProtocol inventoryMaskProtocol;
    private volatile int inventoryMode = 1;
    private volatile boolean pendingDisconnectAlert;
    private volatile DisconnectReason lastUnexpectedReason = DisconnectReason.NONE;
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
        ReaderSessionManager manager = getInstance(application);
        manager.initializeNativeAtApplication();
    }

    public static ReaderSessionManager getInstance(@NonNull Application application) {
        if (instance == null) {
            synchronized (ReaderSessionManager.class) {
                if (instance == null) {
                    instance = new ReaderSessionManager(application, new NativeUhfSdkGateway());
                }
            }
        }
        return instance;
    }

    static ReaderSessionManager createForTest(Application application, UhfSdkGateway gateway) {
        return new ReaderSessionManager(application, gateway);
    }

    private ReaderSessionManager(Application application, UhfSdkGateway gateway) {
        this.application = application;
        this.gateway = gateway;
        sdkExecutor = createSdkExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        storage = MMKV.mmkvWithID(MMKV_ID);
        bleTransport = new BleTransport(new BleTransport.Listener() {
            @Override
            public void onPhase(long attemptId, ConnectionPhase phase, String message) {
                if (isCurrentConnection(attemptId)) {
                    publish(state.buildUpon().phase(phase).message(message).errorCode(0).build());
                }
            }

            @Override
            public void onReady(long attemptId) {
                if (!isCurrentConnection(attemptId)) { return; }
                Log.i(TAG, "BLE data channel ready, starting handshake");
                publish(state.buildUpon().phase(ConnectionPhase.CONNECTING_DATA_CHANNEL)
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
                if (isCurrentConnection(attemptId) && state.getTransport() == TransportType.BLE) {
                    handleConnectionLost(message, errorCode, reason);
                }
            }
        });
        wifiMonitor = new WifiNetworkMonitor(application,
                () -> {
                    if (state.getTransport() == TransportType.WIFI && state.isConnected()) {
                        handleConnectionLost("Wi-Fi network lost", -20, DisconnectReason.WIFI_LOST);
                    }
                });
        gateway.setInventoryListener(tag -> {
            if (!state.isConnected() || !state.isInventoryRunning()) { return; }
            inventory.add(tag.id, tag.data, tag.rssi, tag.count, resolveChipModel(tag.data));
            scheduleInventoryUpdate();
        });
        wifiMonitor.start();
    }

    public ReaderState getState() { return state; }
    public ReaderConfiguration getConfiguration() { return configuration; }
    public ReaderTag getCurrentTag() { return currentTag; }
    public int getInventoryMode() { return inventoryMode; }
    public boolean isPendingDisconnectAlert() { return pendingDisconnectAlert; }
    public DisconnectReason getLastUnexpectedReason() { return lastUnexpectedReason; }
    public void acknowledgeDisconnect() { pendingDisconnectAlert = false; }

    public void addObserver(@NonNull ReaderObserver observer) {
        observers.add(observer);
        mainHandler.post(() -> {
            observer.onReaderStateChanged(state);
            if (configuration != null) { observer.onReaderConfigurationChanged(configuration); }
            if (currentTag != null) { observer.onCurrentTagChanged(currentTag); }
            observer.onInventoryMaskChanged(inventoryMask);
            observer.onInventoryChanged(inventory.snapshot(), inventory.getTotalReads());
        });
    }

    public void removeObserver(@NonNull ReaderObserver observer) { observers.remove(observer); }

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
        ReaderState previousState = state;
        long generation = connectionGeneration.incrementAndGet();
        if (!isValidIpv4(normalized)) {
            configuration = null;
            clearCurrentTag();
            publish(new ReaderState.Builder().transport(TransportType.WIFI).phase(ConnectionPhase.FAILED)
                    .device("Wi-Fi reader", normalized).message("Invalid reader IP address")
                    .errorCode(-21).disconnectReason(DisconnectReason.SDK_ERROR).build());
            sdkExecutor.execute(() -> disconnectTransportInternal(previousState.getTransport(),
                    previousState.isInventoryRunning()));
            return;
        }
        startConnectionService();
        configuration = null;
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
        ReaderState previousState = state;
        long generation = connectionGeneration.incrementAndGet();
        configuration = null;
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
        ConnectionPhase phase = state.getPhase();
        if (phase == ConnectionPhase.DISCONNECTED || phase == ConnectionPhase.FAILED) {
            connectionGeneration.incrementAndGet();
            configuration = null;
            clearCurrentTag();
            if (state.getTransport() != TransportType.NONE || state.getDisconnectReason() != DisconnectReason.NONE) {
                publish(new ReaderState.Builder().disconnectReason(reason).build());
            }
            stopConnectionService();
            return;
        }
        long generation = connectionGeneration.incrementAndGet();
        publish(state.buildUpon().phase(ConnectionPhase.DISCONNECTING)
                .inventoryRunning(false).message("Disconnecting").build());
        sdkExecutor.execute(() -> {
            disconnectTransportInternal();
            configuration = null;
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
        connectionGeneration.incrementAndGet();
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
            if (!state.getModuleSubtype().supportedProtocols().contains(protocol)) {
                throw new ReaderException("Protocol is not supported by this module", -30);
            }
            int status = stopInventoryInternal();
            if (status != 0) { return status; }
            if (inventoryMask != null) {
                status = monitorSdkStatus(gateway.clearInventoryMask(state.getProtocol()));
                Log.i(TAG, "clear inventory mask before protocol switch status=" + status);
                if (status != 0) { return status; }
                inventoryMask = null;
                inventoryMaskProtocol = null;
                notifyMask(null);
            }
            status = monitorSdkStatus(gateway.setProtocol(protocol));
            if (status == 0) { status = gateway.configureDefaultInventory(protocol); }
            status = monitorSdkStatus(status);
            if (status == 0) {
                clearCurrentTag();
                publish(state.buildUpon().protocol(protocol).inventoryRunning(false).build());
            }
            return status;
        });
    }

    public void setInventoryMode(int mode) {
        if (mode < 0 || mode > 2) { return; }
        inventoryMode = state.getModuleSubtype() == ModuleSubtype.MAGIC_RF ? 1 : mode;
        notifyConfiguration();
    }

    public CompletableFuture<Integer> setPower(int powerTenthsDbm) {
        return submitConnected(() -> updateConfiguration(monitorSdkStatus(
                gateway.setPowerTenthsDbm(powerTenthsDbm)),
                new ReaderConfiguration(powerTenthsDbm, inventoryMode, configuration.blfProfile,
                        configuration.session, configuration.target, configuration.dynamicQ, configuration.qValue)));
    }

    public CompletableFuture<Integer> setBlf(int profile) {
        return submitConnected(() -> updateConfiguration(monitorSdkStatus(gateway.setBlfProfile(profile)),
                new ReaderConfiguration(configuration.powerTenthsDbm, inventoryMode, profile,
                        configuration.session, configuration.target, configuration.dynamicQ, configuration.qValue)));
    }

    public CompletableFuture<Integer> setSessionTarget(int session, int target) {
        return submitConnected(() -> {
            int status = state.getModuleSubtype() == ModuleSubtype.MAGIC_RF
                    ? gateway.setMagicQuery(session, target, configuration.qValue)
                    : gateway.setQueryGroup(session, target);
            return updateConfiguration(monitorSdkStatus(status), new ReaderConfiguration(configuration.powerTenthsDbm,
                    inventoryMode, configuration.blfProfile, session, target,
                    configuration.dynamicQ, configuration.qValue));
        });
    }

    public CompletableFuture<Integer> setQ(boolean dynamic, int qValue) {
        return submitConnected(() -> {
            int status = state.getModuleSubtype() == ModuleSubtype.MAGIC_RF
                    ? gateway.setMagicQuery(configuration.session, configuration.target, qValue)
                    : gateway.setQ(dynamic, qValue);
            return updateConfiguration(monitorSdkStatus(status), new ReaderConfiguration(configuration.powerTenthsDbm,
                    inventoryMode, configuration.blfProfile, configuration.session,
                    configuration.target, state.getModuleSubtype() != ModuleSubtype.MAGIC_RF && dynamic, qValue));
        });
    }

    public CompletableFuture<Integer> startInventory() {
        return submitConnected(() -> {
            int status = gateway.configureDefaultInventory(state.getProtocol());
            InventoryMaskConfig activeMask = inventoryMask;
            if (status == 0 && activeMask != null) {
                if (inventoryMaskProtocol != state.getProtocol()) {
                    inventoryMask = null;
                    inventoryMaskProtocol = null;
                    notifyMask(null);
                    Log.i(TAG, "discard inventory mask after protocol mismatch");
                } else {
                    status = gateway.applyInventoryMask(state.getProtocol(), activeMask);
                    Log.i(TAG, "inventory mask re-applied on start status=" + status);
                }
            }
            if (status == 0) { status = gateway.startInventory(inventoryMode, 0); }
            status = monitorSdkStatus(status);
            if (status == 0) {
                publish(state.buildUpon().inventoryRunning(true).build());
            }
            return status;
        });
    }

    public CompletableFuture<Integer> stopInventory() {
        return submitConnected(this::stopInventoryInternal);
    }

    public void clearInventory() {
        inventory.clear();
        notifyInventory();
    }

    public List<InventoryItem> getInventorySnapshot() { return inventory.snapshot(); }

    /** Applies a manual inventory mask without changing power, BLF, or Q settings. */
    public CompletableFuture<Integer> applyInventoryMask(@NonNull InventoryMaskConfig config) {
        return submitConnected(() -> {
            TagProtocol protocol = state.getProtocol();
            int status = monitorSdkStatus(gateway.applyInventoryMask(protocol, config));
            Log.i(TAG, "applyInventoryMask status=" + status + " bank=" + config.bank
                    + " offsetBits=" + config.offsetBits + " lengthBits=" + config.lengthBits);
            if (status == 0) {
                inventoryMask = config;
                inventoryMaskProtocol = protocol;
                notifyMask(config);
            }
            return status;
        });
    }

    /** Clears only the active inventory Select criteria. */
    public CompletableFuture<Integer> clearInventoryMask() {
        if (inventoryMask == null) {
            return CompletableFuture.completedFuture(0);
        }
        if (!state.isConnected()) {
            inventoryMask = null;
            inventoryMaskProtocol = null;
            notifyMask(null);
            return CompletableFuture.completedFuture(0);
        }
        return submitConnected(() -> {
            TagProtocol protocol = inventoryMaskProtocol == null
                    ? state.getProtocol() : inventoryMaskProtocol;
            int status = monitorSdkStatus(gateway.clearInventoryMask(protocol));
            Log.i(TAG, "clearInventoryMask status=" + status);
            if (status == 0) {
                inventoryMask = null;
                inventoryMaskProtocol = null;
                notifyMask(null);
            }
            return status;
        });
    }

    public boolean hasInventoryMask() {
        return inventoryMask != null;
    }

    public CompletableFuture<ReaderTag> readSingleTag() {
        return submitConnected(() -> {
            int status = stopInventoryInternal();
            if (status != 0) { throw new ReaderException("Unable to stop inventory", status); }
            ReaderTag tag = gateway.inventoryOnce(1500);
            currentTag = tag;
            mainHandler.post(() -> observers.forEach(observer -> observer.onCurrentTagChanged(tag)));
            return tag;
        });
    }

    public CompletableFuture<byte[]> readCurrentTag(int length, int address, int bank, byte[] password) {
        TagProtocol protocol = state.getProtocol();
        return withTargetMask(() -> gateway.readTag(protocol, length, address, bank, password, 2000));
    }

    public CompletableFuture<Integer> writeCurrentTag(int length, int address, int bank,
            byte[] password, byte[] data) {
        TagProtocol protocol = state.getProtocol();
        return withTargetMask(() -> monitorSdkStatus(
                gateway.writeTag(protocol, length, address, bank, password, data, 2500)));
    }

    public CompletableFuture<Integer> lockCurrentTag(byte[] password, int bank, int policy) {
        return with6cTargetMask(() -> monitorSdkStatus(gateway.lockTag(password, bank, policy, 2500)));
    }

    public CompletableFuture<Integer> killCurrentTag(byte[] accessPassword, byte[] killPassword) {
        return with6cTargetMask(() -> monitorSdkStatus(
                gateway.killTag(accessPassword, killPassword, 2500)));
    }

    private <T> CompletableFuture<T> with6cTargetMask(Callable<T> operation) {
        if (state.getProtocol() != TagProtocol.ISO_18000_6C) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new ReaderException("Operation is only supported for ISO 18000-6C", -41));
            return future;
        }
        return withTargetMask(operation);
    }

    private <T> CompletableFuture<T> withTargetMask(Callable<T> operation) {
        return submitConnected(() -> {
            if (currentTag == null) {
                throw new ReaderException("Read a target tag first", -40);
            }
            int status = stopInventoryInternal();
            if (status != 0) { throw new ReaderException("Unable to stop inventory", status); }
            status = monitorSdkStatus(gateway.setTargetMask(state.getProtocol(), currentTag));
            if (status != 0) { throw new ReaderException("Unable to set target mask", status); }
            try {
                return operation.call();
            } finally {
                int clearStatus = monitorSdkStatus(gateway.clearTargetMask(state.getProtocol()));
                if (clearStatus != 0) {
                    throw new ReaderException("Unable to restore target mask", clearStatus);
                }
            }
        });
    }

    private void performHandshake(long generation) {
        if (!isCurrentConnection(generation)) { return; }
        Log.i(TAG, "RM70XX handshake started generation=" + generation
                + " transport=" + state.getTransport() + " address=" + state.getAddress());
        publish(state.buildUpon().phase(ConnectionPhase.VERIFYING_MODULE)
                .message("Verifying RM70XX module").build());
        try {
            ReaderHandshake.Result result = ReaderHandshake.perform(gateway);
            if (!isCurrentConnection(generation)) { return; }
            ReaderModuleInfo info = result.moduleInfo;
            inventoryMode = 1;
            configuration = result.configuration;
            if (inventoryMask != null && inventoryMaskProtocol != TagProtocol.ISO_18000_6C) {
                inventoryMask = null;
                inventoryMaskProtocol = null;
                notifyMask(null);
                Log.i(TAG, "discard inventory mask because handshake restored 6C protocol");
            }
            Log.i(TAG, "RM70XX handshake succeeded subtype=" + info.subtype
                    + " rawSubtype=" + info.rawSubtype + " boardSerial=" + info.boardSerial
                    + " boardVersion=" + info.boardVersion + " moduleSerial=" + info.moduleSerial
                    + " moduleVersion=" + info.moduleVersion);
            publish(state.buildUpon().phase(ConnectionPhase.CONNECTED)
                    .moduleSubtype(info.subtype, info.rawSubtype)
                    .protocol(TagProtocol.ISO_18000_6C)
                    .versions(info.boardSerial, info.boardVersion, info.moduleSerial, info.moduleVersion)
                    .message("").errorCode(0).inventoryRunning(false).build());
            pendingDisconnectAlert = false;
            lastUnexpectedReason = DisconnectReason.NONE;
            notifyConfiguration();
            if (state.getTransport() == TransportType.WIFI) { scheduleWifiHeartbeat(generation); }
        } catch (ReaderException error) {
            Log.e(TAG, "RM70XX handshake failed code=" + error.getErrorCode()
                    + " message=" + error.getMessage(), error);
            disconnectTransportInternal();
            if (isCurrentConnection(generation)) {
                publishFailure(state.getTransport(), error.getMessage(), error.getErrorCode(),
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
        service.updateReaderState(state);
        Log.i(TAG, "reader connection foreground service created");
    }

    void onConnectionServiceDestroyed(@NonNull ReaderConnectionService service) {
        synchronized (serviceLock) {
            if (connectionService == service) { connectionService = null; }
        }
        Log.i(TAG, "reader connection foreground service destroyed");
    }

    private int stopInventoryInternal() {
        if (!state.isInventoryRunning()) { return 0; }
        int status = monitorSdkStatus(gateway.stopInventory());
        if (status == 0) { publish(state.buildUpon().inventoryRunning(false).build()); }
        return status;
    }

    private void disconnectTransportInternal() {
        disconnectTransportInternal(state.getTransport(), state.isInventoryRunning());
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
        ReaderState lostState = state;
        TransportType transport = lostState.getTransport();
        if (reason.isUnexpected() && lostState.getPhase() != ConnectionPhase.DISCONNECTED
                && lostState.getPhase() != ConnectionPhase.FAILED) {
            handleUnexpectedDisconnect(message, errorCode, reason);
            sdkExecutor.execute(() -> disconnectTransportInternal(transport, false));
            return;
        }
        long generation = connectionGeneration.incrementAndGet();
        sdkExecutor.execute(() -> {
            disconnectTransportInternal(transport, lostState.isInventoryRunning());
            configuration = null;
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
        connectionGeneration.incrementAndGet();
        mainHandler.removeCallbacks(wifiHeartbeat);
        ReaderException failure = new ReaderException(message, errorCode);
        for (CompletableFuture<?> pending : pendingOperations) {
            pending.completeExceptionally(failure);
        }
        inventoryMask = null;
        inventoryMaskProtocol = null;
        notifyMask(null);
        configuration = null;
        clearCurrentTag();
        pendingDisconnectAlert = true;
        lastUnexpectedReason = reason;
        publish(state.buildUpon().phase(ConnectionPhase.DISCONNECTED).inventoryRunning(false)
                .message(message).errorCode(errorCode).disconnectReason(reason).build());
        mainHandler.post(() -> observers.forEach(observer ->
                observer.onReaderUnexpectedDisconnect(reason)));
    }

    private boolean isCurrentConnection(long generation) {
        return connectionGeneration.get() == generation;
    }

    private void clearCurrentTag() {
        if (currentTag == null) { return; }
        currentTag = null;
        mainHandler.post(() -> observers.forEach(observer -> observer.onCurrentTagChanged(null)));
    }

    private int updateConfiguration(int status, ReaderConfiguration updated) {
        if (status == 0) {
            configuration = updated;
            notifyConfiguration();
        }
        return status;
    }

    private <T> CompletableFuture<T> submitConnected(Callable<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        pendingOperations.add(future);
        future.whenComplete((value, error) -> pendingOperations.remove(future));
        try {
            sdkExecutor.execute(() -> {
                try {
                    if (!state.isConnected()) {
                        throw new ReaderException("Reader is not connected", -50);
                    }
                    future.complete(operation.call());
                } catch (Throwable error) {
                    if (error instanceof ReaderException readerError
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
                + " address=" + state.getAddress() + " message=" + message);
        publish(state.buildUpon().transport(transport).phase(ConnectionPhase.FAILED)
                .message(message).errorCode(errorCode).disconnectReason(reason)
                .inventoryRunning(false).build());
    }

    private int monitorSdkStatus(int status) {
        if (status != 0 && state.isConnected()) {
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
        if (!state.isConnected() || state.getTransport() != TransportType.WIFI) { return; }
        long generation = connectionGeneration.get();
        sdkExecutor.execute(() -> {
            if (!isCurrentConnection(generation) || state.isInventoryRunning()) {
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
        state = updated;
        ReaderConnectionService service = connectionService;
        if (service != null) { service.updateReaderState(updated); }
        mainHandler.post(() -> observers.forEach(observer -> observer.onReaderStateChanged(updated)));
    }

    private void notifyConfiguration() {
        ReaderConfiguration current = configuration;
        if (current == null) { return; }
        ReaderConfiguration updated = new ReaderConfiguration(current.powerTenthsDbm, inventoryMode,
                current.blfProfile, current.session, current.target, current.dynamicQ, current.qValue);
        configuration = updated;
        mainHandler.post(() -> observers.forEach(observer -> observer.onReaderConfigurationChanged(updated)));
    }

    private void scheduleInventoryUpdate() {
        if (inventoryUpdateScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                inventoryUpdateScheduled.set(false);
                notifyInventory();
            }, 100);
        }
    }

    private void notifyInventory() {
        List<InventoryItem> snapshot = inventory.snapshot();
        long total = inventory.getTotalReads();
        mainHandler.post(() -> observers.forEach(observer -> observer.onInventoryChanged(snapshot, total)));
    }

    private void notifyMask(@Nullable InventoryMaskConfig config) {
        mainHandler.post(() -> observers.forEach(observer ->
                observer.onInventoryMaskChanged(config)));
    }

    private static String resolveChipModel(String data) {
        if (data == null || data.length() < 6) { return ""; }
        if (data.startsWith("E28011") || data.startsWith("E28012")) { return "Impinj Monza"; }
        return "";
    }
}
