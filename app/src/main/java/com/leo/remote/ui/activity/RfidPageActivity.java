package com.leo.remote.ui.activity;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gyf.immersionbar.ImmersionBar;
import com.leo.remote.R;
import com.leo.remote.app.AppActivity;

/**
 * RFID 业务页基类，统一深色沉浸式状态栏和返回按钮。
 */
public abstract class RfidPageActivity extends AppActivity {

    @Override
    protected void initView() {
        View backView = findViewById(R.id.iv_title_back);
        if (backView != null) {
            backView.setOnClickListener(v -> finish());
        }
        initPageView();
    }

    protected void initPageView() {
        // Optional for subclasses.
    }

    @Nullable
    @Override
    public View getImmersionTopView() {
        return findViewById(R.id.ll_title_bar);
    }

    @Override
    protected boolean isStatusBarDarkFont() {
        return false;
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                .statusBarDarkFont(false)
                .statusBarColor(R.color.rfid_nav_bg)
                .navigationBarColor(R.color.rfid_nav_bg);
    }
}
