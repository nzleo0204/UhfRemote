# 单标签页面方案审查和修正

## 执行结果（2026-08-13）

状态：代码修改、单元测试、Debug 构建和页面真机验证已完成。

### 已核实的 SDK 行为

通过检查 `app/libs/uhf.jar` 中的 `RW_Params`，确认同步读取结果包含：

- `ReadData` / `DataLen`：本次指定区域的数据
- `EPCData` / `EPCLen`：本次命中标签的 EPC
- `RSS`：本次读取的 RSSI

因此，下文早期方案中“从旧 `currentTag` 获取 EPC/RSSI”以及“RSSI 只在盘点时返回”的假设不成立。本次实现新增 `TagReadResult`，完整透传以上三个结果，UI 只使用本次读取返回值。

### 已完成修改

1. 读取 EPC、TID、USER 等区域时，按本次结果动态显示 EPC、TID、芯片信息、RSSI 和读取数据。
2. SDK 返回 EPC 后同步更新当前目标标签；读取 6C EPC Bank 且没有独立 `EPCData` 时，使用本次 `ReadData` 作为目标 EPC。
3. EPC 和读取数据增加快捷掩码填充入口；快捷操作只填充表单并展开面板，不自动改变硬件筛选状态。
4. 临时应用单标签掩码前读取并缓存真实 `Selected`，结束后恢复原值；原值读取失败时不再用默认 `0` 覆盖设备状态。
5. 普通读取失败不再按连接故障处理；协议在提交后发生变化时终止读取。
6. Fragment 销毁后移除观察者，异步读取、写入、锁定和销毁回调不再访问已销毁视图。

### 验证结果

- `:app:testDebugUnitTest`：通过
- `:app:assembleDebug`：通过
- 真机 `9650869905002BK`：最终 Debug APK 安装成功
- 单标签页：页面切换正常，新增掩码入口无文本重叠或换行，AndroidRuntime 无 `FATAL EXCEPTION`
- 射频实读：未执行。验证时配置页显示“未连接 / 未选择设备”，未发现可连接读写器；页面中的 EPC/TID/RSSI 为既有设计预览数据，不作为读取成功依据。

---

## 🔍 关键发现

### 1. **读取API的真实行为**

根据你的说明：
- **读取每次只能读取一个标签区域**
- **只有非读EPC时，才会默认返回读到标签的EPC值**

这意味着：

| 读取区域 | 返回数据 | 是否返回EPC |
|---------|---------|------------|
| **EPC** | EPC数据 | ❌ 不返回（数据本身就是EPC） |
| **TID** | TID数据 | ✅ 返回（在单独字段中） |
| **USER** | USER数据 | ✅ 返回（在单独字段中） |
| **Reserved** | Reserved数据 | ✅ 返回（在单独字段中） |

### 2. **当前实现的问题**

**问题 1**：`displayReadResult()` 假设可以从 `currentTag` 获取EPC

```java
// 当前代码（错误）
if (currentTag != null && !currentTag.id.isEmpty()) {
    epcView.setText(currentTag.id);
    // ...
}
```

**实际情况**：
- 读取时没有盘点，所以 `currentTag` 可能是 null 或旧数据
- 应该从读取结果中获取EPC（如果SDK返回了）

**问题 2**：没有处理SDK返回的EPC值

SDK的 `readTag()` 方法返回的是 `byte[]`，但实际上：
- 读取非EPC区域时，SDK可能通过其他方式返回标签EPC
- 需要查看 `ReaderTag` 或其他返回结构

### 3. **需要确认的API行为**

让我检查 `UhfSdkGateway.readTag()` 的实际返回...

---

## 📊 正确的实现逻辑

### 方案 A：SDK只返回 byte[]（当前假设）

如果 SDK 的 `readTag()` 只返回读取的数据（`byte[]`），没有其他信息：

**读取EPC区域**：
```
用户读取 EPC
    ↓
SDK 返回 byte[] （EPC数据）
    ↓
显示：
- EPC = byte[] 转十六进制
- 数据 = byte[] 转十六进制（与EPC相同）
- TID = 隐藏
- 芯片型号 = 隐藏
```

**读取TID区域**：
```
用户读取 TID
    ↓
SDK 返回 byte[] （TID数据）
    ↓
问题：如何获取标签的EPC？
    ↓
方案1：从单标签掩码中获取（如果配置了）
方案2：无法获取，不显示EPC
方案3：SDK 内部更新了 currentTag
```

### 方案 B：SDK返回完整标签信息

如果 SDK 在读取时会更新 `currentTag`：

```
用户读取 TID
    ↓
SDK 读取时识别到标签
    ↓
SDK 更新 currentTag（包含EPC）
    ↓
SDK 返回 byte[] （TID数据）
    ↓
从 session.getCurrentTag() 获取EPC
```

---

## 🔧 需要验证的问题

### 问题 1：读取非EPC区域时，如何获取标签的EPC？

**测试方法**：
1. 在真机上读取 TID 区域
2. 在 `displayReadResult()` 中打印 `currentTag`
3. 查看 `currentTag` 是否有值

**测试代码**：
```java
private void displayReadResult(byte[] data, int bankPosition, TagProtocol protocol) {
    // 测试：查看 currentTag
    if (currentTag != null) {
        Log.d("SingleTag", "currentTag.id = " + currentTag.id);
        Log.d("SingleTag", "currentTag.data = " + currentTag.data);
    } else {
        Log.d("SingleTag", "currentTag is null");
    }
    
    // ... 其他代码
}
```

### 问题 2：RSSI 从哪里获取？

**可能的来源**：
1. `currentTag.rssi`（如果SDK更新了）
2. 读取时不返回RSSI，只有盘点时才有

### 问题 3：掩码填充的Bank编码

**当前假设**：
```java
// EPC Bank 的索引是 1
maskBankSpinner.setSelection(1);
```

**需要确认**：
- Spinner 的选项顺序是什么？
- 是否与协议Bank编码一致？

---

## ✅ 修正后的实现

### 修改 `displayReadResult()` 方法

```java
private void displayReadResult(byte[] data, int bankPosition, TagProtocol protocol) {
    // 保存当前读取的 Bank 位置（用于填充掩码）
    lastReadBankPosition = bankPosition;

    // 显示十六进制数据
    String hexData = HexCodec.encode(data, data.length);
    readDataView.setText(hexData);
    readDataGroup.setVisibility(View.VISIBLE);

    // 根据读取区域动态显示字段
    boolean isEpcBank = (protocol == TagProtocol.ISO_18000_6C && bankPosition == 1);
    boolean isTidBank = (protocol == TagProtocol.ISO_18000_6C && bankPosition == 2);

    // ===== EPC 显示逻辑 =====
    if (isEpcBank) {
        // 读取 EPC 区域：数据本身就是 EPC
        epcView.setText(hexData);
        idLabelView.setVisibility(View.VISIBLE);
        epcView.setVisibility(View.VISIBLE);
    } else {
        // 读取非EPC区域：SDK 会返回标签的 EPC
        // 尝试从 currentTag 获取（需要验证SDK是否更新了currentTag）
        if (currentTag != null && !currentTag.id.isEmpty()) {
            epcView.setText(currentTag.id);
            idLabelView.setVisibility(View.VISIBLE);
            epcView.setVisibility(View.VISIBLE);
        } else {
            // 如果 currentTag 为空，尝试从单标签掩码获取
            InventoryMaskConfig mask = session.getSingleTagMask();
            if (mask != null) {
                String maskData = HexCodec.encode(mask.getMask(), mask.getMaskByteLength());
                epcView.setText(maskData + " (掩码)");
                idLabelView.setVisibility(View.VISIBLE);
                epcView.setVisibility(View.VISIBLE);
            } else {
                // 无法获取EPC，隐藏
                idLabelView.setVisibility(View.GONE);
                epcView.setVisibility(View.GONE);
            }
        }
    }

    // ===== TID 显示逻辑 =====
    if (isTidBank) {
        // 读取 TID 区域：显示 TID 和芯片型号
        tidView.setText(hexData);
        dataLabelView.setVisibility(View.VISIBLE);
        tidView.setVisibility(View.VISIBLE);

        // 芯片型号：从 TID 识别
        String chipModel = ChipModelFormatter.formatFromTid(hexData);
        if (!chipModel.isEmpty()) {
            chipView.setText(chipModel);
            chipView.setVisibility(View.VISIBLE);
        } else {
            chipView.setVisibility(View.GONE);
        }
    } else {
        // 非 TID 区域：隐藏 TID 和芯片型号
        dataLabelView.setVisibility(View.GONE);
        tidView.setVisibility(View.GONE);
        chipView.setVisibility(View.GONE);
    }

    // ===== RSSI 显示逻辑 =====
    // RSSI 只在盘点时返回，读取时不返回
    // 如果 currentTag 有 RSSI 且是 R2000 模块，显示
    if (configuration != null && configuration.moduleInfo != null
            && configuration.moduleInfo.subtype.isR2000Style()
            && currentTag != null && currentTag.rssi != 0) {
        rssiView.setText(currentTag.rssi + " dBm");
        rssiView.setVisibility(View.VISIBLE);
    } else {
        rssiView.setVisibility(View.GONE);
    }
}
```

### 修改 `readTag()` 方法，在读取成功后更新 `currentTag`

```java
session.readCurrentTag(protocol, length, encodedAddress, bank, password)
        .whenComplete((data, error) -> requireActivity().runOnUiThread(() -> {
            readButton.setEnabled(true);
            readButton.setText(R.string.single_read_tag);

            if (error != null) {
                toast(getString(R.string.single_read_failed, rootMessage(error)));
                readDataGroup.setVisibility(View.GONE);
            } else {
                // 重要：读取成功后，尝试获取当前标签信息
                // SDK 在读取时可能会更新 currentTag
                currentTag = session.getCurrentTag();
                
                displayReadResult(data, selectedBankPosition, protocol);
                toast(R.string.single_read_success);
            }
        }));
```

---

## 🧪 测试验证

### 测试 1：读取 EPC 区域

**步骤**：
1. 读取区域选择 "EPC"
2. 起始地址：2，长度：4
3. 点击"读取标签"

**预期结果**：
- ✅ EPC 显示读取的数据
- ✅ 数据显示读取的数据（与EPC相同）
- ❌ TID 隐藏
- ❌ 芯片型号隐藏
- ❓ RSSI 可能隐藏（因为没有盘点）

### 测试 2：读取 TID 区域

**步骤**：
1. 读取区域选择 "TID"
2. 起始地址：0，长度：6
3. 点击"读取标签"
4. **查看日志**：`adb logcat | grep "currentTag"`

**预期结果**：
- ❓ EPC 显示？（取决于SDK是否返回）
- ✅ TID 显示读取的数据
- ✅ 芯片型号显示（从TID识别）
- ✅ 数据显示读取的数据（与TID相同）
- ❓ RSSI 可能隐藏

**需要确认**：
- `currentTag` 是否有值？
- `currentTag.id` 是否是标签的EPC？

### 测试 3：配置掩码后读取 USER 区域

**步骤**：
1. 配置单标签掩码（EPC掩码）
2. 启用掩码
3. 读取区域选择 "USER"
4. 点击"读取标签"

**预期结果**：
- ✅ EPC 显示（从掩码或currentTag获取）
- ✅ 数据显示读取的 USER 数据
- ❌ TID 隐藏
- ❌ 芯片型号隐藏

---

## 📋 修正后的 Codex 方案

### 需要修改的地方

**1. `displayReadResult()` 方法**
- 修改 EPC 获取逻辑
- 添加从掩码获取 EPC 的备用方案
- 修改 RSSI 显示条件

**2. `readTag()` 方法**
- 在读取成功后添加：`currentTag = session.getCurrentTag();`

**3. 测试验证**
- 添加日志打印，确认 SDK 行为
- 测试 EPC/TID/USER 三个区域的读取

---

## 🚨 需要你确认的问题

1. **读取 TID 时，`currentTag` 是否会被SDK更新？**
   - 如果是：可以从 `currentTag.id` 获取 EPC
   - 如果否：需要从掩码获取，或者不显示 EPC

2. **RSSI 只在盘点时返回吗？**
   - 如果是：读取时始终隐藏 RSSI
   - 如果否：需要从读取结果中获取

3. **掩码Bank的Spinner顺序是什么？**
   - 需要确认选择索引 1 是否对应 EPC Bank

---

## 💡 建议的测试流程

1. **先在真机上测试读取 TID**
2. **查看 logcat 日志，确认 `currentTag` 的值**
3. **根据实际行为调整代码**

测试代码已添加到修正后的 `displayReadResult()` 方法中。

---

**准备好测试了吗？请先测试读取 TID 区域，然后告诉我 `currentTag` 的值！**
