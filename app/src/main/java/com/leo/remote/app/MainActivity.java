package com.leo.remote.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.base.BasePagerAdapter;
import com.hjq.core.tools.DoubleClickHelper;
import com.leo.remote.R;
import com.leo.remote.rfid.demo.ui.common.ReaderAwareActivity;
import com.leo.remote.core.ui.base.BaseFragment;
import com.leo.remote.core.ui.adapter.NavigationAdapter;
import com.leo.remote.core.ui.adapter.NavigationAdapter.NavigationItem;
import com.leo.remote.rfid.demo.ui.inventory.InventoryFragment;
import com.leo.remote.business.auth.ui.MineFragment;
import com.leo.remote.rfid.demo.ui.config.ReaderConfigFragment;
import com.leo.remote.rfid.demo.ui.singletag.SingleTagFragment;
import com.leo.remote.business.stock.ui.StockListFragment;
import com.leo.remote.rfid.sdk.connection.ReaderSessionManager;

/**
 * 应用主界面
 *
 * 采用底部导航 + ViewPager 的架构：
 * - 配置页：Reader 连接和参数配置
 * - 盘点页：批量标签盘点
 * - 单标签页：单个标签读写操作
 * - 库存页：实时库存查询
 * - 我的页：业务功能入口
 *
 * 平板设备使用侧边导航栏（layout-sw600dp-land）
 *
 * 原作者: Android 轮子哥
 * 原项目: https://github.com/getActivity/AndroidProject
 * 修改时间: 2024
 * 修改说明: 基于原框架改造为 RFID 应用
 */
public final class MainActivity extends ReaderAwareActivity
        implements NavigationAdapter.OnNavigationListener {

    private static final String TAG = "UhfRemote/Home";
    private static final String INTENT_KEY_IN_FRAGMENT_INDEX = "fragmentIndex";
    private static final String INTENT_KEY_IN_FRAGMENT_CLASS = "fragmentClass";

    private ViewPager viewPager;
    private RecyclerView navigationView;

    private NavigationAdapter navigationAdapter;
    private BasePagerAdapter<BaseFragment<?>> pagerAdapter;
    private ReaderSessionManager readerSession;
    private int selectedPage;
    private final ViewPager.SimpleOnPageChangeListener pageChangeListener =
            new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    Log.d(TAG, "切换页面: " + position);
                    if (selectedPage == 1 && position != 1 && readerSession != null
                            && readerSession.getState().isInventoryRunning()) {
                        Log.i(TAG, "离开盘点页面，自动停止盘点");
                        readerSession.stopInventory();
                    }
                    selectedPage = position;
                }
            };

    public static void start(@NonNull Context context) {
        start(context, ReaderConfigFragment.class);
    }

    public static void start(@NonNull Context context, @NonNull Class<? extends BaseFragment<?>> fragmentClass) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(INTENT_KEY_IN_FRAGMENT_CLASS, fragmentClass);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.home_activity;
    }

    @Override
    protected void initView() {
        Log.d(TAG, "初始化主界面视图");
        viewPager = findViewById(R.id.vp_home_pager);
        navigationView = findViewById(R.id.rv_home_navigation);

        navigationAdapter = new NavigationAdapter(this);
        navigationAdapter.addItem(new NavigationItem(getString(R.string.home_nav_config),
                ContextCompat.getDrawable(this, R.drawable.rfid_nav_config_ic)));
        navigationAdapter.addItem(new NavigationItem(getString(R.string.home_nav_inventory),
                ContextCompat.getDrawable(this, R.drawable.rfid_nav_inventory_ic)));
        navigationAdapter.addItem(new NavigationItem(getString(R.string.home_nav_tag),
                ContextCompat.getDrawable(this, R.drawable.rfid_nav_tag_ic)));
        navigationAdapter.addItem(new NavigationItem(getString(R.string.home_nav_stock),
                ContextCompat.getDrawable(this, R.drawable.rfid_nav_shipment_ic)));
        navigationAdapter.addItem(new NavigationItem(getString(R.string.home_nav_mine),
                ContextCompat.getDrawable(this, R.drawable.rfid_nav_mine_ic)));
        navigationAdapter.setOnNavigationListener(this);
        navigationView.setAdapter(navigationAdapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {

            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });
    }

    @Override
    protected void initData() {
        Log.d(TAG, "初始化主界面数据，加载 Fragment");
        readerSession = ReaderSessionManager.getInstance(getApplication());
        pagerAdapter = new BasePagerAdapter<>(this);
        pagerAdapter.addFragment(ReaderConfigFragment.newInstance());
        pagerAdapter.addFragment(InventoryFragment.newInstance());
        pagerAdapter.addFragment(SingleTagFragment.newInstance());
        pagerAdapter.addFragment(StockListFragment.newInstance());
        pagerAdapter.addFragment(MineFragment.newInstance());
        viewPager.setAdapter(pagerAdapter);
        viewPager.addOnPageChangeListener(pageChangeListener);

        onNewIntent(getIntent());
    }

    @Nullable
    @Override
    public View getImmersionTopView() {
        return findViewById(R.id.fl_home_root);
    }

    @Nullable
    @Override
    public View getImmersionBottomView() {
        return getResources().getBoolean(R.bool.home_navigation_rail) ? null : navigationView;
    }

    @Override
    protected boolean isStatusBarDarkFont() {
        return isRfidLightTheme();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        switchFragment(pagerAdapter.getFragmentIndex(getSerializable(INTENT_KEY_IN_FRAGMENT_CLASS)));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存当前 Fragment 索引位置
        outState.putInt(INTENT_KEY_IN_FRAGMENT_INDEX, viewPager.getCurrentItem());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 恢复当前 Fragment 索引位置
        switchFragment(savedInstanceState.getInt(INTENT_KEY_IN_FRAGMENT_INDEX));
    }

    private void switchFragment(int fragmentIndex) {
        if (fragmentIndex == -1) {
            return;
        }

        switch (fragmentIndex) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                viewPager.setCurrentItem(fragmentIndex);
                navigationAdapter.setSelectedPosition(fragmentIndex);
                break;
            default:
                break;
        }
    }

    public void showReaderConfig() {
        switchFragment(0);
    }

    /**
     * {@link NavigationAdapter.OnNavigationListener}
     */

    @Override
    public boolean onNavigationItemSelected(int position) {
        Log.d(TAG, "导航栏点击: " + position);
        switch (position) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                viewPager.setCurrentItem(position);
                return true;
            default:
                return false;
        }
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                .statusBarDarkFont(isRfidLightTheme())
                .statusBarColor(R.color.rfid_nav_bg)
                .navigationBarColor(R.color.rfid_nav_bg);
    }

    private void handleBackPressed() {
        if (!DoubleClickHelper.isOnDoubleClick()) {
            toast(R.string.home_exit_hint);
            return;
        }

        // 移动到上一个任务栈
        moveTaskToBack(false);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "销毁主界面，清理资源");
        super.onDestroy();
        viewPager.removeOnPageChangeListener(pageChangeListener);
        viewPager.setAdapter(null);
        navigationView.setAdapter(null);
        navigationAdapter.setOnNavigationListener(null);
    }

}
