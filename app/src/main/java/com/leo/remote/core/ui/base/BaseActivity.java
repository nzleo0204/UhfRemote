package com.leo.remote.core.ui.base;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.bar.TitleBar;
import com.leo.remote.R;
import com.leo.remote.core.action.ImmersionAction;
import com.leo.remote.core.action.TitleBarAction;
import com.leo.remote.core.action.ToastAction;
import com.leo.remote.core.ui.dialog.WaitDialog;
import com.leo.remote.core.util.ThemeModeManager;

/** Activity 业务基类 */
public abstract class BaseActivity extends com.hjq.base.BaseActivity
    implements ToastAction, TitleBarAction, ImmersionAction {

    /** 标题栏对象 */
    private TitleBar titleBar;
    /** 状态栏沉浸 */
    private ImmersionBar immersionBar;

    /** 加载对话框 */
    private WaitDialog.Builder dialog;
    /** 对话框数量 */
    private int dialogCount;

    /**
     * 当前加载对话框是否在显示中
     */
    public boolean isShowDialog() {
        return dialog != null && dialog.isShowing();
    }

    /**
     * 显示加载对话框
     */
    public void showLoadingDialog() {
        showLoadingDialog(getString(R.string.common_loading));
    }

    public void showLoadingDialog(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        dialogCount++;
        postDelayed(() -> {
            if (dialogCount <= 0 || isFinishing() || isDestroyed()) {
                return;
            }

            if (dialog == null) {
                dialog = new WaitDialog.Builder(this)
                        .setCancelable(false);
            }
            dialog.setMessage(message);
            if (!dialog.isShowing()) {
                dialog.show();
            }
        }, 300);
    }

    /**
     * 隐藏加载对话框
     */
    public void hideLoadingDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (dialogCount > 0) {
            dialogCount--;
        }

        if (dialogCount != 0 || dialog == null || !dialog.isShowing()) {
            return;
        }

        dialog.dismiss();
    }

    @Override
    protected void initLayout() {
        super.initLayout();

        TitleBar titleBar = acquireTitleBar();
        if (titleBar != null) {
            titleBar.setOnTitleBarListener(this);
        }

        // 初始化沉浸式状态栏
        if (isStatusBarEnabled()) {
            getStatusBarConfig().init();
        }

        applyEdgeToEdgeInsets();
    }

    /**
     * 统一处理状态栏、刘海区域与底部导航栏安全区。
     * 系统虚拟按键显示、隐藏或切换手势导航时，Insets 会自动重新下发。
     */
    private void applyEdgeToEdgeInsets() {
        View topView = getImmersionTopView();
        View bottomView = getImmersionBottomView();
        if (topView == null && bottomView == null) {
            return;
        }

        View contentView = findViewById(android.R.id.content);
        if (contentView == null) {
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(isStatusBarDarkFont());
        controller.setAppearanceLightNavigationBars(isNavigationBarDarkFont());

        View bottomInsetView = bottomView != null ? bottomView : contentView;
        int topPaddingLeft = topView == null ? 0 : topView.getPaddingLeft();
        int topPaddingTop = topView == null ? 0 : topView.getPaddingTop();
        int topPaddingRight = topView == null ? 0 : topView.getPaddingRight();
        int topPaddingBottom = topView == null ? 0 : topView.getPaddingBottom();
        int topBaseHeight = topView == null || topView.getLayoutParams() == null
                ? ViewGroup.LayoutParams.WRAP_CONTENT : topView.getLayoutParams().height;

        int bottomPaddingLeft = bottomInsetView.getPaddingLeft();
        int bottomPaddingTop = bottomInsetView.getPaddingTop();
        int bottomPaddingRight = bottomInsetView.getPaddingRight();
        int bottomPaddingBottom = bottomInsetView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navigationBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());

            if (topView != null) {
                topView.setPadding(topPaddingLeft, topPaddingTop + statusBars.top,
                        topPaddingRight, topPaddingBottom);
                if (topBaseHeight > 0) {
                    ViewGroup.LayoutParams params = topView.getLayoutParams();
                    params.height = topBaseHeight + statusBars.top;
                    topView.setLayoutParams(params);
                }
            }

            if (bottomInsetView == topView) {
                bottomInsetView.setPadding(topPaddingLeft, topPaddingTop + statusBars.top,
                        topPaddingRight, topPaddingBottom + navigationBars.bottom);
            } else {
                bottomInsetView.setPadding(bottomPaddingLeft, bottomPaddingTop,
                        bottomPaddingRight, bottomPaddingBottom + navigationBars.bottom);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(contentView);
    }

    /**
     * 是否使用沉浸式状态栏
     */
    protected boolean isStatusBarEnabled() {
        return true;
    }

    /**
     * 状态栏字体深色模式
     */
    protected boolean isStatusBarDarkFont() {
        return true;
    }

    protected boolean isNavigationBarDarkFont() {
        return isStatusBarDarkFont();
    }

    /**
     * 获取状态栏沉浸的配置对象
     */
    @NonNull
    public ImmersionBar getStatusBarConfig() {
        if (immersionBar == null) {
            immersionBar = createStatusBarConfig();
        }
        return immersionBar;
    }

    /**
     * 初始化沉浸式状态栏
     */
    @NonNull
    protected ImmersionBar createStatusBarConfig() {
        ImmersionBar immersionBar = ImmersionBar.with(this)
            // 默认状态栏字体颜色为黑色
            .statusBarDarkFont(isStatusBarDarkFont())
            // 状态栏字体和导航栏内容自动变色，必须指定状态栏颜色和导航栏颜色才可以自动变色
            .autoDarkModeEnable(true, 0.2f);
        immersionBar.navigationBarColor(R.color.rfid_nav_bg);
        return immersionBar;
    }

    protected boolean isRfidLightTheme() {
        return ThemeModeManager.isLightTheme(this);
    }

    /**
     * 设置标题栏的标题
     */
    @Override
    public void setTitle(@StringRes int id) {
        setTitle(getString(id));
    }

    /**
     * 设置标题栏的标题
     */
    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        TitleBar titleBar = acquireTitleBar();
        if (titleBar != null) {
            titleBar.setTitle(title);
        }
    }

    @Nullable
    @Override
    public TitleBar acquireTitleBar() {
        if (titleBar == null) {
            titleBar = findTitleBar(getContentView());
        }
        return titleBar;
    }

    /**
     * 获取需要沉浸的顶部 View 对象
     */
    @Nullable
    @Override
    public View getImmersionTopView() {
        return acquireTitleBar();
    }

    @Override
    public void onLeftClick(TitleBar titleBar) {
        getOnBackPressedDispatcher().onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isShowDialog()) {
            hideLoadingDialog();
        }
        dialog = null;
    }

}
