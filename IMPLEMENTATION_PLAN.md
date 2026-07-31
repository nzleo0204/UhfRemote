# UhfRemote 新一轮实现方案

> 更新于 2026-07-31。本轮目标：完善多子模块配置展示、标题栏固定、盘点/单标签掩码功能。
> 项目背景：连接方式固定为 RM70XX（BLE 透传或 WiFi），子模块由 `getBoardModuleType()` 运行时
> 检测，可能是 R2000(0)、MagicRF(1)、R2000Plus(3)、RM100X(6)。

---

## 0. 代码规范约定

### 0.1 代码分类组织

**按业务关注点分段，而非按方法拆类**。在单个文件内用注释分隔不同职责区域：

```java
public class InventoryFragment extends AppFragment<HomeActivity> {
    // ========== 字段声明 ==========
    private ReaderSessionManager session;
    private InventoryAdapter adapter;
    // 掩码面板控件
    private View maskPanel;
    private Spinner maskBankSpinner;
    
    // ========== 连接管理 ==========
    @Override
    public void onReaderStateChanged(ReaderState state) { ... }
    
    private void updateConnectionStatus() { ... }
    
    // ========== 盘点操作 ==========
    @SingleClick
    private void toggleInventory() { ... }
    
    private void handleInventoryResult() { ... }
    
    // ========== 掩码管理 ==========
    @Override
    public void onInventoryMaskChanged(InventoryMaskConfig config) { ... }
    
    @SingleClick
    private void applyMask() { ... }
    
    @SingleClick
    private void clearMask() { ... }
    
    private void updateMaskStatusDisplay() { ... }
    
    // ========== 数据导出 ==========
    private void exportToCsv() { ... }
}
```

**禁止**：为每个小功能创建独立的 Helper/Util/Manager 类导致类爆炸。

### 0.2 日志与注释

1. **Log 策略**：
   - `ReaderSessionManager` 用 `TAG = "UhfReader"`
   - Fragment 用 `TAG = "UhfReader"` + 类名后缀（如 `"UhfReader/Config"`）
   - `Log.d` — UI 状态变化（模块切换、行显隐）
   - `Log.i` — SDK 操作（掩码设置/清除、协议切换、盘点启停）
   - `Log.e` — 错误路径，必须带错误码或异常对象

2. **注释位置**：
   - 方法头部一行 Javadoc 说明"为什么需要这个方法"
   - 关键分支行内注释说明业务逻辑
   - 段落分隔注释使用 `// ========== 标题 ==========` 格式

3. **资源字符串**：所有中文文案进 `strings.xml`，禁止硬编码。

---

## 一、UI 界面设计

### 1.1 配置页 (ReaderConfigFragment) — 布局改动

**当前问题**：AppName + 状态 chip 区域在 ScrollView 内，向下滚动后消失。

**改后结构（ASCII 示意）**：

```
┌─────────────────────────────────────────┐
│  UHF Remote              [● 已连接]     │  ← 固定 header（rfid_nav_bg，56~72dp）
│  192.168.1.100                          │    从 ScrollView 内移出，始终可见
├─────────────────────────────────────────┤
│  ┌──────── 发射功率 ────────────────────┐│  ↑
│  │ 发射功率                    26 dBm   ││  │
│  │ ●━━━━━━━━━━━━━━━━━━━━━━━━━━━○       ││  │
│  │ 0 dBm                     33 dBm    ││  │  ScrollView 区域
│  └─────────────────────────────────────┘│  │  (可滚动)
│                                         │  │
│  ┌──────── 读取协议 ────────────────────┐│  │
│  │ 协议                           6C > ││  │
│  │ 工作模式                     连续 > ││  │
│  │ Session / Target          S1 · A > ││  │
│  └─────────────────────────────────────┘│  │
│  ┌──────── 连接方式 ────────────────────┐│  │
│  │ 🔵 蓝牙          ●                   ││  │
│  │ 📶 WiFi           ○                  ││  │
│  │ [UHF-Reader-001]        [扫描设备]  ││  │
│  └─────────────────────────────────────┘│  │
│  ┌──────── 速率参数 ────────────────────┐│  │
│  │ BLF 速率                    256K > ││  │
│  │ Q 参数                   动态(Q4) > ││  ↓
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

**MagicRF 时**（BLF、SeekBar 隐藏，工作模式置灰）：
```
┌─────────────────────────────────────────┐
│  UHF Remote              [● 已连接]     │  ← 固定 header
├─────────────────────────────────────────┤
│  ┌──────── 发射功率 ────────────────────┐│
│  │ 发射功率                    20 dBm   ││  ← 点击数值弹离散选择框（0~20 dBm）
│  │          [SeekBar 隐藏]              ││    无 SeekBar
│  └─────────────────────────────────────┘│
│  ┌──────── 读取协议 ────────────────────┐│
│  │ 协议                           6C > ││  ← 仅 6C 可选
│  │ 工作模式          连续（不可点击）    ││  ← 置灰，固定连续
│  │ Session / Target          S1 · A > ││
│  └─────────────────────────────────────┘│
│  ┌──────── 连接方式 ─...               ││
│  ┌──────── 速率参数 ────────────────────┐│
│  │ Q 参数                      固定4 > ││  ← 仅固定Q，BLF 行隐藏
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

---

### 1.2 盘点页 (InventoryFragment) — 手动掩码面板新增

**设计原则**：掩码完全由用户**手动开关 + 手动填写**，程序不做任何自动应用。

**折叠状态（默认，掩码关闭）**：

```
┌─────────────────────────────────────────┐
│  实时盘点                       [12]    │  ← 固定 header（已是固定，无改动）
├─────────────────────────────────────────┤
│ [▶ 开始盘点] [清除] [导出]              │  ← 操作栏
├─────────────────────────────────────────┤
│ ▼ 掩码过滤                    [ ○——]   │  ← 标题行可点展开；右侧手动开关（关）
├─────────────────────────────────────────┤
│ #   EPC                  次数  RSSI 芯片│  ← 表头（固定）
├─────────────────────────────────────────┤
│ 1   E28011C000200001234  3    -55  Monza│  ↑
│ 2   E28011C000200001235  1    -62     - │  │  RecyclerView
│ ...                                     │  ↓
└─────────────────────────────────────────┘
```

**展开状态（开关打开，等待手动填写并应用）**：

```
├─────────────────────────────────────────┤
│ ▲ 掩码过滤                    [——● ]   │  ← 开关打开，展开表单
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
│  存储区  [EPC ▾]      偏移(bit) [32  ] │  ← 手动填写区（开关关闭时整体置灰禁用）
│  长度(bit) [32  ]                       │
│  掩码 HEX  [E28011C0002000            ] │  ← 手动输入，只允许 0-9A-Fa-f
│                                         │
│  [  应用掩码  ]        [  取消掩码  ]   │  ← 手动触发；未应用时"取消"置灰
├─────────────────────────────────────────┤
```

**已应用状态（标题行显示摘要 chip）**：
```
│ ▲ 掩码过滤   [● EPC·偏32·32bit] [——● ] │  ← 蓝色 chip 显示生效摘要
```

**交互规则**：

| 操作 | 行为 |
|---|---|
| 开关 → 打开 | 展开表单，输入控件启用。**不立即下发**，等用户点「应用掩码」 |
| 开关 → 关闭 | 若已应用则自动调 `clearMask()`；表单置灰折叠 |
| 点「应用掩码」 | 校验表单 → 下发 SDK → 成功后标题行显示蓝色摘要 chip |
| 点「取消掩码」 | 调 `clearMask()`，chip 消失，**表单内容保留**（方便再次应用） |
| 表单校验失败 | Toast 提示具体原因，不下发 |

---

### 1.3 单标签页 (SingleTagFragment) — 标题固定 + 目标指示

**当前问题**：根为 ScrollView，"单标签操作"标题随内容滚动消失。

**改后布局**：

```
┌─────────────────────────────────────────┐
│              单标签操作                  │  ← 固定 header（LinearLayout 根，非 ScrollView）
├─────────────────────────────────────────┤
│  ↑ NestedScrollView 区域（可滚动）       │
│  ┌──────── 当前标签信息 ───────────────┐ │
│  │ 🔵 目标 EPC: E28011C000200001234    │ │  ← 新增目标锁定指示（蓝色）
│  │    已锁定此标签，操作将精准定向      │ │
│  │ EPC   E28011C000200001234           │ │
│  │ TID   E28011C0002019B7123A          │ │
│  │ 芯片  Impinj Monza                  │ │
│  │ RSSI  -55 dBm                       │ │
│  │         [📡 读取标签]               │ │
│  └─────────────────────────────────────┘ │
│  ┌──────── 标签操作 ───────────────────┐ │
│  │ ✏️  写入数据                         │ │
│  │ 🏷️  修改 EPC                         │ │
│  │ 🔒 锁定标签                          │ │
│  │ 💣 销毁标签                          │ │
│  └─────────────────────────────────────┘ │
│  ↓                                       │
└─────────────────────────────────────────┘
```

**无目标标签时**（灰色提示）：
```
│  │ ⚪ 暂无目标标签                       │ │
│  │    点击「读取标签」扫描周围标签       │ │
```

---

## 二、模块设置差异矩阵

### 2.1 配置页设置行可见性

| 设置行 | R2000 | R2000Plus | MagicRF | RM100X |
|---|:---:|:---:|:---:|:---:|
| 功率 — SeekBar 连续调节（0~33 dBm） | ✓ | ✓ | ✗ | ✓ |
| 功率 — 固定范围点击选择（0~20 dBm） | ✗ | ✗ | ✓ | ✗ |
| 协议选择 | 6C/6B/GJB/GB | 6C/6B/GJB/GB | 仅 6C | 6C + GJB |
| 工作模式（单次/连续/低功耗） | ✓ 可改 | ✓ 可改 | ✗ 固定连续 | ✓ 可改 |
| Session / Target | ✓ | ✓ | ✓ | ✓ |
| BLF 速率 | ✓ | ✓ | ✗ | ✓ |
| Q 参数（动态 + 固定） | ✓ | ✓ | ✗ 仅固定 | ✓ |

**MagicRF 功率简化（重要修改）**：统一使用 0~20 dBm 整数范围，不再根据序列号/固件版本
做多分支检测。`MagicPowerLevels.java` 中复杂的 `forModule(serial, version)` 逻辑
**不再使用**，改为固定生成 `[0, 1, 2, ..., 20]` 21 个档位。详见 T3.2。

**判定入口**：`ReaderConfigFragment.applyModuleUi(ModuleSubtype)`
当前只处理了 MagicRF 的 BLF/SeekBar 隐藏，本轮需补全工作模式和功率选择器逻辑（见 T3）。

### 2.2 盘点页掩码面板（新增）

盘点页无模块差异设置，新增**掩码过滤面板**（默认折叠的卡片）：

| 控件 | 说明 |
|---|---|
| 存储区 Spinner | 根据当前协议动态填充（6C: 保留/EPC/TID/USER；6B/GJB 固定用 EPC/UID） |
| 偏移量 EditText | bit 偏移，整数，默认 0 |
| 长度 EditText | bit 长度，整数，默认 32 |
| 掩码 HEX EditText | 十六进制字符串，hint 示例 "E28011..." |
| 一键应用 Button | 解析表单 → `session.applyInventoryMask(config)` |
| 一键取消 Button | `session.clearInventoryMask()` |

**关键约束**：`clearInventoryMask()` 仅将 Select 条件的 status 置 0，
不改变功率/BLF/Q 等任何参数，确保"取消后不影响最初配置"。

### 2.3 单标签页目标标签指示（新增）

单标签操作掩码由 `withTargetMask()` 自动管理（读取→setMask→操作→clearMask），无需手动。
新增目标标签状态指示条：

| 状态 | 显示内容 | 颜色 |
|---|---|---|
| 无目标标签 | "暂无目标标签 — 点击「读取标签」扫描" | 灰色 |
| 有目标标签 | "目标 EPC: E28011… · 操作已定向此标签" | 主题蓝 |

---

## 二、固定标题栏方案

### 问题诊断

| 页面 | 当前根布局 | 问题 |
|---|---|---|
| `inventory_fragment.xml` | `LinearLayout` | 标题已在顶部固定 **✅ 无需改动** |
| `single_tag_fragment.xml` | `ScrollView` | 标题 TextView 在 ScrollView 内，随滚动消失 **❌** |
| `reader_config_fragment.xml` | `FrameLayout` → 内部 `ScrollView` | AppName/Status 头部在 ScrollView 内，随滚动消失 **❌** |

### T2.1 — single_tag_fragment.xml

**策略**：保留 `FrameLayout` 根外层 id，内改为 `LinearLayout(vertical)` + 固定标题 + `NestedScrollView`。

```
FrameLayout (或直接 LinearLayout 作为根)
  ├── LinearLayout (标题栏, height=56dp, rfid_nav_bg, fixed)
  │     └── TextView ("单标签操作")
  └── NestedScrollView (layout_weight=1)
        └── LinearLayout (内容)
              ├── Card: 当前标签信息 + 读取按钮
              └── Card: 标签操作列表
```

`SingleTagFragment.java` 无需改动 Java 代码（无 ScrollView 引用）。

### T2.2 — reader_config_fragment.xml

**策略**：保持外层 `FrameLayout(fl_config_root)` 不变（IME 遮罩层依赖 FrameLayout），
内部增加一层 `LinearLayout` 将 header 固定在 ScrollView 之上：

```
FrameLayout (fl_config_root)               ← 保留，IME guard 层叠于此
  ├── LinearLayout (vertical, match_parent) ← 新增包裹
  │     ├── [header: AppName + Status chip] ← 从 ScrollView 内移出，固定
  │     └── ScrollView (sv_config_root, layout_weight=1)
  │           └── ...各配置卡片...
  ├── View (v_config_input_guard_top)
  └── View (v_config_input_guard_bottom)
```

`ReaderConfigFragment.java`：`configRoot(fl_config_root)` 和 `configScroll(sv_config_root)`
的引用 id 均不变，IME inset 监听逻辑无需修改。

---

## 三、掩码功能实现

### 3.1 新增 `InventoryMaskConfig.java`

**路径**：`reader/InventoryMaskConfig.java`

不可变对象，线程安全：

```java
/**
 * 盘点掩码配置。对应 SDK set18K6CSelectCriteria / set18K6BSelectCriteria 的参数子集。
 * 持有 mask 的深拷贝，保证外部修改不影响已提交的配置。
 */
public final class InventoryMaskConfig {
    public final int bank;       // 存储区（6C: 0=保留 1=EPC 2=TID 3=USER）
    public final int offsetBits; // bit 偏移
    public final int lengthBits; // 有效掩码 bit 数
    public final byte[] mask;    // 掩码字节数组（深拷贝）

    public InventoryMaskConfig(int bank, int offsetBits, int lengthBits, byte[] mask) {
        this.bank = bank;
        this.offsetBits = offsetBits;
        this.lengthBits = lengthBits;
        this.mask = Arrays.copyOf(mask, mask.length); // 深拷贝
    }
}
```

### 3.2 UhfSdkGateway 接口扩展

```java
/** 应用盘点掩码，覆盖 Select 条件。不影响功率/BLF/Q 等参数。 */
int applyInventoryMask(TagProtocol protocol, InventoryMaskConfig config);

/** 清除盘点掩码，仅将 Select status 置0。不改变其他任何配置。 */
int clearInventoryMask(TagProtocol protocol);
```

### 3.3 NativeUhfSdkGateway 实现

`applyInventoryMask`：
- 6C 路径：填充 `SelectCriteria`，bank/offset/length/mask 来自 `InventoryMaskConfig`，
  status=1，selectorIdx=0，session=4，action=0，调用 `linkage.set18K6CSelectCriteria(criteria)`
- 6B 路径：填充 `Select6BCriteria`，status=1，length=maskConfig.mask.length，
  调用 `linkage.set18K6BSelectCriteria(criteria)`
- GJB/GB 路径：使用 6C 路径（GJB 底层走 6C Select 指令）

`clearInventoryMask`：直接委托现有 `clearTargetMask(protocol)`，语义完全等价。

### 3.4 ReaderObserver 扩展

```java
/** 盘点掩码状态变化时回调。config=null 表示掩码已清除。 */
default void onInventoryMaskChanged(@Nullable InventoryMaskConfig config) {}
```

### 3.5 ReaderSessionManager 扩展

```java
// --- 新增字段 ---
/** 当前激活的盘点掩码，null 表示无掩码。断连不自动清除（保持用户意图）。 */
private volatile InventoryMaskConfig inventoryMask;

// --- 新增公开方法 ---
/** 应用盘点掩码并通知 UI。成功后掩码在每次 startInventory 时自动重新应用。 */
public CompletableFuture<Integer> applyInventoryMask(InventoryMaskConfig config) {
    return submitConnected(() -> {
        int status = monitorSdkStatus(
                gateway.applyInventoryMask(state.getProtocol(), config));
        Log.i(TAG, "applyInventoryMask status=" + status
                + " bank=" + config.bank + " offsetBits=" + config.offsetBits
                + " lengthBits=" + config.lengthBits);
        if (status == 0) {
            inventoryMask = config;
            notifyMask(config);
        }
        return status;
    });
}

/** 清除盘点掩码（仅影响 Select 条件）。 */
public CompletableFuture<Integer> clearInventoryMask() {
    return submitConnected(() -> {
        int status = monitorSdkStatus(
                gateway.clearInventoryMask(state.getProtocol()));
        Log.i(TAG, "clearInventoryMask status=" + status);
        if (status == 0) {
            inventoryMask = null;
            notifyMask(null);
        }
        return status;
    });
}

public boolean hasInventoryMask() { return inventoryMask != null; }

private void notifyMask(@Nullable InventoryMaskConfig config) {
    mainHandler.post(() ->
            observers.forEach(o -> o.onInventoryMaskChanged(config)));
}
```

在 `startInventory()` 中，`configureDefaultInventory` 之后、`gateway.startInventory` 之前：

```java
// 重新应用盘点掩码（防止断连重连后硬件侧掩码丢失）
if (inventoryMask != null) {
    int maskStatus = gateway.applyInventoryMask(state.getProtocol(), inventoryMask);
    Log.i(TAG, "inventory mask re-applied on start status=" + maskStatus);
    if (maskStatus != 0) { return maskStatus; }
}
```

---

## 四、任务清单（执行顺序）

### T1 — RM100X 协议修复（最小改动，先做）

**文件**：`reader/ModuleSubtype.java`，`supportedProtocols()` 方法

```java
// 改前
if (this == MAGIC_RF || this == RM100X) {
    return EnumSet.of(TagProtocol.ISO_18000_6C);
}
// 改后（RM100X 支持 6C + GJB 7377.1；不支持 6B/GB）
if (this == MAGIC_RF) {
    return EnumSet.of(TagProtocol.ISO_18000_6C);
}
if (this == RM100X) {
    return EnumSet.of(TagProtocol.ISO_18000_6C, TagProtocol.GJB_7377_1);
}
```

### T2 — 固定标题栏

1. 改 `res/layout/single_tag_fragment.xml`（根改为 LinearLayout，NestedScrollView 包内容）
2. 改 `res/layout/reader_config_fragment.xml`（header 提到 ScrollView 外）

### T3 — 配置页模块感知完善

**涉及文件**：`ui/fragment/home/ReaderConfigFragment.java`、`reader/MagicPowerLevels.java`

#### T3.1 — 简化 MagicRF 功率范围

**修改 `MagicPowerLevels.java`**：移除根据序列号/固件版本做多分支的 `forModule(serial, version)` 逻辑，
统一返回 0~20 dBm 整数档位：

```java
/** MagicRF 模块功率档位。统一使用 0~20 dBm，不做版本区分。 */
public final class MagicPowerLevels {
    /** 返回 MagicRF 可用的功率值（单位：十分之一 dBm）。固定为 0~20 dBm 整数步进。 */
    public static int[] levels() {
        int[] result = new int[21]; // 0, 10, 20, ..., 200（十分之一 dBm）
        for (int i = 0; i <= 20; i++) { result[i] = i * 10; }
        return result;
    }

    private MagicPowerLevels() {} // 工具类，禁止实例化
}
```

#### T3.2 — 重构 `applyModuleUi()`

将所有子模块相关的 UI 显隐逻辑集中到此方法，同时将 `setHardwareEnabled()` 中的
工作模式 enabled 控制迁移过来，避免双重控制：

```java
// ========== 模块感知 UI ==========

/** 根据子模块类型调整配置行可见性与可用性。连接/模块变化时调用。 */
private void applyModuleUi(ModuleSubtype subtype) {
    boolean isMagic = (subtype == ModuleSubtype.MAGIC_RF);
    boolean connected = readerState.isConnected();

    // 功率区：R2000系列用 SeekBar；MagicRF 点击数值弹离散选择框（0~20 dBm）
    powerSeekBar.setVisibility(isMagic ? View.GONE : View.VISIBLE);
    powerValueView.setClickable(isMagic);

    // BLF 速率行：R2000/RM100X 显示，MagicRF 无 BLF 概念
    findViewById(R.id.row_config_blf).setVisibility(isMagic ? View.GONE : View.VISIBLE);

    // 工作模式：MagicRF 固定连续（硬件不支持切换），其他模块连接后可改
    View workModeRow = findViewById(R.id.row_config_work_mode);
    workModeRow.setEnabled(!isMagic && connected);
    if (isMagic) { workModeView.setText(workModeLabel(1)); }

    // 协议选项由 supportedProtocols() 驱动，applyModuleUi 只需同步当前协议显示
    protocolView.setText(readerState.getProtocol().getDisplayName());

    Log.d(TAG, "applyModuleUi subtype=" + subtype
            + " isMagic=" + isMagic + " connected=" + connected);
}

/** MagicRF 功率离散选择弹窗。使用固定 0~20 dBm 范围，无需版本检测。 */
private void showMagicPowerDialog() {
    int[] levels = MagicPowerLevels.levels(); // 统一 0~20 dBm
    String[] labels = new String[levels.length];
    for (int i = 0; i < levels.length; i++) { labels[i] = formatPower(levels[i]); }
    new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.config_magic_power)
            .setSingleChoiceItems(labels, -1, (dialog, which) -> {
                handleResult(session.setPower(levels[which]), R.string.config_power_set_failed);
                dialog.dismiss();
            })
            .setNegativeButton(R.string.common_cancel, null)
            .show();
}
```

**同步修改 `setHardwareEnabled()`**：移除对 `row_config_work_mode` 的 enabled 控制
（已归并到 `applyModuleUi`），避免两个方法互相覆盖。

### T4 — 盘点页掩码面板

执行顺序：
1. 创建 `reader/InventoryMaskConfig.java`
2. 扩展 `reader/UhfSdkGateway.java`
3. 实现 `reader/NativeUhfSdkGateway.java`
4. 扩展 `reader/ReaderObserver.java`
5. 扩展 `reader/ReaderSessionManager.java`
6. 创建 `res/layout/inventory_mask_panel.xml`
7. 改造 `ui/fragment/home/InventoryFragment.java`

**InventoryFragment.java 改造要点**：

```java
// initView() 中增加掩码面板控件引用
private View maskPanelContent;   // 折叠区域
private View maskToggleRow;      // 点击展开/折叠
private Spinner maskBankSpinner;
private EditText maskOffsetView, maskLengthView, maskHexView;
private MaterialButton maskApplyButton, maskClearButton;
private TextView maskStatusView; // 显示"掩码激活：EPC 区，偏移0，32 bit"

// onReaderStateChanged() 中：
//   连接状态变化 → 更新 apply/clear 按钮 enabled
//   协议切换 → 重新填充 maskBankSpinner；若有激活掩码，自动清除并提示

// onInventoryMaskChanged() 中：
//   config != null → 更新 maskStatusView，maskClearButton enabled=true
//   config == null → maskStatusView 显示"无掩码"，maskClearButton enabled=false

// applyMask() 私有方法（@SingleClick）：
//   解析表单输入 → 构建 InventoryMaskConfig → session.applyInventoryMask(config)
//   成功 → toast("掩码已应用")；失败 → toast 错误码

// clearMask() 私有方法（@SingleClick）：
//   session.clearInventoryMask()
//   成功 → toast("掩码已清除")
```

### T5 — 单标签页目标标签指示

**文件**：`res/layout/single_tag_fragment.xml` + `ui/fragment/home/SingleTagFragment.java`

在"当前标签信息"卡片，读取按钮上方添加一行指示 TextView（id: `tv_single_target_hint`）。

`bindTag(ReaderTag tag)` 中更新：
```java
if (tag == null) {
    targetHintView.setText(R.string.single_no_target_hint);
    targetHintView.setTextColor(ContextCompat.getColor(requireContext(), R.color.rfid_text_muted));
} else {
    targetHintView.setText(getString(R.string.single_target_locked, tag.id));
    targetHintView.setTextColor(ContextCompat.getColor(requireContext(), R.color.rfid_primary_soft));
}
```

---

## 五、文件变更清单

### 新建文件（2个）

| 文件 | 说明 |
|---|---|
| `reader/InventoryMaskConfig.java` | 盘点掩码配置不可变 POJO |
| `res/layout/inventory_mask_panel.xml` | 掩码折叠面板布局 |

### 修改文件（10个）

| 文件 | 改动摘要 |
|---|---|
| `reader/ModuleSubtype.java` | RM100X 加入 GJB_7377_1 协议支持（2行） |
| `reader/MagicPowerLevels.java` | **简化**：移除版本检测多分支，统一返回 0~20 dBm 21个档位 |
| `reader/UhfSdkGateway.java` | 新增 `applyInventoryMask` / `clearInventoryMask` 接口声明 |
| `reader/NativeUhfSdkGateway.java` | 实现上述两个方法（6C/6B/GJB 路径分支） |
| `reader/ReaderObserver.java` | 新增 `onInventoryMaskChanged(@Nullable InventoryMaskConfig)` default 方法 |
| `reader/ReaderSessionManager.java` | 掩码字段、apply/clear 方法、startInventory 中自动重新应用 |
| `res/layout/single_tag_fragment.xml` | 根改 LinearLayout，标题固定，内容改 NestedScrollView，新增目标指示行 |
| `res/layout/reader_config_fragment.xml` | header 提到 ScrollView 外（FrameLayout 根保持不变） |
| `ui/fragment/home/ReaderConfigFragment.java` | 重构 applyModuleUi() 和 showMagicPowerDialog()，归并所有模块 UI 逻辑 |
| `ui/fragment/home/InventoryFragment.java` | 接入掩码面板，监听 onInventoryMaskChanged 回调，按连接/盘点/掩码/导出四段组织 |
| `ui/fragment/home/SingleTagFragment.java` | 新增目标标签指示条绑定，按连接/读取/读写操作/辅助工具四段组织 |
| `res/values/strings.xml` | 补充掩码面板和目标指示相关文案 |

---

## 六、验收标准

| 任务 | 验收条件 |
|---|---|
| T1 RM100X 协议 | 连接 RM100X 子模块后协议弹窗出现 "GJB 7377.1" 选项，切换生效 |
| T2 固定标题栏 | 配置、单标签页滚动内容时标题栏始终固定可见；盘点页本身无变化 |
| T3 模块感知 | MagicRF：BLF 行消失、SeekBar 消失、工作模式不可点；R2000/RM100X：三者均正常显示和可点 |
| T4 盘点掩码 | 设置 EPC 区 32bit 掩码 → 盘点只出匹配标签；清除后恢复全量；功率/BLF/Q 不受影响 |
| T5 单标签指示 | 读取标签后指示条显示 EPC；操作后不误操作其他标签 |
| 整体 | `./gradlew assembleDebug` 通过，无新增 lint 警告 |
