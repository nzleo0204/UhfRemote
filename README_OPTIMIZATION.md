# RFID 整改记录

## 已完成

- RFID 代码按 `api`、`domain`、`session`、`transport`、`sdk`、`persistence`、
  `platform` 和 `demo` 分层。
- `ReaderSessionManager` 作为客户可使用的进程级 API 入口，生产依赖在此装配。
- Linkage SDK 直接引用集中在 `UhfNativeBridge`，业务和页面不直接依赖
  `com.uhf.*`。
- 串口生产链路收敛为上电、等待、`Linkage.open_serial(String)` 和统一握手，
  删除未接入生产链路的并行原始流串口类。
- 串口波特率固定为 `115200`，默认端口为 `/dev/ttyS1`。
- 老 Demo 的 `MagicRF` 与当前 `ModuleSubtype.RM8011` 对齐，原始 SDK 类型值为
  `1`，旗连默认上电等待为 3000 毫秒。
- 连接任务使用代次校验，旧任务不能污染新连接；状态区分传输链路、初始化和可用
  三个阶段。
- UI 代码移动到 `demo` 包，库存、订单、出货和认证等业务包未参与本次迁移。

## 未完成和边界

- 厂商设备的 GPIO 上电控制仍由具体产品通过 `SerialPowerController` 提供，
  不把参考 Demo 的厂商 JNI 直接复制到公共 RFID 层。
- BLE、Wi-Fi 和串口的最终连接结果仍需目标硬件矩阵回归。
- 现有历史资源和 Lint baseline 未在本次包迁移中批量删除；它们应在页面回归
  完整后单独清理。

## 验证命令

```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:lintDebug
git diff --check
```

上述命令分别验证 JVM 单测、APK 构建、Lint 和补丁格式。真机验证仍需覆盖连接
弹窗切换、切页/旋转、断开恢复、盘点、单标签读写和串口上电时序。
