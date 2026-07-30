package com.leo.remote.ui.fragment.home;

import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.leo.remote.R;
import com.leo.remote.aop.SingleClick;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.InventoryItem;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.adapter.InventoryAdapter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Live RFID inventory page. */
public final class InventoryFragment extends AppFragment<HomeActivity> implements ReaderObserver {
    private ReaderSessionManager session;
    private InventoryAdapter adapter;
    private MaterialButton startButton;
    private TextView totalView;
    private TextView emptyView;
    private List<InventoryItem> exportItems = List.of();

    private final ActivityResultLauncher<String> createCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), this::writeCsv);

    public static InventoryFragment newInstance() { return new InventoryFragment(); }

    @Override
    protected int getLayoutId() { return R.layout.inventory_fragment; }

    @Override
    protected void initView() {
        startButton = findViewById(R.id.btn_inventory_start);
        totalView = findViewById(R.id.tv_inventory_total);
        emptyView = findViewById(R.id.tv_inventory_empty);
        RecyclerView recyclerView = findViewById(R.id.rv_inventory_items);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        adapter = new InventoryAdapter();
        recyclerView.setAdapter(adapter);

        startButton.setOnClickListener(view -> toggleInventory());
        findViewById(R.id.btn_inventory_clear).setOnClickListener(view -> session.clearInventory());
        findViewById(R.id.btn_inventory_export).setOnClickListener(view -> {
            exportItems = session.getInventorySnapshot();
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            createCsv.launch("uhf-inventory-" + date + ".csv");
        });
    }

    @Override
    protected void initData() {
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        session.addObserver(this);
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            if (session.getState().isInventoryRunning()) { session.stopInventory(); }
            session.removeObserver(this);
        }
        super.onDestroy();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        startButton.setEnabled(true);
        startButton.setText(state.isInventoryRunning()
                ? R.string.inventory_stop : R.string.inventory_start);
        startButton.setIconResource(state.isInventoryRunning()
                ? R.drawable.rfid_inventory_stop_ic : R.drawable.rfid_inventory_play_ic);
    }

    @Override
    public void onInventoryChanged(List<InventoryItem> items, long totalReads) {
        adapter.submitList(items);
        totalView.setText(getString(R.string.inventory_total, items.size()));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @SingleClick
    private void toggleInventory() {
        ReaderState state = session.getState();
        if (!state.isConnected()) {
            toast(R.string.inventory_connect_first);
            return;
        }
        if (state.isInventoryRunning()) {
            session.stopInventory().whenComplete((status, error) ->
                    showResult(status, error, R.string.inventory_stop_failed));
        } else {
            session.startInventory().whenComplete((status, error) ->
                    showResult(status, error, R.string.inventory_start_failed));
        }
    }

    private void showResult(Integer status, Throwable error, @StringRes int message) {
        requireActivity().runOnUiThread(() -> {
            if (error != null) { toast(rootMessage(error)); }
            else if (status != null && status != 0) {
                toast(getString(R.string.config_error_code, getString(message), status));
            }
        });
    }

    private void writeCsv(Uri uri) {
        if (uri == null) { return; }
        StringBuilder csv = new StringBuilder("index,id,additional_data,count,rssi,chip_model\r\n");
        for (int i = 0; i < exportItems.size(); i++) {
            InventoryItem item = exportItems.get(i);
            csv.append(i + 1).append(',').append(escape(item.getId())).append(',')
                    .append(escape(item.getData())).append(',').append(item.getCount()).append(',')
                    .append(item.getRssi()).append(',').append(escape(item.getChipModel())).append("\r\n");
        }
        try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
            if (output == null) { throw new IOException("Unable to open document"); }
            output.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            toast(R.string.inventory_exported);
        } catch (IOException error) {
            toast(getString(R.string.inventory_export_failed, error.getMessage()));
        }
    }

    private static String escape(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error.getCause();
        return cause == null ? error.getMessage() : cause.getMessage();
    }
}
