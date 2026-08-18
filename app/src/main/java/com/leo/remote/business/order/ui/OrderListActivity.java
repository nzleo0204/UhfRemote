package com.leo.remote.business.order.ui;

import android.content.Context;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.order.data.model.Order;
import com.leo.remote.core.data.RepositoryProvider;
import com.leo.remote.core.ui.base.PagedQueryActivity;
import java.util.List;

public final class OrderListActivity extends PagedQueryActivity<Order> {
    private OrderAdapter adapter;

    public static void start(Context context) {
        context.startActivity(new Intent(context, OrderListActivity.class));
    }

    @Override
    protected int getLayoutId() {
        return R.layout.order_progress_activity;
    }

    @Override
    protected int getRecyclerViewId() {
        return R.id.rv_order;
    }

    @Override
    protected int getStateViewId() {
        return R.id.tv_order_state;
    }

    @Override
    protected int getRefreshLayoutId() {
        return R.id.srl_order;
    }

    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        adapter = new OrderAdapter();
        return adapter;
    }

    @Override
    protected void initQueryControls() {
        findViewById(R.id.tv_order_filter).setOnClickListener(v -> reloadFromControl());
    }

    @Override
    protected void queryData(DataCallback<List<Order>> callback) {
        RepositoryProvider.order().queryOrders("", null, callback);
    }

    @Override
    protected void submitPage(List<Order> page) {
        adapter.submit(page);
    }

    @Override
    protected String emptyMessage() {
        return getString(R.string.order_empty);
    }

    @Override
    protected String errorMessage() {
        return getString(R.string.order_load_failed);
    }
}
