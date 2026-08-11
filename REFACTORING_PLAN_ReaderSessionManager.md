# ReaderSessionManager 重构方案

**日期**: 2026-08-10  
**当前状态**: 1112 行，职责过多  
**目标**: 拆分为职责单一的类，提升可维护性和可测试性

---

## 📊 现状分析

### 当前问题

**ReaderSessionManager.java** (1112 行)

**承担的职责**:
1. ✅ 连接管理（BLE + WiFi）
2. ✅ 状态管理与发布
3. ✅ 观察者模式通知
4. ✅ 盘点操作控制
5. ✅ 标签读写操作
6. ✅ 配置管理与缓存
7. ✅ Mask 过滤管理
8. ✅ 前台服务生命周期
9. ✅ 心跳机制
10. ✅ 错误处理与重连

**违反的原则**:
- ❌ 单一职责原则 (SRP) - 一个类承担了 10+ 个职责
- ❌ 开闭原则 (OCP) - 难以扩展新功能
- ⚠️ 可测试性差 - 1112 行代码难以单元测试

---

## 🎯 重构目标

### 拆分后的架构

```
ReaderSessionManager (原有)
├── ReaderConnectionManager     // 连接管理
├── ReaderStatePublisher        // 状态发布
├── ReaderInventoryController   // 盘点控制
├── ReaderTagOperations         // 标签操作
└── ReaderConfigurationManager  // 配置管理
```

### 预期效果

1. **每个类不超过 300 行**
2. **职责清晰单一**
3. **易于单元测试**
4. **便于扩展新功能**

---

## 📦 拆分方案

### 1. ReaderConnectionManager (连接管理)

**职责**:
- WiFi 连接建立与断开
- BLE 连接建立与断开
- 连接状态监控
- 心跳机制
- 重连逻辑

**核心方法**:
```java
public final class ReaderConnectionManager {
    
    // 连接方法
    CompletableFuture<Void> connectWifi(String address, int port);
    CompletableFuture<Void> connectBle(Device device);
    CompletableFuture<Void> disconnect();
    
    // 状态查询
    boolean isConnected();
    TransportType getTransportType();
    
    // 心跳管理
    void startHeartbeat();
    void stopHeartbeat();
    
    // 监听器
    void setConnectionListener(ConnectionListener listener);
}
```

**依赖**:
- UhfSdkGateway - SDK 调用
- BleTransport - BLE 传输
- WifiNetworkMonitor - WiFi 监控

**预计行数**: 200-250 行

---

### 2. ReaderStatePublisher (状态发布)

**职责**:
- 管理观察者列表
- 状态变更通知
- 主线程切换
- 状态历史记录（可选）

**核心方法**:
```java
public final class ReaderStatePublisher {
    
    // 观察者管理
    void addObserver(ReaderObserver observer);
    void removeObserver(ReaderObserver observer);
    
    // 状态发布
    void publishState(ReaderState state);
    void publishInventoryUpdate(List<InventoryItem> items, int totalReads);
    void publishCurrentTag(ReaderTag tag);
    void publishConfiguration(ReaderConfiguration config);
    void publishMask(InventoryMaskConfig mask);
    
    // 特殊事件
    void notifyUnexpectedDisconnect(DisconnectReason reason);
}
```

**依赖**:
- Handler - 主线程切换
- CopyOnWriteArraySet - 线程安全集合

**预计行数**: 150-200 行

---

### 3. ReaderInventoryController (盘点控制)

**职责**:
- 盘点启动与停止
- 盘点数据累积
- Mask 过滤应用
- 盘点模式切换

**核心方法**:
```java
public final class ReaderInventoryController {
    
    // 盘点控制
    CompletableFuture<Integer> startInventory();
    CompletableFuture<Integer> stopInventory();
    boolean isInventoryRunning();
    
    // Mask 管理
    CompletableFuture<Integer> setInventoryMask(InventoryMaskConfig mask);
    CompletableFuture<Integer> clearInventoryMask();
    InventoryMaskConfig getCurrentMask();
    
    // 模式管理
    void setInventoryMode(int mode);
    int getInventoryMode();
    
    // 数据访问
    InventoryAccumulator getAccumulator();
}
```

**依赖**:
- UhfSdkGateway - SDK 调用
- InventoryAccumulator - 数据累积
- ReaderStatePublisher - 状态通知

**预计行数**: 250-300 行

---

### 4. ReaderTagOperations (标签操作)

**职责**:
- 单标签读取
- 单标签写入
- 标签锁定/解锁
- 标签 Kill

**核心方法**:
```java
public final class ReaderTagOperations {
    
    // 读操作
    CompletableFuture<byte[]> readTag(int length, int address, int bank, byte[] password);
    CompletableFuture<ReaderTag> readMultipleBanks();
    
    // 写操作
    CompletableFuture<Integer> writeTag(byte[] data, int address, int bank, byte[] password);
    
    // 锁操作
    CompletableFuture<Integer> lockTag(int bank, byte[] password);
    CompletableFuture<Integer> killTag(byte[] password);
    
    // 当前标签
    void setCurrentTag(ReaderTag tag);
    ReaderTag getCurrentTag();
}
```

**依赖**:
- UhfSdkGateway - SDK 调用
- ReaderStatePublisher - 状态通知

**预计行数**: 200-250 行

---

### 5. ReaderConfigurationManager (配置管理)

**职责**:
- Reader 配置读取与应用
- 配置缓存管理
- 参数验证

**核心方法**:
```java
public final class ReaderConfigurationManager {
    
    // 配置读取
    CompletableFuture<ReaderConfiguration> readConfiguration();
    ReaderConfiguration getCachedConfiguration();
    
    // 配置应用
    CompletableFuture<Integer> applyPowerLevel(int power);
    CompletableFuture<Integer> applyQParams(ReaderQParams qParams);
    CompletableFuture<Integer> applyFrequency(int frequency);
    CompletableFuture<Integer> applyInventoryArea(InventoryArea area);
    
    // 缓存管理
    void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration config);
    ReaderConfiguration loadCachedConfiguration(ModuleSubtype subtype);
}
```

**依赖**:
- UhfSdkGateway - SDK 调用
- ReaderConfigCache - 缓存存储
- ReaderStatePublisher - 状态通知

**预计行数**: 200-250 行

---

### 6. ReaderSessionManager (重构后)

**新职责**:
- 作为门面模式（Facade），统一对外接口
- 管理各子模块生命周期
- 协调模块间交互

**核心方法**:
```java
public final class ReaderSessionManager {
    
    private final ReaderConnectionManager connectionManager;
    private final ReaderStatePublisher statePublisher;
    private final ReaderInventoryController inventoryController;
    private final ReaderTagOperations tagOperations;
    private final ReaderConfigurationManager configurationManager;
    
    // 委托给子模块
    public CompletableFuture<Void> connectWifi(String address, int port) {
        return connectionManager.connectWifi(address, port);
    }
    
    public CompletableFuture<Integer> startInventory() {
        return inventoryController.startInventory();
    }
    
    public void addObserver(ReaderObserver observer) {
        statePublisher.addObserver(observer);
    }
    
    // ... 其他委托方法
    
    // 单例模式保持不变
    public static ReaderSessionManager getInstance(Application app) {
        // ...
    }
}
```

**预计行数**: 150-200 行

---

## 🔄 重构步骤

### Phase 1: 准备阶段 (1 天)

1. ✅ 编写重构方案文档
2. ⏳ 创建单元测试框架
3. ⏳ 为现有代码编写集成测试（作为回归测试基准）
4. ⏳ 备份当前代码

### Phase 2: 提取 ReaderStatePublisher (0.5 天)

**步骤**:
1. 创建 `ReaderStatePublisher` 类
2. 移动观察者管理相关代码
3. 移动状态发布相关方法
4. 在 `ReaderSessionManager` 中使用新类
5. 运行测试验证

**风险**: 低 - 纯粹的代码移动

### Phase 3: 提取 ReaderConfigurationManager (0.5 天)

**步骤**:
1. 创建 `ReaderConfigurationManager` 类
2. 移动配置读取和应用代码
3. 移动缓存管理代码
4. 更新 `ReaderSessionManager` 引用
5. 运行测试验证

**风险**: 低

### Phase 4: 提取 ReaderTagOperations (0.5 天)

**步骤**:
1. 创建 `ReaderTagOperations` 类
2. 移动标签读写操作代码
3. 移动当前标签管理代码
4. 更新 `ReaderSessionManager` 引用
5. 运行测试验证

**风险**: 低

### Phase 5: 提取 ReaderInventoryController (1 天)

**步骤**:
1. 创建 `ReaderInventoryController` 类
2. 移动盘点控制代码
3. 移动 Mask 管理代码
4. 处理与其他模块的交互
5. 运行测试验证

**风险**: 中 - 涉及较多交互逻辑

### Phase 6: 提取 ReaderConnectionManager (1 天)

**步骤**:
1. 创建 `ReaderConnectionManager` 类
2. 移动连接建立/断开代码
3. 移动心跳和重连逻辑
4. 处理生命周期管理
5. 运行测试验证

**风险**: 中 - 涉及复杂的连接状态管理

### Phase 7: 重构 ReaderSessionManager (0.5 天)

**步骤**:
1. 重写为门面模式
2. 添加委托方法
3. 保持向后兼容的 API
4. 更新文档注释
5. 最终集成测试

**风险**: 低

### Phase 8: 优化与清理 (0.5 天)

**步骤**:
1. 移除冗余代码
2. 优化模块间接口
3. 完善单元测试
4. 更新文档
5. Code Review

**总耗时**: 约 5 天

---

## 🧪 测试策略

### 单元测试

**每个模块需要测试**:

```java
// ReaderConnectionManager 测试
@Test
public void testWifiConnection() {
    // Given
    String address = "192.168.1.100";
    int port = 1200;
    
    // When
    CompletableFuture<Void> future = connectionManager.connectWifi(address, port);
    
    // Then
    assertNotNull(future);
    assertTrue(connectionManager.isConnected());
}

// ReaderInventoryController 测试
@Test
public void testStartInventory() {
    // Given
    when(gateway.startInventory(anyInt(), anyInt())).thenReturn(0);
    
    // When
    CompletableFuture<Integer> future = inventoryController.startInventory();
    
    // Then
    assertEquals(0, future.get());
    assertTrue(inventoryController.isInventoryRunning());
}
```

### 集成测试

**端到端流程测试**:

```java
@Test
public void testCompleteInventoryFlow() {
    // 1. 连接
    sessionManager.connectWifi("192.168.1.100", 1200).get();
    
    // 2. 启动盘点
    sessionManager.startInventory().get();
    Thread.sleep(5000);
    
    // 3. 停止盘点
    sessionManager.stopInventory().get();
    
    // 4. 验证数据
    assertTrue(inventoryController.getAccumulator().getCount() > 0);
}
```

---

## 📊 预期收益

### 代码质量

| 指标 | 重构前 | 重构后 | 改善 |
|------|--------|--------|------|
| 最大类行数 | 1,112 | ~250 | ⬇️ 78% |
| 类的职责数 | 10+ | 1-2 | ⬇️ 80% |
| 圈复杂度 | 高 | 低 | ⬇️ 60% |
| 单元测试覆盖 | 0% | 70%+ | ⬆️ 70% |

### 可维护性

- ✅ 每个模块职责清晰
- ✅ 修改影响范围小
- ✅ 易于理解和上手
- ✅ 便于并行开发

### 可扩展性

- ✅ 新增连接方式（如 USB）容易
- ✅ 新增标签操作容易
- ✅ 新增配置项容易

### 可测试性

- ✅ 可独立测试每个模块
- ✅ 可 Mock 依赖进行测试
- ✅ 测试用例编写简单

---

## ⚠️ 风险与应对

### 风险 1: API 兼容性破坏

**风险级别**: 中

**应对**:
- 保持外部 API 不变
- 内部实现委托给新模块
- 添加 @Deprecated 标记过时方法

### 风险 2: 性能下降

**风险级别**: 低

**应对**:
- 模块间调用开销很小
- 使用性能测试验证
- 必要时内联关键路径

### 风险 3: 引入新 Bug

**风险级别**: 中

**应对**:
- 编写完善的单元测试
- 保留集成测试作为回归基准
- 分阶段重构，每步验证

### 风险 4: 开发时间超期

**风险级别**: 中

**应对**:
- 严格按照 Phase 推进
- 每个 Phase 完成后立即测试
- 遇到问题及时调整方案

---

## 📝 检查清单

### 重构前检查

- [ ] 现有功能都有集成测试覆盖
- [ ] 代码已备份（Git 分支）
- [ ] 团队成员已知晓重构计划
- [ ] 单元测试框架已搭建

### 重构中检查

- [ ] Phase 1: ReaderStatePublisher 提取完成
- [ ] Phase 2: ReaderConfigurationManager 提取完成
- [ ] Phase 3: ReaderTagOperations 提取完成
- [ ] Phase 4: ReaderInventoryController 提取完成
- [ ] Phase 5: ReaderConnectionManager 提取完成
- [ ] Phase 6: ReaderSessionManager 重构完成
- [ ] Phase 7: 优化与清理完成

### 重构后检查

- [ ] 所有单元测试通过
- [ ] 所有集成测试通过
- [ ] 功能测试通过（真机验证）
- [ ] 代码 Review 完成
- [ ] 文档更新完成
- [ ] 性能对比无明显下降

---

## 📚 参考资料

- [Refactoring: Improving the Design of Existing Code](https://martinfowler.com/books/refactoring.html)
- [Clean Code](https://www.oreilly.com/library/view/clean-code-a/9780136083238/)
- [SOLID 原则](https://en.wikipedia.org/wiki/SOLID)
- [门面模式 (Facade Pattern)](https://refactoring.guru/design-patterns/facade)

---

**方案制定**: 2026-08-10  
**计划开始**: 待定  
**预计完成**: 开始后 5 个工作日

**制定人员**: Claude Fable 5

