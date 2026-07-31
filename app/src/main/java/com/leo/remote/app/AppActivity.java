package com.leo.remote.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.bar.TitleBar;
import com.hjq.base.BaseActivity;
import com.hjq.core.manager.ActivityManager;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.remote.action.ImmersionAction;
import com.leo.remote.action.TitleBarAction;
import com.leo.remote.action.ToastAction;
import com.leo.remote.ui.dialog.common.WaitDialog;
import com.leo.remote.ui.dialog.common.MessageDialog;
import com.leo.remote.reader.DisconnectReason;
import com.leo.remote.reader.ReaderConnectionStatus;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.fragment.home.ReaderConfigFragment;
import com.leo.remote.util.ThemeModeManager;
import java.lang.ref.WeakReference;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2018/10/18
 *    desc   : Activity 业务基类
 */
public abstract class AppActivity extends BaseActivity
    implements ToastAction, TitleBarAction, ImmersionAction, ReaderObserver {

    private static WeakReference<AppActivity> sResumedActivity = new WeakReference<>(null);

    /** 标题栏对象 */
    private TitleBar mTitleBar;
    /** 状态栏沉浸 */
    private ImmersionBar mImmersionBar;

    /** 加载对话框 */
    private WaitDialog.Builder mDialog;
    /** 对话框数量 */
    private int mDialogCount;
    private ReaderSessionManager mReaderSession;
    private TextView mReaderStatusView;
    private MessageDialog.Builder mDisconnectDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ActivityManager.isMainProcess(this)) { return; }
        mReaderSession = ReaderSessionManager.getInstance(getApplication());
        setupReaderStatusView();
        mReaderSession.addObserver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sResumedActivity = new WeakReference<>(this);
        if (mReaderSession != null && mReaderSession.isPendingDisconnectAlert()) {
            showDisconnectDialog(mReaderSession.getLastUnexpectedReason());
        }
    }

    @Override
    protected void onPause() {
        if (sResumedActivity.get() == this) { sResumedActivity.clear(); }
        super.onPause();
    }

    /**
     * 当前加载对话框是否在显示中
     */
    public boolean isShowDialog() {
        return mDialog != null && mDialog.isShowing();
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

        mDialogCount++;
        postDelayed(() -> {
            if (mDialogCount <= 0 || isFinishing() || isDestroyed()) {
                return;
            }

            if (mDialog == null) {
                mDialog = new WaitDialog.Builder(this)
                        .setCancelable(false);
            }
            mDialog.setMessage(message);
            if (!mDialog.isShowing()) {
                mDialog.show();
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

        if (mDialogCount > 0) {
            mDialogCount--;
        }

        if (mDialogCount != 0 || mDialog == null || !mDialog.isShowing()) {
            return;
        }

        mDialog.dismiss();
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
        if (mImmersionBar == null) {
            mImmersionBar = createStatusBarConfig();
        }
        return mImmersionBar;
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
        if (mTitleBar == null) {
            mTitleBar = findTitleBar(getContentView());
        }
        return mTitleBar;
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

    // ========== Reader state ==========

    private void setupReaderStatusView() {
        mReaderStatusView = findViewById(R.id.tv_home_reader_status);
        if (mReaderStatusView != null) { return; }
        View title = findViewById(R.id.ll_title_bar);
        if (!(title instanceof LinearLayout titleLayout)) { return; }
        TextView chip = new TextView(this);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(1);
        chip.setTextSize(12);
        chip.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        params.setMarginStart(dp(8));
        titleLayout.addView(chip, params);
        mReaderStatusView = chip;
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        if (mReaderStatusView == null) { return; }
        ReaderConnectionStatus status = state.getConnectionStatus();
        mReaderStatusView.setText(readerStatusText(status));
        mReaderStatusView.setBackgroundResource(readerStatusBackground(status));
        mReaderStatusView.setTextColor(ContextCompat.getColor(this,
                status == ReaderConnectionStatus.NOT_CONNECTED ? R.color.rfid_text : R.color.white));
    }

    @Override
    public void onReaderUnexpectedDisconnect(DisconnectReason reason) {
        if (sResumedActivity.get() == this) { showDisconnectDialog(reason); }
    }

    /** Blocks reader-dependent actions and repeats the strong disconnect prompt when offline. */
    protected boolean requireReaderOnline() {
        if (mReaderSession != null && mReaderSession.getState().isConnected()) { return true; }
        showDisconnectDialog(mReaderSession == null
                ? DisconnectReason.NONE : mReaderSession.getLastUnexpectedReason());
        return false;
    }

    private void showDisconnectDialog(DisconnectReason reason) {
        if (isFinishing() || isDestroyed()
                || (mDisconnectDialog != null && mDisconnectDialog.isShowing())) {
            return;
        }
        @StringRes int message = disconnectReasonText(reason);
        MessageDialog.Builder builder = new MessageDialog.Builder(this)
                .setTitle(R.string.reader_disconnected_title)
                .setMessage(message)
                .setCancelable(false)
                .setCanceledOnTouchOutside(false)
                .setCancel(R.string.reader_goto_connect)
                .setConfirm(R.string.common_confirm)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(@NonNull BaseDialog dialog) {
                        acknowledgeDisconnectDialog();
                    }

                    @Override
                    public void onCancel(@NonNull BaseDialog dialog) {
                        acknowledgeDisconnectDialog();
                        openReaderConfig();
                    }
                });
        mDisconnectDialog = builder;
        builder.show();
    }

    private void acknowledgeDisconnectDialog() {
        if (mReaderSession != null) { mReaderSession.acknowledgeDisconnect(); }
        mDisconnectDialog = null;
    }

    private void openReaderConfig() {
        if (this instanceof HomeActivity homeActivity) {
            homeActivity.showReaderConfig();
        } else {
            HomeActivity.start(this, ReaderConfigFragment.class);
        }
    }

    @StringRes
    public static int readerStatusText(ReaderConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> R.string.config_status_connected;
            case DISCONNECTED -> R.string.config_status_disconnected;
            case FAILED -> R.string.config_status_failed;
            case NOT_CONNECTED -> R.string.config_status_not_connected;
        };
    }

    public static int readerStatusBackground(ReaderConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> R.drawable.rfid_chip_green_bg;
            case DISCONNECTED, FAILED -> R.drawable.rfid_chip_red_bg;
            case NOT_CONNECTED -> R.drawable.rfid_chip_gray_bg;
        };
    }

    @StringRes
    public static int disconnectReasonText(DisconnectReason reason) {
        return switch (reason) {
            case LINK_LOST -> R.string.reader_disconnected_link_lost;
            case BLUETOOTH_OFF -> R.string.reader_disconnected_bluetooth_off;
            case WIFI_LOST -> R.string.reader_disconnected_wifi_lost;
            case SDK_ERROR -> R.string.reader_disconnected_sdk_error;
            default -> R.string.reader_offline_action_blocked;
        };
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (mReaderSession != null) { mReaderSession.removeObserver(this); }
        if (sResumedActivity.get() == this) { sResumedActivity.clear(); }
        if (mDisconnectDialog != null) { mDisconnectDialog.dismiss(); }
        mDisconnectDialog = null;
        super.onDestroy();
        if (isShowDialog()) {
            hideLoadingDialog();
        }
        mDialog = null;
    }

}
