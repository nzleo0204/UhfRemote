# UhfRemote 页面结构重构计划 - 库存页面独立

**制定日期**: 2026-08-11  
**优先级**: P1  
**预计时间**: 3-4 小时  
**执行者**: Codex

## 执行状态（2026-08-11）

- ✅ 原 `ShipmentQueryActivity` 内容已迁移到 `ShipmentFragment`，旧 Activity、Manifest
  声明及孤立布局已删除。
- ✅ 底部导航已调整为“配置、盘点、单标签、库存、我的”五项，“我的”页面不再显示
  实时库存查询入口。
- ✅ 库存页下拉刷新、上拉加载和列表状态保留，Debug/Release 构建通过。
- ✅ 真机已验证五项导航、库存列表显示、刷新与滚动；现代帧统计 janky frame 3.69%，
  期间无应用崩溃。

> 下文复选框是原始执行模板，当前状态以上述实测结果为准。

---

## 📋 需求概述

### 当前结构
```
底部导航栏 (4个标签):
├── 配置 (ReaderConfigFragment)
├── 盘点 (InventoryFragment)
├── 单标签 (SingleTagFragment)
└── 我的 (MineFragment)
    └── 点击"实时库存查询" → 跳转到 ShipmentQueryActivity
```

### 目标结构
```
底部导航栏 (5个标签):
├── 配置 (ReaderConfigFragment)
├── 盘点 (InventoryFragment)
├── 单标签 (SingleTagFragment)
├── 库存 (ShipmentFragment) ← 新增，原 ShipmentQueryActivity 内容
└── 我的 (MineFragment)
    └── 移除"实时库存查询"入口
```

### 改动说明
1. ✅ 将 `ShipmentQueryActivity` 的内容转换为 `ShipmentFragment`
2. ✅ 在底部导航栏添加"库存"标签（第4个位置）
3. ✅ 从"我的"页面移除"实时库存查询"入口
4. ✅ 更新导航图标和文案
5. ✅ 保持其他功能不变

---

## 🎯 Task 1: 创建 ShipmentFragment

### Step 1.1: 创建 Fragment 类

**文件**: `app/src/main/java/com/leo/remote/ui/fragment/home/ShipmentFragment.java`

**完整代码**:

```java
package com.leo.remote.ui.fragment.home;

import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.app.AppFragment;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.Shipment;
import com.leo.remote.data.repository.RepositoryProvider;
import com.leo.remote.ui.adapter.ShipmentAdapter;
import com.leo.remote.widget.StatusLayout;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import java.util.List;

/**
 * 库存查询页面
 * 
 * 显示实时库存信息，支持下拉刷新和上拉加载更多。
 * 原 ShipmentQueryActivity 的内容迁移到此 Fragment。
 */
public final class ShipmentFragment extends AppFragment<HomeActivity> {
    
    private static final String TAG = "ShipmentFragment";
    
    private SmartRefreshLayout refreshLayout;
    private RecyclerView recyclerView;
    private StatusLayout statusLayout;
    private ShipmentAdapter adapter;
    
    private boolean isLoading = false;
    
    public static ShipmentFragment newInstance() {
        return new ShipmentFragment();
    }
    
    @Override
    protected int getLayoutId() {
        return R.layout.shipment_fragment;
    }
    
    @Override
    protected void initView() {
        Log.d(TAG, "初始化库存页面");
        
        refreshLayout = findViewById(R.id.srl_shipment);
        recyclerView = findViewById(R.id.rv_shipment);
        statusLayout = findViewById(R.id.sl_shipment_status);
        
        // 配置 RecyclerView
        adapter = new ShipmentAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        
        // 配置下拉刷新
        refreshLayout.setOnRefreshListener(refreshLayout -> {
            Log.d(TAG, "下拉刷新库存数据");
            loadShipments();
        });
        
        // 配置上拉加载
        refreshLayout.setOnLoadMoreListener(refreshLayout -> {
            Log.d(TAG, "上拉加载更多库存数据");
            loadMoreShipments();
        });
        
        // 初始加载
        loadShipments();
    }
    
    @Override
    protected void initData() {
        // 数据加载在 initView 中处理
    }
    
    /**
     * 加载库存数据（刷新）
     */
    private void loadShipments() {
        if (isLoading) {
            return;
        }
        
        isLoading = true;
        statusLayout.showLoading();
        
        RepositoryProvider.shipment().queryShipments("", null, new DataCallback<List<Shipment>>() {
            @Override
            public void onSuccess(List<Shipment> shipments) {
                isLoading = false;
                refreshLayout.finishRefresh();
                
                if (shipments == null || shipments.isEmpty()) {
                    Log.d(TAG, "库存数据为空");
                    statusLayout.showEmpty();
                    adapter.clearData();
                } else {
                    Log.d(TAG, "加载到 " + shipments.size() + " 条库存数据");
                    statusLayout.showComplete();
                    adapter.setData(shipments);
                }
            }
            
            @Override
            public void onFailure(String error) {
                isLoading = false;
                refreshLayout.finishRefresh();
                
                Log.e(TAG, "加载库存数据失败: " + error);
                statusLayout.showError();
                toast(R.string.shipment_load_failed);
            }
        });
    }
    
    /**
     * 加载更多库存数据
     */
    private void loadMoreShipments() {
        // TODO: 实现分页加载
        refreshLayout.finishLoadMore();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "库存页面恢复");
    }
    
    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "库存页面暂停");
    }
}
```

---

### Step 1.2: 创建 Fragment 布局文件

**文件**: `app/src/main/res/layout/shipment_fragment.xml`

**完整代码**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/white">

    <com.scwang.smart.refresh.layout.SmartRefreshLayout
        android:id="@+id/srl_shipment"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rv_shipment"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:overScrollMode="never"
            android:scrollbars="none" />

    </com.scwang.smart.refresh.layout.SmartRefreshLayout>

    <!-- 状态布局：加载中、空数据、错误 -->
    <com.leo.remote.widget.StatusLayout
        android:id="@+id/sl_shipment_status"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone"
        app:emptyText="@string/shipment_empty"
        app:errorText="@string/shipment_load_failed" />

</FrameLayout>
```

---

### Step 1.3: 添加字符串资源

**文件**: `app/src/main/res/values/strings.xml`

**添加（如果不存在）**:

```xml
<!-- 库存页面 -->
<string name="home_nav_shipment">库存</string>
<string name="shipment_title">库存查询</string>
<string name="shipment_empty">暂无库存数据</string>
<string name="shipment_load_failed">加载库存数据失败</string>
```

---

## 🎯 Task 2: 添加导航栏图标

### Step 2.1: 准备图标资源

**检查是否已有图标**:
```bash
find app/src/main/res/drawable -name "*shipment*" -o -name "*stock*" -o -name "*inventory*"
```

**如果没有，创建图标**:

**文件**: `app/src/main/res/drawable/rfid_nav_shipment_ic.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M20,2H4C3,2 2,2.9 2,4v3.01C2,7.73 2.43,8.35 3,8.7V20c0,1.1 1.1,2 2,2h14c0.9,0 2,-0.9 2,-2V8.7c0.57,-0.35 1,-0.97 1,-1.69V4C22,2.9 21,2 20,2zM19,20H5V9h14V20zM20,7H4V4h16V7z"/>
    <path
        android:fillColor="@android:color/white"
        android:pathData="M9,12h6v2H9z"/>
    <path
        android:fillColor="@android:color/white"
        android:pathData="M9,15h6v2H9z"/>
</vector>
```

**或者使用 Material Icons 中的 `inventory` 图标**:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M20,2L4,2c-1,0 -2,0.9 -2,1.99L2,20c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM20,20L4,20L4,7h16v13zM7,10h10v2L7,12zM7,14h7v2L7,16z"/>
</vector>
```

---

## 🎯 Task 3: 修改 HomeActivity

### Step 3.1: 添加 ShipmentFragment 到导航

**文件**: `app/src/main/java/com/leo/remote/ui/activity/HomeActivity.java`

**修改 `initData()` 方法**:

```java
@Override
protected void initData() {
    Log.d(TAG, "初始化主界面数据，加载 Fragment");
    readerSession = ReaderSessionManager.getInstance(getApplication());
    pagerAdapter = new BasePagerAdapter<>(this);
    
    // 添加 Fragment（注意顺序）
    pagerAdapter.addFragment(ReaderConfigFragment.newInstance());  // 0: 配置
    pagerAdapter.addFragment(InventoryFragment.newInstance());      // 1: 盘点
    pagerAdapter.addFragment(SingleTagFragment.newInstance());      // 2: 单标签
    pagerAdapter.addFragment(ShipmentFragment.newInstance());       // 3: 库存 ← 新增
    pagerAdapter.addFragment(MineFragment.newInstance());           // 4: 我的
    
    viewPager.setAdapter(pagerAdapter);
    viewPager.addOnPageChangeListener(pageChangeListener);

    onNewIntent(getIntent());
}
```

### Step 3.2: 添加导航栏图标

**修改 `initView()` 方法**:

```java
@Override
protected void initView() {
    Log.d(TAG, "初始化主界面视图");
    viewPager = findViewById(R.id.vp_home_pager);
    navigationView = findViewById(R.id.rv_home_navigation);

    navigationAdapter = new NavigationAdapter(this);
    
    // 添加导航项（注意顺序）
    navigationAdapter.addItem(new NavigationItem(
            getString(R.string.home_nav_config),
            ContextCompat.getDrawable(this, R.drawable.rfid_nav_config_ic)));
    
    navigationAdapter.addItem(new NavigationItem(
            getString(R.string.home_nav_inventory),
            ContextCompat.getDrawable(this, R.drawable.rfid_nav_inventory_ic)));
    
    navigationAdapter.addItem(new NavigationItem(
            getString(R.string.home_nav_tag),
            ContextCompat.getDrawable(this, R.drawable.rfid_nav_tag_ic)));
    
    // 新增：库存
    navigationAdapter.addItem(new NavigationItem(
            getString(R.string.home_nav_shipment),
            ContextCompat.getDrawable(this, R.drawable.rfid_nav_shipment_ic)));
    
    navigationAdapter.addItem(new NavigationItem(
            getString(R.string.home_nav_mine),
            ContextCompat.getDrawable(this, R.drawable.rfid_nav_mine_ic)));
    
    navigationAdapter.setOnNavigationListener(this);
    navigationView.setAdapter(navigationAdapter);

    // 设置禁用后退键回调
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            handleBackPressed();
        }
    });
}
```

### Step 3.3: 更新导航点击处理

**修改 `onNavigationItemSelected()` 方法**:

```java
@Override
public boolean onNavigationItemSelected(int position) {
    Log.d(TAG, "导航栏点击: " + position);
    switch (position) {
        case 0:  // 配置
        case 1:  // 盘点
        case 2:  // 单标签
        case 3:  // 库存 ← 新增
        case 4:  // 我的
            viewPager.setCurrentItem(position);
            return true;
        default:
            return false;
    }
}
```

### Step 3.4: 更新 switchFragment 方法

**修改 `switchFragment()` 方法**:

```java
private void switchFragment(int fragmentIndex) {
    if (fragmentIndex == -1) {
        return;
    }

    switch (fragmentIndex) {
        case 0:  // 配置
        case 1:  // 盘点
        case 2:  // 单标签
        case 3:  // 库存 ← 新增
        case 4:  // 我的
            viewPager.setCurrentItem(fragmentIndex);
            navigationAdapter.setSelectedPosition(fragmentIndex);
            break;
        default:
            break;
    }
}
```

### Step 3.5: 更新页面切换监听

**修改 `pageChangeListener`**:

```java
private final ViewPager.SimpleOnPageChangeListener pageChangeListener =
        new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                Log.d(TAG, "切换页面: " + position);
                
                // 离开盘点页面时，自动停止盘点
                if (selectedPage == 1 && position != 1 && readerSession != null
                        && readerSession.getState().isInventoryRunning()) {
                    Log.i(TAG, "离开盘点页面，自动停止盘点");
                    readerSession.stopInventory();
                }
                
                selectedPage = position;
            }
        };
```

---

## 🎯 Task 4: 修改 MineFragment

### Step 4.1: 移除"实时库存查询"入口

**文件**: `app/src/main/java/com/leo/remote/ui/fragment/home/MineFragment.java`

**删除导入**:
```java
// 删除这行
import com.leo.remote.ui.activity.ShipmentQueryActivity;
```

**删除点击监听**:

找到并**注释或删除**这行代码:
```java
// 删除或注释这行
// findViewById(R.id.ll_mine_shipment).setOnClickListener(v -> ShipmentQueryActivity.start(getAttachActivity()));
```

### Step 4.2: 修改布局文件（可选）

**文件**: `app/src/main/res/layout/mine_fragment.xml`

**如果要完全移除该入口，找到并删除或隐藏**:

```xml
<!-- 找到这个 LinearLayout 并删除或设置 visibility="gone" -->
<LinearLayout
    android:id="@+id/ll_mine_shipment"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone">
    <!-- ... -->
</LinearLayout>
```

**或者保留布局但不设置点击事件，由用户决定**

---

## 🎯 Task 5: 更新平板布局（如果有）

### Step 5.1: 检查是否有平板布局

```bash
find app/src/main/res -name "home_activity.xml"
```

### Step 5.2: 如果有 `layout-sw600dp-land/home_activity.xml`

**需要同步更新导航栏配置**，确保平板横屏布局也支持5个标签。

**检查 `values-sw600dp-land/bools.xml`**:
```xml
<bool name="home_navigation_rail">true</bool>
```

**平板布局通常使用侧边导航栏，确保有足够空间显示5个标签**

---

## 📋 完整执行流程

### Phase 1: 创建新页面 (1.5 小时)

**Step 1: 创建 ShipmentFragment**
```bash
# 创建 Java 文件
touch app/src/main/java/com/leo/remote/ui/fragment/home/ShipmentFragment.java

# 复制上面的完整代码到文件中
```

**Step 2: 创建布局文件**
```bash
# 创建 XML 文件
touch app/src/main/res/layout/shipment_fragment.xml

# 复制上面的 XML 代码到文件中
```

**Step 3: 添加字符串资源**
- 编辑 `strings.xml`
- 添加 `home_nav_shipment` 等字符串

**Step 4: 创建导航图标**
- 创建 `rfid_nav_shipment_ic.xml`
- 复制图标代码

**Step 5: 编译验证**
```bash
./gradlew compileDebugJavaWithJavac
```

---

### Phase 2: 修改导航结构 (1 小时)

**Step 1: 修改 HomeActivity**
- 在 `initData()` 中添加 ShipmentFragment
- 在 `initView()` 中添加导航项
- 更新 `onNavigationItemSelected()`
- 更新 `switchFragment()`

**Step 2: 编译验证**
```bash
./gradlew compileDebugJavaWithJavac
```

---

### Phase 3: 清理旧入口 (30 分钟)

**Step 1: 修改 MineFragment**
- 删除 ShipmentQueryActivity 导入
- 删除点击监听代码

**Step 2: （可选）修改布局**
- 隐藏或删除"实时库存查询"入口

**Step 3: 编译验证**
```bash
./gradlew compileDebugJavaWithJavac
```

---

### Phase 4: 测试验证 (1 小时)

#### 编译测试
```bash
./gradlew clean
./gradlew assembleDebug
```

#### 功能测试清单

**导航测试**:
- [ ] 底部导航栏显示5个标签
- [ ] 标签顺序正确：配置、盘点、单标签、库存、我的
- [ ] 库存图标显示正确
- [ ] 点击各标签可正常切换

**库存页面测试**:
- [ ] 进入库存页面显示加载状态
- [ ] 加载成功显示数据列表
- [ ] 数据为空显示空状态
- [ ] 加载失败显示错误状态
- [ ] 下拉刷新功能正常
- [ ] 列表滚动正常

**我的页面测试**:
- [ ] "实时库存查询"入口已移除或隐藏
- [ ] 其他功能入口正常

**页面切换测试**:
- [ ] 配置 ↔ 盘点 ↔ 单标签 ↔ 库存 ↔ 我的 切换正常
- [ ] 离开盘点页面时自动停止盘点
- [ ] 返回库存页面数据状态保持

**平板测试**（如果有）:
- [ ] 平板横屏布局正常
- [ ] 侧边导航栏显示5个标签

---

## 🎯 Task 6: 提交代码

### Git 提交

```bash
# 添加所有修改
git add -A

# 提交
git commit -m "feat: 重构页面结构，库存页面独立为导航标签

重构内容：
1. 创建 ShipmentFragment
   - 从 ShipmentQueryActivity 迁移功能
   - 完整的下拉刷新和数据加载
   - 状态管理（加载中、空数据、错误）
   
2. 更新底部导航栏
   - 新增"库存"标签（第4个位置）
   - 添加库存导航图标
   - 更新导航逻辑支持5个标签
   
3. 清理旧入口
   - 从"我的"页面移除"实时库存查询"
   - 删除相关跳转代码

修改文件：
+ ShipmentFragment.java (新建)
+ shipment_fragment.xml (新建)
+ rfid_nav_shipment_ic.xml (新建)
~ HomeActivity.java (修改)
~ MineFragment.java (修改)
~ strings.xml (添加资源)

导航结构变化：
- 原 4 个标签 → 5 个标签
- 顺序：配置、盘点、单标签、库存、我的

测试验证：
- 编译通过
- 导航切换正常
- 库存页面功能正常
- 其他页面不受影响

Co-Authored-By: Codex <codex@anthropic.com>"
```

---

## 📊 变更影响分析

### 新增文件
```
+ app/src/main/java/com/leo/remote/ui/fragment/home/ShipmentFragment.java
+ app/src/main/res/layout/shipment_fragment.xml
+ app/src/main/res/drawable/rfid_nav_shipment_ic.xml
```

### 修改文件
```
~ app/src/main/java/com/leo/remote/ui/activity/HomeActivity.java
~ app/src/main/java/com/leo/remote/ui/fragment/home/MineFragment.java
~ app/src/main/res/values/strings.xml
~ app/src/main/res/layout/mine_fragment.xml (可选)
```

### 不变的文件
```
= ShipmentQueryActivity.java (保留，但不再使用)
= PagedQueryActivity.java (基类，保留)
= ShipmentAdapter.java (适配器，复用)
= 其他所有文件
```

---

## ⚠️ 注意事项

### 1. 兼容性
- ✅ ShipmentQueryActivity 保留但不使用（向后兼容）
- ✅ 可随时回退（恢复 MineFragment 的跳转代码）

### 2. 数据共享
- ShipmentFragment 和 ShipmentQueryActivity 使用相同的：
  - ShipmentAdapter
  - RepositoryProvider.shipment()
  - Shipment 数据模型

### 3. 布局复用
- 复用 ShipmentQueryActivity 的布局结构
- 复用下拉刷新组件
- 复用状态布局

### 4. 性能考虑
- Fragment 生命周期管理
- 数据缓存（避免重复加载）
- 内存优化

---

## 🎨 UI 效果示意

### 底部导航栏（修改后）

```
┌─────────────────────────────────────┐
│  ⚙️      📦      🏷️      📊      👤  │
│ 配置    盘点    单标签    库存     我的 │ ← 新增"库存"
└─────────────────────────────────────┘
```

### 库存页面

```
┌─────────────────────────────────────┐
│ ← 库存                               │
├─────────────────────────────────────┤
│ 🔄 下拉刷新...                       │
│                                      │
│ ┌─────────────────────────────────┐ │
│ │ 订单号: SO202611001             │ │
│ │ 商品: iPhone 15 Pro             │ │
│ │ 数量: 100                       │ │
│ │ 状态: 已入库                    │ │
│ └─────────────────────────────────┘ │
│                                      │
│ ┌─────────────────────────────────┐ │
│ │ 订单号: SO202611002             │ │
│ │ ...                             │ │
│ └─────────────────────────────────┘ │
│                                      │
│ 上拉加载更多...                      │
└─────────────────────────────────────┘
```

---

## ✅ 验收标准

### 功能验收
- [ ] 底部导航栏显示5个标签
- [ ] 点击"库存"标签进入库存页面
- [ ] 库存页面显示数据列表
- [ ] 下拉刷新功能正常
- [ ] 页面切换流畅
- [ ] "我的"页面已移除"实时库存查询"

### 代码质量
- [ ] 编译无错误
- [ ] 编译无警告
- [ ] 代码风格一致
- [ ] 注释完整

### 用户体验
- [ ] 导航图标清晰
- [ ] 页面加载流畅
- [ ] 状态反馈及时
- [ ] 交互逻辑合理

---

**计划制定**: 2026-08-11  
**执行者**: Codex  
**预计完成**: 3-4 小时

**开始执行前请确认已理解所有需求！**
