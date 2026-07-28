package com.leo.remote.ui.activity;

import com.leo.remote.R;

public final class OrderProgressActivity extends RfidPageActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.order_progress_activity;
    }

    @Override
    protected void initPageView() {
        // No order filters are available until the backend is integrated.
    }

    @Override
    protected void initData() {
        // Backend integration is intentionally out of scope.
    }
}
