package com.leo.rfid.sdk.connect;

import com.leo.rfid.sdk.connect.service.ReaderConnectionService;
import com.leo.rfid.sdk.connect.service.ReaderConnectionServiceController;
import com.leo.rfid.sdk.connect.service.ReaderServiceNotificationConfig;
import com.leo.rfid.sdk.model.*;
import com.leo.rfid.sdk.bridge.UhfNativeBridge;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cn.wandersnail.ble.Device;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 提供进程级 RFID 读写器会话的公共调用入口。
 */
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

    /**
     * 使用默认进度文案初始化进程级读写器会话。
     *
     * @param application 应用对象
     */
    public static void initialize(@NonNull Application application) {
        getInstance(application).coordinator.initializeNativeAtApplication();
    }

    /**
     * 初始化进程级读写器会话并指定进度文案解析器。
     *
     * @param application 应用对象
     * @param messageResolver 连接进度文案解析器
     */
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
                    UhfNativeBridge gateway = new UhfNativeBridge();
                    instance = new ReaderSessionManager(new ReaderSessionCoordinator(
                            gateway, gateway, gateway, gateway,
                            SessionDeps.production(application, messageResolver),
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

    /** 返回当前读写器会话状态快照。 */
    public ReaderState getState() { return coordinator.getState(); }

    /** 返回最近一次读取或更新后的设备参数快照。 */
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
    /** 注册读写器状态观察者，并立即分发当前状态。 */
    public void addObserver(@NonNull ReaderObserver observer) { coordinator.addObserver(observer); }

    /** 注销读写器状态观察者。 */
    public void removeObserver(@NonNull ReaderObserver observer) {
        coordinator.removeObserver(observer);
    }

    public static boolean isValidIpv4(@Nullable String value) {
        return ReaderSessionCoordinator.isValidIpv4(value);
    }

    /** 使用 IPv4 地址连接 Wi-Fi 读写器。 */
    public void connectWifi(@NonNull String address) { coordinator.connectWifi(address); }

    /** 连接扫描结果中选定的 BLE 读写器。 */
    public void connectBle(@NonNull Device device) { coordinator.connectBle(device); }

    /** 按用户主动操作断开当前读写器。 */
    public void disconnect() { coordinator.disconnect(); }

    /** 使用指定原因断开当前读写器。 */
    public void disconnect(@NonNull DisconnectReason reason) { coordinator.disconnect(reason); }

    /**
     * 设置当前标签协议。
     *
     * @return Future 结果为底层 SDK 状态码，0 表示成功
     */
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

    /** 启动连续盘点，Future 结果为底层 SDK 状态码。 */
    public CompletableFuture<Integer> startInventory() { return coordinator.startInventory(); }

    /** 停止连续盘点，Future 结果为底层 SDK 状态码。 */
    public CompletableFuture<Integer> stopInventory() { return coordinator.stopInventory(); }

    /** 清空当前会话内聚合的盘点结果。 */
    public void clearInventory() { coordinator.clearInventory(); }

    /** 返回当前盘点结果的只读快照。 */
    public List<InventoryItem> getInventorySnapshot() {
        return coordinator.getInventorySnapshot();
    }

    /** 应用连续盘点使用的标签掩码。 */
    public CompletableFuture<Integer> applyInventoryMask(@NonNull InventoryMaskConfig config) {
        return coordinator.applyInventoryMask(config);
    }

    /** 清除连续盘点使用的标签掩码。 */
    public CompletableFuture<Integer> clearInventoryMask() {
        return coordinator.clearInventoryMask();
    }
    @Nullable
    public InventoryMaskConfig getInventoryMask() { return coordinator.getInventoryMask(); }

    /** 设置单标签操作使用的目标标签掩码，传入 null 表示清除。 */
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

    /**
     * 向当前目标标签写入数据。
     *
     * @return Future 结果为底层 SDK 状态码，0 表示成功
     */
    public CompletableFuture<Integer> writeCurrentTag(int length, int address, int bank,
            byte[] password, byte[] data) {
        return coordinator.writeCurrentTag(length, address, bank, password, data);
    }

    /** 锁定当前目标标签的指定存储区。 */
    public CompletableFuture<Integer> lockCurrentTag(byte[] password, int bank, int policy) {
        return coordinator.lockCurrentTag(password, bank, policy);
    }

    /** 使用访问密码和销毁密码销毁当前目标标签。 */
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
