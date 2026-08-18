package com.leo.remote.app.splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import com.airbnb.lottie.LottieAnimationView;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.base.BaseDialog;
import com.hjq.core.manager.ActivityManager;
import com.hjq.custom.widget.view.SlantedTextView;
import com.leo.remote.R;
import com.leo.remote.core.ui.base.BaseActivity;
import com.leo.remote.app.MainActivity;
import com.leo.remote.app.bootstrap.AppInitializer;
import com.leo.remote.core.util.AppConfig;
import com.leo.remote.core.ui.dialog.PrivacyAgreementDialog;
import com.leo.remote.core.ui.dialog.MessageDialog;
import java.util.Locale;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2018/10/18
 *    desc   : 闪屏界面
 */
@SuppressLint("CustomSplashScreen")
public final class SplashActivity extends BaseActivity {

    private LottieAnimationView lottieView;
    private SlantedTextView buildTypeView;

    @Override
    protected int getLayoutId() {
        return R.layout.splash_activity;
    }

    @Override
    protected void initView() {
        lottieView = findViewById(R.id.lav_splash_lottie);
        buildTypeView = findViewById(R.id.iv_splash_build_type);

        // 禁用返回键
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {

            @Override
            public void handleOnBackPressed() {
                // ignored
            }
        });

        // 设置动画监听
        lottieView.addAnimatorListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                lottieView.removeAnimatorListener(this);

                if (AppInitializer.isAgreePrivacy(SplashActivity.this)) {
                    agreePrivacyAfter();
                    return;
                }

                // 弹窗用户协议与隐私政策对话框
                new PrivacyAgreementDialog.Builder(SplashActivity.this)
                        .setListener(new MessageDialog.OnListener() {

                            @Override
                            public void onConfirm(@NonNull BaseDialog dialog) {
                                AppInitializer.setAgreePrivacy(SplashActivity.this, true);
                                agreePrivacyAfter();
                            }

                            @Override
                            public void onCancel(@NonNull BaseDialog dialog) {
                                ActivityManager.getInstance().finishAllActivities();
                            }
                        })
                        .show();
            }
        });
    }

    @Override
    protected void initData() {
        if (AppConfig.isDebug()) {
            buildTypeView.setVisibility(View.VISIBLE);
            buildTypeView.setText(AppConfig.getBuildType().toUpperCase(Locale.ROOT));
        } else {
            buildTypeView.setVisibility(View.INVISIBLE);
        }

    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 隐藏状态栏和导航栏
                .hideBar(BarHide.FLAG_HIDE_BAR);
    }

    @Override
    protected void initActivity() {
        // 问题及方案：https://www.cnblogs.com/net168/p/5722752.html
        // 如果当前 Activity 不是任务栈中的第一个 Activity
        if (!isTaskRoot()) {
            Intent intent = getIntent();
            // 如果当前 Activity 是通过桌面图标启动进入的
            if (intent != null && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                    && Intent.ACTION_MAIN.equals(intent.getAction())) {
                // 对当前 Activity 执行销毁操作，避免重复实例化入口
                finish();
                return;
            }
        }
        super.initActivity();
    }

    @Override
    protected void onDestroy() {
        // 因为修复了一个启动页被重复启动的问题，所以有可能 Activity 还没有初始化完成就已经销毁了
        // 所以如果需要在此处释放对象资源需要先对这个对象进行判空，否则可能会导致空指针异常
        super.onDestroy();
    }

    /**
     * 同意隐私后需要做的事情
     */
    private void agreePrivacyAfter() {
        AppInitializer.initializeReader(getApplication());
        MainActivity.start(this);
        finish();
    }
}
