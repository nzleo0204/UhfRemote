package com.leo.remote.ui.fragment.home;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.leo.remote.R;
import com.leo.remote.aop.SingleClick;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.HexCodec;
import com.leo.remote.reader.InventoryMaskConfig;
import com.leo.remote.reader.InventoryItem;
import com.leo.remote.reader.InventoryArea;
import com.leo.remote.reader.ModuleSubtype;
import com.leo.remote.reader.ProtocolEncoding;
import com.leo.remote.reader.ReaderConfiguration;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.TagProtocol;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.adapter.InventoryAdapter;
import com.leo.remote.ui.dialog.InventoryDetailSheet;
import com.leo.remote.util.ViewUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    private View maskPanelContent;
    private Spinner maskBankSpinner;
    private EditText maskOffsetView;
    private EditText maskLengthView;
    private EditText maskHexView;
    private MaterialButton maskToggleButton;
    private TextView maskLengthHintView;
    private TextView maskStatusView;
    private ImageView maskExpandView;
    private ReaderState readerState = ReaderState.disconnected();
    private ReaderConfiguration configuration;
    private InventoryMaskConfig activeMask;
    private boolean maskExpanded;
    private boolean maskOperationInFlight;
    private List<InventoryItem> exportItems = List.of();

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
        maskPanelContent = findViewById(R.id.ll_inventory_mask_content);
        maskBankSpinner = findViewById(R.id.sp_inventory_mask_bank);
        maskOffsetView = findViewById(R.id.et_inventory_mask_offset);
        maskLengthView = findViewById(R.id.et_inventory_mask_length);
        maskHexView = findViewById(R.id.et_inventory_mask_hex);
        maskToggleButton = findViewById(R.id.btn_inventory_mask_toggle);
        maskLengthHintView = findViewById(R.id.tv_inventory_mask_length_hint);
        maskStatusView = findViewById(R.id.tv_inventory_mask_status);
        maskExpandView = findViewById(R.id.iv_inventory_mask_expand);
        RecyclerView recyclerView = findViewById(R.id.rv_inventory_items);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        adapter = new InventoryAdapter(this::showItemDetail);
        recyclerView.setAdapter(adapter);
        applyColumnVisibility();

        maskBankSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                maskOffsetView.setText(String.valueOf(ProtocolEncoding.defaultMaskOffsetBits(
                        readerState.getProtocol(), position)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        TextWatcher maskFormWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) { updateMaskControls(); }
        };
        maskHexView.addTextChangedListener(maskFormWatcher);
        maskLengthView.addTextChangedListener(maskFormWatcher);
        maskHexView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) { return; }
            int bitLength = maskHexView.getText().toString().trim().length() * 4;
            if (readerState.getProtocol() == TagProtocol.ISO_18000_6B) {
                bitLength -= bitLength % 8;
            }
            maskLengthView.setText(bitLength == 0 ? "" : String.valueOf(bitLength));
        });
        updateMaskBanks(readerState.getProtocol());
        startButton.setOnClickListener(view -> toggleInventory());
        findViewById(R.id.btn_inventory_clear).setOnClickListener(view -> session.clearInventory());
        findViewById(R.id.btn_inventory_export).setOnClickListener(view -> {
            exportItems = session.getInventorySnapshot();
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            createCsv.launch("uhf-inventory-" + date + ".csv");
        });
        findViewById(R.id.row_inventory_mask_toggle).setOnClickListener(view -> {
            dismissMaskKeyboard();
            maskExpanded = !maskExpanded;
            updateMaskControls();
        });
        maskToggleButton.setOnClickListener(view -> toggleMask());
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
        maskExpanded = false;
        syncMaskFromSession();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "销毁盘点页面，清理资源");
        if (session != null) {
            if (session.getState().isInventoryRunning()) {
                Log.i(TAG, "页面销毁时停止盘点");
                session.stopInventory();
            }
            session.removeObserver(this);
        }
        super.onDestroy();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        TagProtocol previousProtocol = readerState.getProtocol();
        readerState = state;
        if (!isViewAlive()) { return; }
        if (previousProtocol != state.getProtocol()) {
            updateMaskBanks(state.getProtocol());
        }
        applyColumnVisibility();
        startButton.setEnabled(true);
        startButton.setText(state.isInventoryRunning()
                ? R.string.inventory_stop : R.string.inventory_start);
        startButton.setIconResource(state.isInventoryRunning()
                ? R.drawable.rfid_inventory_stop_ic : R.drawable.rfid_inventory_play_ic);
        updateMaskControls();
    }

    @Override
    public void onInventoryChanged(List<InventoryItem> items, long totalReads) {
        if (!isViewAlive()) { return; }
        Log.d(TAG, "盘点数据更新: " + items.size() + " 个标签, 总读取次数: " + totalReads);
        adapter.submitList(items);
        totalView.setText(getString(R.string.inventory_total, items.size()));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onReaderConfigurationChanged(ReaderConfiguration value) {
        configuration = value;
        if (!isViewAlive()) { return; }
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
        new InventoryDetailSheet(requireContext(), item, area, this::fillMaskFromItem).show();
    }

    private void fillMaskFromItem(int bank, String hexValue) {
        if (bank < 0 || bank >= maskBankSpinner.getCount() || hexValue.isEmpty()) { return; }
        if (session.getState().isInventoryRunning()) {
            session.stopInventory();
        }
        dismissMaskKeyboard();
        maskBankSpinner.setSelection(bank);
        maskOffsetView.setText(String.valueOf(ProtocolEncoding.defaultMaskOffsetBits(
                readerState.getProtocol(), bank)));
        maskHexView.setText(hexValue);
        maskLengthView.setText(String.valueOf(hexValue.length() * 4));
        maskExpanded = true;
        updateMaskControls();
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
        requireActivity().runOnUiThread(() -> {
            if (error != null) { toast(rootMessage(error)); }
            else if (status != null && status != 0) {
                toast(getString(R.string.config_error_code, getString(message), status));
            }
        });
    }

    // ========== Inventory mask ==========

    @Override
    public void onInventoryMaskChanged(@Nullable InventoryMaskConfig config) {
        if (!isViewAlive()) { return; }
        activeMask = config;
        if (config != null) { bindMaskForm(config); }
        if (adapter != null) { adapter.setMaskConfig(config); }
        updateMaskControls();
    }

    /** Single button with two states: apply when clear, cancel when active. */
    @SingleClick
    private void toggleMask() {
        if (activeMask != null) {
            clearMask();
        } else {
            applyMask();
        }
    }

    private void applyMask() {
        if (!readerState.isConnected()) {
            requireReaderOnline();
            return;
        }
        try {
            InventoryMaskConfig config = parseMaskForm();
            maskOperationInFlight = true;
            updateMaskControls();
            session.applyInventoryMask(config).whenComplete((status, error) ->
                    showMaskResult(status, error, R.string.inventory_mask_applied,
                            R.string.inventory_mask_apply_failed));
        } catch (IllegalArgumentException error) {
            maskExpanded = true;
            focusInvalidMaskField();
            updateMaskControls();
            toast(error.getMessage());
        }
    }

    private void clearMask() {
        if (!readerState.isConnected()) {
            requireReaderOnline();
            return;
        }
        maskOperationInFlight = true;
        updateMaskControls();
        session.clearInventoryMask().whenComplete((status, error) ->
                showMaskResult(status, error, R.string.inventory_mask_cleared,
                        R.string.inventory_mask_clear_failed));
    }

    private void showMaskResult(Integer status, Throwable error, @StringRes int successMessage,
            @StringRes int failureMessage) {
        if (!isViewAlive()) { return; }
        requireActivity().runOnUiThread(() -> {
            if (!isViewAlive()) { return; }
            maskOperationInFlight = false;
            if (error != null) {
                Log.e(TAG, getString(failureMessage), error);
                toast(rootMessage(error));
            } else if (status != null && status != 0) {
                Log.e(TAG, getString(failureMessage) + " status=" + status);
                toast(getString(R.string.config_error_code, getString(failureMessage), status));
            } else {
                Log.i(TAG, getString(successMessage));
                toast(successMessage);
            }
            updateMaskControls();
        });
    }

    private void focusInvalidMaskField() {
        EditText target = maskHexView;
        String hex = maskHexView.getText().toString().trim();
        if (hex.matches("[0-9A-Fa-f]+") && (hex.length() & 1) == 0) {
            target = maskLengthView;
            try {
                if (Integer.parseInt(maskLengthView.getText().toString()) > 0) {
                    target = maskOffsetView;
                }
            } catch (NumberFormatException ignored) {
                target = maskLengthView;
            }
        }
        target.requestFocus();
    }

    private InventoryMaskConfig parseMaskForm() {
        TagProtocol protocol = readerState.getProtocol();
        int offset = parseMaskInteger(maskOffsetView, R.string.inventory_mask_offset);
        int length = parseMaskInteger(maskLengthView, R.string.inventory_mask_length);
        if (length == 0) {
            throw new IllegalArgumentException(getString(R.string.inventory_mask_length_positive));
        }
        String hex = maskHexView.getText().toString().trim();
        if (hex.isEmpty()) {
            throw new IllegalArgumentException(getString(R.string.inventory_mask_hex_required));
        }
        if ((hex.length() & 1) != 0 || !hex.matches("[0-9A-Fa-f]+")) {
            throw new IllegalArgumentException(getString(R.string.inventory_mask_hex_invalid));
        }
        byte[] mask = HexCodec.decode(hex);
        if (mask.length > 64) {
            throw new IllegalArgumentException(getString(R.string.inventory_mask_too_long));
        }
        if (length > mask.length * 8) {
            throw new IllegalArgumentException(getString(R.string.inventory_mask_length_exceeds_data));
        }
        if (protocol == TagProtocol.ISO_18000_6B && (length & 7) != 0) {
            throw new IllegalArgumentException(getString(R.string.inventory_mask_6b_byte_aligned));
        }
        if ((protocol == TagProtocol.GJB_7377_1 || protocol == TagProtocol.GB_T_29768)
                && offset > 0xFF) {
            throw new IllegalArgumentException(getString(R.string.inventory_mask_offset_range));
        }
        return new InventoryMaskConfig(maskBank(protocol), offset, length, mask);
    }

    private int parseMaskInteger(EditText view, @StringRes int label) {
        try {
            int value = Integer.parseInt(view.getText().toString());
            if (value < 0) { throw new NumberFormatException(); }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(getString(
                    R.string.inventory_mask_number_invalid, getString(label)));
        }
    }

    private int maskBank(TagProtocol protocol) {
        return switch (protocol) {
            case ISO_18000_6C, GB_T_29768 -> maskBankSpinner.getSelectedItemPosition();
            case ISO_18000_6B -> 0;
            case GJB_7377_1 -> 1;
        };
    }

    /** Rebuilds bank choices because each RFID protocol exposes different memory areas. */
    private void updateMaskBanks(TagProtocol protocol) {
        int labels = switch (protocol) {
            case ISO_18000_6C -> R.array.single_bank_labels_6c;
            case ISO_18000_6B -> R.array.inventory_mask_bank_uid;
            case GJB_7377_1 -> R.array.inventory_mask_bank_epc;
            case GB_T_29768 -> R.array.single_bank_labels_gb;
        };
        ArrayAdapter<CharSequence> bankAdapter = ArrayAdapter.createFromResource(requireContext(),
                labels, android.R.layout.simple_spinner_item);
        bankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        maskBankSpinner.setAdapter(bankAdapter);
        maskBankSpinner.setSelection(protocol == TagProtocol.ISO_18000_6C
                || protocol == TagProtocol.GB_T_29768 ? 1 : 0);
        maskOffsetView.setText(String.valueOf(ProtocolEncoding.defaultMaskOffsetBits(protocol,
                maskBankSpinner.getSelectedItemPosition())));
        updateMaskControls();
        Log.d(TAG, "mask banks updated protocol=" + protocol);
    }

    private void bindMaskForm(InventoryMaskConfig config) {
        TagProtocol protocol = readerState.getProtocol();
        int position = switch (protocol) {
            case ISO_18000_6C, GB_T_29768 -> config.bank;
            case ISO_18000_6B, GJB_7377_1 -> 0;
        };
        if (position >= 0 && position < maskBankSpinner.getCount()) {
            maskBankSpinner.setSelection(position);
        }
        maskOffsetView.setText(String.valueOf(config.offsetBits));
        maskLengthView.setText(String.valueOf(config.lengthBits));
        maskHexView.setText(HexCodec.encode(config.getMask(), config.getMaskByteLength()));
    }

    private void updateMaskControls() {
        boolean connected = readerState.isConnected();
        boolean formEnabled = connected && !maskOperationInFlight;
        boolean formValid = updateMaskLengthHint();
        maskPanelContent.setVisibility(maskExpanded ? View.VISIBLE : View.GONE);
        maskPanelContent.setAlpha(formEnabled ? 1f : 0.48f);
        maskExpandView.setImageResource(R.drawable.arrows_bottom_ic);
        maskExpandView.setRotation(maskExpanded ? 180f : 0f);
        maskExpandView.setContentDescription(getString(maskExpanded
                ? R.string.inventory_mask_collapse : R.string.inventory_mask_expand));
        ViewUtils.setEnabledRecursively(maskPanelContent, formEnabled);
        maskOffsetView.setEnabled(formEnabled
                && readerState.getProtocol() != TagProtocol.ISO_18000_6B);
        boolean masked = activeMask != null;
        maskToggleButton.setText(masked
                ? R.string.inventory_mask_cancel : R.string.inventory_mask_apply);
        maskToggleButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(),
                masked ? R.color.rfid_danger_button_background
                        : R.color.rfid_primary_button_background));
        maskToggleButton.setTextColor(ContextCompat.getColorStateList(requireContext(),
                R.color.rfid_primary_button_text));
        maskToggleButton.setEnabled(formEnabled && (masked || formValid));
        maskStatusView.setVisibility(View.VISIBLE);
        if (masked) {
            Object bank = maskBankSpinner.getSelectedItem();
            String bankLabel = bank == null ? "" : bank.toString();
            maskStatusView.setBackgroundResource(R.drawable.rfid_chip_red_bg);
            maskStatusView.setText(getString(R.string.inventory_mask_active, bankLabel,
                    activeMask.offsetBits, activeMask.lengthBits));
        } else {
            maskStatusView.setBackgroundResource(R.drawable.rfid_chip_blue_bg);
            maskStatusView.setText(R.string.inventory_mask_inactive);
        }
    }

    private boolean updateMaskLengthHint() {
        String hex = maskHexView.getText().toString().trim();
        if (hex.isEmpty()) {
            setMaskLengthHint(R.string.inventory_mask_length_hint_empty, false);
            return false;
        }
        if ((hex.length() & 1) != 0 || !hex.matches("[0-9A-Fa-f]+")) {
            setMaskLengthHint(R.string.inventory_mask_length_hint_odd, true, hex.length());
            return false;
        }
        int dataBits = hex.length() * 4;
        int length;
        try {
            length = Integer.parseInt(maskLengthView.getText().toString());
        } catch (NumberFormatException error) {
            setMaskLengthHint(R.string.inventory_mask_length_positive, true);
            return false;
        }
        if (length <= 0) {
            setMaskLengthHint(R.string.inventory_mask_length_positive, true);
            return false;
        }
        if (length > dataBits) {
            setMaskLengthHint(R.string.inventory_mask_length_hint_short, true, length, dataBits);
            return false;
        }
        if (readerState.getProtocol() == TagProtocol.ISO_18000_6B && (length & 7) != 0) {
            setMaskLengthHint(R.string.inventory_mask_6b_byte_aligned, true);
            return false;
        }
        setMaskLengthHint(R.string.inventory_mask_length_hint_ok, false,
                hex.length() / 2, dataBits, length);
        return true;
    }

    private void setMaskLengthHint(@StringRes int message, boolean warning, Object... arguments) {
        maskLengthHintView.setText(arguments.length == 0
                ? getString(message) : getString(message, arguments));
        maskLengthHintView.setTextColor(ContextCompat.getColor(requireContext(),
                warning ? R.color.rfid_warning : R.color.rfid_text_muted));
    }

    private boolean isViewAlive() {
        return getView() != null && isAdded();
    }

    private void syncMaskFromSession() {
        activeMask = session.getInventoryMask();
        if (activeMask != null) { bindMaskForm(activeMask); }
        if (adapter != null) { adapter.setMaskConfig(activeMask); }
        updateMaskControls();
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

        try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             BufferedWriter buffered = new BufferedWriter(writer)) {

            if (output == null) {
                throw new IOException("Unable to open document");
            }

            // 写入表头
            buffered.write("index,id,additional_data,count,rssi,chip_model\r\n");

            // 流式写入数据，避免内存溢出
            for (int i = 0; i < exportItems.size(); i++) {
                InventoryItem item = exportItems.get(i);
                String line = String.format("%d,%s,%s,%d,%d,%s\r\n",
                        i + 1,
                        escape(item.getId()),
                        escape(item.getData()),
                        item.getCount(),
                        item.getRssi(),
                        escape(item.getChipModel()));
                buffered.write(line);

                // 每 100 条刷新一次
                if ((i + 1) % 100 == 0) {
                    buffered.flush();
                    Log.d(TAG, "已写入 " + (i + 1) + " 条数据");
                }
            }

            buffered.flush();
            Log.i(TAG, "CSV 导出成功，共 " + exportItems.size() + " 条");
            toast(R.string.inventory_exported);

        } catch (IOException error) {
            Log.e(TAG, "CSV 导出失败", error);
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
