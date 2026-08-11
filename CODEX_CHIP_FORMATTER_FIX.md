# ChipModelFormatter 修复计划

## 执行状态（2026-08-11）

- ✅ `ChipModelFormatter` 已移除对 `tag.data` 的硬编码识别，只格式化 SDK 提供的
  `chipModel`，未识别时显示 `tidPrefix`。
- ✅ 双语型号格式化已提取为独立方法，字段职责边界已补充到类注释。
- ✅ 三个计划指定的定向测试、全量 Debug 单元测试和 Debug APK 构建通过。
- ✅ 真机连接 `RFID-BT860`，在 `EPC+TID`、起始地址 0 下完成盘点；14 个标签均显示
  SDK 返回的芯片型号 `IMPINJ Manza4QT`，期间无应用崩溃。

> 本计划已执行完成，以下内容保留为实施与验证记录。

## 📋 执行摘要

**目标**：修复 ChipModelFormatter 的字段混用问题，移除不必要的硬编码芯片识别逻辑。

**背景**：
- UHF SDK 底层已通过 JNI 查表识别芯片型号（`InventoryData.chipModel`）
- SDK 支持完整芯片库：Impinj、Fudan、NXP 等多厂商
- 应用层的 `ChipModelFormatter` 不应重复实现识别逻辑
- 当前代码将 `tag.data` 误当作 TID 使用，存在字段混用风险

**预计时间**：30 分钟

---

## 🎯 核心问题

### 问题 1：字段混用风险

**文件**：`app/src/main/java/com/leo/remote/reader/ChipModelFormatter.java:21-24`

```java
String tid = tag.data.trim().toUpperCase(Locale.US);
if (tid.startsWith("E28011") || tid.startsWith("E28012")) {
    return "Impinj Monza";
}
```

**错误**：
- `tag.data` 内容随 `InventoryArea` 变化，不一定是 TID
- 只有在 `C_EPC_TID` 区域时才是真正的 TID 数据
- 在 `C_EPC_USER`、`C_EPC_RESERVED` 区域会误匹配

### 问题 2：重复实现芯片识别

SDK 已提供完整芯片识别：
```java
// InventoryData.java (SDK 层)
/**
 * 芯片型号，由 TID 前 4 字节查表得出
 * 如 "IMPINJ Monza M750"、"Fudan FM13UF011E|复旦 FM13UF011E"
 */
private String chipModel;
```

应用层不应重复实现，只需格式化输出。

---

## 📝 详细执行步骤

### Task 1: 简化 ChipModelFormatter

**文件**：`app/src/main/java/com/leo/remote/reader/ChipModelFormatter.java`

**操作**：移除硬编码的 Impinj 识别逻辑

**修改前**（第 9-27 行）：
```java
public static String format(ReaderTag tag) {
    if (tag == null) { return ""; }
    String model = tag.chipModel.trim();
    if (!model.isEmpty()) {
        String[] names = model.split("\\|", -1);
        String english = names[0].trim();
        if (names.length == 1) { return english; }
        String chinese = names[1].trim();
        return Locale.getDefault().getLanguage().startsWith("zh")
                ? (chinese.isEmpty() ? english : chinese)
                : (english.isEmpty() ? chinese : english);
    }
    String tid = tag.data.trim().toUpperCase(Locale.US);  // ❌ 错误：data 不一定是 TID
    if (tid.startsWith("E28011") || tid.startsWith("E28012")) {
        return "Impinj Monza";
    }
    return tag.tidPrefix == 0 ? ""
            : String.format(Locale.US, "未知(%08X)", tag.tidPrefix);
}
```

**修改后**（完整替换）：
```java
public static String format(ReaderTag tag) {
    if (tag == null) { return ""; }
    
    // 优先使用 SDK 识别的芯片型号
    String model = tag.chipModel.trim();
    if (!model.isEmpty()) {
        return formatBilingualModel(model);
    }
    
    // SDK 未识别时，显示 TID 前缀
    return tag.tidPrefix == 0 ? ""
            : String.format(Locale.US, "未知(%08X)", tag.tidPrefix);
}

/**
 * 格式化双语芯片型号。
 * 
 * SDK 返回格式：
 * - 单语：直接返回（如 "IMPINJ Monza M750"）
 * - 双语："英文|中文"（如 "Fudan FM13UF011E|复旦 FM13UF011E"）
 * 
 * 根据系统语言选择合适的名称。
 */
private static String formatBilingualModel(String model) {
    String[] names = model.split("\\|", -1);
    if (names.length == 1) {
        return names[0].trim();
    }
    
    String english = names[0].trim();
    String chinese = names[1].trim();
    boolean isChineseLocale = Locale.getDefault().getLanguage().startsWith("zh");
    
    if (isChineseLocale) {
        return chinese.isEmpty() ? english : chinese;
    } else {
        return english.isEmpty() ? chinese : english;
    }
}
```

**关键改动**：
1. ✅ 移除 `tag.data` 的 TID 前缀匹配逻辑
2. ✅ 完全依赖 SDK 的 `chipModel` 字段
3. ✅ 提取双语格式化为独立方法，增强可读性
4. ✅ 添加详细注释说明 SDK 返回格式

---

### Task 2: 更新单元测试

**文件**：`app/src/test/java/com/leo/remote/reader/ReaderDomainTest.java`

**操作**：更新芯片识别测试用例

**修改前**（第 260-266 行）：
```java
@Test
public void identifiesImpinjChipFromTidWhenSdkMetadataIsMissing() {
    assertEquals("Impinj Monza", ChipModelFormatter.format(
            new ReaderTag("EPC", "E2801160600002041891F8C0", -50, 0, 1, "", 0)));
    assertEquals("Impinj Monza", ChipModelFormatter.format(
            new ReaderTag("EPC", "e280120000000000", -50, 0, 1, "", 0)));
}
```

**修改后**：
```java
@Test
public void showsTidPrefixWhenSdkMetadataIsMissing() {
    // SDK 未识别芯片时，显示 TID 前缀
    assertEquals("未知(E2801160)", ChipModelFormatter.format(
            new ReaderTag("EPC", "任意数据", -50, 0, 1, "", 0xE2801160)));
    
    // TID 前缀为 0 时，返回空字符串
    assertEquals("", ChipModelFormatter.format(
            new ReaderTag("EPC", "任意数据", -50, 0, 1, "", 0)));
}

@Test
public void formatsSdkProvidedChipModel() {
    // SDK 已识别时，使用 SDK 的结果
    assertEquals("IMPINJ Monza M750", ChipModelFormatter.format(
            new ReaderTag("EPC", "任意数据", -50, 0, 1, "IMPINJ Monza M750", 0xE2801160)));
    
    // 双语芯片名称根据系统语言选择
    ReaderTag fudan = new ReaderTag("EPC", "任意数据", -50, 0, 1, 
            "Fudan FM13UF011E|复旦 FM13UF011E", 0xE0040150);
    String result = ChipModelFormatter.format(fudan);
    assertTrue(result.equals("Fudan FM13UF011E") || result.equals("复旦 FM13UF011E"));
}
```

**关键改动**：
1. ✅ 移除对 `tag.data` 内容的依赖
2. ✅ 测试改为验证 `tidPrefix` 显示
3. ✅ 新增 SDK 识别结果的测试
4. ✅ 测试用例更符合实际使用场景

---

### Task 3: 添加代码注释

**文件**：`app/src/main/java/com/leo/remote/reader/ChipModelFormatter.java`

**操作**：在类注释中说明职责边界

**完整类定义**：
```java
package com.leo.remote.reader;

import java.util.Locale;

/**
 * 芯片型号格式化器。
 * 
 * <h3>职责</h3>
 * <ul>
 *   <li>格式化 SDK 识别的芯片型号（双语适配）</li>
 *   <li>当 SDK 未识别时，显示 TID 前缀</li>
 * </ul>
 * 
 * <h3>不负责</h3>
 * <ul>
 *   <li>芯片识别：由 UHF SDK 底层 JNI 通过 TID 前 4 字节查表完成</li>
 *   <li>盘点区域判断：调用方需确保在 C_EPC_TID 区域且地址为 0 时才显示芯片信息</li>
 * </ul>
 * 
 * <h3>SDK 芯片识别条件</h3>
 * <p>SDK 只在满足以下条件时才识别芯片：</p>
 * <ol>
 *   <li>标签协议为 ISO18000-6C</li>
 *   <li>盘点区域为 C_EPC_TID</li>
 *   <li>盘点起始地址为 0</li>
 *   <li>TID 前缀在芯片库内</li>
 * </ol>
 * 
 * @see InventoryFragment#applyColumnVisibility() 调用方的区域判断示例
 */
public final class ChipModelFormatter {
    private ChipModelFormatter() {}
    
    // ... 方法实现 ...
}
```

---

## ✅ 验证清单

### 编译验证
```bash
cd /Users/lei/Projects/UhfRemote
./gradlew assembleDebug
```

**预期结果**：编译成功，无错误

### 单元测试
```bash
./gradlew test --tests ReaderDomainTest.showsTidPrefixWhenSdkMetadataIsMissing
./gradlew test --tests ReaderDomainTest.formatsSdkProvidedChipModel
./gradlew test --tests ReaderDomainTest.formatsChipModelForDifferentLocales
```

**预期结果**：3 个测试全部通过

### 功能测试

**前提条件**：
- 连接 UHF 读写器
- 配置盘点区域为 `C_EPC_TID`
- 盘点起始地址设为 0

**测试步骤**：

1. **测试 SDK 识别的芯片**
   - 盘点 Impinj Monza 标签
   - 预期：显示 "IMPINJ Monza M750" 等具体型号

2. **测试未识别的芯片**
   - 盘点未在 SDK 芯片库中的标签
   - 预期：显示 "未知(E2801160)" 等 TID 前缀

3. **测试非 TID 区域**
   - 切换盘点区域为 `C_EPC_USER`
   - 预期：芯片列不显示（由 `InventoryFragment.applyColumnVisibility()` 控制）

4. **测试双语显示**
   - 系统语言设为中文：盘点复旦芯片，显示"复旦 FM13UF011E"
   - 系统语言设为英文：盘点复旦芯片，显示"Fudan FM13UF011E"

---

## 📊 影响范围

### 修改文件
- ✏️ `ChipModelFormatter.java` - 移除硬编码逻辑
- ✏️ `ReaderDomainTest.java` - 更新测试用例

### 不受影响
- ✅ `NativeUhfSdkGateway.java` - 无需修改
- ✅ `ReaderInventoryController.java` - 无需修改
- ✅ `InventoryFragment.java` - 无需修改
- ✅ `InventoryAdapter.java` - 无需修改

---

## 🎯 技术要点

### SDK 芯片识别流程

```
┌─────────────────────────────────────────────────────────────┐
│ 1. UHF 读写器硬件盘点                                        │
│    - 协议: ISO18000-6C                                       │
│    - 区域: C_EPC_TID                                         │
│    - 地址: 0                                                 │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. JNI 层（UHF SDK 底层）                                    │
│    - 提取 TID 前 4 字节                                      │
│    - 查芯片库（Impinj/Fudan/NXP...）                         │
│    - 生成 chipModel + tidPrefix                              │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Java 层（InventoryData）                                  │
│    - chipModel: "IMPINJ Monza M750" 或                       │
│                 "Fudan FM13UF011E|复旦 FM13UF011E"          │
│    - tidPrefix: 0xE2801160                                   │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 应用层（ChipModelFormatter）                              │
│    - 仅负责格式化输出                                        │
│    - 双语适配                                                │
│    - TID 前缀显示                                            │
└─────────────────────────────────────────────────────────────┘
```

### 为什么移除硬编码识别

**原实现问题**：
```java
String tid = tag.data.trim().toUpperCase(Locale.US);
if (tid.startsWith("E28011") || tid.startsWith("E28012")) {
    return "Impinj Monza";
}
```

1. **字段混用**：`tag.data` 不是 TID
   - `C_EPC_TID` 区域：是 TID ✓
   - `C_EPC_USER` 区域：是 USER 数据 ✗
   - `C_EPC_RESERVED` 区域：是 RESERVED 数据 ✗

2. **功能重复**：SDK 已完成识别
   - SDK 支持 20+ 厂商芯片库
   - 应用层硬编码只能识别 2 个前缀

3. **维护成本**：新芯片需双重维护
   - SDK 更新芯片库
   - 应用层也要加硬编码

**新实现优势**：
- ✅ 完全依赖 SDK 芯片库
- ✅ 支持所有 SDK 识别的芯片
- ✅ 代码更简洁（19 行 → 35 行，但逻辑更清晰）
- ✅ 职责单一（格式化，不识别）

---

## 🚀 执行顺序

### Phase 1: 代码修改（15 分钟）
1. 修改 `ChipModelFormatter.java`
2. 更新 `ReaderDomainTest.java`
3. 添加类注释

### Phase 2: 验证（10 分钟）
4. 运行单元测试
5. 编译项目

### Phase 3: 提交（5 分钟）
6. Git commit

---

## 📌 注意事项

1. **不要修改 SDK 调用**：`NativeUhfSdkGateway.toReaderTag()` 已正确传递 `chipModel`
2. **不要修改调用方**：`InventoryFragment.applyColumnVisibility()` 的区域判断是正确的
3. **测试用例要更新**：旧测试依赖 `tag.data` 内容，需修改为依赖 `chipModel`

---

## 💬 Git Commit Message

```
fix: 修复 ChipModelFormatter 字段混用问题

移除硬编码的 Impinj 芯片识别逻辑，完全依赖 UHF SDK 的芯片识别结果。

变更：
- 移除对 tag.data 的 TID 前缀匹配（data 字段内容随盘点区域变化）
- 完全依赖 SDK 的 chipModel 字段（底层 JNI 通过 TID 查表识别）
- 提取双语格式化为独立方法
- 更新单元测试以匹配新逻辑

原因：
- SDK 已支持完整芯片库（Impinj/Fudan/NXP 等 20+ 厂商）
- 应用层不应重复实现识别逻辑
- tag.data 在非 TID 区域会导致错误匹配

影响：
- ChipModelFormatter.java（核心逻辑简化）
- ReaderDomainTest.java（测试用例更新）
```

---

## 📚 相关文档

- UHF SDK 芯片识别条件：`InventoryData.chipModel` 字段注释
- 盘点区域枚举：`InventoryArea.java`
- 调用方示例：`InventoryFragment.applyColumnVisibility()`
