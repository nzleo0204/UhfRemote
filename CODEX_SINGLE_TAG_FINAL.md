# 单标签页面优化方案

## 执行状态（2026-08-14）

✅ 代码修改已完成
✅ 编译通过
✅ Debug APK 已安装到真机
⚠️ 真机启动/页面验证待继续：`adb shell monkey -p com.leo.remote.debug 1` 需要提升权限，审批服务返回 502
⚠️ 单元测试待继续：`./gradlew :app:testDebugUnitTest --tests com.leo.remote.reader.ReaderTagOperationsTest` 需要访问用户级 Gradle 缓存，审批服务返回 502

---

## 需求分析

### 问题 1：读取区域切换时，地址和长度固定不变
**现状**：无论选择哪个 Bank，地址和长度都需要手动输入。
**需求**：选择不同 Bank 时，自动填充合理的默认值。

### 问题 2：EPC Bank 读取时数据重复显示
**现状**：读取 EPC Bank 时，EPC 和数据显示相同内容，产生重复。
**需求**：读取 EPC Bank 时，只显示 EPC，不显示"数据"行。

### 问题 3：非 EPC Bank 读取时副标题固定为"TID"
**现状**：读取 USER/Reserved 区域时，副标题仍显示"TID"。
**需求**：副标题应根据读取区域动态变化（Reserved/EPC/TID/USER）。

### 问题 4：EPC 值在非 EPC Bank 读取时未显示
**现状**：读取 TID/USER 时，SDK 返回了 EPC，但界面未显示。
**需求**：非 EPC Bank 读取时，在下方默认显示返回的 EPC 值。

### 问题 5：芯片型号依赖硬编码解析
**现状**：`ChipModelFormatter.formatFromTid()` 硬编码解析 TID 前缀。
**需求**：SDK 的 `RW_Params` 已新增 `chipModel` 字段，优先使用 SDK 返回值，为空时回退到硬编码。

### 问题 6：主次数据颜色未区分
**现状**：所有数据颜色相同。
**需求**：本次读取的主要数据（如 TID）用主色，辅助信息（EPC、RSSI）用次色。

---

## 解决方案

### 方案 1：Bank 选择联动默认参数

**文件**：`SingleTagFragment.java`

**修改点**：
1. 在 `readBankSpinner` 的 `onItemSelected` 回调中增加 `updateReadDefaults(position)` 调用
2. 新增 `updateReadDefaults(int bankPosition)` 方法

**逻辑**：
```java
private void updateReadDefaults(int bankPosition) {
    TagProtocol protocol = readerState.getProtocol();
    if (protocol != TagProtocol.ISO_18000_6C) {
        return;
    }

    switch (bankPosition) {
        case 0:  // Reserved
            readAddressView.setText("0");
            readLengthView.setText("2");
            break;
        case 1:  // EPC
            readAddressView.setText("2");
            readLengthView.setText("6");
            break;
        case 2:  // TID
            readAddressView.setText("0");
            readLengthView.setText("6");
            break;
        case 3:  // USER
            readAddressView.setText("0");
            readLengthView.setText("8");
            break;
    }
}
```

**默认值对照表**：

| Bank | 起始地址 | 长度（Word） | 说明 |
|------|---------|------------|------|
| Reserved (0) | 0 | 2 | Kill/Access 密码区 |
| EPC (1) | 2 | 6 | 跳过 PC+CRC，读取 EPC 数据 |
| TID (2) | 0 | 6 | TID 前 12 字节包含芯片信息 |
| USER (3) | 0 | 8 | 用户数据区起始位置 |

**影响**：用户选择 Bank 后，地址和长度自动填充，减少手动输入。

---

### 方案 2：SDK 芯片信息支持

**背景**：SDK 的 `RW_Params` 现已包含 `chipModel` 和 `tidPrefix` 字段。

#### 2.1 修改 `TagReadResult`

**文件**：`TagReadResult.java`

**增加字段**：
```java
private final String chipModel;  // SDK 返回的芯片型号
private final int tidPrefix;     // TID 前缀（用于硬编码回退）

public String getChipModel() { return chipModel; }
public int getTidPrefix() { return tidPrefix; }
```

**构造函数**：
```java
public TagReadResult(byte[] data, byte[] epc, int rssi, String chipModel, int tidPrefix) {
    this.data = data;
    this.epc = epc;
    this.rssi = rssi;
    this.chipModel = chipModel;
    this.tidPrefix = tidPrefix;
}
```

#### 2.2 修改 `NativeUhfSdkGateway`

**文件**：`NativeUhfSdkGateway.java`
**方法**：`readTag()`

**修改**：
```java
// 原代码
return new TagReadResult(data, epc, result.RSS);

// 新代码
String chipModel = result.chipModel != null ? result.chipModel : "";
int tidPrefix = result.tidPrefix;
return new TagReadResult(data, epc, result.RSS, chipModel, tidPrefix);
```

#### 2.3 修改 `ReaderTagOperations`

**文件**：`ReaderTagOperations.java`
**方法**：`read()`

**修改**：透传芯片信息到 `currentTag`
```java
TagReadResult result = gateway.readTag(protocol, length, address, bank, password, 2000);
byte[] epc = result.getEpc();
if (epc.length == 0 && protocol == TagProtocol.ISO_18000_6C && bank == 1) {
    epc = result.getData();
}
if (epc.length > 0) {
    ReaderTag tag = new ReaderTag(HexCodec.encode(epc, epc.length), "", result.getRssi(),
            0, 1);
    currentTag = tag;
    publisher.publishCurrentTag(tag);
}
return result;
```

**影响**：UI 层可直接获取 SDK 返回的芯片型号，无需完全依赖硬编码解析。

---

### 方案 3：重写 `displayReadResult()` 显示逻辑

**文件**：`SingleTagFragment.java`
**方法**：`displayReadResult(TagReadResult result, int bankPosition, TagProtocol protocol)`

**核心逻辑**：

#### 3.1 数据准备
```java
byte[] data = result.getData();
byte[] epcBytes = result.getEpc();
String hexData = HexCodec.encode(data, data.length);
String hexEpc = HexCodec.encode(epcBytes, epcBytes.length);

boolean is6C = (protocol == TagProtocol.ISO_18000_6C);
boolean isEpcBank = (is6C && bankPosition == 1);
boolean isTidBank = (is6C && bankPosition == 2);

// 确定最终 EPC 值
String epc = hexEpc;
if (epc.isEmpty() && isEpcBank) {
    epc = hexData;  // EPC Bank 读取时，数据本身就是 EPC
}
```

#### 3.2 EPC 显示规则
```java
if (!epc.isEmpty()) {
    idLabelView.setText("EPC");
    epcView.setText(epc);
    // 颜色区分：EPC Bank 读取时为主色，其他为次色
    epcView.setTextColor(ContextCompat.getColor(requireContext(),
            isEpcBank ? R.color.rfid_primary : R.color.rfid_primary_soft));
    epcGroup.setVisibility(View.VISIBLE);
    fillEpcMaskButton.setVisibility(is6C ? View.VISIBLE : View.GONE);
} else {
    epcGroup.setVisibility(View.GONE);
}
```

#### 3.3 数据行显示规则
```java
if (isEpcBank) {
    // EPC Bank：不显示数据行，避免重复
    readDataGroup.setVisibility(View.GONE);
    tidGroup.setVisibility(View.GONE);
} else {
    // 非 EPC Bank：显示读取数据
    readDataView.setText(hexData);
    readDataGroup.setVisibility(View.VISIBLE);

    // 动态副标题
    String bankLabel = getBankLabel(bankPosition, protocol);
    dataLabelView.setText(bankLabel);
    tidView.setText(hexData);
    tidView.setTextColor(ContextCompat.getColor(requireContext(), R.color.rfid_primary));
    tidGroup.setVisibility(View.VISIBLE);
}
```

#### 3.4 芯片型号显示（仅 TID Bank）
```java
if (isTidBank) {
    String chipModel = result.getChipModel();
    if (chipModel.isEmpty()) {
        // SDK 未返回，回退到硬编码解析
        chipModel = ChipModelFormatter.formatFromTid(hexData);
    }
    chipGroup.setVisibility(chipModel.isEmpty() ? View.GONE : View.VISIBLE);
    chipView.setText(chipModel);
} else {
    chipGroup.setVisibility(View.GONE);
}
```

#### 3.5 RSSI 显示
```java
rssiGroup.setVisibility(result.getRssi() == 0 ? View.GONE : View.VISIBLE);
rssiView.setText(result.getRssi() == 0 ? "-" : result.getRssi() + " dBm");
```

---

### 方案 4：新增 `getBankLabel()` 辅助方法

**文件**：`SingleTagFragment.java`

**实现**：
```java
private String getBankLabel(int bankPosition, TagProtocol protocol) {
    if (protocol == TagProtocol.ISO_18000_6C) {
        switch (bankPosition) {
            case 0: return "Reserved";
            case 1: return "EPC";
            case 2: return "TID";
            case 3: return "USER";
        }
    }
    return "数据";
}
```

**作用**：根据 Bank 位置和协议返回对应的中文/英文标签，用于动态副标题。

---

## 显示效果对照

### 场景 1：读取 EPC Bank

**输入**：Bank = EPC, Address = 2, Length = 6

**SDK 返回**：
```
ReadData = E280116060000000000012AB
EPCData = (空)
RSS = -45
chipModel = (空)
```

**显示**：
```
┌─────────────────────────────┐
│ 读取结果                      │
├─────────────────────────────┤
│ EPC   E280116060000000000012AB │  ← 主色（rfid_primary）
│       [掩码] 按钮                │
├─────────────────────────────┤
│ ❌ 不显示"数据"行              │
│ ❌ 不显示 TID                  │
│ ❌ 不显示芯片型号               │
├─────────────────────────────┤
│ RSSI  -45 dBm                 │  ← 次色
└─────────────────────────────┘
```

---

### 场景 2：读取 TID Bank

**输入**：Bank = TID, Address = 0, Length = 6

**SDK 返回**：
```
ReadData = E200340520191710
EPCData = E280116060000000000012AB
RSS = -42
chipModel = "Impinj Monza 4QT"
```

**显示**：
```
┌─────────────────────────────┐
│ 读取结果                      │
├─────────────────────────────┤
│ EPC   E280116060000000000012AB │  ← 次色（rfid_primary_soft）
│       [掩码] 按钮                │
├─────────────────────────────┤
│ TID   E200340520191710        │  ← 主色（rfid_primary）
├─────────────────────────────┤
│ 芯片型号  Impinj Monza 4QT    │
├─────────────────────────────┤
│ RSSI  -42 dBm                 │
├─────────────────────────────┤
│ 读取数据    [掩码] [复制]       │
│ E200340520191710              │
└─────────────────────────────┘
```

**芯片型号来源**：优先使用 SDK 返回的 `chipModel`，为空时回退到 `ChipModelFormatter.formatFromTid()`。

---

### 场景 3：读取 USER Bank

**输入**：Bank = USER, Address = 0, Length = 8

**SDK 返回**：
```
ReadData = 0000000000000000000000000000000000
EPCData = E280116060000000000012AB
RSS = -40
chipModel = (空)
```

**显示**：
```
┌─────────────────────────────┐
│ 读取结果                      │
├─────────────────────────────┤
│ EPC   E280116060000000000012AB │  ← 次色
│       [掩码] 按钮                │
├─────────────────────────────┤
│ USER  0000000000000000000000...│  ← 主色
├─────────────────────────────┤
│ ❌ 不显示芯片型号               │
├─────────────────────────────┤
│ RSSI  -40 dBm                 │
├─────────────────────────────┤
│ 读取数据    [掩码] [复制]       │
│ 0000000000000000000000000000...│
└─────────────────────────────┘
```

---

### 场景 4：读取 Reserved Bank

**输入**：Bank = Reserved, Address = 0, Length = 2

**SDK 返回**：
```
ReadData = 00000000
EPCData = E280116060000000000012AB
RSS = -38
chipModel = (空)
```

**显示**：
```
┌─────────────────────────────┐
│ 读取结果                      │
├─────────────────────────────┤
│ EPC      E280116060000000000012AB │  ← 次色
│          [掩码] 按钮               │
├─────────────────────────────┤
│ Reserved 00000000             │  ← 主色
├─────────────────────────────┤
│ RSSI     -38 dBm              │
├─────────────────────────────┤
│ 读取数据    [掩码] [复制]       │
│ 00000000                      │
└─────────────────────────────┘
```

---

## 颜色规范

| 数据类型 | 颜色资源 | 使用场景 |
|---------|---------|---------|
| **主要数据** | `R.color.rfid_primary` | 本次读取的核心内容（EPC Bank 的 EPC / TID Bank 的 TID / USER Bank 的 USER） |
| **次要数据** | `R.color.rfid_primary_soft` | 辅助信息（非 EPC Bank 读取时显示的 EPC / RSSI） |
| **标签文本** | `R.color.rfid_text_muted` | "EPC" / "TID" / "USER" 等标签 |

---

## 测试清单

### 测试 1：Bank 选择联动
- [ ] 选择 EPC → 地址自动变为 2，长度变为 6
- [ ] 选择 TID → 地址自动变为 0，长度变为 6
- [ ] 选择 USER → 地址自动变为 0，长度变为 8
- [ ] 选择 Reserved → 地址自动变为 0，长度变为 2

### 测试 2：EPC Bank 读取
- [ ] 只显示 EPC 行（主色）
- [ ] 不显示"数据"行
- [ ] 不显示 TID 和芯片型号
- [ ] RSSI 正常显示

### 测试 3：TID Bank 读取
- [ ] 显示 EPC（次色）+ TID（主色）
- [ ] 副标题显示"TID"
- [ ] 芯片型号从 SDK 获取（如 "Impinj Monza 4QT"）
- [ ] SDK 未返回时回退到硬编码解析
- [ ] "读取数据"区域显示 TID 的十六进制值

### 测试 4：USER Bank 读取
- [ ] 显示 EPC（次色）+ USER 数据（主色）
- [ ] 副标题显示"USER"
- [ ] 不显示芯片型号

### 测试 5：Reserved Bank 读取
- [ ] 显示 EPC（次色）+ Reserved 数据（主色）
- [ ] 副标题显示"Reserved"
- [ ] 不显示芯片型号

### 测试 6：颜色区分
- [ ] 主要数据颜色为 `rfid_primary`（深色/高对比度）
- [ ] 次要数据颜色为 `rfid_primary_soft`（浅色/低对比度）

---

## 修改文件清单

| 文件 | 修改内容 | 状态 |
|------|---------|------|
| `TagReadResult.java` | 增加 `chipModel` 和 `tidPrefix` 字段 | ✅ 已完成 |
| `NativeUhfSdkGateway.java` | 从 `RW_Params` 读取 `chipModel` 和 `tidPrefix` | ✅ 已完成 |
| `ReaderTagOperations.java` | 透传芯片信息到 `currentTag` | ✅ 已完成 |
| `SingleTagFragment.java` | 1. 增加 `updateReadDefaults()` Bank 联动<br>2. 重写 `displayReadResult()` 显示逻辑<br>3. 新增 `getBankLabel()` 辅助方法 | ✅ 已完成 |

---

## 编译状态

```bash
✅ :app:assembleDebug 成功
✅ APK 生成：build/app/outputs/apk/debug/app-debug.apk (21MB)
```

---

## 下一步

真机测试验证以上 6 个测试场景，特别关注：
1. **芯片型号来源**：确认 SDK 是否正确返回 `chipModel`
2. **EPC Bank 无重复**：确认读取 EPC 时不显示数据行
3. **颜色对比度**：确认主次数据颜色区分明显
