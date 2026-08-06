# UhfRemote 修改方案（codex 可执行）

基线 commit：`76ba208`（`chore: remove obsolete execution plans`）。
改动范围：仅 `app` 模块，不动 `library/`。
本方案替换此前所有方案文件，旧内容已删除（可从 git 历史找回）。

## 执行顺序

按任务编号顺序执行，每个任务独立提交，每次提交前跑一次编译：

```bash
./gradlew :app:compileDebugJavaWithJavac
```

全部任务完成后跑一次完整装配：

```bash
./gradlew :app:assembleDebug
```

## 任务总览

| # | 任务 | 主要文件 |
|---|---|---|
| 1 | 盘点列表分列配色 + EPC 列多段分色 | `colors.xml`、`InventoryAdapter`、`inventory_item.xml`、`inventory_fragment.xml` |
| 2 | item 右上角掩码小锁指示 | 新增 2 个 drawable、`inventory_item.xml`、`InventoryAdapter`、`InventoryFragment` |
| 3 | 应用/取消掩码合并为一个按钮 + 面板宽度对齐列表 | `inventory_mask_panel.xml`、`InventoryFragment`、新增 1 个 color selector |
| 4 | 掩码应用/取消时序修正（偶发取消失败） | `NativeUhfSdkGateway`、`ReaderSessionManager` |
| 5 | 切页后丢失掩码状态（bug） | `ReaderSessionManager`、`InventoryFragment` |
| 6 | 工作模式（盘点模式）命名与有效值 | `strings.xml`、`ModuleSubtype`、`ReaderSessionManager`、`ReaderConfigFragment` |
| 7 | 射频协议完整命名 | `TagProtocol` |
| 8 | Session 初始化缓存 Sel/Target，设置时一并下发 | `UhfSdkGateway`、`NativeUhfSdkGateway`、`ReaderConfigCache`、`ReaderHandshake`、`ReaderSessionManager`、`ReaderConfigFragment`、`strings.xml` |
| 9 | 设备信息弹窗：去掉子类型原始值 + UI 精美化 | `reader_device_info_dialog.xml`、`ReaderDeviceInfoDialog`、`styles.xml` |

## 需要确认的两处假设

1. **RM610 是否支持盘点模式切换。** 需求写明"单次盘点和低功耗模式目前仅对 R2000 及连接 R2000 的 RM70XX 有效，对 RM801X 无效"。按此表述 RM610 同样不是 R2000，故任务 6 把 RM610 也归入"仅高性能盘点"。若实测 RM610 支持单次/低功耗，只需在 `ModuleSubtype.supportsInventoryModeSwitch()` 里加上 `RM610`。
2. **掩码面板的 Switch 一并移除。** 需求要求应用/取消掩码合为一个按钮。面板头部现有的 `SwitchMaterial`（`sw_inventory_mask`）与该按钮功能完全重复，两个控件同时存在会出现状态互相打架。任务 3 移除 Switch，改为「状态 chip + 单按钮」，状态唯一来源是 `ReaderSessionManager` 的掩码状态。

## 任务 1：盘点列表分列配色 + EPC 列多段分色

### 现状

`inventory_item.xml` 里 EPC 值用 `rfid_primary_soft`，附加数据用 `rfid_text_secondary`，芯片用 `rfid_text`，三个 label 都是 `rfid_text_muted`。`InventoryAdapter` 只按 RSSI 改颜色。盘点 USER/TID 时，EPC 行和数据行颜色接近，看不出是两段不同的数据。

### 修改清单

**1.1 `app/src/main/res/values/colors.xml`** — 在 `rfid_input_guard` 之后追加语义色：

```xml
    <!-- 盘点列表分列语义色 -->
    <color name="rfid_col_index">#94A3B8</color>
    <color name="rfid_col_epc">#2563EB</color>
    <color name="rfid_col_tid">#7C3AED</color>
    <color name="rfid_col_user">#0F766E</color>
    <color name="rfid_col_reserved">#C2410C</color>
    <color name="rfid_col_data">#475569</color>
    <color name="rfid_col_count">#B45309</color>
    <color name="rfid_col_chip">#0F172A</color>
```

**1.2 `InventoryAdapter.java`** — 新增按盘点区域取色的方法，放在 `dataLabel(InventoryArea)` 旁边：

```java
    /** 附加数据列按盘点区域取色，使 EPC 与 TID/USER 段落在视觉上可区分。 */
    private static int dataColor(InventoryArea area) {
        return switch (area) {
            case C_EPC_TID -> R.color.rfid_col_tid;
            case C_EPC_USER, B_UID_USER, GJB_CODE_USER, GB_CODE_USER -> R.color.rfid_col_user;
            case C_EPC_RESERVED -> R.color.rfid_col_reserved;
            default -> R.color.rfid_col_data;
        };
    }

    /** 逐列下发语义色，label 用同色系半透明以弱化。 */
    private void bindColors(ViewHolder holder) {
        android.content.Context context = holder.itemView.getContext();
        int epcColor = ContextCompat.getColor(context, R.color.rfid_col_epc);
        int dataColor = ContextCompat.getColor(context, dataColor(currentArea));
        holder.index.setTextColor(ContextCompat.getColor(context, R.color.rfid_col_index));
        holder.epcLabel.setTextColor(epcColor);
        holder.epcLabel.setAlpha(0.65f);
        holder.id.setTextColor(epcColor);
        holder.dataLabel.setTextColor(dataColor);
        holder.dataLabel.setAlpha(0.65f);
        holder.data.setTextColor(dataColor);
        holder.chipLabel.setTextColor(ContextCompat.getColor(context, R.color.rfid_col_index));
        holder.chip.setTextColor(ContextCompat.getColor(context, R.color.rfid_col_chip));
        holder.count.setTextColor(ContextCompat.getColor(context, R.color.rfid_col_count));
    }
```

`ViewHolder` 补两个字段（现有 `dataLabel` 已存在，缺 EPC/芯片的 label）：

```java
        final TextView epcLabel;
        final TextView chipLabel;
```

构造里补：

```java
            epcLabel = view.findViewById(R.id.tv_inventory_epc_label);
            chipLabel = view.findViewById(R.id.tv_inventory_chip_label);
```

`onBindViewHolder(holder, position)` 里 `bindVisibility(holder, item);` 之后加一行 `bindColors(holder);`。
带 payload 的重载里，`payloads.contains(PAYLOAD_LAYOUT)` 分支内 `bindVisibility(holder, item);` 之后同样加 `bindColors(holder);`（切换盘点区域后颜色要跟着变）。

**1.3 `inventory_item.xml`** — 去掉写死的颜色，改为语义色（运行时仍会被 `bindColors` 覆盖，这里保证预览与首帧正确）：

- `tv_inventory_index`：`android:textColor="@color/rfid_col_index"`
- `tv_inventory_epc_label`、`tv_inventory_id`：`@color/rfid_col_epc`
- `tv_inventory_data_label`、`tv_inventory_data`：`@color/rfid_col_data`
- `tv_inventory_chip_label`：`@color/rfid_col_index`
- `tv_inventory_chip`：`@color/rfid_col_chip`
- `tv_inventory_count`：`@color/rfid_col_count`
- `tv_inventory_rssi` 保持 `@color/rfid_success`（由 `rssiColor()` 按信号强度覆盖）

**1.4 `inventory_fragment.xml`** — 表头 5 个 TextView 的 `android:textColor` 与列一一对应，避免表头与内容语义脱节：

- 编号列 → `@color/rfid_col_index`
- `tv_inventory_column_data` → `@color/rfid_col_epc`
- 次数列 → `@color/rfid_col_count`
- `tv_inventory_column_rssi` → `@color/rfid_text_muted`（信号色动态变化，表头保持中性）
- `tv_inventory_column_chip` → `@color/rfid_col_chip`

### 验证

- 仅盘点 EPC：EPC 蓝色，无数据行。
- 盘点 EPC + TID：EPC 蓝色、TID 紫色，两行可区分。
- 盘点 EPC + USER：USER 青色；切到 RESERVED 变橙色。
- 盘点中切换盘点区域，已有列表项颜色立即跟着变（走 `PAYLOAD_LAYOUT`）。

## 任务 2：item 右上角掩码小锁指示

### 需求

未设掩码时锁是打开形状（中性色）；掩码生效后锁标红且是关闭状态。

### 修改清单

**2.1 新增 `app/src/main/res/drawable/rfid_lock_open_ic.xml`**（开口锁，锁梁向右上开）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="@dimen/dp_14"
    android:height="@dimen/dp_14"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M5,11h11v10H5zM8,11V7a4,4 0,0 1,8 -0.5"
        android:strokeColor="@color/rfid_text_muted"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="1.8" />
</vector>
```

**2.2 新增 `app/src/main/res/drawable/rfid_lock_closed_ic.xml`**（闭合锁，红色）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="@dimen/dp_14"
    android:height="@dimen/dp_14"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M5,11h14v10H5zM8,11V7a4,4 0,0 1,8 0v4"
        android:strokeColor="@color/rfid_danger"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="1.8" />
</vector>
```

注意：不要复用现有的 `rfid_lock_ic.xml`，它是白色描边、给单标签页按钮用的，改它会影响那边。

**2.3 `inventory_item.xml`** — 根节点从 `LinearLayout` 换成 `FrameLayout`，把原来的 `LinearLayout` 作为内容层放进去，锁作为右上角浮层。结构：

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/rfid_panel_bg">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="@dimen/dp_56"
        android:gravity="center_vertical"
        android:paddingHorizontal="@dimen/dp_12"
        android:paddingVertical="@dimen/dp_8">
        <!-- 原有子控件整体搬进来，顺序与属性不变 -->
    </LinearLayout>

    <ImageView
        android:id="@+id/iv_inventory_mask_lock"
        android:layout_width="@dimen/dp_14"
        android:layout_height="@dimen/dp_14"
        android:layout_gravity="top|end"
        android:layout_marginTop="@dimen/dp_4"
        android:layout_marginEnd="@dimen/dp_4"
        android:contentDescription="@string/inventory_mask_lock_open"
        android:src="@drawable/rfid_lock_open_ic" />
</FrameLayout>
```

两点注意：

- 原 `LinearLayout` 上的 `android:background="@color/rfid_panel_bg"` 移到 `FrameLayout`，因为 `InventoryAdapter.onBindViewHolder` 里 `holder.itemView.setBackgroundColor(...)` 作用在根节点上，隔行变色要继续生效。
- 内容层的 `minHeight`、`paddingHorizontal`、`paddingVertical`、`gravity` 全部保留，行高不变。

**2.4 `InventoryAdapter.java`** — 加掩码状态字段与刷新入口：

```java
    private boolean maskActive;

    public void setMaskActive(boolean active) {
        if (maskActive == active) { return; }
        maskActive = active;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_LAYOUT);
    }
```

`ViewHolder` 加字段 `final ImageView maskLock;`，构造里 `maskLock = view.findViewById(R.id.iv_inventory_mask_lock);`（需 `import android.widget.ImageView;`）。

`bindVisibility(holder, item)` 末尾追加锁的绑定：

```java
        holder.maskLock.setImageResource(maskActive
                ? R.drawable.rfid_lock_closed_ic : R.drawable.rfid_lock_open_ic);
        holder.maskLock.setContentDescription(holder.itemView.getContext().getString(maskActive
                ? R.string.inventory_mask_lock_closed : R.string.inventory_mask_lock_open));
```

`bindVisibility` 已在 `onBindViewHolder` 两条路径里被调用，锁会随 `PAYLOAD_LAYOUT` 一起刷新，不需要额外改动。

**2.5 `strings.xml`** — 在掩码相关字符串区域追加：

```xml
    <string name="inventory_mask_lock_open">未设置掩码</string>
    <string name="inventory_mask_lock_closed">掩码已生效</string>
```

**2.6 `InventoryFragment.java`** — 掩码状态变化时同步给 adapter。`onInventoryMaskChanged` 内 `activeMask = config;` 之后加：

```java
        if (adapter != null) { adapter.setMaskActive(config != null); }
```

同时任务 5 的 `syncMaskFromSession()` 里也要调用一次，保证切页回来锁的形态正确。

### 验证

- 未应用掩码：每个 item 右上角灰色开口锁。
- 应用掩码成功：全部 item 的锁变红色闭合锁。
- 取消掩码：锁恢复灰色开口。
- 隔行底色仍然正常（偶数行 `rfid_panel_bg`、奇数行 `rfid_page_bg`）。

## 任务 3：应用/取消掩码合并为一个按钮 + 面板宽度对齐列表

### 现状与问题

- `inventory_mask_panel.xml` 有 `android:layout_marginHorizontal="@dimen/dp_16"`，而表头与列表都是通栏，面板明显比列表窄。
- 底部是「应用掩码」+「取消掩码」两个按钮并排，点完应用后按钮外观不变，看不出掩码已生效。
- 头部还有一个 `SwitchMaterial` 与这两个按钮功能重复。

### 修改清单

**3.1 `inventory_mask_panel.xml` 根节点** — 去掉 `style="@style/RfidCard"` 与水平外边距，改为通栏卡片：

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/rfid_panel_bg"
    android:orientation="vertical"
    android:paddingBottom="@dimen/dp_2">
```

宽度与 `inventory_fragment.xml` 的表头、`RecyclerView` 完全一致。内部左右统一用 `@dimen/dp_16`，与表头 `paddingHorizontal` 对齐：把 `row_inventory_mask_toggle` 和 `ll_inventory_mask_content` 的 `android:paddingHorizontal` 从 `dp_12` 改成 `dp_16`。

**3.2 头部：删除 Switch，保留状态 chip**

删除整个 `FrameLayout`（`fl_inventory_mask_switch_target`）及其内部的 `SwitchMaterial`（`sw_inventory_mask`）。
`tv_inventory_mask_status` 保留，`android:layout_marginStart` 改为 `@dimen/dp_8`，背景在代码里按状态切换（生效 `rfid_chip_red_bg`，未生效 `rfid_chip_gray_bg`）。

**3.3 底部：两个按钮合并成一个**

把原来那个含两个 `MaterialButton` 的 `LinearLayout` 整体替换成：

```xml
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_inventory_mask_toggle"
            style="@style/RfidPrimaryButton"
            android:layout_width="match_parent"
            android:layout_height="@dimen/dp_40"
            android:layout_marginTop="@dimen/dp_12"
            android:fontFamily="sans-serif-medium"
            android:stateListAnimator="@null"
            android:text="@string/inventory_mask_apply"
            android:textSize="@dimen/sp_14" />
```

原来的 `btn_inventory_mask_apply` / `btn_inventory_mask_clear` 两个 id 全部删除。

**3.4 新增危险态按钮底色 ColorStateList**

`MaterialButton` 不能在运行时换 style，所以两态靠 `setBackgroundTintList` 切换。新建 `app/src/main/res/color/rfid_danger_button_background.xml`，与现有 `rfid_primary_button_background.xml` 结构对齐（禁用态同样落到 `rfid_line`，保证置灰观感一致）：

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:color="@color/rfid_line" />
    <item android:color="@color/rfid_danger" />
</selector>
```

`@color/rfid_danger`（`#DC2626`）已存在，不用新增颜色。布局里按钮的初始 style 仍是 `RfidPrimaryButton`（未掩码态），无需新增按钮 style。

**3.5 `InventoryFragment.java`** — 字段与监听改造。

删除字段 `maskApplyButton`、`maskClearButton`、`maskSwitch`、`bindingMaskSwitch`，新增：

```java
    private MaterialButton maskToggleButton;
```

`initView()` 中删除 `maskApplyButton` / `maskClearButton` / `maskSwitch` 三行 `findViewById`，替换为：

```java
        maskToggleButton = findViewById(R.id.btn_inventory_mask_toggle);
```

删除 `maskSwitch.setOnCheckedChangeListener(...)` 整段、`findViewById(R.id.fl_inventory_mask_switch_target).setOnClickListener(...)` 整段，以及 `maskApplyButton.setOnClickListener(...)`、`maskClearButton.setOnClickListener(...)` 两行，替换为：

```java
        maskToggleButton.setOnClickListener(view -> toggleMask());
```

同时删掉 `import com.google.android.material.switchmaterial.SwitchMaterial;`（确认文件内无其他引用后再删）。

**3.6 掩码动作方法简化**

`applyMask()` / `applyMask(boolean)` / `clearMask()` / `clearMask(boolean)` / `showMaskResult(...)` / `restoreMaskSwitch(boolean)` / `setMaskSwitchChecked(boolean)` 全部替换成下面这一组：

```java
    /** 单按钮双态：未掩码时校验并下发，已掩码时取消。 */
    @SingleClick
    private void toggleMask() {
        if (activeMask != null) {
            clearMask();
        } else {
            applyMask();
        }
    }
```

```java
    private void applyMask() {
        if (!readerState.isConnected()) {
            requireReaderOnline();
            return;
        }
        try {
            InventoryMaskConfig config = parseMaskForm();
            maskOperationInFlight = true;
            updateMaskControls();
            session.applyInventoryMask(config).whenComplete((status, error) ->
                    showMaskResult(status, error, R.string.inventory_mask_applied,
                            R.string.inventory_mask_apply_failed));
        } catch (IllegalArgumentException error) {
            maskExpanded = true;
            focusInvalidMaskField();
            updateMaskControls();
            toast(error.getMessage());
        }
    }

    private void clearMask() {
        if (!readerState.isConnected()) {
            requireReaderOnline();
            return;
        }
        maskOperationInFlight = true;
        updateMaskControls();
        session.clearInventoryMask().whenComplete((status, error) ->
                showMaskResult(status, error, R.string.inventory_mask_cleared,
                        R.string.inventory_mask_clear_failed));
    }

    /** 成功与否都不手动改按钮态，统一由 onInventoryMaskChanged 回调驱动，避免两套状态打架。 */
    private void showMaskResult(Integer status, Throwable error, @StringRes int successMessage,
            @StringRes int failureMessage) {
        if (!isViewAlive()) { return; }
        requireActivity().runOnUiThread(() -> {
            if (!isViewAlive()) { return; }
            maskOperationInFlight = false;
            if (error != null) {
                Log.e(TAG, getString(failureMessage), error);
                toast(rootMessage(error));
            } else if (status != null && status != 0) {
                Log.e(TAG, getString(failureMessage) + " status=" + status);
                toast(getString(R.string.config_error_code, getString(failureMessage), status));
            } else {
                Log.i(TAG, getString(successMessage));
                toast(successMessage);
            }
            updateMaskControls();
        });
    }
```

**3.7 `updateMaskControls()` 改造**

把 `maskSwitch` / `maskApplyButton` / `maskClearButton` 三行（现 500–502 行）替换为下面这段。必须保留在 `setEnabledRecursively(maskPanelContent, formEnabled)` 之后，因为按钮在 `maskPanelContent` 内部，会被递归置灰再由这里覆盖：

```java
        boolean masked = activeMask != null;
        maskToggleButton.setText(masked
                ? R.string.inventory_mask_cancel : R.string.inventory_mask_apply);
        // 用 ColorStateList 而不是单色，保住 disabled 态置灰。
        maskToggleButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(),
                masked ? R.color.rfid_danger_button_background
                        : R.color.rfid_primary_button_background));
        maskToggleButton.setTextColor(ContextCompat.getColorStateList(requireContext(),
                R.color.rfid_primary_button_text));
        // 已掩码时只要在线就能取消；未掩码时还要表单校验通过。
        maskToggleButton.setEnabled(formEnabled && (masked || formValid));
```

> `rfid_primary_button_background` / `rfid_primary_button_text` 是 `app/src/main/res/color/` 下的 **ColorStateList**（`state_enabled="false"` 分别落到 `rfid_line` / `rfid_text_muted`），不是单色，必须用 `getColorStateList` 取。文字色两态都是「启用白 / 禁用灰」，直接复用同一个 selector。

`maskStatusView` 那段补上背景与文案兜底，让收起面板时也能看出掩码状态：

```java
        maskStatusView.setVisibility(masked ? View.VISIBLE : View.GONE);
        if (masked) {
            Object bank = maskBankSpinner.getSelectedItem();
            String bankLabel = bank == null ? "" : bank.toString();
            maskStatusView.setBackgroundResource(R.drawable.rfid_chip_red_bg);
            maskStatusView.setText(getString(R.string.inventory_mask_active, bankLabel,
                    activeMask.offsetBits, activeMask.lengthBits));
        }
```

不需要新增 import：`ContextCompat` 与 `R.drawable` 都已在用，`getColorStateList` 直接返回 `ColorStateList`，不必显式引入类型。

**3.8 `onInventoryMaskChanged` 简化**

```java
    @Override
    public void onInventoryMaskChanged(@Nullable InventoryMaskConfig config) {
        if (!isViewAlive()) { return; }
        activeMask = config;
        if (config != null) { bindMaskForm(config); }
        if (adapter != null) { adapter.setMaskActive(config != null); }
        updateMaskControls();
    }
```

`isViewAlive()` 在任务 5 中新增。

### 验证

- 掩码面板左右边缘与列表表头「编号」文字左边缘对齐，卡片通栏无空隙。
- 未掩码：单按钮显示「应用掩码」蓝底；填写非法时置灰。
- 点击后成功：按钮变「取消掩码」红底，头部出现红色状态 chip，item 锁变红闭合。
- 再点一次：恢复「应用掩码」蓝底，chip 隐藏，锁恢复灰色开口。
- 断开连接时按钮置灰，不崩溃。

## 任务 4：修复取消掩码偶发失败

### 现状与问题

1. `ReaderSessionManager.applyInventoryMask` / `clearInventoryMask` 直接改 Select 参数，**没有先停盘点再重启**。盘点进行中修改 `SelectCriteria`，模块可能忽略本次设置，表现为「取消掩码偶发失败」。
2. `NativeUhfSdkGateway.clearTargetMask` 用 `savedSelected` 快照还原 Sel。如果快照是 2（上一轮掩码残留）就还原不回 0。按客户口径应**硬置 0**。
3. 取消/应用后重启盘点时的 `status` 掩码开关参数（0 禁用 / 1 启用）没有跟着变。

### 修改清单

**4.1 `NativeUhfSdkGateway.java`** — 删除 `savedSelected` 字段（第 27 行）和 `saveSelectedIfAbsent(ModuleSubtype)` 方法（364–380 行），并删掉 `applyInventoryMask`（281 行）、`setTargetMask`（318 行）里的两处 `saveSelectedIfAbsent(subtype);` 调用。

`clearTargetMask` 的 345–347 行替换为硬置 0：

```java
        // 客户口径：取消掩码时 Sel 一律回 0，不做快照还原，避免上一轮残留的 2 被还原回去。
        int status = setSelectValue(subtype, 0);
```

并把后面的 `SelectCriteria` 重置扩展为「全部字段回初始值」（原来只改了 status/session/jq/action，bank/offset/length/maskData 仍留着上次的掩码数据）：

```java
        criteria.selectorIdx = 0;
        criteria.status = 0;
        criteria.bank = 0;
        criteria.offset = 0;
        criteria.length = 0;
        criteria.session = 0;
        criteria.jq = 0;
        criteria.action = 0;
        criteria.maskData = new byte[64];
        return linkage.set18K6CSelectCriteria(criteria);
```

> `maskData` 用 `new byte[64]` 与 `toFixedMaskData(null)` 等价，长度和 SDK 结构体一致。

**4.2 `ReaderSessionManager.java`** — 抽出 `startInventoryInternal()`。

`startInventory()`（474–517 行）的 lambda body 整体搬进新私有方法，`startInventory()` 只留下提交动作：

```java
    public CompletableFuture<Integer> startInventory() {
        return submitConnected(this::startInventoryInternal);
    }

    /** 在 uhf-sdk 线程上真正拉起盘点，maskFlag 跟随当前掩码状态。 */
    private int startInventoryInternal() {
        // 476–515 行原样搬过来：applyInventoryParams → 掩码重下 → 低功耗调度
        // → int maskFlag = inventoryMaskApplied ? 1 : 0 → gateway.startInventory(inventoryMode, maskFlag)
        // → monitorSdkStatus → publish(inventoryRunning(true))
    }
```

与 `stopInventoryInternal()` 对称，同样只能在 uhf-sdk 线程上调用。

**4.3 `applyInventoryMask(config)` 包上停止—设置—重启**

532–545 行的 lambda body 替换为：

```java
            TagProtocol protocol = state.getProtocol();
            // 盘点进行中改 SelectCriteria 会被模块忽略，必须先停。
            boolean wasRunning = state.isInventoryRunning();
            if (wasRunning) {
                int stopStatus = stopInventoryInternal();
                if (stopStatus != 0) { return stopStatus; }
            }
            int status = monitorSdkStatus(gateway.applyInventoryMask(protocol,
                    state.getModuleSubtype(), config));
            Log.i(TAG, "applyInventoryMask status=" + status + " bank=" + config.bank
                    + " offsetBits=" + config.offsetBits + " lengthBits=" + config.lengthBits);
            if (status == 0) {
                inventoryMask = config;
                inventoryMaskProtocol = protocol;
                inventoryMaskApplied = true;
                notifyMask(config);
            }
            // 无论成败都把盘点恢复回去，避免界面还显示在盘点但模块已停。
            // 重启走 startInventoryInternal()，maskFlag 会按新的 inventoryMaskApplied 取 1 或 0。
            if (wasRunning) {
                int restartStatus = startInventoryInternal();
                if (status == 0 && restartStatus != 0) { status = restartStatus; }
            }
            return status;
```

**4.4 `clearInventoryMask()` 同样处理**

560–573 行的 lambda body 替换为：

```java
            TagProtocol protocol = inventoryMaskProtocol == null
                    ? state.getProtocol() : inventoryMaskProtocol;
            boolean wasRunning = state.isInventoryRunning();
            if (wasRunning) {
                int stopStatus = stopInventoryInternal();
                if (stopStatus != 0) { return stopStatus; }
            }
            int status = monitorSdkStatus(gateway.clearInventoryMask(protocol,
                    state.getModuleSubtype()));
            Log.i(TAG, "clearInventoryMask status=" + status);
            if (status == 0) {
                inventoryMask = null;
                inventoryMaskProtocol = null;
                inventoryMaskApplied = false;
                notifyMask(null);
            }
            if (wasRunning) {
                int restartStatus = startInventoryInternal();
                if (status == 0 && restartStatus != 0) { status = restartStatus; }
            }
            return status;
```

> `stopInventoryInternal()` 在未盘点时直接返回 0，所以「用户没在盘点时点掩码」这条路径不受影响。
> 重启时 `startInventoryInternal()` 会重下一次 Select 参数（`inventoryMask != null` 分支），与「取消后 status=0、应用后 status=1」的要求一致。

### 验证

- 不盘点时应用/取消掩码：均成功，按钮与锁状态正确。
- **盘点中**点应用掩码：盘点短暂中断后自动继续，列表只出现命中掩码的标签，按钮变红。
- **盘点中**点取消掩码：盘点自动继续，全部标签重新出现，按钮变蓝。
- 连续「应用 → 取消 → 应用 → 取消」10 次，无一次失败（这是本任务的核心回归点）。
- 6B 协议下同样验证一遍（走 `set18K6BSelectCriteria` 分支）。

## 任务 5：修复切页后掩码状态丢失（bug）

### 根因

`library/base` 的 `BaseFragment`：

```java
    public View onCreateView(...) { mLoading = false; ... initView(); return mRootView; }
    public void onResume() { super.onResume(); if (mLoading) { return; } mLoading = true; initData(); }
```

`initView()` 每次创建 View 都会跑，`initData()` 被 `mLoading` 挡住。ViewPager 回收 `InventoryFragment` 的 View 之后再回来：布局被重新 inflate 成默认态（按钮=「应用掩码」、chip 隐藏、锁=开），但 `session` 不会重新 `notifyMask`，`activeMask` 也还是新 View 上的 `null`，于是掩码明明生效却显示成未设置。

`ReaderSessionManager` 里有 `hasInventoryMask()` 但没有能取回配置对象的 getter，所以 View 重建后无法回填表单。

### 修改清单

**5.1 `ReaderSessionManager.java`** — 在 `hasInventoryMask()` 旁边补 getter：

```java
    @Nullable
    public InventoryMaskConfig getInventoryMask() {
        return inventoryMask;
    }
```

**5.2 `InventoryFragment.java`** — `session` 提前到 `initView()` 获取。

现在 `session` 在 `initData()`（192 行）里赋值，而 `initView()` 里要用它做同步。在 `initView()` 开头（`startButton = findViewById(...)` 之前）先拿到实例，写法与 `initData()` 保持一致（`ReaderSessionManager` 是单例，重复获取无副作用）：

```java
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
```

`initData()` 里那行 `session = ...` 保留（幂等），`session.addObserver(this)` 不动。

**5.3 新增 View 存活判定与同步方法**

```java
    /** View 被 ViewPager 回收后回调仍可能到达，先判活再碰控件。 */
    private boolean isViewAlive() {
        return getView() != null && isAdded();
    }

    /** View 重建后从会话回读掩码状态，修复切页丢失。 */
    private void syncMaskFromSession() {
        activeMask = session.getInventoryMask();
        if (activeMask != null) { bindMaskForm(activeMask); }
        if (adapter != null) { adapter.setMaskActive(activeMask != null); }
        updateMaskControls();
    }
```

`initView()` 末尾原来的 `updateMaskControls();` 改成 `syncMaskFromSession();`。

`onResume()` 里 `maskExpanded = false;` 之后的 `updateMaskControls();` 也改成 `syncMaskFromSession();`，双保险覆盖「View 没被回收但掩码被别的页面改了」的情况。

**5.4 给其余 observer 回调加判活**

`ReaderSessionManager` 的通知是通过 `addObserver` 广播的，View 被回收后回调仍会到达，现在 `onReaderStateChanged` / `onInventoryChanged` / `onReaderConfigurationChanged` 都直接碰控件，切页时容易 NPE。三个方法开头统一加：

```java
        if (!isViewAlive()) { return; }
```

`onReaderStateChanged` 里 `readerState = state;` 这类纯数据赋值应放在判活**之前**，保证 View 重建后 `syncMaskFromSession()` 能拿到最新协议；碰控件的部分放在判活之后。改成：

```java
    @Override
    public void onReaderStateChanged(ReaderState state) {
        TagProtocol previousProtocol = readerState.getProtocol();
        readerState = state;
        if (!isViewAlive()) { return; }
        if (previousProtocol != state.getProtocol()) {
            updateMaskBanks(state.getProtocol());
        }
        // ...以下原样
    }
```

`onReaderConfigurationChanged` 同理：`configuration = value;` 放在判活之前。

### 验证

- 应用掩码 → 滑到「配置」页 → 滑回「盘点」页：按钮仍是红色「取消掩码」，chip 仍在，锁仍是红色闭合，表单里还是原来的掩码数据。
- 掩码状态下切页多次（≥5 次），状态不丢、不闪回。
- 应用掩码 → 切到别的页 → 在配置页改协议（会清掩码）→ 回盘点页：按钮已恢复「应用掩码」，与会话一致。
- 掩码状态下退到后台再回前台，状态保持。
- 切页过程中持续盘点，无 NPE、无崩溃。

## 任务 6：盘点模式有效值与设备适配

### 现状与问题

- 字符串是「单次盘点 / 模块持续盘点 / 库控持续盘点」，与客户口径「单次盘点 / 高性能盘点 / 低功耗盘点」不一致。
- `ReaderSessionManager.setInventoryMode` 用 `state.getModuleSubtype() == ModuleSubtype.RM8011 ? 1 : mode;` 硬编码降级，逻辑散在一处，界面上还能选到无效项。

### 修改清单

**6.1 `strings.xml`** — 三个模式名改为客户口径，并补上不支持时的提示：

```xml
    <string name="config_work_mode_single">单次盘点</string>
    <string name="config_work_mode_module">高性能盘点</string>
    <string name="config_work_mode_user">低功耗盘点</string>
    <string name="config_work_mode_unsupported">当前模块仅支持高性能盘点</string>
```

`config_work_mode_labels` 数组的 3 项引用不变，跟着改名后的字符串走。

> id 保持不动（`_module` / `_user`），只改文案，避免牵动所有引用点。

**6.2 `ModuleSubtype.java`** — 新增能力判定，把散落的机型判断收口：

```java
    /**
     * 单次盘点（0）与低功耗盘点（2）目前仅对 R2000 及连接 R2000 的 RM70XX 有效。
     * RM8011（旗连 RM801X）只支持高性能盘点（1）。
     */
    public boolean supportsInventoryModeSwitch() {
        return this == R2000 || this == R2000_PLUS;
    }
```

> **不要复用现成的 `isR2000Style()`**：它把 RM610 也算进 R2000 系（用于功率/协议判断），但 RM610 并不是 R2000 板卡，盘点模式上按「仅高性能」处理（见文首假设 1）。两个方法语义不同，必须分开。

**6.3 `ReaderSessionManager.setInventoryMode(int mode)`**（381 行起）—— 换成能力判定：

```java
        ModuleSubtype subtype = state.getModuleSubtype();
        inventoryMode = subtype.supportsInventoryModeSwitch() ? mode : 1;
        if (inventoryMode != mode) {
            Log.w(TAG, "inventory mode " + mode + " unsupported on " + subtype
                    + "; fall back to high performance");
        }
```

**6.4 `ReaderConfigFragment.java`** — 三处 RM8011 硬编码换成能力判定。

`applyModuleUi(subtype)`（798–802 行）：

```java
        boolean supportsModeSwitch = subtype.supportsInventoryModeSwitch();
        workModeRow.setEnabled(supportsModeSwitch && connected);
        if (!supportsModeSwitch) {
            workModeView.setText(workModeLabel(1));
        }
```

`onReaderConfigurationChanged`（346–347 行）：

```java
        workModeView.setText(readerState.getModuleSubtype().supportsInventoryModeSwitch()
                ? workModeLabel(value.inventoryMode) : workModeLabel(1));
```

`showWorkModeDialog()` 开头加一道拦截，避免行禁用被绕过（例如无障碍点击）：

```java
        if (!requireReaderOnline()) { return; }
        if (!readerState.getModuleSubtype().supportsInventoryModeSwitch()) {
            toast(R.string.config_work_mode_unsupported);
            return;
        }
```

### 验证

- 连 RM8011：工作模式行置灰不可点，显示「高性能盘点」；强制触发也只弹提示。
- 连 R2000 / R2000Plus：三个模式都能选，切换后显示对应文案并持久化（杀进程重进仍在）。
- 单次盘点模式下点开始，盘完一轮按钮自动回到「开始」。
- 低功耗模式下点开始，日志出现 `low-power inventory scheduler status=`。

## 任务 7：射频协议完整命名

### 修改清单

`TagProtocol.java` 的 `getDisplayName()` 按客户口径改为完整命名：

```java
    public String getDisplayName() {
        return switch (this) {
            case ISO_18000_6C -> "ISO 18000-6C";
            case ISO_18000_6B -> "ISO 18000-6B";
            case GJB_7377_1 -> "GJB_7377";
            case GB_T_29768 -> "GB_29768";
        };
    }
```

> 只改展示名，枚举名与 `rawValue` 一律不动，避免牵动 `ProtocolEncoding`、`ReaderConfigCache` 等所有引用点。

改完先全局搜一遍短名残留：

```bash
grep -rn '"6C"\|"6B"\|GJB 7377\|GB/T 29768' app/src/main
```

命中的地方（`strings.xml` 里的协议标签数组、掩码存储区提示等）按同一口径统一。若 `R.array.config_protocol_labels` 之类的数组写的是短名，也一并改成完整命名，保证弹窗与详情页一致。

### 验证

- 配置页协议行显示 `ISO 18000-6C`。
- 协议选择弹窗四项分别为 `ISO 18000-6C` / `ISO 18000-6B` / `GJB_7377` / `GB_29768`。
- 设备信息弹窗、标签详情页里的协议文案同步更新。
- 切协议后 toast 与确认弹窗里的名字也是完整命名。

## 任务 8：Session 只改 session，selected/target 走缓存

### 现状与问题

- `config_session_labels` 是 8 项（S0/A、S0/B … S3/B），把 target 也暴露给用户，而客户只要求改 session。
- 6B / GJB 协议下 session 无意义，界面仍可点。
- `setSessionTarget(session, target)` 下发时 `selected` 由 `setQueryGroup` / `setMagicQuery` 内部隐式沿用当前值，没有「初始化时缓存、设置时取缓存」的确定性；掩码生效期间 `selected` 已被改成 2，此时改 session 会把 2 固化下去。

### 参考实现

`/Users/lei/Downloads/Uhf_Android` 的 `QueryQFragment`：读用 `get_Query(parameters)` 取 `getSel()/getSession()/getTarget()/getQ()`，写用 `set_Query(0, 0, 1, sel, session, target, Q)` 一次性下发全部字段。本项目同样必须「读全量 → 只改 session → 写全量」。

### 修改清单

**8.1 `UhfSdkGateway.java`** — 用三元组读取替换 `getQueryGroup`，新增只改 session 的写入口：

```java
    /** 返回 {session, target, selected}，读失败返回 null。 */
    int[] getQueryValues(ModuleSubtype subtype);

    /** 只改 session，selected/target/其余 Query 参数按传入值原样下发。 */
    int setSession(ModuleSubtype subtype, int session, int target, int selected);
```

删除 `int[] getQueryGroup(ModuleSubtype subtype);` 与 `int setQueryGroup(int session, int target);` 两行声明。
`setMagicQuery` 保留不动 —— `ReaderSessionManager.setQ` 还在用它。

**8.2 `NativeUhfSdkGateway.java`** — `getQueryGroup`（187–196 行）改名并补 `selected`：

```java
    @Override
    public int[] getQueryValues(ModuleSubtype subtype) {
        if (subtype == ModuleSubtype.RM8011) {
            Parameters params = new Parameters();
            if (linkage.get_Query(params) != STATUS_OK) { return null; }
            return new int[]{params.getSession(), params.getTarget(), params.getSel()};
        }
        TagGroup group = new TagGroup();
        if (linkage.Radio_GetQueryTagGroup(group) != STATUS_OK) { return null; }
        return new int[]{group.session, group.target, group.selected};
    }
```

`setQueryGroup`（234 行起）替换为 `setSession`：

```java
    @Override
    public int setSession(ModuleSubtype subtype, int session, int target, int selected) {
        if (subtype == ModuleSubtype.RM8011) {
            // 参考 Uhf_Android 的 set_Query：读全量 → 只改 session/target/sel → 写全量。
            Parameters current = new Parameters();
            int status = linkage.get_Query(current);
            if (status != STATUS_OK) { return status; }
            return linkage.set_Query(current.getDR(), current.getM(), current.getTRext(),
                    selected, session, target, current.getQ());
        }
        TagGroup current = new TagGroup();
        int status = linkage.Radio_GetQueryTagGroup(current);
        if (status != STATUS_OK) { return status; }
        current.session = session;
        current.target = target;
        current.selected = selected;
        return linkage.Radio_SetQueryTagGroup(current);
    }
```

**8.3 `ReaderConfigCache.java`** — 单独缓存 `selected`（不塞进 `ReaderConfiguration`，避免改它全部 3 个构造函数与所有调用点）：

```java
    /** Query 的 Sel 值单独缓存：初始化读到后存下，设置 session 时取出一并下发。 */
    public void saveSelected(ModuleSubtype subtype, int selected) {
        mmkv.encode(prefix(subtype) + "selected", selected);
    }

    public int loadSelected(ModuleSubtype subtype) {
        return mmkv.decodeInt(prefix(subtype) + "selected", 0);
    }
```

**8.4 `ReaderHandshake.java`** — 握手时把三元组读出来并落盘（91–102 行）：

```java
        progress.accept(R.string.handshake_reading_session);
        int session = fallback.session;
        int target = fallback.target;
        int selected = cache.loadSelected(subtype);
        try {
            int[] values = gateway.getQueryValues(subtype);
            if (values != null && values.length >= 3) {
                session = values[0];
                target = values[1];
                selected = values[2];
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "读取 Session 失败，使用缓存值 S" + session, error);
        }
        // selected/target 只在这里从设备取一次，后续改 session 时原样回填。
        cache.saveSelected(subtype, selected);
```

`readConfigurationStepwise` 末尾已有 `cache.saveConfiguration(subtype, result);`，`selected` 由上面单独一行落盘，不受影响。

**8.5 `ReaderSessionManager.java`** — `setSessionTarget` 改成 `setSession`。

441–454 行整个方法替换：

```java
    /**
     * 客户只要求改 session：target 沿用配置里的值，selected 取握手时缓存的值。
     * 掩码生效期间设备 Sel 被临时改成 2，此时不能把 2 固化进来，仍按缓存值下发。
     */
    public CompletableFuture<Integer> setSession(int session) {
        if (session < 0 || session > 3) {
            return CompletableFuture.completedFuture(-31);
        }
        return submitConnected(() -> {
            ModuleSubtype subtype = state.getModuleSubtype();
            int target = configuration.target;
            int selected = configCache.loadSelected(subtype);
            int status = monitorSdkStatus(gateway.setSession(subtype, session, target, selected));
            Log.i(TAG, "setSession S" + session + " target=" + target
                    + " selected=" + selected + " status=" + status);
            if (status == 0 && inventoryMaskApplied) {
                // Sel 刚被写回缓存值，掩码需要 Sel=2，这里补回去。
                gateway.applyInventoryMask(state.getProtocol(), subtype, inventoryMask);
            }
            return updateConfiguration(status, new ReaderConfiguration(
                    configuration.powerTenthsDbm, inventoryMode, configuration.blfProfile,
                    session, target, configuration.dynamicQ, configuration.qValue,
                    configuration.qMinValue, configuration.qMaxValue, configuration.qRetryCount,
                    configuration.qThresholdMultiplier, configuration.qToggleTarget,
                    configuration.qRepeatUntilNoTags, configuration.inventoryArea,
                    configuration.inventoryAddress, configuration.inventoryWordLen));
        });
    }
```

> 注意 `updateConfiguration(status, updated)` 内部只在 `status == 0` 时写入，失败时配置不动。

**8.6 `strings.xml`** — session 只剩 4 项。

现有 `config_session`（"Session"）、`config_session_s0`（"S0"）、`config_session_value`（"S%1$d"）全部保留复用；删除 `config_session_target`（"Session / Target"）和 `config_session_format`（"S%1$d · %2$s"）——去掉 target 后不再需要。

`config_session_labels`（139 行）从 8 项裁成 4 项：

```xml
    <string-array name="config_session_labels">
        <item>S0</item>
        <item>S1</item>
        <item>S2</item>
        <item>S3</item>
    </string-array>
```

**8.7 `ReaderConfigFragment.java`** — 三处跟 target 相关的写法改掉。

`onReaderConfigurationChanged`（341–342 行）：

```java
        sessionView.setText(getString(R.string.config_session_value, value.session));
```

`showSessionDialog()`（633–644 行）整体替换：

```java
    private void showSessionDialog() {
        if (!requireReaderOnline()) { return; }
        // 6B / GJB 下 Session 无意义，直接拦住。
        if (!supportsSession(readerState.getProtocol())) {
            toast(R.string.config_session_unsupported);
            return;
        }
        String[] labels = getResources().getStringArray(R.array.config_session_labels);
        int selected = configuration == null ? 0 : configuration.session;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_session)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_session, labels[which],
                            () -> session.setSession(which),
                            R.string.config_query_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    /** Session 仅对 18K6C 与 GB 有效，6B / GJB 无意义。 */
    private static boolean supportsSession(TagProtocol protocol) {
        return protocol == TagProtocol.ISO_18000_6C || protocol == TagProtocol.GB_T_29768;
    }
```

`applyModuleUi(subtype)` 末尾加上按协议控制行可用性（协议变化时 `onReaderStateChanged` 会调用它）：

```java
        boolean sessionEnabled = supportsSession(readerState.getProtocol()) && connected;
        View sessionRow = findViewById(R.id.row_config_session);
        sessionRow.setEnabled(sessionEnabled);
        sessionRow.setAlpha(sessionEnabled ? 1f : 0.45f);
```

`strings.xml` 补一条：

```xml
    <string name="config_session_unsupported">当前协议下 Session 无意义</string>
```

> `setHardwareEnabled`（811 行）里对 `row_config_session` 的 `setEnabledRecursive` 保留不动，两者是「断连禁用」与「协议禁用」两层，`applyModuleUi` 在状态回调里跑得更晚，会覆盖成正确值。

### 验证

- 6C 协议：Session 行可点，弹窗 4 项 S0–S3，选中项与当前值一致；改完显示 `S2` 这类文案。
- 6B / GJB 协议：Session 行置灰半透明，强制点击只弹提示。
- 改 session 后重新握手（断开重连），读回来的 session 是刚设的值，target 没被改动。
- **掩码生效期间改 session**：改完掩码仍生效（列表只出命中标签），按钮仍是红色「取消掩码」。
- 连 RM8011 改 session：走 `set_Query` 分支，Q 值不变（配置页 Q 行数值不动）。

## 任务 9：设备信息弹窗去字段 + UI 重做

### 现状与问题

- `reader_device_info_dialog.xml` 是 `ScrollView > TableLayout` 十行「标签 : 值」，其中「子类型原始值」是调试字段，要去掉。
- 没有分组、没有层级，纯表格，观感简陋。

### 设计方案

改成「头部 + 三分组」结构，弹窗自带 title 去掉，由布局内的头部承担：

```
┌─────────────────────────────────────┐
│ ⬢  RM8011 读写器            ● 已连接 │   ← 头部：图标 + 设备名 + 状态 chip
│    ISO 18000-6C                     │
├─────────────────────────────────────┤
│ 连接                                │   ← 分组标题（小号、muted、加字距）
│   传输方式                    蓝牙   │
│   地址 / IP        AA:BB:CC:DD:EE:FF │
├─────────────────────────────────────┤
│ 板卡                                │
│   序列号                  1234567890 │
│   版本                        V1.2.3 │
├─────────────────────────────────────┤
│ 射频模块                            │
│   型号                       RM8011 │
│   序列号                  9876543210 │
│   版本                        V2.0.1 │
└─────────────────────────────────────┘
```

要点：分组标题吃掉重复前缀（「板卡序列号」→ 分组「板卡」+ 行「序列号」）；标签左、值右；值用等宽字体便于核对序列号；「子类型原始值」整行删除。

**9.1 `styles.xml`** — 追加三个样式：

```xml
    <style name="ReaderDeviceInfoGroup" parent="TextAppearance.MaterialComponents.Overline">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:layout_marginTop">@dimen/dp_12</item>
        <item name="android:layout_marginBottom">@dimen/dp_2</item>
        <item name="android:letterSpacing">0.08</item>
        <item name="android:textColor">@color/rfid_text_muted</item>
        <item name="android:textSize">@dimen/sp_11</item>
    </style>

    <style name="ReaderDeviceInfoRow">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:gravity">center_vertical</item>
        <item name="android:orientation">horizontal</item>
        <item name="android:paddingTop">@dimen/dp_6</item>
        <item name="android:paddingBottom">@dimen/dp_6</item>
    </style>

    <style name="ReaderDeviceInfoDivider">
        <item name="android:layout_width">match_parent</item>
        <item name="android:layout_height">@dimen/dp_1</item>
        <item name="android:layout_marginTop">@dimen/dp_8</item>
        <item name="android:background">@color/rfid_line</item>
    </style>
```

`ReaderDeviceInfoLabel` / `ReaderDeviceInfoValue` 两个已有样式微调，让行内左右分布并让值等宽：

```xml
    <style name="ReaderDeviceInfoLabel" parent="TextAppearance.MaterialComponents.Body2">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:paddingEnd">16dp</item>
        <item name="android:textColor">@color/rfid_text_muted</item>
        <item name="android:textSize">@dimen/sp_13</item>
    </style>

    <style name="ReaderDeviceInfoValue" parent="TextAppearance.MaterialComponents.Body2">
        <item name="android:layout_width">0dp</item>
        <item name="android:layout_weight">1</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:fontFamily">monospace</item>
        <item name="android:gravity">end</item>
        <item name="android:textColor">@color/rfid_text</item>
        <item name="android:textSize">@dimen/sp_13</item>
    </style>
```

> `layout_weight` 只在 `LinearLayout` 里生效，所以下面的布局必须从 `TableLayout` 换成嵌套 `LinearLayout`，不能保留 `TableRow`。

**9.2 `reader_device_info_dialog.xml`** — 整个文件重写。头部部分：

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="@dimen/dp_20"
        android:paddingTop="@dimen/dp_20"
        android:paddingBottom="@dimen/dp_4">

        <!-- 头部：图标 + 设备名/协议 + 连接状态 chip -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageView
                android:id="@+id/iv_device_info_icon"
                android:layout_width="@dimen/dp_40"
                android:layout_height="@dimen/dp_40"
                android:background="@drawable/rfid_chip_blue_bg"
                android:contentDescription="@null"
                android:padding="@dimen/dp_8"
                android:src="@drawable/rfid_bluetooth_ic" />

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="@dimen/dp_12"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/tv_device_info_name"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ellipsize="end"
                    android:fontFamily="sans-serif-medium"
                    android:singleLine="true"
                    android:textColor="@color/rfid_text"
                    android:textSize="@dimen/sp_16" />
```

```xml
                <TextView
                    android:id="@+id/tv_device_info_protocol"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/dp_2"
                    android:singleLine="true"
                    android:textColor="@color/rfid_text_muted"
                    android:textSize="@dimen/sp_12" />
            </LinearLayout>

            <TextView
                android:id="@+id/tv_device_info_status"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="@drawable/rfid_chip_green_bg"
                android:paddingHorizontal="@dimen/dp_8"
                android:paddingVertical="@dimen/dp_2"
                android:text="@string/device_info_connected"
                android:textColor="@color/white"
                android:textSize="@dimen/sp_11" />
        </LinearLayout>

        <View style="@style/ReaderDeviceInfoDivider" />
```

分组部分（三段结构相同，只列第一段与后两段的行，`style` 全部复用 9.1 的样式）：

```xml
        <!-- 连接 -->
        <TextView style="@style/ReaderDeviceInfoGroup" android:text="@string/device_info_group_connection" />

        <LinearLayout style="@style/ReaderDeviceInfoRow">
            <TextView style="@style/ReaderDeviceInfoLabel" android:text="@string/device_info_transport" />
            <TextView android:id="@+id/tv_device_info_transport" style="@style/ReaderDeviceInfoValue" />
        </LinearLayout>

        <LinearLayout style="@style/ReaderDeviceInfoRow">
            <TextView style="@style/ReaderDeviceInfoLabel" android:text="@string/device_info_address" />
            <TextView android:id="@+id/tv_device_info_address" style="@style/ReaderDeviceInfoValue" />
        </LinearLayout>

        <View style="@style/ReaderDeviceInfoDivider" />

        <!-- 板卡 -->
        <TextView style="@style/ReaderDeviceInfoGroup" android:text="@string/device_info_group_board" />

        <LinearLayout style="@style/ReaderDeviceInfoRow">
            <TextView style="@style/ReaderDeviceInfoLabel" android:text="@string/device_info_serial" />
            <TextView android:id="@+id/tv_device_info_board_serial" style="@style/ReaderDeviceInfoValue" />
        </LinearLayout>

        <LinearLayout style="@style/ReaderDeviceInfoRow">
            <TextView style="@style/ReaderDeviceInfoLabel" android:text="@string/device_info_version" />
            <TextView android:id="@+id/tv_device_info_board_version" style="@style/ReaderDeviceInfoValue" />
        </LinearLayout>

        <View style="@style/ReaderDeviceInfoDivider" />
```

```xml
        <!-- 射频模块 -->
        <TextView style="@style/ReaderDeviceInfoGroup" android:text="@string/device_info_group_module" />

        <LinearLayout style="@style/ReaderDeviceInfoRow">
            <TextView style="@style/ReaderDeviceInfoLabel" android:text="@string/device_info_model" />
            <TextView android:id="@+id/tv_device_info_subtype" style="@style/ReaderDeviceInfoValue" />
        </LinearLayout>

        <LinearLayout style="@style/ReaderDeviceInfoRow">
            <TextView style="@style/ReaderDeviceInfoLabel" android:text="@string/device_info_serial" />
            <TextView android:id="@+id/tv_device_info_module_serial" style="@style/ReaderDeviceInfoValue" />
        </LinearLayout>

        <LinearLayout style="@style/ReaderDeviceInfoRow">
            <TextView style="@style/ReaderDeviceInfoLabel" android:text="@string/device_info_version" />
            <TextView android:id="@+id/tv_device_info_module_version" style="@style/ReaderDeviceInfoValue" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

`tv_device_info_subtype_raw` 整行不再出现（任务要求）。原来的 `tv_device_info_protocol` 从「当前协议」行升到头部副标题，`tv_device_info_name` 升到头部主标题。

**9.3 `strings.xml`** — 把弹窗里的硬编码文案收进资源（原文件是写死的中文）：

```xml
    <string name="device_info_title">设备信息</string>
    <string name="device_info_connected">已连接</string>
    <string name="device_info_group_connection">连接</string>
    <string name="device_info_group_board">板卡</string>
    <string name="device_info_group_module">射频模块</string>
    <string name="device_info_transport">传输方式</string>
    <string name="device_info_address">地址 / IP</string>
    <string name="device_info_serial">序列号</string>
    <string name="device_info_version">版本</string>
    <string name="device_info_model">型号</string>
    <string name="device_info_transport_ble">蓝牙</string>
    <string name="device_info_transport_wifi">Wi-Fi</string>
    <string name="device_info_name_wifi">Wi-Fi 读写器</string>
    <string name="device_info_close">关闭</string>
```

**9.4 `ReaderDeviceInfoDialog.java`** — `bind` 改为实例方法（要用 `getString`），并按传输方式换头部图标：

```java
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.reader_device_info_dialog,
                new FrameLayout(requireContext()), false);
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        bind(view, session.getState());
        // 标题移进布局头部，这里不再 setTitle。
        return new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setPositiveButton(R.string.device_info_close, null)
                .create();
    }

    private void bind(View view, ReaderState state) {
        boolean ble = state.getTransport() == TransportType.BLE;
        ((ImageView) view.findViewById(R.id.iv_device_info_icon)).setImageResource(
                ble ? R.drawable.rfid_bluetooth_ic : R.drawable.rfid_wifi_ic);
        set(view, R.id.tv_device_info_name, ble
                ? state.getDeviceName() : getString(R.string.device_info_name_wifi));
        set(view, R.id.tv_device_info_protocol, state.getProtocol().getDisplayName());
        set(view, R.id.tv_device_info_transport, getString(ble
                ? R.string.device_info_transport_ble : R.string.device_info_transport_wifi));
        set(view, R.id.tv_device_info_address, state.getAddress());
        set(view, R.id.tv_device_info_board_serial, state.getBoardSerial());
        set(view, R.id.tv_device_info_board_version, state.getBoardVersion());
        set(view, R.id.tv_device_info_subtype, state.getModuleSubtype().getDisplayName());
        set(view, R.id.tv_device_info_module_serial, state.getModuleSerial());
        set(view, R.id.tv_device_info_module_version, state.getModuleVersion());
    }
```

`set(...)` 保持 `private static`（只写文本，不需要 Context），`getRawModuleSubtype()` 那行删除。新增 import `android.widget.ImageView;`。

### 验证

- 弹窗不再出现「子类型原始值」。
- 头部：蓝牙连接显示蓝牙图标 + 设备名 + 绿色「已连接」chip；Wi-Fi 连接显示 Wi-Fi 图标 + 「Wi-Fi 读写器」。
- 三组分隔线清晰，序列号等宽右对齐，长序列号不换行错位。
- 字段缺失时显示 `--`，不是空白。
- 弹窗打开期间断开连接，弹窗自动关闭（原有 `onReaderStateChanged` 行为不变）。
- 小屏（sw360dp）下可滚动，不裁切底部。

## 全局验证

每个任务改完立刻编译，避免错误堆积：

```bash
cd /Users/lei/Projects/UhfRemote
./gradlew :app:compileDebugJavaWithJavac
```

九个任务全部完成后：

```bash
./gradlew :app:assembleDebug
```

编译通过后再做一遍全局残留检查：

```bash
# 应已全部消失
grep -rn "sw_inventory_mask\|fl_inventory_mask_switch_target" app/src/main
grep -rn "btn_inventory_mask_apply\|btn_inventory_mask_clear" app/src/main
grep -rn "tv_device_info_subtype_raw" app/src/main
grep -rn "setQueryGroup\|getQueryGroup" app/src/main
grep -rn "setSessionTarget\|config_session_target\|config_session_format" app/src/main
grep -rn "savedSelected\|saveSelectedIfAbsent" app/src/main
grep -rn "restoreMaskSwitch\|setMaskSwitchChecked\|bindingMaskSwitch" app/src/main

# 应只剩 ReaderSessionManager.setQ 一处在用
grep -rn "setMagicQuery" app/src/main
```

`ReaderConfigFragment` 里若还有别的地方引用 `configuration.target`，保持不动 —— target 仍在 `ReaderConfiguration` 里正常读写，只是不再由用户直接选择。

## 真机回归清单

按顺序在 RM8011（旗连）与 R2000 两台设备上各跑一遍：

1. **连接** → 握手日志出现 `读取 Session`，`selected` 被缓存（新增日志 `setSession ... selected=` 可验证）。
2. **设备信息弹窗** → 头部、三分组、无「子类型原始值」。
3. **协议行** → 显示 `ISO 18000-6C`；切协议弹窗四项完整命名。
4. **工作模式** → RM8011 置灰显示「高性能盘点」；R2000 三项可选并生效。
5. **Session** → 6C 下弹窗 4 项 S0–S3，改完显示 `S2`；切 6B 后行置灰。
6. **盘点列表** → 各列颜色区分；切到 EPC+TID 区域，TID 段紫色；切到 EPC+USER，USER 段青色。
7. **掩码面板** → 宽度与列表齐平；单按钮「应用掩码」→ 红色「取消掩码」→ 回蓝。
8. **item 小锁** → 未掩码灰色开口，掩码后红色闭合。
9. **盘点中掩码开关 ×10** → 每次都成功，盘点自动续上（任务 4 核心）。
10. **切页 ×5** → 掩码状态、按钮颜色、锁形态全部保持（任务 5 核心）。
11. **掩码期间改 session** → 掩码不失效。
12. **杀进程重进** → 工作模式、session 等配置从缓存恢复。

任一项不通过，先看该任务「验证」小节的对应条目，再回到「修改清单」核对是否漏改。

## 影响面小结

| 文件 | 涉及任务 |
| --- | --- |
| `res/values/colors.xml` | 1 |
| `res/values/styles.xml` | 9 |
| `res/values/strings.xml` | 2 / 6 / 7 / 8 / 9 |
| `res/color/rfid_danger_button_background.xml`（新增） | 3 |
| `res/drawable/rfid_lock_open_ic.xml`、`rfid_lock_closed_ic.xml`（新增） | 2 |
| `res/layout/inventory_item.xml` | 1 / 2 |
| `res/layout/inventory_fragment.xml` | 1 |
| `res/layout/inventory_mask_panel.xml` | 3 |
| `res/layout/reader_device_info_dialog.xml` | 9 |
| `ui/adapter/InventoryAdapter.java` | 1 / 2 |
| `ui/fragment/home/InventoryFragment.java` | 2 / 3 / 5 |
| `ui/fragment/home/ReaderConfigFragment.java` | 6 / 8 |
| `ui/dialog/ReaderDeviceInfoDialog.java` | 9 |
| `reader/ModuleSubtype.java` | 6 |
| `reader/TagProtocol.java` | 7 |
| `reader/UhfSdkGateway.java` | 8 |
| `reader/NativeUhfSdkGateway.java` | 4 / 8 |
| `reader/ReaderSessionManager.java` | 4 / 5 / 6 / 8 |
| `reader/ReaderConfigCache.java` | 8 |
| `reader/ReaderHandshake.java` | 8 |

`library/` 四个模块一行不动。`ReaderConfiguration` 的字段与构造函数不动（`selected` 走 `ReaderConfigCache` 单独的 key）。
