package com.leo.remote.ui.activity;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.StockItem;
import com.leo.remote.data.repository.RepositoryProvider;
import com.leo.remote.ui.adapter.StockAdapter;
import java.util.List;

public final class StockQueryActivity extends RfidPageActivity {
    private EditText keywordView;
    private TextView countView;
    private TextView stateView;
    private RecyclerView recyclerView;
    private StockAdapter adapter;

    @Override
    protected int getLayoutId() {
        return R.layout.stock_query_activity;
    }

    @Override
    protected void initPageView() {
        keywordView = findViewById(R.id.et_stock_keyword);
        countView = findViewById(R.id.tv_stock_count);
        stateView = findViewById(R.id.tv_stock_state);
        recyclerView = findViewById(R.id.rv_stock);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        adapter = new StockAdapter();
        recyclerView.setAdapter(adapter);
        findViewById(R.id.tv_stock_search).setOnClickListener(v -> loadStock());
    }

    @Override
    protected void initData() {
        loadStock();
    }

    private void loadStock() {
        showState("加载中...");
        RepositoryProvider.stock().queryStock(keywordView.getText().toString(), new DataCallback<>() {
            @Override
            public void onSuccess(List<StockItem> data) {
                adapter.submit(data);
                countView.setText(getString(R.string.stock_count, data.size()));
                recyclerView.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
                stateView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                stateView.setText(data.isEmpty() ? "暂无库存数据" : "");
            }

            @Override
            public void onFail(Exception e) {
                recyclerView.setVisibility(View.GONE);
                stateView.setVisibility(View.VISIBLE);
                stateView.setText("加载失败，点击搜索重试");
            }
        });
    }

    private void showState(String text) {
        recyclerView.setVisibility(View.GONE);
        stateView.setVisibility(View.VISIBLE);
        stateView.setText(text);
    }
}
