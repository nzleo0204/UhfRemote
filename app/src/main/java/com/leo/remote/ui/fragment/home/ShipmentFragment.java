package com.leo.remote.ui.fragment.home;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.leo.remote.R;
import com.leo.remote.app.AppFragment;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.Shipment;
import com.leo.remote.data.repository.RepositoryProvider;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.adapter.ShipmentAdapter;
import com.scwang.smart.refresh.layout.SmartRefreshLayout;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener;
import java.lang.ref.WeakReference;
import java.util.List;

/** Bottom-navigation shipment inventory page with refresh and incremental loading. */
public final class ShipmentFragment extends AppFragment<HomeActivity>
        implements OnRefreshLoadMoreListener {
    private static final int PAGE_SIZE = 4;

    private RecyclerView recyclerView;
    private TextView stateView;
    private SmartRefreshLayout refreshLayout;
    private ShipmentAdapter adapter;
    private List<Shipment> allItems = List.of();
    private int visibleItemCount;
    private int requestGeneration;
    private boolean viewDestroyed;

    public static ShipmentFragment newInstance() {
        return new ShipmentFragment();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.shipment_fragment;
    }

    @Override
    protected void initView() {
        viewDestroyed = false;
        recyclerView = findViewById(R.id.rv_shipment);
        stateView = findViewById(R.id.tv_shipment_state);
        refreshLayout = findViewById(R.id.srl_shipment);
        adapter = new ShipmentAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter);
        refreshLayout.setEnableLoadMoreWhenContentNotFull(true);
        refreshLayout.setOnRefreshLoadMoreListener(this);
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

    private void reload(boolean fromRefresh) {
        int generation = ++requestGeneration;
        refreshLayout.setNoMoreData(false);
        if (visibleItemCount == 0) {
            showState(getString(R.string.common_loading));
        }
        RepositoryProvider.shipment().queryShipments("", null,
                new QueryCallback(this, generation, fromRefresh));
    }

    private void onQuerySuccess(int generation, boolean fromRefresh, List<Shipment> data) {
        if (!isRequestActive(generation)) { return; }
        allItems = List.copyOf(data);
        visibleItemCount = Math.min(PAGE_SIZE, allItems.size());
        renderPage();
        if (fromRefresh) { refreshLayout.finishRefresh(true); }
    }

    private void onQueryFailure(int generation, boolean fromRefresh) {
        if (!isRequestActive(generation)) { return; }
        if (visibleItemCount == 0) {
            showState(getString(R.string.shipment_load_failed));
        }
        refreshLayout.setNoMoreData(visibleItemCount >= allItems.size());
        if (fromRefresh) { refreshLayout.finishRefresh(false); }
    }

    private void renderPage() {
        adapter.submit(allItems.subList(0, visibleItemCount));
        recyclerView.setVisibility(visibleItemCount == 0 ? View.GONE : View.VISIBLE);
        stateView.setVisibility(visibleItemCount == 0 ? View.VISIBLE : View.GONE);
        stateView.setText(visibleItemCount == 0 ? getString(R.string.shipment_empty) : "");
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

    @Override
    public void onDestroyView() {
        viewDestroyed = true;
        requestGeneration++;
        if (refreshLayout != null) { refreshLayout.setOnRefreshLoadMoreListener(null); }
        if (recyclerView != null) { recyclerView.setAdapter(null); }
        adapter = null;
        recyclerView = null;
        stateView = null;
        refreshLayout = null;
        super.onDestroyView();
    }

    private static final class QueryCallback implements DataCallback<List<Shipment>> {
        private final WeakReference<ShipmentFragment> owner;
        private final int generation;
        private final boolean fromRefresh;

        QueryCallback(ShipmentFragment owner, int generation, boolean fromRefresh) {
            this.owner = new WeakReference<>(owner);
            this.generation = generation;
            this.fromRefresh = fromRefresh;
        }

        @Override
        public void onSuccess(List<Shipment> data) {
            ShipmentFragment fragment = owner.get();
            if (fragment != null) { fragment.onQuerySuccess(generation, fromRefresh, data); }
        }

        @Override
        public void onFail(Exception error) {
            ShipmentFragment fragment = owner.get();
            if (fragment != null) { fragment.onQueryFailure(generation, fromRefresh); }
        }
    }
}
