package com.leo.remote.ui.activity;

import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.Shipment;
import com.leo.remote.data.repository.RepositoryProvider;
import com.leo.remote.ui.adapter.ShipmentAdapter;
import java.util.List;

public final class ShipmentQueryActivity extends RfidPageActivity {
    private RecyclerView recyclerView;
    private TextView stateView;
    private ShipmentAdapter adapter;

    @Override
    protected int getLayoutId() {
        return R.layout.shipment_query_activity;
    }

    @Override
    protected void initPageView() {
        recyclerView = findViewById(R.id.rv_shipment);
        stateView = findViewById(R.id.tv_shipment_state);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        adapter = new ShipmentAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void initData() {
        loadShipments();
    }

    private void loadShipments() {
        showState("加载中...");
        RepositoryProvider.shipment().queryShipments("", null, new DataCallback<>() {
            @Override
            public void onSuccess(List<Shipment> data) {
                adapter.submit(data);
                recyclerView.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
                stateView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                stateView.setText(data.isEmpty() ? "暂无发货数据" : "");
            }

            @Override
            public void onFail(Exception e) {
                showState("加载失败，请稍后重试");
            }
        });
    }

    private void showState(String text) {
        recyclerView.setVisibility(View.GONE);
        stateView.setVisibility(View.VISIBLE);
        stateView.setText(text);
    }
}
