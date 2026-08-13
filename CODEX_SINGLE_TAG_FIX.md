# 单标签页读取功能修复计划

## 📋 执行摘要

**目标**：修复单标签页面的读取功能，使其正确应用单标签掩码。

**核心问题**：
- `readSingleTag()` 未应用单标签掩码，导致多标签环境下无法精确定位
- 错误提示不够友好，用户无法判断失败原因

**背景**：
- 写入/锁定/销毁功能通过 `withTargetMask()` 包装器正确应用掩码
- 唯独读取功能直接调用 `inventoryOnce`，未应用掩码
- 协议联动监听器工作正常，UI 已完善

**预计时间**：2-3 小时

---

## 🎯 问题分析

### 1. 当前实现的问题

**文件**：`ReaderTagOperations.java:44-49`

```java
@Nullable
ReaderTag readSingleTag() throws ReaderException {
    ReaderTag tag = gateway.inventoryOnce(1500);  // ❌ 未应用掩码
    currentTag = tag;
    publisher.publishCurrentTag(tag);
    return tag;
}
```

**问题**：
1. 直接调用 `gateway.inventoryOnce()`，未应用 `singleTagMask`
2. 多标签环境下随机读取一个标签
3. 有掩码配置时也不生效

### 2. 正确的实现模式

**参考**：`ReaderSessionCoordinator.withTargetMask()` (625-673行)

```java
private <T> CompletableFuture<T> withTargetMask(Callable<T> operation) {
    return submitConnected(() -> {
        // 1. 停止盘点
        stopInventoryInternal();
        
        // 2. 保存并清除盘点掩码
        InventoryMaskConfig maskToRestore = inventoryController.getMask();
        if (maskToRestore != null) {
            gateway.clearInventoryMask(...);
        }
        
        // 3. 应用单标签掩码
        InventoryMaskConfig activeMask = tagOperations.getSingleTagMask();
        if (activeMask != null) {
            gateway.applyInventoryMask(..., activeMask);
        }
        
        try {
            // 4. 执行操作
            return operation.call();
        } finally {
            // 5. 恢复掩码
            if (maskToRestore != null) {
                gateway.applyInventoryMask(..., maskToRestore);
            }
        }
    });
}
```

**关键流程**：
- ✅ 停止盘点
- ✅ 保存现有掩码
- ✅ 应用单标签掩码
- ✅ 执行操作
- ✅ 恢复掩码

---

## 📝 详细执行步骤

### Task 1: 在 ReaderSessionCoordinator 中实现 readSingleTag

**文件**：`app/src/main/java/com/leo/remote/reader/ReaderSessionCoordinator.java`

**操作**：在公开 API 区域（约第 158 行附近）添加新方法

**插入位置**：在 `getSingleTagMask()` 方法之后

**完整代码**：
```java
/**
 * 读取单个标签，如果配置了单标签掩码则应用掩码。
 *
 * <p>该方法会：
 * <ol>
 *   <li>停止当前盘点</li>
 *   <li>保存并清除盘点掩码</li>
 *   <li>应用单标签掩码（如果存在）</li>
 *   <li>执行 inventoryOnce 读取标签</li>
 *   <li>恢复原有掩码状态</li>
 * </ol>
 *
 * @return 读取到的标签，超时或未读取到返回 null
 * @throws ReaderException 读取失败时抛出异常
 */
public CompletableFuture<ReaderTag> readSingleTag() {
    return submitConnected(() -> {
        // 1. 停止盘点
        int status = stopInventoryInternal();
        if (status != 0) { 
            Log.w(TAG, "Unable to stop inventory before readSingleTag, status=" + status);
        }
        
        // 2. 获取当前状态和单标签掩码
        InventoryMaskConfig activeMask = tagOperations.getSingleTagMask();
        TagProtocol protocol = currentState().getProtocol();
        ModuleSubtype subtype = currentState().getModuleSubtype();
        
        // 3. 保存并清除盘点掩码
        InventoryMaskConfig inventoryMask = inventoryController.getMask();
        boolean inventoryMaskWasApplied = false;
        if (inventoryMask != null) {
            int clearStatus = gateway.clearInventoryMask(protocol, subtype, 
                    inventoryMaskRestoreValue());
            if (clearStatus == 0) {
                inventoryController.setMaskApplied(false);
                inventoryMaskWasApplied = true;
            } else {
                Log.w(TAG, "Failed to clear inventory mask before readSingleTag, status=" 
                        + clearStatus);
            }
        }
        
        int selectedBeforeMask = 0;
        boolean singleMaskApplied = false;
        
        try {
            // 4. 应用单标签掩码
            if (activeMask != null) {
                selectedBeforeMask = inventoryMask == null 
                    ? readSelectedForTemporaryMask(subtype)
                    : inventoryMaskRestoreValue();
                
                status = gateway.applyInventoryMask(protocol, subtype, activeMask);
                if (status != 0) {
                    throw new ReaderException("Unable to apply single-tag mask", status);
                }
                singleMaskApplied = true;
                Log.d(TAG, "Applied single-tag mask for readSingleTag: " 
                        + activeMask.toDisplayString());
            }
            
            // 5. 执行读取操作
            ReaderTag tag = tagOperations.readSingleTag();
            
            if (tag != null) {
                Log.d(TAG, "Read single tag: " + tag.id);
            } else {
                Log.d(TAG, "No tag read within timeout");
            }
            
            return tag;
            
        } finally {
            // 6. 恢复掩码状态
            if (inventoryMaskWasApplied && inventoryMask != null) {
                int restoreStatus = gateway.applyInventoryMask(protocol, subtype, inventoryMask);
                if (restoreStatus == 0) {
                    inventoryController.setMaskApplied(true);
                    Log.d(TAG, "Restored inventory mask after readSingleTag");
                } else {
                    Log.w(TAG, "Failed to restore inventory mask, status=" + restoreStatus);
                }
            } else if (singleMaskApplied) {
                int clearStatus = gateway.clearTargetMask(protocol, subtype, selectedBeforeMask);
                if (clearStatus == 0) {
                    Log.d(TAG, "Cleared single-tag mask after readSingleTag");
                } else {
                    Log.w(TAG, "Failed to clear single-tag mask, status=" + clearStatus);
                }
            }
        }
    });
}
```

**关键点**：
1. ✅ 完整的掩码管理流程
2. ✅ 详细的日志记录便于调试
3. ✅ 异常安全的 finally 块
4. ✅ 与 `withTargetMask()` 一致的逻辑

---

### Task 2: 在 ReaderSessionManager 中暴露方法

**文件**：`app/src/main/java/com/leo/remote/reader/ReaderSessionManager.java`

**操作**：在公开 API 区域（约第 94 行附近）添加新方法

**插入位置**：在 `getSingleTagMask()` 方法之后

**完整代码**：
```java
/**
 * 读取单个标签。
 *
 * <p>如果配置了单标签掩码，将自动应用掩码以精确定位目标标签。
 * 读取完成后自动恢复原有的盘点掩码状态。
 *
 * @return 读取到的标签，超时或未读取到返回 null
 */
public CompletableFuture<ReaderTag> readSingleTag() {
    return coordinator.readSingleTag();
}
```

---

### Task 3: 增强 SingleTagFragment 的错误提示

**文件**：`app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java`

**操作**：修改 `readTag()` 方法（第 199-211 行）

**修改前**：
```java
private void readTag() {
    if (session == null || !session.getState().isConnected()) {
        requireReaderOnline();
        return;
    }
    readButton.setEnabled(false);
    readButton.setText(R.string.single_reading);
    session.readSingleTag().whenComplete((tag, error) -> {
        readButton.setEnabled(true);
        readButton.setText(R.string.single_read_tag);
        if (error != null) { 
            toast(getString(R.string.single_read_failed, rootMessage(error))); 
        }
    });
}
```

**修改后**：
```java
private void readTag() {
    if (session == null || !session.getState().isConnected()) {
        requireReaderOnline();
        return;
    }
    
    readButton.setEnabled(false);
    readButton.setText(R.string.single_reading);
    
    session.readSingleTag().whenComplete((tag, error) -> requireActivity().runOnUiThread(() -> {
        readButton.setEnabled(true);
        readButton.setText(R.string.single_read_tag);
        
        if (error != null) {
            String message = rootMessage(error);
            // 区分超时和其他错误，提供具体的排查建议
            if (message.contains("timeout") || message.contains("超时")) {
                toast(getString(R.string.single_read_timeout_hint));
            } else {
                toast(getString(R.string.single_read_failed, message));
            }
        } else if (tag != null) {
            // 增加成功提示，让用户明确知道操作完成
            toast(getString(R.string.single_read_success, tag.id.substring(0, 
                    Math.min(16, tag.id.length())) + "..."));
        } else {
            // inventoryOnce 返回 null 的情况
            toast(getString(R.string.single_read_no_tag));
        }
    }));
}
```

**改进点**：
1. ✅ 显式调用 `runOnUiThread` 确保在主线程更新 UI
2. ✅ 区分超时、错误、无标签三种情况
3. ✅ 超时时提供具体的排查建议
4. ✅ 成功时显示读取到的 EPC（截取前 16 位）
5. ✅ 所有分支都有明确的用户反馈

---

### Task 4: 更新字符串资源

**文件**：`app/src/main/res/values/strings.xml`

**操作**：在 Single Tag 相关字符串区域添加新字符串

**插入位置**：在现有 `single_read_failed` 附近

**新增字符串**：
```xml
<!-- 单标签页 - 读取反馈 -->
<string name="single_read_success">读取成功：%s</string>
<string name="single_read_no_tag">未读取到标签</string>
<string name="single_read_timeout_hint">未读取到标签，请检查：\n1. 标签是否在天线感应范围内\n2. 单标签掩码设置是否正确\n3. 射频功率是否足够</string>
```

**说明**：
- `%s` 占位符用于显示 EPC 前缀
- 分行提示用 `\n` 换行符
- 提供 3 个常见排查方向

---

## ✅ 验证清单

### 编译验证

```bash
cd /Users/lei/Projects/UhfRemote
./gradlew assembleDebug
```

**预期结果**：编译成功，无错误

### 功能测试

#### 测试场景 1：无掩码读取单标签

**前提**：
- 连接 UHF 读写器
- 单标签掩码未启用
- 天线范围内有 1 个标签

**步骤**：
1. 点击"读取标签"按钮
2. 观察按钮状态变化
3. 查看标签信息卡片更新

**预期结果**：
- ✅ 按钮显示"读取中..."并禁用
- ✅ 1.5 秒内读取成功
- ✅ 显示 toast："读取成功：E280116060000000..."
- ✅ 标签卡片显示完整信息（EPC、TID、芯片、RSSI）
- ✅ 按钮恢复为"读取标签"并启用

#### 测试场景 2：有掩码读取指定标签

**前提**：
- 连接 UHF 读写器
- 配置单标签掩码（EPC 区域，偏移 32 位，掩码数据 = 目标标签的 EPC）
- 启用掩码
- 天线范围内有多个标签（包括目标标签）

**步骤**：
1. 点击"读取标签"按钮
2. 观察读取结果

**预期结果**：
- ✅ 读取成功，且读取到的是掩码匹配的目标标签
- ✅ 不会随机读取其他标签
- ✅ 标签卡片显示正确的 EPC（与掩码匹配）

#### 测试场景 3：多标签环境无掩码

**前提**：
- 连接 UHF 读写器
- 单标签掩码未启用
- 天线范围内有多个标签

**步骤**：
1. 多次点击"读取标签"
2. 观察每次读取的结果

**预期结果**：
- ✅ 每次读取成功
- ✅ 可能读取到不同的标签（随机）
- ✅ 提示读取成功并显示对应的 EPC

#### 测试场景 4：读取超时

**前提**：
- 连接 UHF 读写器
- 天线范围内无标签（或标签信号太弱）

**步骤**：
1. 点击"读取标签"
2. 等待超时

**预期结果**：
- ✅ 1.5 秒后返回
- ✅ 显示 toast："未读取到标签，请检查：\n1. 标签是否在天线感应范围内\n2. 单标签掩码设置是否正确\n3. 射频功率是否足够"
- ✅ 按钮恢复正常

#### 测试场景 5：协议切换后读取

**前提**：
- 连接 UHF 读写器
- 天线范围内有标签

**步骤**：
1. 在配置页切换射频协议（ISO 18000-6C → GB29768）
2. 返回单标签页
3. 点击"读取标签"

**预期结果**：
- ✅ 掩码 Bank 选项已自动更新（EPC / 用户 / 参数 / 用户子区）
- ✅ 读取成功
- ✅ 标签信息正确显示

#### 测试场景 6：读取后写入

**前提**：
- 成功读取到一个标签
- 配置单标签掩码（基于读取到的 EPC）
- 启用掩码

**步骤**：
1. 点击"写入数据"
2. 填写参数并执行写入
3. 再次点击"读取标签"

**预期结果**：
- ✅ 读取到的是同一个标签
- ✅ 数据区显示新写入的内容

---

## 📊 影响范围

### 修改文件

| 文件 | 行数变化 | 说明 |
|------|---------|------|
| ReaderSessionCoordinator.java | +85 行 | 新增 readSingleTag 方法 |
| ReaderSessionManager.java | +10 行 | 暴露 readSingleTag API |
| SingleTagFragment.java | ~20 行修改 | 增强错误提示逻辑 |
| strings.xml | +3 行 | 新增错误提示字符串 |

### 不受影响

- ✅ ReaderTagOperations.java - 保持不变（底层实现正确）
- ✅ 单标签掩码 UI - 保持不变（已完善）
- ✅ 协议监听器 - 保持不变（工作正常）
- ✅ 写入/锁定/销毁功能 - 保持不变（已正确使用 withTargetMask）

---

## 🎯 技术要点

### 1. 为什么在 ReaderSessionCoordinator 而不是 ReaderTagOperations？

**ReaderTagOperations 职责**：
- 封装单个标签操作的 SDK 调用
- 不管理掩码状态
- 不管理盘点状态

**ReaderSessionCoordinator 职责**：
- 协调复杂的会话操作
- 管理掩码切换
- 管理盘点状态

**结论**：掩码应用属于会话级别的协调工作，应在 Coordinator 层实现。

### 2. 掩码应用流程

```
读取前状态检查
├── 停止盘点
├── 保存盘点掩码
└── 清除盘点掩码

应用单标签掩码
├── 读取 selected 寄存器值（用于恢复）
└── 调用 applyInventoryMask

执行读取操作
└── inventoryOnce(1500)

恢复掩码状态
├── 如果有盘点掩码：重新应用
└── 如果无盘点掩码：清除单标签掩码并恢复 selected
```

### 3. 与 withTargetMask 的对比

| 特性 | withTargetMask | readSingleTag |
|------|----------------|---------------|
| 用途 | 通用掩码包装器 | 专用读取方法 |
| 参数 | Callable<T> | 无（固定调用 inventoryOnce） |
| 返回 | CompletableFuture<T> | CompletableFuture<ReaderTag> |
| 掩码管理 | ✅ 完整 | ✅ 完整 |
| 日志记录 | 较少 | 详细 |

**为什么不直接使用 withTargetMask？**
- `withTargetMask` 是私有方法，设计为内部工具
- `readSingleTag` 提供更明确的语义和更详细的日志
- 未来可能需要在读取前后添加特定逻辑

---

## 🚀 执行顺序

### Phase 1: 核心修复（1.5 小时）

1. **添加 ReaderSessionCoordinator.readSingleTag()**（30 分钟）
   - 复制 withTargetMask 的掩码管理逻辑
   - 调整为读取操作的专用版本
   - 添加详细日志

2. **添加 ReaderSessionManager.readSingleTag()**（5 分钟）
   - 简单的转发方法

3. **修改 SingleTagFragment.readTag()**（15 分钟）
   - 增强错误处理
   - 添加成功提示

4. **更新 strings.xml**（5 分钟）
   - 添加 3 个新字符串

5. **编译验证**（5 分钟）
   - 运行 assembleDebug

### Phase 2: 功能测试（1 小时）

6. **基础测试**（20 分钟）
   - 无掩码读取
   - 有掩码读取
   - 超时测试

7. **协议测试**（20 分钟）
   - 切换协议后读取
   - 不同协议的掩码应用

8. **集成测试**（20 分钟）
   - 读取 → 写入 → 读取验证
   - 读取 → 锁定 → 写入测试

### Phase 3: 提交（10 分钟）

9. **Git 提交**
   - 提交代码变更

---

## 📌 注意事项

### 1. 线程安全

**问题**：`readSingleTag()` 涉及多步 SDK 调用
**解决**：通过 `submitConnected()` 确保在 SDK 线程串行执行

### 2. 掩码恢复

**问题**：操作失败时掩码状态可能不一致
**解决**：使用 `try-finally` 确保恢复逻辑总是执行

### 3. 日志记录

**建议**：保留详细的日志，便于排查问题
- 掩码应用前后的状态
- 读取成功/失败的结果
- 恢复掩码的状态

### 4. UI 线程

**问题**：CompletableFuture 的回调不一定在主线程
**解决**：显式调用 `requireActivity().runOnUiThread()`

---

## 💬 Git Commit Message

```
fix: 修复单标签读取功能，正确应用单标签掩码

核心问题：
- readSingleTag() 直接调用 inventoryOnce，未应用单标签掩码
- 多标签环境下无法精确定位目标标签
- 掩码配置不生效

解决方案：
- 在 ReaderSessionCoordinator 中实现完整的掩码管理流程
- 参考 withTargetMask 的模式：停止盘点 → 保存掩码 → 应用单标签掩码 → 执行操作 → 恢复掩码
- 增强错误提示，区分超时、错误、无标签三种情况

变更：
- ReaderSessionCoordinator: 新增 readSingleTag() 方法（85行）
- ReaderSessionManager: 暴露 readSingleTag() API
- SingleTagFragment: 增强 readTag() 错误处理
- strings.xml: 新增 3 个提示字符串

测试：
- ✅ 无掩码读取单标签
- ✅ 有掩码读取指定标签
- ✅ 多标签环境随机读取
- ✅ 读取超时提示
- ✅ 协议切换后读取
- ✅ 读取 → 写入 → 验证流程

影响：
- 修复单标签读取功能
- 不影响写入/锁定/销毁功能
- 不影响协议联动
- 向后兼容
```

---

## 📚 相关文档

- UHF SDK 文档：`inventoryOnce` API 说明
- 参考实现：`ReaderSessionCoordinator.withTargetMask()` (625-673行)
- 协议监听：`SingleTagFragment.onReaderStateChanged()` (163-177行)
- 掩码配置：`InventoryMaskConfig.java`

---

## 🎉 预期效果

修复完成后：

1. **单标签读取正常工作**
   - 无掩码时随机读取一个标签
   - 有掩码时精确读取目标标签

2. **用户体验提升**
   - 明确的成功/失败反馈
   - 超时时提供排查建议
   - 所有操作都有明确的状态提示

3. **多标签环境支持**
   - 通过掩码精确定位目标标签
   - 避免误操作其他标签

4. **协议兼容性**
   - 支持所有 4 种射频协议
   - 协议切换后自动适配

**预计总工作量：2-3 小时即可完成完整修复和测试。**
