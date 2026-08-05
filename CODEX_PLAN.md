# UhfRemote 缺陷修复方案（codex 可执行）

> 参考实现：`/Users/lei/Projects/Uhf_Android`（同一套 `uhf.jar` SDK，掩码逻辑已验证可用）
> 弹窗参考：`/Users/lei/Downloads/AndroidProject-master`（本地框架的上游）
> 约定：代码标识符用英文，注释用中文；改动完成后必须能通过 `./gradlew :app:assembleDebug` 与 `:app:testDebugUnitTest`。

## 执行顺序

任务 1 与任务 2 共用 `UhfSdkGateway` 接口改动，必须一起完成；任务 5 也会动同一个接口，建议
按 `1 → 2 → 5 → 3 → 4 → 6` 的顺序推进，每完成一个任务编译一次。

---

## 任务 1：盘点掩码完全不生效

### 根因（已定位，四处叠加）

1. **缺少 Query Sel 设置（主因）**
   `app/src/main/java/com/leo/remote/reader/NativeUhfSdkGateway.java:226` 的
   `applyInventoryMask()` 只调用了 `set18K6CSelectCriteria()`，从未设置 `TagGroup.selected`。
   Gen2 协议里 Select 命令只负责把匹配标签的 **SL flag** 置位，真正决定"只盘点 SL 标签"的是
   Query 命令的 **Sel** 字段。Sel 保持默认 0（All）时，模块对所有标签一律响应，
   所以掩码写进去了但盘点结果不变。
   参考实现 `Uhf_Android/app/src/main/java/com/uhf/android/rfid/manager/InventoryManger.java`
   的 `setSelectCriteriaMask()` 明确分两步：先 `Radio_GetQueryTagGroup` → `selected = 2`
   → `Radio_SetQueryTagGroup`，再写 `SelectCriteria`。

2. **`startInventory` 的掩码开关恒为 0**
   `app/src/main/java/com/leo/remote/reader/ReaderSessionManager.java:486`
   写死 `gateway.startInventory(inventoryMode, 0)`。
   `uhf.jar` 中 `com/uhf/linkage/Linkage.java:247` 的 javadoc 说明第二个参数
   `flag`：`0` = 不启用掩码盘点，`bit0` = Select 掩码，`bit1` = Post 掩码。
   参考实现 `Uhf_Android/.../rfid/inventory/InventoryFragment.java:225` 是
   `StartInventory(mode, inventoryMaskStatue.isChecked() ? 1 : 0)`。

3. **写 `SelectCriteria` 前没有回读**
   参考实现先 `get18K6CSelectCriteria()` 再改字段（read-modify-write），
   UhfRemote 直接 `new SelectCriteria()` 覆盖，`jq` 字段从未赋值。

4. **`maskData` 长度不固定 64**
   `NativeUhfSdkGateway.java` 用 `Arrays.copyOf(mask, Math.max(64, mask.length))`，
   掩码超过 64 字节时会写出比结构体更长的数组。`com/uhf/structures/SelectCriteria.java`
   声明的是 `public byte[] maskData = new byte[64];`，必须固定 64。

### 修改清单

**1.1 `app/src/main/java/com/leo/remote/reader/UhfSdkGateway.java`**

掩码相关方法补一个 `ModuleSubtype` 参数（RM8011 与 RM70XX 系走不同的 Query 接口）：

```java
int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype, InventoryMaskConfig config);
int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype);
int setTargetMask(TagProtocol protocol, ModuleSubtype subtype, ReaderTag tag);
int clearTargetMask(TagProtocol protocol, ModuleSubtype subtype);
```

**1.2 `app/src/main/java/com/leo/remote/reader/NativeUhfSdkGateway.java:226`**

`applyInventoryMask` 改成"两步 + 回读"，并抽出两个私有辅助方法：

```java
/** 应用盘点掩码：第一步开 Query 的 Sel 过滤，第二步写 SelectCriteria */
@Override
public int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype, InventoryMaskConfig config) {
    // 6B 分支保持原样，只有 6C/GB/GJB 走下面的流程
    int status = setSelectEnabled(subtype, true);
    if (status != 0) {
        return status;
    }
    // 回读后再改，避免把模块里其他字段清零
    SelectCriteria criteria = new SelectCriteria();
    status = linkage.get18K6CSelectCriteria(criteria);
    if (status != 0) {
        return status;
    }
    criteria.selectorIdx = 0;
    criteria.status = 1;
    criteria.bank = config.bank;
    criteria.offset = ProtocolEncoding.encodeMaskOffset(protocol, config.offsetBits);
    criteria.length = config.lengthBits;
    criteria.session = 4;   // 4 = SL flag，与参考实现一致
    criteria.jq = 0;
    criteria.action = 0;
    criteria.maskData = toFixedMaskData(config.mask);
    return linkage.set18K6CSelectCriteria(criteria);
}
```

新增两个私有方法（`setSelectEnabled` 是任务 1 和任务 2 共用的核心）：

```java
/** 开关 Query 命令的 Sel 过滤；enabled=true 时只盘点 SL 置位的标签 */
private int setSelectEnabled(ModuleSubtype subtype, boolean enabled) {
    int sel = enabled ? 2 : 0;
    if (subtype == ModuleSubtype.RM8011) {
        // RM8011 走 Parameters/set_Query，Sel 在 Parameters 里
        Parameters current = new Parameters();
        int status = linkage.get_Query(current);
        if (status != 0) {
            return status;
        }
        // 参数顺序与本类 setMagicQuery 保持一致
        return linkage.set_Query(current.getDR(), current.getM(), current.getTRext(),
                sel, current.getSession(), current.getTarget(), current.getQ());
    }
    TagGroup tagGroup = new TagGroup();
    int status = linkage.Radio_GetQueryTagGroup(tagGroup);
    if (status != 0) {
        return status;
    }
    tagGroup.selected = sel;
    return linkage.Radio_SetQueryTagGroup(tagGroup);
}

/** maskData 必须固定 64 字节，与 SelectCriteria 结构体一致 */
private static byte[] toFixedMaskData(byte[] mask) {
    byte[] data = new byte[64];
    if (mask != null) {
        System.arraycopy(mask, 0, data, 0, Math.min(mask.length, 64));
    }
    return data;
}
```

> 注意：`set_Query` / `Parameters` 的 getter 名称以 `NativeUhfSdkGateway.setMagicQuery`
> （约在 `NativeUhfSdkGateway.java:217`）现有写法为准，不要凭记忆写，照抄它的取值顺序。
> `setTargetMask` 同样要在写 `SelectCriteria` 前调用 `setSelectEnabled(subtype, true)`。

**1.3 `app/src/main/java/com/leo/remote/reader/ReaderSessionManager.java`**

- 新增字段记录掩码是否已下发：

```java
// 掩码是否已下发到模块，决定 startInventory 的 maskFlag
private boolean inventoryMaskApplied;
```

- `applyInventoryMask`（约 `:507`）成功后置 `inventoryMaskApplied = true`；
  `clearInventoryMask`（约 `:523`）成功后置 `false`。
- 所有 mask 相关调用补上 `subtype` 实参（用 session 里已有的当前模块子类型字段）。
- `ReaderSessionManager.java:486` 改为：

```java
// maskFlag：bit0 = Select 掩码，掩码未下发时必须传 0
int maskFlag = inventoryMaskApplied ? 1 : 0;
status = gateway.startInventory(inventoryMode, maskFlag);
```

**1.4 `app/src/test/java/com/leo/remote/reader/ReaderHandshakeTest.java:34`**

`FakeGateway` 的四个 mask 方法签名同步加 `ModuleSubtype` 参数，否则编译失败。

### 验证

1. 连接设备，设置 EPC bank / offset 32 / length 16 / 掩码值取某标签 EPC 前 4 位十六进制，
   点"应用掩码"后开始盘点：只应出现该标签。
2. 取消掩码后重新盘点：全部标签恢复出现（这条同时验证任务 2）。
3. 若过滤结果**相反**（匹配的标签消失、其他标签出现），把 `setSelectEnabled` 里的
   `sel = 2` 改成 `3` 再试 —— Gen2 里 2 与 3 分别对应 `~SL` 与 `SL`，
   不同模块固件实现有差异，参考实现用的是 2，先按 2 走。

---

## 任务 2：取消掩码时还原 session 等参数

### 根因

`NativeUhfSdkGateway.java:277` 的 `clearTargetMask` 直接写
`set18K6CSelectCriteria(new SelectCriteria(0))`，把 `session`、`jq`、`action`、`bank`、
`offset`、`length` 全部清零，而且**从未还原 `TagGroup.selected`**。

而 `NativeUhfSdkGateway.java:191` 的 `setQueryGroup` 是 read-modify-write，会**保留**
`selected`。于是取消掩码后模块状态变成"`selected` 仍为 2（只盘 SL 标签）+ SelectCriteria 全零
（没有任何标签被置 SL）"，后续盘点、读写、锁定一个标签都读不到 —— 这正是用户说的"影响其他操作"。

### 修改清单

**2.1 `NativeUhfSdkGateway.java`：加入下发前快照**

```java
// 掩码下发前的 Query Sel 快照，取消掩码时还原，避免影响读写/锁定等其他操作
private Integer savedSelected;
```

在 `setSelectEnabled(subtype, true)` 之前先把当前值存起来（只在 `savedSelected == null`
时保存，避免连续两次应用掩码把快照覆盖成 2）：

```java
/** 保存当前 Sel 值，仅首次生效 */
private void saveSelectedIfAbsent(ModuleSubtype subtype) {
    if (savedSelected != null) {
        return;
    }
    if (subtype == ModuleSubtype.RM8011) {
        Parameters current = new Parameters();
        if (linkage.get_Query(current) == 0) {
            savedSelected = current.getSel();
        }
        return;
    }
    TagGroup tagGroup = new TagGroup();
    if (linkage.Radio_GetQueryTagGroup(tagGroup) == 0) {
        savedSelected = tagGroup.selected;
    }
}
```

**2.2 `clearInventoryMask` / `clearTargetMask` 改为"先还原 Sel，再关 SelectCriteria"**

```java
/** 取消掩码：还原 Query Sel，并把 SelectCriteria 置为不启用 */
@Override
public int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype) {
    int restore = savedSelected != null ? savedSelected : 0;
    int status = setSelectValue(subtype, restore);
    savedSelected = null;
    if (status != 0) {
        return status;
    }
    SelectCriteria criteria = new SelectCriteria();
    status = linkage.get18K6CSelectCriteria(criteria);
    if (status != 0) {
        return status;
    }
    criteria.selectorIdx = 0;
    criteria.status = 0;   // 0 = 不启用
    criteria.session = 0;
    criteria.jq = 0;
    criteria.action = 0;
    return linkage.set18K6CSelectCriteria(criteria);
}
```

**2.3 把 `setSelectEnabled` 重构成 `setSelectValue(ModuleSubtype, int sel)`**

任务 1 里的 `setSelectEnabled(subtype, true)` 等价于 `setSelectValue(subtype, 2)`，
取消时传还原值。两条路径共用同一个写入方法，避免逻辑分叉。

**2.4 `ReaderSessionManager.java:602` `withTargetMask` 的 `finally`**

现状：`finally` 里只调 `clearTargetMask`，把模块恢复成"无掩码"。如果用户此前已经应用了盘点掩码，
一次读写操作就会把盘点掩码悄悄清掉。改为按 `inventoryMaskApplied` 决定收尾动作：

```java
} finally {
    if (inventoryMaskApplied) {
        // 读写用的临时掩码结束后，恢复用户设置的盘点掩码
        applyMaskInternal(protocol, subtype, currentInventoryMask);
    } else {
        gateway.clearTargetMask(protocol, subtype);
    }
}
```

需要在 session 里保存最近一次成功下发的 `InventoryMaskConfig`（字段
`currentInventoryMask`），`clearInventoryMask` 成功后置 null。

### 验证

1. 应用掩码 → 取消掩码 → 直接读某标签 EPC：能读到（回归前读不到）。
2. 应用掩码 → 对某标签做一次读操作 → 继续盘点：掩码仍然生效。
3. 不应用掩码 → 读某标签 → 盘点：全部标签可见。

---

## 任务 3：点击盘点列表 item 填充掩码时应停止盘点

### 根因

`app/src/main/java/com/leo/remote/ui/fragment/home/InventoryFragment.java:265`
的 `fillMaskFromItem(int bank, String hexValue)` 只填表单并展开面板，没有停止盘点。
盘点持续刷新时，用户刚填好的地址/长度会被后续操作和列表滚动干扰，掩码也无法在盘点中途生效。

### 修改清单

`InventoryFragment.java:265`，在填充表单之前先停盘点：

```java
private void fillMaskFromItem(int bank, String hexValue) {
    // 填充掩码前先停止盘点，否则掩码无法在本轮盘点中生效
    if (session.isInventoryRunning()) {
        session.stopInventory();
    }
    // ...原有填充逻辑
}
```

方法名以 `ReaderSessionManager` 现有的运行状态查询/停止方法为准
（`stopInventory` 见 `ReaderSessionManager.java:728` 附近的 `stopInventoryInternal` 的公开入口）。

### 验证

盘点进行中点击列表 item，选择"填充掩码"：盘点按钮回到"开始"状态，表单已填好。

---

## 任务 4：盘点区域的地址/长度弹窗不规范

### 根因

`app/src/main/java/com/leo/remote/ui/fragment/home/ReaderConfigFragment.java:555`
的 `showInventoryRangeDialog(InventoryArea)` 用的是 Material 的 `MaterialAlertDialogBuilder`
+ `R.layout.dialog_inventory_range`（`TextInputLayout` / `TextInputEditText`），
与项目其余弹窗（`com.leo.remote.ui.dialog.common.StyleDialog` 体系）风格、圆角、按钮布局都不一致。

本地框架现有 `MessageDialog` / `WaitDialog`，**没有输入类弹窗**，需要从上游框架移植：
`/Users/lei/Downloads/AndroidProject-master/app/src/main/java/com/hjq/demo/ui/dialog/common/InputDialog.java`
（配套 `app/src/main/res-common/layout/input_dialog.xml`、
`res-common/drawable/dialog_input_bg.xml`）。上游 `InputDialog` 只有单个输入框，
本需求要地址 + 长度两个，所以移植为一个双输入框的新弹窗。

### 修改清单

**4.1 新增 `app/src/main/res/drawable/dialog_input_bg.xml`**

从上游 `res-common/drawable/dialog_input_bg.xml` 移植（输入框底色 + 圆角）。

**4.2 新增 `app/src/main/res/layout/dialog_inventory_range_input.xml`**

参照上游 `input_dialog.xml` 的结构，两组"标签 + RegexEditText"竖排，
外层不带标题栏和按钮（标题与确定/取消由 `StyleDialog` 提供）：

```xml
<!-- 起始地址 -->
<com.leo.remote.widget.view.RegexEditText
    android:id="@+id/et_inventory_range_addr"
    android:background="@drawable/dialog_input_bg"
    android:inputType="number"
    app:regexType="number" />
<!-- 长度 -->
<com.leo.remote.widget.view.RegexEditText
    android:id="@+id/et_inventory_range_len"
    ... />
```

外加一个 `tv_inventory_range_recommendation`（推荐值提示），沿用原
`dialog_inventory_range.xml` 里的同名文案逻辑。

> `RegexEditText` 的实际包名以 `library:customWidget` 中的类为准，
> 参照项目里已在用的 `IpAddressInputView` 的 XML 引用写法照抄。

**4.3 新增 `app/src/main/java/com/leo/remote/ui/dialog/common/InventoryRangeDialog.java`**

继承 `StyleDialog.Builder`，照抄
`app/src/main/java/com/leo/remote/ui/dialog/common/MessageDialog.java` 的
`@SingleClick onClick` + `performClickDismiss()` + `OnListener` 写法：

```java
/** 盘点区域的起始地址与长度输入弹窗 */
public final class InventoryRangeDialog {

    public static final class Builder extends StyleDialog.Builder<Builder> {

        private final RegexEditText addrView;
        private final RegexEditText lengthView;
        private final TextView hintView;
        private OnListener listener;

        public Builder(Context context) {
            super(context);
            setCustomView(R.layout.dialog_inventory_range_input);
            addrView = findViewById(R.id.et_inventory_range_addr);
            lengthView = findViewById(R.id.et_inventory_range_len);
            hintView = findViewById(R.id.tv_inventory_range_recommendation);
        }

        public Builder setAddress(int address) { ... }
        public Builder setLength(int length) { ... }
        public Builder setHint(CharSequence hint) { ... }
        public Builder setListener(OnListener listener) { ... }

        @Override
        protected void onClick(View view) {
            // 确定：校验非空并回调；取消：直接关闭
        }

        public interface OnListener {
            void onConfirm(BaseDialog dialog, int address, int length);
            default void onCancel(BaseDialog dialog) {}
        }
    }
}
```

校验规则沿用现有 `showInventoryRangeDialog` 里的逻辑（空值、非法数字、上下限提示），
提示统一走项目现有的 toast 封装，不要新造。

**4.4 改造 `ReaderConfigFragment.java:555`**

`showInventoryRangeDialog(InventoryArea)` 内部替换为
`new InventoryRangeDialog.Builder(getAttachActivity())...show()`，
确认回调里保留原来的 `confirmAndApply` 调用链，不改业务语义。

**4.5 删除 `app/src/main/res/layout/dialog_inventory_range.xml`**

确认全局无其他引用后删除（`ReaderConfigFragment` 是唯一使用方）。
如果 Material 的 `TextInputLayout` 在删除后成为未使用依赖，**不要**动 `build.gradle`，
其他界面可能还在用。

### 验证

选择盘点区域 → 弹窗样式与 `MessageDialog` 一致（同样的圆角、标题、底部双按钮），
输入非数字被拦截，确定后配置正常下发。

---

## 任务 5：工作模式（盘点模式）设置不生效

### 根因（三处）

1. **模式没有持久化**
   `ReaderSessionManager.java:376` 的 `setInventoryMode` 只改了内存字段并
   `notifyConfiguration()`，**没有调用 `configCache.saveConfiguration(...)`**；
   `notifyConfiguration()`（约 `:912`）也只是重建配置对象通知观察者。
   而 `ReaderHandshake.java:62,140` 的 `readConfigurationStepwise` 读的是
   `fallback.inventoryMode`（即 cache 里的值），刷新配置或重连后就回到旧值，
   表现为"设了但没生效"。唯一写 cache 的路径是 `updateConfiguration`（约 `:823`）。

2. **模式 0（单次盘点）看起来完全没反应**
   `startInventory(0, flag)` 下发后模块只盘一轮就自己停了，但 UhfRemote 从未注册
   `OnInventoryStopListener`（`uhf.jar` 中 `com/uhf/structures/OnInventoryStopListener.java`
   的 `getInventoryStopReport(int status)`，注册入口 `Linkage.java:96`
   `setOnInventoryStopListener`）。App 里 `inventoryRunning` 一直是 `true`，
   按钮停在"停止"，用户以为模式没切过去。

3. **模式 2（库控持续盘点）缺少低功耗调度参数**
   `Linkage.java:271` 的 `setLowpowerScheduler(LowpowerParams)` 只在 mode 2 下有意义，
   UhfRemote 从未调用。参考实现
   `Uhf_Android/.../rfid/inventory/InventoryFragment.java` 的 `setLowPower()` 里：
   先 `antennaPorts.setDwellTime(30)` 并 `setAntennaPort(...)`，再下发
   `highPerformanceTime = 0, inventoryOnTime = 30, inventoryOffTime = 100`。
   不下发时模块按默认值跑，与模式 1 无区别。

   另外 `NativeUhfSdkGateway.java:118` 的 `readConfiguration` 把 inventoryMode
   写死成 `1`（只影响单参数的 handshake 重载，属于潜在坑，一并修掉）。

### 修改清单

**5.1 `ReaderSessionManager.java:376`**

```java
public void setInventoryMode(int mode) {
    this.inventoryMode = mode;
    // 必须落盘，否则刷新配置/重连后会被 cache 里的旧值覆盖
    configCache.saveConfiguration(currentSubtype, buildConfigurationSnapshot());
    notifyConfiguration();
}
```

复用 `notifyConfiguration()` 里已有的配置对象构造逻辑抽成
`buildConfigurationSnapshot()`，两处共用，避免字段漏拷。

**5.2 注册盘点停止回调**

在 `ReaderSessionManager.java:135` 现有 `gateway.setInventoryListener(...)` 旁边补：

```java
// 模块自行结束盘点（单次模式、达到次数上限等）时同步 UI 状态
gateway.setInventoryStopListener(status -> handleInventoryStopped(status));
```

`UhfSdkGateway` 增加 `void setInventoryStopListener(InventoryStopListener listener)`，
`NativeUhfSdkGateway` 里转成 SDK 的 `OnInventoryStopListener`；
`handleInventoryStopped` 复位 `inventoryRunning` 并走现有的状态通知路径
（不要直接改 UI，沿用 `ReaderObserver` 回调）。

**5.3 模式 2 下发低功耗参数**

`UhfSdkGateway` 增加：

```java
int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime, int inventoryOffTime);
```

`NativeUhfSdkGateway` 实现：

```java
/** 下发低功耗调度参数，仅 mode 2（库控持续盘点）有效 */
@Override
public int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime, int inventoryOffTime) {
    LowpowerParams params = new LowpowerParams();
    params.setValue(highPerformanceTime, inventoryOnTime, inventoryOffTime);
    return linkage.setLowpowerScheduler(params);
}
```

`ReaderSessionManager.java:486` 启动盘点前按模式分支：

```java
if (inventoryMode == 2) {
    // 库控持续盘点需要先下发低功耗调度，否则与模式 1 无差别
    gateway.setLowPowerScheduler(0, 30, 100);
}
status = gateway.startInventory(inventoryMode, maskFlag);
```

`setValue` 的参数顺序以 `com/uhf/structures/LowpowerParams.java` 实际声明为准
（`highPerformanceTime, inventoryOnTime, inventoryOffTime`）。

**5.4 `ReaderConfigFragment.java:877` `confirmAndApply` 去掉工作模式的短路**

现状 `:893-896` 对工作模式特殊处理：`action.get(); toast(config_work_mode_deferred_hint); return;`，
即只执行动作、不走后续确认与状态刷新。改为与其他配置项一致地走完整流程，
并删除 `config_work_mode_deferred_hint` 这条字符串（确认无其他引用）。

**5.5 `NativeUhfSdkGateway.java:118`**

`readConfiguration` 里写死的 `inventoryMode = 1` 改为从入参/cache 的 fallback 取值，
与 `ReaderHandshake` 的双参数重载行为保持一致。

### 验证

1. 设为"单次盘点"→ 开始盘点：盘一轮后按钮自动回到"开始"。
2. 设为"库控持续盘点"→ 开始盘点：能持续出标签，日志里有低功耗参数下发记录。
3. 任意切换模式后，下拉刷新配置 / 断开重连：模式保持为刚设置的值（回归前会跳回旧值）。

---

## 任务 6：右上角连接状态下不再显示设备名称

### 根因

不是缺陷，是需求变更。`reader_config_fragment.xml:66` 的
`tv_config_connection_target` 位于 `tv_config_status` 状态 chip 下方，
由 `ReaderConfigFragment.bindConnectionTarget()` 填入"设备名 · MAC"。

### 修改清单

**6.1 `app/src/main/res/layout/reader_config_fragment.xml`**

删除 `:66` 起的整个 `tv_config_connection_target` TextView 节点。

**6.2 `app/src/main/java/com/leo/remote/ui/fragment/home/ReaderConfigFragment.java`**

删除以下四处：

- `:69` 字段 `private TextView connectionTargetView;`
- `:116` `connectionTargetView = findViewById(R.id.tv_config_connection_target);`
- `:295` 调用 `bindConnectionTarget(state);`
- `:390-400` 整个 `bindConnectionTarget(ReaderState state)` 方法

**6.3 不要动的部分**

连接卡片内部的 `connectedTargetView` / `R.id.tv_config_connected_target`
（`ReaderConfigFragment.java:137`）是另一个控件，负责卡片里的已连接设备信息，保留。
两个 id 只差一个字母（`connection` vs `connected`），删除前逐个核对。

### 验证

已连接状态下进入配置页：右上角只有状态 chip，没有设备名与 MAC；
连接卡片里的设备信息照旧显示。

---

## 全局验证

```bash
cd /Users/lei/Projects/UhfRemote
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

`ReaderHandshakeTest.FakeGateway`（`app/src/test/java/com/leo/remote/reader/ReaderHandshakeTest.java:34`）
需要同步补齐本次新增/改签名的方法：四个 mask 方法的 `ModuleSubtype` 参数、
`setLowPowerScheduler`、`setInventoryStopListener`。

## 真机回归清单

| # | 场景 | 预期 |
|---|---|---|
| 1 | 应用掩码后盘点 | 只出现匹配标签 |
| 2 | 取消掩码后盘点 | 全部标签恢复 |
| 3 | 取消掩码后读写标签 | 正常读写（验证 Sel 已还原） |
| 4 | 掩码生效期间读写标签后继续盘点 | 掩码仍生效 |
| 5 | 盘点中点 item 填充掩码 | 盘点停止，表单已填 |
| 6 | 盘点区域弹窗 | 样式与其他弹窗一致 |
| 7 | 单次盘点 | 一轮后自动停止 |
| 8 | 库控持续盘点 | 持续出标签 |
| 9 | 切模式后重连 | 模式不回退 |
| 10 | 配置页右上角 | 无设备名 |
