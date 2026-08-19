package com.leo.uhf.business.common.ui;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.uhf.R;
import com.leo.uhf.core.data.DataCallback;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import java.lang.ref.WeakReference;
import java.util.List;

/** Shared pull-to-refresh and paged loading behavior for query result pages. */
public abstract class PagedQueryActivity<T> extends BusinessPageActivity
        implements OnRefreshLoadMoreListener {
    private static final int PAGE_SIZE = 4;

    private RecyclerView recyclerView;
    private TextView stateView;
    private SmartRefreshLayout refreshLayout;
    private List<T> allItems = List.of();
    private int visibleItemCount;
    private int requestGeneration;
    private boolean destroyed;

    @Override
    protected final void initPageView() {
        recyclerView = findViewById(getRecyclerViewId());
        stateView = findViewById(getStateViewId());
        refreshLayout = findViewById(getRefreshLayoutId());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(createAdapter());
        refreshLayout.setEnableLoadMoreWhenContentNotFull(true);
        refreshLayout.setOnRefreshLoadMoreListener(this);
        initQueryControls();
    }

    @Override
    protected final void initData() {
        reload(false);
    }

    @Override
    public final void onRefresh(@NonNull RefreshLayout layout) {
        reload(true);
    }

    @Override
    public final void onLoadMore(@NonNull RefreshLayout layout) {
        if (visibleItemCount < allItems.size()) {
            visibleItemCount = Math.min(visibleItemCount + PAGE_SIZE, allItems.size());
            renderPage();
        }
        refreshLayout.finishLoadMore();
        refreshLayout.setNoMoreData(visibleItemCount >= allItems.size());
    }

    protected final void reloadFromControl() {
        reload(false);
    }

    protected void initQueryControls() {
    }

    protected void onResultCountChanged(int totalCount) {
    }

    @IdRes
    protected abstract int getRecyclerViewId();

    @IdRes
    protected abstract int getStateViewId();

    @IdRes
    protected abstract int getRefreshLayoutId();

    protected abstract RecyclerView.Adapter<?> createAdapter();

    protected abstract void submitPage(List<T> page);

    protected abstract void queryData(DataCallback<List<T>> callback);

    protected abstract String emptyMessage();

    protected abstract String errorMessage();

    private void reload(boolean fromRefresh) {
        int generation = ++requestGeneration;
        refreshLayout.setNoMoreData(false);
        if (visibleItemCount == 0) {
            showState(getString(R.string.common_loading));
        }
        queryData(new QueryCallback<>(this, generation, fromRefresh));
    }

    private void onQuerySuccess(int generation, boolean fromRefresh, List<T> data) {
        if (!isRequestActive(generation)) {
            return;
        }
        allItems = List.copyOf(data);
        visibleItemCount = Math.min(PAGE_SIZE, allItems.size());
        renderPage();
        if (fromRefresh) {
            refreshLayout.finishRefresh(true);
        }
    }

    private void onQueryFailure(int generation, boolean fromRefresh) {
        if (!isRequestActive(generation)) {
            return;
        }
        if (visibleItemCount == 0) {
            showState(errorMessage());
        }
        refreshLayout.setNoMoreData(visibleItemCount >= allItems.size());
        if (fromRefresh) {
            refreshLayout.finishRefresh(false);
        }
    }

    private void renderPage() {
        submitPage(allItems.subList(0, visibleItemCount));
        onResultCountChanged(allItems.size());
        recyclerView.setVisibility(visibleItemCount == 0 ? View.GONE : View.VISIBLE);
        stateView.setVisibility(visibleItemCount == 0 ? View.VISIBLE : View.GONE);
        stateView.setText(visibleItemCount == 0 ? emptyMessage() : "");
        refreshLayout.setNoMoreData(visibleItemCount >= allItems.size());
    }

    private boolean isRequestActive(int generation) {
        return !destroyed && generation == requestGeneration;
    }

    private void showState(String text) {
        recyclerView.setVisibility(View.GONE);
        stateView.setVisibility(View.VISIBLE);
        stateView.setText(text);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        requestGeneration++;
        if (refreshLayout != null) {
            refreshLayout.setOnRefreshLoadMoreListener(null);
        }
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        super.onDestroy();
    }

    private static final class QueryCallback<T> implements DataCallback<List<T>> {
        private final WeakReference<PagedQueryActivity<T>> owner;
        private final int generation;
        private final boolean fromRefresh;

        QueryCallback(PagedQueryActivity<T> owner, int generation, boolean fromRefresh) {
            this.owner = new WeakReference<>(owner);
            this.generation = generation;
            this.fromRefresh = fromRefresh;
        }

        @Override
        public void onSuccess(List<T> data) {
            PagedQueryActivity<T> activity = owner.get();
            if (activity != null) {
                activity.onQuerySuccess(generation, fromRefresh, data);
            }
        }

        @Override
        public void onFail(Exception error) {
            PagedQueryActivity<T> activity = owner.get();
            if (activity != null) {
                activity.onQueryFailure(generation, fromRefresh);
            }
        }
    }
}
