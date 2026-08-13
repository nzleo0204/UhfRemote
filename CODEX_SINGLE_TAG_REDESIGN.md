# 单标签页面重新设计方案

## 📋 执行摘要

**目标**：重新设计单标签页面，将"读取标签"改为真正的数据读取功能。

**核心问题**：
- 当前"读取标签"按钮调用 `inventoryOnce()`（盘点），这是错误的
- 应该调用 `readTag()` 读取指定存储区域的数据
- 读取参数应该在页面上直接显示，不需要弹窗

**设计原则**：
- 参考 Windows Demo 和 Android 示例项目的 UI 设计
- 所有读取参数直接显示在页面上
- 协议切换时自动联动更新参数选项
- 条件显示：RSSI（仅R2000）、芯片型号（仅TID区域）

**预计时间**：4-6 小时

---

## 🎨 新 UI 设计

### 完整页面布局

```
╔═══════════════════════════════════════════════════════════════╗
║                       单标签操作                                ║
╠═══════════════════════════════════════════════════════════════╣
║                                                               ║
║ ┌─────────────────────────────────────────────────────────┐ ║
║ │ 🔍 掩码配置（可折叠）                 [未激活 ▼]         │ ║
║ ├─────────────────────────────────────────────────────────┤ ║
║ │ 存储区域： [EPC ▼]           偏移（位）： [32        ]  │ ║
║ │ 掩码数据（Hex）： [E280116060000000123456789ABC     ]  │ ║
║ │ 长度（位）： [96        ]  💡 12字节 = 96位              │ ║
║ │                      [🔓 启用掩码]                       │ ║
║ └─────────────────────────────────────────────────────────┘ ║
║                                                               ║
║ ┌─────────────────────────────────────────────────────────┐ ║
║ │ 📖 读取参数                                              │ ║
║ ├─────────────────────────────────────────────────────────┤ ║
║ │ 读取区域： [EPC ▼]          起始地址： [2          ]    │ ║
║ │ 读取长度： [4          ]    访问密码： [00000000   ]    │ ║
║ │                                                          │ ║
║ │ ━━ 6B/国标/国军标 参数 ━━ (协议自动显示/隐藏)           │ ║
║ │ 块长度/重试： [1          ]                              │ ║
║ │                                                          │ ║
║ │ ━━ 国标子区 ━━ (仅国标+用户区显示)                      │ ║
║ │ 用户子区： [子区1 ▼]                                     │ ║
║ │                                                          │ ║
║ │              [🔍 读取标签]                               │ ║
║ └─────────────────────────────────────────────────────────┘ ║
║                                                               ║
║ ┌─────────────────────────────────────────────────────────┐ ║
║ │ 📋 读取结果                                              │ ║
║ ├─────────────────────────────────────────────────────────┤ ║
║ │ EPC:  E280116060000000123456789ABC                      │ ║
║ │ TID:  E28011051200012345678901                          │ ║
║ │ 芯片: Impinj Monza M750 (仅TID时显示)                   │ ║
║ │ RSSI: -45 dBm (仅R2000模块显示)                         │ ║
║ │                                                          │ ║
║ │ 数据: 30313233343536373839414243 [📋 复制]              │ ║
║ └─────────────────────────────────────────────────────────┘ ║
║                                                               ║
║ ┌─────────────────────────────────────────────────────────┐ ║
║ │ ⚙️ 标签操作                                              │ ║
║ ├─────────────────────────────────────────────────────────┤ ║
║ │ [✏️ 写入数据]  [🔄 修改EPC]                             │ ║
║ │ [🔒 锁定区域]  [💀 销毁标签]                            │ ║
║ └─────────────────────────────────────────────────────────┘ ║
║                                                               ║
╚═══════════════════════════════════════════════════════════════╝
```

### 关键变化

**1. 移除"当前目标标签"卡片**
- 原来的 EPC/TID/芯片/RSSI 显示是盘点结果
- 现在改为"读取结果"，显示 `readTag()` 的返回数据

**2. 新增"读取参数"面板**
- 直接在页面上显示所有参数
- 不需要弹窗
- 参数根据协议自动联动

**3. 协议联动**
- 读取区域选项根据协议变化
- 6B/国标/国军标参数自动显示/隐藏
- 国标子区仅在国标协议+用户区时显示

---

## 📝 详细执行步骤

### Task 1: 创建新的读取参数面板布局

**文件**：`app/src/main/res/layout/single_read_params_panel.xml`

**完整代码**：
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    style="@style/RfidCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="@dimen/dp_16"
    android:layout_marginTop="@dimen/dp_16"
    android:orientation="vertical">

    <TextView
        style="@style/RfidGroupTitle"
        android:text="@string/single_read_params_title" />

    <!-- 第一行：读取区域 + 起始地址 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/dp_12"
        android:orientation="horizontal">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginEnd="@dimen/dp_8"
            android:orientation="vertical">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/single_read_bank"
                android:textSize="@dimen/sp_13"
                android:textColor="@color/rfid_text_secondary"
                android:paddingBottom="@dimen/dp_4" />

            <Spinner
                android:id="@+id/sp_single_read_bank"
                android:layout_width="match_parent"
                android:layout_height="@dimen/dp_44"
                android:background="@drawable/rfid_field_bg"
                android:paddingHorizontal="@dimen/dp_12" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/single_start_address"
                android:textSize="@dimen/sp_13"
                android:textColor="@color/rfid_text_secondary"
                android:paddingBottom="@dimen/dp_4" />

            <EditText
                android:id="@+id/et_single_read_address"
                android:layout_width="match_parent"
                android:layout_height="@dimen/dp_44"
                android:background="@drawable/rfid_field_bg"
                android:paddingHorizontal="@dimen/dp_12"
                android:inputType="number"
                android:textSize="@dimen/sp_15"
                android:textColor="@color/rfid_text" />
        </LinearLayout>
    </LinearLayout>

    <!-- 第二行：读取长度 + 访问密码 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/dp_12"
        android:orientation="horizontal">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginEnd="@dimen/dp_8"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_single_read_length_label"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/single_read_length"
                android:textSize="@dimen/sp_13"
                android:textColor="@color/rfid_text_secondary"
                android:paddingBottom="@dimen/dp_4" />

            <EditText
                android:id="@+id/et_single_read_length"
                android:layout_width="match_parent"
                android:layout_height="@dimen/dp_44"
                android:background="@drawable/rfid_field_bg"
                android:paddingHorizontal="@dimen/dp_12"
                android:inputType="number"
                android:textSize="@dimen/sp_15"
                android:textColor="@color/rfid_text" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/single_access_password"
                android:textSize="@dimen/sp_13"
                android:textColor="@color/rfid_text_secondary"
                android:paddingBottom="@dimen/dp_4" />

            <EditText
                android:id="@+id/et_single_read_password"
                android:layout_width="match_parent"
                android:layout_height="@dimen/dp_44"
                android:background="@drawable/rfid_field_bg"
                android:paddingHorizontal="@dimen/dp_12"
                android:inputType="textCapCharacters"
                android:textSize="@dimen/sp_15"
                android:textColor="@color/rfid_text"
                android:fontFamily="monospace"
                android:maxLength="8" />
        </LinearLayout>
    </LinearLayout>

    <!-- 6B/国标/国军标参数：块长度/重试次数 -->
    <LinearLayout
        android:id="@+id/group_single_auxiliary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/dp_12"
        android:orientation="vertical"
        android:visibility="gone">

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="@color/rfid_divider"
            android:layout_marginVertical="@dimen/dp_8" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/single_auxiliary_params"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_text_secondary"
            android:fontFamily="sans-serif-medium"
            android:paddingBottom="@dimen/dp_8" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tv_single_auxiliary_label"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/single_block_or_retry"
                android:textSize="@dimen/sp_13"
                android:textColor="@color/rfid_text_secondary"
                android:paddingBottom="@dimen/dp_4" />

            <EditText
                android:id="@+id/et_single_auxiliary"
                android:layout_width="match_parent"
                android:layout_height="@dimen/dp_44"
                android:background="@drawable/rfid_field_bg"
                android:paddingHorizontal="@dimen/dp_12"
                android:inputType="number"
                android:textSize="@dimen/sp_15"
                android:textColor="@color/rfid_text" />
        </LinearLayout>
    </LinearLayout>

    <!-- 国标子区 -->
    <LinearLayout
        android:id="@+id/group_single_gb_sub_bank"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/dp_12"
        android:orientation="vertical"
        android:visibility="gone">

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="@color/rfid_divider"
            android:layout_marginVertical="@dimen/dp_8" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/single_gb_sub_bank"
            android:textSize="@dimen/sp_13"
            android:textColor="@color/rfid_text_secondary"
            android:fontFamily="sans-serif-medium"
            android:paddingBottom="@dimen/dp_8" />

        <Spinner
            android:id="@+id/sp_single_gb_sub_bank"
            android:layout_width="match_parent"
            android:layout_height="@dimen/dp_44"
            android:background="@drawable/rfid_field_bg"
            android:paddingHorizontal="@dimen/dp_12" />
    </LinearLayout>

    <!-- 读取按钮 -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_single_read"
        style="@style/RfidPrimaryButton"
        android:layout_width="match_parent"
        android:layout_height="@dimen/dp_44"
        android:layout_marginTop="@dimen/dp_16"
        android:text="@string/single_read_tag"
        android:textSize="@dimen/sp_15"
        app:icon="@drawable/rfid_read_ic"
        app:iconPadding="@dimen/dp_8"
        app:iconSize="@dimen/dp_20" />
</LinearLayout>
```

---

### Task 2: 创建读取结果面板布局

**文件**：`app/src/main/res/layout/single_read_result_panel.xml`

**完整代码**：
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    style="@style/RfidCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="@dimen/dp_16"
    android:layout_marginTop="@dimen/dp_16"
    android:orientation="vertical">

    <TextView
        style="@style/RfidGroupTitle"
        android:text="@string/single_read_result_title" />

    <!-- EPC -->
    <include layout="@layout/rfid_single_info_epc" />

    <!-- TID -->
    <include layout="@layout/rfid_single_info_tid" />

    <!-- 芯片型号（仅TID时显示） -->
    <include layout="@layout/rfid_single_info_chip" />

    <!-- RSSI（仅R2000模块显示） -->
    <include layout="@layout/rfid_single_info_rssi" />

    <!-- 读取数据 -->
    <LinearLayout
        android:id="@+id/group_single_read_data"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/dp_8"
        android:orientation="vertical"
        android:visibility="gone">

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

        <TextView
            android:id="@+id/tv_single_read_data"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="@dimen/dp_12"
            android:background="@drawable/rfid_field_bg"
            android:textSize="@dimen/sp_15"
            android:textColor="@color/rfid_primary"
            android:fontFamily="monospace"
            android:textIsSelectable="true" />
    </LinearLayout>
</LinearLayout>
```

---

### Task 3: 修改主布局文件

**文件**：`app/src/main/res/layout/single_tag_fragment.xml`

**操作**：替换"当前目标标签"卡片为新的面板

**修改前**（删除第 36-96 行）：
```xml
<LinearLayout
    style="@style/RfidCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="@dimen/dp_16"
    android:orientation="vertical">
    <!-- 当前目标标签卡片 -->
    ...
</LinearLayout>
```

**修改后**（替换为）：
```xml
<!-- 读取参数面板 -->
<include layout="@layout/single_read_params_panel" />

<!-- 读取结果面板 -->
<include layout="@layout/single_read_result_panel" />
```

---

### Task 4: 修改 SingleTagFragment.java

**文件**：`app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java`

**操作 1**：更新字段声明（第 47-70 行附近）

**删除**：
```java
private TextView readButton;
private TextView idLabelView;
private TextView tidLabelView;
private TextView chipLabelView;
private TextView rssiLabelView;
private TextView targetHintView;
```

**新增**：
```java
// 读取参数控件
private Spinner readBankSpinner;
private Spinner gbSubBankSpinner;
private EditText readAddressView;
private EditText readLengthView;
private EditText readPasswordView;
private EditText auxiliaryView;
private View auxiliaryGroup;
private View gbSubBankGroup;
private TextView auxiliaryLabel;
private TextView readLengthLabel;
private MaterialButton readButton;

// 读取结果控件
private View readDataGroup;
private TextView readDataView;
private TextView copyDataButton;
private TextView idLabelView;
private TextView tidLabelView;
private TextView chipLabelView;
private TextView rssiLabelView;
```

**操作 2**：修改 initView() 方法（第 76-110 行附近）

**删除**：
```java
readButton = findViewById(R.id.tv_single_read);
idLabelView = findViewById(R.id.tv_single_id_label);
tidLabelView = findViewById(R.id.tv_single_tid_label);
chipLabelView = findViewById(R.id.tv_single_chip_label);
rssiLabelView = findViewById(R.id.tv_single_rssi_label);
targetHintView = findViewById(R.id.tv_single_target_hint);
```

**新增**：
```java
// 读取参数控件
readBankSpinner = findViewById(R.id.sp_single_read_bank);
gbSubBankSpinner = findViewById(R.id.sp_single_gb_sub_bank);
readAddressView = findViewById(R.id.et_single_read_address);
readLengthView = findViewById(R.id.et_single_read_length);
readPasswordView = findViewById(R.id.et_single_read_password);
auxiliaryView = findViewById(R.id.et_single_auxiliary);
auxiliaryGroup = findViewById(R.id.group_single_auxiliary);
gbSubBankGroup = findViewById(R.id.group_single_gb_sub_bank);
auxiliaryLabel = findViewById(R.id.tv_single_auxiliary_label);
readLengthLabel = findViewById(R.id.tv_single_read_length_label);
readButton = findViewById(R.id.btn_single_read);

// 读取结果控件
readDataGroup = findViewById(R.id.group_single_read_data);
readDataView = findViewById(R.id.tv_single_read_data);
copyDataButton = findViewById(R.id.tv_single_copy_data);
idLabelView = findViewById(R.id.tv_single_id_label);
tidLabelView = findViewById(R.id.tv_single_tid_label);
chipLabelView = findViewById(R.id.tv_single_chip_label);
rssiLabelView = findViewById(R.id.tv_single_rssi_label);

// 初始化读取参数
initReadParams();

// 绑定事件
readButton.setOnClickListener(view -> readTag());
copyDataButton.setOnClickListener(view -> copyReadData());
```

**操作 3**：新增 initReadParams() 方法

**插入位置**：在 initView() 方法之后

**完整代码**：
```java
private void initReadParams() {
    TagProtocol protocol = readerState.getProtocol();
    
    // 设置 Bank 选项
    String[] banks = bankLabels(protocol);
    readBankSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, banks));
    
    // 设置国标子区选项
    gbSubBankSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            getResources().getStringArray(R.array.single_gb_sub_bank_labels)));
    
    // 设置默认值
    readBankSpinner.setSelection(protocol == TagProtocol.ISO_18000_6C ? 1 : 0);  // EPC或第一个
    readAddressView.setText(protocol == TagProtocol.ISO_18000_6C ? "2" : "0");
    readLengthView.setText(protocol == TagProtocol.ISO_18000_6B ? "8" : "4");
    readPasswordView.setText("00000000");
    auxiliaryView.setText(protocol == TagProtocol.ISO_18000_6B ? "3" : "4");
    
    // 协议联动
    updateReadParamsForProtocol(protocol);
    
    // Bank 选择监听
    readBankSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            updateGbSubBankVisibility(position);
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    });
}

private void updateReadParamsForProtocol(TagProtocol protocol) {
    // 更新长度标签
    if (protocol == TagProtocol.ISO_18000_6B) {
        readLengthLabel.setText(R.string.single_read_length_byte);
    } else {
        readLengthLabel.setText(R.string.single_read_length_word);
    }
    
    // 显示/隐藏辅助参数
    if (protocol == TagProtocol.ISO_18000_6C) {
        auxiliaryGroup.setVisibility(View.GONE);
    } else {
        auxiliaryGroup.setVisibility(View.VISIBLE);
        if (protocol == TagProtocol.ISO_18000_6B) {
            auxiliaryLabel.setText(R.string.single_retry_count_hint);
        } else {
            auxiliaryLabel.setText(R.string.single_block_length_hint);
        }
    }
    
    // 更新国标子区可见性
    updateGbSubBankVisibility(readBankSpinner.getSelectedItemPosition());
}

private void updateGbSubBankVisibility(int bankPosition) {
    TagProtocol protocol = readerState.getProtocol();
    boolean showGbSubBank = (protocol == TagProtocol.GB_T_29768) && (bankPosition == 3);
    gbSubBankGroup.setVisibility(showGbSubBank ? View.VISIBLE : View.GONE);
}
```

**操作 4**：修改 readTag() 方法（第 199-211 行）

**修改前**：
```java
@SingleClick
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
            toast(getString(R.string.single_read_failed, rootMessage(error))); 
        }
    }));
}
```

**修改后**：
```java
@SingleClick
private void readTag() {
    if (session == null || !session.getState().isConnected()) {
        requireReaderOnline();
        return;
    }
    
    try {
        // 解析参数
        TagProtocol protocol = readerState.getProtocol();
        byte[] password = parsePassword(readPasswordView.getText().toString());
        int bank = ProtocolEncoding.encodeBank(protocol,
                readBankSpinner.getSelectedItemPosition(),
                gbSubBankSpinner.getSelectedItemPosition());
        int address = parseUnsigned(readAddressView, R.string.single_start_address);
        int length = parseUnsigned(readLengthView, R.string.single_read_length);
        int blockOrRetry = protocol == TagProtocol.ISO_18000_6C ? 0
                : parseUnsigned(auxiliaryView, R.string.single_block_or_retry);
        int encodedAddress = ProtocolEncoding.encodeAddress(protocol, address, blockOrRetry);
        
        int selectedBankPosition = readBankSpinner.getSelectedItemPosition();
        
        // 执行读取
        readButton.setEnabled(false);
        readButton.setText(R.string.single_reading);
        
        session.readCurrentTag(protocol, length, encodedAddress, bank, password)
                .whenComplete((data, error) -> requireActivity().runOnUiThread(() -> {
                    readButton.setEnabled(true);
                    readButton.setText(R.string.single_read_tag);
                    
                    if (error != null) {
                        toast(getString(R.string.single_read_failed, rootMessage(error)));
                        readDataGroup.setVisibility(View.GONE);
                    } else {
                        displayReadResult(data, selectedBankPosition, protocol);
                        toast(R.string.single_read_success);
                    }
                }));
    } catch (IllegalArgumentException error) {
        toast(error.getMessage());
    }
}
```

**操作 5**：新增 displayReadResult() 方法

**插入位置**：在 readTag() 方法之后

**完整代码**：
```java
private void displayReadResult(byte[] data, int bankPosition, TagProtocol protocol) {
    // 显示十六进制数据
    String hexData = HexCodec.encode(data);
    readDataView.setText(hexData);
    readDataGroup.setVisibility(View.VISIBLE);
    
    // 更新 EPC 显示（如果是读取 EPC 区域）
    if (protocol == TagProtocol.ISO_18000_6C && bankPosition == 1) {
        idLabelView.setText(hexData);
    }
    
    // 更新 TID 显示（如果是读取 TID 区域）
    if (protocol == TagProtocol.ISO_18000_6C && bankPosition == 2) {
        tidLabelView.setText(hexData);
        
        // 显示芯片型号
        String chipModel = ChipModelFormatter.formatFromTid(hexData);
        if (!chipModel.isEmpty()) {
            chipLabelView.setText(chipModel);
            chipLabelView.setVisibility(View.VISIBLE);
        }
    } else {
        chipLabelView.setVisibility(View.GONE);
    }
    
    // 条件显示 RSSI（仅 R2000 模块）
    if (configuration != null && configuration.moduleInfo != null
            && configuration.moduleInfo.subtype.isR2000Style()) {
        // RSSI 从当前标签获取（需要先盘点）
        // 注：读取数据时不返回 RSSI，需要单独处理
        rssiLabelView.setVisibility(View.GONE);
    } else {
        rssiLabelView.setVisibility(View.GONE);
    }
}

private void copyReadData() {
    String data = readDataView.getText().toString();
    if (data.isEmpty()) {
        toast(R.string.single_no_data_to_copy);
        return;
    }
    
    ClipboardManager clipboard = (ClipboardManager) 
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("RFID Data", data));
    toast(R.string.common_copied);
}
```

**操作 6**：修改协议监听器（第 163-177 行）

**修改前**：
```java
@Override
public void onReaderStateChanged(ReaderState state) {
    TagProtocol previousProtocol = readerState.getProtocol();
    readerState = state;
    if (previousProtocol != state.getProtocol()) {
        updateMaskBanks(state.getProtocol());
    }
    // ...
}
```

**修改后**：
```java
@Override
public void onReaderStateChanged(ReaderState state) {
    TagProtocol previousProtocol = readerState.getProtocol();
    readerState = state;
    if (previousProtocol != state.getProtocol()) {
        updateMaskBanks(state.getProtocol());
        updateReadBanks(state.getProtocol());  // 新增：更新读取区域
    }
    // ...
}

private void updateReadBanks(TagProtocol protocol) {
    String[] banks = bankLabels(protocol);
    readBankSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, banks));
    readBankSpinner.setSelection(protocol == TagProtocol.ISO_18000_6C ? 1 : 0);
    updateReadParamsForProtocol(protocol);
}
```

---

### Task 5: 在 ReaderSessionManager 中添加读取方法

**文件**：`app/src/main/java/com/leo/remote/reader/ReaderSessionManager.java`

**操作**：在 getSingleTagMask() 方法之后添加新方法

**完整代码**：
```java
/**
 * 读取当前目标标签的数据。
 *
 * <p>通过单标签掩码定位目标标签，然后读取指定存储区域的数据。
 *
 * @param protocol 射频协议
 * @param length 读取长度
 * @param address 起始地址
 * @param bank 存储区域
 * @param password 访问密码
 * @return 读取的数据
 */
public CompletableFuture<byte[]> readCurrentTag(TagProtocol protocol, int length,
        int address, int bank, byte[] password) {
    return coordinator.readCurrentTag(protocol, length, address, bank, password);
}
```

---

### Task 6: 在 ReaderSessionCoordinator 中实现读取方法

**文件**：`app/src/main/java/com/leo/remote/reader/ReaderSessionCoordinator.java`

**操作**：在 getSingleTagMask() 方法之后添加新方法

**完整代码**：
```java
/**
 * 读取当前目标标签的数据。
 *
 * <p>如果配置了单标签掩码，将应用掩码后读取；否则直接读取。
 */
public CompletableFuture<byte[]> readCurrentTag(TagProtocol protocol, int length,
        int address, int bank, byte[] password) {
    return withTargetMask(() -> tagOperations.read(protocol, length, address, bank, password));
}
```

**说明**：
- 使用现有的 `withTargetMask()` 包装器
- 自动处理掩码的应用和恢复
- 与写入/锁定/销毁保持一致的逻辑

---

### Task 7: 添加 ChipModelFormatter 的 TID 格式化方法

**文件**：`app/src/main/java/com/leo/remote/reader/ChipModelFormatter.java`

**操作**：添加新的静态方法

**完整代码**：
```java
/**
 * 从 TID 十六进制字符串中提取芯片型号。
 *
 * @param tidHex TID 的十六进制字符串
 * @return 芯片型号，如果无法识别则返回空字符串
 */
public static String formatFromTid(String tidHex) {
    if (tidHex == null || tidHex.length() < 8) {
        return "";
    }
    
    try {
        // TID 前 4 字节（8 个十六进制字符）
        String prefix = tidHex.substring(0, 8).toUpperCase(Locale.US);
        int tidPrefix = (int) Long.parseLong(prefix, 16);
        
        // 创建临时 ReaderTag 用于格式化
        ReaderTag tempTag = new ReaderTag("", "", 0, 0, 0, "", tidPrefix);
        return format(tempTag);
    } catch (NumberFormatException e) {
        return "";
    }
}
```

---

### Task 8: 更新字符串资源

**文件**：`app/src/main/res/values/strings.xml`

**操作**：在单标签相关字符串区域添加新字符串

**完整代码**：
```xml
<!-- 单标签页 - 读取参数 -->
<string name="single_read_params_title">读取参数</string>
<string name="single_read_bank">读取区域</string>
<string name="single_start_address">起始地址</string>
<string name="single_read_length">读取长度</string>
<string name="single_read_length_word">读取长度（Word）</string>
<string name="single_read_length_byte">读取长度（Byte）</string>
<string name="single_access_password">访问密码</string>
<string name="single_auxiliary_params">协议参数</string>
<string name="single_block_or_retry">块长度 / 重试次数</string>
<string name="single_block_length_hint">块长度</string>
<string name="single_retry_count_hint">重试次数</string>
<string name="single_gb_sub_bank">用户子区</string>

<!-- 单标签页 - 读取结果 -->
<string name="single_read_result_title">读取结果</string>
<string name="single_read_data">数据</string>
<string name="single_read_success">读取成功</string>
<string name="single_read_failed">读取失败：%s</string>
<string name="single_reading">读取中&#8230;</string>
<string name="single_no_data_to_copy">暂无数据</string>
```

---

### Task 9: 删除已废弃的方法

**文件**：`app/src/main/java/com/leo/remote/reader/ReaderTagOperations.java`

**操作**：删除或注释掉 readSingleTag() 方法（第 43-49 行）

**删除代码**：
```java
@Nullable
ReaderTag readSingleTag() throws ReaderException {
    ReaderTag tag = gateway.inventoryOnce(1500);
    currentTag = tag;
    publisher.publishCurrentTag(tag);
    return tag;
}
```

**说明**：
- 该方法调用 `inventoryOnce()`，与单标签页面的需求不符
- 删除后，避免混淆
- 如果其他地方引用，需要一并删除

---

## ✅ 验证清单

### 编译验证

```bash
cd /Users/lei/Projects/UhfRemote
./gradlew assembleDebug
```

**预期结果**：编译成功，无错误

### 功能测试

#### 测试场景 1：ISO 18000-6C 协议读取 EPC

**前提**：
- 连接 UHF 读写器
- 配置单标签掩码指向目标标签
- 射频协议为 ISO 18000-6C

**步骤**：
1. 打开单标签页面
2. 读取区域选择"EPC"
3. 起始地址输入"2"
4. 读取长度输入"4"
5. 访问密码输入"00000000"
6. 点击"读取标签"

**预期结果**：
- ✅ 读取成功
- ✅ 显示十六进制数据
- ✅ EPC 标签更新
- ✅ 不显示芯片型号（非 TID 区域）
- ✅ 不显示 RSSI

#### 测试场景 2：ISO 18000-6C 协议读取 TID

**步骤**：
1. 读取区域选择"TID"
2. 起始地址输入"0"
3. 读取长度输入"6"
4. 点击"读取标签"

**预期结果**：
- ✅ 读取成功
- ✅ 显示十六进制数据
- ✅ TID 标签更新
- ✅ **显示芯片型号**（如 "Impinj Monza M750"）
- ✅ 不显示 RSSI

#### 测试场景 3：ISO 18000-6B 协议读取

**前提**：
- 切换射频协议为 ISO 18000-6B

**步骤**：
1. 观察页面变化
2. 读取区域选择"UID"
3. 起始地址输入"0"
4. 读取长度输入"8"（字节）
5. 重试次数输入"3"
6. 点击"读取标签"

**预期结果**：
- ✅ Bank 选项自动更新为"UID / USER"
- ✅ 长度标签显示"读取长度（Byte）"
- ✅ **显示重试次数参数**
- ✅ 读取成功

#### 测试场景 4：GB29768 协议读取用户区

**前提**：
- 切换射频协议为 GB29768

**步骤**：
1. 观察页面变化
2. 读取区域选择"用户区"
3. 观察是否显示用户子区选择
4. 选择"子区 1"
5. 点击"读取标签"

**预期结果**：
- ✅ Bank 选项自动更新为"标签信息区 / 编码区 / 安全区 / 用户区"
- ✅ **显示用户子区选择**
- ✅ **显示块长度参数**
- ✅ 读取成功

#### 测试场景 5：协议切换联动

**步骤**：
1. 在 ISO 18000-6C 协议下设置参数
2. 切换到配置页，修改协议为 GB29768
3. 返回单标签页

**预期结果**：
- ✅ Bank 选项自动更新
- ✅ 辅助参数自动显示
- ✅ 默认值根据协议调整

#### 测试场景 6：复制数据

**步骤**：
1. 成功读取标签数据
2. 点击"复制"按钮
3. 粘贴到其他应用

**预期结果**：
- ✅ 复制成功提示
- ✅ 粘贴内容为十六进制数据

#### 测试场景 7：R2000 模块 RSSI 显示

**前提**：
- 使用 R2000 或 R2000Plus 模块

**步骤**：
1. 读取标签数据

**预期结果**：
- ✅ 不显示 RSSI（读取数据时不返回 RSSI）

**注**：RSSI 只在盘点时返回，读取数据时不返回。如果需要显示 RSSI，需要先盘点标签再读取。

---

## 📊 影响范围

### 新增文件

| 文件 | 行数 | 说明 |
|------|------|------|
| single_read_params_panel.xml | ~180 | 读取参数面板布局 |
| single_read_result_panel.xml | ~60 | 读取结果面板布局 |

### 修改文件

| 文件 | 变化 | 说明 |
|------|------|------|
| single_tag_fragment.xml | -60 行, +2 行 | 删除当前标签卡片，引入新面板 |
| SingleTagFragment.java | +150 行 | 新增读取逻辑，删除盘点逻辑 |
| ReaderSessionManager.java | +15 行 | 新增 readCurrentTag API |
| ReaderSessionCoordinator.java | +10 行 | 新增 readCurrentTag 实现 |
| ChipModelFormatter.java | +20 行 | 新增 formatFromTid 方法 |
| ReaderTagOperations.java | -7 行 | 删除 readSingleTag 方法 |
| strings.xml | +15 行 | 新增字符串资源 |

### 不受影响

- ✅ 写入/锁定/销毁功能 - 保持不变
- ✅ 掩码配置面板 - 保持不变
- ✅ 协议监听器 - 仅新增读取区域联动
- ✅ 盘点页面 - 完全独立，不受影响

---

## 🎯 技术要点

### 1. 为什么不需要 inventoryOnce？

**单标签读取流程**：
```
用户点击"读取标签"
    ↓
解析读取参数（Bank、地址、长度、密码）
    ↓
调用 withTargetMask() 包装器
    ↓
    ├─ 停止盘点
    ├─ 应用单标签掩码
    ├─ 调用 gateway.readTag()  ← SDK 内部会先定位标签
    └─ 恢复掩码
    ↓
返回读取的数据
```

**SDK 的 readTag() 会自动执行**：
1. 根据掩码定位标签
2. 发送读取命令
3. 返回数据

**因此不需要单独调用 inventoryOnce()**。

### 2. RSSI 显示问题

**问题**：读取数据时不返回 RSSI

**原因**：
- `readTag()` 只返回读取的数据（byte[]）
- 不返回 RSSI、TID 等元数据

**解决方案**：
- 方案 A：不显示 RSSI（当前实现）
- 方案 B：读取前先调用 `inventoryOnce()` 获取 RSSI，然后再读取数据
- 方案 C：在掩码配置面板中显示"上次盘点的 RSSI"

**推荐**：方案 A（保持简洁）

### 3. 协议差异处理

| 协议 | 长度单位 | 辅助参数 | 特殊处理 |
|------|---------|---------|---------|
| ISO 18000-6C | Word | 无 | - |
| ISO 18000-6B | Byte | 重试次数 | 地址编码 |
| GB29768 | Word | 块长度 | Bank编码、子区编码 |
| GJB7377 | Word | 块长度 | Bank编码 |

**地址编码**（ProtocolEncoding.encodeAddress）：
```java
// 6B: address | (retryCount << 24)
// GB/GJB: address | (blockLength << 24)
// 6C: address（不需要编码）
```

### 4. Bank 编码

**ISO 18000-6C**：
- 0 = Reserved
- 1 = EPC
- 2 = TID
- 3 = USER

**GB29768**：
- (0 << 4) = 标签信息区
- (1 << 4) = 编码区
- (2 << 4) = 安全区
- (3 << 4) | subBank = 用户区 + 子区

**ISO 18000-6B**：
- 0 = UID
- 1 = USER

---

## 🚀 执行顺序

### Phase 1: 布局文件（1 小时）

1. 创建 `single_read_params_panel.xml`
2. 创建 `single_read_result_panel.xml`
3. 修改 `single_tag_fragment.xml`
4. 更新 `strings.xml`

### Phase 2: Java 代码（2 小时）

5. 修改 `SingleTagFragment.java`：
   - 更新字段声明
   - 修改 initView()
   - 添加 initReadParams()
   - 修改 readTag()
   - 添加 displayReadResult()
   - 添加 updateReadBanks()
6. 修改 `ReaderSessionManager.java`
7. 修改 `ReaderSessionCoordinator.java`
8. 修改 `ChipModelFormatter.java`
9. 删除 `ReaderTagOperations.readSingleTag()`

### Phase 3: 验证测试（1.5 小时）

10. 编译验证
11. 功能测试（7 个场景）

### Phase 4: 提交（15 分钟）

12. Git commit

---

## 📌 注意事项

### 1. 数据显示

- **十六进制格式**：所有数据以大写十六进制显示
- **可选择**：使用 `textIsSelectable="true"` 允许用户选择复制
- **等宽字体**：使用 `fontFamily="monospace"` 便于阅读

### 2. 错误处理

**常见错误**：
- 访问密码错误 → 提示"密码错误"
- 地址超出范围 → 提示"地址无效"
- 掩码未匹配 → 提示"未找到匹配标签"
- 超时 → 提示"读取超时"

**错误提示格式**：
```java
toast(getString(R.string.single_read_failed, rootMessage(error)));
```

### 3. 用户体验

**加载状态**：
- 按钮禁用
- 文本变为"读取中..."
- 完成后恢复

**成功反馈**：
- Toast 提示"读取成功"
- 显示读取结果
- 自动滚动到结果区域

### 4. 向后兼容

**保留的功能**：
- ✅ 掩码配置面板
- ✅ 写入/锁定/销毁操作
- ✅ 协议监听器

**删除的功能**：
- ❌ `readSingleTag()` 方法（调用 inventoryOnce）
- ❌ "当前目标标签"卡片（显示盘点结果）

---

## 💬 Git Commit Message

```
refactor: 重新设计单标签页面，修复读取功能

核心变更：
- 将"读取标签"从盘点（inventoryOnce）改为真正的数据读取（readTag）
- 参数直接显示在页面上，不使用弹窗
- 协议切换时自动联动更新参数选项

新增功能：
- 读取参数面板：Bank、地址、长度、密码、协议参数
- 读取结果面板：数据、芯片型号（TID时）、复制功能
- 协议适配：6C/6B/GB/GJB 参数自动调整

UI 改进：
- 移除"当前目标标签"卡片（盘点结果）
- 新增"读取参数"面板（单行布局，紧凑）
- 新增"读取结果"面板（条件显示芯片和RSSI）

技术实现：
- 使用 withTargetMask() 自动处理掩码
- 新增 ChipModelFormatter.formatFromTid()
- 协议联动：Bank选项、辅助参数、子区显示

测试：
- ✅ ISO 18000-6C 读取 EPC/TID/USER
- ✅ ISO 18000-6B 读取 UID
- ✅ GB29768 读取用户区+子区
- ✅ 协议切换自动联动
- ✅ TID 区域显示芯片型号

影响：
- 新增 2 个布局文件
- 修改 SingleTagFragment.java（+150行）
- 删除 ReaderTagOperations.readSingleTag()
- 向后兼容：写入/锁定/销毁不受影响

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

---

## 📚 参考文档

- UHF SDK 文档：`readTag` API 说明
- 参考实现：`withTargetMask()` (ReaderSessionCoordinator.java:625-673)
- 协议编码：`ProtocolEncoding.java`
- 掩码配置：`InventoryMaskConfig.java`

---

## 🎉 预期效果

修复完成后：

1. **功能正确**
   - "读取标签"真正读取数据，不再盘点
   - 通过掩码精确定位目标标签
   - 支持所有 4 种射频协议

2. **UI 简洁**
   - 所有参数在一个面板
   - 不需要弹窗
   - 结果直接显示

3. **协议适配**
   - Bank 选项自动更新
   - 辅助参数自动显示/隐藏
   - 默认值根据协议调整

4. **条件显示**
   - 芯片型号仅 TID 区域显示
   - RSSI 暂不显示（读取数据时不返回）

**预计总工作量：4-6 小时即可完成完整重新设计和测试。**
