# RFID SDK 使用指南

本文档描述当前工程中可直接使用的 RFID 会话 API。应用的 `namespace` 和
`applicationId` 为 `com.leo.uhf`，RFID 公共代码位于
`com.leo.uhf.rfid.api`。

## 初始化

在 `Application.onCreate()` 中初始化一次：

```java
ReaderSessionManager.initialize(this);
ReaderSessionManager reader = ReaderSessionManager.getInstance(this);
```

业务页面通过 `addObserver` 订阅状态，并在页面销毁时调用 `removeObserver`：

```java
ReaderObserver observer = new ReaderObserver() {
    @Override
    public void onReaderStateChanged(ReaderState state) {
        // 根据 state.getPhase() 更新页面
    }
};
reader.addObserver(observer);
// 页面销毁时
reader.removeObserver(observer);
```

## 连接

### 串口

当前生产适配器调用 Linkage 的 `open_serial(String)`，该方法不接收波特率，
因此串口配置只接受 `115200`。默认端口为 `/dev/ttyS1`。

```java
SerialConfig config = SerialConfig.defaultsFor(ModuleSubtype.RM8011);
reader.connectSerial(config);
```

需要设备平台自行控制上电时，可传入 `SerialPowerController`。旗连设备在老 Demo
中称为 `MagicRF`，当前统一使用 `ModuleSubtype.RM8011` 表示，原始类型值为 `1`，
默认上电后等待 3000 毫秒；其它当前模块默认等待 500 毫秒。

这里的类型值只用于串口直连模块的解析选择；BLE/Wi-Fi 连接先使用 RM70XX 板卡
透传类型 `2`，再由设备返回模块类型，不能把 `2` 当作 RM8011。

### 蓝牙和 Wi-Fi

```java
reader.connectBle(device);
reader.connectWifi("192.168.1.100");
```

Wi-Fi 端口由 `ReaderSessionManager.WIFI_PORT` 固定为 `1200`。蓝牙传输层只负责
GATT 建链和数据转发，RFID 协议由 Linkage SDK 处理。

### 断开和状态

```java
reader.disconnect();
reader.disconnect(DisconnectReason.USER);
```

`ReaderState.isConnected()` 表示已完成握手和参数初始化；`hasTransportLink()`
表示物理链路已建立；`isInitializing()` 表示正在读取版本或更新设备参数。

## 配置、盘点和标签

异步操作返回 `CompletableFuture<Integer>`，底层状态码 `0` 表示成功：

```java
reader.refreshConfiguration();
reader.setPower(260); // 26.0 dBm，单位为 0.1 dBm
reader.setProtocol(TagProtocol.ISO_18000_6C);
reader.setInventoryArea(0, 0, 6);
reader.startInventory();
reader.stopInventory();
reader.clearInventory();
```

盘点结果通过 `ReaderObserver.onInventoryChanged` 接收。掩码使用
`InventoryMaskConfig`，盘点掩码和单标签掩码相互独立：

```java
reader.applyInventoryMask(mask);
reader.clearInventoryMask();
reader.setSingleTagMask(mask);
reader.setSingleTagMask(null);
```

单标签 API 提供 `readCurrentTag`、`writeCurrentTag`、`lockCurrentTag` 和
`killCurrentTag`。读取结果中的 `getData()` 为本次区域数据，`getEpc()` 为返回的
完整 EPC。

## 依赖边界

应用业务只依赖 `api` 和 `api.model`。`session` 负责会话编排，`transport` 负责
物理连接，`sdk.linkage.UhfNativeBridge` 是唯一直接适配 Linkage SDK 的生产入口。
应用层不需要自行创建串口读取线程或解析 RFID 协议。Android 存储权限也不能授予
`/dev/tty*` 设备节点访问权限，具体权限由设备系统和厂商平台负责。
