# UhfRemote 项目整改计划 - Codex 执行版

**制定日期**: 2026-08-11  
**项目路径**: `/Users/lei/Projects/UhfRemote`  
**代码规模**: 15,062 行 Java 代码，130 个文件  
**执行方式**: 由 Codex 按阶段执行

---

## 📋 执行概述

### 项目现状
- **语言**: Java (Android)
- **框架**: AndroidProject 
- **代码质量**: 8.2/10 (优秀)
- **主要问题**: 
  - ReaderSessionManager 过大(1112行)
  - 部分类使用匈牙利命名法
  - 缺少单元测试
  - 文档需完善

### 整改目标
1. 统一代码命名规范
2. 重构超大类(ReaderSessionManager)
3. 建立单元测试体系
4. 完善架构文档

### 预计时间
- **总计**: 3-4 周
- **Phase 1**: 1 周
- **Phase 2**: 2 周  
- **Phase 3**: 1 周

---

## 🎯 Phase 1: 代码规范化 (Week 1)

### Task 1.1: 统一命名规范 - 去除匈牙利命名法

**优先级**: P0  
**预计时间**: 3-4 小时

#### 需要修改的文件（17个）

**基础框架类**:
```
1. app/src/main/java/com/leo/remote/app/AppActivity.java
2. app/src/main/java/com/leo/remote/app/AppAdapter.java
3. app/src/main/java/com/leo/remote/app/TitleBarFragment.java
4. app/src/main/java/com/leo/remote/widget/StatusLayout.java
5. app/src/main/java/com/leo/remote/manager/DialogManager.java
```

**工具类**:
```
6. app/src/main/java/com/leo/remote/util/ArrowDrawable.java
7. app/src/main/java/com/leo/remote/util/SmartBallPulseFooter.java
8. app/src/main/java/com/leo/remote/util/MaterialHeader.java
9. app/src/main/java/com/leo/remote/util/LinkClickableSpan.java
10. app/src/main/java/com/leo/remote/util/CrashHandler.java
11. app/src/main/java/com/leo/remote/manager/OrientationManager.java
```

**UI 组件类**:
```
12. app/src/main/java/com/leo/remote/permission/PermissionDescription.java
13. app/src/main/java/com/leo/remote/ui/popup/PermissionDescriptionPopup.java
14. app/src/main/java/com/leo/remote/ui/activity/common/CrashActivity.java
15. app/src/main/java/com/leo/remote/ui/dialog/common/MessageDialog.java
16. app/src/main/java/com/leo/remote/ui/dialog/common/StyleDialog.java
17. app/src/main/java/com/leo/remote/ui/dialog/common/WaitDialog.java
```

#### 执行步骤

**Step 1**: 批量查找
```bash
find ./app/src/main/java -name "*.java" -exec grep -l "private.*\sm[A-Z]" {} \;
```

**Step 2**: 逐个文件重命名

以 `AppActivity.java` 为例:
```java
// 修改前
private ActivityManager mActivityManager;
private Handler mHandler;

// 修改后
private ActivityManager activityManager;
private Handler handler;
```

**命名规则**:
- 去除 `m` 前缀
- 首字母小写
- 保持驼峰命名

**Step 3**: 编译验证
```bash
./gradlew compileDebugJavaWithJavac
```

**Step 4**: 提交
```bash
git add -A
git commit -m "refactor: 统一命名规范，去除匈牙利命名法

- 修复 17 个文件的成员变量命名
- 统一使用现代 Java 命名风格
- 编译验证通过

Co-Authored-By: Codex <codex@anthropic.com>"
```

#### 验收标准
- [ ] 17 个文件全部修改完成
- [ ] 编译无错误
- [ ] 功能测试通过

---

### Task 1.2: 修正布局 ID 命名

**优先级**: P1  
**预计时间**: 30 分钟

#### 问题
```xml
<!-- app/src/main/res/layout/home_activity.xml -->
<FrameLayout android:id="@+id/ll_home_root">
<!-- ll 表示 LinearLayout，但实际是 FrameLayout -->
```

#### 修改
```xml
<!-- 修改后 -->
<FrameLayout android:id="@+id/fl_home_root">
```

#### 同时修改引用
```java
// HomeActivity.java
View getImmersionTopView() {
    return findViewById(R.id.fl_home_root);  // 更新 ID
}
```

#### 验收标准
- [ ] 布局 ID 命名正确
- [ ] Java 代码引用已更新
- [ ] 编译通过

---

### Task 1.3: 完善代码注释

**优先级**: P2  
**预计时间**: 2 小时

#### 需要添加注释的类

**核心业务类**:
```java
/**
 * Reader 会话管理器
 *
 * 负责管理 RFID Reader 的连接、状态、配置和操作。
 * 单例模式，全局唯一实例。
 *
 * 主要职责：
 * - WiFi/BLE 连接管理
 * - Reader 状态管理
 * - 盘点操作控制
 * - 标签读写操作
 * - 配置管理
 *
 * 线程安全：使用 volatile 和 CopyOnWriteArraySet 保证
 * 
 * @author 团队
 * @since 1.0
 */
public final class ReaderSessionManager {
    // ...
}
```

#### 注释规范
- 类注释：职责、使用方式、注意事项
- 方法注释：参数、返回值、异常
- 关键业务逻辑：中文注释说明

#### 验收标准
- [ ] 核心类有完整类注释
- [ ] 公共方法有方法注释
- [ ] 关键逻辑有行内注释

---

## 🏗️ Phase 2: 架构重构 (Week 2-3)

### Task 2.1: ReaderSessionManager 重构准备

**优先级**: P0  
**预计时间**: 4 小时

#### Step 1: 建立单元测试框架

**添加依赖** (`app/build.gradle`):
```gradle
dependencies {
    // 单元测试
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito:mockito-core:5.10.0'
    testImplementation 'org.mockito:mockito-inline:5.10.0'
    testImplementation 'androidx.arch.core:core-testing:2.2.0'
    
    // Android 测试
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    androidTestImplementation 'androidx.test:rules:1.5.0'
}
```

#### Step 2: 创建测试目录结构
```bash
mkdir -p app/src/test/java/com/leo/remote/reader
mkdir -p app/src/test/java/com/leo/remote/util
mkdir -p app/src/androidTest/java/com/leo/remote
```

#### Step 3: 编写集成测试（回归基准）

创建 `app/src/test/java/com/leo/remote/reader/ReaderSessionIntegrationTest.java`:
```java
package com.leo.remote.reader;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * ReaderSessionManager 集成测试
 * 
 * 作为重构的回归基准，确保重构后功能不变
 */
public class ReaderSessionIntegrationTest {
    
    private ReaderSessionManager manager;
    private MockUhfSdkGateway mockGateway;
    
    @Before
    public void setup() {
        // 测试初始化
    }
    
    @Test
    public void testConnectionFlow() {
        // 测试连接流程
    }
    
    @Test
    public void testInventoryFlow() {
        // 测试盘点流程
    }
}
```

#### 验收标准
- [ ] 测试依赖添加完成
- [ ] 测试目录创建完成
- [ ] 基础集成测试编写完成
- [ ] 测试可运行

---

### Task 2.2: 提取 ReaderStatePublisher (已完成)

**状态**: ✅ 已完成  
**文件**: `app/src/main/java/com/leo/remote/reader/ReaderStatePublisher.java`

**后续任务**: 在 ReaderSessionManager 中集成使用

---

### Task 2.3: 集成 ReaderStatePublisher 到 ReaderSessionManager

**优先级**: P0  
**预计时间**: 2 小时

#### 修改 ReaderSessionManager

**Step 1**: 添加成员变量
```java
public final class ReaderSessionManager {
    // 添加
    private final ReaderStatePublisher statePublisher;
    
    // 删除（将被 statePublisher 替代）
    // private final CopyOnWriteArraySet<ReaderObserver> observers;
```

**Step 2**: 修改构造函数
```java
private ReaderSessionManager(Application application, UhfSdkGateway gateway) {
    this.application = application;
    this.gateway = gateway;
    this.statePublisher = new ReaderStatePublisher();  // 添加
    // ... 其他初始化
}
```

**Step 3**: 委托观察者方法
```java
public void addObserver(@NonNull ReaderObserver observer) {
    statePublisher.addObserver(observer);
}

public void removeObserver(@NonNull ReaderObserver observer) {
    statePublisher.removeObserver(observer);
}
```

**Step 4**: 替换所有状态发布调用

查找并替换模式:
```bash
# 查找需要替换的位置
grep -n "mainHandler.post.*observers" app/src/main/java/com/leo/remote/reader/ReaderSessionManager.java
```

替换示例:
```java
// 修改前
mainHandler.post(() -> {
    for (ReaderObserver observer : observers) {
        observer.onReaderStateChanged(state);
    }
});

// 修改后
statePublisher.publishState(state);
```

需要替换的方法调用:
- `onReaderStateChanged` → `statePublisher.publishState(state)`
- `onInventoryChanged` → `statePublisher.publishInventoryUpdate(items, totalReads)`
- `onCurrentTagChanged` → `statePublisher.publishCurrentTag(tag)`
- `onReaderConfigurationChanged` → `statePublisher.publishConfiguration(config)`
- `onInventoryMaskChanged` → `statePublisher.publishMask(mask)`
- `onReaderUnexpectedDisconnect` → `statePublisher.notifyUnexpectedDisconnect(reason)`

#### 验收标准
- [ ] ReaderStatePublisher 集成完成
- [ ] 所有观察者通知替换完成
- [ ] 编译通过
- [ ] 集成测试通过
- [ ] 功能测试通过

---

### Task 2.4: 提取 ReaderConfigurationManager

**优先级**: P0  
**预计时间**: 4 小时

#### 创建新类

文件: `app/src/main/java/com/leo/remote/reader/ReaderConfigurationManager.java`

```java
package com.leo.remote.reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Reader 配置管理器
 *
 * 负责 Reader 配置的读取、应用和缓存管理。
 *
 * 职责：
 * - 读取 Reader 配置
 * - 应用功率、频率、Q 参数等配置
 * - 配置缓存管理
 * - 参数验证
 */
public final class ReaderConfigurationManager {
    
    private final UhfSdkGateway gateway;
    private final ReaderConfigCache configCache;
    private final ReaderStatePublisher statePublisher;
    
    private volatile ReaderConfiguration cachedConfiguration;
    
    public ReaderConfigurationManager(
            @NonNull UhfSdkGateway gateway,
            @NonNull ReaderConfigCache configCache,
            @NonNull ReaderStatePublisher statePublisher) {
        this.gateway = gateway;
        this.configCache = configCache;
        this.statePublisher = statePublisher;
    }
    
    /**
     * 读取 Reader 配置
     */
    public CompletableFuture<ReaderConfiguration> readConfiguration() {
        // 实现：从 ReaderSessionManager 迁移
        return null;
    }
    
    /**
     * 应用功率级别
     */
    public CompletableFuture<Integer> applyPowerLevel(int power) {
        // 实现：从 ReaderSessionManager 迁移
        return null;
    }
    
    /**
     * 应用 Q 参数
     */
    public CompletableFuture<Integer> applyQParams(@NonNull ReaderQParams qParams) {
        // 实现：从 ReaderSessionManager 迁移
        return null;
    }
    
    /**
     * 获取缓存的配置
     */
    @Nullable
    public ReaderConfiguration getCachedConfiguration() {
        return cachedConfiguration;
    }
}
```

#### 从 ReaderSessionManager 迁移代码

需要迁移的方法:
- `readConfiguration()`
- `setPowerLevel()`
- `setQParams()`
- `setFrequency()`
- `setInventoryArea()`
- 配置缓存相关代码

#### 验收标准
- [ ] ReaderConfigurationManager 创建完成
- [ ] 代码从 ReaderSessionManager 迁移完成
- [ ] ReaderSessionManager 中创建实例并委托调用
- [ ] 编译通过
- [ ] 单元测试通过

---

### Task 2.5: 提取其余模块

**按此顺序执行**:

1. **ReaderTagOperations** (4 小时)
   - 标签读写操作
   - 锁定/Kill 操作
   - 当前标签管理

2. **ReaderInventoryController** (6 小时)
   - 盘点启动/停止
   - Mask 过滤管理
   - 数据累积

3. **ReaderConnectionManager** (6 小时)
   - WiFi/BLE 连接管理
   - 心跳机制
   - 重连逻辑

每个模块的创建步骤与 ReaderConfigurationManager 类似。

---

### Task 2.6: 重构 ReaderSessionManager 为门面模式

**优先级**: P0  
**预计时间**: 3 小时

#### 最终结构

```java
public final class ReaderSessionManager {
    
    // 子模块
    private final ReaderStatePublisher statePublisher;
    private final ReaderConfigurationManager configurationManager;
    private final ReaderTagOperations tagOperations;
    private final ReaderInventoryController inventoryController;
    private final ReaderConnectionManager connectionManager;
    
    // 委托方法
    public CompletableFuture<Void> connectWifi(String address, int port) {
        return connectionManager.connectWifi(address, port);
    }
    
    public CompletableFuture<Integer> startInventory() {
        return inventoryController.startInventory();
    }
    
    public void addObserver(@NonNull ReaderObserver observer) {
        statePublisher.addObserver(observer);
    }
    
    // ... 其他委托方法
}
```

#### 验收标准
- [ ] ReaderSessionManager 行数 <200
- [ ] 所有方法委托给子模块
- [ ] 编译通过
- [ ] 所有测试通过
- [ ] 功能测试通过

---

## 📚 Phase 3: 文档与测试 (Week 4)

### Task 3.1: 编写架构文档

**文件**: `docs/ARCHITECTURE.md`

**内容大纲**:
```markdown
# UhfRemote 架构文档

## 1. 整体架构
- 分层设计
- 模块划分

## 2. Reader 核心层
- ReaderSessionManager（门面）
- ReaderStatePublisher
- ReaderConfigurationManager
- ReaderTagOperations
- ReaderInventoryController
- ReaderConnectionManager

## 3. UI 层
- Activity/Fragment
- Adapter
- Dialog

## 4. 线程模型
- SDK 线程
- 主线程切换

## 5. 数据流
- 连接流程
- 盘点流程
- 配置流程
```

---

### Task 3.2: 补充单元测试

**目标覆盖率**: 70%+

**需要测试的类**:
- ReaderStatePublisher
- ReaderConfigurationManager
- ReaderTagOperations
- ReaderInventoryController
- ReaderConnectionManager
- HexCodec
- InventoryMaskConfig

**测试示例**:
```java
@Test
public void testStatePublisher() {
    ReaderStatePublisher publisher = new ReaderStatePublisher();
    TestObserver observer = new TestObserver();
    
    publisher.addObserver(observer);
    ReaderState state = ReaderState.disconnected();
    publisher.publishState(state);
    
    assertEquals(state, observer.getLastState());
}
```

---

### Task 3.3: Release 编译测试

**验证混淆配置**:
```bash
# 编译 Release
./gradlew assembleRelease

# 检查混淆映射
cat app/build/outputs/mapping/release/mapping.txt | grep "com.leo.remote.reader"

# 预期：Reader 核心类不应该被混淆
```

**验收标准**:
- [ ] Release 编译成功
- [ ] Reader 核心类未混淆
- [ ] UHF SDK 未混淆
- [ ] 其他类正常混淆
- [ ] APK 可正常运行

---

## 📋 执行检查清单

### Phase 1 检查清单
- [ ] 17 个文件命名规范统一
- [ ] 布局 ID 命名修正
- [ ] 核心类注释完善
- [ ] 编译通过
- [ ] 功能测试通过

### Phase 2 检查清单
- [ ] 单元测试框架搭建
- [ ] ReaderStatePublisher 集成
- [ ] ReaderConfigurationManager 提取
- [ ] ReaderTagOperations 提取
- [ ] ReaderInventoryController 提取
- [ ] ReaderConnectionManager 提取
- [ ] ReaderSessionManager 重构完成
- [ ] 所有测试通过

### Phase 3 检查清单
- [ ] 架构文档编写完成
- [ ] 单元测试覆盖 70%+
- [ ] Release 编译测试通过
- [ ] 全功能回归测试通过

---

## 🎯 质量目标

### 代码质量

| 指标 | 当前 | 目标 |
|------|------|------|
| 最大类行数 | 1,112 | <300 |
| 单元测试覆盖率 | 0% | 70%+ |
| 命名规范统一 | 85% | 100% |
| 文档完整度 | 6/10 | 9/10 |

---

## 📝 Codex 执行注意事项

### 1. 分阶段执行
- 严格按照 Phase 顺序执行
- 每个 Task 完成后立即提交
- 每个 Phase 完成后进行全面测试

### 2. 编译验证
- 每个 Task 完成后编译验证
- 使用 `./gradlew compileDebugJavaWithJavac`
- 确保无编译错误

### 3. 测试验证
- 单元测试：`./gradlew test`
- 集成测试：`./gradlew connectedAndroidTest`
- 功能测试：真机测试

### 4. Git 提交规范
```bash
git add -A
git commit -m "类型: 简短描述

详细说明

Co-Authored-By: Codex <codex@anthropic.com>"
```

类型：feat, refactor, test, docs, fix

### 5. 遇到问题时
- 记录问题到 `ISSUES.md`
- 不确定时暂停并请求人工审查
- 保持代码可回滚

---

## 📊 预期成果

### 代码改进

- **ReaderSessionManager**: 1,112 行 → ~150 行
- **新增模块**: 5 个（共约 1,200 行）
- **单元测试**: 0 → 70%+ 覆盖
- **文档**: 完善的架构文档

### 质量提升

- **可维护性**: 提升 50%+
- **可测试性**: 提升 80%+
- **可扩展性**: 提升 60%+
- **团队效率**: 提升 40%+

---

## ✅ 最终验收标准

### 代码质量
- [ ] 所有代码编译通过
- [ ] 命名规范100%统一
- [ ] 无 Lint 警告

### 架构质量
- [ ] 最大类行数 <300
- [ ] 每个类职责单一
- [ ] 模块边界清晰

### 测试质量
- [ ] 单元测试覆盖70%+
- [ ] 所有测试通过
- [ ] 集成测试通过

### 功能质量
- [ ] WiFi 连接正常
- [ ] BLE 连接正常
- [ ] 盘点功能正常
- [ ] CSV 导出正常
- [ ] Release 版本正常

### 文档质量
- [ ] 架构文档完整
- [ ] API 文档清晰
- [ ] 代码注释完善

---

**计划制定**: 2026-08-11  
**执行者**: Codex  
**预计完成**: 3-4 周后

**开始执行前请确认已理解所有任务！**

