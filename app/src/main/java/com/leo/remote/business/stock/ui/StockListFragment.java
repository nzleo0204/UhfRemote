package com.leo.remote.business.stock.ui;

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.core.ui.base.BaseFragment;
import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.stock.data.model.StockItem;
import com.leo.remote.core.data.RepositoryProvider;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.business.stock.ui.StockAdapter;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import java.lang.ref.WeakReference;
import java.util.List;

/** Real-time stock query shown directly in the bottom navigation. */
public final class StockListFragment extends BaseFragment<HomeActivity>
        implements OnRefreshLoadMoreListener {
    private static final int PAGE_SIZE = 4;

    private RecyclerView recyclerView;
    private TextView stateView;
    private TextView countView;
    private EditText keywordView;
    private SmartRefreshLayout refreshLayout;
    private StockAdapter adapter;
    private List<StockItem> allItems = List.of();
    private int visibleItemCount;
    private int requestGeneration;
    private boolean viewDestroyed;

    public static StockListFragment newInstance() {
        return new StockListFragment();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.stock_fragment;
    }

    @Override
    protected void initView() {
        viewDestroyed = false;
        recyclerView = findViewById(R.id.rv_stock);
        stateView = findViewById(R.id.tv_stock_state);
        countView = findViewById(R.id.tv_stock_count);
        keywordView = findViewById(R.id.et_stock_keyword);
        refreshLayout = findViewById(R.id.srl_stock);
        adapter = new StockAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter);
        refreshLayout.setEnableLoadMoreWhenContentNotFull(true);
        refreshLayout.setOnRefreshLoadMoreListener(this);
        findViewById(R.id.tv_stock_search).setOnClickListener(view -> submitSearch());
        keywordView.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) { return false; }
            submitSearch();
            return true;
        });
        findViewById(R.id.stock_fragment_root).setOnClickListener(view -> dismissKeyboard());
    }

    @Override
    protected void initData() {
        reload(false);
    }

    @Override
    public void onRefresh(@NonNull RefreshLayout layout) {
        reload(true);
    }

    @Override
    public void onLoadMore(@NonNull RefreshLayout layout) {
        if (visibleItemCount < allItems.size()) {
            visibleItemCount = Math.min(visibleItemCount + PAGE_SIZE, allItems.size());
            renderPage();
        }
        refreshLayout.finishLoadMore();
        refreshLayout.setNoMoreData(visibleItemCount >= allItems.size());
    }

    private void submitSearch() {
        dismissKeyboard();
        reload(false);
    }

    private void reload(boolean fromRefresh) {
        int generation = ++requestGeneration;
        refreshLayout.setNoMoreData(false);
        if (visibleItemCount == 0) {
            showState(getString(R.string.common_loading));
        }
        RepositoryProvider.stock().queryStock(keywordView.getText().toString(),
                new QueryCallback(this, generation, fromRefresh));
    }

    private void onQuerySuccess(int generation, boolean fromRefresh, List<StockItem> data) {
        if (!isRequestActive(generation)) { return; }
        allItems = List.copyOf(data);
        visibleItemCount = Math.min(PAGE_SIZE, allItems.size());
        renderPage();
        if (fromRefresh) { refreshLayout.finishRefresh(true); }
    }

    private void onQueryFailure(int generation, boolean fromRefresh) {
        if (!isRequestActive(generation)) { return; }
        if (visibleItemCount == 0) {
            showState(getString(R.string.stock_load_failed));
        }
        refreshLayout.setNoMoreData(visibleItemCount >= allItems.size());
        if (fromRefresh) { refreshLayout.finishRefresh(false); }
    }

    private void renderPage() {
        adapter.submit(allItems.subList(0, visibleItemCount));
        countView.setText(getString(R.string.stock_count, allItems.size()));
        recyclerView.setVisibility(visibleItemCount == 0 ? View.GONE : View.VISIBLE);
        stateView.setVisibility(visibleItemCount == 0 ? View.VISIBLE : View.GONE);
        stateView.setText(visibleItemCount == 0 ? getString(R.string.stock_empty) : "");
        refreshLayout.setNoMoreData(visibleItemCount >= allItems.size());
    }

    private boolean isRequestActive(int generation) {
        return !viewDestroyed && generation == requestGeneration;
    }

    private void showState(String text) {
        recyclerView.setVisibility(View.GONE);
        stateView.setVisibility(View.VISIBLE);
        stateView.setText(text);
    }

    private void dismissKeyboard() {
        hideKeyboard(keywordView);
        keywordView.clearFocus();
    }

    @Override
    public void onDestroyView() {
        viewDestroyed = true;
        requestGeneration++;
        if (refreshLayout != null) { refreshLayout.setOnRefreshLoadMoreListener(null); }
        if (recyclerView != null) { recyclerView.setAdapter(null); }
        adapter = null;
        recyclerView = null;
        stateView = null;
        countView = null;
        keywordView = null;
        refreshLayout = null;
        super.onDestroyView();
    }

    private static final class QueryCallback implements DataCallback<List<StockItem>> {
        private final WeakReference<StockListFragment> owner;
        private final int generation;
        private final boolean fromRefresh;

        QueryCallback(StockListFragment owner, int generation, boolean fromRefresh) {
            this.owner = new WeakReference<>(owner);
            this.generation = generation;
            this.fromRefresh = fromRefresh;
        }

        @Override
        public void onSuccess(List<StockItem> data) {
            StockListFragment fragment = owner.get();
            if (fragment != null) { fragment.onQuerySuccess(generation, fromRefresh, data); }
        }

        @Override
        public void onFail(Exception error) {
            StockListFragment fragment = owner.get();
            if (fragment != null) { fragment.onQueryFailure(generation, fromRefresh); }
        }
    }
}
