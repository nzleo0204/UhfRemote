package com.leo.remote.ui.activity;

import com.leo.remote.R;

public final class StockQueryActivity extends RfidPageActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.stock_query_activity;
    }

    @Override
    protected void initPageView() {
        findViewById(R.id.tv_stock_search).setOnClickListener(v -> toast("正在搜索标签库存"));
    }

    @Override
    protected void initData() {
        // Static preview data is rendered in XML.
    }
}
