# UhfRemote - RFID 远程控制 Android 应用

通过蓝牙和 WiFi 远程获取或修改 RFID 数据

## 📋 项目状态

- **代码质量**: 8.2/10 (优秀)
- **代码规模**: 15,062 行 Java 代码
- **主要功能**: WiFi/BLE 连接、RFID 标签盘点、数据导出

## 🚀 当前任务

正在执行项目整改计划，详见 [`CODEX_IMPLEMENTATION_PLAN.md`](CODEX_IMPLEMENTATION_PLAN.md)

### 整改目标
1. ✅ P1 修复完成（混淆、网络安全、部分命名）
2. ✅ 统一命名规范
3. ✅ 拆分 Reader 核心状态与操作模块
4. ✅ 建立单元测试体系
5. ✅ 完善架构文档

## 📊 项目结构

```
UhfRemote/
├── app/                        # 主应用模块
│   ├── src/main/java/
│   │   └── com/leo/remote/
│   │       ├── reader/         # Reader 核心层
│   │       ├── ui/             # UI 层
│   │       ├── util/           # 工具类
│   │       └── manager/        # 管理器
│   └── src/main/res/           # 资源文件
├── library/                    # 基础库模块
└── docs/                       # 文档
```

## 🛠️ 开发环境

- Android Studio Hedgehog+
- JDK 21
- Gradle 9.6.1
- Android SDK 34

## 📝 文档

- [执行计划](CODEX_IMPLEMENTATION_PLAN.md) - Codex 执行的详细整改计划
- [架构文档](docs/ARCHITECTURE.md) - Reader 核心、线程模型与数据流

## 🤝 贡献

由 Codex 执行整改计划，人工审查和验证。
