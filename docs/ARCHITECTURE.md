# UhfRemote 架构

## 1. 分层

- `ui/activity`、`ui/fragment`：页面生命周期、输入处理和用户反馈。
- `data/repository`：业务数据接口及当前使用的 Mock 实现。
- `reader`：UHF Reader 连接、配置、盘点和标签操作。
- `UhfSdkGateway`：应用 Reader 核心层与厂商 SDK 之间的唯一接口。
- `library`：AndroidProject 基础组件，不承载 RFID 业务状态。

## 2. Reader 核心

`ReaderSessionManager` 是 UI 使用的会话门面，只保留稳定的公开 API，并把调用委托给
包内 `ReaderSessionCoordinator`。协调器负责 SDK 串行执行、BLE/Wi-Fi 传输编排、
握手、断连清理，以及需要跨模块保持原子性的掩码切换流程。

门面将稳定、可独立测试的状态交给以下组件：

- `ReaderStatePublisher`：保存观察者，并把状态、配置、盘点和标签通知分发到主线程。
- `ReaderConnectionManager`：保存连接状态、连接代次和异常断开提示状态。
- `ReaderConfigurationManager`：读取和修改 Reader 配置，维护模块级缓存并发布快照。
- `ReaderInventoryController`：累积盘点数据、合并高频通知、维护库存掩码和 Query Sel。
- `ReaderTagOperations`：维护当前标签和单标签掩码，封装标签读写、锁定和 Kill 调用。

`ReaderConfigCache` 通过 `ReaderConfigurationStore` 接口保存配置。生产实现使用 MMKV，
单元测试使用内存实现，因此核心配置逻辑不依赖 Android 存储环境。

## 3. 线程模型

- 所有厂商 UHF SDK 调用在单线程执行器 `uhf-sdk` 上串行执行。
- BLE 回调和 Android 网络回调不直接执行阻塞 SDK 操作。
- `ReaderStatePublisher` 把观察者通知切换到 Android 主线程。
- 盘点标签先在线程安全的 `InventoryAccumulator` 中合并，再以 100ms 间隔发布快照。
- `ReaderSessionCoordinator` 的共享状态通过 `volatile` 或线程安全组件跨线程读取。

## 4. 连接数据流

1. UI 调用 `connectBle` 或 `connectWifi`，连接管理器增加连接代次。
2. 旧传输在 `uhf-sdk` 线程关闭，过期代次产生的回调被丢弃。
3. 传输通道建立后执行 RM70XX 握手，读取模块信息与配置。
4. 连接状态先发布，随后发布配置快照；Wi-Fi 连接启动空闲心跳。
5. 意外断开时完成所有待处理 Future，清理掩码、配置和当前标签，再发布一次断开事件。

## 5. 盘点与标签数据流

盘点启动前重新应用盘点区域和库存掩码。标签回调由
`ReaderInventoryController` 合并后通知 UI。离开盘点页或执行单标签操作时，门面先停止盘点。

库存掩码与单标签掩码相互独立。首次启用库存掩码时缓存 Reader 当前 Query Sel，
清除掩码时恢复该值。单标签操作临时切换掩码，并在操作结束后恢复原库存掩码或原 Query Sel。

## 6. 测试

纯 JVM 测试覆盖协议编码、握手、配置管理、状态发布、连接代次、盘点累积、掩码恢复和标签操作。
涉及 BLE、Wi-Fi、前台服务和厂商硬件的流程由 Debug 构建及真机回归覆盖。
