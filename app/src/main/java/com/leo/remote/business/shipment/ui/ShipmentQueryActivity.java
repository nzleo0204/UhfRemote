package com.leo.remote.business.shipment.ui;

import android.content.Context;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.shipment.data.model.Shipment;
import com.leo.remote.core.data.RepositoryProvider;
import com.leo.remote.core.ui.base.PagedQueryActivity;
import java.util.List;

public final class ShipmentQueryActivity extends PagedQueryActivity<Shipment> {
    private ShipmentAdapter adapter;

    public static void start(Context context) {
        context.startActivity(new Intent(context, ShipmentQueryActivity.class));
    }

    @Override
    protected int getLayoutId() {
        return R.layout.shipment_query_activity;
    }

    @Override
    protected int getRecyclerViewId() {
        return R.id.rv_shipment;
    }

    @Override
    protected int getStateViewId() {
        return R.id.tv_shipment_state;
    }

    @Override
    protected int getRefreshLayoutId() {
        return R.id.srl_shipment;
    }

    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        adapter = new ShipmentAdapter();
        return adapter;
    }

    @Override
    protected void queryData(DataCallback<List<Shipment>> callback) {
        RepositoryProvider.shipment().queryShipments("", null, callback);
    }

    @Override
    protected void submitPage(List<Shipment> page) {
        adapter.submit(page);
    }

    @Override
    protected String emptyMessage() {
        return getString(R.string.shipment_empty);
    }

    @Override
    protected String errorMessage() {
        return getString(R.string.shipment_load_failed);
    }
}
