package com.leo.remote.rfid.sdk.connection;

import com.leo.remote.rfid.sdk.connection.service.ReaderConnectionService;
import com.leo.remote.rfid.sdk.connection.service.ReaderConnectionServiceController;
import com.leo.remote.rfid.sdk.connection.service.ReaderServiceNotificationConfig;
import com.leo.remote.rfid.sdk.model.*;
import com.leo.remote.rfid.sdk.nativebridge.NativeUhfSdkGateway;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cn.wandersnail.ble.Device;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Public facade for the process-wide RFID reader session. */
public final class ReaderSessionManager {
    public static final int WIFI_PORT = ReaderSessionCoordinator.WIFI_PORT;
    private static volatile ReaderSessionManager instance;

    private final ReaderSessionCoordinator coordinator;
    private final ReaderConnectionServiceController serviceController;
    private final ReaderServiceNotificationConfig notificationConfig;

    private ReaderSessionManager(ReaderSessionCoordinator coordinator,
            ReaderConnectionServiceController serviceController,
            ReaderServiceNotificationConfig notificationConfig) {
        this.coordinator = coordinator;
        this.serviceController = serviceController;
        this.notificationConfig = notificationConfig;
    }

    public static void initialize(@NonNull Application application) {
        getInstance(application).coordinator.initializeNativeAtApplication();
    }

    public static void initialize(@NonNull Application application,
            @NonNull Function<ReaderProgress, String> messageResolver) {
        initialize(application, messageResolver, ReaderServiceNotificationConfig.defaultConfig(application));
    }

    public static void initialize(@NonNull Application application,
            @NonNull Function<ReaderProgress, String> messageResolver,
            @NonNull ReaderServiceNotificationConfig notificationConfig) {
        getInstance(application, messageResolver, notificationConfig)
                .coordinator.initializeNativeAtApplication();
    }

    public static ReaderSessionManager getInstance(@NonNull Application application) {
        return getInstance(application, ReaderProgress::getDefaultMessage,
                ReaderServiceNotificationConfig.defaultConfig(application));
    }

    private static ReaderSessionManager getInstance(@NonNull Application application,
            @NonNull Function<ReaderProgress, String> messageResolver,
            @NonNull ReaderServiceNotificationConfig notificationConfig) {
        if (instance == null) {
            synchronized (ReaderSessionManager.class) {
                if (instance == null) {
                    ReaderConnectionServiceController services =
                            new ReaderConnectionServiceController(application);
                    NativeUhfSdkGateway gateway = new NativeUhfSdkGateway();
                    instance = new ReaderSessionManager(new ReaderSessionCoordinator(
                            gateway, gateway, gateway, gateway,
                            ReaderSessionDependencies.production(application, messageResolver),
                            services), services, notificationConfig);
                }
            }
        }
        return instance;
    }

    @NonNull
    public ReaderServiceNotificationConfig getNotificationConfig() {
        return notificationConfig;
    }

    public ReaderState getState() { return coordinator.getState(); }
    public ReaderConfiguration getConfiguration() { return coordinator.getConfiguration(); }
    public boolean isPendingDisconnectAlert() { return coordinator.isPendingDisconnectAlert(); }
    public DisconnectReason getLastUnexpectedReason() {
        return coordinator.getLastUnexpectedReason();
    }
    public void acknowledgeDisconnect() { coordinator.acknowledgeDisconnect(); }
    public boolean isConnectionFailureAcknowledged(@NonNull ReaderState failure) {
        return coordinator.isConnectionFailureAcknowledged(failure);
    }
    public void acknowledgeConnectionFailure(@NonNull ReaderState failure) {
        coordinator.acknowledgeConnectionFailure(failure);
    }
    public void addObserver(@NonNull ReaderObserver observer) { coordinator.addObserver(observer); }
    public void removeObserver(@NonNull ReaderObserver observer) {
        coordinator.removeObserver(observer);
    }

    public static boolean isValidIpv4(@Nullable String value) {
        return ReaderSessionCoordinator.isValidIpv4(value);
    }

    public void connectWifi(@NonNull String address) { coordinator.connectWifi(address); }
    public void connectBle(@NonNull Device device) { coordinator.connectBle(device); }
    public void disconnect() { coordinator.disconnect(); }
    public void disconnect(@NonNull DisconnectReason reason) { coordinator.disconnect(reason); }

    public CompletableFuture<Integer> setProtocol(@NonNull TagProtocol protocol) {
        return coordinator.setProtocol(protocol);
    }
    public void setInventoryMode(int mode) { coordinator.setInventoryMode(mode); }
    public CompletableFuture<Integer> setInventoryArea(int area, int address, int wordLen) {
        return coordinator.setInventoryArea(area, address, wordLen);
    }
    public CompletableFuture<Integer> refreshConfiguration() {
        return coordinator.refreshConfiguration();
    }
    public CompletableFuture<Integer> setPower(int powerTenthsDbm) {
        return coordinator.setPower(powerTenthsDbm);
    }
    public CompletableFuture<Integer> setBlf(int profile) { return coordinator.setBlf(profile); }
    public CompletableFuture<Integer> setSession(int session) {
        return coordinator.setSession(session);
    }

    public CompletableFuture<Integer> startInventory() { return coordinator.startInventory(); }
    public CompletableFuture<Integer> stopInventory() { return coordinator.stopInventory(); }
    public void clearInventory() { coordinator.clearInventory(); }
    public List<InventoryItem> getInventorySnapshot() {
        return coordinator.getInventorySnapshot();
    }
    public CompletableFuture<Integer> applyInventoryMask(@NonNull InventoryMaskConfig config) {
        return coordinator.applyInventoryMask(config);
    }
    public CompletableFuture<Integer> clearInventoryMask() {
        return coordinator.clearInventoryMask();
    }
    @Nullable
    public InventoryMaskConfig getInventoryMask() { return coordinator.getInventoryMask(); }

    public void setSingleTagMask(@Nullable InventoryMaskConfig config) {
        coordinator.setSingleTagMask(config);
    }
    @Nullable
    public InventoryMaskConfig getSingleTagMask() { return coordinator.getSingleTagMask(); }

    /**
     * 读取当前目标标签的数据。
     *
     * <p>通过单标签掩码定位目标标签，然后读取指定存储区域的数据。
     *
     * @param protocol 射频协议
     * @param length 读取长度
     * @param address 起始地址
     * @param bank 存储区域
     * @param password 访问密码
     * @return 读取的数据
     */
    public CompletableFuture<TagReadResult> readCurrentTag(TagProtocol protocol, int length,
            int address, int bank, byte[] password) {
        return coordinator.readCurrentTag(protocol, length, address, bank, password);
    }

    public CompletableFuture<Integer> writeCurrentTag(int length, int address, int bank,
            byte[] password, byte[] data) {
        return coordinator.writeCurrentTag(length, address, bank, password, data);
    }
    public CompletableFuture<Integer> lockCurrentTag(byte[] password, int bank, int policy) {
        return coordinator.lockCurrentTag(password, bank, policy);
    }
    public CompletableFuture<Integer> killCurrentTag(byte[] accessPassword, byte[] killPassword) {
        return coordinator.killCurrentTag(accessPassword, killPassword);
    }

    public void onConnectionServiceCreated(@NonNull ReaderConnectionService service) {
        serviceController.onServiceCreated(service);
    }
    public void onConnectionServiceDestroyed(@NonNull ReaderConnectionService service) {
        serviceController.onServiceDestroyed(service);
    }
}
