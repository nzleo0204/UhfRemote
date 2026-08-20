# RFID 架构说明

## 目标

RFID 代码既服务于当前库存应用，也作为独立示例能力供客户二次开发。公共 API
保持稳定，Android 页面、连接实现和 Linkage SDK 细节不向业务模块扩散。

## 包结构

```text
com.leo.uhf.rfid
├── api/                 # 客户可使用的会话入口、观察者和模型
│   └── model/
├── domain/              # 配置、盘点和单标签领域逻辑
├── session/             # 连接代次、握手、命令串行化和状态发布
├── transport/           # 蓝牙、串口和 Wi-Fi 物理连接
├── sdk/
│   ├── capability/      # 配置、盘点、标签和传输能力接口
│   └── linkage/         # UhfNativeBridge，唯一的 Linkage 适配入口
├── persistence/         # MMKV 和配置存储
├── platform/android/    # 主线程和 Android Service 适配
└── demo/                # RFID 示例页面和页面专用弹窗
```

订单、库存、出货和认证等业务包继续使用各自的 `com.leo.uhf` 包结构，不与
RFID 公共 API 混合。

## 运行链路

```text
业务或 RFID Demo 页面
        ↓
ReaderSessionManager
        ↓
ReaderSessionCoordinator
        ↓
ReaderConnectionOrchestrator / ReaderCommandExecutor
        ↓
ReaderTransportGateway 等能力接口
        ↓
UhfNativeBridge
        ↓
com.uhf.linkage.Linkage
```

`ReaderSessionManager` 是进程级生产入口，负责创建默认依赖。协调器编排会话、
配置、盘点和标签操作；连接编排器负责连接代次、握手、心跳和旧任务清理。SDK
命令通过单线程执行器串行运行，观察者通知切回主线程。

## 串口边界

```text
connectSerial
  → SerialPowerController.powerOn()
  → 等待模块就绪
  → UhfNativeBridge.openSerial(path)
  → Linkage.open_serial(path)
  → 复用统一握手和参数初始化
```

Linkage 已经负责串口读取线程、协议封装和解析，因此工程不再维护另一套
旧的并行串口传输实现或串口 JNI。由于 `open_serial` 不接收
波特率，公开串口配置固定为 `115200`。

直连串口模块会先按用户选择设置 Linkage 的射频解析类型；其中
`ModuleSubtype.RM8011` 明确映射为 `setRFModuleType(1)`。蓝牙和 Wi-Fi 使用的是
板卡透传模式 `setRFModuleType(2)`，随后通过 `getBoardModuleType` 读取真实模块类型，
两者不能混用。

## 旗连模块映射

老项目 `Uhf_Android` 中 `ConnectManger.setModuleMagicRF()` 使用
`setRFModuleType(1)`，相关固件文件名为 `RM8011`。当前使用
`ModuleSubtype.RM8011` 表示该设备，`getRawValue()` 仍返回 `1`，显示名为
“旗连（RM8011 / MagicRF）”。这层映射集中在模型和 Linkage 适配边界，页面不再
自行判断裸数值。

## 生命周期和竞态

每次连接尝试拥有递增代次。握手、进度、失败和断开回调只有在代次仍为当前值时
才能更新状态；旧任务只关闭自己捕获的传输资源，不能覆盖新连接。页面观察者应在
View 销毁时移除，弹窗由页面生命周期关闭，异步回调不得持有已销毁的 View。

`ReaderState` 的含义如下：

- `isConnected()`：已完成设备验证和参数初始化，可以执行 RFID 操作。
- `hasTransportLink()`：物理传输通道已经建立。
- `isInitializing()`：正在读取模块信息或更新设备参数。

## 测试边界

JVM 单元测试覆盖模型、掩码解析、读取格式化、连接代次和连接失败状态。真实
BLE、Wi-Fi、串口设备仍需在目标硬件上验证，尤其是厂商上电控制和系统设备节点
权限；不以 JVM Fake 代替真机回归。
