package com.leo.remote.reader;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cn.wandersnail.ble.Connection;
import cn.wandersnail.ble.ConnectionConfiguration;
import cn.wandersnail.ble.ConnectionState;
import cn.wandersnail.ble.Device;
import cn.wandersnail.ble.EasyBLE;
import cn.wandersnail.ble.EventObserver;
import cn.wandersnail.ble.Request;
import cn.wandersnail.ble.RequestBuilderFactory;
import cn.wandersnail.ble.RequestType;
import cn.wandersnail.ble.WriteOptions;
import cn.wandersnail.ble.callback.IndicationChangeCallback;
import cn.wandersnail.ble.callback.MtuChangeCallback;
import cn.wandersnail.ble.callback.NotificationChangeCallback;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class BleTransport implements EventObserver {
    private static final String TAG = "UhfBle";
    private static final String REQUEST_TAG_PREFIX = "uhf-ble-";
    interface Listener {
        void onPhase(long attemptId, ConnectionPhase phase, String message);
        void onReady(long attemptId);
        void onInboundData(long attemptId, byte[] data);
        void onDisconnected(long attemptId, String message, int errorCode, DisconnectReason reason);
    }

    private final EasyBLE easyBle;
    private final Listener listener;
    private volatile Connection connection;
    private volatile Device device;
    private volatile UUID serviceUuid;
    private volatile UUID writeUuid;
    private volatile UUID receiveUuid;
    private volatile int writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
    private volatile int mtuPayloadSize = 20;
    private volatile boolean disconnecting;
    private volatile long attemptId;

    BleTransport(Listener listener) {
        this.listener = listener;
        easyBle = EasyBLE.getInstance();
        easyBle.registerObserver(this);
    }

    void connect(Device target, long newAttemptId) {
        disconnecting = false;
        attemptId = newAttemptId;
        device = target;
        serviceUuid = null;
        writeUuid = null;
        receiveUuid = null;
        writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        mtuPayloadSize = 20;
        Log.i(TAG, "connect attempt=" + attemptId + " name=" + target.getName()
                + " address=" + target.getAddress());
        ConnectionConfiguration configuration = new ConnectionConfiguration()
                .setAutoReconnect(false)
                .setTryReconnectMaxTimes(0)
                .setConnectTimeoutMillis(12_000)
                .setRequestTimeoutMillis(5_000);
        connection = easyBle.connect(target, configuration);
        if (connection == null) {
            notifyDisconnected("Unable to create BLE connection", -1, DisconnectReason.SDK_ERROR);
        }
    }

    void disconnect() {
        disconnecting = true;
        Connection currentConnection = connection;
        Device currentDevice = device;
        if (currentConnection != null) { currentConnection.clearRequestQueue(); }
        if (currentDevice != null) { easyBle.releaseConnection(currentDevice); }
        connection = null;
        device = null;
    }

    void release() {
        disconnect();
        easyBle.unregisterObserver(this);
    }

    void write(byte[] data) {
        Connection currentConnection = connection;
        UUID currentService = serviceUuid;
        UUID currentWrite = writeUuid;
        int currentWriteType = writeType;
        int currentPayloadSize = mtuPayloadSize;
        long currentAttempt = attemptId;
        if (currentConnection == null || currentService == null || currentWrite == null
                || data == null || data.length == 0) {
            notifyDisconnected("BLE data channel is unavailable", -2, DisconnectReason.SDK_ERROR);
            return;
        }
        WriteOptions options = new WriteOptions.Builder()
                .setPackageSize(currentPayloadSize)
                .setPackageWriteDelayMillis(5)
                .setRequestWriteDelayMillis(10)
                .setWaitWriteResult(currentWriteType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                .setWriteType(currentWriteType)
                .build();
        Request request = new RequestBuilderFactory()
                .getWriteCharacteristicBuilder(currentService, currentWrite, data)
                .setWriteOptions(options)
                .setTag(requestTag(currentAttempt))
                .build();
        currentConnection.execute(request);
    }

    @Override
    public void onConnectionStateChanged(@NonNull Device changedDevice) {
        if (!isCurrent(changedDevice)) {
            return;
        }
        ConnectionState state = changedDevice.getConnectionState();
        Log.d(TAG, "state attempt=" + attemptId + " state=" + state
                + " address=" + changedDevice.getAddress());
        if (state == ConnectionState.CONNECTING || state == ConnectionState.CONNECTED) {
            listener.onPhase(attemptId, ConnectionPhase.CONNECTING, "正在连接蓝牙设备");
        } else if (state == ConnectionState.SERVICE_DISCOVERING) {
            listener.onPhase(attemptId, ConnectionPhase.DISCOVERING_SERVICES, "正在发现 GATT 服务");
        } else if (state == ConnectionState.SERVICE_DISCOVERED) {
            listener.onPhase(attemptId, ConnectionPhase.DISCOVERING_SERVICES, "正在匹配数据特征");
            selectDataCharacteristics();
        } else if ((state == ConnectionState.DISCONNECTED || state == ConnectionState.RELEASED) && !disconnecting) {
            notifyDisconnected("BLE connection lost", -3, DisconnectReason.LINK_LOST);
        }
    }

    @Override
    public void onBluetoothAdapterStateChanged(int state) {
        if (state == BluetoothAdapter.STATE_OFF) {
            notifyDisconnected("Bluetooth is turned off", state, DisconnectReason.BLUETOOTH_OFF);
        } else if (state == BluetoothAdapter.STATE_ON) {
            Log.d(TAG, "Bluetooth adapter is available; waiting for explicit connection");
        }
    }

    @Override
    public void onConnectFailed(@NonNull Device failedDevice, int failType) {
        if (isCurrent(failedDevice)) {
            Log.e(TAG, "connect failed attempt=" + attemptId + " type=" + failType);
            notifyDisconnected("BLE connection failed", failType, DisconnectReason.SDK_ERROR);
        }
    }

    @Override
    public void onConnectTimeout(@NonNull Device failedDevice, int type) {
        if (isCurrent(failedDevice)) {
            Log.e(TAG, "connect timeout attempt=" + attemptId + " type=" + type);
            notifyDisconnected("BLE connection timed out", type, DisconnectReason.SDK_ERROR);
        }
    }

    @Override
    public void onConnectionError(@NonNull Device failedDevice, int status) {
        if (isCurrent(failedDevice)) {
            Log.e(TAG, "connection error attempt=" + attemptId + " gattStatus=" + status);
            notifyDisconnected("BLE connection error", status, DisconnectReason.SDK_ERROR);
        }
    }

    @Override
    public void onCharacteristicChanged(@NonNull Device changedDevice, @NonNull UUID service,
            @NonNull UUID characteristic, @NonNull byte[] value) {
        if (isCurrent(changedDevice) && service.equals(serviceUuid) && characteristic.equals(receiveUuid)) {
            Log.v(TAG, "receive attempt=" + attemptId + " bytes=" + value.length);
            listener.onInboundData(attemptId, value);
        }
    }

    @Override
    public void onRequestFailed(@NonNull Request request, int failType, int gattStatus, @Nullable Object value) {
        if (!isRequestForCurrentAttempt(request)) { return; }
        Log.e(TAG, "request failed type=" + request.getType() + " failType=" + failType
                + " gattStatus=" + gattStatus);
        boolean dataChannelFailure = request.getType() == RequestType.WRITE_CHARACTERISTIC;
        if (!disconnecting && device != null && (dataChannelFailure
                || failType == Connection.REQUEST_FAIL_TYPE_CONNECTION_DISCONNECTED
                || failType == Connection.REQUEST_FAIL_TYPE_BLUETOOTH_ADAPTER_DISABLED)) {
            notifyDisconnected("BLE request failed", gattStatus == -1 ? failType : gattStatus,
                    DisconnectReason.SDK_ERROR);
        }
    }

    private boolean isCurrent(Device candidate) {
        return device != null && device.equals(candidate);
    }

    private void selectDataCharacteristics() {
        if (connection == null || connection.getGatt() == null) {
            notifyDisconnected("GATT services are unavailable", -4, DisconnectReason.SDK_ERROR);
            return;
        }
        logGattProfile();
        DataChannel preferred = null;
        boolean hasStandardDataChannel = false;
        boolean hasHidDataChannel = false;
        List<BluetoothGattService> services = new ArrayList<>(connection.getGatt().getServices());
        services.sort(Comparator.comparing(service -> service.getUuid().toString()));
        for (BluetoothGattService service : services) {
            DataChannel candidate = createDataChannel(service);
            if (candidate == null) { continue; }
            if (isStandardGattService(service.getUuid())) {
                hasStandardDataChannel = true;
                hasHidDataChannel |= service.getUuid().toString().toLowerCase().startsWith("00001812-");
            } else {
                preferred = betterOf(preferred, candidate);
            }
        }
        if (preferred != null) {
            serviceUuid = preferred.service.getUuid();
            writeUuid = preferred.write.getUuid();
            receiveUuid = preferred.receive.getUuid();
            writeType = (preferred.write.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                    ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
            Log.i(TAG, "data channel attempt=" + attemptId + " service=" + serviceUuid
                    + " write=" + writeUuid + " receive=" + receiveUuid + " writeType=" + writeType);
            requestMtuAndEnable(preferred.receive);
            return;
        }
        notifyDisconnected(hasHidDataChannel
                ? "设备仅提供 HID 服务，未发现 UHF BLE 透传通道"
                : hasStandardDataChannel ? "该设备仅提供标准蓝牙服务，不支持 UHF BLE 透传"
                : "未发现兼容的 UHF BLE 透传服务", -5, DisconnectReason.SDK_ERROR);
    }

    private DataChannel createDataChannel(BluetoothGattService service) {
        BluetoothGattCharacteristic write = null;
        BluetoothGattCharacteristic receive = null;
        int writeScore = -1;
        int receiveScore = -1;
        List<BluetoothGattCharacteristic> characteristics = new ArrayList<>(service.getCharacteristics());
        characteristics.sort(Comparator.comparing(characteristic -> characteristic.getUuid().toString()));
        for (BluetoothGattCharacteristic characteristic : characteristics) {
            int properties = characteristic.getProperties();
            int candidateWriteScore = writeScore(characteristic.getUuid(), properties);
            if (candidateWriteScore >= writeScore) {
                writeScore = candidateWriteScore;
                if (candidateWriteScore >= 0) { write = characteristic; }
            }
            int candidateReceiveScore = receiveScore(characteristic.getUuid(), properties);
            if (candidateReceiveScore >= receiveScore) {
                receiveScore = candidateReceiveScore;
                if (candidateReceiveScore >= 0) { receive = characteristic; }
            }
        }
        if (write == null || receive == null) { return null; }
        int score = writeScore + receiveScore + serialUuidScore(service.getUuid());
        if (write.getUuid().equals(receive.getUuid())) { score += 5; }
        return new DataChannel(service, write, receive, score);
    }

    private static DataChannel betterOf(@Nullable DataChannel current, DataChannel candidate) {
        if (current == null || candidate.score > current.score) { return candidate; }
        if (candidate.score < current.score) { return current; }
        return candidate.service.getUuid().toString().compareTo(current.service.getUuid().toString()) < 0
                ? candidate : current;
    }

    private static int writeScore(UUID uuid, int properties) {
        int score = -1;
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) { score = 10; }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) { score = 20; }
        return score < 0 ? score : score + serialUuidScore(uuid);
    }

    private static int receiveScore(UUID uuid, int properties) {
        int score = -1;
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) { score = 10; }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) { score = 20; }
        return score < 0 ? score : score + serialUuidScore(uuid);
    }

    private static int serialUuidScore(UUID uuid) {
        String value = uuid.toString().toLowerCase();
        if (value.startsWith("0000ffe") || value.startsWith("0000fff")
                || value.startsWith("0000ff0") || value.startsWith("6e4000")
                || value.startsWith("49535343")) {
            return 100;
        }
        return 0;
    }

    private static boolean isStandardGattService(UUID uuid) {
        String value = uuid.toString().toLowerCase();
        if (!value.endsWith("-0000-1000-8000-00805f9b34fb") || !value.startsWith("0000")) {
            return false;
        }
        try {
            int assignedNumber = Integer.parseInt(value.substring(4, 8), 16);
            return assignedNumber >= 0x1800 && assignedNumber <= 0x18ff;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void logGattProfile() {
        if (connection == null || connection.getGatt() == null) { return; }
        for (BluetoothGattService service : connection.getGatt().getServices()) {
            Log.i(TAG, "service attempt=" + attemptId + " uuid=" + service.getUuid());
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                Log.i(TAG, "characteristic attempt=" + attemptId + " uuid=" + characteristic.getUuid()
                        + " properties=0x" + Integer.toHexString(characteristic.getProperties()));
            }
        }
    }

    private void requestMtuAndEnable(BluetoothGattCharacteristic characteristic) {
        long requestAttempt = attemptId;
        Connection currentConnection = connection;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || currentConnection == null) {
            enableReceive(characteristic);
            return;
        }
        Request request = new RequestBuilderFactory().getChangeMtuBuilder(512)
                .setTag(requestTag(requestAttempt))
                .setCallback(new MtuChangeCallback() {
                    @Override
                    public void onMtuChanged(@NonNull Request request, int mtu) {
                        if (requestAttempt != attemptId) { return; }
                        mtuPayloadSize = Math.max(20, mtu - 3);
                        Log.i(TAG, "MTU negotiated attempt=" + requestAttempt + " mtu=" + mtu
                                + " payload=" + mtuPayloadSize);
                        enableReceive(characteristic);
                    }

                    @Override
                    public void onRequestFailed(@NonNull Request request, int failType,
                            int gattStatus, @Nullable Object value) {
                        if (requestAttempt != attemptId) { return; }
                        mtuPayloadSize = 20;
                        Log.w(TAG, "MTU negotiation failed attempt=" + requestAttempt
                                + " failType=" + failType + " gattStatus=" + gattStatus);
                        enableReceive(characteristic);
                    }
                }).build();
        currentConnection.execute(request);
    }

    private void enableReceive(BluetoothGattCharacteristic characteristic) {
        long requestAttempt = attemptId;
        listener.onPhase(requestAttempt, ConnectionPhase.ENABLING_NOTIFICATIONS, "正在启用数据通知");
        RequestBuilderFactory factory = new RequestBuilderFactory();
        boolean supportsNotify = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        if (supportsNotify) {
            connection.execute(factory.getSetNotificationBuilder(serviceUuid, receiveUuid, true)
                    .setTag(requestTag(requestAttempt))
                    .setCallback(new NotificationChangeCallback() {
                        @Override
                        public void onNotificationChanged(@NonNull Request request, boolean enabled) {
                            handleReceiveEnabled(requestAttempt, enabled);
                        }

                        @Override
                        public void onRequestFailed(@NonNull Request request, int failType, int gattStatus, @Nullable Object value) {
                            if (requestAttempt == attemptId) {
                                notifyDisconnected("Unable to enable BLE notifications",
                                        gattStatus == -1 ? failType : gattStatus, DisconnectReason.SDK_ERROR);
                            }
                        }
                    }).build());
            return;
        }
        connection.execute(factory.getSetIndicationBuilder(serviceUuid, receiveUuid, true)
                .setTag(requestTag(requestAttempt))
                .setCallback(new IndicationChangeCallback() {
                    @Override
                    public void onIndicationChanged(@NonNull Request request, boolean enabled) {
                        handleReceiveEnabled(requestAttempt, enabled);
                    }

                    @Override
                    public void onRequestFailed(@NonNull Request request, int failType, int gattStatus, @Nullable Object value) {
                        if (requestAttempt == attemptId) {
                            notifyDisconnected("Unable to enable BLE indications",
                                    gattStatus == -1 ? failType : gattStatus, DisconnectReason.SDK_ERROR);
                        }
                    }
                }).build());
    }

    private void handleReceiveEnabled(long requestAttempt, boolean enabled) {
        if (requestAttempt != attemptId) { return; }
        Log.i(TAG, "receive channel attempt=" + requestAttempt + " enabled=" + enabled + " service=" + serviceUuid
                + " characteristic=" + receiveUuid);
        if (enabled) {
            listener.onReady(requestAttempt);
        } else {
            notifyDisconnected("BLE receive channel was not enabled", -6, DisconnectReason.SDK_ERROR);
        }
    }

    private void notifyDisconnected(String message, int errorCode, DisconnectReason reason) {
        listener.onDisconnected(attemptId, message, errorCode, reason);
    }

    private boolean isRequestForCurrentAttempt(Request request) {
        if (request.getDevice() != null && !isCurrent(request.getDevice())) { return false; }
        String tag = request.getTag();
        return tag == null || !tag.startsWith(REQUEST_TAG_PREFIX) || tag.equals(requestTag(attemptId));
    }

    private static String requestTag(long requestAttempt) {
        return REQUEST_TAG_PREFIX + requestAttempt;
    }

    private static final class DataChannel {
        final BluetoothGattService service;
        final BluetoothGattCharacteristic write;
        final BluetoothGattCharacteristic receive;
        final int score;

        DataChannel(BluetoothGattService service, BluetoothGattCharacteristic write,
                BluetoothGattCharacteristic receive, int score) {
            this.service = service;
            this.write = write;
            this.receive = receive;
            this.score = score;
        }
    }
}
