# UhfRemote 实现方案（委托执行规格）

## 0. 背景与约束

**目标**：按 `UI设计/` 下 8 张设计稿实现完整 App。

**四个代码库的角色**：

| 路径 | 角色 |
|---|---|
| `/Users/lei/Projects/UhfRemote` | 当前项目，改这里 |
| `/Users/lei/Projects/UhfRemote/UI设计/` | 8 张设计稿，UI 唯一依据 |
| `/Users/lei/Downloads/AndroidProject-master` | 框架来源。网络层从这里平移，不要自己造 |
| `/Users/lei/Projects/Uhf_Android` | 旧项目。**仅**参考读写器/EPC/TID 设备逻辑；它没有网络层，业务四页无可参考 |

**硬约束**：

1. **后端不接入**。所有业务数据走 Repository + Mock 假数据。但字段结构必须按真实契约设计，日后换 HTTP 实现时 UI 零改动。
2. 完全沿用 AndroidProject 框架范式：`AppActivity` / `AppFragment` / `BaseAdapter` / TitleBar / `libs.versions.toml` 版本目录。不引入框架外的新架构（不上 MVVM/Compose/Hilt）。
3. 语言 Java，与现有代码一致。
4. 已有的读写器层（`session.startInventory()` / `stopInventory()` / `getInventorySnapshot()` / CSV 导出）已可用，**不要重写**。
5. 主题色用 `app/src/main/res/values/colors.xml` 里现成的 `rfid_*` token，不要新增硬编码色值。

**现成 token**（勿重复定义）：

```
rfid_page_bg #0F172A    rfid_panel_bg #1E293B   rfid_panel_bg_deep #111827
rfid_nav_bg #1B2638     rfid_primary #2F5BEA    rfid_primary_soft #3B82F6
rfid_success #22C55E    rfid_teal #087A55       rfid_warning #FBBF24
rfid_danger #EF4444     rfid_orange #F97316     rfid_text #F8FAFC
rfid_text_secondary #CBD5E1  rfid_text_muted #64748B  rfid_line #334155
```

## 1. 现状

已完成：框架接入、主题 token、四个底部 Tab（`HomeActivity` + `BasePagerAdapter`）、读写器会话层。

待做：
- 设备四页（配置/盘点/单标签/我的）UI 未对齐设计稿
- 业务四页是 21 行空壳：`StockQueryActivity`、`OrderProgressActivity`、`ShipmentQueryActivity`、`FeedbackActivity`
- `com/leo/remote/http/` 是空目录，`build.gradle` 无任何网络依赖
- 登录未接，`MineFragment` 里是 `toast("登录服务尚未接入")`

## 2. 架构决策

用 Repository 接口隔离数据源：

```
Activity → XxxRepository (interface)
              ├── MockXxxRepository   ← 现在用
              └── HttpXxxRepository   ← 后端就绪后新增，只换注入点
```

统一回调签名，两版严格互换：

```java
interface OrderRepository {
    void queryOrders(String keyword, OrderStatus filter, DataCallback<List<Order>> callback);
}

interface DataCallback<T> {
    void onSuccess(T data);
    void onFail(Exception e);
}
```

**Mock 必须模拟真实网络行为**：300–800ms 随机延迟 + 可开关的失败分支。否则 loading/error 态在开发期看不到，等接真接口才发现没写。

**状态一律用枚举，不用 String**。设计稿里每个状态都有独立配色，UI 要按状态分支。接口返回字符串码时在 Repository 实现里映射，UI 不动。

## 3. 数据模型（从设计稿反推，按此实现）

```java
// ── 库存 ──────────────────────────────────────
class StockItem {
    String productName;      // "H47 Monza R6 定制款"
    String chipModel;        // "Monza R6"
    int availableQty;        // 可用库存
    int reservedQty;         // 已预定
    String warehouse;        // 仓库位置
    String spec;             // "30×15mm 白底黑字背胶"
    String imageUrl;
    long updateTime;
}

// ── 订单 ──────────────────────────────────────
enum OrderStatus {
    IN_PRODUCTION("生产中",   R.color.rfid_primary_soft),
    PARTIAL_SHIPPED("部分发货", R.color.rfid_orange),
    PENDING("待处理",         R.color.rfid_text_muted),
    COMPLETED("已完成",       R.color.rfid_success);
}

class Order {
    String orderNo;              // "ORD-2024-0851"
    String productName;          // "H47 Monza R6 定制款"
    int quantity;                // 50000，UI 显示千分位 "50,000"
    String customRequirement;    // "30×15mm 白底黑字背胶" / "标准白底 无特殊要求"
    OrderStatus status;
    int progress;                // 0-100，仅 IN_PRODUCTION 显示进度条
    int shippedQty;              // 12000，仅 PARTIAL_SHIPPED 显示
    int pendingQty;              // 8000
    List<String> processImages;  // 生产过程记录缩略图
    String imageUrl;             // 产品主图
    long submitTime;             // PENDING 显示「提交时间」
    long finishTime;             // COMPLETED 显示「完成时间」
}

// ── 发货 ──────────────────────────────────────
enum ShipmentStatus { SHIPPED("已发货"), IN_TRANSIT("运输中"), DELIVERED("已签收"), PREPARING("备货中"); }

class Shipment {
    String orderNo;
    String batchNo;              // 批次号
    int quantity;
    String trackingNo;           // 运单号，支持点击复制到剪贴板
    String carrier;              // 承运商
    ShipmentStatus status;
    long shipTime;
    List<TimelineNode> timeline; // 时间轴节点
}

class TimelineNode { String title; String desc; long time; boolean done; }

// ── 问题反馈 ──────────────────────────────────
enum FeedbackType { PRODUCT("产品问题"), ORDER("订单问题"), REQUIREMENT("新需求"); }

class FeedbackDraft {
    FeedbackType type;           // 默认 PRODUCT
    String relatedOrderNo;       // 选填
    String title;
    String detail;
    List<String> imagePaths;     // 最多 6 张
}

// ── 登录 ──────────────────────────────────────
class UserInfo { String username; String role; boolean logged; }  // "admin" / "管理员"
```

## 4. 任务清单（按此顺序执行）

### 阶段零：主题体系（必须最先做）

**T0 深浅双主题 + 手动切换**

> **为什么排在最前**：T1 会沉淀 `RfidCard` / `RfidValueChip` 等公共 style。若先按深色写死、后续再抽主题，这批 style 加上九个页面全部要重构。现在做，后续所有页面从第一行起就是主题安全的。

现状对本项有利：255 处色值引用**全部**走语义化 `rfid_*` token，无散落硬编码。因此：

**0.1 色值分离**（34 个布局文件无需任何改动）
- 新建 `values-night/colors.xml`，把 `values/colors.xml` 现有的 15 个深色值原样搬入
- `values/colors.xml` 改为亮色值（见下方色板）
- token 名保持完全一致 —— 这是布局零改动的前提

**亮色色板**（与深色语义一一对应，遵循 WCAG AA 对比度）：
```xml
<color name="rfid_page_bg">#F1F5F9</color>        <!-- 深: #0F172A -->
<color name="rfid_panel_bg">#FFFFFF</color>       <!-- 深: #1E293B -->
<color name="rfid_panel_bg_deep">#E2E8F0</color>  <!-- 深: #111827 -->
<color name="rfid_nav_bg">#FFFFFF</color>         <!-- 深: #1B2638 -->
<color name="rfid_primary">#1D4ED8</color>        <!-- 深: #2F5BEA 提深保证白底对比 -->
<color name="rfid_primary_soft">#2563EB</color>   <!-- 深: #3B82F6 -->
<color name="rfid_success">#15803D</color>        <!-- 深: #22C55E 亮绿在白底不可读 -->
<color name="rfid_teal">#0F766E</color>           <!-- 深: #087A55 -->
<color name="rfid_warning">#B45309</color>        <!-- 深: #FBBF24 黄色白底必须压深 -->
<color name="rfid_danger">#DC2626</color>         <!-- 深: #EF4444 -->
<color name="rfid_orange">#C2410C</color>         <!-- 深: #F97316 -->
<color name="rfid_text">#0F172A</color>           <!-- 深: #F8FAFC 反转 -->
<color name="rfid_text_secondary">#475569</color> <!-- 深: #CBD5E1 反转 -->
<color name="rfid_text_muted">#64748B</color>     <!-- 深: 同值，两侧均达标 -->
<color name="rfid_line">#CBD5E1</color>           <!-- 深: #334155 -->
```
注意 success / warning / orange 三色不是简单沿用：`#22C55E`、`#FBBF24` 在白底上对比度不足 3:1，属于不可读，必须压深。

**0.2 Java 侧 5 处修正**
- `HomeActivity:200-201`、`RfidPageActivity:44-45` 的 ImmersionBar `statusBarColor` / `navigationBarColor`：需随主题切换，并同步设置 `statusBarDarkFont(亮色主题为 true)`，否则亮色下白底白字图标不可见
- `InventoryAdapter:57` 斑马纹 `setBackgroundResource(R.color.xxx)`：**必须改**。它直接取资源 id，主题切换不会重解析。改用 `ContextCompat.getColor(context, ...)` 或改走 `?attr/`

**0.3 切换入口**
- `values/themes.xml`（当前不存在，需新建）声明主题，`AppCompatDelegate.setDefaultNightMode()` 执行切换
- 三档：跟随系统 / 亮色 / 深色，默认**跟随系统**
- 选择持久化到 MMKV（已在依赖），`Application` 启动时读取并应用，避免冷启动闪烁
- 入口放「我的」页，作为「功能菜」之外的独立设置项（设计稿无此项，按现有卡片 style 新增一行，图标用主题/亮度图标）

**0.4 验收**
- 两种主题下逐页检查，无不可读文字、无白底白字图标、无深底深字
- 切换后无需重启 App 即生效
- 冷启动直接进入上次选择的主题，无闪烁

### 阶段一：设备四页对齐设计稿

零外部依赖，要先沉淀 style 供后续九页复用。

> **全阶段主题约束**：所有新增 style 与布局只允许引用 `rfid_*` token 或 `?attr/`，禁止字面色值（`#RRGGBB`）。设计稿是深色态，实现时需同时确认亮色态可读。

**T1 配置页**（`ReaderConfigFragment` + `配置页.png`）
- 标题「RFID 读写器」，右上角连接状态 chip（绿点 + 「已连接」，圆角胶囊）
- 分组卡片：功率设 / 协议与模 / 传输方 / 速率设（注意设计稿标题就是截断的，照抄）
- 功率：滑杆 0–33 dBm，右侧实时数值蓝色显示「26 dBm」，下方左右端点标注
- 选择项：射频协议「ISO 18000-6C」、工作模式「连续触发」、Session「S0」、Blf 速率「256 kHz」、Q 值「自适应」—— 统一为右侧深蓝底圆角 chip，点击弹底部选择器
- 开关项：蓝牙（开）、WiFi（关），带图标
- **产出 style**：`RfidCard`、`RfidGroupTitle`、`RfidValueChip`、`RfidRowLabel` —— 后续页面必须复用

**T2 盘点页**（`InventoryFragment` + `盘点页.png`）
- 标题「标签盘点」，右侧计数 badge「128 条」（蓝底胶囊）
- 三按钮：开始盘点（绿底 + play 图标）、清除（幽灵 + 垃圾桶）、导出（幽灵 + 下载）
- 表头：`编号 | EPC 号 | 次数 | RSSI | 芯片型号`
- 行：编号补零三位（001）、EPC 蓝色截断显示、次数、**RSSI 按强度着色**（≥ -70 绿 / -70~-80 黄 / < -80 红）、芯片型号
- 复用现有 `session` 盘点逻辑，只改 UI
- **主题相关**：RSSI 三色走 `rfid_success` / `rfid_warning` / `rfid_danger` token，不要写死。斑马纹背景见 T0.2，必须改掉 `setBackgroundResource` 取色方式

**T3 单标签页**（`SingleTagFragment` + `单标签操作页.png`）
- 上卡片「当前标签信」：EPC（蓝色）、TID、芯片型号、RSSI 值（绿色）四行 + 全宽蓝色「读取标签」按钮（带扫描框图标）
- 下卡片「标签操」：四个操作项，左侧圆角方形彩色图标 + 主副标题 + 右箭头
  - 写入数据（蓝）、修改 EPC（绿）、锁定标签（橙）、销毁标签（红）
- 销毁标签：整行红色边框 + 红色标题，点击必须二次确认弹窗（不可恢复）

**T4 我的页**（`MineFragment` + `我的页面.png`）
- 顶部卡片双态：
  - 未登录：用户名输入框（person 图标）+ 密码框（lock 图标）+ 蓝色全宽「登录」按钮
  - 已登录：圆形蓝色头像 + 「admin」+ 绿色「管理员 · 已登」
  - 设计稿同时画了两态，实现为条件切换
- 「功能菜」分组，四个入口，图标配色：实时库存查询（蓝）、订单进度查询（绿）、发货情况与单号（紫）、问题与需求提交（橙）
- 问题与需求提交项带橙色边框高亮
- 登录走 `MockAuthRepository`，任意非空账号密码即成功，token 存 MMKV（已在依赖里）
- **新增主题设置项**（T0.3）：置于「功能菜」分组之外的独立设置区，点击弹三档选择（跟随系统 / 亮色 / 深色），右侧 chip 显示当前值，复用 T1 的 `RfidValueChip`

### 阶段二：网络地基 + 业务四页

**T5 网络层平移**

`gradle/libs.versions.toml` 补版本（框架里已验证过的版本号）：
```toml
easyHttp = "13.6"
okHttp = "5.4.0"
gsonFactory = "10.8"
gson = "2.14.0"
```
从 `AndroidProject-master/app/src/main/java/com/hjq/demo/http/model/` 平移到 `com/leo/remote/http/model/`：
`HttpData`、`HttpListData`、`RequestHandler`、`RequestServer`（改包名）。
`InitManager` 中做 `EasyConfig.with(client).setServer(new RequestServer())...into()`，host 在 `AppConfig` 留占位常量。

> 此步现在就做，因为 `DataCallback` 等类型要出现在 Repository 签名里；等业务页写完再补会返工。

**T6 Repository 接口 + Mock**
- 四个接口 + `DataCallback<T>`，按第 3 节模型
- `MockXxxRepository`：假数据至少覆盖设计稿里出现的全部条目（订单四条 ORD-2024-0851/0732/0904/0615 照抄），含 300–800ms 延迟、空态、失败分支
- 提供 `RepositoryProvider` 单点注入，日后换 HTTP 实现只改这一处

**T7 实时库存查询**（`实时库存查询.png`）
搜索栏 + 右侧筛选按钮 + 卡片列表。最简单，**先做它产出可复用的 list / empty / error / loading 骨架**，后三页复用。

**T8 订单进度查询**（`订单进度查询.png`）
复用 T7 骨架。状态 chip 四色、生产进度条（68% 蓝色）、生产过程记录缩略图组、「已发: 12,000 枚 | 待发: 8,000」高亮。数量千分位格式化。

**T9 发货情况与单号**（`发货情况与单号.png`）
批次时间轴布局、状态 chip、运单号点击复制到剪贴板（复制后 toast 提示）。

**T10 问题与需求提交**（`问题与需求提交.png`）
放最后，是本阶段最重的一块：
- 三选 tab（产品问题 / 订单问题 / 新需求），选中蓝底
- 关联订单（选填）、问题标题、详细描述（多行，聚焦时蓝色边框，右下角红色圆形清除按钮）
- 上传图片最多 6 张：拍照格 + 已选缩略图（带删除角标）+ 添加格
- 需 XXPermissions（已在依赖）申请相机/相册权限，图片要压缩
- 全宽蓝色「提交问题」按钮（带发送图标）

### 阶段三：后端接入（本次不做，仅留接口）

后端就绪后：新增四个 `HttpXxxRepository` → 改 `RepositoryProvider` 注入点 → 登录接真接口 + token 持久化 → 401 刷新/重试/全局错误提示。UI 层不应有任何改动，若需改动说明 T6 的接口设计有问题。

## 5. 通用要求

- **主题安全强制规则**：所有新增布局、style、代码设色必须走 `rfid_*` token 或 `?attr/`，禁止字面色值 `#RRGGBB`。每完成一页须同时在亮/深两主题下验收，无不可读文字。
- 所有列表页必须有 loading / empty / error 三态，不允许白屏
- 数量一律千分位格式化（`50,000`）
- 时间戳格式化统一走一个工具方法，不要各页自己写
- 所有文案进 `strings.xml`，不硬编码在布局或代码里
- 底部导航在八个页面中的高亮态要正确（业务四页从「我的」进入，保持「我的」高亮）
- 每完成一个任务跑一次编译，不要堆到最后
- 设计稿中被截断的标题（如「当前标签信」「标签操」「功率设」「传输方」「速率设」「协议与模」）**照抄，不要自行补全**

## 6. 验收

逐页与 `UI设计/` 下对应 PNG 比对：布局结构、间距、圆角、字号层级、状态配色。

**主题验收**：
- 每页在亮/深两主题下无不可读文字（对比度达 WCAG AA）、无白底白字图标、无深底深字
- 主题切换后不重启 App 即全局生效
- 冷启动直接进入上次选择的主题，无闪烁
- 跟随系统档位在系统主题改变时自动切换

功能验收：
- 配置页各项可改并持久化
- 盘点可启停、计数正确、RSSI 着色正确、CSV 可导出
- 单标签可读取，四个操作入口可进，销毁有二次确认
- 登录态可切换，四个功能入口可跳转，主题设置可切换三档
- 业务四页 Mock 数据正常渲染，三态齐全
- 反馈页可选类型、填表、选 6 张图、提交成功提示

编译验收：`./gradlew assembleDebug` 通过，无新增警告。

