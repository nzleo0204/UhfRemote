# UhfRemote

通过蓝牙、Wi-Fi 或串口连接 UHF RFID 读写器，支持设备配置、标签盘点、单标签读写与 CSV 导出。

## 项目结构

```
UhfRemote/
├── app/                        # 主应用模块
│   ├── src/main/java/
│   │   └── com/leo/
│   │       └── uhf/            # 应用根包
│   │           ├── app/        # 应用壳、启动与崩溃处理
│   │           ├── business/   # 库存、订单、发货和认证业务
│   │           ├── core/       # 通用框架核心
│   │           └── rfid/       # 可复用 RFID 演示、SDK 和 Native 桥接
│   └── src/main/res/           # 资源文件
├── library/                    # 基础库模块
└── docs/                       # 文档
```

RFID 公共能力位于 `com.leo.uhf.rfid`，应用壳、业务模块和基础组件位于 `com.leo.uhf`。

## 支持的连接方式

- 蓝牙（BLE）
- Wi-Fi
- 串口直连（R2000、R2000Plus、RM610、RM8011）

## 串口连接配置

1. 输入串口路径，例如 `/dev/ttyS1`。
2. 选择模块型号、波特率和上电延时。
3. 点击“连接”，应用会保存下次使用的配置。

串口设备节点权限由客户设备平台控制，可能需要系统签名、设备用户组或 root 配置。当前 SDK 的 `Linkage` 已提供串口打开/关闭 API，因此生产连接不额外复制未经验证的 JNI 库；如客户平台使用其他串口 JNI，实现 `SerialPortManager.Factory` 即可接入。

## 开发环境

- Android Studio Hedgehog+
- JDK 21
- Gradle 9.6.1
- Android SDK 34

## 验证

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Lint 使用 `app/lint-baseline.xml` 记录现有警告，并保持 `abortOnError` 开启。当前未使用模板资源暂时保留，新增警告不能进入基线。
