# UhfRemote 新一轮实现方案

> 更新于 2026-07-31。
> **当前执行方案见「A. 本轮整改方案」**（后台常驻 / 全局在线 / 断开强提示 / 掩码整改）。
> 「附录 H」为上一轮方案（多子模块配置展示、标题栏固定、掩码面板落地），已实现，保留备查。
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

### 0.3 事件机制约定（本轮新增，重要）

**禁止引入 EventBus**。参考项目 `Uhf_Android` 用 `EventUtils.post(new EventMsg(EventKey.KEY_BLUETOOTH_CONNECT_FAILED))`
做跨组件通知，该方案已过时；本项目也无此依赖（`gradle/libs.versions.toml`、`app/build.gradle`
全文无 EventBus / LiveData / ViewModel / lifecycle-runtime），不得为此新增依赖。

**统一使用项目现有的 `ReaderObserver` 观察者机制**：
- 注册/注销：`ReaderSessionManager.addObserver(this)` / `removeObserver(this)`
- 扇出：`ReaderSessionManager` 内部 `mainHandler.post(...)` 保证回调落在主线程（参照 `notifyMask()`，:798-801）
- 新增事件一律走 `ReaderObserver` 的 **default method**，不破坏既有实现类
- 服务与会话之间用直接方法调用 + 回调接口，不用广播、不用 EventBus

本轮为 `ReaderObserver` 新增的默认方法：

```java
/** 异常断开时回调，UI 据此立即下发停止态并弹强确认框。 */
default void onReaderUnexpectedDisconnect(@NonNull DisconnectReason reason) {}
```

---

## A. 本轮整改方案

> 三条硬要求：**BLE 与 WiFi 连接后台常驻**、**连接状态全局可见**、**时刻监听连接状态**。
> 已确认取舍：**不做自动重连**。断开后立即下发停止态 + 强确认弹窗，由用户手动重连。

### A.1 参考项目 BLE 执行链（`/Users/lei/Downloads/Uhf_Android`）

1. **App.onCreate** — `EasyBLE.getBuilder()` 配 `ScanConfiguration`（15s 周期、`onlyAcceptBleDevice`、
   `acceptSysConnectedDevice`）+ `setMethodDefaultThreadMode(BACKGROUND)` → `initialize()`；
   `Linkage` 懒加载，首次 `getLinkage()` 时 `initRFID()`。
2. **BluetoothFragment** — 申请权限 → 扫描 → 选中设备存 static → `setRFConnectMode(2)` →
   `startActivity(BleDialog)`，`Device` 以 Parcelable 传递。
3. **BleDialog**（dialog 样式的 Activity，承担连接过程）— `connect`（超时 10s、`autoReconnect=false`、
   `tryReconnectMaxTimes=0`）→ `SERVICE_DISCOVERED` 时跳过 UUID 含 `0000180` 的标准服务、
   按特征属性名挑 write/notify/indicate → **请求 MTU 512** → `setNotification()` →
   `onNotificationChanged(true)` 里启动 `BleService`、`sleep(200)`、`finish()`。
4. **BleService**（普通后台 Service，非前台）— 双向绑定：`setOnBluetoothListener(this)` 承接 SDK 出栈数据写
   GATT；`onCharacteristicChanged` 把入栈数据 `pushRemoteRFIDData()` 回 SDK；
   `onConnectionStateChanged==DISCONNECTED` 时发事件并停盘点。
5. **ConnectManger** — 收到连接成功后 `stopInventory()` + `sleep(200)`，链式读 RM70XX 版本（板序列号 →
   板软件版本 → 模块版本 → 模块序列号 → `getBoardModuleType`），成功才置 `IS_MODULE_CONNECTED`。

**结论**：参考项目是演示级实现——无前台服务、无重连、无网络监听，WiFi 路径（`NetFragment`）更简陋，
`connectRemoteNetwork(ip,1200)` 直连且 `onDestroy` 就断开。当前项目在状态机、错误归因、GATT 通道
选择上已明显更完整。**唯二值得吸收的是 MTU 协商与 EasyBLE 显式 builder 初始化**（见 R7）。

### A.2 缺陷清单

| 编号 | 优先级 | 缺陷 | 位置 |
|---|---|---|---|
| D1 | P0 | 返回键 `shutdown()` 杀连接；`shutdown()` 置 `instance=null` 且 `sdkInitialized` 只在 `App.onCreate` 置 true，进程存活时再进入必然 -60 且 executor 已关闭 | `HomeActivity.handleBackPressed()`；`ReaderSessionManager.java:279-302,551,557-567` |
| D2 | P0 | 任何断开路径都 `stopService`，后台再连时 Android 12+ 拒绝启动前台服务 | `ReaderSessionManager.java:653-666` |
| D3 | P0 | WiFi 无前台服务、无 WifiLock，后台进程与 socket 随时被回收 | `ReaderSessionManager.java:210,569-589` |
| D4 | P1 | 服务挤在数据通路上，未就绪即**静默丢包**；并因此产生 `pendingBleHandshake` 状态舞蹈 | `ReaderSessionManager.java:569-634` |
| D5 | P1 | 监听「连上才听、断了就不听」：适配器仅处理 `STATE_OFF` 且要求 `device!=null`；`WifiNetworkMonitor` 仅 `onLost` 且连接后才注册 | `BleTransport.java:135-139`；`WifiNetworkMonitor` |
| D6 | P1 | WiFi 无活性探测，TCP 半开可长时间「假在线」；`probeConnectionAfterError()` 是 5 次调用的重量级反应式探测 | `ReaderSessionManager.java:753-767` |
| D7 | P1 | 连接状态只有少数页面感知，二级业务页完全不可见 | `StockQueryActivity` / `OrderProgressActivity` / `ShipmentQueryActivity` 等 |
| D8 | P1 | 掩码开关与应用/取消未联动：`applyMask()` 成功不置开关、`clearMask()` 成功不复位、`onInventoryMaskChanged(null)` 不复位 | `InventoryFragment.java:203-211,215-240` |
| D9 | P1 | 掩码开关与展开状态互抢（行点击与开关都改 `maskExpanded`）；开关在 `ll_inventory_mask_content` 之外，`setEnabledRecursively` 碰不到，未连接也能随意拨动 | `InventoryFragment.java:107-120,356`；`inventory_mask_panel.xml:59-67` |
| D10 | P1 | 偏移无按存储区初始值（仅 6B 硬置 0）；长度写死 32；数据不足只在点击应用后才报错 | `InventoryFragment.java:285-287,331`；`inventory_mask_panel.xml:159` |
| D11 | P2 | `BleTransport` 跨线程字段无 volatile（主线程写、JNI 线程与 EasyBLE 回调线程读） | `BleTransport.java` 字段区 |
| D12 | P2 | 断开只 `connection.releaseNoEvent()`，未走 `EasyBLE.releaseConnection(device)`，`connectionMap` 可能残留同 MAC 旧连接 | `BleTransport.disconnect()` |
| D13 | P2 | 无 MTU 协商，固定 `setPackageSize(20)` | `BleTransport.java:99` |
| D14 | P2 | EasyBLE 裸 `initialize()`，无 builder / ScanConfiguration / 线程模型 | `BleTransport.java:51-54` |
| D15 | P2 | `POST_NOTIFICATIONS` 已声明但从不运行时申请，API 33+ 前台通知可能不可见 | 全局无申请点 |
| D16 | P2 | 权限口径矛盾：manifest `BLUETOOTH_SCAN` + `neverForLocation`，代码却无条件申请 `ACCESS_FINE_LOCATION` | `AndroidManifest.xml`；`BleDeviceSheet` |

### A.3 状态与事件模型改动

**不新增 `ConnectionPhase` 枚举值**（不做自动重连，无需 `RECONNECTING`）。改动集中在两处：

1. `ReaderSessionManager` 新增字段：
```java
// ========== 断开告警 ==========
private volatile boolean pendingDisconnectAlert;      // 异常断开未被用户确认
private volatile DisconnectReason lastUnexpectedReason = DisconnectReason.NONE;
```
   - 异常断开时置位；用户点确认时清除；连接成功时清除。
   - 这是「弹窗复现」的唯一依据。对外暴露 `isPendingDisconnectAlert()` /
     `getLastUnexpectedReason()` / `acknowledgeDisconnect()`。

2. `ReaderObserver` 新增 default method `onReaderUnexpectedDisconnect(DisconnectReason)`（见 §0.3）。

**触发口径**：复用现有 `DisconnectReason.isUnexpected()`。
- 走强提示：`LINK_LOST` / `BLUETOOTH_OFF` / `WIFI_LOST` / `SDK_ERROR`
- 不弹窗：`USER` / `TRANSPORT_SWITCH` / `CANCELED` / `APP_EXIT`

---

### R1 — 会话生命周期（P0，修 D1）

**目标**：进程存活期间会话可重入；返回键不杀连接。

**改动**：`ReaderSessionManager`、`HomeActivity`

1. `HomeActivity.handleBackPressed()`：双击退出只 `moveTaskToBack(false)`，**删除
   `mReaderSession.shutdown()` 调用**；同时不再 `finishAllActivities()`（否则回前台需重建全部 Activity）。
2. 把 `shutdown()` 拆成两个方法：
   - `releaseNative()`：真正释放（断链、`deinitRFID()`、关 executor），**不再置 `instance=null`**，
     改为置 `sdkInitialized=false` 并把 `sdkExecutor` 置为可重建状态。
   - `shutdown()` 保留为对外语义「用户显式退出并断开」，内部调 `releaseNative()`。
3. `ensureSdkInitialized()`（:551）由「直接抛 -60」改为**惰性重建**：
   `sdkInitialized==false` 时重建 `sdkExecutor` 并重新 `initializeNativeAtApplication()`，
   失败才抛 `ReaderException(-60)`。
4. `releaseNative()` 的调用点收缩为两处：用户在配置页显式「断开并退出」、以及进程终止。

**验收**：连接后按返回键退出 → 重新进入 App → 连接仍在线；若已断开则能立刻重连（不再必然 -60）。

---

### R2 — 前台服务统一覆盖 BLE + WiFi（P0，修 D2/D3/D15）

**目标**：两种传输都后台常驻；服务生命周期绑「会话意图」而非「单次链路」。

**改动**：`ReaderBleService` → 重命名 `ReaderConnectionService`；`AndroidManifest.xml`；`ReaderSessionManager`

1. manifest 保持 `foregroundServiceType="connectedDevice"`、`exported="false"`；该类型对 BLE 与
   网络设备均适用，无需新增权限（`FOREGROUND_SERVICE_CONNECTED_DEVICE` 已声明）。
2. 启动时机：**用户发起连接时**（BLE 与 WiFi 都启动）`ContextCompat.startForegroundService`。
3. 停止时机：仅「用户显式断开」或「`releaseNative()`」。**链路异常断开时不停服务**——
   这是 D2 的修复关键，删掉 `disconnectTransportInternal()`（:653-666）里无条件的 `stopService`。
4. 通知实时反映状态，随 `ReaderState` 变化刷新：
   - `已连接 · <设备名或IP>` / `连接中…` / `连接已断开`
   - `contentIntent` → `HomeActivity`；附「断开」Action
5. WiFi 传输期间持 `WifiManager.WifiLock(WIFI_MODE_FULL_HIGH_PERF)`，随服务生命周期获取/释放。
6. `onStartCommand` 改 `START_STICKY` 且**幂等**：重复 `ACTION_START` 只刷新通知。
7. 首次连接前用现有 XXPermissions 链路申请 `POST_NOTIFICATIONS`（API 33+）；用户拒绝不阻断连接，
   仅记 `Log.w` 并提示通知不可见。

**验收**：BLE / WiFi 各自连接后熄屏 30 分钟仍在线可盘点；通知随状态变化；Android 13 通知可见；
Android 14 前台服务类型校验通过。

---

### R3 — 数据通路瘦身（P1，修 D4）

**目标**：会话直接持有通路，消除丢包路径与握手时序耦合。服务只做「进程存活锚点 + 通知」。

**改动**：`ReaderSessionManager`、`ReaderConnectionService`

1. 出栈：`gateway.setOutboundDataListener(bleTransport::write)`，不再经服务转发。
2. 入栈：`BleTransport` 收到 Notify 后直接 `gateway.pushRemoteData(...)`。
3. 删除：`forwardInboundDataToService()`（:627-634，含静默丢包分支）、`pendingBleHandshake`、
   `serviceStopRequested`、`onBleServiceCreated/Destroyed` 里的握手触发逻辑（:569-625）。
4. 握手时机改为：**notify 使能成功后**立即在 `sdkExecutor` 上执行 `ReaderHandshake.perform()`，
   不再等服务 `onCreate` 落地。

**注意**：本项净删代码。改完后服务不再出现在任何数据路径上，`ReaderConnectionService` 内不应有
`pushNotifyToJni` / `writeToBle` 之类方法。

**验收**：盘点数据不丢；杀掉服务（`adb shell am stopservice`）后已建立的链路数据仍正常流转。

---

### R4 — 常驻监听 + 断开即停（P1，修 D5/D6）

**目标**：时刻监听；发现断开后**不等 SDK 往返**，本地强制发布停止态。

**改动**：`BleTransport`、`WifiNetworkMonitor`、`ReaderSessionManager`

1. **监听常驻**：蓝牙适配器 `ACTION_STATE_CHANGED` 与 `ConnectivityManager.NetworkCallback`
   从 App 启动即注册、进程存活期间不注销。
   - 适配器：补 `STATE_ON` 分支（仅刷新可用性与 UI 提示，**不自动重连**）；
     删掉 `STATE_OFF` 分支里的 `device != null` 前置条件（`BleTransport.java:135-139`）。
   - `WifiNetworkMonitor`：补 `onAvailable` / `onCapabilitiesChanged`；移除已废弃的
     `getAllNetworks()` 用法；注册时机上移到 App 启动，不再「连接成功才 start」。
2. **WiFi 空闲心跳**：未盘点时每 5–10s 一次轻量 SDK 读探活；盘点期间不跑心跳（盘点回包本身即活性证明）。
   用它替换重量级的 `probeConnectionAfterError()`（:753-767）。
3. **本地强制停止态**（核心）。异常断开时在 `ReaderSessionManager` 内一次性完成：
```java
// ========== 断开处理 ==========
// 链路已死，stopInventory() 发不出去，停止态必须本地发布，不等 SDK 回包
private void handleUnexpectedDisconnect(DisconnectReason reason) {
    connectionGeneration.incrementAndGet();   // 丢弃在飞回调
    cancelInventoryLoopLocally();             // 取消盘点循环/定时器
    failPendingOperations(reason);            // 在飞 Future 全部异常完成，避免 UI 卡转圈
    clearInventoryMaskLocally();              // inventoryMask=null + notifyMask(null)
    pendingDisconnectAlert = true;
    lastUnexpectedReason = reason;
    publishState(b -> b.phase(DISCONNECTED).inventoryRunning(false).disconnectReason(reason));
    notifyUnexpectedDisconnect(reason);       // ReaderObserver 扇出
}
```
   - `inventoryRunning=false` 必须随状态一起发布，UI 才能立刻回到「开始」态。
   - 掩码必须本地清空：链路重建后模块里的 Select 条件已不存在，UI 不能显示「掩码生效中」。
4. 触发点：`BleTransport.notifyDisconnected(...)`、`onBluetoothAdapterStateChanged(STATE_OFF)`、
   `WifiNetworkMonitor.onLost`、心跳失败、SDK 调用判定链路已死 —— 统一汇入
   `handleUnexpectedDisconnect(reason)`，由 `DisconnectReason.isUnexpected()` 分流。

**验收**：盘点中拔读写器电源 / 关蓝牙 / 断 WiFi → 列表 10s 内停止增长、按钮回到「开始」、
掩码状态清空、原因判定正确；在飞的读写操作立即以异常结束而非一直转圈。

---

### R5 — 全局在线 + 强确认弹窗 + 功能键门禁（P1，修 D7）

**目标**：连接状态全局可见；异常断开必须用户确认；未连接时点功能键弹窗复现。

**改动**：`AppActivity`（基类接管）、`ReaderConfigFragment`、各功能入口

1. **基类接管观察**：`AppActivity implements ReaderObserver`，`onCreate` 注册 / `onDestroy` 注销
   （用 `ReaderSessionManager.getInstance(getApplication())`）。子类无需重复接线，二级业务页
   （`StockQueryActivity` / `OrderProgressActivity` / `ShipmentQueryActivity` / `FeedbackActivity` /
   `PagedQueryActivity`）自动获得状态显示与门禁。
2. **状态芯片**：标题栏右侧统一显示，文案与背景复用现有映射
   （`ReaderConfigFragment.statusText/statusBackground`，:520-532），下沉到基类共用。
3. **强确认弹窗**：`onReaderUnexpectedDisconnect(reason)` 时用 `MessageDialog.Builder`：
   - `setCancelable(false)` + `setCanceledOnTouchOutside(false)`（现成写法见
     `ReaderConnectionDialog.java:33,56`），返回键与点外部都不能取消
   - 标题 `reader_disconnected_title`；正文按 `reason` 取对应 string（见 A.4 映射）
   - 按钮：`确认`（`acknowledgeDisconnect()`）、`去连接`（同样 acknowledge，并跳配置页 `switchFragment(0)`）
4. **防重复 / 补弹**：
   - 只有处于 resumed 的顶层 Activity 弹；`AppActivity` 维护静态「当前 resumed 实例」引用
   - 其他 Activity 在 `onResume` 检查 `session.isPendingDisconnectAlert()` 补弹
     —— 断开发生在后台时，用户回前台第一眼即可见
   - 同一次断开只弹一次：以 `pendingDisconnectAlert` 为唯一闸门，弹出中不重复创建
5. **功能键门禁**：基类提供
```java
/** 未连接则弹强确认框并返回 false，替代散落的 toast 提示。 */
protected boolean requireReaderOnline() {
    if (session.getState().isConnected()) { return true; }
    showDisconnectDialog(session.getLastUnexpectedReason());
    return false;
}
```
   接入点（替换现有 `toast(R.string.inventory_connect_first)`，`InventoryFragment.java:179,217`）：
   盘点开始/停止、掩码开关、应用掩码、取消掩码、单标签读/写/改 EPC/锁/销毁、配置页各写入项、
   以及各业务页依赖读写器的按钮。
6. **配置页断开原因行**：`ReaderConfigFragment` 在状态芯片下方增加一行原因提示，
   **与弹窗共用同一个 `DisconnectReason → stringRes` 映射**（避免两处措辞漂移），连接成功时隐藏。

**验收**：任意页面断开都能看到状态与弹窗；弹窗只能点按钮关闭；确认后点任意功能键弹窗复现；
后台断开回前台补弹且只弹一次；重连成功后不再复现。

---

### R6 — 掩码整改（P1，修 D8/D9/D10）

**目标**：偏移按存储区给初始值；长度自动算并提示够不够；长度项移到掩码数据下面；
开关与应用/取消三向联动；未连接不可拨动。

**改动**：`ProtocolEncoding`、`InventoryFragment`、`inventory_mask_panel.xml`、`strings.xml`

**R6.1 偏移初始值**。在 `ProtocolEncoding` 增加（与既有 `targetMaskOffset()` 同文件同语义，
不新建类）：
```java
/** 各存储区数据起始位不同，切换存储区时回填对应初始偏移。 */
public static int defaultMaskOffsetBits(TagProtocol protocol, int bankPosition) {
    return switch (protocol) {
        // 保留区0 / EPC32(跳过 CRC-16+PC) / TID0 / USER0
        case ISO_18000_6C -> bankPosition == 1 ? 32 : 0;
        case ISO_18000_6B -> 0;          // UID
        case GJB_7377_1 -> 0;            // EPC
        case GB_T_29768 -> 0;            // 各区均从 0 起
    };
}
```
   与现有 `targetMaskOffset()`（:43-45，6C 取 32、其余取 0）保持同一规则。
   回填时机：bank spinner 的 `OnItemSelectedListener` 与 `updateMaskBanks()`（:318-334），
   替换现在只对 6B 硬置 0 的一行（:331）。用户手改过的值在**下次切换存储区时被覆盖**，属预期行为。

**R6.2 布局顺序**。`inventory_mask_panel.xml` 改为：
`[存储区 | 偏移]` → `掩码 HEX` → `长度（bit）` → **长度提示** → 按钮行。
即把长度标签+输入框（:139-161）整体移到 hex 输入框（:171-187）之后，并在其下新增
`tv_inventory_mask_length_hint`（`sp_11`，默认 `rfid_text_muted`）。长度框去掉硬编码 `android:text="32"`。

**R6.3 焦点变化自动算长度 + 够不够提示**。
- `et_inventory_mask_hex` 加 `OnFocusChangeListener`：失焦时按 `hex.length() * 4` 算位数写入长度框；
  6B 协议下向下取整到 8 的倍数（现有校验 :288-290 要求字节对齐）。
- hex 或 length 任一变化都刷新提示，四种情形对应 A.4 的四条 string。
- **数据不足时**：提示置警示色 + 禁用「应用掩码」按钮。即把现在只在 `parseMaskForm()` 抛异常时才
  toast 的校验（:285-287）**提前到输入阶段**。

**R6.4 开关三向联动**。开关语义定为「掩码是否生效」，严格等于 `activeMask != null`；
展开/收起交回行点击与箭头独占（解 D9 的互抢）。
- OFF→ON：用当前表单执行 `applyInventoryMask`；表单不合法 → 回弹 OFF + 展开面板 + 焦点落到出错框；
  SDK 返回非 0 → 回弹 OFF
- ON→OFF：执行 `clearInventoryMask`；失败 → 回弹 ON（沿用 `restoreMaskSwitch` 思路）
- 点「应用掩码」成功 → 开关置 ON；点「取消掩码」成功 → 开关置 OFF
- `onInventoryMaskChanged(config)` **无条件** `setMaskSwitchChecked(config != null)`，
  两个方向都收敛到这一个回调（修 D8 的 null 分支缺失，:203-211）；`bindingMaskSwitch` 抑制递归沿用
- 操作在飞期间开关置灰，避免连点

**R6.5 未连接不可拨动**。`maskSwitch.setEnabled(readerState.isConnected())`。
因 `setEnabled(false)` 收不到点击，需在开关外层容器拦一次点击 → 走 `requireReaderOnline()` 弹窗，
否则用户点了没反应会以为卡死。

**验收**：逐协议逐存储区切换看偏移初始值；hex 失焦自动算长度（含奇数位、6B 非 8 倍数）；
长度超数据时应用按钮禁用且提示准确；三向联动不出现「开关 ON 但无掩码生效」；未连接时开关不可拨动
且点击有提示。

---

### R7 — 稳定性与参考项目吸收项（P2，修 D11-D14/D16）

**改动**：`BleTransport`、`InitManager`、`BleDeviceSheet`、`AndroidManifest.xml`

1. **MTU 协商**（吸收参考项目）：`SERVICE_DISCOVERED` 后请求 MTU 512，接受协商结果，
   据此把 `WriteOptions.packageSize` 设为 `mtu - 3`（替换固定 20，`BleTransport.java:99`）。
   协商失败退回 20，不阻断连接。
2. **线程安全**：`connection` / `device` / `serviceUuid` / `writeUuid` / `receiveUuid` / `writeType` /
   `attemptId` / `disconnecting` 加 `volatile`；`write()` 内先快照到局部变量再用，
   避免 `disconnect()` 置 null 后的 NPE 窗口。
3. **连接释放**：`disconnect()` 改用 `EasyBLE.getInstance().releaseConnection(device)` 替代
   仅 `connection.releaseNoEvent()`，确保 EasyBLE 内部 `connectionMap` 清理。
   （反复连同一 MAC 偶发失败的合理嫌疑点，需真机验证。）
4. **EasyBLE 显式初始化**（吸收参考项目）：从 `BleTransport` 构造（:51-54）上移到
   `InitManager.preInitSdk`，用 `EasyBLE.getBuilder()` 显式配 `ScanConfiguration`
   （`onlyAcceptBleDevice`、扫描周期）与 `setMethodDefaultThreadMode`，使回调线程模型确定。
5. **权限口径对齐**：Android 12+ 只申请 `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`；
   `ACCESS_FINE_LOCATION` 加 `android:maxSdkVersion="30"`。
   **需真机验证** EasyBLE 1.5.8 的权限检查在 12+ 是否仍强求定位：若强求，则去掉 manifest 的
   `neverForLocation`（二者只能取一），并同步修正 `BleDeviceSheet` 的申请列表。
6. **扫描互斥**：`BleDeviceSheet` 打开时不并发其他扫描，避免两路扫描互相干扰 GATT。

**验收**：大批量标签盘点吞吐明显提升（对比 20 字节分包）；反复连断同一设备 20 次不出现连接失败；
Android 12/13/14 扫描与连接均正常。

---

### A.4 新增字符串资源（`strings.xml`）

```xml
<!-- 断开提示：弹窗与配置页原因行共用同一映射 -->
<string name="reader_disconnected_title">读写器已断开</string>
<string name="reader_disconnected_link_lost">与读写器的连接已丢失，请检查设备电源与距离后重新连接。</string>
<string name="reader_disconnected_bluetooth_off">蓝牙已关闭，请开启蓝牙后重新连接。</string>
<string name="reader_disconnected_wifi_lost">Wi-Fi 已断开，请恢复网络后重新连接。</string>
<string name="reader_disconnected_sdk_error">读写器通信异常，请重新连接。</string>
<string name="reader_offline_action_blocked">读写器未连接，无法执行该操作。</string>
<string name="reader_goto_connect">去连接</string>

<!-- 前台服务通知 -->
<string name="reader_service_channel_name">读写器连接</string>
<string name="reader_service_connected">已连接 · %1$s</string>
<string name="reader_service_connecting">连接中&#8230;</string>
<string name="reader_service_disconnected">连接已断开</string>
<string name="reader_service_action_disconnect">断开</string>

<!-- 掩码长度提示（长度项移到掩码数据下方后新增） -->
<string name="inventory_mask_length_hint_ok">数据 %1$d 字节（%2$d bit）· 长度 %3$d bit 有效</string>
<string name="inventory_mask_length_hint_short">长度 %1$d bit 超过数据 %2$d bit，请补足掩码数据</string>
<string name="inventory_mask_length_hint_odd">掩码 HEX 需为偶数位，当前 %1$d 位</string>
<string name="inventory_mask_length_hint_empty">请输入掩码数据</string>
```

`DisconnectReason → stringRes` 映射（**唯一一份**，弹窗与配置页原因行都调它）：

| DisconnectReason | string |
|---|---|
| `LINK_LOST` | `reader_disconnected_link_lost` |
| `BLUETOOTH_OFF` | `reader_disconnected_bluetooth_off` |
| `WIFI_LOST` | `reader_disconnected_wifi_lost` |
| `SDK_ERROR` | `reader_disconnected_sdk_error` |
| 其余（`isUnexpected()==false`） | 不提示、不弹窗 |

---

### A.5 文件变更清单

**重命名（1）**
- `reader/ReaderBleService.java` → `reader/ReaderConnectionService.java`（R2/R3；同步改 manifest 声明）

**修改（Java）**
| 文件 | 涉及 |
|---|---|
| `reader/ReaderSessionManager.java` | R1 R2 R3 R4 |
| `reader/ReaderConnectionService.java` | R2 R3 |
| `reader/BleTransport.java` | R3 R4 R7 |
| `reader/WifiNetworkMonitor.java` | R4 |
| `reader/ReaderObserver.java` | §0.3 新增 default method |
| `reader/ProtocolEncoding.java` | R6.1 |
| `app/AppActivity.java` | R5（基类接管观察/芯片/弹窗/门禁） |
| `manager/InitManager.java` | R7.4 |
| `ui/activity/HomeActivity.java` | R1 R5 |
| `ui/fragment/home/ReaderConfigFragment.java` | R5.6 |
| `ui/fragment/home/InventoryFragment.java` | R5.5 R6 |
| `ui/fragment/home/SingleTagFragment.java` | R5.5 门禁接入 |
| `ui/dialog/BleDeviceSheet.java` | R7.5 R7.6 |
| 各业务 Activity（Stock/OrderProgress/Shipment/Feedback/PagedQuery） | R5.5 门禁接入 |

**修改（资源）**
- `res/layout/inventory_mask_panel.xml` — R6.2 顺序调整 + 新增长度提示 TextView
- `res/values/strings.xml` — A.4
- `AndroidManifest.xml` — R2 服务改名、R7.5 权限口径

**不新增任何依赖**（§0.3）；**不新增 Helper/Util/Manager 类**（§0.1）。

---

### A.6 建议实施顺序

1. **R6 掩码整改** — 与其余项无耦合，改动最独立、界面上最快见效，建议先做
2. **R1 → R2 → R3** — 需连贯处理（服务改名 + 数据通路解耦 + 生命周期），中途状态不自洽
3. **R4** — 依赖 R3 完成后的通路
4. **R5** — 依赖 R4 的 `onReaderUnexpectedDisconnect` 事件
5. **R7** — 收尾，可独立验证

---

### A.7 验收标准

**单元测试**（沿用 `ReaderDomainTest` 风格，假 gateway + 假时钟）
- 异常断开时在飞 Future 全部异常完成，且 `inventoryRunning` 置 false
- `pendingDisconnectAlert` 生命周期：异常断开置位 → acknowledge 清除 → 连接成功清除
- `DisconnectReason.isUnexpected()` 分流正确（8 个枚举值逐个覆盖）
- `connectionGeneration` 递增后旧回调被丢弃
- `defaultMaskOffsetBits()` 四协议各存储区取值

**真机测试**
- BLE / WiFi 各自连接后熄屏 30 分钟仍可盘点
- 盘点中读写器断电 → 10s 内停止态 + 强确认弹窗
- 关蓝牙 / 断 WiFi / 断电三条路径文案各自正确
- 断开发生在后台 → 回前台补弹且只弹一次
- 确认后点开始/掩码/单标签/业务页按钮 → 弹窗复现
- 重连成功后弹窗不复现，掩码状态与模块一致
- 返回键退出再进入能立刻连接（D1 回归）
- Android 13 通知可见性 / Android 14 前台服务类型校验
- 反复连断同一设备 20 次无失败（D12 回归）

---

# 附录 H：上一轮方案（已实现，备查）

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
