package com.leo.remote.ui.activity;

import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.Shipment;
import com.leo.remote.data.repository.RepositoryProvider;
import com.leo.remote.ui.adapter.ShipmentAdapter;
import java.util.List;

public final class ShipmentQueryActivity extends PagedQueryActivity<Shipment> {
    private ShipmentAdapter adapter;

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
        return "暂无发货数据";
    }

    @Override
    protected String errorMessage() {
        return "加载失败，请稍后重试";
    }
}
