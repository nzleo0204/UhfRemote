package com.leo.uhf.core.ui.base;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gyf.immersionbar.ImmersionBar;
import com.leo.uhf.R;

/** Shared page chrome for feature activities with an optional title-bar back action. */
public abstract class PageActivity extends BaseActivity {
    @Override
    protected void initView() {
        View backView = findViewById(R.id.iv_title_back);
        if (backView != null) {
            backView.setOnClickListener(v -> finish());
        }
        initPageView();
    }

    protected void initPageView() {
        // 子类按需重写。
    }

    @Nullable
    @Override
    public View getImmersionTopView() {
        return findViewById(R.id.ll_title_bar);
    }

    @Override
    protected boolean isStatusBarDarkFont() {
        return isRfidLightTheme();
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                .statusBarDarkFont(isRfidLightTheme())
                .statusBarColor(R.color.rfid_nav_bg)
                .navigationBarColor(R.color.rfid_nav_bg);
    }
}
