package com.leo.remote.ui.activity;

import com.leo.remote.R;

public final class ShipmentQueryActivity extends RfidPageActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.shipment_query_activity;
    }

    @Override
    protected void initPageView() {
        findViewById(R.id.tv_shipment_copy).setOnClickListener(v -> toast("运单号已复制"));
    }

    @Override
    protected void initData() {
        // Static preview data is rendered in XML.
    }
}
