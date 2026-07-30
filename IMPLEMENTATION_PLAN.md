# UhfRemote 整改方案

> 更新于 2026-07-30。原计划所有任务已全部完成，本文档仅保留已完成清单摘要，以及对照
> [AndroidCodeStandard](https://github.com/getActivity/AndroidCodeStandard) /
> [AndroidVersionAdapter](https://github.com/getActivity/AndroidVersionAdapter) /
> AndroidProject 框架规范发现的遗留整改项。

---

## 0. 原计划完成情况（全部 ✅）

| 编号 | 内容 | 状态 |
|---|---|---|
| T0 | 深浅双主题 + 手动切换（`values/colors.xml` 亮色板、`values-night/colors.xml` 深色板、`ThemeModeManager`、`InitManager` 启动时 `applyStoredMode()`、我的页三档入口） | ✅ |
| T1 | 配置页（`ReaderConfigFragment`：功率滑杆、协议/工作模式/Session/BLF/Q 弹窗、BLE/WiFi 切换） | ✅ |
| T2 | 盘点页（`InventoryFragment`：启停/清除/CSV 导出，RSSI 三色走 token，斑马纹用 `ContextCompat.getColor`） | ✅ |
| T3 | 单标签页（`SingleTagFragment`：读取、写数据/改 EPC/锁定/销毁，销毁有二次确认） | ✅ |
| T4 | 我的页（`MineFragment`：登录双态、功能菜四入口、主题设置、MMKV 持久化） | ✅ |
| T5 | 网络层平移（`http/model/`：`HttpData` / `HttpListData` / `RequestHandler` / `RequestServer`，`InitManager` 初始化 EasyConfig） | ✅ |
| T6 | Repository 接口 + Mock（`DataCallback<T>`，五套接口 + Mock 实现含随机延迟，`RepositoryProvider` 单点注入） | ✅ |
| T7 | 实时库存查询（`StockQueryActivity` + `PagedQueryActivity` 骨架：loading / empty / error 三态） | ✅ |
| T8 | 订单进度查询（`OrderProgressActivity`） | ✅ |
| T9 | 发货情况与单号（`ShipmentQueryActivity`） | ✅ |
| T10 | 问题与需求提交（`FeedbackActivity`：类型 tab、关联订单、图片最多 6 张、相机权限申请、压缩） | ✅ |

---

## 1. 整改任务

### R1 — Manifest android:exported 补全 ⚠️ 高优先级

**问题**：Android 12（API 31）起，`Manifest` 中所有 `<activity>` 必须显式声明 `android:exported`，否则编译产生 lint 警告，`targetSdkVersion ≥ 31` 时系统可能拒绝安装。

**涉及文件**：`app/src/main/AndroidManifest.xml`

当前缺少 `exported` 的 Activity：

| 组件 | 应补值 | 说明 |
|---|---|---|
| `HomeActivity` | `false` | 无 intent-filter，仅内部启动 |
| `StockQueryActivity` | `false` | 同上 |
| `OrderProgressActivity` | `false` | 同上 |
| `ShipmentQueryActivity` | `false` | 同上 |
| `FeedbackActivity` | `false` | 同上 |
| `CrashActivity` | `false` | 独立进程，仍需声明 |
| `RestartActivity` | `false` | 同上 |

`SplashActivity` 已有 `exported="true"`（有 `LAUNCHER` filter），`ReaderBleService` 已有 `exported="false"`，均无需改动。

**修改方式**：每个缺失的 `<activity>` 标签补一行 `android:exported="false"`。

---

### R2 — SingleTagFragment 硬编码字符串提取 ⚠️ 高优先级

**问题**：`SingleTagFragment.java` 中大量中文文案直接写在 Java 代码里，违反「所有文案进 `strings.xml`」规范。

**涉及文件**：
- `app/src/main/java/.../ui/fragment/home/SingleTagFragment.java`
- `app/src/main/res/values/strings.xml`

**需提取的字符串**（在 strings.xml 补充 `single_` 前缀的条目）：

```
toast 文案：
  "请先连接读写器"   → 复用已有 inventory_connect_first（勿重复定义）
  "请先读取目标标签" → single_no_tag_hint
  "读取中..."        → single_reading
  "读取标签"         → single_read_tag
  "读取失败："       → single_read_failed（格式串 %1$s）
  "密码必须是 8 位 HEX"               → single_password_invalid
  "必须是非负整数"（含 name 参数）    → single_field_must_be_unsigned（格式串 %1$s）
  "EPC 必须是 2-62 字节的偶数长度 HEX" → single_epc_length_invalid

对话框 title：
  "写入标签数据"  → single_write_title
  "修改 EPC"      → single_update_epc_title
  "锁定标签"      → single_lock_title
  "销毁标签"      → single_kill_title
  "确认永久销毁？" → single_kill_confirm_title
  "目标标签销毁后不可恢复。" → single_kill_confirm_message

对话框按钮：
  "执行"     → 复用 common_commit（res-common/values/strings.xml 已有）
  "取消"     → 复用 common_cancel
  "下一步"   → single_next_step
  "确认销毁" → single_kill_confirm

字符串数组（对话框 items，迁移到 strings.xml <string-array>）：
  6C 存储区：single_bank_labels_6c  = [保留区, EPC, TID, USER]
  6B 存储区：single_bank_labels_6b  = [UID, USER]
  GB 存储区：single_bank_labels_gb  = [标签信息区, 编码区, 安全区, 用户区]
  锁定策略：single_lock_policy_labels = [可读写, 永久可读写, 授权可读写, 永久不可读写]
  锁定目标：single_lock_bank_labels   = [访问密码, 销毁密码, EPC, TID, USER]
  GB 子区：single_gb_sub_bank_labels = [子区 1, 子区 2, …, 子区 30]（10 个常用项）
```

Java 侧改法示例：
```java
// 改前
toast("请先连接读写器");
// 改后
toast(R.string.inventory_connect_first);

// 改前
readButton.setText("读取标签");
// 改后
readButton.setText(R.string.single_read_tag);

// 改前
bank.setAdapter(new ArrayAdapter<>(…, new String[]{"访问密码", …}));
// 改后
bank.setAdapter(new ArrayAdapter<>(…, getResources().getStringArray(R.array.single_lock_bank_labels)));
```

---

### R3 — Activity 静态入口方法缺失 ⚠️ 高优先级

**问题**：规范要求跳转目标 Activity 在目标类中定义 `static start(Context context)` 方法，避免调用方直接构造 `Intent`（修改参数时需全局搜索调用点）。

**涉及文件**：四个业务 Activity + `MineFragment`。

**修改方案**：

在每个目标 Activity 中添加（以 `StockQueryActivity` 为例）：
```java
public static void start(Context context) {
    context.startActivity(new Intent(context, StockQueryActivity.class));
}
```

`MineFragment` 中的四处调用改为：
```java
// 改前
startActivity(new Intent(getAttachActivity(), StockQueryActivity.class));
// 改后
StockQueryActivity.start(getAttachActivity());
```

同样处理 `OrderProgressActivity.start()`、`ShipmentQueryActivity.start()`、`FeedbackActivity.start()`。

---

### R4 — FeedbackActivity + PagedQueryActivity 硬编码字符串 ▸ 中优先级

**问题**：`FeedbackActivity` 和 `PagedQueryActivity` 中仍有裸字符串。

**FeedbackActivity.java** 需提取：

| 当前字面量 | strings.xml 键 |
|---|---|
| `"未获得图片权限"` | `feedback_permission_denied` |
| `"最多上传 6 张图片"` | `feedback_image_limit` |
| `"图片保存失败"` | `feedback_image_save_failed` |
| `"请输入问题标题"` | `feedback_title_empty` |
| `"请输入详细描述"` | `feedback_detail_empty` |

**PagedQueryActivity.java** 需提取：

```java
// 改前
showState("加载中...");
// 改后
showState(getString(R.string.common_loading));  // res-common 已有此串
```

各子类的 `emptyMessage()` / `errorMessage()` 已返回字面量，应改为 `getString(R.string.xxx)`：

| Activity | 方法 | 建议键 |
|---|---|---|
| `StockQueryActivity` | `emptyMessage` | `stock_empty` |
| `StockQueryActivity` | `errorMessage` | `stock_load_failed` |
| `OrderProgressActivity` | `emptyMessage` | `order_empty` |
| `OrderProgressActivity` | `errorMessage` | `order_load_failed` |
| `ShipmentQueryActivity` | `emptyMessage` | `shipment_empty` |
| `ShipmentQueryActivity` | `errorMessage` | `shipment_load_failed` |

---

### R5 — @SingleClick 防重复点击注解接入 ▸ 中优先级

**问题**：项目已配置 `androidAop` + `SingleClickCut` 切面，但各 Activity/Fragment 的点击回调均未标注 `@SingleClick`，快速双击可触发重复操作（如重复提交反馈、重复盘点）。

**涉及文件**：
- `FeedbackActivity.java` → `submit()` 方法
- `MineFragment.java` → `login()` 方法
- `InventoryFragment.java` → `toggleInventory()` 方法
- `SingleTagFragment.java` → `readTag()`, `showWriteDialog()`, `showLockDialog()`, `showKillDialog()`

**修改方式**：

```java
// 方式一：在 lambda 引用的具名方法上加注解
@SingleClick
private void submit() { … }

// 方式二：给 setOnClickListener 传入的方法引用直接加（框架约定）
// 若使用匿名 lambda，需提取为具名方法后加注解
```

注意：`SingleClickCut` 已在 AOP 作用域 `include android.defaultConfig.applicationId` 内，无需额外配置。

---

### R6 — strings.xml 清理框架模板残留 ▸ 低优先级

**问题**：`strings.xml` 保留了 AndroidProject 原始模板的大量无用条目（注册、忘记密码、修改手机号、个人资料等），UhfRemote 中无对应功能，增加维护干扰。

**涉及文件**：`app/src/main/res/values/strings.xml`

**可删除的 key**（确认无引用后删除）：
```
login_register, login_forget, login_other
register_title, register_hint, register_password_hint1/2
setting_title, setting_language_*, setting_update, setting_password,
setting_phone, setting_auto, setting_cache, setting_agreement,
setting_about, setting_exit
about_title, about_author, about_copyright
password_forget_title, password_reset_*, password_reset_input_error,
password_reset_success
phone_reset_*
personal_data_*
update_* （若无自动更新功能）
safe_title
```

删除前用 `./gradlew lint` 或 Android Studio「Find Usages」确认无引用。

---

## 2. 验收标准

| 整改项 | 验收条件 |
|---|---|
| R1 | `./gradlew lint` 中不再有 `MissingApplicationIcon`/`ActivityExported` 相关 warning |
| R2 | `SingleTagFragment.java` 中无中文字面量；`strings.xml` 新增所有 `single_*` 条目 |
| R3 | `MineFragment.java` 中无 `new Intent(…Activity.class)` 直接构造；四个 Activity 各有 `start()` |
| R4 | `FeedbackActivity.java` 无中文 toast 字面量；`PagedQueryActivity` `showState` 无字面量 |
| R5 | `submit` / `login` / `toggleInventory` / `readTag` 等高频操作方法头部有 `@SingleClick` |
| R6 | `strings.xml` 无未使用条目（lint `UnusedResources` 通过） |

编译验收：`./gradlew assembleDebug` 通过，lint 无新增警告。
