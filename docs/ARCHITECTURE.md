# UhfRemote 架构

## 1. 包依赖

- `reader.model`：连接、配置、协议、盘点和标签值对象，不依赖 Android 或厂商 SDK。
- `reader.sdk`：传输、配置、盘点和标签能力接口；只有 `NativeUhfSdkGateway` 引用 `com.uhf.*`。
- `reader.transport`：BLE 与 Wi-Fi 传输适配，向会话层报告连接代次和链路事件。
- `reader.persistence`：模块配置和连接地址存储接口，生产实现使用 MMKV。
- `reader.inventory`、`reader.tag`：盘点聚合、掩码状态和单标签操作。
- `reader.session`：进程会话门面、连接编排、命令串行化和跨功能流程。
- `reader.android`：主线程调度器和 Reader 前台服务适配。
- `ui.reader.config`、`ui.reader.inventory`、`ui.reader.singletag`：Reader 功能页面；`ui.reader.common` 保存共享控件和 Fragment 基类。
- `library:*`：既有 Android 基础组件，不承载 RFID 会话状态。

## 2. Reader 核心

`ReaderSessionManager` 是唯一进程单例和生产依赖装配入口。它创建原生 SDK、传输、存储、
Android 前台服务控制器和会话协调器。`ReaderSessionCoordinator` 不持有 Android 组件，
只编排配置、盘点、标签操作和跨功能掩码恢复。

门面将稳定、可独立测试的状态交给以下组件：

- `ReaderConnectionOrchestrator`：管理传输连接、握手、心跳、连接代次和断连清理。
- `ReaderCommandExecutor`：在单线程执行器上运行 SDK 命令，管理 Future 和 SDK 错误断连。
- `ReaderStatePublisher`：保存观察者，并把状态、配置、盘点和标签通知分发到主线程。
- `ReaderConnectionManager`：保存连接状态、连接代次和异常断开提示状态。
- `ReaderConfigurationManager`：读取和修改 Reader 配置，维护模块级缓存并发布快照。
- `ReaderInventoryController`：累积盘点数据、合并高频通知、维护库存掩码和 Query Sel。
- `ReaderTagOperations`：维护当前标签和单标签掩码，封装标签读写、锁定和 Kill 调用。

`ReaderConfigCache` 通过 `ReaderConfigurationStore` 保存配置；`MmkvReaderConnectionStore`
保存 Wi-Fi 地址。JVM 测试注入内存实现，因此会话和领域逻辑不依赖 Android 存储环境。

## 3. 线程模型

- 所有厂商 UHF SDK 调用由 `ReaderCommandExecutor` 在单线程执行器 `uhf-sdk` 上串行执行。
- BLE 回调和 Android 网络回调不直接执行阻塞 SDK 操作。
- `ReaderStatePublisher` 把观察者通知切换到 Android 主线程。
- 盘点标签先在线程安全的 `InventoryAccumulator` 中合并，再以 100ms 间隔发布快照。
- 连接代次、状态快照和掩码状态通过原子类型、`volatile` 或线程安全集合跨线程读取。

## 4. 连接数据流

1. UI 调用 `connectBle` 或 `connectWifi`，连接管理器增加连接代次。
2. 旧传输在 `uhf-sdk` 线程关闭，过期代次产生的进度、成功和失败被丢弃。
3. 传输通道建立后先验证 RM70XX 模块并读取版本信息。
4. 模块验证成功后更新并读取设备参数，再发布可操作的 `CONNECTED` 状态与配置快照。
5. Wi-Fi 连接启动空闲心跳；BLE 数据由传输适配器转交原生 SDK。
6. 意外断开时完成所有待处理 Future，清理掩码、配置和当前标签，再发布一次断开事件。

## 5. 盘点与标签数据流

盘点启动前重新应用盘点区域和库存掩码。标签回调由
`ReaderInventoryController` 合并后通知 UI。离开盘点页或执行单标签操作时，门面先停止盘点。

库存掩码与单标签掩码相互独立。首次启用库存掩码时缓存 Reader 当前 Query Sel，
清除掩码时恢复该值。单标签操作临时切换掩码，并在操作结束后恢复原库存掩码或原 Query Sel。

## 6. 测试

纯 JVM 测试使用 JUnit 4 和手写 Fake，覆盖协议编码、读取格式、握手、配置管理、命令 Future、
BLE/Wi-Fi 连接编排、取消和旧代次隔离、盘点累积、掩码校验与恢复、标签操作和 CSV 导出。

JVM 测试不验证 Android 蓝牙栈、网络切换、前台服务通知、厂商 native 库和真实标签时序。
这些边界由 Debug 构建和真机回归验证。
