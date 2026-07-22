package com.leo.remote.ui.activity;

import com.leo.remote.R;

public final class OrderProgressActivity extends RfidPageActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.order_progress_activity;
    }

    @Override
    protected void initPageView() {
        findViewById(R.id.tv_order_filter).setOnClickListener(v -> toast("筛选条件已打开"));
    }

    @Override
    protected void initData() {
        // Static preview data is rendered in XML.
    }
}
