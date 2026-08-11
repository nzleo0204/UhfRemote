# UhfRemote UI 改进计划 - 单标签页面增强

**制定日期**: 2026-08-11  
**优先级**: P1  
**预计时间**: 4-6 小时  
**执行者**: Codex

## 执行状态（2026-08-11）

- ✅ 单标签页已监听 `ReaderConfiguration`，协议/库存区域变化会同步更新 ID 与数据列标题。
- ✅ 掩码状态始终显示“已启用/未启用”，未启用使用灰色状态，启用使用绿色状态。
- ✅ 当前标签卡片右上角已增加掩码锁图标，并同步切换图标、颜色和无障碍描述。
- ✅ 编译、单元测试、Lint、Debug/Release 构建均通过。
- ✅ 真机已验证假数据颜色、未启用状态和打开锁图标，页面无重叠、无崩溃。
- ⚠️ 启用掩码后的真实锁定状态需要连接 RFID 读写器后做硬件验收。

> 下文复选框是原始执行模板，当前状态以上述实测结果为准。

---

## 📋 需求概述

### 需求1: 单标签页面与配置页面联动
**问题**: 单标签页面未监听 Reader 配置变化，导致显示数据格式不更新

**目标**: 参考盘点页面，监听配置变化并更新 UI

### 需求2: 掩码启用/禁用状态标识
**问题**: 掩码面板缺少明确的启用/禁用状态指示

**目标**: 
- 参考盘点页面，添加掩码状态切换按钮
- 显示"已启用"/"未启用"状态文本

### 需求3: 当前标签卡片锁定状态图标
**问题**: 无法直观看出标签读取是否受掩码过滤影响

**目标**:
- 当前标签信息卡片右上角添加锁图标
- 未启用掩码：锁打开状态（unlocked）
- 已启用掩码：锁关闭状态（locked）

---

## 🎯 Task 1: 单标签页面监听配置变化

### 问题分析

查看 `InventoryFragment.java`:
```java
@Override
public void onReaderConfigurationChanged(ReaderConfiguration configuration) {
    this.configuration = configuration;
    updateDataHeaders();  // 更新数据格式显示
}
```

查看 `SingleTagFragment.java`:
```java
// 当前没有实现 onReaderConfigurationChanged
```

### 实现步骤

#### Step 1: 在 SingleTagFragment 添加配置字段

**文件**: `app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java`

**位置**: 在 Fields 区域添加

```java
private ReaderState readerState = ReaderState.disconnected();
private ReaderConfiguration configuration;  // 添加这行
private InventoryMaskConfig activeMask;
```

#### Step 2: 实现配置变化监听

**位置**: 在 ReaderObserver 实现方法区域添加

```java
@Override
public void onReaderConfigurationChanged(ReaderConfiguration configuration) {
    Log.d(TAG, "Reader 配置已更新");
    this.configuration = configuration;
    updateTagDisplay();  // 更新标签显示格式
}
```

#### Step 3: 创建 updateTagDisplay 方法

**位置**: 在私有方法区域添加

```java
/**
 * 根据配置更新标签显示格式
 */
private void updateTagDisplay() {
    if (currentTag == null) {
        return;
    }
    
    // 根据配置显示 TID 或 USER 数据
    if (configuration != null) {
        ProtocolEncoding encoding = configuration.getEncoding();
        
        // 更新数据显示
        if (tidView != null && currentTag.hasTid()) {
            String tidText = HexCodec.encode(currentTag.getTid());
            tidView.setText(tidText);
        }
        
        // 如果有 USER 区数据也更新
        // ... 其他数据格式更新
    }
}
```

#### Step 4: 在 onCurrentTagChanged 中调用

**修改**: 现有的 `onCurrentTagChanged` 方法

```java
@Override
public void onCurrentTagChanged(ReaderTag tag) {
    this.currentTag = tag;
    updateTagDisplay();  // 使用新方法
    updateActions();
}
```

### 验收标准
- [ ] 配置页面修改协议/编码后，单标签页面数据格式同步更新
- [ ] 不影响现有功能
- [ ] 编译通过

---

## 🎯 Task 2: 添加掩码启用/禁用状态标识

### 参考实现

查看 `InventoryFragment.java` 中的掩码状态处理:

```java
// 掩码状态文本
private TextView maskStatusView;

// 掩码切换按钮
private MaterialButton maskToggleButton;

// 更新掩码状态显示
private void updateMaskStatus() {
    if (activeMask != null) {
        maskStatusView.setText("已启用");
        maskStatusView.setTextColor(/* 绿色 */);
        maskToggleButton.setText("禁用");
    } else {
        maskStatusView.setText("未启用");
        maskStatusView.setTextColor(/* 灰色 */);
        maskToggleButton.setText("启用");
    }
}
```

### 实现步骤

#### Step 1: 检查布局文件是否已有相关控件

**文件**: `app/src/main/res/layout/single_tag_fragment.xml`

**查找**: 
- `tv_inventory_mask_status` (已存在)
- `btn_inventory_mask_toggle` (已存在)

**确认**: SingleTagFragment 已有这些控件的引用

#### Step 2: 实现掩码状态切换逻辑

**文件**: `SingleTagFragment.java`

**在 initView() 中添加按钮点击监听**:

```java
@Override
protected void initView() {
    // ... 现有代码
    
    // 添加掩码切换按钮点击事件
    maskToggleButton.setOnClickListener(v -> {
        if (activeMask != null) {
            // 当前已启用，执行禁用
            clearMask();
        } else {
            // 当前未启用，执行启用
            applyMask();
        }
    });
}
```

#### Step 3: 创建掩码状态更新方法

```java
/**
 * 更新掩码状态显示
 */
private void updateMaskStatus() {
    if (activeMask != null) {
        // 已启用掩码
        maskStatusView.setText(R.string.inventory_mask_enabled);
        maskStatusView.setTextColor(ContextCompat.getColor(
                requireContext(), R.color.rfid_success));
        maskToggleButton.setText(R.string.inventory_mask_disable);
        maskToggleButton.setIcon(ContextCompat.getDrawable(
                requireContext(), R.drawable.ic_check_circle));
    } else {
        // 未启用掩码
        maskStatusView.setText(R.string.inventory_mask_disabled);
        maskStatusView.setTextColor(ContextCompat.getColor(
                requireContext(), R.color.rfid_text_muted));
        maskToggleButton.setText(R.string.inventory_mask_enable);
        maskToggleButton.setIcon(ContextCompat.getDrawable(
                requireContext(), R.drawable.ic_cancel));
    }
}
```

#### Step 4: 在关键位置调用状态更新

```java
@Override
public void onSingleTagMaskChanged(@Nullable InventoryMaskConfig config) {
    this.activeMask = config;
    updateMaskStatus();        // 添加这行
    updateMaskControls();
}

private void applyMask() {
    // ... 应用掩码逻辑
    // 成功后会触发 onSingleTagMaskChanged
}

private void clearMask() {
    // ... 清除掩码逻辑
    // 成功后会触发 onSingleTagMaskChanged
}
```

### 需要添加的字符串资源

**文件**: `app/src/main/res/values/strings.xml`

```xml
<!-- 如果不存在则添加 -->
<string name="inventory_mask_enabled">已启用</string>
<string name="inventory_mask_disabled">未启用</string>
<string name="inventory_mask_enable">启用</string>
<string name="inventory_mask_disable">禁用</string>
```

### 验收标准
- [ ] 掩码面板显示"已启用"/"未启用"状态
- [ ] 切换按钮显示"启用"/"禁用"文本
- [ ] 点击切换按钮可切换状态
- [ ] 状态文本颜色正确（已启用=绿色，未启用=灰色）

---

## 🎯 Task 3: 当前标签卡片添加锁定状态图标

### UI 设计

**位置**: 当前标签信息卡片右上角

**图标**:
- 未启用掩码：`ic_lock_open` (锁打开)
- 已启用掩码：`ic_lock` (锁关闭)

**颜色**:
- 未启用掩码：灰色 (`rfid_text_muted`)
- 已启用掩码：橙色 (`rfid_warning`)

### 实现步骤

#### Step 1: 检查并添加图标资源

**检查是否已有图标**:
```bash
find app/src/main/res/drawable -name "*lock*"
```

**如果没有，添加矢量图标**:

**文件**: `app/src/main/res/drawable/ic_lock.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10C20,8.9 19.1,8 18,8zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2s2,0.9 2,2S13.1,17 12,17zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1c1.71,0 3.1,1.39 3.1,3.1V8z"/>
</vector>
```

**文件**: `app/src/main/res/drawable/ic_lock_open.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h1.9c0,-1.71 1.39,-3.1 3.1,-3.1c1.71,0 3.1,1.39 3.1,3.1v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10C20,8.9 19.1,8 18,8zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2s2,0.9 2,2S13.1,17 12,17z"/>
</vector>
```

#### Step 2: 修改布局文件添加图标

**文件**: `app/src/main/res/layout/single_tag_fragment.xml`

**查找当前标签卡片布局** (通常是包含 EPC、TID 等信息的 CardView 或 LinearLayout)

**在卡片右上角添加 ImageView**:

```xml
<!-- 在当前标签信息卡片内部 -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cv_single_tag_info"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">
        
        <!-- 添加标题栏 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">
            
            <TextView
                android:id="@+id/tv_single_tag_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/single_tag_current_tag"
                android:textSize="16sp"
                android:textStyle="bold"/>
            
            <!-- 锁定状态图标 -->
            <ImageView
                android:id="@+id/iv_single_mask_lock"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:src="@drawable/ic_lock_open"
                android:tint="@color/rfid_text_muted"
                android:contentDescription="@string/single_tag_mask_status"/>
        </LinearLayout>
        
        <!-- 原有的 EPC、TID 等信息 -->
        <!-- ... -->
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

#### Step 3: 在 Fragment 中添加图标引用

**文件**: `SingleTagFragment.java`

**在 Fields 区域添加**:
```java
private ImageView maskLockIcon;  // 掩码锁定图标
```

**在 initView() 中初始化**:
```java
@Override
protected void initView() {
    // ... 现有代码
    maskLockIcon = findViewById(R.id.iv_single_mask_lock);
}
```

#### Step 4: 创建图标更新方法

```java
/**
 * 更新掩码锁定图标
 */
private void updateMaskLockIcon() {
    if (maskLockIcon == null) {
        return;
    }
    
    if (activeMask != null) {
        // 已启用掩码 - 锁关闭
        maskLockIcon.setImageResource(R.drawable.ic_lock);
        maskLockIcon.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.rfid_warning)));
        maskLockIcon.setContentDescription(getString(R.string.single_tag_mask_locked));
    } else {
        // 未启用掩码 - 锁打开
        maskLockIcon.setImageResource(R.drawable.ic_lock_open);
        maskLockIcon.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.rfid_text_muted)));
        maskLockIcon.setContentDescription(getString(R.string.single_tag_mask_unlocked));
    }
}
```

#### Step 5: 在状态变化时更新图标

```java
@Override
public void onSingleTagMaskChanged(@Nullable InventoryMaskConfig config) {
    this.activeMask = config;
    updateMaskStatus();
    updateMaskLockIcon();  // 添加这行
    updateMaskControls();
}
```

#### Step 6: 添加字符串资源

**文件**: `app/src/main/res/values/strings.xml`

```xml
<string name="single_tag_current_tag">当前标签</string>
<string name="single_tag_mask_status">掩码状态</string>
<string name="single_tag_mask_locked">掩码已启用</string>
<string name="single_tag_mask_unlocked">掩码未启用</string>
```

### 验收标准
- [ ] 当前标签卡片右上角显示锁图标
- [ ] 未启用掩码：显示打开的锁（灰色）
- [ ] 已启用掩码：显示关闭的锁（橙色）
- [ ] 切换掩码状态时图标同步更新
- [ ] 无障碍描述正确

---

## 📋 完整执行流程

### Phase 1: 准备工作 (15分钟)

1. **备份当前代码**
```bash
git checkout -b feature/single-tag-improvements
git add -A
git commit -m "feat: 准备单标签页面改进

- 创建功能分支
- 备份当前代码
"
```

2. **检查相关文件**
```bash
# 确认文件存在
ls app/src/main/java/com/leo/remote/ui/fragment/home/SingleTagFragment.java
ls app/src/main/res/layout/single_tag_fragment.xml
ls app/src/main/res/values/strings.xml
```

---

### Phase 2: 实现功能 (3-4小时)

#### 任务顺序

1. **Task 1: 配置联动** (1小时)
   - [ ] 添加 configuration 字段
   - [ ] 实现 onReaderConfigurationChanged
   - [ ] 创建 updateTagDisplay 方法
   - [ ] 编译验证

2. **Task 2: 掩码状态标识** (1.5小时)
   - [ ] 添加按钮点击监听
   - [ ] 实现 updateMaskStatus 方法
   - [ ] 添加字符串资源
   - [ ] 测试切换功能
   - [ ] 编译验证

3. **Task 3: 锁定图标** (1.5小时)
   - [ ] 添加/检查图标资源
   - [ ] 修改布局文件
   - [ ] 实现 updateMaskLockIcon 方法
   - [ ] 集成到状态变化回调
   - [ ] 编译验证

---

### Phase 3: 测试验证 (1-2小时)

#### 单元测试

```bash
./gradlew test
```

#### 编译测试

```bash
./gradlew compileDebugJavaWithJavac
./gradlew assembleDebug
```

#### 功能测试清单

**配置联动测试**:
- [ ] 在配置页面修改协议（6C ↔ 6B）
- [ ] 切换到单标签页面
- [ ] 验证标签数据显示格式已更新
- [ ] 读取标签验证数据正确

**掩码状态测试**:
- [ ] 填写掩码参数
- [ ] 点击"启用"按钮
- [ ] 验证状态显示"已启用"（绿色）
- [ ] 验证按钮显示"禁用"
- [ ] 点击"禁用"按钮
- [ ] 验证状态显示"未启用"（灰色）
- [ ] 验证按钮显示"启用"

**锁定图标测试**:
- [ ] 未启用掩码时，图标显示打开的锁（灰色）
- [ ] 启用掩码后，图标显示关闭的锁（橙色）
- [ ] 禁用掩码后，图标恢复打开的锁（灰色）
- [ ] 切换页面后图标状态保持正确

---

### Phase 4: 提交代码 (15分钟)

```bash
# 添加所有修改
git add -A

# 提交
git commit -m "feat: 单标签页面功能增强

实现内容：
1. 添加与配置页面的联动
   - 监听 Reader 配置变化
   - 同步更新标签数据显示格式
   
2. 添加掩码启用/禁用状态标识
   - 参考盘点页面实现
   - 显示"已启用"/"未启用"状态
   - 支持一键切换掩码状态
   
3. 当前标签卡片添加锁定图标
   - 右上角显示锁状态图标
   - 未启用掩码：锁打开（灰色）
   - 已启用掩码：锁关闭（橙色）

修改文件：
- SingleTagFragment.java
- single_tag_fragment.xml
- strings.xml
- 新增 ic_lock.xml, ic_lock_open.xml

测试验证：
- 编译通过
- 功能测试通过
- 状态切换正常

Co-Authored-By: Codex <codex@anthropic.com>"

# 合并到主分支
git checkout main
git merge feature/single-tag-improvements --no-ff

# 删除功能分支
git branch -d feature/single-tag-improvements
```

---

## 🎨 UI 效果示意

### 掩码面板状态

```
┌─────────────────────────────────────┐
│ 掩码过滤                   ▼        │
├─────────────────────────────────────┤
│ 状态: [已启用] 🟢                    │
│                                      │
│ 存储区: EPC                          │
│ 偏移(bit): 32                        │
│ 长度(bit): 96                        │
│ HEX: E280116060000...                │
│                                      │
│          [禁用掩码] ✓                │
└─────────────────────────────────────┘
```

### 当前标签卡片

```
┌─────────────────────────────────────┐
│ 当前标签                     🔒     │ ← 锁图标（橙色=已锁定）
├─────────────────────────────────────┤
│ EPC: E28011606000020933A55D0D       │
│ TID: E2801160600002...              │
│ 芯片: Impinj M750                   │
│ RSSI: -45 dBm                       │
└─────────────────────────────────────┘
```

---

## ⚠️ 注意事项

### 1. 代码风格
- 遵循现有代码风格
- 使用中文注释
- 方法命名清晰

### 2. 兼容性
- 不破坏现有功能
- 保持向后兼容
- 测试多种场景

### 3. 性能考虑
- 避免频繁更新UI
- 状态变化时才更新图标
- 合理使用缓存

### 4. 用户体验
- 状态切换立即反馈
- 图标颜色对比明显
- 提示文本清晰易懂

---

## 📊 预期成果

### 功能改进
- ✅ 单标签页面与配置页面实现联动
- ✅ 掩码状态清晰可见
- ✅ 一键切换掩码启用/禁用
- ✅ 锁定状态图标直观展示

### 代码质量
- ✅ 参考盘点页面实现，代码一致性好
- ✅ 完整的状态管理
- ✅ 清晰的视觉反馈

### 用户体验
- ✅ 降低用户困惑
- ✅ 提升操作效率
- ✅ 增强视觉反馈

---

**计划制定**: 2026-08-11  
**执行者**: Codex  
**预计完成**: 4-6 小时

**开始执行前请确认已理解所有需求！**
