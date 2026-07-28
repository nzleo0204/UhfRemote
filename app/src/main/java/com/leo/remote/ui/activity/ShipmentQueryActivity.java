package com.leo.remote.ui.activity;

import com.leo.remote.R;

public final class ShipmentQueryActivity extends RfidPageActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.shipment_query_activity;
    }

    @Override
    protected void initPageView() {
        // No shipment action is available until the backend is integrated.
    }

    @Override
    protected void initData() {
        // Backend integration is intentionally out of scope.
    }
}
