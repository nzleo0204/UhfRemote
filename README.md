# UhfRemote

通过蓝牙或 Wi-Fi 连接 UHF RFID 读写器，支持设备配置、标签盘点、单标签读写与 CSV 导出。

## 项目结构

```
UhfRemote/
├── app/                        # 主应用模块
│   ├── src/main/java/
│   │   └── com/leo/remote/
│   │       ├── rfid/           # RFID 演示 UI、SDK 和 Native 桥接
│   │       ├── business/       # 库存、订单、出货和认证业务
│   │       ├── core/           # 基础 UI、网络、存储和工具
│   │       ├── app/            # Application、主界面和启动页
│   │       └── manager/        # 管理器
│   └── src/main/res/           # 资源文件
├── library/                    # 基础库模块
└── docs/                       # 文档
```

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
