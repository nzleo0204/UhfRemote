package com.leo.rfid.sdk.connect;

import com.leo.rfid.sdk.inventory.ReaderInventoryController;
import com.leo.rfid.sdk.config.ReaderConfigurationManager;
import com.leo.rfid.sdk.model.*;
import com.leo.rfid.sdk.storage.ReaderConfigurationStore;
import com.leo.rfid.sdk.bridge.*;
import com.leo.rfid.sdk.tag.ReaderTagOperations;
import com.leo.rfid.sdk.connect.serial.SerialConfig;
import com.leo.rfid.sdk.connect.serial.SerialPowerController;

import android.annotation.SuppressLint;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

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
    public static final int WIFI_PORT = ReaderConnectionOrchestrator.WIFI_PORT;

    private final ReaderConfigurationGateway configurationGateway;
    private final InventoryBridge inventoryGateway;
    private final ReaderCommandExecutor commandExecutor;
    private final MainThreadDispatcher mainThread;
    private final ReaderStatePublisher statePublisher;
    private final ReaderConfigurationStore configStore;
    private final ReaderConfigurationManager configurationManager;
    private final ReaderTagOperations tagOperations;
    private final ReaderInventoryController inventoryController;
    private final ReaderConnectionOrchestrator connectionOrchestrator;

    ReaderSessionCoordinator(ReaderTransportGateway transportGateway,
            ReaderConfigurationGateway configurationGateway,
            InventoryBridge inventoryGateway, ReaderTagGateway tagGateway,
            SessionDeps dependencies,
            ReaderService connectionServiceHost) {
        this.configurationGateway = configurationGateway;
        this.inventoryGateway = inventoryGateway;
        mainThread = dependencies.mainThread;
        statePublisher = new ReaderStatePublisher();
        ReaderConnectionManager connectionManager = new ReaderConnectionManager(statePublisher,
                connectionServiceHost::update);
        commandExecutor = new ReaderCommandExecutor(dependencies.sdkExecutorFactory.get(),
                this::currentState, error -> handleConnectionLost("Reader operation failed",
                        error.getErrorCode(), DisconnectReason.SDK_ERROR));
        configStore = dependencies.configurationStore;
        configurationManager = new ReaderConfigurationManager(configurationGateway, configStore,
                statePublisher);
        tagOperations = new ReaderTagOperations(tagGateway, statePublisher);
        inventoryController = new ReaderInventoryController(inventoryGateway,
                configurationGateway, configStore, statePublisher,
                mainThread::postDelayed);
        connectionOrchestrator = new ReaderConnectionOrchestrator(transportGateway,
                configurationGateway, inventoryGateway, configStore,
                dependencies.connectionStore, configurationManager, tagOperations,
                inventoryController, connectionManager, commandExecutor, dependencies,
                connectionServiceHost::start, connectionServiceHost::stop);
        inventoryGateway.setInventoryListener(tag -> {
            if (!currentState().isConnected() || !currentState().isInventoryRunning()) { return; }
            inventoryController.onTag(tag);
        });
        inventoryGateway.setInventoryStopListener(this::handleInventoryStopped);
    }

    public ReaderState getState() { return currentState(); }
    public ReaderConfiguration getConfiguration() { return configurationManager.getConfiguration(); }
    public boolean isPendingDisconnectAlert() {
        return connectionOrchestrator.isPendingDisconnectAlert();
    }
    public DisconnectReason getLastUnexpectedReason() {
        return connectionOrchestrator.getLastUnexpectedReason();
    }
    public void acknowledgeDisconnect() { connectionOrchestrator.acknowledgeDisconnect(); }
    public boolean isConnectionFailureAcknowledged(@NonNull ReaderState failure) {
        return connectionOrchestrator.isConnectionFailureAcknowledged(failure);
    }
    public void acknowledgeConnectionFailure(@NonNull ReaderState failure) {
        connectionOrchestrator.acknowledgeConnectionFailure(failure);
    }

    private ReaderState currentState() {
        return connectionOrchestrator.currentState();
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

    public static boolean isValidIpv4(@Nullable String value) {
        return ReaderConnectionOrchestrator.isValidIpv4(value);
    }

    public void connectWifi(@NonNull String address) {
        connectionOrchestrator.connectWifi(address);
    }

    public void connectBle(@NonNull cn.wandersnail.ble.Device device) {
        connectionOrchestrator.connectBle(device);
    }

    /** 连接串口读写器，端口打开后复用现有 Reader 握手流程。 */
    public void connectSerial(@NonNull SerialConfig config) {
        connectionOrchestrator.connectSerial(config);
    }

    /** 使用客户提供的上电控制器连接串口读写器。 */
    public void connectSerial(@NonNull SerialConfig config,
            @NonNull SerialPowerController powerController) {
        connectionOrchestrator.connectSerial(config, powerController);
    }

    public void disconnect() {
        disconnect(DisconnectReason.USER);
    }

    public void disconnect(@NonNull DisconnectReason reason) {
        connectionOrchestrator.disconnect(reason);
    }

    public CompletableFuture<Integer> setProtocol(@NonNull TagProtocol protocol) {
        return submitConnected(() -> {
            if (!currentState().getModuleSubtype().supportedProtocols().contains(protocol)) {
                throw new ReaderException("Protocol is not supported by this module", -30);
            }
            int status = stopInventoryInternal();
            if (status != 0) { return status; }
            if (inventoryController.getMask() != null) {
                status = monitorSdkStatus(inventoryGateway.clearInventoryMask(currentState().getProtocol(),
                        currentState().getModuleSubtype(), inventoryMaskRestoreValue()));
                Log.i(TAG, "clear inventory mask before protocol switch status=" + status);
                if (status != 0) { return status; }
                inventoryController.discardMask();
            }
            status = monitorSdkStatus(configurationGateway.setProtocol(protocol));
            ReaderConfiguration current = configurationManager.getConfiguration();
            InventoryArea mappedArea = InventoryArea.of(protocol,
                    current == null ? 0 : current.inventoryArea);
            int mappedAddress = current == null || mappedArea.isBaseOnly()
                    ? 0 : current.inventoryAddress;
            int mappedLength = current == null || mappedArea.isBaseOnly()
                    ? 0 : current.inventoryWordLen;
            if (status == 0) {
                status = inventoryGateway.applyInventoryParams(protocol, mappedArea.getValue(),
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
            ModuleSubtype subtype = currentState().getModuleSubtype();
            configurationManager.restore(ReaderHandshake.readConfigurationStepwise(
                    configurationGateway, subtype, configStore, ignored -> {}));
            configurationManager.publishCurrent();
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
            int selected = configStore.loadSelected(subtype);
            int status = monitorSdkStatus(configurationManager.applySession(
                    subtype, session, selected));
            Log.i(TAG, "setSession S" + session + " target=" + target
                    + " selected=" + selected + " status=" + status);
            if (status == 0 && inventoryController.isMaskApplied()) {
                inventoryGateway.applyInventoryMask(currentState().getProtocol(), subtype,
                        inventoryController.getMask());
            }
            if (status == 0) {
                configurationManager.commitSession(subtype, session);
            }
            return status;
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
            int status = inventoryGateway.applyInventoryMask(protocol, subtype, config);
            if (status != 0 && capturedNow) {
                inventoryGateway.clearInventoryMask(protocol, subtype, inventoryMaskRestoreValue());
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
            int status = monitorSdkStatus(inventoryGateway.clearInventoryMask(protocol,
                    currentState().getModuleSubtype(), inventoryMaskRestoreValue()));
            Log.i(TAG, "clearInventoryMask status=" + status);
            if (status == 0) {
                configStore.saveSelected(currentState().getModuleSubtype(), inventoryMaskRestoreValue());
                inventoryController.discardMask();
            }
            if (wasRunning) {
                int restartStatus = startInventoryInternal();
                if (status == 0 && restartStatus != 0) { status = restartStatus; }
            }
            return status;
        });
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
            int[] queryValues = configurationGateway.getQueryValues(subtype);
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
                    status = monitorSdkStatus(inventoryGateway.clearInventoryMask(currentProtocol,
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
                    status = monitorSdkStatus(inventoryGateway.applyInventoryMask(currentProtocol,
                            subtype, singleTagMask));
                    if (status != 0) {
                        throw new ReaderException("Unable to apply single tag mask", status);
                    }
                }

                return tagOperations.read(protocol, length, address, bank, password);
            } finally {
                if (needRestoreInventoryMask && inventoryMaskToRestore != null) {
                    int restoreStatus = inventoryGateway.applyInventoryMask(currentProtocol, subtype,
                            inventoryMaskToRestore);
                    inventoryController.setMaskApplied(restoreStatus == 0);
                    if (restoreStatus != 0) {
                        throw new ReaderException("Unable to restore inventory mask", restoreStatus);
                    }
                } else if (singleTagMask != null && selectedBeforeSingleMaskCaptured) {
                    int clearStatus = inventoryGateway.clearTargetMask(currentProtocol, subtype,
                            selectedBeforeSingleMask);
                    if (clearStatus != 0) {
                        throw new ReaderException("Unable to clear single-tag mask", clearStatus);
                    }
                }
            }
        }, false);
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
                status = monitorSdkStatus(inventoryGateway.clearInventoryMask(protocol,
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
                    status = monitorSdkStatus(inventoryGateway.applyInventoryMask(protocol,
                            currentState().getModuleSubtype(), activeMask));
                    if (status != 0) {
                        throw new ReaderException("Unable to set single-tag mask", status);
                    }
                }
                return operation.call();
            } finally {
                if (maskToRestore != null) {
                    int restoreStatus = monitorSdkStatus(inventoryGateway.applyInventoryMask(protocol,
                            currentState().getModuleSubtype(), maskToRestore));
                    inventoryController.setMaskApplied(restoreStatus == 0);
                    if (restoreStatus != 0) {
                        throw new ReaderException("Unable to restore inventory mask", restoreStatus);
                    }
                } else if (activeMask != null) {
                    int clearStatus = monitorSdkStatus(inventoryGateway.clearTargetMask(protocol,
                            currentState().getModuleSubtype(), selectedBeforeTargetMask));
                    if (clearStatus != 0) {
                        throw new ReaderException("Unable to clear single-tag mask", clearStatus);
                    }
                }
            }
        });
    }

    void initializeNativeAtApplication() {
        connectionOrchestrator.initializeNativeAtApplication();
    }

    private int stopInventoryInternal() {
        if (!currentState().isInventoryRunning()) { return 0; }
        int status = monitorSdkStatus(inventoryController.stop(currentState().isInventoryRunning()));
        if (status == 0) { publish(currentState().buildUpon().inventoryRunning(false).build()); }
        return status;
    }

    /** 模块自行结束盘点时同步会话状态，避免单次模式按钮停在“停止”。 */
    private void handleInventoryStopped(int status) {
        mainThread.post(() -> {
            if (currentState().isInventoryRunning()) {
                Log.i(TAG, "inventory stopped by reader status=" + status);
                publish(currentState().buildUpon().inventoryRunning(false).build());
            }
        });
    }

    private void handleConnectionLost(String message, int errorCode, DisconnectReason reason) {
        connectionOrchestrator.handleConnectionLost(message, errorCode, reason);
    }

    private void clearCurrentTag() {
        tagOperations.clearCurrentTag();
    }

    private <T> CompletableFuture<T> submitConnected(Callable<T> operation) {
        return submitConnected(operation, true);
    }

    private <T> CompletableFuture<T> submitConnected(Callable<T> operation,
            boolean disconnectOnReaderError) {
        return commandExecutor.submitConnected(operation, disconnectOnReaderError);
    }

    private int monitorSdkStatus(int status) {
        if (status != 0 && currentState().isConnected()) {
            handleConnectionLost("Reader SDK returned an error", status, DisconnectReason.SDK_ERROR);
        }
        return status;
    }

    private void publish(ReaderState updated) {
        connectionOrchestrator.publish(updated);
    }

}
