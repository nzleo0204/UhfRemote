package com.leo.uhf.business.order.ui;

import android.content.Context;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.uhf.R;
import com.leo.uhf.core.data.DataCallback;
import com.leo.uhf.business.order.data.model.Order;
import com.leo.uhf.business.common.data.BusinessRepositories;
import com.leo.uhf.business.common.ui.PagedQueryActivity;
import java.util.List;

/**
 * 提供订单查询列表页面。
 */
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
        BusinessRepositories.order().queryOrders("", null, callback);
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
