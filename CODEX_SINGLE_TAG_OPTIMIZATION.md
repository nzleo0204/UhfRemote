# 单标签页面优化方案

## 📋 执行摘要

**目标**：优化单标签读取结果显示和掩码快捷操作

**核心需求**：
1. 读取结果根据读取区域和模块类型动态显示
2. 新增"填充掩码"快捷按钮
3. 智能的掩码填充逻辑和错误提示

**预计时间**：2-3 小时

---

## 🎯 需求分析

### 需求 1：读取结果动态显示

**当前问题**：
- 读取 EPC 区域时，仍显示 TID、芯片型号、RSSI
- 这些字段应该根据读取区域和模块类型动态显示

**正确逻辑**：

| 读取区域 | EPC | TID | 芯片型号 | RSSI | 数据 |
|---------|-----|-----|---------|------|------|
| **EPC** | ✅ 显示 | ❌ 隐藏 | ❌ 隐藏 | 条件显示 | ✅ 显示 |
| **TID** | ✅ 显示（返回值） | ✅ 显示 | ✅ 显示 | 条件显示 | ✅ 显示 |
| **USER/Reserved** | ✅ 显示（返回值） | ❌ 隐藏 | ❌ 隐藏 | 条件显示 | ✅ 显示 |

**RSSI 显示条件**：
- 仅 R2000/R2000Plus/RM610 模块显示
- 需要从返回的标签信息中获取（如果有）

**说明**：
- 除 EPC 区域外，其他区域读取都会返回标签的 EPC 值
- 芯片型号仅在读取 TID 区域时识别和显示

### 需求 2：掩码快捷按钮

**位置**：
- EPC 文本右侧显示"填充掩码"按钮
- 数据文本右侧显示"填充掩码"按钮

**按钮样式**：
```
┌─────────────────────────────────────────┐
│ EPC:  E280116060000000123456  [🔍 掩码] │
│ 数据: 30313233343536...       [🔍 掩码] │
└─────────────────────────────────────────┘
```

**点击行为**：

1. **检查文本是否为空**
   - 空值 → 提示："掩码数据不能为空"
   - 有值 → 继续

2. **检查掩码是否已启用**
   - 已启用 → 提示："掩码已启用，请先取消掩码应用"
   - 未启用 → 继续

3. **填充掩码数据**
   - 自动展开掩码面板（如果折叠）
   - 填充存储区域（根据点击的按钮）
     - EPC 按钮 → 存储区域选择 "EPC"
     - 数据按钮 → 存储区域选择当前读取的 Bank
   - 填充掩码数据（十六进制文本）
   - 填充偏移量（默认值）
   - 填充长度（数据位数）
   - 启用掩码

4. **完成提示**
   - 提示："掩码已启用"

---

## 📝 详细执行步骤

### Task 1: 修改读取结果显示逻辑

**文件**：`app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java`

**操作**：修改 `displayReadResult()` 方法

**修改前**（第 356-380 行）：
```java
private void displayReadResult(byte[] data, int bankPosition, TagProtocol protocol) {
    // 显示十六进制数据
    String hexData = HexCodec.encode(data, data.length);
    readDataView.setText(hexData);
    readDataGroup.setVisibility(View.VISIBLE);

    // 更新 EPC 显示（如果是读取 EPC 区域）
    if (protocol == TagProtocol.ISO_18000_6C && bankPosition == 1) {
        epcView.setText(hexData);
    }

    // 更新 TID 显示（如果是读取 TID 区域）
    if (protocol == TagProtocol.ISO_18000_6C && bankPosition == 2) {
        tidView.setText(hexData);

        // 显示芯片型号
        String chipModel = ChipModelFormatter.formatFromTid(hexData);
        if (!chipModel.isEmpty()) {
            chipView.setText(chipModel);
            chipView.setVisibility(View.VISIBLE);
        }
    } else {
        chipView.setVisibility(View.GONE);
    }

    // RSSI 暂不显示（读取数据时不返回 RSSI）
    rssiView.setVisibility(View.GONE);
}
```

**修改后**：
```java
private void displayReadResult(byte[] data, int bankPosition, TagProtocol protocol) {
    // 显示十六进制数据
    String hexData = HexCodec.encode(data, data.length);
    readDataView.setText(hexData);
    readDataGroup.setVisibility(View.VISIBLE);

    // 根据读取区域动态显示字段
    boolean isEpcBank = (protocol == TagProtocol.ISO_18000_6C && bankPosition == 1);
    boolean isTidBank = (protocol == TagProtocol.ISO_18000_6C && bankPosition == 2);

    // EPC：读取 EPC 区域时显示；其他区域读取时也显示（SDK 返回）
    if (isEpcBank) {
        epcView.setText(hexData);
        idLabelView.setVisibility(View.VISIBLE);
        epcView.setVisibility(View.VISIBLE);
    } else {
        // 其他区域读取会返回标签的 EPC（需要从 currentTag 获取）
        if (currentTag != null && !currentTag.id.isEmpty()) {
            epcView.setText(currentTag.id);
            idLabelView.setVisibility(View.VISIBLE);
            epcView.setVisibility(View.VISIBLE);
        } else {
            idLabelView.setVisibility(View.GONE);
            epcView.setVisibility(View.GONE);
        }
    }

    // TID：仅读取 TID 区域时显示
    if (isTidBank) {
        tidView.setText(hexData);
        dataLabelView.setVisibility(View.VISIBLE);
        tidView.setVisibility(View.VISIBLE);

        // 芯片型号：仅 TID 区域显示
        String chipModel = ChipModelFormatter.formatFromTid(hexData);
        if (!chipModel.isEmpty()) {
            chipView.setText(chipModel);
            chipView.setVisibility(View.VISIBLE);
        } else {
            chipView.setVisibility(View.GONE);
        }
    } else {
        dataLabelView.setVisibility(View.GONE);
        tidView.setVisibility(View.GONE);
        chipView.setVisibility(View.GONE);
    }

    // RSSI：仅 R2000 模块显示（需要从 currentTag 获取）
    if (configuration != null && configuration.moduleInfo != null
            && configuration.moduleInfo.subtype.isR2000Style()
            && currentTag != null) {
        rssiView.setText(currentTag.rssi + " dBm");
        rssiView.setVisibility(View.VISIBLE);
    } else {
        rssiView.setVisibility(View.GONE);
    }
}
```

---

### Task 2: 修改读取结果布局，添加掩码按钮

**文件**：`app/src/main/res/layout/single_read_result_panel.xml`

**操作**：修改 EPC 和数据行，添加掩码按钮

**修改前**（第 13-15 行）：
```xml
<!-- EPC -->
<include layout="@layout/rfid_single_info_epc" />
```

**修改后**：
```xml
<!-- EPC -->
<LinearLayout
    android:id="@+id/group_single_epc"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="@dimen/dp_8"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:id="@+id/tv_single_id_label"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/single_epc_label"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_text_secondary"
            android:fontFamily="sans-serif-medium"
            android:paddingBottom="@dimen/dp_4" />

        <TextView
            android:id="@+id/btn_single_fill_epc_mask"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/single_fill_mask"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_primary"
            android:padding="@dimen/dp_8"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:drawableStart="@drawable/rfid_filter_ic"
            android:drawablePadding="@dimen/dp_4" />
    </LinearLayout>

    <TextView
        android:id="@+id/tv_single_epc"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="@dimen/dp_12"
        android:background="@drawable/rfid_field_bg"
        android:textSize="@dimen/sp_15"
        android:textColor="@color/rfid_text"
        android:fontFamily="monospace"
        android:textIsSelectable="true" />
</LinearLayout>
```

**同样修改"读取数据"部分**（第 28-63 行）：

**修改前**：
```xml
<LinearLayout
    android:id="@+id/group_single_read_data"
    ...>
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/single_read_data"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_text_secondary"
            android:fontFamily="sans-serif-medium"
            android:paddingBottom="@dimen/dp_4" />

        <TextView
            android:id="@+id/tv_single_copy_data"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/common_copy"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_primary"
            android:padding="@dimen/dp_8"
            android:background="?attr/selectableItemBackgroundBorderless" />
    </LinearLayout>
    ...
</LinearLayout>
```

**修改后**：
```xml
<LinearLayout
    android:id="@+id/group_single_read_data"
    ...>
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/single_read_data"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_text_secondary"
            android:fontFamily="sans-serif-medium"
            android:paddingBottom="@dimen/dp_4" />

        <TextView
            android:id="@+id/btn_single_fill_data_mask"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/single_fill_mask"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_primary"
            android:padding="@dimen/dp_8"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:drawableStart="@drawable/rfid_filter_ic"
            android:drawablePadding="@dimen/dp_4" />

        <TextView
            android:id="@+id/tv_single_copy_data"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/common_copy"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_primary"
            android:padding="@dimen/dp_8"
            android:background="?attr/selectableItemBackgroundBorderless" />
    </LinearLayout>
    ...
</LinearLayout>
```

---

### Task 3: 添加字符串资源

**文件**：`app/src/main/res/values/strings.xml`

**位置**：在单标签相关字符串区域添加

**新增内容**：
```xml
<!-- 单标签页 - 掩码快捷操作 -->
<string name="single_fill_mask">掩码</string>
<string name="single_mask_filled">掩码已启用</string>
<string name="single_mask_active_warning">掩码已启用，请先取消掩码应用</string>
<string name="single_mask_empty_warning">掩码数据不能为空</string>
```

---

### Task 4: 在 SingleTagFragment 中添加字段和初始化

**文件**：`app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java`

**操作 1**：添加字段（第 68-76 行附近）

**新增**：
```java
// 读取结果控件
private View readDataGroup;
private TextView readDataView;
private TextView copyDataButton;
private TextView fillEpcMaskButton;      // 新增：EPC 掩码按钮
private TextView fillDataMaskButton;     // 新增：数据掩码按钮
private View epcGroup;                   // 新增：EPC 组
private TextView idLabelView;
private TextView epcView;
private TextView dataLabelView;
private TextView tidView;
private TextView chipView;
private TextView rssiView;

// 新增：当前读取的 Bank 位置（用于填充掩码）
private int lastReadBankPosition = -1;
```

**操作 2**：在 initView() 中初始化（第 105-120 行附近）

**新增**：
```java
// 读取结果控件
readDataGroup = findViewById(R.id.group_single_read_data);
readDataView = findViewById(R.id.tv_single_read_data);
copyDataButton = findViewById(R.id.tv_single_copy_data);
fillEpcMaskButton = findViewById(R.id.btn_single_fill_epc_mask);     // 新增
fillDataMaskButton = findViewById(R.id.btn_single_fill_data_mask);   // 新增
epcGroup = findViewById(R.id.group_single_epc);                      // 新增
idLabelView = findViewById(R.id.tv_single_id_label);
epcView = findViewById(R.id.tv_single_epc);
dataLabelView = findViewById(R.id.tv_single_data_label);
tidView = findViewById(R.id.tv_single_tid);
chipView = findViewById(R.id.tv_single_chip);
rssiView = findViewById(R.id.tv_single_rssi);
```

**操作 3**：在 initView() 中绑定事件（第 172 行附近）

**新增**：
```java
readButton.setOnClickListener(view -> readTag());
copyDataButton.setOnClickListener(view -> copyReadData());
fillEpcMaskButton.setOnClickListener(view -> fillMaskFromEpc());     // 新增
fillDataMaskButton.setOnClickListener(view -> fillMaskFromData());   // 新增
writeAction.setOnClickListener(view -> showWriteDialog(false));
```

---

### Task 5: 修改 displayReadResult 方法

**文件**：`app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java`

**位置**：第 356-380 行

**完整代码**：见 Task 1

**额外操作**：保存当前读取的 Bank 位置

```java
private void displayReadResult(byte[] data, int bankPosition, TagProtocol protocol) {
    // 保存当前读取的 Bank 位置（用于填充掩码）
    lastReadBankPosition = bankPosition;
    
    // ... 其他代码
}
```

---

### Task 6: 添加填充掩码方法

**文件**：`app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java`

**位置**：在 copyReadData() 方法之后

**完整代码**：
```java
private void fillMaskFromEpc() {
    String epcData = epcView.getText().toString().trim();
    
    // 检查数据是否为空
    if (epcData.isEmpty() || epcData.equals("-") 
            || epcData.equals(getString(R.string.single_preview_epc))) {
        toast(R.string.single_mask_empty_warning);
        return;
    }
    
    // 检查掩码是否已启用
    if (activeMask != null) {
        toast(R.string.single_mask_active_warning);
        return;
    }
    
    // 填充掩码
    TagProtocol protocol = readerState.getProtocol();
    int epcBankIndex = 1;  // EPC Bank 在 6C 协议中的索引
    
    // 填充存储区域（EPC）
    maskBankSpinner.setSelection(epcBankIndex);
    
    // 填充偏移量（EPC 默认偏移 32 位）
    int offsetBits = ProtocolEncoding.defaultMaskOffsetBits(protocol, epcBankIndex);
    maskOffsetView.setText(String.valueOf(offsetBits));
    
    // 填充掩码数据
    maskHexView.setText(epcData);
    
    // 填充长度（位数）
    int lengthBits = epcData.length() * 4;
    maskLengthView.setText(String.valueOf(lengthBits));
    
    // 展开掩码面板（如果折叠）
    if (maskPanelContent.getVisibility() != View.VISIBLE) {
        maskPanelContent.setVisibility(View.VISIBLE);
        maskExpandView.setRotation(180);
    }
    
    // 启用掩码
    applyMask();
    
    // 提示
    toast(R.string.single_mask_filled);
}

private void fillMaskFromData() {
    String readData = readDataView.getText().toString().trim();
    
    // 检查数据是否为空
    if (readData.isEmpty()) {
        toast(R.string.single_mask_empty_warning);
        return;
    }
    
    // 检查掩码是否已启用
    if (activeMask != null) {
        toast(R.string.single_mask_active_warning);
        return;
    }
    
    // 检查是否有有效的读取 Bank 位置
    if (lastReadBankPosition < 0) {
        toast(R.string.single_mask_empty_warning);
        return;
    }
    
    // 填充掩码
    TagProtocol protocol = readerState.getProtocol();
    
    // 填充存储区域（当前读取的 Bank）
    maskBankSpinner.setSelection(lastReadBankPosition);
    
    // 填充偏移量
    int offsetBits = ProtocolEncoding.defaultMaskOffsetBits(protocol, lastReadBankPosition);
    maskOffsetView.setText(String.valueOf(offsetBits));
    
    // 填充掩码数据
    maskHexView.setText(readData);
    
    // 填充长度（位数）
    int lengthBits = readData.length() * 4;
    maskLengthView.setText(String.valueOf(lengthBits));
    
    // 展开掩码面板（如果折叠）
    if (maskPanelContent.getVisibility() != View.VISIBLE) {
        maskPanelContent.setVisibility(View.VISIBLE);
        maskExpandView.setRotation(180);
    }
    
    // 启用掩码
    applyMask();
    
    // 提示
    toast(R.string.single_mask_filled);
}
```

---

### Task 7: 修改 displayReadResult 保存当前标签

**文件**：`app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java`

**说明**：读取数据时，SDK 会返回标签的 EPC，需要更新 `currentTag`

**修改 readTag() 方法**（第 320-350 行）：

**在 `displayReadResult` 调用之前，先保存返回的标签信息**

**问题**：当前实现中，`readCurrentTag` 只返回 `byte[]` 数据，不返回完整的标签信息

**解决方案**：需要在读取时同时获取标签信息

**修改 `ReaderSessionCoordinator.readCurrentTag()`**：

```java
// 当前实现
public CompletableFuture<byte[]> readCurrentTag(TagProtocol protocol, int length,
        int address, int bank, byte[] password) {
    return withTargetMask(() -> tagOperations.read(protocol, length, address, bank, password));
}

// 需要额外实现一个方法，返回完整标签信息
public CompletableFuture<ReadResult> readCurrentTagWithInfo(TagProtocol protocol, int length,
        int address, int bank, byte[] password) {
    return withTargetMask(() -> {
        byte[] data = tagOperations.read(protocol, length, address, bank, password);
        ReaderTag tag = tagOperations.getCurrentTag();
        return new ReadResult(data, tag);
    });
}

// 定义返回类型
public static class ReadResult {
    public final byte[] data;
    public final ReaderTag tag;
    
    public ReadResult(byte[] data, ReaderTag tag) {
        this.data = data;
        this.tag = tag;
    }
}
```

**简化方案**：直接在读取后从 `session.getCurrentTag()` 获取

修改 `SingleTagFragment.readTag()`：

```java
session.readCurrentTag(protocol, length, encodedAddress, bank, password)
        .whenComplete((data, error) -> requireActivity().runOnUiThread(() -> {
            readButton.setEnabled(true);
            readButton.setText(R.string.single_read_tag);

            if (error != null) {
                toast(getString(R.string.single_read_failed, rootMessage(error)));
                readDataGroup.setVisibility(View.GONE);
            } else {
                // 更新当前标签（读取时 SDK 会返回标签信息）
                currentTag = session.getCurrentTag();  // 新增
                displayReadResult(data, selectedBankPosition, protocol);
                toast(R.string.single_read_success);
            }
        }));
```

---

### Task 8: 检查图标资源

**文件**：`app/src/main/res/drawable/`

**检查是否存在**：`rfid_filter_ic.xml` 或类似的过滤/掩码图标

**如果不存在，使用替代图标**：
- `rfid_scan_ic` （扫描图标）
- 或者创建一个简单的图标

**替代方案**（如果没有 filter 图标）：

在布局中去掉图标，只显示文字：

```xml
<TextView
    android:id="@+id/btn_single_fill_epc_mask"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/single_fill_mask"
    android:textSize="@dimen/sp_13"
    android:textColor="@color/rfid_primary"
    android:padding="@dimen/dp_8"
    android:background="?attr/selectableItemBackgroundBorderless" />
```

---

## ✅ 验证清单

### 编译验证

```bash
./gradlew assembleDebug
```

### 功能测试

#### 测试场景 1：读取 EPC 区域

**步骤**：
1. 读取区域选择 "EPC"
2. 点击"读取标签"
3. 观察结果显示

**预期**：
- ✅ 显示 EPC
- ❌ 不显示 TID
- ❌ 不显示芯片型号
- ✅ 显示数据
- ✅ 显示 RSSI（R2000 模块）
- ✅ EPC 右侧显示"掩码"按钮
- ✅ 数据右侧显示"掩码"按钮

#### 测试场景 2：读取 TID 区域

**步骤**：
1. 读取区域选择 "TID"
2. 点击"读取标签"
3. 观察结果显示

**预期**：
- ✅ 显示 EPC（SDK 返回）
- ✅ 显示 TID
- ✅ 显示芯片型号
- ✅ 显示数据
- ✅ 显示 RSSI（R2000 模块）

#### 测试场景 3：读取 USER 区域

**步骤**：
1. 读取区域选择 "USER"
2. 点击"读取标签"
3. 观察结果显示

**预期**：
- ✅ 显示 EPC（SDK 返回）
- ❌ 不显示 TID
- ❌ 不显示芯片型号
- ✅ 显示数据
- ✅ 显示 RSSI（R2000 模块）

#### 测试场景 4：填充 EPC 掩码

**步骤**：
1. 读取 EPC 成功
2. 点击 EPC 右侧的"掩码"按钮

**预期**：
- ✅ 掩码面板自动展开
- ✅ 存储区域自动选择 "EPC"
- ✅ 偏移量自动填充（32）
- ✅ 掩码数据自动填充（EPC 值）
- ✅ 长度自动计算（EPC 长度 * 4）
- ✅ 掩码自动启用
- ✅ 提示"掩码已启用"

#### 测试场景 5：填充数据掩码

**步骤**：
1. 读取 TID 成功
2. 点击数据右侧的"掩码"按钮

**预期**：
- ✅ 掩码面板自动展开
- ✅ 存储区域自动选择 "TID"
- ✅ 偏移量自动填充（0）
- ✅ 掩码数据自动填充（TID 值）
- ✅ 长度自动计算（TID 长度 * 4）
- ✅ 掩码自动启用
- ✅ 提示"掩码已启用"

#### 测试场景 6：掩码已启用时点击

**步骤**：
1. 已有掩码启用
2. 点击"掩码"按钮

**预期**：
- ✅ 提示"掩码已启用，请先取消掩码应用"
- ✅ 不执行填充操作

#### 测试场景 7：空数据时点击

**步骤**：
1. 未读取标签（数据为空）
2. 点击"掩码"按钮

**预期**：
- ✅ 提示"掩码数据不能为空"
- ✅ 不执行填充操作

---

## 📊 影响范围

### 修改文件

| 文件 | 变化 | 说明 |
|------|------|------|
| SingleTagFragment.java | +150 行 | 新增填充掩码逻辑、动态显示逻辑 |
| single_read_result_panel.xml | +40 行 | 添加掩码按钮 |
| strings.xml | +4 行 | 新增提示文本 |

### 不受影响

- ✅ 读取参数面板 - 保持不变
- ✅ 掩码配置面板 - 保持不变
- ✅ 写入/锁定/销毁功能 - 保持不变

---

## 🎯 技术要点

### 1. 动态显示逻辑

**核心思路**：根据读取的 Bank 位置决定显示哪些字段

```java
boolean isEpcBank = (protocol == TagProtocol.ISO_18000_6C && bankPosition == 1);
boolean isTidBank = (protocol == TagProtocol.ISO_18000_6C && bankPosition == 2);

// EPC：总是显示
epcGroup.setVisibility(View.VISIBLE);

// TID：仅 TID Bank 显示
tidView.setVisibility(isTidBank ? View.VISIBLE : View.GONE);

// 芯片型号：仅 TID Bank 显示
chipView.setVisibility(isTidBank && hasChipModel ? View.VISIBLE : View.GONE);
```

### 2. 掩码填充流程

```
用户点击"掩码"按钮
    ↓
检查数据是否为空 ← 空值提示
    ↓
检查掩码是否已启用 ← 已启用提示
    ↓
展开掩码面板（如果折叠）
    ↓
填充存储区域（根据按钮）
    ↓
填充偏移量（协议默认值）
    ↓
填充掩码数据（十六进制文本）
    ↓
填充长度（数据位数 = 字符数 * 4）
    ↓
启用掩码（调用 applyMask()）
    ↓
提示"掩码已启用"
```

### 3. 获取当前标签信息

**问题**：读取数据时，如何获取标签的 EPC？

**方案 1**（推荐）：从 `session.getCurrentTag()` 获取

```java
currentTag = session.getCurrentTag();
if (currentTag != null) {
    epcView.setText(currentTag.id);
}
```

**方案 2**：修改 API 返回完整标签信息

```java
// 返回包含数据和标签的结果
ReadResult result = session.readCurrentTagWithInfo(...);
byte[] data = result.data;
ReaderTag tag = result.tag;
```

**推荐使用方案 1**，更简单，无需修改 API。

---

## 💬 Git Commit Message

```
feat: 优化单标签读取结果显示和掩码快捷操作

新增功能：
- 读取结果根据读取区域动态显示字段
- EPC/数据右侧添加"掩码"快捷按钮
- 一键填充掩码并启用

动态显示逻辑：
- 读取 EPC 区域：显示 EPC + 数据
- 读取 TID 区域：显示 EPC + TID + 芯片型号 + 数据
- 读取其他区域：显示 EPC + 数据
- RSSI 仅 R2000 模块显示

掩码快捷操作：
- 点击 EPC 右侧"掩码"按钮 → 自动填充 EPC 掩码
- 点击数据右侧"掩码"按钮 → 自动填充当前 Bank 掩码
- 智能检查：空数据提示、已启用提示
- 自动展开掩码面板并启用

UI 改进：
- 新增"掩码"按钮（带图标）
- 动态显示/隐藏字段
- 友好的错误提示

测试：
- ✅ EPC/TID/USER 区域读取
- ✅ 动态字段显示
- ✅ EPC 掩码填充
- ✅ 数据掩码填充
- ✅ 错误提示（空数据、已启用）
- ✅ R2000 模块 RSSI 显示

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

---

## 📌 注意事项

### 1. 图标资源

如果 `rfid_filter_ic` 不存在，使用以下替代方案：
- 使用 `rfid_scan_ic`
- 或者去掉图标，只显示文字"掩码"

### 2. 当前标签获取

SDK 读取数据时会自动更新 `currentTag`，确保在 `displayReadResult` 中可以访问到。

### 3. 掩码面板交互

填充掩码后自动展开面板，用户可以看到填充的内容并进行调整。

### 4. 错误提示

所有错误提示使用 `toast()` 方法，简洁明了。

---

## ⏱️ 预计工作量

| 阶段 | 时间 | 任务 |
|------|------|------|
| **Phase 1** | 1h | 修改显示逻辑和布局 |
| **Phase 2** | 1h | 实现填充掩码逻辑 |
| **Phase 3** | 0.5h | 测试验证 |
| **Phase 4** | 10min | Git 提交 |
| **总计** | **2.5-3h** | 完整优化 |

---

**方案已准备就绪，可以开始执行！** 🚀
