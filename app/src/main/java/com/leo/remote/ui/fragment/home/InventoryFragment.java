package com.leo.remote.ui.fragment.home;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.leo.remote.R;
import com.leo.remote.aop.SingleClick;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.InventoryMaskConfig;
import com.leo.remote.reader.InventoryMaskFormParser;
import com.leo.remote.reader.InventoryItem;
import com.leo.remote.reader.InventoryArea;
import com.leo.remote.reader.ModuleSubtype;
import com.leo.remote.reader.ReaderConfiguration;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.TagProtocol;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.adapter.InventoryAdapter;
import com.leo.remote.ui.dialog.InventoryDetailSheet;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.leo.remote.ui.reader.common.InventoryMaskPanelController;
import com.leo.remote.ui.reader.inventory.InventoryCsvExporter;
import com.leo.remote.util.ThrowableUtils;

/**
 * 盘点页面 Fragment
 *
 * 实时 RFID 标签盘点功能，支持：
 * - 批量标签盘点
 * - 标签过滤（Mask 功能）
 * - 数据导出为 CSV
 * - 实时显示标签信息（EPC、TID、USER 等）
 */
@SuppressLint("LogNotTimber")
public final class InventoryFragment extends AppFragment<HomeActivity> implements ReaderObserver {
    private static final String TAG = "UhfReader/Inventory";

    // ========== Fields ==========
    private ReaderSessionManager session;
    private InventoryAdapter adapter;
    private MaterialButton startButton;
    private TextView totalView;
    private TextView emptyView;
    private TextView dataHeader;
    private TextView rssiHeader;
    private TextView chipHeader;
    private InventoryMaskPanelController maskPanel;
    private ReaderState readerState = ReaderState.disconnected();
    private ReaderConfiguration configuration;
    private List<InventoryItem> exportItems = List.of();
    private InventoryDetailSheet detailSheet;

    private final ActivityResultLauncher<String> createCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), this::writeCsv);

    public static InventoryFragment newInstance() { return new InventoryFragment(); }

    @Override
    protected int getLayoutId() { return R.layout.inventory_fragment; }

    @Override
    protected void initView() {
        Log.d(TAG, "初始化盘点页面视图");
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        startButton = findViewById(R.id.btn_inventory_start);
        totalView = findViewById(R.id.tv_inventory_total);
        emptyView = findViewById(R.id.tv_inventory_empty);
        dataHeader = findViewById(R.id.tv_inventory_column_data);
        rssiHeader = findViewById(R.id.tv_inventory_column_rssi);
        chipHeader = findViewById(R.id.tv_inventory_column_chip);
        maskPanel = new InventoryMaskPanelController(this,
                findViewById(R.id.inventory_root),
                InventoryMaskPanelController.Appearance.INVENTORY,
                new InventoryMaskPanelController.Listener() {
                    @Override public void onApplyMask() { applyMask(); }
                    @Override public void onClearMask() { clearMask(); }
                });
        RecyclerView recyclerView = findViewById(R.id.rv_inventory_items);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        adapter = new InventoryAdapter(this::showItemDetail);
        recyclerView.setAdapter(adapter);
        applyColumnVisibility();

        startButton.setOnClickListener(view -> toggleInventory());
        findViewById(R.id.btn_inventory_clear).setOnClickListener(view -> session.clearInventory());
        findViewById(R.id.btn_inventory_export).setOnClickListener(view -> {
            exportItems = session.getInventorySnapshot();
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            createCsv.launch("uhf-inventory-" + date + ".csv");
        });
        findViewById(R.id.inventory_root).setOnClickListener(view -> dismissMaskKeyboard());
        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    dismissMaskKeyboard();
                }
                return false;
            }
        });
        syncMaskFromSession();
    }

    @Override
    protected void initData() {
        Log.d(TAG, "初始化盘点页面数据，注册 Reader 观察者");
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        session.addObserver(this);
    }

    /** Keeps the mask form collapsed whenever this ViewPager page becomes active. */
    @Override
    public void onResume() {
        super.onResume();
        maskPanel.setExpanded(false);
        syncMaskFromSession();
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "销毁盘点页面，清理资源");
        if (session != null) {
            if (session.getState().isInventoryRunning()) {
                Log.i(TAG, "页面销毁时停止盘点");
                session.stopInventory();
            }
            session.removeObserver(this);
        }
        if (detailSheet != null) {
            detailSheet.dismiss();
            detailSheet = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        TagProtocol previousProtocol = readerState.getProtocol();
        readerState = state;
        if (!isViewReady()) { return; }
        if (previousProtocol != state.getProtocol()) {
            maskPanel.updateProtocol(state.getProtocol());
        }
        applyColumnVisibility();
        startButton.setEnabled(true);
        startButton.setText(state.isInventoryRunning()
                ? R.string.inventory_stop : R.string.inventory_start);
        startButton.setIconResource(state.isInventoryRunning()
                ? R.drawable.rfid_inventory_stop_ic : R.drawable.rfid_inventory_play_ic);
        maskPanel.setConnected(state.isConnected());
    }

    @Override
    public void onInventoryChanged(List<InventoryItem> items, long totalReads) {
        if (!isViewReady()) { return; }
        Log.d(TAG, "盘点数据更新: " + items.size() + " 个标签, 总读取次数: " + totalReads);
        adapter.submitList(items);
        totalView.setText(getString(R.string.inventory_total, items.size()));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onReaderConfigurationChanged(ReaderConfiguration value) {
        configuration = value;
        if (!isViewReady()) { return; }
        applyColumnVisibility();
    }

    private void applyColumnVisibility() {
        if (adapter == null) { return; }
        ModuleSubtype subtype = readerState.getModuleSubtype();
        boolean rssiVisible = subtype == ModuleSubtype.R2000 || subtype == ModuleSubtype.R2000_PLUS;
        InventoryArea area = InventoryArea.of(readerState.getProtocol(),
                configuration == null ? 0 : configuration.inventoryArea);
        boolean chipVisible = readerState.getProtocol() == TagProtocol.ISO_18000_6C
                && area == InventoryArea.C_EPC_TID
                && (configuration == null || configuration.inventoryAddress == 0);
        adapter.setModuleSubtype(subtype);
        adapter.setChipVisible(chipVisible);
        adapter.setInventoryArea(area);
        adapter.setMaskContext(readerState.getProtocol(),
                configuration == null ? 0 : configuration.inventoryAddress);
        rssiHeader.setVisibility(rssiVisible ? View.VISIBLE : View.GONE);
        chipHeader.setVisibility(View.GONE);
        dataHeader.setText(area.getColumnHeader());
    }

    private void showItemDetail(InventoryItem item) {
        InventoryArea area = InventoryArea.of(readerState.getProtocol(),
                configuration == null ? 0 : configuration.inventoryArea);
        detailSheet = new InventoryDetailSheet(requireContext(), item, area, this::fillMaskFromItem);
        detailSheet.setOnDismissListener(dialog -> detailSheet = null);
        detailSheet.show();
    }

    private void fillMaskFromItem(int bank, String hexValue) {
        if (bank < 0 || hexValue.isEmpty()) { return; }
        if (session.getState().isInventoryRunning()) {
            session.stopInventory();
        }
        dismissMaskKeyboard();
        maskPanel.fill(bank, hexValue);
    }

    // ========== Inventory controls ==========

    /**
     * 切换盘点状态（开始/停止）
     *
     * 如果当前正在盘点，则停止盘点；
     * 如果当前未盘点，则启动盘点。
     *
     * 前置条件：
     * - Reader 必须已连接
     * - 单击防抖保护（@SingleClick 注解）
     */
    @SingleClick
    private void toggleInventory() {
        ReaderState state = session.getState();
        if (!state.isConnected() && !requireReaderOnline()) {
            Log.w(TAG, "Reader 未连接，无法切换盘点状态");
            return;
        }

        if (state.isInventoryRunning()) {
            Log.i(TAG, "停止盘点");
            session.stopInventory().whenComplete((status, error) ->
                    showResult(status, error, R.string.inventory_stop_failed));
        } else {
            Log.i(TAG, "开始盘点");
            session.startInventory().whenComplete((status, error) ->
                    showResult(status, error, R.string.inventory_start_failed));
        }
    }

    private void showResult(Integer status, Throwable error, @StringRes int message) {
        runOnViewThread(() -> {
            if (error != null) { toast(ThrowableUtils.rootMessage(error)); }
            else if (status != null && status != 0) {
                toast(getString(R.string.config_error_code, getString(message), status));
            }
        });
    }

    // ========== Inventory mask ==========

    @Override
    public void onInventoryMaskChanged(@Nullable InventoryMaskConfig config) {
        if (!isViewReady()) { return; }
        maskPanel.setActiveMask(config);
        if (adapter != null) { adapter.setMaskConfig(config); }
    }

    private void applyMask() {
        if (!readerState.isConnected()) {
            requireReaderOnline();
            return;
        }
        try {
            InventoryMaskFormParser.Result parsed = maskPanel.parse();
            if (!parsed.isSuccess()) { throw new MaskFormException(parsed.getError()); }
            InventoryMaskConfig config = parsed.getConfig();
            maskPanel.setOperationInFlight(true);
            session.applyInventoryMask(config).whenComplete((status, error) ->
                    showMaskResult(status, error, R.string.inventory_mask_applied,
                            R.string.inventory_mask_apply_failed));
        } catch (MaskFormException error) {
            maskPanel.setExpanded(true);
            maskPanel.focus(error.error);
            toast(maskErrorMessage(error.error));
        }
    }

    private void clearMask() {
        if (!readerState.isConnected()) {
            requireReaderOnline();
            return;
        }
        maskPanel.setOperationInFlight(true);
        session.clearInventoryMask().whenComplete((status, error) ->
                showMaskResult(status, error, R.string.inventory_mask_cleared,
                        R.string.inventory_mask_clear_failed));
    }

    private void showMaskResult(Integer status, Throwable error, @StringRes int successMessage,
            @StringRes int failureMessage) {
        runOnViewThread(() -> {
            maskPanel.setOperationInFlight(false);
            if (error != null) {
                Log.e(TAG, getString(failureMessage), error);
                toast(ThrowableUtils.rootMessage(error));
            } else if (status != null && status != 0) {
                Log.e(TAG, getString(failureMessage) + " status=" + status);
                toast(getString(R.string.config_error_code, getString(failureMessage), status));
            } else {
                Log.i(TAG, getString(successMessage));
                toast(successMessage);
            }
            maskPanel.setConnected(readerState.isConnected());
        });
    }

    private void syncMaskFromSession() {
        InventoryMaskConfig activeMask = session.getInventoryMask();
        maskPanel.setActiveMask(activeMask);
        if (adapter != null) { adapter.setMaskConfig(activeMask); }
    }

    private void dismissMaskKeyboard() {
        View focused = requireActivity().getCurrentFocus();
        if (focused != null) {
            hideKeyboard(focused);
            focused.clearFocus();
        }
    }

    // ========== Data export ==========

    /**
     * 导出盘点数据为 CSV 文件
     *
     * CSV 格式：
     * index,id,additional_data,count,rssi,chip_model
     * 1,"E280...","-",5,-65,"Impinj M730"
     *
     * 使用流式写入避免大数据集（几千条）内存溢出
     *
     * @param uri 用户选择的保存位置
     */
    private void writeCsv(Uri uri) {
        if (uri == null) {
            return;
        }

        Log.i(TAG, "开始导出 CSV，数据量: " + exportItems.size());

        try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {

            if (output == null) {
                throw new IOException("Unable to open document");
            }

            InventoryCsvExporter.write(output, exportItems);
            Log.i(TAG, "CSV 导出成功，共 " + exportItems.size() + " 条");
            toast(R.string.inventory_exported);

        } catch (IOException error) {
            Log.e(TAG, "CSV 导出失败", error);
            toast(getString(R.string.inventory_export_failed, error.getMessage()));
        }
    }

    private String maskErrorMessage(InventoryMaskFormParser.Error error) {
        if (error == InventoryMaskFormParser.Error.OFFSET_INVALID
                || error == InventoryMaskFormParser.Error.LENGTH_INVALID) {
            int label = error == InventoryMaskFormParser.Error.OFFSET_INVALID
                    ? R.string.inventory_mask_offset : R.string.inventory_mask_length;
            return getString(R.string.inventory_mask_number_invalid, getString(label));
        }
        return getString(switch (error) {
            case LENGTH_NOT_POSITIVE -> R.string.inventory_mask_length_positive;
            case HEX_REQUIRED -> R.string.inventory_mask_hex_required;
            case HEX_INVALID -> R.string.inventory_mask_hex_invalid;
            case DATA_TOO_LONG -> R.string.inventory_mask_too_long;
            case LENGTH_EXCEEDS_DATA -> R.string.inventory_mask_length_exceeds_data;
            case SIX_B_LENGTH_NOT_BYTE_ALIGNED -> R.string.inventory_mask_6b_byte_aligned;
            case OFFSET_OUT_OF_RANGE -> R.string.inventory_mask_offset_range;
            default -> R.string.inventory_mask_hex_invalid;
        });
    }

    private static final class MaskFormException extends RuntimeException {
        final InventoryMaskFormParser.Error error;
        MaskFormException(InventoryMaskFormParser.Error error) { this.error = error; }
    }
}
