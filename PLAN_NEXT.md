# UhfRemote 问题修复执行计划

> 生成时间：2026-08-04  
> 涉及 7 个独立问题，可逐条按序执行。  
> 关联代码均已现场验证，注有文件路径和行号。

---

## Issue 1 — 旗连(RM8011)模块功率固定 0~20


### 根因
`Rm8011PowerLevels.levels()` 按序列号推断功率档位（20/26/30dBm 等档位表），但旗连模块只支持 0~20 dBm 连续档。  
当前 `applyModuleUi` 中 RM8011 分支隐藏 SeekBar，改用点击 `powerValueView` 弹出离散列表对话框。

### 修改清单

#### 1-A `Rm8011PowerLevels.java`（保留类，仅废弃档位推断逻辑）
不删文件（免得影响 test），但在 `levels()` 方法上加 `@Deprecated` 注释，新增：
```java
/** 旗连模块固定使用 0-20 dBm（以 10 为单位）的连续 SeekBar 档位。*/
public static int maxTenthsDbm() { return 200; }
```

#### 1-B `ReaderConfigFragment.java`
- `applyModuleUi()`（第 801 行附近）：删除 RM8011 对 SeekBar 的特殊隐藏分支，统一使用：
  ```java
  // RM8011 max=20，其余 max=30（RM610 CMT 也是 max=20）
  int maxDbm = (subtype == ModuleSubtype.RM8011
              || subtype == ModuleSubtype.RM610) ? 20 : 30;
  powerSeekBar.setMax(maxDbm);
  powerSeekBar.setVisibility(isRm610Discrete ? View.GONE : View.VISIBLE);
  powerRangeView.setVisibility(isRm610Discrete ? View.GONE : View.VISIBLE);
  powerValueView.setClickable(isRm610Discrete && connected);  // 只有 RM610 非CMT 才点击弹窗
  ```
- `onReaderConfigurationChanged()`（第 322 行附近）：删除 RM8011 分支的 `powerSeekBar.setVisibility(GONE)`，改为正常走 SeekBar 更新路径。
- 删除对 `showRm8011PowerDialog()` 的所有调用（第 220 行 `powerValueView.setOnClickListener`）。
- 可选：删除 `showRm8011PowerDialog()` 方法本身（第 749-770 行）。
- `powerMaxView`：RM8011 时显示"20 dBm"，与 RM610 CMT 共用 `R.string.config_power_max_rm610`（若字符串内容是"20 dBm"即可复用，否则新增 string）。

#### 1-C `ReaderConfigCache.java`
`getDefaultConfiguration(RM8011)` 当前返回 `power=100`（10 dBm），不需改。

---

## Issue 2 — 配置页缺少手动断开设备按钮

### 根因
`bindTransportRows(ble, connected)` 只在 `!connected` 时显示操作区域，已连接时两个操作行都隐藏，没有断开入口。

### 修改清单

#### 2-A `reader_config_fragment.xml`
在 `ll_config_ble_actions` 和 `ll_config_wifi_actions` 的**同级**位置，新增一个断开行：
```xml
<LinearLayout
    android:id="@+id/ll_config_disconnect"
    android:layout_width="match_parent"
    android:layout_height="@dimen/dp_48"
    android:gravity="center_vertical"
    android:visibility="gone">

    <TextView
        android:id="@+id/tv_config_connected_target"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="@color/rfid_text"
        android:textSize="@dimen/sp_15" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_config_disconnect"
        style="@style/RfidTextButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/rfid_danger"
        android:text="@string/config_disconnect" />
</LinearLayout>
```
> 在 `strings.xml` 中新增 `config_disconnect = "断开连接"`。

#### 2-B `ReaderConfigFragment.java`
新增字段：
```java
private View disconnectRow;
private TextView connectedTargetView;
```
`initView()` 末尾绑定：
```java
disconnectRow       = findViewById(R.id.ll_config_disconnect);
connectedTargetView = findViewById(R.id.tv_config_connected_target);
findViewById(R.id.btn_config_disconnect).setOnClickListener(view -> disconnectDevice());
```
`bindTransportRows(boolean ble, boolean connected)` 改为三态：
```java
private void bindTransportRows(boolean ble, boolean connected) {
    bleActions.setVisibility(!connected && ble   ? View.VISIBLE : View.GONE);
    wifiActions.setVisibility(!connected && !ble ? View.VISIBLE : View.GONE);
    disconnectRow.setVisibility(connected        ? View.VISIBLE : View.GONE);
    if (connected) {
        // 连接成功时同步显示目标名称
        String target = readerState.getTransport() == TransportType.BLE
                ? bleDisplayName(readerState.getDeviceName()) : readerState.getAddress();
        connectedTargetView.setText(target);
    }
}
```
新增断开方法：
```java
private void disconnectDevice() {
    if (session != null) {
        session.disconnect(DisconnectReason.USER);
    }
}
```

---

## Issue 3 — 底部导航栏选中时亮色模式图标与文字颜色不一致

### 根因
图标 tint 定义在 VectorDrawable 的 `android:tint="@color/rfid_nav_icon_selector"` 属性中，需要 Drawable 感知 View 的选中状态（`isStateful()=true`）。  
`ContextCompat.getDrawable()` 返回的是共享实例（未 mutate），多个 item 共用同一个 Drawable 对象时，状态传播会互相干扰，导致图标 tint 在亮色模式下不能正确切换到 white。  
文字走 `ShapeTextView` 的 `shape_textSelectedColor`，自有状态，不受影响。

### 修改清单

#### 3-A `NavigationAdapter.java`
在 `onBindView` 中，不依赖 Drawable 的 state propagation，直接通过 `ImageViewCompat.setImageTintList` 设置独立 tint：
```java
@Override
public void onBindView(int position) {
    NavigationItem item = getItem(position);
    boolean selected = (mSelectedPosition == position);
    // 先 mutate drawable，避免共享实例互相污染
    Drawable icon = item.drawable == null ? null : item.drawable.mutate();
    mIconView.setImageDrawable(icon);
    mTitleView.setText(item.text);
    itemView.setSelected(selected);
    mIconView.setSelected(selected);
    mTitleView.setSelected(selected);
    // 亮色/暗色模式统一：选中=白色，未选中=rfid_text_muted
    int tintColor = selected
            ? ContextCompat.getColor(getContext(), R.color.white)
            : ContextCompat.getColor(getContext(), R.color.rfid_text_muted);
    ImageViewCompat.setImageTintList(mIconView, ColorStateList.valueOf(tintColor));
}
```
> 需要在文件头 import `androidx.core.widget.ImageViewCompat` 和 `android.content.res.ColorStateList`。

---

## Issue 4 — 盘点数据列表显示问题（全盘重新设计）

### 现状分析
- `InventoryItem.id` = EPC（按 `epcLength` 截断，长度正确）
- `InventoryItem.data` = 副区数据（TID/USER/RESERVED，按 `dataLength` 截断）
- `inventory_item.xml` 当前布局：序号 + 垂直两行(id/data) + 次数 + rssi + chip，高度固定 64dp
- 问题：无标题区分 EPC 与副区；EPC/TID 超长时无省略；chip 字体偏小但显示正常；EPC 与副区颜色相同

### Item 布局重新设计（`inventory_item.xml`）
```
┌──────────────────────────────────────────────────────┐
│  [序号]  ┊  EPC  [EPC值，超长省略]    ┊  次数  ┊ RSSI  │
│          ┊  TID  [TID值，超长省略]    ┊  芯片型号(完整) │
└──────────────────────────────────────────────────────┘
```
新 layout 要点：
- 整体高度改为 `wrap_content`，最小高度 `@dimen/dp_56`
- 中间主体区用垂直 LinearLayout：
  - 第一行（EPC 行）：水平排列 — `"EPC"` 小标（sp_11, rfid_text_muted, 固定28dp宽） + EPC hex（monospace, sp_13, rfid_primary_soft, ellipsize=end, layout_weight=1）
  - 第二行（副区行，`data` 不为空时才 VISIBLE）：水平排列 — `"TID"`/`"USER"`/`"RSRV"` 小标（sp_11, rfid_text_muted, 固定28dp宽） + data hex（monospace, sp_13, rfid_text_secondary, ellipsize=end, layout_weight=1）
  - 第三行（芯片行，`chip` 不为空时才 VISIBLE）：水平排列 — `"芯片"` 小标（sp_11, rfid_text_muted, 固定28dp宽） + chip 名（sp_13, rfid_text, wrap_content, 允许超出则 ellipsize=end）

#### 4-A `inventory_item.xml`
完全替换为三行自适应布局（见上图），增加 id：
- `tv_inventory_epc_label`（"EPC"）、`tv_inventory_id`（EPC值）
- `tv_inventory_data_label`（"TID"/"USER"/"RSRV"）、`tv_inventory_data`（副区值）
- `tv_inventory_chip_label`（"芯片"）、`tv_inventory_chip`（芯片名）
- `tv_inventory_count`（次数，右侧列）、`tv_inventory_rssi`（RSSI，右侧列，可隐藏）

#### 4-B `InventoryAdapter.java`
- 增加 `private InventoryArea currentArea` 字段
- 增加 `setInventoryArea(InventoryArea area)` 方法（由 InventoryFragment 在 `onReaderConfigurationChanged` 中调用）
- `onBindViewHolder` 中根据 area 设置副区标签文字：
  ```java
  holder.dataLabel.setText(area == C_EPC_TID ? "TID" :
                            area == C_EPC_USER ? "USER" :
                            area == C_EPC_RESERVED ? "RSRV" : "DATA");
  ```
- EPC 超过 12 字节(24 hex 字符 = 6 word)时，`tv_inventory_id` 的 `maxWidth` 设置为 `ellipsize=end` 即可（XML 已设 `singleLine=true`，只需保证 `layout_weight=1` 布局正确）
- 绑定 `chip_label` 可见性：有副区数据且有 chip 时才显示第三行

#### 4-C 点击弹出详情底部弹窗

##### 新建 `InventoryDetailSheet.java`
继承 `BottomSheetDialog` 或使用 `MaterialAlertDialogBuilder` 弹全屏底部 sheet：
```
╔══════════════════════════════════════╗
║  EPC    [完整值，可选中复制]           ║
║  TID    [完整值]  （无则不显示）        ║
║  芯片   [完整名称]（无则不显示）        ║
║  次数   [n 次]                         ║
║  RSSI   [n dBm]（不支持时不显示）       ║
╠══════════════════════════════════════╣
║ [填充EPC掩码] [填充TID掩码]  [关闭]    ║
╚══════════════════════════════════════╝
```
- "填充xxx掩码"按钮根据 `currentArea` 显隐：
  - 仅 EPC：只显示"填充EPC掩码"
  - EPC+TID：显示"填充EPC掩码"和"填充TID掩码"
  - EPC+USER：显示"填充EPC掩码"和"填充USER掩码"
  - EPC+RESERVED：显示"填充EPC掩码"和"填充RSRV掩码"
- 点击"填充xxx掩码"后：关闭弹窗，将对应值填入掩码面板的 hex 输入框，并自动展开掩码面板，同时设置掩码 bank spinner 到对应区域

##### 4-D `InventoryFragment.java`
- `initView()` 中对 RecyclerView 增加 item 点击监听（通过 `InventoryAdapter` 回调或 `addOnItemTouchListener`），点击时调用 `showItemDetail(item)`
- `showItemDetail(InventoryItem item)` 中构造 `InventoryDetailSheet` 并 show；"填充掩码"回调时：
  ```java
  maskBankSpinner.setSelection(targetBank);
  maskHexView.setText(hexValue);
  maskLengthView.setText(String.valueOf(hexValue.length() * 4));
  maskExpanded = true;
  updateMaskControls();
  ```

---

## Issue 5 — 盘点区域设置弹窗优化

### 现状分析
`showInventoryRangeDialog()` 使用 `MaterialAlertDialogBuilder` + 自定义 view（`dialog_inventory_range.xml`）。  
用户觉得丑的原因是布局是纯 `TextInputLayout` 堆叠，没有说明当前区域的默认值，且确认后还会再弹一次 `confirmAndApply` 确认框（三步操作）。

### 各区域真实起始地址与长度默认值
> **已通过 UHF Library C/C++ 开发文档（v2.9）确认。**  
> 数据来源：`setInventoryArea(u8 area, u8 startAddr, u8 wordLen)` API 说明 + `#define MAX_TID_LEN (12)` / `#define MAX_USR_LEN (64)` 宏定义。  
> EPC Gen2 / ISO 18000-6C 标准：所有非 EPC bank 的有效数据均从 word 0 起始。

| 区域 | bank | 起始地址（word） | 长度默认值（word） | 来源 |
|------|------|----------------|-----------------|------|
| TID | 2 | 0 | 6 | `MAX_TID_LEN = 12 bytes = 6 words` |
| USER | 3 | 0 | 6 | `DEFAULT_INVENTORY_WORD_LEN = 6`（上限 32 words） |
| RESERVED | 0 | 0 | 4 | EPC Gen2：Kill pwd (2w) + Access pwd (2w) |

### 修改清单

#### 5-A `showInventoryRangeDialog()` in `ReaderConfigFragment.java`
1. **按区域预填充默认值**：不再从 `configuration` 读当前值作为默认，改为按 `InventoryArea` 返回推荐值：
   ```java
   // 各区域 startAddr 均为 0（EPC Gen2 非EPC bank 从 word 0 起）
   private static int defaultAddress(InventoryArea area) { return 0; }
   // TID: MAX_TID_LEN=12字节=6word；USER: DEFAULT_INVENTORY_WORD_LEN=6；RESERVED: Kill(2w)+Access(2w)=4w
   private static int defaultWordLen(InventoryArea area) {
       return switch (area) {
           case C_EPC_RESERVED -> 4;
           default -> ReaderConfiguration.DEFAULT_INVENTORY_WORD_LEN; // 6
       };
   }
   ```
2. **界面上不再显示地址和长度**：`updateInventoryAreaView()` 中只显示区域名称：
   ```java
   inventoryAreaView.setText(area.getDisplayName());
   ```
   同时 `applyInventoryArea()` 的 `summary` 也只用 `target.getDisplayName()`，去掉地址/长度字符串拼接。
3. **取消三次弹窗**：`showInventoryRangeDialog()` 点击确定后，直接提交（不再调 `confirmAndApply`），改为直接调：
   ```java
   dialog.dismiss();
   // 直接执行，不再弹确认框
   applyInventoryAreaDirect(target, address, length);
   ```
   新增 `applyInventoryAreaDirect()`（内部逻辑与 `applyInventoryArea` 相同，但跳过 `confirmAndApply` 确认步骤，直接进 WaitDialog + 执行）：
   ```java
   private void applyInventoryAreaDirect(InventoryArea target, int address, int length) {
       if (!requireReaderOnline()) { return; }
       settingWaitDialog = new WaitDialog.Builder(requireActivity())
               .setMessage(R.string.config_setting_wait);
       settingWaitDialog.show();
       session.setInventoryArea(target.getValue(), address, length)
               .thenApply(status -> {
                   if (status == 0) { session.clearInventory(); }
                   return status;
               })
               .whenComplete((status, error) -> requireActivity().runOnUiThread(() -> {
                   dismissSettingWaitDialog();
                   if (error != null || (status != null && status != 0)) {
                       int code = status == null ? -1 : status;
                       toast(getString(R.string.config_error_code,
                               getString(R.string.config_inventory_area_set_failed), code));
                   } else {
                       toast(getString(R.string.config_setting_success,
                               getString(R.string.config_inventory_area), target.getDisplayName()));
                   }
               }));
   }
   ```
4. **优化弹窗标题与 hint**：标题改为 `target.getDisplayName()` + "设置"（例如"盘点 EPC 和 TID 设置"），在 hint 处写明默认值（"默认 0"/"默认 6"）。

#### 5-B `dialog_inventory_range.xml`（可选美化）
在两个输入框上方加一行说明 `TextView`，展示各区域推荐值，字体颜色 `rfid_text_muted`，sp_12。

---

## Issue 6 — 功率 SeekBar 点击困难

### 根因
`SeekBar` 在 `ScrollView` 内时，`AbsSeekBar.onTouchEvent()` 对 `ACTION_DOWN` 走了 `isInScrollingContainer()` 判断：若为 true，则不立即抢占触摸事件，等待超过 `ViewConfiguration.getTouchSlop()` 后才开始 seek。  
`ScrollView` 在 `ACTION_MOVE` 前优先消费竖向滑动，导致用户轻触/短距拖拽时 SeekBar 根本拿不到事件。

### 修改清单

#### 6-A `ReaderConfigFragment.java`
在 SeekBar 上设置 `OnTouchListener`，拦截 `ACTION_DOWN` 时请求父级不拦截：
```java
powerSeekBar.setOnTouchListener((view, event) -> {
    if (event.getAction() == MotionEvent.ACTION_DOWN
            || event.getAction() == MotionEvent.ACTION_MOVE) {
        // 告知 ScrollView：从现在起把触摸事件交给 SeekBar
        view.getParent().requestDisallowInterceptTouchEvent(true);
    }
    if (event.getAction() == MotionEvent.ACTION_UP
            || event.getAction() == MotionEvent.ACTION_CANCEL) {
        view.getParent().requestDisallowInterceptTouchEvent(false);
    }
    return false;  // false = 让 SeekBar 继续处理事件
});
```
> 需要 import `android.view.MotionEvent`。

#### 6-B SeekBar 高度（可选辅助）
`sb_config_power` 当前高度 `@dimen/dp_36`，如仍难点击可改为 `@dimen/dp_48` 以扩大触摸区。

---

## Issue 7 — 误触互斥按钮导致断开连接（致命 Bug）

### 根因
`bleSwitch` / `wifiSwitch` 的 `OnCheckedChangeListener` 在 `isChecked == true` 且当前有连接时，直接调用 `session.disconnect(DisconnectReason.TRANSPORT_SWITCH)`（第 161 / 177 行），没有任何防护。  
用户误触 Switch 即立即断开，且无法恢复（需重新扫描/输入 IP）。

### 修改方案
结合 Issue 2 已添加的"断开连接"按钮，采用**连接时锁定 Switch 互斥区**的方案：

**原则**：已连接时，Switch 只反映当前传输方式，且设为不可交互（用户只能通过专属"断开连接"按钮断开）。

#### 7-A `ReaderConfigFragment.java`

修改 `bindTransportRows(boolean ble, boolean connected)`（Issue 2 已修改此方法）：
```java
private void bindTransportRows(boolean ble, boolean connected) {
    bleActions.setVisibility(!connected && ble   ? View.VISIBLE : View.GONE);
    wifiActions.setVisibility(!connected && !ble ? View.VISIBLE : View.GONE);
    disconnectRow.setVisibility(connected        ? View.VISIBLE : View.GONE);
    // 连接时锁定两个 Switch，防止误触
    bleSwitch.setEnabled(!connected);
    wifiSwitch.setEnabled(!connected);
    if (connected) {
        String target = readerState.getTransport() == TransportType.BLE
                ? bleDisplayName(readerState.getDeviceName()) : readerState.getAddress();
        connectedTargetView.setText(target);
    }
}
```

修改两个 Switch 的 `OnCheckedChangeListener`，移除直接断开的逻辑（只保留 UI 互斥）：
```java
bleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
    if (bindingUi) { return; }
    if (!isChecked && !wifiSwitch.isChecked()) {
        buttonView.setChecked(true);
        return;
    }
    if (!isChecked) { return; }
    bindingUi = true;
    wifiSwitch.setChecked(false);
    bindingUi = false;
    dismissWifiKeyboard();
    bindTransportRows(true, readerState.isConnected());
    // 注意：不再在这里 disconnect！已连接时 Switch 已被 disable，不会触发此处。
});
wifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
    if (bindingUi) { return; }
    if (!isChecked && !bleSwitch.isChecked()) {
        buttonView.setChecked(true);
        return;
    }
    if (!isChecked) { return; }
    bindingUi = true;
    bleSwitch.setChecked(false);
    bindingUi = false;
    bindTransportRows(false, readerState.isConnected());
    // 注意：不再在这里 disconnect！
});
```

> **断开时序**：用户点"断开连接"→ `disconnectDevice()` → `session.disconnect(USER)` → `onReaderStateChanged` 回调 → `bindTransportRows(ble, false)` → Switch 重新可点击，用户可切换传输方式。

---

## 依赖关系与执行顺序建议

```
Issue 6  →  独立，先做（改 SeekBar 一行，风险最低）
Issue 1  →  独立，RM8011 功率简化
Issue 3  →  独立，NavigationAdapter tint 修复
Issue 7  →  依赖 Issue 2（必须先建断开按钮再移除 Switch 的断开逻辑）
Issue 2  →  独立（但 Issue 7 依赖它）
Issue 5  →  独立，dialog 优化
Issue 4  →  改动最大，放最后
```

**推荐执行序**：6 → 3 → 1 → 2+7（同批） → 5 → 4

---

## 已确认项（原待确认）

> 已通过 UHF Library C/C++ 开发文档（v2.9，2025-09-05）全部核实，无需再做修改。

| 编号 | 内容 | 确认结果 |
|------|------|---------|
| T1 | TID/USER/RESERVED 区的 SDK word 起始地址 | **全部为 0**。EPC Gen2 非 EPC bank 均从 word 0 起始，文档 `setInventoryArea` API 描述一致。 |
| T2 | RESERVED 区默认长度 | **4 words**（Kill pwd 2w + Access pwd 2w），已写入 `defaultWordLen()` 分支。 |
| T3 | RM8011 功率上限 | **20 dBm**（= powerLevel 200，tenths）。文档 3.4.7 仅说明 R2000 range 0-300，RM8011 无单独说明；用户产品规格确认上限为 20 dBm，Issue 1 的 `maxDbm = 20` 正确。 |
