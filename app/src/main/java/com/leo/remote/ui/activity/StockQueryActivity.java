package com.leo.remote.ui.activity;

import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.StockItem;
import com.leo.remote.data.repository.RepositoryProvider;
import com.leo.remote.ui.adapter.StockAdapter;
import java.util.List;

public final class StockQueryActivity extends PagedQueryActivity<StockItem> {
    private EditText keywordView;
    private TextView countView;
    private StockAdapter adapter;

    @Override
    protected int getLayoutId() {
        return R.layout.stock_query_activity;
    }

    @Override
    protected int getRecyclerViewId() {
        return R.id.rv_stock;
    }

    @Override
    protected int getStateViewId() {
        return R.id.tv_stock_state;
    }

    @Override
    protected int getRefreshLayoutId() {
        return R.id.srl_stock;
    }

    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        adapter = new StockAdapter();
        return adapter;
    }

    @Override
    protected void initQueryControls() {
        keywordView = findViewById(R.id.et_stock_keyword);
        countView = findViewById(R.id.tv_stock_count);
        findViewById(R.id.tv_stock_search).setOnClickListener(v -> reloadFromControl());
    }

    @Override
    protected void queryData(DataCallback<List<StockItem>> callback) {
        RepositoryProvider.stock().queryStock(keywordView.getText().toString(), callback);
    }

    @Override
    protected void submitPage(List<StockItem> page) {
        adapter.submit(page);
    }

    @Override
    protected void onResultCountChanged(int totalCount) {
        countView.setText(getString(R.string.stock_count, totalCount));
    }

    @Override
    protected String emptyMessage() {
        return "暂无库存数据";
    }

    @Override
    protected String errorMessage() {
        return "加载失败，点击搜索重试";
    }
}
