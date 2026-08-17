# UhfRemote

通过蓝牙或 Wi-Fi 连接 UHF RFID 读写器，支持设备配置、标签盘点、单标签读写与 CSV 导出。

## 项目结构

```
UhfRemote/
├── app/                        # 主应用模块
│   ├── src/main/java/
│   │   └── com/leo/remote/
│   │       ├── reader/         # Reader 模型、会话、SDK、传输和持久化
│   │       ├── ui/reader/      # 配置、盘点和单标签页面
│   │       ├── util/           # 工具类
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

## 文档

- [架构文档](docs/ARCHITECTURE.md) - Reader 包依赖、线程模型、连接流程和测试边界
