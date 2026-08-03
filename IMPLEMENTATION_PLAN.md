# UhfRemote 新一轮实现方案

> 更新于 2026-08-03。
> **当前执行方案：C + D 轮整改（2026-08-03）**
>
> **C 轮：参数管理与初始化优化**
> - RM100X 更名为 RM610
> - MagicRF 更名为 RM8011
> - RM610 功率分档处理（cmt 版本 0-20，非 cmt 版本索引档位）
> - 其他模块功率值校准（RM8011 按序列号 / 固件分 5 组档位，含半 dBm）
> - Session/Q/BLF 参数 UI 优化
> - RM8011 特殊 Q 值 API 处理（setMagicQuery）
> - 连接后初始化进度弹窗
> - 参数持久化（使用 MMKV）
> - 手动刷新参数功能
>
> **D 轮：盘点页 UI 优化**
> - RSSI 列动态显示（仅 R2000/R2000Plus）
> - 标签信息列重新布局（EPC/USER/TID 24位格式）
> - 盘点区域设置（依《UHF Library开发文档》§3.3.8 / §3.3.9，按协议切换选项集）
> - 列标题动态更新
> - 芯片型号识别（接上底层库的 `chipModel` / `tidPrefix`，App 侧原本未接）
> - 布局修正（居中对齐、掩码 switch 垂直居中）
>
> **重要提示**：
> - ✅ PDF 开发文档已成功读取（安装 poppler-utils 后）
> - Session/Q/BLF 参数说明已从 PDF §3.5.4, §3.5.10, §3.5.12, §3.5.14 章节提取
> - **盘点区域按 PDF §3.3.8 / §3.3.9 实现**（4 档 6C 选项 + startAddr/wordLen）；
>   `app/libs/uhf.jar` 内 `InventoryParams` 的旧注释与文档 TID/USER 顺序相反，
>   已在 D.0 与 D.3.6 记录，需真机核对一次
> - **持久化使用 MMKV 框架，不使用 SharedPreferences**
> - **6 步进度弹窗要求拆掉原子的 `readConfiguration()`**（C.5.3）：
>   它现在每个 SDK 调用都 `check(...)`，第一个失败就抛异常、所有参数一起回退，
>   与「某一项失败不打断流程」冲突；且分步进度也无处挂
> - 进度弹窗宿主是 **`ReaderConnectionDialog`**（握手期 `VERIFYING_MODULE`），
>   不是 `configInitDialog`（后者在 `CONNECTED` 之后才延迟 1050ms 出现）—— 见 C.5.4
> - RSSI 功能仅 R2000 系列支持，RM610/RM8011 不支持
> - 芯片型号依赖 TID：仅「6C + 盘点区域含 TID + 起始地址 0」时才有值，
>   因此 D.8 必须排在 D.3 之后
> - ⏭️ **设计文件对照（C.6）已确认跳过**（2026-08-03 用户决定，暂不提供导出图），
>   本轮不做设计稿查漏补缺，codex 无需等待该输入
>
> 「B. 本轮整改方案（2026-08-01）」已完成，保留备查。
> 「A. 上一轮整改方案」已完成，保留备查。

---

## C. 本轮整改方案（2026-08-03）

### C.0 核心需求

1. **模块命名统一**：
   - RM100X 和 RM610 是同一个模块，统一改为 RM610
   - **MagicRF 更名为 RM8011**（统一使用 RM8011，不再使用 MagicRF）
2. **RM610 功率分档**：
   - 序列号包含 "cmt" → 功率范围 0～20 dBm（整数）
   - 序列号不包含 "cmt" → 功率档位索引：`{"-1dBm", "02dBm", "05dBm", "08dBm", "11dBm", "14dBm", "17dBm", "20dBm"}`
3. **其他模块功率校准**：按 Uhf_Android 的 `setPower` / `getPower` 实现来设置和获取，并更新 UI
   - RM8011 档位随 `moduleSerial` / `moduleVersion` 分 5 组（含 `14.5 / 15.5 / 18.5` 半 dBm）
   - R2000 / R2000Plus 参考实现为自由输入，无档位表，保持现状（C.3.4）
4. **Session/Q/BLF 参数**：阅读开发文档，参考 Uhf_Android，完善 UI
   - **重要**：RM8011 的 Q 值设置方式与其他模块不同（使用 `setMagicQuery` API）
5. **连接后初始化流程**：不可打断的进度弹窗，依次获取各参数
6. ~~**设计文件同步**：根据最新设计文件查漏补缺~~ → **本轮跳过**（见 C.6）

### C.1 模块重命名

#### C.1.1 RM100X → RM610

**涉及文件**：
- `reader/ModuleSubtype.java`
- 所有引用 `RM100X` / `RM100x` / `RM_100X` 的代码

**改动**：

```java
// ModuleSubtype.java
public enum ModuleSubtype {
    R2000(0),
    RM8011(1),      // 改前：MAGIC_RF(1)
    R2000_PLUS(3),
    RM610(6),       // 改前：RM100X(6)
    UNKNOWN(Integer.MIN_VALUE);
    
    // ...
    
    public String getDisplayName() {
        return switch (this) {
            case R2000 -> "R2000";
            case RM8011 -> "RM8011";          // 改前：MagicRF
            case R2000_PLUS -> "R2000Plus";
            case RM610 -> "RM610";            // 改前：RM100X
            case UNKNOWN -> "未知";
        };
    }
    
    public boolean isR2000Style() {
        return this == R2000 || this == R2000_PLUS || this == RM610;  // 改前：RM100X
    }
    
    public Set<TagProtocol> supportedProtocols() {
        // ...
        if (this == RM8011) {  // 改前：MAGIC_RF
            return EnumSet.of(TagProtocol.ISO_18000_6C);
        }
        if (this == RM610) {   // 改前：RM100X
            return EnumSet.of(TagProtocol.ISO_18000_6C, TagProtocol.GJB_7377_1);
        }
        // ...
    }
}
```

**全局替换**：
```bash
# 使用 IDE 的 Refactor > Rename 功能，或全局搜索替换
RM100X → RM610
RM100x → RM610
RM_100X → RM610

MAGIC_RF → RM8011
MagicRF → RM8011
MagicRf → Rm8011
```

**文件重命名**：
```bash
mv app/src/main/java/com/leo/remote/reader/MagicPowerLevels.java \
   app/src/main/java/com/leo/remote/reader/Rm8011PowerLevels.java
```

**Rm8011PowerLevels.java 内容更新**：
```java
package com.leo.remote.reader;

/** RM8011 module power levels. */
public final class Rm8011PowerLevels {
    private Rm8011PowerLevels() {}

    /** Returns fixed integer levels from 0 to 20 dBm in tenths of a dBm. */
    public static int[] levels() {
        int[] result = new int[21];
        for (int i = 0; i <= 20; i++) {
            result[i] = i * 10;
        }
        return result;
    }
}
```

**验收**：
- 编译通过
- 连接 RM610 模块后，设备信息对话框显示 "RM610"
- 连接 RM8011 模块后，设备信息对话框显示 "RM8011"
- 日志中显示正确的模块名称

---

### C.2 RM610 功率分档实现

**需求分析**：
- RM610 模块序列号通过 `readModuleInfo()` 获取，存储在 `ReaderModuleInfo.moduleSerial`
- 判断 `moduleSerial.toLowerCase().contains("cmt")`
  - **包含 "cmt"**：功率值直接使用 0～20 dBm 整数（与 SDK 交互时 × 10）
  - **不包含 "cmt"**：功率档位为固定 8 档索引，设置/获取时传递索引值（0-7），界面显示对应文案

**新增类**：`reader/Rm610PowerLevels.java`

```java
package com.leo.remote.reader;

/**
 * RM610 模块功率档位管理。
 * 根据模块序列号中是否包含 "cmt" 判断功率模式：
 * - CMT 版本：0～20 dBm 整数，SDK 交互时使用 value * 10
 * - 非 CMT 版本：8 档索引（0-7），SDK 交互时使用索引，界面显示对应档位文案
 */
public final class Rm610PowerLevels {
    
    /** 非 CMT 版本的 8 档功率文案（索引 0-7） */
    private static final String[] NON_CMT_LABELS = {
        "-1 dBm", "02 dBm", "05 dBm", "08 dBm",
        "11 dBm", "14 dBm", "17 dBm", "20 dBm"
    };
    
    /**
     * 判断是否为 CMT 版本
     * @param moduleSerial 模块序列号（来自 ReaderModuleInfo.moduleSerial）
     */
    public static boolean isCmtVersion(String moduleSerial) {
        return moduleSerial != null && moduleSerial.toLowerCase().contains("cmt");
    }
    
    /**
     * CMT 版本：返回 0～20 整数数组（21 个档位）
     */
    public static int[] getCmtPowerValues() {
        int[] values = new int[21];
        for (int i = 0; i <= 20; i++) {
            values[i] = i * 10;  // SDK 使用十分之一 dBm
        }
        return values;
    }
    
    /**
     * CMT 版本：格式化功率显示文案
     * @param tenthsDbm SDK 返回的功率值（十分之一 dBm）
     */
    public static String formatCmtPower(int tenthsDbm) {
        return (tenthsDbm / 10) + " dBm";
    }
    
    /**
     * 非 CMT 版本：返回 8 档文案数组
     */
    public static String[] getNonCmtLabels() {
        return NON_CMT_LABELS.clone();
    }
    
    /**
     * 非 CMT 版本：根据索引获取显示文案
     * @param index SDK 返回的索引值（0-7）
     */
    public static String formatNonCmtPower(int index) {
        if (index < 0 || index >= NON_CMT_LABELS.length) {
            return "未知(" + index + ")";
        }
        return NON_CMT_LABELS[index];
    }
    
    /**
     * 非 CMT 版本：从显示文案中解析出索引
     */
    public static int parseNonCmtIndex(String label) {
        for (int i = 0; i < NON_CMT_LABELS.length; i++) {
            if (NON_CMT_LABELS[i].equals(label)) {
                return i;
            }
        }
        return -1;
    }
    
    private Rm610PowerLevels() {} // 工具类，禁止实例化
}
```

**修改 `ReaderConfigFragment.java`**：

在 `applyModuleUi()` 中增加 RM610 判断：

```java
private void applyModuleUi(ModuleSubtype subtype) {
    boolean isRm8011 = (subtype == ModuleSubtype.RM8011);  // 改前：MAGIC_RF
    boolean isRm610 = (subtype == ModuleSubtype.RM610);
    boolean connected = readerState.isConnected();
    
    // 功率区：
    // - R2000/R2000Plus: SeekBar 连续调节（0~30 dBm）
    // - RM8011: 点击数值弹离散选择框（0~20 dBm）
    // - RM610: 根据 CMT 版本决定（CMT 用 SeekBar / 非 CMT 用离散选择）
    if (isRm610) {
        // RM610 初始时隐藏 SeekBar，连接成功后根据序列号判断
        powerSeekBar.setVisibility(View.GONE);
        powerValueView.setClickable(true);
    } else if (isRm8011) {  // 改前：isMagic
        powerSeekBar.setVisibility(View.GONE);
        powerValueView.setClickable(true);
    } else {
        powerSeekBar.setVisibility(View.VISIBLE);
        powerValueView.setClickable(false);
    }
    
    // ... 其余逻辑保持不变
}
```

新增 RM610 功率弹窗方法：

```java
/** RM610 功率离散选择弹窗（非 CMT 版本）*/
private void showRm610PowerDialog() {
    String[] labels = Rm610PowerLevels.getNonCmtLabels();
    int currentIndex = -1;
    
    // 从当前功率值反推索引（如果有的话）
    if (readerConfiguration != null) {
        String currentLabel = Rm610PowerLevels.formatNonCmtPower(
            readerConfiguration.powerTenthsDbm / 10);  // 假设存储的是索引值
        currentIndex = Rm610PowerLevels.parseNonCmtIndex(currentLabel);
    }
    
    new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.config_rm610_power)
            .setSingleChoiceItems(labels, currentIndex, (dialog, which) -> {
                // which 就是索引值，直接传给 SDK
                handleResult(session.setPower(which), R.string.config_power_set_failed);
                dialog.dismiss();
            })
            .setNegativeButton(R.string.common_cancel, null)
            .show();
}
```

在 `onReaderConfigurationChanged()` 中更新功率显示：

```java
@Override
public void onReaderConfigurationChanged(@NonNull ReaderConfiguration config) {
    // ...
    
    // 功率显示
    ModuleSubtype subtype = readerState.getModuleSubtype();
    if (subtype == ModuleSubtype.RM610) {
        String moduleSerial = readerState.getModuleSerial();
        if (Rm610PowerLevels.isCmtVersion(moduleSerial)) {
            // CMT 版本：显示整数 dBm
            powerValueView.setText(Rm610PowerLevels.formatCmtPower(config.powerTenthsDbm));
            powerSeekBar.setVisibility(View.VISIBLE);
            powerSeekBar.setProgress(config.powerTenthsDbm / 10);
        } else {
            // 非 CMT 版本：显示档位文案
            powerValueView.setText(Rm610PowerLevels.formatNonCmtPower(config.powerTenthsDbm / 10));
            powerSeekBar.setVisibility(View.GONE);
        }
    } else if (subtype == ModuleSubtype.RM8011) {  // 改前：MAGIC_RF
        powerValueView.setText(formatPower(config.powerTenthsDbm));
        powerSeekBar.setVisibility(View.GONE);
    } else {
        // R2000/R2000Plus
        powerValueView.setText(formatPower(config.powerTenthsDbm));
        powerSeekBar.setVisibility(View.VISIBLE);
        powerSeekBar.setProgress(config.powerTenthsDbm / 10);
    }
    
    // ...
}
```

---

### C.3 其他模块功率值校准

> 需求原文：「并检查其他模块功率值，按照 `/Users/lei/Downloads/Uhf_Android` 中
> setPower，getPower 方法中的实现来设置和获取，并更新 UI」。
> **本轮真做，不再延后**（旧版方案曾写「不做序列号解析」，与需求冲突，已修正）。

**参考实现出处**（已逐行核对，非推测）：

| 内容 | 文件 |
|---|---|
| RM8011 setPower / getPower / loadPowerSpinner | `rfid/setting/magicrf/PowerQFragment.java` |
| R2000 setPower / getPower | `rfid/setting/r2000/PowerRFragment.java` |
| 5 组档位数组 | `res/values/strings.xml`（`arr_power_20` / `arr_power_26` / `arr_power_0_26` / `arr_power_30` / `arr_run_30`） |
| 分档依据的来源 | `rfid/manager/ConnectManger.java`（`getHardwareVersion()` / `getSoftwareVersion()`） |

**关键结论：本 App 已具备分档所需的全部数据，不需要新增任何 SDK 调用。**
参考工程的两个全局变量与本 App 字段是同一来源：

| 参考工程 | 底层调用 | 本 App 字段 |
|---|---|---|
| `ConstantUtil.SERIAL_VERSION` | `linkage.getSerialNumber()` | `ReaderModuleInfo.moduleSerial` |
| `ConstantUtil.VERSION` | `linkage.getVersion()` | `ReaderModuleInfo.moduleVersion` |

`NativeUhfSdkGateway.readModuleInfo()` 已经在读这两个值，握手步骤 1 完成即可用
（时序见 C.5 的严格初始化顺序）。

#### C.3.1 RM8011 的 5 组功率档位

数组值是**真实 dBm**，下发前 ×10。这与 RM610 非 CMT 的「索引语义」（C.2）
完全不同，**不要混淆两套模型**。

| 判定条件（严格按参考代码的 if / else 顺序） | 档位（dBm） | 档数 |
|---|---|---|
| `moduleSerial` 含 `RM-20dBm` | `13, 14.5, 15.5, 17, 18.5, 20` | 6 |
| 含 `RM-26dBm` 且含 `V1.0` | `15, 16, … 26` | 12 |
| 含 `RM-26dBm`（其他版本） | `0, 1, … 26` | 27 |
| 含 `RM-30dBm` 且 `moduleVersion` 解析 ≥ 3.80 | `10, 14, 17, 19, 21, 23, 24, 25, 26, 27, 28, 29, 30` | 13 |
| 含 `RM-30dBm` 且解析 < 3.80（含解析失败） | `19, 20, … 30` | 12 |
| 含 `30dBm` 且含 `V1.3.1` | `19, 20, … 30` | 12 |
| **都不匹配** | 回退 `0 … 20` + warning 日志 | 21 |

#### C.3.2 三个必须注意的实现细节

1. **半 dBm 值真实存在**（`14.5 / 15.5 / 18.5`）。内部**一律用十分之一 dBm 存**
   （`145 / 155 / 185`），与现有 `ReaderConfiguration.powerTenthsDbm`、
   `setPowerTenthsDbm()` 的单位一致，**接口不用改**。
   显示时**不能用整数除法**：参考工程 `PowerQFragment.getPower()` 写的是
   `rfid_value.value / 10`（int 除法），`14.5 dBm` 会显示成 `14` ——
   **这是参考工程的 bug，不要照搬**。本 App 按 `PowerRFragment` 的 `/ 10.0` 处理，
   整数档位省掉小数点。

2. **固件版本解析很脆弱**。参考代码是
   `VERSION.substring(1, VERSION.length() - 1)` 再 `Double.parseDouble`，
   对空串 / 短串 / 非数字都会抛异常。本 App 必须包 try/catch，
   解析失败时**按 < 3.80 的保守档位处理**（`19 … 30`）——既不崩，也不误开高档位。

3. **兜底不可省**。参考代码若 6 个条件全不匹配，`iu32` 保持 `0` 并直接下发，
   等于把功率设成 0。本 App 改为：无法识别档位时**保留现有 `0 … 20` 21 档行为**，
   并记一条带 `moduleSerial` 的 warning 日志，便于后续补档位表。

#### C.3.3 新增类：`reader/Rm8011PowerLevels.java`

替换现有 `MagicPowerLevels.java`（当前实现无条件返回 0-20，共 21 档）。
类名随 C.1 的 MagicRF → RM8011 重命名一并落地。

```java
/**
 * RM8011 功率档位。档位随模块序列号 / 固件版本变化，
 * 对应参考工程 PowerQFragment.setPower() 与 loadPowerSpinner()。
 * 所有返回值单位为十分之一 dBm，与 ReaderConfiguration.powerTenthsDbm 一致。
 */
public final class Rm8011PowerLevels {

    private static final String TAG = "Rm8011PowerLevels";

    private static final int[] TIER_20     = {130, 145, 155, 170, 185, 200};
    private static final int[] TIER_26_V10 = range(150, 260);   // 15…26
    private static final int[] TIER_26     = range(0, 260);     // 0…26
    private static final int[] TIER_30_OLD = range(190, 300);   // 19…30（固件 < 3.80）
    private static final int[] TIER_30_NEW =
            {100, 140, 170, 190, 210, 230, 240, 250, 260, 270, 280, 290, 300};
    private static final int[] TIER_FALLBACK = range(0, 200);   // 兜底 0…20

    private Rm8011PowerLevels() { }

    /** 按序列号 / 固件版本选择档位；无法识别时回退 0…20。 */
    public static int[] levels(String moduleSerial, String moduleVersion) {
        String serial = moduleSerial == null ? "" : moduleSerial;
        if (serial.contains("RM-20dBm")) {
            return TIER_20.clone();
        }
        if (serial.contains("RM-26dBm")) {
            return serial.contains("V1.0") ? TIER_26_V10.clone() : TIER_26.clone();
        }
        if (serial.contains("RM-30dBm")) {
            return parseVersion(moduleVersion) >= 3.80d
                    ? TIER_30_NEW.clone() : TIER_30_OLD.clone();
        }
        if (serial.contains("30dBm") && serial.contains("V1.3.1")) {
            return TIER_30_OLD.clone();
        }
        Log.w(TAG, "未识别的功率档位，回退 0-20dBm，serial=" + serial);
        return TIER_FALLBACK.clone();
    }

    /**
     * 解析固件版本。参考工程用 substring(1, len - 1) 去掉首尾字符（如 "V3.80\0"），
     * 但未做任何防御。解析失败返回 0，调用方据此走保守档位。
     */
    private static double parseVersion(String version) {
        if (version == null || version.length() < 3) {
            return 0d;
        }
        try {
            return Double.parseDouble(version.substring(1, version.length() - 1).trim());
        } catch (RuntimeException e) {
            Log.w(TAG, "固件版本解析失败: " + version, e);
            return 0d;
        }
    }

    /** 显示文案：整数档不带小数点，半档保留一位小数（14.5 dBm）。 */
    public static String format(int tenthsDbm) {
        return tenthsDbm % 10 == 0
                ? (tenthsDbm / 10) + " dBm"
                : String.format(Locale.US, "%.1f dBm", tenthsDbm / 10.0);
    }

    /** 生成 [fromTenths, toTenths] 闭区间、步长 1 dBm 的档位数组。 */
    private static int[] range(int fromTenths, int toTenths) {
        int count = (toTenths - fromTenths) / 10 + 1;
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = fromTenths + i * 10;
        }
        return result;
    }
}
```

#### C.3.4 R2000 / R2000Plus 不改档位

参考工程 `PowerRFragment` 是**自由输入**（文本框 → `× 10` → `Radio_SetAntennaPower`，
仅校验非负），**没有档位表**。本 App 现有 SeekBar（上限 30 dBm）与之等价，
**保持不变**。唯一需要确认的是读回后的显示走 `/ 10.0` 而非整数除法。

#### C.3.5 UI 改动（`ReaderConfigFragment`）

- RM8011 功率行点击 → 单选对话框，选项由
  `Rm8011PowerLevels.levels(info.moduleSerial, info.moduleVersion)` 生成，
  文案走 `Rm8011PowerLevels.format()`
- 当前值高亮：按 `powerTenthsDbm` 在数组中查找下标，**找不到就不高亮**
  （设备可能返回档位表外的值，不要强行取最近档，避免静默改值）
- 复用 C.2 已有的 `confirmAndApply` 确认流；设置成功后写缓存（C.5.1）
- 档位表所需的 `moduleSerial` / `moduleVersion` 在握手步骤 1 已取到，时序安全

#### C.3.6 验收标准

- RM8011 功率选项**随序列号变化**：`RM-20dBm` 设备出现 6 档且含 `14.5 dBm`
- `14.5 / 15.5 / 18.5` 显示为一位小数，**不被截断**成 `14 / 15 / 18`
- `RM-26dBm` + `V1.0` 出现 12 档（15…26）；其他 26dBm 版本出现 27 档（0…26）
- `RM-30dBm` 按固件 3.80 分界切换两套 30dBm 档位
- 固件版本字符串异常（空串 / 非数字 / 长度 < 3）时不崩溃，走 `19…30` 保守档位
- 未识别序列号时回退 0…20 并有 warning 日志，功率**不会被设成 0**
- R2000 / R2000Plus 保持 SeekBar 上限 30 dBm，无行为回归
- 超出设备实际支持范围的功率值，SDK 返回错误码，UI 通过确认流提示用户

---

### C.4 Session/Q/BLF 参数完善

#### C.4.1 Session 参数说明

**参考开发文档**（PDF §3.5.14 设置 Query 信息）：
- **Session**：ISO 18000-6C 协议的会话概念
  - 取值范围：S0、S1、S2、S3（对应索引 0-3）
  - API 枚举值：
    - `RFID_18K6C_INVENTORY_SESSION_S0`
    - `RFID_18K6C_INVENTORY_SESSION_S1`
    - `RFID_18K6C_INVENTORY_SESSION_S2`
    - `RFID_18K6C_INVENTORY_SESSION_S3`
- **Target**：会话目标
  - 取值范围：A 或 B（对应索引 0-1）
  - API 枚举值：
    - `RFID_18K6C_INVENTORY_SESSION_TARGET_A`
    - `RFID_18K6C_INVENTORY_SESSION_TARGET_B`
- **组合显示**：`combinedIndex = session * 2 + target`
  - S0·A = 0, S0·B = 1
  - S1·A = 2, S1·B = 3
  - S2·A = 4, S2·B = 5
  - S3·A = 6, S3·B = 7

**当前实现检查**：
- `ReaderConfigFragment.showSessionDialog()` 已正确实现 8 档选择
- `session.setSessionTarget(which / 2, which % 2)` 正确解析
- **需确认**：当前对话框选中项是否高亮当前值

**改进**：
```java
private void showSessionDialog() {
    String[] options = new String[8];
    for (int s = 0; s < 4; s++) {
        for (int t = 0; t < 2; t++) {
            int idx = s * 2 + t;
            options[idx] = getString(R.string.config_session_format, s, t == 0 ? "A" : "B");
        }
    }
    
    // 计算当前选中项
    int currentIndex = -1;
    if (readerConfiguration != null) {
        currentIndex = readerConfiguration.session * 2 + readerConfiguration.target;
    }
    
    new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.config_session)
            .setSingleChoiceItems(options, currentIndex, (dialog, which) -> {
                handleResult(
                    session.setSessionTarget(which / 2, which % 2),
                    R.string.config_session_set_failed);
                dialog.dismiss();
            })
            .setNegativeButton(R.string.common_cancel, null)
            .show();
}
```

**新增字符串资源**：
```xml
<string name="config_session_format">S%1$d · %2$s</string>
```

#### C.4.2 Q 参数说明

**参考开发文档**（PDF §3.5.10 固定算法 + §3.5.12 动态算法）：

**Q 值**是 ISO 18000-6C 协议的时隙参数，影响防碰撞算法：
- **动态 Q**（`setSingulationDynamicQParameters`）：SDK 自动调整 Q 值
  - `startQValue`（起始 Q 值）：[in] 起始 Q 值
  - `minQValue`（最小 Q 值）：[in] 最小 Q 值
  - `maxQValue`（最大 Q 值）：[in] 最大 Q 值
  - `thresholdMultiplier`（阈值）：[in] 阈值
  - `retryCount`（重试次数）：[in] 重试次数
  - `toggleTarget`（翻转）：[in] 翻转(1=启用/0 -- 禁用)
  
- **固定 Q**（`setSingulationFixedQParameters`）：用户指定固定 Q 值
  - `qValue`（Q 值）：[in] Q 值
  - `retryCount`（重试次数）：[in] 重试次数
  - `toggleTarget`（翻转）：[in] 翻转(1=启用/0 -- 禁用)
  - `repeatUntilNoTags`（是否重复）：
    - `1` = Continue running inventory rounds until a round is completed without reading any tags.
    - `0` = Run one inventory round only

**当前实现**：
- `ReaderConfigFragment.showQDialog()` 提供 "动态" + 16 档固定 Q 选择
- 子参数全部硬编码在 `NativeUhfSdkGateway.setQ()` 中
- 对话框预选索引硬编码为 `-1`（永不高亮）

**本轮改进**：
1. 修正预选高亮
2. **可选**：暴露子参数（如工作量过大，可降级为仅修正预选）

**简化方案（仅修正预选）**：
```java
private void showQDialog() {
    // ... 构建选项数组 ...
    
    // 计算当前选中项
    int currentIndex = -1;
    if (readerConfiguration != null) {
        if (readerConfiguration.dynamicQ) {
            currentIndex = 0;  // "动态" 选项
        } else {
            currentIndex = readerConfiguration.qValue + 1;  // Q0-Q15
        }
    }
    
    new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.config_q_value)
            .setSingleChoiceItems(options, currentIndex, (dialog, which) -> {
                // ... 处理逻辑 ...
            })
            .setNegativeButton(R.string.common_cancel, null)
            .show();
}
```

**完整方案（暴露子参数）** - 留作后续任务：
- 点击 "动态" 弹二级对话框，输入 startQ/minQ/maxQ/retryCount/thresholdMultiplier
- 点击固定 Q 弹二级对话框，输入 retryCount
- 修改 `ReaderConfiguration` 扩展字段
- 修改 `UhfSdkGateway.setQ()` 签名

**重要：RM8011 模块的 Q 值设置**
- RM8011 使用独立的 `setMagicQuery(session, target, qValue)` API（PDF §3.6.2）
- 与其他模块的 `setQ()` API 完全不同
- 在设置 Q 值时需要判断模块类型：
  ```java
  if (subtype == ModuleSubtype.RM8011) {
      // RM8011 使用 setMagicQuery
      session.setMagicQuery(currentSession, currentTarget, qValue);
  } else {
      // R2000/R2000Plus/RM610 使用 setQ
      session.setQ(dynamic, qValue, minQ, maxQ, ...);
  }
  ```

#### C.4.3 BLF 速率

**参考开发文档**（PDF §3.5.4 设置调制模式）：

BLF（Backscatter Link Frequency）是标签到读写器的反向链路频率，即调制模式（profile）：
- **4 档固定值**：
  - 0 -- DSB_ASK/M0/40khz
  - 1 -- PR_ASK/M2/250khz
  - 2 -- PR_ASK/M2/300khz
  - 3 -- DSB_ASK/M0/400khz

**当前状态**：B 轮方案已修正：
- 4 档：40 kHz / 250 kHz / 300 kHz / 400 kHz
- 文案已补充调制信息：`DSB_ASK/FM0 · 40 kHz` 等
- 预选高亮需检查

**验收**：
- BLF 对话框打开时，当前生效的档位高亮选中
- 档位文案显示完整（含调制/编码信息）

---

### C.5 连接后初始化进度弹窗与参数持久化

**核心需求**：
1. **严格的初始化顺序**：
   - 第一步：读取模块信息（包括模块类型 subtype）
   - 第二步：根据模块类型决定后续参数获取方式
   - 第三步：依次获取各项参数（功率、协议、Session、BLF、Q 值）
   
2. **参数持久化策略**：
   - 所有获取成功的参数保存到本地存储（**使用 MMKV 框架**）
   - 按模块类型分别存储（R2000/RM8011/RM610 各有独立配置）
   - 包括完整的 Q 值参数：
     - 固定 Q：qValue, retryCount, toggleTarget, repeatUntilNoTags
     - 动态 Q：startQValue, minQValue, maxQValue, retryCount, thresholdMultiplier, toggleTarget
   
3. **容错机制**：
   - 参数获取失败时：从本地缓存读取上次成功的值填充 UI
   - 如果本地也没有缓存：使用模块默认值
   - 在配置页增加"刷新参数"按钮，允许用户手动重新获取
   
4. **安全的参数设置**：
   - 修改单个参数（如 Q 值）时，从缓存中取出其他参数值
   - 组合成完整参数后再调用 SDK，避免其他参数被清零

**进度弹窗流程**：连接成功后显示**不可取消**的进度弹窗，依次执行 6 步。
文案必须与需求原文一致（不要自行改写）：

| 步骤 | 弹窗文案 | 失败处理 |
|---|---|---|
| 1 | 正在更新设备参数 | **必须成功**，失败中断连接（拿不到 subtype 就没法按类型读参数） |
| 2 | 正在获取功率 | 记日志 → 用缓存值 → 继续 |
| 3 | 正在获取射频协议 | 不发 SDK 指令，确认步骤 1 设的 6C（见 C.5.3），必然成功 |
| 4 | 正在获取 Session | 记日志 → 用缓存值 → 继续 |
| 5 | 正在获取 Blf 速率 | 记日志 → 用缓存值 → 继续；RM8011 无此档位，直接跳过 |
| 6 | 正在获取 Q 值 | 记日志 → 用缓存值 → 继续 |

- 弹窗 `setCancelable(false)`，并屏蔽返回键，**只能等 6 步走完自动关闭**
- 步骤 2-6 任一失败都不打断流程；无缓存时用模块默认值
- 步骤 1 之所以必须最先且必须成功：需求原文「一定是获取设备信息成功，
  知道当前子设备是什么类型，再去根据类型获取这些参数」

**实现位置**：`ReaderHandshake.java` 或 `ReaderSessionManager.performHandshake()`

**当前流程**：
```java
// ReaderHandshake.perform() - 连接成功后执行
readModuleInfo() 
  → setProtocol(6C)
  → configureDefaultInventory(6C)
  → readConfiguration(subtype)
  → 返回 CONNECTED
```

`readConfiguration()` 已经读取了所有参数，但有三个问题：
1. **没有视觉反馈** —— 整段读取对用户是一次静默等待，给不出分步进度
2. **没有参数持久化** —— 读到的值不落盘，失败时无可回退的值
3. **是原子操作** —— 内部每个 SDK 调用都 `check(...)`，第一个失败就抛异常，
   导致**所有参数一起回退**，与「某一项失败不打断流程」的需求冲突

第 3 点是本轮必须改的结构问题，拆解方案见 **C.5.3**。

#### C.5.1 参数缓存基础设施（MMKV）

**新增类**：`reader/ReaderConfigCache.java`

```java
package com.leo.remote.reader;

import com.tencent.mmkv.MMKV;

/**
 * 读写器配置参数本地缓存。
 * 按模块类型分别存储，支持容错回退。
 * 使用 MMKV 框架进行持久化存储。
 *
 * 注意：MMKV 已在 InitManager.init() 中全局初始化（MMKV.initialize(application)），
 * 此处无需传入 Context，直接使用 mmkvWithID 即可。
 */
public final class ReaderConfigCache {
    private static final String MMKV_ID = "reader_config_cache";
    private final MMKV mmkv = MMKV.mmkvWithID(MMKV_ID);
    
    /**
     * 保存完整配置（按模块类型存储）
     */
    public void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration config) {
        String prefix = subtype.name() + "_";
        mmkv.encode(prefix + "power", config.powerTenthsDbm);
        mmkv.encode(prefix + "inventoryMode", config.inventoryMode);
        mmkv.encode(prefix + "blfProfile", config.blfProfile);
        mmkv.encode(prefix + "session", config.session);
        mmkv.encode(prefix + "target", config.target);
        mmkv.encode(prefix + "dynamicQ", config.dynamicQ);
        mmkv.encode(prefix + "qValue", config.qValue);
        mmkv.encode(prefix + "qMinValue", config.qMinValue);
        mmkv.encode(prefix + "qMaxValue", config.qMaxValue);
        mmkv.encode(prefix + "qRetryCount", config.qRetryCount);
        mmkv.encode(prefix + "qThresholdMultiplier", config.qThresholdMultiplier);
        mmkv.encode(prefix + "qToggleTarget", config.qToggleTarget);
        mmkv.encode(prefix + "qRepeatUntilNoTags", config.qRepeatUntilNoTags);
    }
    
    /**
     * 读取缓存配置（按模块类型）
     * @return 如果没有缓存则返回 null
     */
    public ReaderConfiguration loadConfiguration(ModuleSubtype subtype) {
        String prefix = subtype.name() + "_";
        if (!mmkv.contains(prefix + "power")) {
            return null; // 没有缓存
        }
        return new ReaderConfiguration(
            mmkv.decodeInt(prefix + "power", 200),
            mmkv.decodeInt(prefix + "inventoryMode", 0),
            mmkv.decodeInt(prefix + "blfProfile", 0),
            mmkv.decodeInt(prefix + "session", 0),
            mmkv.decodeInt(prefix + "target", 0),
            mmkv.decodeBool(prefix + "dynamicQ", true),
            mmkv.decodeInt(prefix + "qValue", 7),
            mmkv.decodeInt(prefix + "qMinValue", 0),
            mmkv.decodeInt(prefix + "qMaxValue", 15),
            mmkv.decodeInt(prefix + "qRetryCount", 0),
            mmkv.decodeInt(prefix + "qThresholdMultiplier", 1),
            mmkv.decodeInt(prefix + "qToggleTarget", 1),
            mmkv.decodeInt(prefix + "qRepeatUntilNoTags", 0)
        );
    }
    
    /**
     * 获取模块默认配置（无缓存时的后备方案）
     */
    public static ReaderConfiguration getDefaultConfiguration(ModuleSubtype subtype) {
        // 根据模块类型返回不同的默认值
        if (subtype == ModuleSubtype.RM8011) {
            return new ReaderConfiguration(100, 0, 0, 0, 0, false, 7);
        }
        return new ReaderConfiguration(200, 0, 0, 0, 0, true, 7);
    }
}
```

#### C.5.2 进度回调通道

**改动方案**：

**方案 A**：在 `ReaderConfigFragment` 中监听（B 轮 R10.1 现状，**不采用**）
- `onReaderStateChanged(CONNECTED)` → 显示 `configInitDialog`（"正在初始化设备…"）
- `onReaderConfigurationChanged()` → 600ms 后关闭
- **问题 1**：只有一个阶段提示，不显示具体步骤
- **问题 2**（更致命）：它的触发条件是 `phase == CONNECTED`，而且延迟 1050ms 才显示，
  **握手早就结束了** —— 6 步文案挂上去一步都看不见（详见 C.5.4）

**方案 B**：`ReaderHandshake` 发进度回调 → 经 state 通道 → `ReaderConnectionDialog` 显示（**本轮采用**）
- 握手期间屏幕上本来就是 `ReaderConnectionDialog`（`isConnectingPhase` 已含 `VERIFYING_MODULE`）
- 不新增 observer 回调、不新建弹窗，改动面最小（宿主细节见 C.5.4）

修改 `ReaderHandshake.perform()` 支持容错和进度回调：

```java
/**
 * 握手：先拿设备信息（决定 subtype），再逐项容错读参数。
 *
 * 注意 progressCallback 传的是 string 资源 id，不是中文字面量 ——
 * ReaderHandshake 是无 Context 的工具类，文案由 Fragment 侧 getString() 解析。
 */
static Result perform(
        UhfSdkGateway gateway,
        ReaderConfigCache cache,
        IntConsumer progressCallback) throws ReaderException {

    // ===== 步骤 1：设备信息（必须成功，失败中断连接）=====
    progressCallback.accept(R.string.handshake_updating_params);
    ReaderModuleInfo info = gateway.readModuleInfo();
    if (info.subtype == ModuleSubtype.UNKNOWN) {
        throw new ReaderException("Unknown RM70XX subtype: " + info.rawSubtype, info.rawSubtype);
    }
    if (isBlank(info.boardSerial) || isBlank(info.boardVersion)
            || isBlank(info.moduleSerial) || isBlank(info.moduleVersion)) {
        throw new ReaderException("RM70XX device information is incomplete", -7);
    }

    int status = gateway.setProtocol(TagProtocol.ISO_18000_6C);
    if (status != 0) {
        throw new ReaderException("Unable to select 6C protocol", status);
    }

    // ⚠️ D.3.6 会把 configureDefaultInventory(protocol) 改签名为
    //    applyInventoryParams(protocol, area, addr, wordLen)。
    //    C、D 两轮一起交付时，这里直接用新签名 + 缓存值：
    ReaderConfiguration cachedArea = cache.loadConfiguration(info.subtype);
    if (cachedArea == null) {
        cachedArea = ReaderConfigCache.getDefaultConfiguration(info.subtype);
    }
    status = gateway.applyInventoryParams(TagProtocol.ISO_18000_6C,
            cachedArea.inventoryArea, cachedArea.inventoryAddress,
            cachedArea.inventoryWordLen);
    if (status != 0) {
        throw new ReaderException("Unable to configure inventory", status);
    }

    // ===== 步骤 2-6：逐项读参数，任一失败不中断 =====
    ReaderConfiguration config = readConfigurationStepwise(
            gateway, info.subtype, cache, progressCallback);

    cache.saveConfiguration(info.subtype, config);
    return new Result(info, config);
}
```

#### C.5.3 逐项容错读参数（拆解原子的 readConfiguration）

**为什么必须拆**：现在 `NativeUhfSdkGateway.readConfiguration()`（第 112-154 行）里每个
SDK 调用都套着 `check(...)`，任意一个失败就抛 `ReaderException`。结果是
**第一个失败的参数会让后面所有参数都读不到**，整份配置一起回退到缓存 ——
这与需求原文「当中某一个参数获取失败，则不打断流程，继续下一个」直接冲突，
也没法给出分步进度。所以本轮把它拆成逐项读取、逐项回退。

**新增方法**：`ReaderHandshake.readConfigurationStepwise()`

```java
/**
 * 逐项读取参数：每项独立 try/catch，失败只回退该项，不影响其他项。
 * 每项开始前发一次进度（string 资源 id）。
 */
private static ReaderConfiguration readConfigurationStepwise(
        UhfSdkGateway gateway,
        ModuleSubtype subtype,
        ReaderConfigCache cache,
        IntConsumer progress) {

    // 回退基准：缓存 → 模块默认值。逐项失败时从这里取值。
    ReaderConfiguration fb = cache.loadConfiguration(subtype);
    if (fb == null) {
        fb = ReaderConfigCache.getDefaultConfiguration(subtype);
    }

    // ===== 步骤 2：功率 =====
    progress.accept(R.string.handshake_reading_power);
    int power = fb.powerTenthsDbm;
    try {
        Integer v = gateway.getPowerTenthsDbm();
        if (v != null) { power = v; }
    } catch (Exception e) {
        Log.w(TAG, "读取功率失败，使用缓存值 " + power, e);
    }

    // ===== 步骤 3：射频协议 =====
    // ⚠️ 底层没有「读当前协议」的接口（详见下方说明），这一步不发 SDK 指令，
    //    只是把步骤 1 已设好的 6C 确认下来并刷新 UI，必然成功。
    progress.accept(R.string.handshake_reading_protocol);

    // ===== 步骤 4：Session / Target =====
    progress.accept(R.string.handshake_reading_session);
    int session = fb.session;
    int target = fb.target;
    try {
        int[] group = gateway.getQueryGroup(subtype);
        if (group != null) { session = group[0]; target = group[1]; }
    } catch (Exception e) {
        Log.w(TAG, "读取 Session 失败，使用缓存值 S" + session, e);
    }

    // ===== 步骤 5：BLF 速率 =====
    progress.accept(R.string.handshake_reading_blf);
    int blf = fb.blfProfile;
    if (subtype != ModuleSubtype.RM8011) {   // RM8011 无 BLF 档位，跳过
        try {
            Integer v = gateway.getBlfProfile();
            if (v != null) { blf = v; }
        } catch (Exception e) {
            Log.w(TAG, "读取 BLF 失败，使用缓存值 " + blf, e);
        }
    }

    // ===== 步骤 6：Q 值及其子参数 =====
    progress.accept(R.string.handshake_reading_q);
    boolean dynamicQ = fb.dynamicQ;
    int q = fb.qValue, minQ = fb.qMinValue, maxQ = fb.qMaxValue;
    int retry = fb.qRetryCount, threshold = fb.qThresholdMultiplier;
    int toggle = fb.qToggleTarget, repeat = fb.qRepeatUntilNoTags;
    try {
        ReaderQParams qp = gateway.getQParams(subtype);
        if (qp != null) {
            dynamicQ = qp.dynamic;  q = qp.qValue;
            minQ = qp.minQ;         maxQ = qp.maxQ;
            retry = qp.retryCount;  threshold = qp.thresholdMultiplier;
            toggle = qp.toggleTarget; repeat = qp.repeatUntilNoTags;
        }
    } catch (Exception e) {
        Log.w(TAG, "读取 Q 值失败，使用缓存值 Q" + q, e);
    }

    return new ReaderConfiguration(power, 1, blf, session, target,
            dynamicQ, q, minQ, maxQ, retry, threshold, toggle, repeat,
            // D.3.1 新增的三个盘点区域字段，握手阶段沿用刚下发的缓存值
            fb.inventoryArea, fb.inventoryAddress, fb.inventoryWordLen);
}
```

**关于步骤 3「正在获取射频协议」**：已确认底层**没有**读当前协议的接口 ——
`Linkage` 只有 `setTagType(int)`，`Radio_GetMultiProtocolParams(int tagType, ...)`
需要先知道 tagType 才能查，反查不出「当前是哪个协议」。
所以这一步不要去找 getter，就是确认步骤 1 设定的 6C 并更新 UI / 缓存。

**新增 Gateway 接口**（`UhfSdkGateway.java`，失败返回 `null` 而不是抛异常）：

```java
Integer getPowerTenthsDbm();              // 失败返回 null
Integer getBlfProfile();                  // 失败返回 null；RM8011 不适用
int[] getQueryGroup(ModuleSubtype subtype);   // {session, target}，失败返回 null
ReaderQParams getQParams(ModuleSubtype subtype); // 失败返回 null
```

`NativeUhfSdkGateway` 实现要点（**逻辑全部从现有 `readConfiguration()` 搬过来，不要重写**）：

```java
@Override
public Integer getPowerTenthsDbm() {
    Rfid_Value power = new Rfid_Value();
    return linkage.Radio_GetAntennaPower(power) == STATUS_OK ? power.value : null;
}

@Override
public int[] getQueryGroup(ModuleSubtype subtype) {
    if (subtype == ModuleSubtype.RM8011) {          // 原 MAGIC_RF 分支
        Parameters params = new Parameters();
        if (linkage.get_Query(params) != STATUS_OK) { return null; }
        return new int[]{params.getSession(), params.getTarget()};
    }
    TagGroup group = new TagGroup();
    if (linkage.Radio_GetQueryTagGroup(group) != STATUS_OK) { return null; }
    return new int[]{group.session, group.target};
}

@Override
public ReaderQParams getQParams(ModuleSubtype subtype) {
    if (subtype == ModuleSubtype.RM8011) {
        Parameters params = new Parameters();
        if (linkage.get_Query(params) != STATUS_OK) { return null; }
        return ReaderQParams.fixed(params.getQ(), 0, 1, 0);
    }
    Rfid_Value algorithm = new Rfid_Value();
    if (linkage.Radio_getCurrentSingulationAlgorithm(algorithm) != STATUS_OK) { return null; }
    if (algorithm.value == 1) {
        DynamicQParams d = new DynamicQParams();
        if (linkage.Radio_GetSingulationAlgorithmDyParameters(d) != STATUS_OK) { return null; }
        return ReaderQParams.dynamic(d.startQValue, d.minQValue, d.maxQValue,
                d.retryCount, d.thresholdMultiplier, d.toggleTarget);
    }
    FixedQParams f = new FixedQParams();
    if (linkage.Radio_GetSingulationAlgorithmFixedParameters(f) != STATUS_OK) { return null; }
    return ReaderQParams.fixed(f.qValue, f.retryCount, f.toggleTarget, f.repeatUntiNoTags);
}
```

> 注意 `RM8011` 下 `getQueryGroup` 与 `getQParams` 各调一次 `get_Query`，
> 看着重复，但换来的是两步能独立失败、独立回退，符合需求。

**新增类**：`reader/ReaderQParams.java` —— 只是个搬运用的容器，
避免 `getQParams` 返回 8 元素 int[] 那种可读性极差的写法。

```java
public final class ReaderQParams {
    public final boolean dynamic;
    public final int qValue, minQ, maxQ, retryCount, thresholdMultiplier;
    public final int toggleTarget, repeatUntilNoTags;
    // 全参构造 + 两个静态工厂：
    // static ReaderQParams fixed(int q, int retryCount, int toggleTarget, int repeatUntilNoTags)
    //     → dynamic=false, minQ=0, maxQ=15, thresholdMultiplier=1
    // static ReaderQParams dynamic(int startQ, int minQ, int maxQ,
    //         int retryCount, int thresholdMultiplier, int toggleTarget)
    //     → dynamic=true, repeatUntilNoTags=0
}
```

**`readConfiguration()` 的处置**：保留接口不动（`ReaderSessionManager` 的
手动刷新按钮 C.5.2 还在用），但**内部改为调用上面几个 getter 拼装**，
避免同一份读取逻辑维护两遍。手动刷新同样走逐项容错。

**调用点改动**（`ReaderSessionManager.performHandshake()`，现第 571 行）：

```java
// 原：ReaderHandshake.Result result = ReaderHandshake.perform(gateway);
ReaderHandshake.Result result = ReaderHandshake.perform(gateway, configCache, resId ->
        publish(state.buildUpon()
                .phase(ConnectionPhase.VERIFYING_MODULE)
                .message(application.getString(resId))
                .build()));
```

进度文案走**现有的 `publish` + state message 通道**，不新增 listener 回调：
`ReaderSessionManager` 已持有 `application`（第 34 行），负责把资源 id 解析成中文；
`ReaderConfigFragment` 在 `onReaderStateChanged` 里看到 `VERIFYING_MODULE`
就更新弹窗文案，看到 `CONNECTED` 就关闭弹窗。

**需要的 import**：`ReaderHandshake` 加 `java.util.function.IntConsumer`、
`android.util.Log`、`com.leo.remote.R`。

---


#### C.5.4 进度弹窗宿主：改 `ReaderConnectionDialog`，不新建弹窗

**先看清现状**（这几点决定了改法，别按想象写）：

1. `ReaderConfigFragment.isConnectingPhase()`（第 837-842 行）**已包含
   `VERIFYING_MODULE`**，握手期间屏幕上是 `ReaderConnectionDialog`，
   不是 `configInitDialog`。
2. `configInitDialog`（B 轮的"正在初始化设备…"）由 `beginConfigInitialization()`
   触发，条件是 `phase == CONNECTED`（第 311-313 行），而且 `postDelayed(..., 1050)`。
   **它在握手结束之后才出现** —— 6 步进度全部发生在它出现之前，
   所以**绝不能**把分步文案挂在它上面，否则一步都看不到。
3. `ReaderConnectionDialog.update(phase, detail, errorCode)`（第 81 行）
   **收下了 `detail` 却不用**：`VERIFYING_MODULE` 分支把文案写死成
   `R.string.reader_verifying_detail`（"正在验证 RM70XX 设备"），
   把传进来的 `detail` 丢掉了。这是本轮要改的点。
4. 弹窗本身 `setCancelable(false)`（第 36 行）—— **不可取消这条需求已经满足**，
   返回键和点外部都关不掉，不用额外处理。
5. 但 `cancelButton` 在非终态时是 `VISIBLE`（第 101-102 行），
   握手期间用户仍可点"取消连接"。需求要求「不允许打断」，
   所以 `VERIFYING_MODULE` 期间要把它隐藏。

**改动 1**：`ReaderConnectionDialog.update()` 让 `detail` 在握手期生效

```java
String message = getString(switch (phase) {
    case CONNECTING -> R.string.reader_connecting_detail;
    // ...其余不变...
    case VERIFYING_MODULE -> R.string.reader_verifying_detail;
    // ...
});
// 新增：握手期间若上层给了分步文案，用它覆盖默认文案
if (phase == ConnectionPhase.VERIFYING_MODULE && !currentDetail.isEmpty()) {
    message = currentDetail;
}
if (failure && currentErrorCode != 0) {
    message = message + (message.isEmpty() ? "" : "\n") + "错误码：" + currentErrorCode;
}
detailView.setText(message);
```

**改动 2**：握手期间隐藏取消按钮（需求：不允许打断）

```java
// 原：cancelButton.setVisibility(!terminal && phase != ConnectionPhase.DISCONNECTING
//         ? View.VISIBLE : View.GONE);
cancelButton.setVisibility(!terminal
        && phase != ConnectionPhase.DISCONNECTING
        && phase != ConnectionPhase.VERIFYING_MODULE   // 新增：6 步参数读取期间不可打断
        ? View.VISIBLE : View.GONE);
```

**改动 3**：`ReaderConfigFragment` 不需要新增任何回调

`onReaderStateChanged` 里现有的这段（第 301-303 行）已经把分步文案送进去了，
**一行都不用改**：

```java
if (isConnectingPhase(state.getPhase())) {       // 已含 VERIFYING_MODULE
    connectionFailureDialogDismissed = false;
    showOrUpdateConnectionDialog(state);         // 内部 dialog.update(phase, state.getMessage(), ...)
}
```

因为 C.5.3 的进度回调是通过 `publish(state.buildUpon().phase(VERIFYING_MODULE)
.message(...))` 走的，`state.getMessage()` 就是当前步骤文案。

> ❌ **不要新增 `ReaderObserver.onHandshakeProgress(String)`**，
> 也不要 `new WaitDialog(context)` —— `WaitDialog` 是 Builder 模式
> （`new WaitDialog.Builder(activity).setMessage(...)`），没有那个构造器。
> 走现有 state 通道，改动面最小。

**`configInitDialog` 的处置**：保留现状即可（连接成功后的 600ms 收尾提示），
它和 6 步进度不冲突 —— 前者在 `CONNECTED` 之后，后者在 `VERIFYING_MODULE` 期间。
如果觉得两段提示重复，可把 `beginConfigInitialization()` 删掉，
但**不是必须**，且删除会影响 B 轮已验收的行为，建议本轮不动。

---

**手动刷新参数按钮**（需求：参数获取失败时用户可手动重新获取）：

```java
// ReaderConfigFragment 新增：刷新按钮点击
private void onRefreshParametersClick() {
    if (!requireReaderOnline()) { return; }        // 复用现有在线校验
    WaitDialog.Builder dialog = new WaitDialog.Builder(requireActivity())
            .setMessage(R.string.config_refreshing);   // Builder 构造器已 setCancelable(false)
    dialog.show();
    session.refreshConfiguration().whenComplete((config, error) -> {
        if (!isAdded()) { return; }
        requireActivity().runOnUiThread(() -> {
            if (dialog.isShowing()) { dialog.dismiss(); }
            if (error != null) {
                toast(getString(R.string.config_refresh_failed, error.getMessage()));
            } else {
                toast(R.string.config_refresh_success);
            }
            // UI 由 onReaderConfigurationChanged 回调统一刷新，这里不手动 setText
        });
    });
}
```

**`ReaderSessionManager` 新增**（复用 C.5.3 的逐项容错读取）：

```java
/** 手动刷新：逐项重读参数，成功项落盘，失败项保留缓存值。 */
public CompletableFuture<ReaderConfiguration> refreshConfiguration() {
    return submitConnected(() -> {
        ModuleSubtype subtype = state.getModuleSubtype();
        ReaderConfiguration fresh = ReaderHandshake.readConfigurationStepwise(
                gateway, subtype, configCache, resId -> { /* 刷新时不显示分步文案 */ });
        configCache.saveConfiguration(subtype, fresh);
        configuration = fresh;
        notifyConfiguration();
        return fresh;
    });
}
```

> `readConfigurationStepwise` 需从 `private` 放宽为**包内可见**（`static`，无修饰符），
> 供 `ReaderSessionManager` 复用；两者同在 `com.leo.remote.reader` 包内。
> 刷新走同一份逐项容错逻辑，避免两处维护。

文案统一用字符串资源（见 C.10），**不要硬编码中文**，
也不要用 `Toast.makeText` —— 项目已有 `toast(...)` 封装。

**布局文件增加刷新按钮**（`fragment_reader_config.xml`）：

```xml
<!-- 在配置页顶部或工具栏增加刷新按钮 -->
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_refresh_parameters"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/config_refresh_parameters"
    app:icon="@drawable/ic_refresh"
    style="@style/Widget.Material3.Button.TextButton.Icon" />
```

> ⚠️ **不要降级为单阶段提示**：6 步分步文案是 C 轮需求原文
> （"正在更新设备参数，正在获取功率，正在获取射频协议，正在获取Session，
> 正在获取Blf速率，正在获取Q值"），B 轮 R10.1 的单一"正在初始化设备…"
> 不满足要求。若担心改动风险，见 C.13 风险 3 的缓解方式（保留 6 步文案，
> 只把进度通道做薄），而不是砍掉分步。

---

### C.6 设计文件查漏补缺

**设计文件路径**：`/Users/lei/Desktop/设计文件.sketch`（本轮不使用）

**处置**：⏭️ **本轮跳过，不列入交付范围**

- 2026-08-03 用户明确：暂不提供 PNG / PDF 导出，跳过该项
- Sketch 是二进制打包格式，无法在当前环境解析，非工具缺失问题
- **codex 不需要就此项等待或提问**，按 C.1-C.5 与 D.1-D.8 实施即可
- 后续如需对照设计稿，导出关键页面 PNG 后单独开一轮（不影响本轮已定方案）

---

### C.7 文件变更清单

#### 新建文件（3 个）
| 文件 | 说明 |
|---|---|
| `reader/Rm610PowerLevels.java` | RM610 功率档位管理（CMT 版本判断 + 非 CMT 8 档） |
| `reader/ReaderConfigCache.java` | 读写器配置参数本地缓存（MMKV，按模块类型分别存储，支持容错回退） |
| `reader/ReaderQParams.java` | Q 值及子参数搬运容器（C.5.3，避免 getQParams 返回 8 元素 int[]） |

#### 重命名文件（1 个）
| 旧文件名 | 新文件名 | 说明 |
|---|---|---|
| `reader/MagicPowerLevels.java` | `reader/Rm8011PowerLevels.java` | MagicRF → RM8011 重命名，**并重写内容**（C.3：现有实现无条件返回 0-20，改为按序列号 / 固件分 5 组档位） |

#### 修改文件（Java，9 个）
| 文件 | 改动摘要 |
|---|---|
| `reader/ModuleSubtype.java` | RM100X → RM610 重命名；MAGIC_RF → RM8011 重命名（enum 名、getDisplayName、isR2000Style、supportedProtocols） |
| `reader/Rm8011PowerLevels.java` | ⚠️ **不只是改名**：`levels()` 改为接收 `(moduleSerial, moduleVersion)` 并返回 5 组档位之一；新增 `format()` 显示格式化、`parseVersion()` 容错解析、未识别序列号的 0-20 兜底（C.3.3） |
| `reader/UhfSdkGateway.java` | 新增 4 个逐项 getter：`getPowerTenthsDbm()` / `getBlfProfile()` / `getQueryGroup(subtype)` / `getQParams(subtype)`，失败返回 `null`（C.5.3） |
| `reader/NativeUhfSdkGateway.java` | ⚠️ **需要改**（原表写"无需修改"是错的）：实现上述 4 个 getter（逻辑从现有 `readConfiguration()` 第 112-154 行搬出）；`readConfiguration()` 内部改为调这几个 getter 拼装；`MAGIC_RF` → `RM8011`（第 115 行确有该常量引用） |
| `reader/ReaderHandshake.java` | `perform()` 改签名（+`ReaderConfigCache`、+`IntConsumer` 进度）；新增 `readConfigurationStepwise()` 逐项容错读取；参数落盘 |
| `reader/ReaderSessionManager.java` | MAGIC_RF → RM8011 全局替换；注入 `ReaderConfigCache`；`performHandshake()`（第 571 行）改调用并把进度经 `publish` 送出；新增 `refreshConfiguration()` |
| `ui/dialog/ReaderConnectionDialog.java` | `update()` 在 `VERIFYING_MODULE` 时用传入的 `detail` 覆盖写死文案；该阶段隐藏 `cancelButton`（不允许打断）——见 C.5.4 |
| `reader/ReaderConfiguration.java` | **可选**：增加 `withUpdatedQValue()` 等 builder 方法，方便单参数更新 |
| `ui/fragment/home/ReaderConfigFragment.java` | RM610 功率 UI 分支（C.2）+ **RM8011 功率单选框改为按档位表生成、文案走 `format()`**（C.3.5）+ Session/Q 预选修正 + MAGIC_RF → RM8011 替换 + 参数刷新按钮 + 缓存回退逻辑（**握手进度不用改这里**，现有 `isConnectingPhase` 已含 `VERIFYING_MODULE`） |

> ❌ `reader/ReaderObserver.java` **不改** —— 原表列的
> `onHandshakeProgress(String)` default 方法已废弃，进度走现有 state 通道（C.5.4）。

#### 修改文件（资源，2 个）
| 文件 | 改动摘要 |
|---|---|
| `res/values/strings.xml` | 新增 `config_rm610_power`、`config_rm8011_power`、`config_session_format`、`config_refresh_parameters`、握手进度文案 |
| `res/layout/fragment_reader_config.xml` | 新增刷新参数按钮 |

**不新增三方依赖**。

---

### C.8 实施顺序

1. **C.1 模块重命名**（最简单，先做）
   - C.1.1: 全局搜索替换 `RM100X` → `RM610`
   - C.1.2: 全局搜索替换 `MAGIC_RF` → `RM8011`，`MagicRF` → `RM8011`，`MagicRf` → `Rm8011`
   - C.1.3: 重命名文件 `MagicPowerLevels.java` → `Rm8011PowerLevels.java`
   - C.1.4: 更新 `Rm8011PowerLevels.java` 类内注释（内容重写留到第 7 步 C.3）
   - 编译验证

2. **C.5.1 参数缓存基础设施**（为后续功能做准备）
   - 新建 `ReaderConfigCache.java`（MMKV）+ `ReaderQParams.java`
   - 在 `ReaderSessionManager` 中注入缓存实例
   - **C.5.3 拆原子读取**：`UhfSdkGateway` 加 4 个 getter，
     `NativeUhfSdkGateway` 从现有 `readConfiguration()` 搬逻辑，
     `ReaderHandshake` 新增 `readConfigurationStepwise()` 逐项容错
   - 单元测试验证缓存读写

3. **C.2 RM610 功率分档**
   - 新建 `Rm610PowerLevels.java`
   - 修改 `ReaderConfigFragment` 功率 UI 逻辑
   - 真机验证（需要 CMT 版本和非 CMT 版本两台设备）

4. **C.4.1 Session 预选修正**（独立，快速）

5. **C.4.2 Q 预选修正**（独立，快速）
   - **注意**：需要区分 RM8011 和其他模块的 Q 值设置方式
   - **重要**：设置 Q 值时从缓存读取其他参数，组合后完整设置

6. **C.5.2 / C.5.4 连接后初始化进度弹窗**
   - `ReaderHandshake.perform()` 加 `IntConsumer` 进度回调（传 string 资源 id）
   - `ReaderSessionManager.performHandshake()` 把进度经 `publish` 送出
   - `ReaderConnectionDialog.update()` 让 `VERIFYING_MODULE` 用传入 detail，
     并隐藏该阶段的取消按钮（**宿主是连接弹窗，不是 configInitDialog**）
   - 增加手动刷新参数按钮（`refreshConfiguration()` 复用逐项容错）

7. **C.3 其他模块功率校准**（本轮真做，见 C.3）
   - 重写 `Rm8011PowerLevels.levels(moduleSerial, moduleVersion)`：5 组档位 + 0-20 兜底
   - 新增 `format()`（半 dBm 保留一位小数）与 `parseVersion()`（try/catch，失败走保守档）
   - `ReaderConfigFragment` RM8011 功率单选框改为按档位表生成
   - R2000 / R2000Plus 不动，仅确认读回显示走 `/ 10.0`
   - 真机验证需要不同序列号的 RM8011（缺设备时按 C.11 第 8 项的降级方式验证）

8. ~~**C.6 设计文件查漏补缺**~~ → **已跳过**，不占工时

---

### C.9 验收标准

**模块重命名（C.1）**：
- 编译通过，无 `RM100X`、`MAGIC_RF`、`MagicRF` 残留引用
- 连接 RM610 设备后，设备信息对话框显示 "RM610"
- 连接 RM8011 设备后，设备信息对话框显示 "RM8011"
- 日志中显示正确的模块名称

**参数缓存基础设施（C.5.1）**：
- 首次连接设备，参数获取成功后保存到 MMKV
- 断开重连，参数获取失败时能从缓存读取上次的值
- 不同模块类型的参数独立存储，互不干扰
- 无缓存且获取失败时，能正确使用模块默认值

**RM610 功率（C.2）**：
- CMT 版本：
  - 功率行显示 SeekBar，范围 0～20 dBm
  - 滑动后确认弹窗，设置成功显示对应整数 dBm
  - 设置的功率值被缓存
- 非 CMT 版本：
  - 功率行显示数值，点击弹 8 档选择框
  - 选择后确认弹窗，设置成功显示对应档位文案（如 "05 dBm"）
  - 当前档位高亮选中
  - 设置的功率值被缓存

**Session/Q 预选（C.4）**：
- Session 对话框打开时，当前生效的 Session+Target 组合高亮
- Q 对话框打开时，当前 Q 值（动态或固定 Q0-Q15）高亮
- **RM8011 模块**：Q 值设置使用 `setMagicQuery` API，不支持动态 Q
- **重要**：修改 Q 值时，从缓存读取其他 Q 参数（retryCount/toggleTarget 等），组合完整后再设置
- 验证方法：连接设备 → 修改 Q 值 → 使用抓包工具或日志确认传给 SDK 的完整参数

**连接后初始化（C.5.2 / C.5.3 / C.5.4）**：
- 握手期间（`VERIFYING_MODULE`）连接弹窗依次显示 6 步文案，**逐字核对**：
  正在更新设备参数 → 正在获取功率 → 正在获取射频协议 →
  正在获取Session → 正在获取Blf速率 → 正在获取Q值
- 弹窗不可取消：返回键 / 点外部无效，且该阶段**看不到"取消连接"按钮**
- 全部走完自动关闭，配置值更新到界面
- **逐项容错**：单独让某一项失败（如临时改错 SDK 调用），
  验证后续步骤照常执行、该项回退到缓存值，**而不是整份配置一起回退**
- 配置页有"刷新参数"按钮，点击后能重新获取所有参数

**手动刷新参数**：
- 点击刷新按钮，弹出 "正在刷新参数…" 提示（`WaitDialog.Builder`，已默认不可取消）
- 刷新成功：提示 "参数刷新成功"，UI 自动更新（经 `onReaderConfigurationChanged`）
- 刷新失败：提示具体错误信息，UI 保持缓存值不变
- 文案全部走字符串资源，提示走项目现有 `toast(...)` 封装，无硬编码中文

**其他模块功率（C.3）**：
- RM8011 功率选项**随序列号变化**（不再固定 21 档）：
  - `RM-20dBm` → 6 档，且 `14.5 / 15.5 / 18.5` 显示为一位小数，**未被截断**
  - `RM-26dBm` + `V1.0` → 12 档（15…26）；其他 26dBm 版本 → 27 档（0…26）
  - `RM-30dBm` → 按固件 3.80 分界切换 `19…30`（12 档）/ `10,14,17,…,30`（13 档）
- 固件版本串异常（空 / 非数字 / 长度 < 3）不崩溃，走 `19…30` 保守档位
- 未识别序列号回退 0…20 并有 warning 日志，**功率不会被设成 0**
- R2000/R2000Plus：SeekBar 最大 30 dBm 保持不变，读回显示走 `/ 10.0`
- 超范围功率设置失败时，弹窗提示错误码

**设计文件（C.6）**：
- ⏭️ 已跳过，无验收项

---

### C.10 新增字符串资源

```xml
<!-- C.1 模块重命名 -->
<!-- 无需新增，仅更新现有引用 -->

<!-- C.2 RM610 功率 -->
<string name="config_rm610_power">RM610 功率</string>
<string name="config_rm610_cmt_power">RM610 功率（CMT 版本）</string>
<string name="config_rm610_non_cmt_power">RM610 功率（标准版本）</string>

<!-- C.2 RM8011 功率（如果需要更新文案） -->
<string name="config_rm8011_power">RM8011 功率</string>

<!-- C.4 Session 格式化 -->
<string name="config_session_format">S%1$d · %2$s</string>

<!-- C.5 参数刷新 -->
<string name="config_refresh_parameters">刷新参数</string>
<string name="config_refresh_success">参数刷新成功</string>
<string name="config_refresh_failed">参数刷新失败: %s</string>
<string name="config_device_not_connected">设备未连接</string>
<string name="config_refreshing">正在刷新参数&#8230;</string>

<!-- C.5 握手进度：6 步文案，与需求原文逐字一致，勿改写 -->
<string name="handshake_updating_params">正在更新设备参数</string>
<string name="handshake_reading_power">正在获取功率</string>
<string name="handshake_reading_protocol">正在获取射频协议</string>
<string name="handshake_reading_session">正在获取Session</string>
<string name="handshake_reading_blf">正在获取Blf速率</string>
<string name="handshake_reading_q">正在获取Q值</string>
<string name="handshake_using_cached_config">读取失败，使用缓存值</string>
```

---

## D. 盘点页 UI 优化（2026-08-03）

### D.0 核心需求

1. **RSSI 列动态显示**：
   - 仅 R2000 和 R2000Plus 模块显示 RSSI 列
   - RM610 和 RM8011 模块隐藏 RSSI 列
2. **标签信息列重新布局**：
   - EPC/USER/TID 标准长度为 24 位（格式：`2021 0110 1323 1122 1000 1119`）
   - 当前列表显示不全，需重新设计布局
   - 处理不同显示场景：仅 EPC、EPC+USER、EPC+TID
3. **盘点区域设置**（原文中的"盘点区域"，统一改称"盘点区域"）：
   - 在配置页工作模式上方添加盘点区域设置项
   - 默认只盘 EPC
   - **取值以《UHF Library开发文档》§3.3.8 setInventoryArea / §3.3.9 getInventoryArea 为准**，
     且**随当前射频协议切换选项集**：

     | 协议 | 0 | 1 | 2 | 3 |
     |---|---|---|---|---|
     | 6C（ISO 18000-6C） | 仅盘点 EPC | 盘点 EPC 和 USER | 盘点 EPC 和 TID | 盘点 EPC 和 RESERVED |
     | 6B（ISO 18000-6B） | 仅盘点 UID | 盘点 UID 和 USER | — | — |
     | GB/T 29768、GJB 7377.1 | 仅盘点编码区 | 盘点编码区和用户区 | 盘点编码区和标签信息区 | — |

   - 文档同时定义了另两个入参，本轮一并实现（见 D.3.2 / D.3.5）：
     - `startAddr`：起始地址（字为单位），对"仅盘点"档不起作用
     - `wordLen`：盘点长度（字为单位）。`0` 表示返回整个区域
   - ⚠️ 文档对 `wordLen` 的两条硬性约束必须在代码里落实：
     - `wordLen = 0`（读全区）**仅 R2000 新版 firmware 支持**，且 USER 域 buffer 上限
       64 字节 —— 标签 USER 域超过 32 字节时禁止使用，否则内存溢出
     - `wordLen` 必须与标签实际长度匹配（标签 USER 只有 2 word 却读 4 word 会报错）
     - 结论：UI 默认给 `startAddr = 0`、`wordLen = 6`（沿用现有
       `configureDefaultInventory` 的默认值），**不默认下发 0**
   - ⚠️ **与 jar 内注释的冲突（已知，按文档实现）**：
     `app/libs/uhf.jar` 里 `com.uhf.structures.InventoryParams` 的注释（2017/8/18）写的是
     `1 -- INVENTORY_MODE_EPC_TID`、`2 -- INVENTORY_MODE_EPC_USER`，且没有 RESERVED 档，
     与文档的 `1=EPC+USER`、`2=EPC+TID`、`3=EPC+RESERVED` **顺序相反**。
     `InventoryParams` 只是把 int 透传给 native 层，真正解释这个值的是 so 库，
     因此**以文档为准**；jar 注释视为过期文档。
     → 但必须在联调阶段用真机核对一次：设 `area=1` 盘一张已知 TID / USER 的标签，
       确认返回的是 USER 而不是 TID。若实测与 jar 注释一致，只需调换
       `InventoryArea` 枚举里两个常量的 value，UI 与其余逻辑无需改动（见 D.3.6）。
4. **列标题动态更新**：
   - 根据盘点区域更新列表标题（6C 协议下）
   - 仅 EPC（area=0）→ 显示 "EPC 号"
   - EPC+USER（area=1）→ 显示 "EPC/USER"
   - EPC+TID（area=2）→ 显示 "EPC/TID"
   - EPC+RESERVED（area=3）→ 显示 "EPC/RESERVED"
5. **芯片型号识别**：
   - 底层库已在 `InventoryData` 加了 `chipModel` / `tidPrefix` 两个字段，
     App 侧目前**完全没有接**（`ReaderTag` 无该字段、`toReaderTag` 直接丢弃，
     `ReaderSessionManager.resolveChipModel` 是只认两个前缀的硬编码桩函数）
   - 本轮把链路补齐：SDK → ReaderTag → InventoryAccumulator → 列表 / CSV
   - 文案格式 `"英文名|中文名"`，按语言环境取一侧；未识别但有 TID 时显示
     `未知(XXXXXXXX)`，便于反馈给底层补芯片库
   - ⚠️ 依赖盘点区域：仅在「6C + 区域含 TID + 起始地址 0」时才有值，
     故芯片列随盘点区域显隐（详见 D.8）
6. **布局修正**：
   - 列标题居中显示
   - 掩码 switch 控件垂直居中

### D.1 RSSI 列动态显示

#### D.1.1 InventoryAdapter 改动

**文件**：`ui/adapter/InventoryAdapter.java`

**改动**：
```java
public final class InventoryAdapter extends ListAdapter<InventoryItem, InventoryAdapter.ViewHolder> {
    // ...
    
    // 用 setter 而不是构造参数：连接状态变化时不能重建 adapter，
    // 否则盘点中的标签列表会被清空、滚动位置丢失。
    private boolean showRssi = false;
    
    /** 模块类型变化时调用；仅 R2000 / R2000Plus 支持 RSSI */
    public void setModuleSubtype(ModuleSubtype subtype) {
        boolean next = subtype == ModuleSubtype.R2000 || subtype == ModuleSubtype.R2000_PLUS;
        if (next == showRssi) { return; }   // 避免无谓刷新
        showRssi = next;
        notifyItemRangeChanged(0, getItemCount());
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = getItem(position);
        holder.index.setText(String.format(java.util.Locale.US, "%03d", position + 1));
        holder.id.setText(item.getId());
        holder.data.setText(item.getData());
        holder.data.setVisibility(item.getData().isEmpty() ? View.GONE : View.VISIBLE);
        bindCounters(holder, item);
        
        // RSSI 列可见性由 setModuleSubtype 维护的 showRssi 字段决定
        holder.rssi.setVisibility(showRssi ? View.VISIBLE : View.GONE);
        
        int background = position % 2 == 0 ? R.color.rfid_panel_bg : R.color.rfid_page_bg;
        holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), background));
    }
    
    // bindCounters 方法保持不变
}
```

#### D.1.2 InventoryFragment 改动

**文件**：`ui/fragment/home/InventoryFragment.java`

**改动**：
```java
public final class InventoryFragment extends Fragment implements ReaderObserver {
    // ...
    
    private TextView columnRssi;  // 新增：RSSI 列标题引用
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.inventory_fragment, container, false);
        
        // ...现有初始化代码
        
        columnRssi = view.findViewById(R.id.tv_inventory_column_rssi);
        
        return view;
    }
    
    @Override
    public void onReaderStateChanged(ReaderState state) {
        // ...现有代码
        
        if (state.isConnected()) {
            ModuleSubtype subtype = state.getModuleSubtype();
            
            // ✅ 只更新模块类型，不重建 adapter（重建会清空盘点中的列表）
            adapter.setModuleSubtype(subtype);
            
            // 控制 RSSI 列标题可见性
            boolean showRssi = subtype == ModuleSubtype.R2000 
                            || subtype == ModuleSubtype.R2000_PLUS;
            columnRssi.setVisibility(showRssi ? View.VISIBLE : View.GONE);
        }
    }
}
```

#### D.1.3 布局文件改动

**文件**：`res/layout/inventory_item.xml`

**改动**：为 RSSI TextView 添加 ID，保持现有布局结构

**文件**：`res/layout/inventory_fragment.xml`

**改动**：为 RSSI 列标题添加 ID `tv_inventory_column_rssi`（行 110）

### D.2 标签信息列重新布局

#### D.2.1 分析当前问题

- EPC/USER/TID 标准长度为 24 位十六进制字符
- 当前 `tv_inventory_id` 使用 `ellipsize="middle"`，长字符串显示不全
- `tv_inventory_data` 用于显示额外数据（USER 或 TID），当前也显示不全

#### D.2.2 布局方案

**方案 A：两行显示（推荐）**
- 第一行：EPC（始终显示）
- 第二行：扩展区数据（根据盘点区域动态显示，非「仅盘点」档才有内容）
  - 6C：USER / TID / RESERVED
  - 6B：USER
  - GB·GJB：用户区 / 标签信息区
  - `tv_inventory_data` 为空或档位为「仅盘点」时，`View.GONE` 隐藏第二行

**方案 B：自适应字号**
- 根据内容长度动态调整字体大小
- 保持单行显示，但可能影响可读性

**采用方案 A**

**文件**：`res/layout/inventory_item.xml`

**改动**：
```xml
<LinearLayout
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:orientation="vertical"
    android:layout_marginEnd="@dimen/dp_8">
    <TextView
        android:id="@+id/tv_inventory_id"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="@color/rfid_primary_soft"
        android:textSize="@dimen/sp_12"
        android:fontFamily="monospace" />
    <TextView
        android:id="@+id/tv_inventory_data"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="@color/rfid_text_muted"
        android:textSize="@dimen/sp_10"
        android:fontFamily="monospace"
        android:layout_marginTop="@dimen/dp_2" />
</LinearLayout>
```

**关键改动**：
- EPC 和 USER/TID 字号调整（EPC: sp_12, DATA: sp_10）
- 使用等宽字体 `monospace` 提升数字可读性
- `ellipsize` 改为 `end`（从末尾截断更符合十六进制显示习惯）
- 添加 `layout_marginEnd` 避免与下一列过近

### D.3 盘点区域设置

#### D.3.1 数据模型扩展

**文件**：`reader/ReaderConfiguration.java`

⚠️ 现有类里 `inventoryMode` / `blfProfile` 都是 **`int`**（不是枚举），照原样扩展。
文档 §3.3.8 的 `setInventoryArea(area, startAddr, wordLen)` 有三个入参，
因此本轮新增 **3 个字段**，不只是 area。

**改动**：
```java
public final class ReaderConfiguration {
    /** 盘点长度默认 6 字，沿用现有 configureDefaultInventory 的 6C 默认值 */
    public static final int DEFAULT_INVENTORY_WORD_LEN = 6;

    /**
     * 盘点长度上限 32 字。文档 §3.3.8：USER 域 buffer 上限 64 字节 = 32 字，
     * 超出会内存溢出，因此 UI 输入必须卡在这个上限内。
     */
    public static final int MAX_INVENTORY_WORD_LEN = 32;

    public final int powerTenthsDbm;
    public final int inventoryMode;
    public final int blfProfile;
    // ...其余现有字段

    public final int inventoryArea;       // 新增：盘点区域（PDF §3.3.8，6C 为 0-3）
    public final int inventoryAddress;    // 新增：startAddr，字为单位；「仅盘点」档不起作用
    public final int inventoryWordLen;    // 新增：wordLen，字为单位；0 = 读全区（有风险，见 D.0）

    // 全参构造函数（原 13 参 → 16 参）
    public ReaderConfiguration(int powerTenthsDbm, int inventoryMode, int blfProfile,
            int session, int target, boolean dynamicQ, int qValue, int qMinValue,
            int qMaxValue, int qRetryCount, int qThresholdMultiplier, int qToggleTarget,
            int qRepeatUntilNoTags,
            int inventoryArea, int inventoryAddress, int inventoryWordLen) {
        this.powerTenthsDbm = powerTenthsDbm;
        this.inventoryMode = inventoryMode;
        this.blfProfile = blfProfile;
        this.session = session;
        this.target = target;
        this.dynamicQ = dynamicQ;
        this.qValue = qValue;
        this.qMinValue = qMinValue;
        this.qMaxValue = qMaxValue;
        this.qRetryCount = qRetryCount;
        this.qThresholdMultiplier = qThresholdMultiplier;
        this.qToggleTarget = qToggleTarget;
        this.qRepeatUntilNoTags = qRepeatUntilNoTags;
        this.inventoryArea = inventoryArea;
        this.inventoryAddress = inventoryAddress;
        this.inventoryWordLen = inventoryWordLen;
    }

    // 原 13 参构造函数保留为重载，盘点区域取默认值（仅 EPC）
    public ReaderConfiguration(int powerTenthsDbm, int inventoryMode, int blfProfile,
            int session, int target, boolean dynamicQ, int qValue, int qMinValue,
            int qMaxValue, int qRetryCount, int qThresholdMultiplier, int qToggleTarget,
            int qRepeatUntilNoTags) {
        this(powerTenthsDbm, inventoryMode, blfProfile, session, target, dynamicQ, qValue,
                qMinValue, qMaxValue, qRetryCount, qThresholdMultiplier, qToggleTarget,
                qRepeatUntilNoTags, 0, 0, DEFAULT_INVENTORY_WORD_LEN);
    }

    // 现有 7 参构造函数不动（内部转调 13 参版本）
}
```

⚠️ **改动扩散提醒**：`ReaderSessionManager` 里每个 setter 都会重建整个
`ReaderConfiguration`（`setPower` / `setBlf` / `setSessionTarget` / `setQ` /
`setInventoryMode` / 握手回填，共 6+ 处）。保留 13 参重载可让这些调用点
**先不改也能编译**，但那样会把用户设置的盘点区域重置为默认值。
→ 正确做法：全部 6+ 处改调 16 参版本，透传 `configuration.inventoryArea` /
  `inventoryAddress` / `inventoryWordLen`。建议实施时先删掉 13 参重载，
  用编译错误逐个定位调用点，改完再决定是否恢复重载。

#### D.3.2 枚举定义

**新建文件**：`reader/InventoryArea.java`

选项集**随协议不同**（PDF §3.3.8），因此枚举带 protocol 维度：

```java
package com.leo.remote.reader;

import java.util.ArrayList;
import java.util.List;

/**
 * 盘点区域。取值依《UHF Library开发文档》§3.3.8 setInventoryArea。
 *
 * 6C:      0=仅 EPC, 1=EPC+USER, 2=EPC+TID, 3=EPC+RESERVED
 * 6B:      0=仅 UID, 1=UID+USER
 * GB/GJB:  0=仅编码区, 1=编码区+用户区, 2=编码区+标签信息区
 *
 * 注意：app/libs/uhf.jar 内 InventoryParams 的旧注释把 1/2 写成 EPC_TID/EPC_USER，
 * 与文档相反。InventoryParams 仅透传 int，实际语义由 so 库决定，故以文档为准；
 * 联调阶段需真机核对（见 D.3.6）。
 */
public enum InventoryArea {
    // ---- ISO 18000-6C ----
    C_EPC_ONLY(TagProtocol.ISO_18000_6C, 0, "仅盘点 EPC", "EPC 号"),
    C_EPC_USER(TagProtocol.ISO_18000_6C, 1, "盘点 EPC 和 USER", "EPC/USER"),
    C_EPC_TID(TagProtocol.ISO_18000_6C, 2, "盘点 EPC 和 TID", "EPC/TID"),
    C_EPC_RESERVED(TagProtocol.ISO_18000_6C, 3, "盘点 EPC 和 RESERVED", "EPC/RESERVED"),

    // ---- ISO 18000-6B ----
    B_UID_ONLY(TagProtocol.ISO_18000_6B, 0, "仅盘点 UID", "UID"),
    B_UID_USER(TagProtocol.ISO_18000_6B, 1, "盘点 UID 和 USER", "UID/USER"),

    // ---- GJB 7377.1 ----
    GJB_CODE_ONLY(TagProtocol.GJB_7377_1, 0, "仅盘点编码区", "编码区"),
    GJB_CODE_USER(TagProtocol.GJB_7377_1, 1, "盘点编码区和用户区", "编码区/用户区"),
    GJB_CODE_INFO(TagProtocol.GJB_7377_1, 2, "盘点编码区和标签信息区", "编码区/信息区"),

    // ---- GB/T 29768 ----
    GB_CODE_ONLY(TagProtocol.GB_T_29768, 0, "仅盘点编码区", "编码区"),
    GB_CODE_USER(TagProtocol.GB_T_29768, 1, "盘点编码区和用户区", "编码区/用户区"),
    GB_CODE_INFO(TagProtocol.GB_T_29768, 2, "盘点编码区和标签信息区", "编码区/信息区");

    private final TagProtocol protocol;
    private final int value;
    private final String displayName;
    private final String columnHeader;

    InventoryArea(TagProtocol protocol, int value, String displayName, String columnHeader) {
        this.protocol = protocol;
        this.value = value;
        this.displayName = displayName;
        this.columnHeader = columnHeader;
    }

    public TagProtocol getProtocol() { return protocol; }

    public int getValue() { return value; }

    public String getDisplayName() { return displayName; }

    /** 盘点页列标题文案 */
    public String getColumnHeader() { return columnHeader; }

    /** value == 0 即「仅盘点」档：文档明确 startAddr/wordLen 对该档不起作用 */
    public boolean isBaseOnly() { return value == 0; }

    /** 该协议支持的选项，按 SDK 取值升序 */
    public static List<InventoryArea> forProtocol(TagProtocol protocol) {
        List<InventoryArea> list = new ArrayList<>();
        for (InventoryArea area : values()) {
            if (area.protocol == protocol) { list.add(area); }
        }
        return list;
    }

    public static InventoryArea of(TagProtocol protocol, int value) {
        for (InventoryArea area : values()) {
            if (area.protocol == protocol && area.value == value) { return area; }
        }
        return forProtocol(protocol).get(0);   // 越界回落到「仅盘点」档
    }
}
```

#### D.3.3 配置页 UI

**文件**：`res/layout/reader_config_fragment.xml`

在工作模式行（`row_config_work_mode`）**上方**添加一行，样式照现有配置行：

```xml
<!-- 盘点区域设置（在工作模式上方） -->
<LinearLayout
    android:id="@+id/row_config_inventory_area"
    android:layout_width="match_parent"
    android:layout_height="@dimen/dp_56"
    android:background="@color/rfid_panel_bg"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:paddingHorizontal="@dimen/dp_20">
    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/config_inventory_area"
        android:textColor="@color/rfid_text"
        android:textSize="@dimen/sp_14" />
    <TextView
        android:id="@+id/tv_config_inventory_area"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:drawableEnd="@drawable/rfid_arrow_right_ic"
        android:drawablePadding="@dimen/dp_8"
        android:text="@string/config_inventory_area_epc_only"
        android:textColor="@color/rfid_text_muted"
        android:textSize="@dimen/sp_13" />
</LinearLayout>
```

副标题（当前 addr/len）可复用同一行右侧文案，格式：
- 仅盘点档：`仅盘点 EPC`
- 其他档：`盘点 EPC 和 USER · 地址 0 · 6 字`

#### D.3.4 配置页逻辑

**文件**：`ui/fragment/home/ReaderConfigFragment.java`
（注意实际路径是 `ui/fragment/home/`，不是 `ui/fragment/config/`）

选项集由协议决定，因此**标签从枚举动态生成**，不用固定的 string-array：

```java
public final class ReaderConfigFragment extends Fragment implements ReaderObserver {
    // ...

    private TextView inventoryAreaView;

    // onCreateView 内，照 protocolView 的写法
    inventoryAreaView = findViewById(R.id.tv_config_inventory_area);
    findViewById(R.id.row_config_inventory_area)
            .setOnClickListener(view -> showInventoryAreaDialog());

    // 与现有 showProtocolDialog / showBlfDialog 写法保持一致
    private void showInventoryAreaDialog() {
        if (!requireReaderOnline()) { return; }

        TagProtocol protocol = readerState.getProtocol();
        List<InventoryArea> areas = InventoryArea.forProtocol(protocol);
        String[] labels = areas.stream()
                .map(InventoryArea::getDisplayName).toArray(String[]::new);
        int current = configuration == null ? 0 : configuration.inventoryArea;
        int selected = Math.max(0, areas.indexOf(InventoryArea.of(protocol, current)));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_inventory_area)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    InventoryArea target = areas.get(which);
                    if (target.isBaseOnly()) {
                        // 文档：startAddr/wordLen 对「仅盘点」不起作用，直接下发 0/0
                        applyInventoryArea(target, 0, 0);
                    } else {
                        // 需要读扩展区 → 追问 起始地址 / 盘点长度
                        showInventoryRangeDialog(target);
                    }
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    /** 二级弹窗：起始地址 + 盘点长度（字为单位），默认沿用当前配置 */
    private void showInventoryRangeDialog(InventoryArea target) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_inventory_range, null, false);
        EditText addrInput = content.findViewById(R.id.et_inventory_addr);
        EditText lenInput = content.findViewById(R.id.et_inventory_len);
        addrInput.setText(String.valueOf(configuration == null ? 0 : configuration.inventoryAddress));
        lenInput.setText(String.valueOf(configuration == null
                ? ReaderConfiguration.DEFAULT_INVENTORY_WORD_LEN : configuration.inventoryWordLen));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(target.getDisplayName())
                .setMessage(R.string.config_inventory_range_hint)
                .setView(content)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_confirm, (dialog, which) -> {
                    int addr = parseIntOrDefault(addrInput.getText().toString(), 0);
                    int len = parseIntOrDefault(lenInput.getText().toString(),
                            ReaderConfiguration.DEFAULT_INVENTORY_WORD_LEN);
                    if (addr < 0 || len < 0 || len > ReaderConfiguration.MAX_INVENTORY_WORD_LEN) {
                        showToast(getString(R.string.config_inventory_range_invalid,
                                ReaderConfiguration.MAX_INVENTORY_WORD_LEN));
                        return;
                    }
                    if (len == 0) {
                        // 文档：len=0 读全区，仅 R2000 新 firmware 支持，
                        // 且 USER 域 buffer 上限 64 字节，超 32 字节会内存溢出
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.config_inventory_len_zero_title)
                                .setMessage(R.string.config_inventory_len_zero_warning)
                                .setNegativeButton(R.string.common_cancel, null)
                                .setPositiveButton(R.string.common_confirm,
                                        (d2, w2) -> applyInventoryArea(target, addr, 0))
                                .show();
                        return;
                    }
                    applyInventoryArea(target, addr, len);
                })
                .show();
    }

    private void applyInventoryArea(InventoryArea target, int addr, int len) {
        String summary = target.isBaseOnly()
                ? target.getDisplayName()
                : getString(R.string.config_inventory_area_summary,
                        target.getDisplayName(), addr, len);
        confirmAndApply(R.string.config_inventory_area, summary,
                () -> session.setInventoryArea(target.getValue(), addr, len),
                R.string.config_inventory_area_set_failed, () -> {});
    }

    // 注意：不要在 Fragment 里手写「设置 → 更新配置 → 存缓存 → 回填 UI」。
    // 该链路已由 confirmAndApply + ReaderSessionManager.updateConfiguration
    // → notifyConfiguration → onReaderConfigurationChanged 统一完成，
    // 与 setPower / setBlf / setSessionTarget 保持一致。
    // Fragment 只需在 onReaderConfigurationChanged / renderState 里回填文案：
    //   InventoryArea area = InventoryArea.of(readerState.getProtocol(),
    //           configuration.inventoryArea);
    //   inventoryAreaView.setText(area.isBaseOnly() ? area.getDisplayName()
    //           : getString(R.string.config_inventory_area_summary, area.getDisplayName(),
    //                   configuration.inventoryAddress, configuration.inventoryWordLen));
}
```

⚠️ **切协议时必须刷新该行**：协议变了选项集也变（6C 有 4 档、6B 只有 2 档）。
`showProtocolDialog` 成功后，`onReaderStateChanged` 里要用
`InventoryArea.of(newProtocol, configuration.inventoryArea)` 重新取值，
越界时 `of()` 会回落到「仅盘点」档（例如 6C 的 area=3 切到 6B 后回落为 0）。

**新建布局**：`res/layout/dialog_inventory_range.xml`
两个 `TextInputLayout` + `TextInputEditText`（`inputType="number"`），
分别对应「起始地址（字）」「盘点长度（字，0=全区）」。

#### D.3.5 Gateway 与 SessionManager 新增方法

⚠️ **重要**：Java SDK **没有** `setInventoryArea(area, addr, len)` 这样的现成方法。
文档 §3.3.8 描述的是 C/C++ 层接口，Java 层只有整体读写 `InventoryParams` 结构体：

```java
// com.uhf.linkage.Linkage
public native int Radio_SetInventoryParams(InventoryParams inventoryParams);  // line 167
public native int Radio_GetInventoryParams(InventoryParams inventoryParams);  // line 175
```

`InventoryParams` 三个字段 `inventoryArea` / `address` / `len` 正好对应文档的
`area` / `startAddr` / `wordLen`。**改 area 时必须一起带上 addr 和 len**，
否则会被清零——这与 C.5「安全的参数设置」是同一类问题。

**文件**：`reader/UhfSdkGateway.java`（接口新增）

```java
/**
 * 设置盘点区域（PDF §3.3.8）。
 * @param area    区域档位，取值随协议不同，见 InventoryArea
 * @param addr    起始地址（字），「仅盘点」档不起作用
 * @param wordLen 盘点长度（字），0 = 全区（仅 R2000 新 firmware，有溢出风险）
 */
int setInventoryArea(int area, int addr, int wordLen);

/** 读取盘点区域（PDF §3.3.9），返回 int[]{area, addr, len}；失败返回 null */
int[] getInventoryArea();
```

**文件**：`reader/NativeUhfSdkGateway.java`（实现）

```java
@Override
public int setInventoryArea(int area, int addr, int wordLen) {
    InventoryParams params = new InventoryParams();
    params.setValue(area, addr, wordLen);
    return linkage.Radio_SetInventoryParams(params);
}

@Override
public int[] getInventoryArea() {
    InventoryParams params = new InventoryParams();
    if (linkage.Radio_GetInventoryParams(params) != 0) { return null; }
    return new int[]{params.inventoryArea, params.address, params.len};
}
```

**文件**：`reader/ReaderSessionManager.java`（新增，照 `setBlf` 的写法）

```java
public CompletableFuture<Integer> setInventoryArea(int area, int addr, int wordLen) {
    return submitConnected(() -> {
        // 「仅盘点」档 SDK 忽略 addr/len，统一传 0，避免脏值残留
        int effectiveAddr = area == 0 ? 0 : addr;
        int effectiveLen = area == 0 ? 0 : wordLen;
        return updateConfiguration(
                monitorSdkStatus(gateway.setInventoryArea(area, effectiveAddr, effectiveLen)),
                new ReaderConfiguration(configuration.powerTenthsDbm, inventoryMode,
                        configuration.blfProfile, configuration.session, configuration.target,
                        configuration.dynamicQ, configuration.qValue, configuration.qMinValue,
                        configuration.qMaxValue, configuration.qRetryCount,
                        configuration.qThresholdMultiplier, configuration.qToggleTarget,
                        configuration.qRepeatUntilNoTags,
                        area, effectiveAddr, effectiveLen));
    });
}
```

#### D.3.6 与现有 `configureDefaultInventory` 的冲突（必须先解决）

⚠️ **这是本节最容易踩的坑**。现状有两个问题：

**问题 1：默认值不是「仅 EPC」**

```java
// NativeUhfSdkGateway.configureDefaultInventory() 现状（约 76-87 行）
if (protocol == TagProtocol.ISO_18000_6C) {
    params.setValue(1, 0, 6);   // area=1 → 按文档是 EPC + USER，不是「仅 EPC」
} else if (protocol == TagProtocol.ISO_18000_6B) {
    params.setValue(0, 0, 8);
} else {
    params.setValue(0, 0, 6);
}
```

**问题 2：该方法被调用了两处，其中一处在每次开始盘点时**

```java
// ReaderSessionManager.java:342  —— 切协议后
if (status == 0) { status = gateway.configureDefaultInventory(protocol); }

// ReaderSessionManager.java:408  —— startInventory() 内，每次开始盘点都会执行
public CompletableFuture<Integer> startInventory() {
    return submitConnected(() -> {
        int status = gateway.configureDefaultInventory(state.getProtocol());
        ...
```

也就是说，即便配置页设置成功，**下一次点「开始盘点」就会被重置回 area=1/0/6**。
这个调用点比切协议那处更致命，必须一起改。

**改法**（三步，缺一不可）：

1. `configureDefaultInventory(TagProtocol)` 改为
   `applyInventoryParams(TagProtocol protocol, int area, int addr, int wordLen)`，
   由调用方传入当前配置，方法内不再写死档位：
   ```java
   @Override
   public int applyInventoryParams(TagProtocol protocol, int area, int addr, int wordLen) {
       InventoryParams params = new InventoryParams();
       params.setValue(area, area == 0 ? 0 : addr, area == 0 ? 0 : wordLen);
       return linkage.Radio_SetInventoryParams(params);
   }
   ```
2. 两个调用点都改为透传当前配置：
   ```java
   // setProtocol 内：协议变了，用 of() 把旧 area 映射到新协议的合法档位
   InventoryArea mapped = InventoryArea.of(protocol, configuration.inventoryArea);
   status = gateway.applyInventoryParams(protocol, mapped.getValue(),
           configuration.inventoryAddress, configuration.inventoryWordLen);

   // startInventory 内：直接用当前配置，不再"默认化"
   int status = gateway.applyInventoryParams(state.getProtocol(),
           configuration.inventoryArea, configuration.inventoryAddress,
           configuration.inventoryWordLen);
   ```
   `setProtocol` 里 area 被 `of()` 回落时（如 6C 的 3 档切到 6B），
   要同步把回落后的值写回 `ReaderConfiguration`，否则 UI 与设备不一致。
3. 默认值统一为「仅盘点」档：`ReaderConfiguration` 里 `inventoryArea = 0`、
   `inventoryAddress = 0`、`inventoryWordLen = DEFAULT_INVENTORY_WORD_LEN`（6），
   6B 的 8 字默认值不再特殊处理（用户可在二级弹窗里改）。

**真机核对项（发版前必做）**：
拿一张已知 TID 与 USER 内容的 6C 标签，设 `area = 1`、`addr = 0`、`len = 6` 盘点，
看返回的第二段数据是 USER 还是 TID：
- 是 USER → 与文档一致，`InventoryArea` 枚举不用改
- 是 TID → 与 jar 旧注释一致，只需把 `C_EPC_USER` 与 `C_EPC_TID` 的 value 互换
  （1↔2），UI、缓存、列标题逻辑都不用动
`C_EPC_RESERVED`（3）同样要试一次，若 SDK 返回错误码则从 6C 选项集里去掉该档。

**另一处命名冲突提醒**：`ReaderSessionManager.inventoryMode`（现有字段）指的是
`startInventory(mode, flag)` 的**工作模式**（0=单次 / 1=高性能 / 2=低功耗），
与本轮新增的 `inventoryArea`（盘点区域）是两个完全不同的概念，不要混用。
配置页「工作模式」对应前者，新增的「盘点区域」对应后者。

### D.4 列标题动态更新

#### D.4.1 InventoryFragment 改动

**文件**：`ui/fragment/home/InventoryFragment.java`

**改动**：
```java
public final class InventoryFragment extends Fragment implements ReaderObserver {
    // ...
    
    private TextView columnEpc;  // EPC/USER/TID 列标题
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.inventory_fragment, container, false);
        
        // ...
        columnEpc = view.findViewById(R.id.tv_inventory_column_epc);
        columnRssi = view.findViewById(R.id.tv_inventory_column_rssi);
        
        return view;
    }
    
    @Override
    public void onReaderStateChanged(ReaderState state) {
        // ...
        
        if (state.isConnected()) {
            ModuleSubtype subtype = state.getModuleSubtype();
            ReaderConfiguration config = state.getConfiguration();
            
            // 更新 RSSI 列可见性
            boolean showRssi = subtype == ModuleSubtype.R2000 
                            || subtype == ModuleSubtype.R2000_PLUS;
            columnRssi.setVisibility(showRssi ? View.VISIBLE : View.GONE);
            
            // 更新 EPC 列标题：标题文案由 InventoryArea 决定，
            // 且必须带上协议（6C 的 area=1 是 EPC/USER，6B 的 area=1 是 UID/USER）
            InventoryArea area = InventoryArea.of(state.getProtocol(), config.inventoryArea);
            columnEpc.setText(area.getColumnHeader());
            
            // ✅ 只更新模块类型，不重建 adapter
            adapter.setModuleSubtype(subtype);
        }
    }
}
```

### D.5 布局修正

#### D.5.1 列标题居中

**文件**：`res/layout/inventory_fragment.xml`

**改动**（行 107-111）：
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="@dimen/dp_36"
    android:background="@color/rfid_nav_bg"
    android:gravity="center_vertical"
    android:paddingHorizontal="@dimen/dp_16">
    <TextView 
        android:layout_width="@dimen/dp_40" 
        android:layout_height="wrap_content" 
        android:gravity="center" 
        android:text="@string/inventory_column_index" 
        android:textColor="@color/rfid_text_muted" 
        android:textSize="@dimen/sp_12" />
    <TextView 
        android:id="@+id/tv_inventory_column_epc"
        android:layout_width="0dp" 
        android:layout_height="wrap_content" 
        android:layout_weight="1" 
        android:gravity="center"
        android:text="@string/inventory_column_epc" 
        android:textColor="@color/rfid_text_muted" 
        android:textSize="@dimen/sp_12" />
    <TextView 
        android:layout_width="@dimen/dp_52" 
        android:layout_height="wrap_content" 
        android:gravity="center" 
        android:text="@string/inventory_column_count" 
        android:textColor="@color/rfid_text_muted" 
        android:textSize="@dimen/sp_12" />
    <TextView 
        android:id="@+id/tv_inventory_column_rssi"
        android:layout_width="@dimen/dp_52" 
        android:layout_height="wrap_content" 
        android:gravity="center" 
        android:text="@string/inventory_column_rssi" 
        android:textColor="@color/rfid_text_muted" 
        android:textSize="@dimen/sp_12" />
    <TextView 
        android:id="@+id/tv_inventory_column_chip"
        android:layout_width="@dimen/dp_72" 
        android:layout_height="wrap_content" 
        android:gravity="center" 
        android:text="@string/inventory_column_chip" 
        android:textColor="@color/rfid_text_muted" 
        android:textSize="@dimen/sp_12" />
</LinearLayout>
```

**关键改动**：
- 所有列标题添加 `android:gravity="center"`
- EPC / RSSI / 芯片三列补 `id`，供 D.4（标题文案）与 D.8（芯片列可见性）使用

#### D.5.2 掩码 Switch 垂直居中

**文件**：`res/layout/inventory_mask_panel.xml`（第 59-79 行）

**根因**：Switch 的父容器是 `FrameLayout`，而 `FrameLayout` **不解析 `android:gravity`**，
只认子 View 的 `layout_gravity`。当前写法把 `gravity="center"` 加在了 FrameLayout 上，
对子 View 完全无效，所以 Switch 实际贴在容器顶部。

**当前代码**：
```xml
<FrameLayout
    android:id="@+id/fl_inventory_mask_switch_target"
    android:layout_width="wrap_content"
    android:layout_height="match_parent"
    android:gravity="center">                     <!-- ❌ FrameLayout 忽略此属性 -->

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/sw_inventory_mask"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />    <!-- ❌ 缺 layout_gravity -->
</FrameLayout>
```

**修改**：把居中声明移到子 View 上（父容器的 `gravity` 可一并删除）：
```xml
<FrameLayout
    android:id="@+id/fl_inventory_mask_switch_target"
    android:layout_width="wrap_content"
    android:layout_height="match_parent"
    android:layout_marginStart="@dimen/dp_8"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackgroundBorderless">

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/sw_inventory_mask"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical"   <!-- ✅ 关键 -->
        android:clickable="false"
        android:contentDescription="@string/inventory_mask_title"
        android:focusable="false"
        android:minWidth="0dp"
        android:padding="0dp"
        android:text="" />
</FrameLayout>
```

**以下为通用参考写法（若后续改为 LinearLayout 结构）**：

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="@dimen/dp_48"
    android:background="@color/rfid_panel_bg"
    android:gravity="center_vertical"
    android:paddingHorizontal="@dimen/dp_16">
    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="@string/inventory_mask_enable"
        android:textColor="@color/rfid_text"
        android:textSize="@dimen/sp_14" />
    <androidx.appcompat.widget.SwitchCompat
        android:id="@+id/switch_inventory_mask"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical" />
</LinearLayout>
```

**关键改动小结**：
- `FrameLayout` 上的 `android:gravity="center"` 无效，删除
- `SwitchMaterial` 添加 `android:layout_gravity="center_vertical"`
- 若父容器改为 `LinearLayout`，则父级用 `gravity="center_vertical"` 即可生效（两者机制不同，勿混用）

### D.6 参数缓存更新

#### D.6.1 ReaderConfigCache 扩展

**文件**：`reader/ReaderConfigCache.java`

**改动**：
```java
public final class ReaderConfigCache {
    // ...
    
    public void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration config) {
        String prefix = subtype.name() + "_";
        mmkv.encode(prefix + "power", config.powerTenthsDbm);
        // ...现有参数
        mmkv.encode(prefix + "inventoryArea", config.inventoryArea);        // 新增
        mmkv.encode(prefix + "inventoryAddress", config.inventoryAddress);  // 新增
        mmkv.encode(prefix + "inventoryWordLen", config.inventoryWordLen);  // 新增
    }
    
    public ReaderConfiguration loadConfiguration(ModuleSubtype subtype) {
        String prefix = subtype.name() + "_";
        
        if (!mmkv.contains(prefix + "power")) {
            return getDefaultConfiguration(subtype);
        }
        
        return new ReaderConfiguration(
            mmkv.decodeInt(prefix + "power", 0),
            // ...现有参数
            mmkv.decodeInt(prefix + "inventoryArea", 0),         // 新增
            mmkv.decodeInt(prefix + "inventoryAddress", 0),      // 新增
            mmkv.decodeInt(prefix + "inventoryWordLen",
                    ReaderConfiguration.DEFAULT_INVENTORY_WORD_LEN)   // 新增
        );
    }
    
    private ReaderConfiguration getDefaultConfiguration(ModuleSubtype subtype) {
        // ...现有代码
        // inventoryArea = 0（「仅盘点」档）
        // inventoryAddress = 0
        // inventoryWordLen = DEFAULT_INVENTORY_WORD_LEN（6）
    }
}
```

### D.7 初始化握手更新

**文件**：`reader/ReaderHandshake.java`

**改动**：在参数获取流程中添加 inventoryArea 读取：

握手在工作线程内同步执行（见 C.5.2），不使用回调式 API：

```java
// 读取盘点区域（PDF §3.3.9 返回 area + startAddr + wordLen 三个值），
// 失败则回退缓存 → 默认值（与 C.5 容错链一致）
int inventoryArea;
int inventoryAddress;
int inventoryWordLen;
int[] area = gateway.getInventoryArea();
if (area != null) {
    inventoryArea = area[0];
    inventoryAddress = area[1];
    inventoryWordLen = area[2];
    progress.accept("读取盘点区域成功");
} else {
    ReaderConfiguration fallback = cache.loadConfiguration(subtype);
    if (fallback == null) {
        fallback = ReaderConfigCache.getDefaultConfiguration(subtype);
    }
    inventoryArea = fallback.inventoryArea;
    inventoryAddress = fallback.inventoryAddress;
    inventoryWordLen = fallback.inventoryWordLen;
    progress.accept("读取盘点区域失败，使用缓存值");
}

// 协议校验：握手结束时协议固定为 6C（见 ReaderSessionManager 约 588 行
// publish(... .protocol(TagProtocol.ISO_18000_6C) ...)），
// 若读回的 area 在该协议下非法则回落到「仅盘点」档
inventoryArea = InventoryArea.of(TagProtocol.ISO_18000_6C, inventoryArea).getValue();
```

⚠️ 注意 `loadConfiguration` 可能返回 `null`（无缓存），必须再回退到
`getDefaultConfiguration`，否则会 NPE。

### D.8 芯片型号识别

底层库（`app/libs/uhf.jar`）已在 `com.uhf.structures.InventoryData` 里加了两个字段，
App 侧目前**完全没有接**，本节把这条链路补齐。

#### D.8.1 底层能力（已有，无需改动）

```java
// com.uhf.structures.InventoryData
/**
 * 芯片型号，由 TID 前 4 字节查表得出，如 "IMPINJ Monza M750"、
 * "Fudan FM13UF011E|复旦 FM13UF011E"（"|" 前为英文名，后为中文名）。
 * 为 null 表示未能识别，可能是：本次盘点未读 TID 区、盘点起始地址不为 0、
 * 标签协议非 ISO18000-6C、或 TID 前缀不在芯片库内。
 * 后一种情况可通过 getTidPrefix() 非 0 来区分。
 */
public String getChipModel();

/**
 * TID 前 4 字节，大端。为 0 表示本次盘点没有取到可用的 TID 前缀。
 * 注意该值按无符号解读，作为 int 会是负数，打印请用 String.format("%08X", tidPrefix)。
 */
public int getTidPrefix();
```

⚠️ **与 D.3 强耦合**：`chipModel` 只有在「盘点区域包含 TID」+「起始地址 = 0」+
「协议 = 6C」三个条件同时成立时才有值。这意味着：
- 默认档位「仅盘点 EPC」下，芯片型号**永远是空的**——这是正常现象，不是 bug
- D.3.4 的二级弹窗允许用户把起始地址改成非 0，那样会**静默地**让芯片识别失效
  → 弹窗里要给一句提示：「起始地址非 0 时无法识别芯片型号」
- 反过来可以用它验证 D.3.6 的档位争议：**把盘点区域设成文档所说的 EPC+TID 档
  （PDF 记为 2），若 `tidPrefix != 0` 则说明该档确实读的是 TID**，这是比肉眼看
  数据更可靠的判据

#### D.8.2 ReaderTag 扩展

**文件**：`reader/ReaderTag.java`

现有 5 个字段没有芯片相关信息，`NativeUhfSdkGateway.toReaderTag()`（约 297-305 行）
把 SDK 给的 `chipModel` / `tidPrefix` **直接丢掉了**。

```java
public final class ReaderTag {
    public final String id;
    public final String data;
    public final int rssi;
    public final int tagType;
    public final int count;
    public final String chipModel;   // 新增：底层查表结果，可能为 null
    public final int tidPrefix;      // 新增：TID 前 4 字节，0 = 未取到

    public ReaderTag(String id, String data, int rssi, int tagType, int count,
            String chipModel, int tidPrefix) {
        this.id = id;
        this.data = data;
        this.rssi = rssi;
        this.tagType = tagType;
        this.count = count;
        this.chipModel = chipModel;
        this.tidPrefix = tidPrefix;
    }
}
```

**文件**：`reader/NativeUhfSdkGateway.java`（`toReaderTag`，约 297 行）

```java
private static ReaderTag toReaderTag(InventoryData data) {
    Log.d(TAG, "inventory tag epcLength=" + data.getEpcLength()
            + " dataLength=" + data.getDataLength()
            + " rssi=" + data.getRSSI()
            + " tagType=" + data.getTagType()
            + " chipModel=" + data.getChipModel()
            + " tidPrefix=" + String.format("%08X", data.getTidPrefix())
            + " inventoriedTimes=" + data.getTagInventoriedTimes());
    String id = HexCodec.encode(data.getEpc(), data.getEpcLength());
    String extra = HexCodec.encode(data.getData(), data.getDataLength());
    return new ReaderTag(id, extra, data.getRSSI(), data.getTagType(),
            data.getTagInventoriedTimes(), data.getChipModel(), data.getTidPrefix());
}
```

⚠️ `inventoryOnce()`（约 105 行）也走 `toReaderTag`，一并受益，不用单独改。

#### D.8.3 删掉硬编码的 resolveChipModel

**文件**：`reader/ReaderSessionManager.java`（约 880 行）

现状是个写死两个前缀、且**拿 data 当 TID 用**的桩函数，必须删掉：

```java
// ❌ 删除：只认 E28011/E28012，且 data 在「仅 EPC」档位下是空串
private static String resolveChipModel(String data) {
    if (data == null || data.length() < 6) { return ""; }
    if (data.startsWith("E28011") || data.startsWith("E28012")) { return "Impinj Monza"; }
    return "";
}
```

调用点（约 135 行）改为直接用底层给的值：

```java
gateway.setInventoryListener(tag -> {
    if (!state.isConnected() || !state.isInventoryRunning()) { return; }
    inventory.add(tag.id, tag.data, tag.rssi, tag.count,
            ChipModelFormatter.display(tag.chipModel, tag.tidPrefix,
                    application.getString(R.string.inventory_chip_unknown)));
    scheduleInventoryUpdate();
});
```

#### D.8.4 显示文案格式化

**新建文件**：`reader/ChipModelFormatter.java`

底层返回的是 `"英文名|中文名"`（如 `"Fudan FM13UF011E|复旦 FM13UF011E"`），
也可能只有英文名（如 `"IMPINJ Monza M750"`），还可能为 null。

```java
package com.leo.remote.reader;

import java.util.Locale;

public final class ChipModelFormatter {
    private ChipModelFormatter() {}

    /**
     * 芯片型号显示文案。
     * @param chipModel       底层查表结果，"英文名|中文名" 或 "英文名"，可能为 null
     * @param tidPrefix       TID 前 4 字节，0 表示本次盘点没取到 TID
     * @param unknownTemplate 未识别文案模板，取 R.string.inventory_chip_unknown，
     *                        由调用方传入，避免工具类持有 Context
     * @return 识别成功 → 型号名；有 TID 但库里没有 → "未知(XXXXXXXX)"；没读到 TID → ""
     */
    public static String display(String chipModel, int tidPrefix, String unknownTemplate) {
        if (chipModel != null && !chipModel.isEmpty()) {
            int sep = chipModel.indexOf('|');
            if (sep < 0) { return chipModel; }
            // 中文环境取 "|" 后的中文名，其他取前面的英文名
            boolean zh = Locale.getDefault().getLanguage().startsWith("zh");
            String zhName = chipModel.substring(sep + 1).trim();
            String enName = chipModel.substring(0, sep).trim();
            if (zh) { return zhName.isEmpty() ? enName : zhName; }
            return enName.isEmpty() ? zhName : enName;
        }
        if (tidPrefix != 0) {
            // 读到了 TID 但芯片库里没有 → 显示前缀，便于反馈给底层补库
            return String.format(unknownTemplate, String.format("%08X", tidPrefix));
        }
        return "";   // 未读 TID 区，adapter 会显示 "-"
    }
}
```

⚠️ `tidPrefix` 按**无符号**解读，直接打印会是负数，必须用 `%08X`。

#### D.8.5 芯片列可见性

芯片列在「仅盘点 EPC」档位下永远是 `-`，白占 `dp_72` 宽度，而 D.2 正是为了给
24 位 EPC 腾地方。因此**按盘点区域控制该列显隐**，机制与 D.1 的 RSSI 列一致。

**文件**：`ui/adapter/InventoryAdapter.java`

```java
private boolean showChip = false;

/** 盘点区域变化时调用；只有读了 TID 区才可能识别出芯片型号 */
public void setChipVisible(boolean visible) {
    if (visible == showChip) { return; }
    showChip = visible;
    notifyItemRangeChanged(0, getItemCount());   // 同样不要重建 adapter
}

// onBindViewHolder 内
holder.chip.setVisibility(showChip ? View.VISIBLE : View.GONE);
holder.chip.setText(item.getChipModel().isEmpty() ? "-" : item.getChipModel());
```

**文件**：`ui/fragment/home/InventoryFragment.java`（并入 D.4 的 `onReaderStateChanged`）

```java
// 芯片型号依赖 TID：6C 协议 + 区域含 TID + 起始地址为 0
InventoryArea area = InventoryArea.of(state.getProtocol(), config.inventoryArea);
boolean chipCapable = state.getProtocol() == TagProtocol.ISO_18000_6C
        && area == InventoryArea.C_EPC_TID
        && config.inventoryAddress == 0;
columnChip.setVisibility(chipCapable ? View.VISIBLE : View.GONE);
adapter.setChipVisible(chipCapable);
```

#### D.8.6 累积器与去重

**文件**：`reader/InventoryAccumulator.java`（约 12-22 行）

现有 `add()` 的 key 是 `id + '|' + data`，`chipModel` 每次覆盖写入。
同一标签在同一档位下 `data` 相同 → key 相同 → `chipModel` 也相同，
**正常情况下不会抖动**。但底层查表偶发失败时会把已识别的型号覆盖成空串，
加一道「非空不被空覆盖」的保护更稳妥：

```java
public synchronized void add(String id, String data, int rssi, int reportedCount,
        String chipModel) {
    String safeId = id == null ? "" : id;
    String safeData = data == null ? "" : data;
    String key = safeId + '|' + safeData;
    InventoryItem previous = items.get(key);
    long increment = Math.max(1, reportedCount);
    long count = previous == null ? increment : previous.getCount() + increment;
    // 已识别出的型号不被后续空值覆盖
    String safeChip = chipModel == null ? "" : chipModel;
    if (safeChip.isEmpty() && previous != null) { safeChip = previous.getChipModel(); }
    items.put(key, new InventoryItem(safeId, safeData, rssi, count, safeChip));
    totalReads += increment;
}
```

`InventoryItem` / `InventoryAdapter` 的 DiffUtil / CSV 导出**都已支持 chipModel**，
无需改动（`InventoryFragment` 约 528 行导出已含该列）。

⚠️ **切换盘点区域后建议清空列表**：key 含 `data`，同一标签在不同档位下会产生
两条记录（一条无芯片、一条有芯片），容易被误认为重复标签。
在 D.3.4 设置成功的回调里调用现有的清空逻辑即可。

### D.9 文件改动清单

**新建文件**（3个）：
1. `reader/InventoryArea.java` - 盘点区域枚举（按协议分组，PDF §3.3.8）
2. `res/layout/dialog_inventory_range.xml` - 起始地址 / 盘点长度输入弹窗（D.3.4）
3. `reader/ChipModelFormatter.java` - 芯片型号显示文案格式化（D.8.4）

**修改文件**（12个）：
1. `reader/ReaderConfiguration.java` - 新增 `inventoryArea` / `inventoryAddress` /
   `inventoryWordLen` 三个字段 + `DEFAULT_INVENTORY_WORD_LEN` 常量（D.3.1）
2. `reader/ReaderConfigCache.java` - 三个字段的 MMKV 存取（D.6）
3. `reader/UhfSdkGateway.java` - 接口新增 `setInventoryArea(area, addr, len)` /
   `getInventoryArea()`；`configureDefaultInventory` 改签名为
   `applyInventoryParams(protocol, area, addr, len)`（D.3.5 / D.3.6）
4. `reader/NativeUhfSdkGateway.java` - 实现上述方法；删除 6C 分支写死的
   `params.setValue(1, 0, 6)`，改为透传调用方传入的档位（D.3.6）
5. `reader/ReaderSessionManager.java` - 新增 `setInventoryArea(int, int, int)`；
   **两处 `configureDefaultInventory` 调用点（约 342 行切协议、约 408 行
   startInventory）都要改为透传当前配置**，否则每次开始盘点都会重置用户设置；
   所有构造 `ReaderConfiguration` 的地方补传三个新字段（setPower/setBlf/
   setSessionTarget/setQ/setInventoryMode/握手回填共 6+ 处，漏一处即被置 0）
6. `reader/ReaderHandshake.java` - 读取 area/addr/len 三值 + 容错回退（D.7）
7. `ui/adapter/InventoryAdapter.java` - 按模块类型控制 RSSI 列；
   新增 `setChipVisible()` 控制芯片列（D.8.5）
8. `ui/fragment/home/InventoryFragment.java` - 列标题改用
   `InventoryArea.of(protocol, area).getColumnHeader()`；RSSI 列可见性；
   芯片列可见性（D.8.5）
9. `ui/fragment/home/ReaderConfigFragment.java` - 新增盘点区域设置项（含二级
   地址/长度弹窗）；切协议后刷新该行取值
   （注意实际路径是 `ui/fragment/home/`，不是 `ui/fragment/config/`）
10. `reader/ReaderTag.java` - 新增 `chipModel` / `tidPrefix` 字段（D.8.2）
11. `reader/InventoryAccumulator.java` - `add()` 加「已识别型号不被空值覆盖」
    保护（D.8.6）
12. `reader/NativeUhfSdkGateway.java` - `toReaderTag()` 透传 chipModel /
    tidPrefix（D.8.2，与上面第 4 项同一文件，一并改）
    → **同时删除 `ReaderSessionManager.resolveChipModel()` 硬编码桩函数**（D.8.3）

**修改布局文件**（4个）：
1. `res/layout/inventory_item.xml` - 调整 EPC/USER/TID 显示布局
2. `res/layout/inventory_fragment.xml` - 列标题居中，添加 ID
3. `res/layout/reader_config_fragment.xml` - 添加盘点区域设置行
4. `res/layout/inventory_mask_panel.xml` - 掩码 switch 垂直居中

**新增字符串资源**（`res/values/strings.xml`）：
```xml
<string name="config_inventory_area">盘点区域</string>
<string name="config_inventory_area_epc_only">仅盘点 EPC</string>
<string name="config_inventory_area_set_failed">盘点区域设置失败: %s</string>
<!-- %1$s 档位名 / %2$d 起始地址 / %3$d 盘点长度 -->
<string name="config_inventory_area_summary">%1$s · 地址 %2$d · %3$d 字</string>
<string name="config_inventory_addr">起始地址（字）</string>
<string name="config_inventory_len">盘点长度（字，0 = 全区）</string>
<string name="config_inventory_range_hint">长度必须与标签实际长度匹配，超出会读取失败</string>
<string name="config_inventory_range_invalid">长度需在 0 - %1$d 字之间</string>
<string name="config_inventory_len_zero_title">读取全部区域</string>
<string name="config_inventory_len_zero_warning">长度设为 0 表示返回整个区域，仅 R2000 新版固件支持。USER 域缓冲上限 64 字节，标签 USER 超过 32 字节时会内存溢出，确认继续？</string>
<!-- D.8：起始地址非 0 会让芯片识别失效，在二级弹窗里提示 -->
<string name="config_inventory_addr_chip_hint">起始地址非 0 时无法识别芯片型号</string>
<!-- D.8.4：读到 TID 但芯片库无此前缀，%1$s 为 8 位十六进制前缀 -->
<string name="inventory_chip_unknown">未知(%1$s)</string>
```

⚠️ **不使用 string-array 存档位文案**。文档里各协议的档位数量不同
（6C 4 档、6B 2 档、GB/GJB 3 档），固定数组无法表达；档位名与列标题都由
`InventoryArea` 枚举提供（D.3.2）。若后续要做多语言，再把枚举里的中文换成
`@StringRes` 引用，届时 arrays.xml 仍然不需要。

### D.10 实现顺序

1. **D.1 RSSI 列动态显示**（1小时）
   - InventoryAdapter 新增 `setModuleSubtype()`（**不要改构造函数、不要重建 adapter**）
   - InventoryFragment 在 onReaderStateChanged 里调用
   - 测试 R2000、RM610、RM8011 的 RSSI 列显示

2. **D.2 标签信息列重新布局**（1.5小时）
   - 修改 inventory_item.xml 布局
   - 调整字号和间距
   - 测试长 EPC/USER/TID 显示效果

3. **D.3 盘点区域设置**（7小时，比初估多 4 小时）
   - 创建 `InventoryArea` 枚举（按协议分组：6C 4 档 / 6B 2 档 / GB·GJB 3 档，
     取值依 PDF §3.3.8）
   - 扩展 `ReaderConfiguration` 三个字段 + **补齐 ReaderSessionManager 里所有构造点**
   - Gateway 接口/实现新增 `setInventoryArea(area, addr, len)` /
     `getInventoryArea()`（D.3.5）
   - **先做 D.3.6**：`configureDefaultInventory` → `applyInventoryParams`，
     改掉两处调用点（切协议 + 每次 startInventory），否则后面的 UI 白做
   - 配置页按 `confirmAndApply` 模式添加 UI + 二级地址/长度弹窗 + len=0 风险确认
   - 测试：设置生效、**开始盘点后不被重置**、切协议档位回落、缓存是否生效

4. **D.4 列标题动态更新**（1小时）
   - InventoryFragment 监听配置与协议变化
   - 列标题取 `InventoryArea.of(protocol, area).getColumnHeader()`
   - 测试切换不同盘点区域与不同协议

5. **D.5 布局修正**（0.5小时）
   - 列标题添加 `gravity="center"`
   - 掩码 switch：`SwitchMaterial` 加 `layout_gravity="center_vertical"`，
     删除 FrameLayout 上无效的 `gravity="center"`
   - 验证视觉效果

6. **D.6 参数缓存更新**（0.5小时）
   - ReaderConfigCache 增加 area / addr / wordLen 三个键
   - 测试缓存读写

7. **D.7 初始化握手更新**（1小时）
   - ReaderHandshake 读取三值 + 非法档位回落
   - 测试初始化流程

8. **D.8 芯片型号识别**（2.5小时，需排在 D.3 之后）
   - `ReaderTag` 加 `chipModel` / `tidPrefix`，`toReaderTag()` 透传
   - 删掉 `ReaderSessionManager.resolveChipModel()` 硬编码桩函数
   - 新建 `ChipModelFormatter`（"英文名|中文名" 解析 + 未识别前缀回显）
   - `InventoryAccumulator.add()` 加「已识别型号不被空值覆盖」保护
   - 芯片列随盘点区域显隐（`setChipVisible()`，同 D.1 的机制）
   - 测试：区域含 TID 时能出型号；仅 EPC 档位下芯片列隐藏；
     未知前缀显示 `未知(XXXXXXXX)`；CSV 导出含该列

**预计总工时**：15 小时
（初估 8.5 → 盘点区域按文档实现 +4 → 补芯片型号识别 +2.5）

### D.11 验收标准

**RSSI 列动态显示**：
- 连接 R2000 模块：RSSI 列和列标题显示
- 连接 R2000Plus 模块：RSSI 列和列标题显示
- 连接 RM610 模块：RSSI 列和列标题隐藏
- 连接 RM8011 模块：RSSI 列和列标题隐藏

**标签信息列布局**：
- 24 位 EPC 完整显示（使用等宽字体）
- EPC+USER 时，USER 在第二行显示
- EPC+TID 时，TID 在第二行显示
- 仅 EPC 时，第二行隐藏

**盘点区域设置**：
- 配置页「盘点区域」行位于工作模式**上方**，点击弹出选择框
- 选项集随当前协议变化：
  - 6C：仅盘点 EPC / 盘点 EPC 和 USER / 盘点 EPC 和 TID / 盘点 EPC 和 RESERVED
  - 6B：仅盘点 UID / 盘点 UID 和 USER
  - GB·GJB：仅盘点编码区 / 盘点编码区和用户区 / 盘点编码区和标签信息区
- 默认档位为「仅盘点」（area=0）
- 选中非「仅盘点」档位时，弹出二级弹窗填起始地址与盘点长度，默认 0 / 6 字
- 盘点长度填 0 时弹出溢出风险确认框，取消则不下发
- 设置成功后 Toast 提示，配置行文案变为「档位名 · 地址 N · M 字」
- 设置后三个值都被缓存到 MMKV
- **点击「开始盘点」后，设置不被 `applyInventoryParams` 重置**（回归 D.3.6）
- 6C 下选 area=3 后切到 6B，档位自动回落为 0，UI 与设备一致

**列标题动态更新**：
- 6C 仅盘点 EPC → 列标题「EPC 号」
- 6C 盘点 EPC 和 USER → 列标题「EPC/USER」
- 6C 盘点 EPC 和 TID → 列标题「EPC/TID」
- 6C 盘点 EPC 和 RESERVED → 列标题「EPC/RESERVED」
- 6B 盘点 UID 和 USER → 列标题「UID/USER」
- 断开重连后，列标题根据缓存的盘点区域正确显示

**文档一致性核对（发版前必做，见 D.3.6）**：
- 用已知 TID/USER 内容的 6C 标签验证 area=1 返回的是 USER
- area=3（RESERVED）实测可用；若 SDK 报错则从 6C 选项集移除该档

**芯片型号识别**：
- 盘点区域含 TID（6C）且起始地址为 0 时，芯片列显示型号名
- 中文环境显示 `"英文名|中文名"` 的中文侧，英文环境显示英文侧
- 只有英文名（无 `|`）时原样显示
- 读到 TID 但芯片库无此前缀 → 显示 `未知(XXXXXXXX)`（8 位大写十六进制）
- 「仅盘点 EPC」档位下芯片列**整列隐藏**（含表头），不留 `-` 占位
- 同一标签多次上报，已识别出的型号不会被后续空值覆盖
- CSV 导出包含芯片型号列（`InventoryFragment` 约 528 行既有逻辑）
- `ReaderSessionManager.resolveChipModel()` 已删除，全局无该硬编码桩函数

**布局修正**：
- 所有列标题居中对齐
- 掩码 switch 在行内垂直居中

---

---

## 附录：已完成的轮次方案（保留备查）

> 注意：C 轮与 D 轮（2026-08-03）为**待执行**方案，不在本附录中。
> 以下 A/B/H 轮方案已执行完毕，仅作历史记录。

**B 轮方案（2026-08-01）**：
- 连接状态收敛到配置页
- 盘点页掩码进入即折叠
- 单标签掩码面板
- 配置页参数校正
- 设置项统一确认流

**A 轮方案**：
- 会话生命周期
- 前台服务统一覆盖 BLE + WiFi
- 数据通路瘦身
- 常驻监听 + 断开即停
- 全局在线 + 强确认弹窗
- 掩码整改

**H 轮方案**：
- 固定标题栏方案
- 模块设置差异矩阵
- 掩码功能实现

---

## C.11 待用户确认事项

1. **PDF 开发文档**：✅ 已读取
   - 已成功安装 poppler-utils 并读取关键章节
   - Session 参数：PDF §3.5.14（S0-S3 + Target A/B 组合）
   - Q 值参数：PDF §3.5.10（固定 Q）+ §3.5.12（动态 Q）
   - BLF 参数：PDF §3.5.4（4 档调制模式）
   - 参数说明已更新到 C.4.1 - C.4.3 章节

2. **设计文件**：✅ 已闭环 —— 用户 2026-08-03 决定本轮跳过，无需导出图
   - C.6 不列入交付范围，D 轮 UI 改动按本文档自身的布局描述实施

3. **RM610 测试设备**：
   - 需要 CMT 版本和非 CMT 版本两台设备进行真机验证
   - 如只有一种版本，请明确告知，以便调整测试策略

4. **测试标签（D.3 / D.8 都要用）**：
   - 需要一张 **TID 与 USER 内容已知**的 6C 标签，用于核对 §3.3.8 的档位含义
     （area=1 到底返回 USER 还是 TID，见 D.3.6）
   - 芯片型号验证最好准备 2-3 个不同厂商的标签（如 Impinj、复旦），
     确认 `"英文名|中文名"` 解析与未知前缀回显都正常
   - 若手上标签的 TID 前缀不在底层芯片库内，会显示 `未知(XXXXXXXX)`，
     把该前缀反馈给底层补库即可，不是 App 侧缺陷

5. **实施方案选择**：✅ 已定，codex 无需再问
   - **连接后初始化进度：完整方案（6 步分步文案）** —— 这是需求原文，不可降级为单一提示
   - **Q 值子参数暴露：简化方案（仅修正预选高亮）** —— 需求只要求
     「子参数要缓存、改 Q 时整套下发防清零」（C.5 核心需求第 4 条），
     并未要求可编辑；二级输入对话框留作后续迭代

---

## C.12 实施检查清单

执行 codex 前的准备：
- [ ] 确认 RM610 设备序列号读取方法（`ReaderModuleInfo.moduleSerial`）
- [ ] 确认 RM610 CMT 版本和非 CMT 版本的功率设置/获取 API 是否一致
- [ ] 确认 Uhf_Android 中的功率数组索引是否从 0 开始
- [ ] 已核对 5 组档位数组来自 `Uhf_Android/res/values/strings.xml`
      （`arr_power_20` / `arr_power_26` / `arr_power_0_26` / `arr_power_30` / `arr_run_30`），
      并确认 `arr_power_20` 含半 dBm 值（C.3.1）
- [ ] 检查 `ReaderConfigFragment` 中功率点击事件绑定位置
- [ ] 检查 `ReaderConfiguration` 是否需要扩展字段存储 RM610 版本信息
- [ ] 确认 RM8011 模块使用 `setMagicQuery` API 设置 Q 值
- [ ] 确认 MMKV 存储 ID 和键名命名规范（参考 `ThemeModeManager` 的 `MMKV.mmkvWithID` 用法）
- [ ] 确认设置单个参数时需要传递的完整参数结构
- [ ] 已读 `NativeUhfSdkGateway.readConfiguration()`（第 112-154 行），
      确认逐项 getter 的逻辑是**搬运**而非重写（C.5.3）
- [ ] 已确认底层无「读当前协议」接口（`Linkage` 只有 `setTagType`），
      步骤 3 不去找 getter（C.5.3）
- [ ] 已读 `ReaderConnectionDialog.update()`（第 81 行），确认 `detail` 当前被丢弃
- [ ] 已确认 `WaitDialog` 是 Builder 模式且构造器已 `setCancelable(false)`

执行后的验证：
- [ ] 编译通过，无 RM100X、MAGIC_RF、MagicRF 残留
- [ ] 参数缓存基础设施工作正常（首次连接保存，断线重连读取）
- [ ] 连接 RM610 CMT 版本设备，功率 UI 为 SeekBar，范围 0-20
- [ ] 连接 RM610 非 CMT 版本设备，功率 UI 为离散选择，8 档文案正确
- [ ] 连接 RM8011 设备，功率 UI 为离散选择，**档位随 `moduleSerial` 变化**（C.3.1）
- [ ] `RM-20dBm` 档位下 `14.5 / 15.5 / 18.5` 显示为一位小数，未被截断成整数
- [ ] 固件版本串为空 / 非数字时不崩溃，走 `19…30` 保守档位
- [ ] 未识别序列号时回退 0…20 并打 warning，功率**未被设成 0**
- [ ] 全局无 `value / 10` 的整数除法显示写法（参考工程的 bug，勿照搬）
- [ ] RM8011 设备 Q 值设置使用 `setMagicQuery` API
- [ ] 修改 Q 值时，其他 Q 参数（retryCount/toggleTarget 等）正确传递
- [ ] Session/Q 对话框预选高亮正确
- [ ] 连接后初始化进度弹窗显示并自动关闭
- [ ] 6 步文案逐字正确（正在更新设备参数/正在获取功率/正在获取射频协议/
      正在获取Session/正在获取Blf速率/正在获取Q值）
- [ ] 握手期间「取消连接」按钮不可见，返回键与点击外部均无法关闭弹窗
- [ ] **单项失败不影响其他项**：人为让某一项读取失败，
      验证其余项仍读到真实值（不是整份回退到缓存）
- [ ] `readConfiguration()` 内部已改为复用 4 个 getter，无重复读取逻辑
- [ ] 全局无 `onHandshakeProgress`、无 `new WaitDialog(` 构造器写法
- [ ] 参数获取失败时能从缓存读取，UI 正常显示
- [ ] "刷新参数"按钮功能正常，能手动重新获取参数
- [ ] 其他模块（R2000/R2000Plus）功率 UI 未受影响
- [ ] 全局无 `SharedPreferences` 新增引用（统一走 MMKV）

D 轮验证（盘点页 UI）：
- [ ] R2000/R2000Plus 显示 RSSI 列，RM610/RM8011 隐藏 RSSI 列
- [ ] 24 位 EPC 完整显示（等宽字体，不再中间截断）
- [ ] EPC + USER/TID 双行显示，仅 EPC 时第二行隐藏
- [ ] 配置页盘点区域行位于工作模式上方，6C 下弹出 4 个选项（PDF §3.3.8）
- [ ] 选项集随协议切换（6C 4 档 / 6B 2 档 / GB·GJB 3 档），越界档位自动回落
- [ ] 非「仅盘点」档位弹出起始地址 / 盘点长度二级弹窗，长度 0 有溢出风险确认
- [ ] 盘点区域三个值（area/addr/wordLen）设置成功后写入 MMKV，重连后保持
- [ ] **点击「开始盘点」后盘点区域不被重置**（`applyInventoryParams` 透传，D.3.6）
- [ ] 列标题随盘点区域切换（EPC 号 / EPC/USER / EPC/TID / EPC/RESERVED / UID…）
- [ ] 真机核对 6C area=1 返回 USER（与 jar 旧注释相反，见 D.3.6）
- [ ] 芯片型号取自底层 `InventoryData.getChipModel()`，不再用硬编码桩函数
- [ ] `ReaderSessionManager.resolveChipModel()` 已删除，全局无残留引用
- [ ] 中文环境显示 `"英文名|中文名"` 的中文侧；未知前缀显示 `未知(XXXXXXXX)`
- [ ] 「仅盘点 EPC」档位下芯片列整列隐藏（含表头）
- [ ] 已识别的芯片型号不被后续空值覆盖（`InventoryAccumulator` 保护生效）
- [ ] 所有列标题居中对齐
- [ ] 掩码 switch 垂直居中

---

## C.13 风险与降级方案

**风险 1**：RM610 非 CMT 版本功率 API 与预期不符
- **降级方案**：两种版本统一使用 0-20 dBm 整数范围，不做分档处理

**风险 1b**：手上的 RM8011 序列号不在 C.3.1 的 6 个分支内
- **不是缺陷**：`levels()` 已有 0…20 兜底 + warning 日志，功能不受影响
- **处置**：把日志里的 `moduleSerial` 反馈回来，补一条分支即可
- **不要**为了「凑上」而放宽 `contains` 判定（比如把 `RM-20dBm` 改成 `20dBm`），
  参考工程的 `30dBm` + `V1.3.1` 分支已经是这种放宽的产物，容易误命中

**风险 2**：Session/Q 参数理解有误
- **降级方案**：保持当前实现，仅修正预选高亮，等用户反馈后调整
- **注意**：RM8011 模块的 Q 值设置方式与其他模块不同，需要特殊处理

**风险 3**：连接后初始化进度回调改动影响稳定性
- **不可降级为单阶段提示**（6 步文案是需求原文）
- **缓解方式**：进度只走现有 `publish` + state message 通道（C.5.3），
  不新增 listener、不改握手控制流；逐项读取本身各自 try/catch，
  比原来的原子 `readConfiguration()` 更不容易整体失败

**风险 4**：设计文件无法查看
- **降级方案**：跳过 C.6 —— ✅ **已采纳**，用户确认本轮不提供导出图
- D 轮 UI 改动不依赖设计稿，按 D.2 / D.5 的布局描述实施

---

## C.14 交付物

执行完成后提交：
1. **代码改动**：按 C.7 文件变更清单修改
2. **本文档**：`IMPLEMENTATION_PLAN.md` 更新为 C 轮方案
3. **测试报告**（建议格式）：
   ```
   ### 模块重命名测试
   - RM610 设备：✅ 设备信息显示 "RM610"
   - RM8011 设备：✅ 设备信息显示 "RM8011"
   - 编译检查：✅ 无 MAGIC_RF/MagicRF 残留
   
   ### RM610 功率测试
   - 设备型号：RM610
   - 序列号：RMxxxxCMTxxx（或非 CMT 序列号）
   - 功率 UI 类型：SeekBar / 离散选择框
   - 测试档位：0/10/20 dBm（CMT）或 8 档索引（非 CMT）
   - 结果：✅ 通过 / ❌ 失败（附错误日志）
   
   ### RM8011 功率测试（C.3 档位校准）
   - 设备型号：RM8011
   - moduleSerial：（原样贴出，用于判定命中哪组档位）
   - moduleVersion：（原样贴出）
   - 命中档位：RM-20dBm 6 档 / RM-26dBm·V1.0 12 档 / RM-26dBm 27 档
               / RM-30dBm(<3.80) 12 档 / RM-30dBm(>=3.80) 13 档 / 未识别回退 21 档
   - 半 dBm 显示：14.5 / 15.5 / 18.5 是否保留一位小数 ✅ / ❌
   - 异常固件串（手工改成空串）：不崩溃且走 19…30 ✅ / ❌
   - 结果：✅ 通过 / ❌ 失败（附错误日志）
   
   ### RM8011 Q 值测试
   - 设备型号：RM8011
   - Q 值设置方式：使用 setMagicQuery API
   - 测试 Q 值：4/8/12
   - 结果：✅ 通过 / ❌ 失败（附错误日志）
   
   ### Session/Q 预选测试
   - 当前 Session：S1·A
   - 对话框打开：第 2 项高亮 ✅
   - 当前 Q 值：固定 Q4
   - 对话框打开：第 5 项高亮 ✅
   
   ### 连接后初始化（6 步）
   - 握手期间弹窗：✅ 显示，且取消按钮不可见
   - 步骤文案：正在更新设备参数 → 正在获取功率 → 正在获取射频协议
               → 正在获取Session → 正在获取Blf速率 → 正在获取Q值
   - 逐项容错：人为让「功率」读取失败 → 功率回退缓存值，
               后续 Session/Blf/Q 仍读到真实值 ✅
   - 自动关闭：✅ 配置加载后关闭
   - 手动刷新：✅ 点击刷新按钮参数重新获取
   ```

---

**C + D 轮方案制定完成，待执行。**  
**待用户确认事项**：
1. ✅ PDF 开发文档已成功读取，Session/Q/BLF 参数说明已更新到方案中
2. ✅ MagicRF → RM8011 重命名需求已纳入方案
3. ✅ RM8011 的 Q 值特殊 API 处理已明确
4. ✅ 参数持久化（MMKV）和容错机制已设计完成
5. ✅ 手动刷新参数按钮已加入方案
6. ✅ 盘点页 UI 优化（D 轮）已加入方案
7. ⏭️ 设计文件对照（C.6）已确认跳过，不再等待导出图
8. 是否有 RM610 CMT 和非 CMT 两种设备可供测试？（唯一未闭环项，仅影响 C.2 真机验证）

**总执行顺序**：
```
C 轮（参数管理）
  C.1   模块重命名（RM100X→RM610, MagicRF→RM8011）
  C.5.1 参数缓存基础设施（MMKV）
  C.2   RM610 功率分档
  C.3   其他模块功率校准（RM8011 按序列号分 5 组档位，本轮真做）
  C.4   Session/Q/BLF 参数 UI
  C.5.3 拆原子 readConfiguration（4 个 getter + 逐项容错）← 前置
  C.5.2 初始化进度弹窗 + 手动刷新
  C.5.4 进度宿主改 ReaderConnectionDialog（含隐藏取消按钮）
  （C.6 设计文件对照已跳过，本轮不执行）

D 轮（盘点页 UI）
  D.1   RSSI 列动态显示
  D.2   标签信息列重新布局
  D.3.6 先改 configureDefaultInventory → applyInventoryParams（前置项）
  D.3   盘点区域设置（InventoryArea，按协议分档，PDF §3.3.8）
  D.4   列标题动态更新
  D.5   布局修正
  D.6   参数缓存扩展 area/addr/wordLen
  D.7   初始化握手补充 area/addr/wordLen
  D.8   芯片型号识别（依赖 D.3 的盘点区域，必须排在其后）
```

**预计总工时**：C 轮约 21 小时（原 16 + C.5.3 拆原子读取 2 + C.5.4 弹窗宿主 1
+ C.3 功率档位校准 2） + D 轮约 15 小时 ≈ 36 小时

**关键改进点总结**：
1. **严格初始化顺序**：先读取模块信息（确定类型）→ 再根据类型获取参数
2. **参数本地持久化**：所有参数按模块类型分别存储到 **MMKV**（与项目现有 `ThemeModeManager`、`InitManager` 一致，禁用 SharedPreferences）
3. **完整参数保存**：包括动态 Q 的所有子参数（startQ/minQ/maxQ/retryCount/thresholdMultiplier/toggleTarget）
4. **安全的参数设置**：修改单个参数时，从缓存读取其他参数组合完整后再设置
5. **容错回退机制**：参数获取失败 → 使用缓存值 → 无缓存则使用模块默认值。
   为此必须**拆掉原子的 `readConfiguration()`**（C.5.3）：它现在每个 SDK 调用
   都 `check(...)`，第一个失败就抛异常导致所有参数一起回退，
   与「某一项失败不打断流程」的需求直接冲突
6. **手动刷新功能**：配置页增加"刷新参数"按钮，允许用户手动重新获取所有参数
7. **模块能力差异化 UI**：RSSI 列仅 R2000 系列显示，盘点列标题随盘点区域动态变化
8. **盘点区域按文档实现**：选项集随协议切换（6C 4 档 / 6B 2 档 / GB·GJB 3 档），
   含 startAddr / wordLen 两个子参数与 wordLen=0 的溢出风险确认；
   并修掉 `startInventory()` 每次重置盘点区域的既有缺陷（D.3.6）
9. **芯片型号识别接上底层库**：`InventoryData.chipModel` / `tidPrefix` 原本被
   `toReaderTag()` 丢弃，App 用的是只认两个前缀的硬编码桩函数；本轮打通
   SDK → ReaderTag → 累积器 → 列表/CSV，并按盘点区域控制芯片列显隐（D.8）

---

## 附录：PDF 文档关键章节摘要

**已读取章节**：
- §3.3.3：setRFModuleType（R2000 = 0 / RM8011 / RM70XX）
- §3.3.7：setTagType（0 = 6C, 1 = 6B, 2 = GJB 7377, 3 = GB/T 29768）
- §3.3.8：**setInventoryArea(area, startAddr, wordLen)** —— 本轮 D.3 的依据
- §3.3.9：getInventoryArea —— 读回上述三个值
- §3.3.10-3.3.11：盘点过滤门限（filerFlag / maxCacheTimeMs / 进出场监控时长）
- §3.4.1：getModuleSerialNumber（建议 64 字节缓冲）
- §3.4.3：startInventory(mode, maskFlag) —— SINGLE(0) / HIGH_PERFORMANCE(1) /
  LOWPOWER(2)；maskFlag bit0 = Select、bit1 = Post；**单次与低功耗模式仅
  R2000 及 RM70XX 转接的 R2000 有效，RM801X 无效**
- §3.4.x：标签读写、锁定、销毁等操作 API（pages 28-32）
- §3.5.4：设置调制模式（BLF）- 4 档固定值（page 43）
- §3.5.8-3.5.9：设置/获取算法（固定算法 vs 动态算法）（pages 44-45）
- §3.5.10：设置固定算法信息（qValue, retryCount, toggleTarget, repeatUntilNoTags）（page 45）
- §3.5.12：设置动态算法信息（startQValue, minQValue, maxQValue, thresholdMultiplier, retryCount, toggleTarget）（page 46）
- §3.5.14：设置 Query 信息（Session S0-S3 + Target A/B）（pages 47-48）
- §3.6.2：RM8011（MagicRF）设置 Query（DR, M, TRext, Sel, Session, Target, Q）（page 50）

**§3.3.8 setInventoryArea 原文要点**（D.3 直接依据）：

| 协议 | area 取值 |
|---|---|
| 6C | 0 = 仅盘点 EPC；1 = 盘点 EPC 和 USER；2 = 盘点 EPC 和 TID；3 = 盘点 EPC 和 RESERVED |
| 6B | 0 = 仅盘点 UID；1 = 盘点 UID 和 USER |
| GB / GJB | 0 = 仅盘点编码区；1 = 盘点编码区和用户区；2 = 盘点编码区和标签信息区 |

- `startAddr`：起始地址，**对「仅盘点」档不起作用**
- `wordLen`：盘点长度。为 `0` 时返回整个区域，但：
  - USER 域最大 buffer 为 **64 字节**，标签 USER 域超过 32 字节时**不要使用**，
    否则造成内存溢出
  - 该「读全区」能力**仅 R2000 新版本 firmware 支持**
- 设置长度必须与标签实际长度匹配（标签 USER 只有 2 word 却要读 4 word 会出错）

**关键发现**：
0. **盘点区域（§3.3.8）的档位含义与 `app/libs/uhf.jar` 内 `InventoryParams`
   的旧注释相反**（jar 写 1 = EPC_TID、2 = EPC_USER，且无 RESERVED 档）。
   `InventoryParams` 仅把 int 透传给 so 库，语义由 native 层决定，故按文档实现，
   并在联调阶段用真机核对一次（详见 D.3.6）。
1. BLF 调制模式为固定 4 档索引（0-3），对应不同的调制方式和频率
2. Q 值分为固定算法和动态算法两套 API，参数字段已确认
3. Session 和 Target 是独立参数，组合显示为 "S0·A" 至 "S3·B" 共 8 种
4. **RM8011 模块有独立的 setQuery API**，参数包含完整的 DR/M/TRext/Sel/Session/Target/Q
   - RM8011 使用 `linkage.set_Query(DR, M, TRext, Sel, session, target, qValue)`
   - 其他模块（R2000/R2000Plus/RM610）使用 `linkage.Radio_SetSingulationAlgorithm*` 系列 API
   - **在 UI 层需要根据模块类型调用不同的 API**

---

## 附录：关键代码参考

### NativeUhfSdkGateway 中的 Q 值设置方法

```java
// 通用模块（R2000/R2000Plus/RM610）的 Q 值设置
@Override
public int setQ(boolean dynamic, int qValue, int minQValue, int maxQValue,
        int retryCount, int thresholdMultiplier, int toggleTarget, int repeatUntilNoTags) {
    int status = linkage.Radio_setCurrentSingulationAlgorithm(dynamic ? 1 : 0);
    if (status != STATUS_OK) { return status; }
    if (dynamic) {
        DynamicQParams params = new DynamicQParams();
        params.setValue(qValue, minQValue, maxQValue, retryCount, toggleTarget,
                thresholdMultiplier);
        return linkage.Radio_SetSingulationAlgorithmDyParameters(params);
    }
    FixedQParams params = new FixedQParams();
    params.setValue(qValue, retryCount, toggleTarget, repeatUntilNoTags);
    return linkage.Radio_SetSingulationAlgorithmFixedParameters(params);
}

// RM8011 模块的 Q 值设置（使用独立 API）
@Override
public int setMagicQuery(int session, int target, int qValue) {
    Parameters current = new Parameters();
    int status = linkage.get_Query(current);
    if (status != STATUS_OK) { return status; }
    // 保留现有的 DR/M/TRext/Sel 参数，只更新 session/target/qValue
    return linkage.set_Query(current.getDR(), current.getM(), current.getTRext(),
            current.getSel(), session, target, qValue);
}
```

### ReaderConfigFragment 中需要的模块判断逻辑

```java
// Q 值设置时的模块判断
private void handleQValueChange(boolean dynamic, int qValue) {
    ModuleSubtype subtype = readerState.getModuleSubtype();
    
    if (subtype == ModuleSubtype.RM8011) {
        // RM8011 不支持动态 Q，只能设置固定 Q 值
        // 需要同时传入当前的 session 和 target
        int session = readerConfiguration.session;
        int target = readerConfiguration.target;
        handleResult(
            session.setMagicQuery(session, target, qValue),
            R.string.config_q_set_failed
        );
    } else {
        // R2000/R2000Plus/RM610 使用通用的 setQ API
        handleResult(
            session.setQ(dynamic, qValue, minQ, maxQ, ...),
            R.string.config_q_set_failed
        );
    }
}
```
