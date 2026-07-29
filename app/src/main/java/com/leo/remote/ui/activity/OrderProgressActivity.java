package com.leo.remote.ui.activity;

import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.Order;
import com.leo.remote.data.repository.RepositoryProvider;
import com.leo.remote.ui.adapter.OrderAdapter;
import java.util.List;

public final class OrderProgressActivity extends RfidPageActivity {
    private RecyclerView recyclerView;
    private TextView stateView;
    private OrderAdapter adapter;

    @Override
    protected int getLayoutId() {
        return R.layout.order_progress_activity;
    }

    @Override
    protected void initPageView() {
        recyclerView = findViewById(R.id.rv_order);
        stateView = findViewById(R.id.tv_order_state);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrderAdapter();
        recyclerView.setAdapter(adapter);
        findViewById(R.id.tv_order_filter).setOnClickListener(v -> loadOrders());
    }

    @Override
    protected void initData() {
        loadOrders();
    }

    private void loadOrders() {
        showState("加载中...");
        RepositoryProvider.order().queryOrders("", null, new DataCallback<>() {
            @Override
            public void onSuccess(List<Order> data) {
                adapter.submit(data);
                recyclerView.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
                stateView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                stateView.setText(data.isEmpty() ? "暂无订单数据" : "");
            }

            @Override
            public void onFail(Exception e) {
                showState("加载失败，点击筛选重试");
            }
        });
    }

    private void showState(String text) {
        recyclerView.setVisibility(View.GONE);
        stateView.setVisibility(View.VISIBLE);
        stateView.setText(text);
    }
}
