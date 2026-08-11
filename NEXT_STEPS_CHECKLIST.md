# 下一步工作清单

**更新日期**: 2026-08-11  
**当前状态**: 编译验证完成 ✅

---

## ✅ 已完成工作

### 1. 编译验证 ✅
- [x] 清理项目 (./gradlew clean)
- [x] 编译 Debug 版本 (./gradlew assembleDebug)
- [x] 生成 APK 文件
- [x] 验证编译无错误

**APK 位置**: `./build/app/outputs/apk/debug/app-debug.apk`

---

## 🎯 立即执行任务

### Task 1: 安装 APK 到真机测试 ⏳

```bash
# 1. 连接设备
adb devices

# 2. 安装 APK
adb install ./build/app/outputs/apk/debug/app-debug.apk

# 3. 启动应用
adb shell am start -n com.leo.remote.debug/.ui.activity.SplashActivity
```

**验证点**:
- [ ] 应用正常启动
- [ ] 闪屏页显示正常
- [ ] 进入主界面

---

### Task 2: 功能回归测试 ⏳

#### 2.1 WiFi 连接测试
- [ ] 输入 Reader IP 地址
- [ ] 点击连接
- [ ] 验证连接成功
- [ ] 验证网络安全配置生效（局域网明文流量允许）

#### 2.2 BLE 连接测试
- [ ] 扫描蓝牙设备
- [ ] 连接 Reader
- [ ] 验证连接成功

#### 2.3 盘点功能测试
- [ ] 启动盘点
- [ ] 读取标签数据
- [ ] 验证列表实时更新
- [ ] 停止盘点

#### 2.4 CSV 导出测试
- [ ] 导出盘点数据
- [ ] 验证大数据集导出（修复后的流式写入）
- [ ] 检查导出文件内容

#### 2.5 页面切换测试
- [ ] 配置页 → 盘点页
- [ ] 盘点页 → 单标签页
- [ ] 验证自动停止盘点（离开盘点页）

---

## 📋 本周待办事项

### Task 3: 批量修复命名规范 ⏳

**发现 17 个文件仍使用匈牙利命名法**

#### 优先级分类

**高优先级** (基础类，影响范围大):
1. [ ] `app/AppActivity.java` - Activity 基类
2. [ ] `app/AppAdapter.java` - Adapter 基类
3. [ ] `app/TitleBarFragment.java` - Fragment 基类

**中优先级** (工具类):
4. [ ] `manager/DialogManager.java`
5. [ ] `manager/OrientationManager.java`
6. [ ] `util/CrashHandler.java`
7. [ ] `widget/StatusLayout.java`

**低优先级** (UI 组件):
8. [ ] `util/ArrowDrawable.java`
9. [ ] `util/SmartBallPulseFooter.java`
10. [ ] `util/MaterialHeader.java`
11. [ ] `util/LinkClickableSpan.java`
12. [ ] `permission/PermissionDescription.java`
13. [ ] `ui/popup/PermissionDescriptionPopup.java`
14. [ ] `ui/activity/common/CrashActivity.java`
15. [ ] `ui/dialog/common/MessageDialog.java`
16. [ ] `ui/dialog/common/StyleDialog.java`
17. [ ] `ui/dialog/common/WaitDialog.java`

**预计耗时**: 2-3 小时  
**验证方式**: 编译 + 功能测试

---

### Task 4: Release 编译测试 ⏳

```bash
# 编译 Release 版本
./gradlew assembleRelease
```

**验证点**:
- [ ] 编译成功
- [ ] 生成混淆后的 APK
- [ ] 检查 mapping.txt
- [ ] 确认 Reader 核心类未被混淆
- [ ] 确认 UHF SDK 未被混淆

**mapping.txt 位置**: `./build/app/outputs/mapping/release/mapping.txt`

---

### Task 5: 混淆验证 ⏳

```bash
# 查看混淆映射
cat ./build/app/outputs/mapping/release/mapping.txt | grep "com.leo.remote.reader"

# 预期结果：Reader 核心类不应该被混淆
# com.leo.remote.reader.ReaderSessionManager -> com.leo.remote.reader.ReaderSessionManager
```

**验证点**:
- [ ] Reader 核心类保持原名
- [ ] UHF SDK 类保持原名
- [ ] 观察者接口保持原名
- [ ] 其他业务类正常混淆

---

## 🔄 下周计划

### Week 2 任务

#### Task 6: 搭建单元测试框架 ⏳

**依赖库**:
```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.5.0'
testImplementation 'org.mockito:mockito-inline:5.5.0'
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
```

**创建测试目录**:
```
app/src/test/java/com/leo/remote/
├── reader/
│   ├── ReaderStateTest.java
│   ├── InventoryMaskConfigTest.java
│   └── HexCodecTest.java
└── util/
    └── ViewUtilsTest.java
```

**示例测试**:
```java
@Test
public void testInventoryMaskValidation() {
    InventoryMaskConfig config = new InventoryMaskConfig(1, 32, 96, hexData);
    assertTrue(config.getLengthBits() <= config.getMaskByteLength() * 8);
}
```

---

#### Task 7: ReaderSessionManager 重构 - Phase 1 ⏳

**准备工作**:
1. [ ] 编写集成测试（作为回归基准）
2. [ ] 创建重构分支
3. [ ] 备份当前代码

**Phase 1: 提取 ReaderStatePublisher**
- [ ] 创建新类
- [ ] 移动观察者管理代码
- [ ] 移动状态发布方法
- [ ] 更新 ReaderSessionManager
- [ ] 运行测试验证

**预计耗时**: 0.5 天

---

#### Task 8: 编写架构文档 ⏳

**文档大纲**:
```
1. 整体架构
   - 分层设计
   - 模块划分
   - 依赖关系

2. Reader 核心层
   - ReaderSessionManager
   - 连接管理
   - 状态管理
   - 盘点控制

3. UI 层
   - Activity/Fragment 架构
   - 导航设计
   - 数据绑定

4. 线程模型
   - SDK 线程
   - 主线程切换
   - 异步操作

5. 数据流
   - 连接流程
   - 盘点流程
   - 配置流程
```

**预计耗时**: 1 天

---

## 📊 进度跟踪

### 整体进度

| 阶段 | 任务数 | 已完成 | 进行中 | 待开始 | 进度 |
|------|--------|--------|--------|--------|------|
| 编译验证 | 1 | 1 | 0 | 0 | 100% ✅ |
| 功能测试 | 1 | 0 | 1 | 0 | 0% ⏳ |
| 命名修复 | 1 | 0 | 0 | 1 | 0% ⏳ |
| Release 测试 | 2 | 0 | 0 | 2 | 0% ⏳ |
| 测试框架 | 1 | 0 | 0 | 1 | 0% ⏳ |
| 重构准备 | 1 | 0 | 0 | 1 | 0% ⏳ |
| 文档编写 | 1 | 0 | 0 | 1 | 0% ⏳ |

### 里程碑

- [x] **Milestone 1**: 代码审查完成 (2026-08-10)
- [x] **Milestone 2**: P1 核心修复完成 (2026-08-10)
- [x] **Milestone 3**: 编译验证通过 (2026-08-11)
- [ ] **Milestone 4**: 功能测试通过 (预计 2026-08-11)
- [ ] **Milestone 5**: Release 验证通过 (预计 2026-08-12)
- [ ] **Milestone 6**: 命名规范统一 (预计 2026-08-15)
- [ ] **Milestone 7**: 重构完成 (预计 2026-08-25)

---

## 🚨 阻塞问题

### 当前无阻塞问题 ✅

所有依赖已就绪：
- ✅ Java 环境已配置
- ✅ 编译成功
- ✅ APK 已生成

---

## 💡 建议与优化

### 1. 自动化构建

**建议添加 CI/CD**:
```yaml
# .github/workflows/android.yml
name: Android CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Build with Gradle
        run: ./gradlew assembleDebug
      - name: Run tests
        run: ./gradlew test
```

### 2. 代码质量检查

**建议添加 Lint 检查**:
```bash
./gradlew lint
```

### 3. 性能分析

**建议使用 Android Profiler**:
- CPU 使用率
- 内存使用
- 网络请求
- 电量消耗

---

## 📞 需要协助的地方

### 真机测试

如果没有真机设备，可以：
1. 使用 Android 模拟器（AVD）
2. 使用 Firebase Test Lab
3. 寻求测试人员协助

### Reader 硬件

功能测试需要：
- RFID Reader 硬件设备
- RFID 标签（用于测试）
- WiFi 网络环境

---

## ✅ 验证清单

### 代码质量

- [x] 编译无错误
- [ ] 编译无警告（优化过时 API）
- [ ] Lint 检查通过
- [ ] 单元测试覆盖 70%+

### 功能验证

- [ ] WiFi 连接正常
- [ ] BLE 连接正常
- [ ] 盘点功能正常
- [ ] CSV 导出正常
- [ ] 页面切换正常

### 发布验证

- [ ] Release 编译成功
- [ ] 混淆配置正确
- [ ] APK 签名正确
- [ ] 崩溃日志可追溯

---

**最后更新**: 2026-08-11 08:35  
**下次更新**: 完成功能测试后

**负责人**: 开发团队  
**协助**: Claude Fable 5

