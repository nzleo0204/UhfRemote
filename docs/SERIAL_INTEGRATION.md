# 串口读写器集成说明

RFID 会话通过 `com.leo.rfid.sdk.connect.serial` 提供串口连接抽象，支持 R2000、R2000Plus、RM610 和 RM8011。

## 配置

- 串口路径由客户设备提供，例如 `/dev/ttyS1` 或 `/dev/ttyUSB0`。
- 支持波特率 `9600`、`38400`、`57600` 和 `115200`，默认 `115200`。
- 默认上电控制器 `DelayPowerController` 不操作 GPIO，只等待配置的上电延时，默认 `500 ms`。
- 需要 GPIO、继电器或串口命令上电时，实现 `SerialPowerController` 并注入会话装配层。

## 端口实现

`SerialPortManager` 使用可替换的 `Factory`。当前默认实现只打开系统设备节点流，用于具备标准文件串口接口的设备。生产 RFID 会话使用当前 `uhf.jar` 中已验证的 `Linkage.open_serial(String)` / `close_serial()`，因为参考工程的 `serial.aar` 是针对特定设备 GPIO/端口编号的独立实现，不能假定适用于所有客户平台。若客户平台使用其他串口 JNI，实现 `Factory` 和 `SerialPortManager.Port`，不要把 JNI 类型泄漏到 RFID 会话或 UI。

## 配置保存与权限

`SerialConfigDialog` 使用 `MmkvSerialConfigStore` 保存最近一次的路径、波特率、模块型号和上电延时。`/dev` 设备节点通常不受 Android 存储权限控制；对于 Android 12 及以下的非设备节点路径，弹窗会通过 XXPermissions 请求读写存储权限。权限通过后才保存配置并开始连接。

## 权限与故障排查

串口设备节点的读写权限由客户设备的 system/udev 配置决定。普通 Android 存储权限不能保证串口访问，应用不会无条件执行 `su chmod`。连接失败时优先检查：设备节点是否存在、应用进程是否属于允许访问该节点的用户组、端口是否被其他进程占用、波特率是否匹配。

串口读写异常会关闭当前端口并发布断开状态；旧连接代次的回调不会污染新连接。
