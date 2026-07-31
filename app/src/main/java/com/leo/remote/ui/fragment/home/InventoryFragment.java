package com.leo.remote.ui.fragment.home;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.leo.remote.R;
import com.leo.remote.aop.SingleClick;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.HexCodec;
import com.leo.remote.reader.InventoryMaskConfig;
import com.leo.remote.reader.InventoryItem;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.TagProtocol;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.adapter.InventoryAdapter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Live RFID inventory page. */
@SuppressLint("LogNotTimber")
public final class InventoryFragment extends AppFragment<HomeActivity> implements ReaderObserver {
    private static final String TAG = "UhfReader/Inventory";

    // ========== Fields ==========
    private ReaderSessionManager session;
    private InventoryAdapter adapter;
    private MaterialButton startButton;
    private TextView totalView;
    private TextView emptyView;
    private View maskPanelContent;
    private Spinner maskBankSpinner;
    private EditText maskOffsetView;
    private EditText maskLengthView;
    private EditText maskHexView;
    private MaterialButton maskApplyButton;
    private MaterialButton maskClearButton;
    private TextView maskStatusView;
    private ImageView maskExpandView;
    private SwitchMaterial maskSwitch;
    private ReaderState readerState = ReaderState.disconnected();
    private InventoryMaskConfig activeMask;
    private boolean maskExpanded;
    private boolean bindingMaskSwitch;
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
        maskPanelContent = findViewById(R.id.ll_inventory_mask_content);
        maskBankSpinner = findViewById(R.id.sp_inventory_mask_bank);
        maskOffsetView = findViewById(R.id.et_inventory_mask_offset);
        maskLengthView = findViewById(R.id.et_inventory_mask_length);
        maskHexView = findViewById(R.id.et_inventory_mask_hex);
        maskApplyButton = findViewById(R.id.btn_inventory_mask_apply);
        maskClearButton = findViewById(R.id.btn_inventory_mask_clear);
        maskStatusView = findViewById(R.id.tv_inventory_mask_status);
        maskExpandView = findViewById(R.id.iv_inventory_mask_expand);
        maskSwitch = findViewById(R.id.sw_inventory_mask);
        RecyclerView recyclerView = findViewById(R.id.rv_inventory_items);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        adapter = new InventoryAdapter();
        recyclerView.setAdapter(adapter);

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
        maskSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingMaskSwitch) { return; }
            maskExpanded = checked;
            if (!checked && activeMask != null) {
                clearMask(true);
            } else {
                updateMaskControls();
            }
        });
        maskApplyButton.setOnClickListener(view -> applyMask());
        maskClearButton.setOnClickListener(view -> clearMask());
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
        updateMaskControls();
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
        TagProtocol previousProtocol = readerState.getProtocol();
        readerState = state;
        if (previousProtocol != state.getProtocol()) {
            updateMaskBanks(state.getProtocol());
        }
        startButton.setEnabled(true);
        startButton.setText(state.isInventoryRunning()
                ? R.string.inventory_stop : R.string.inventory_start);
        startButton.setIconResource(state.isInventoryRunning()
                ? R.drawable.rfid_inventory_stop_ic : R.drawable.rfid_inventory_play_ic);
        updateMaskControls();
    }

    @Override
    public void onInventoryChanged(List<InventoryItem> items, long totalReads) {
        adapter.submitList(items);
        totalView.setText(getString(R.string.inventory_total, items.size()));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ========== Inventory controls ==========

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

    // ========== Inventory mask ==========

    @Override
    public void onInventoryMaskChanged(@Nullable InventoryMaskConfig config) {
        activeMask = config;
        if (config != null) {
            setMaskSwitchChecked(true);
            maskExpanded = true;
            bindMaskForm(config);
        }
        updateMaskControls();
    }

    /** Applies only a manually submitted mask after protocol-specific validation. */
    @SingleClick
    private void applyMask() {
        if (!readerState.isConnected()) {
            toast(R.string.inventory_connect_first);
            return;
        }
        try {
            InventoryMaskConfig config = parseMaskForm();
            session.applyInventoryMask(config).whenComplete((status, error) ->
                    showMaskResult(status, error, R.string.inventory_mask_applied,
                            R.string.inventory_mask_apply_failed, false));
        } catch (IllegalArgumentException error) {
            toast(error.getMessage());
        }
    }

    /** Clears Select criteria while preserving the form for reuse. */
    @SingleClick
    private void clearMask() {
        clearMask(false);
    }

    private void clearMask(boolean restoreSwitchOnFailure) {
        session.clearInventoryMask().whenComplete((status, error) ->
                showMaskResult(status, error, R.string.inventory_mask_cleared,
                        R.string.inventory_mask_clear_failed, restoreSwitchOnFailure));
    }

    private void showMaskResult(Integer status, Throwable error, @StringRes int successMessage,
            @StringRes int failureMessage, boolean restoreSwitchOnFailure) {
        requireActivity().runOnUiThread(() -> {
            if (error != null) {
                Log.e(TAG, getString(failureMessage), error);
                toast(rootMessage(error));
                restoreMaskSwitch(restoreSwitchOnFailure);
            } else if (status != null && status != 0) {
                Log.e(TAG, getString(failureMessage) + " status=" + status);
                toast(getString(R.string.config_error_code, getString(failureMessage), status));
                restoreMaskSwitch(restoreSwitchOnFailure);
            } else {
                Log.i(TAG, getString(successMessage));
                toast(successMessage);
            }
        });
    }

    private void restoreMaskSwitch(boolean restore) {
        if (!restore) { return; }
        setMaskSwitchChecked(true);
        maskExpanded = true;
        updateMaskControls();
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
        if (protocol == TagProtocol.ISO_18000_6B) { maskOffsetView.setText("0"); }
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
        boolean formEnabled = maskSwitch.isChecked();
        maskPanelContent.setVisibility(maskExpanded ? View.VISIBLE : View.GONE);
        maskPanelContent.setAlpha(formEnabled ? 1f : 0.48f);
        maskExpandView.setImageResource(maskExpanded
                ? R.drawable.arrows_top_ic : R.drawable.arrows_bottom_ic);
        setEnabledRecursively(maskPanelContent, formEnabled);
        maskOffsetView.setEnabled(formEnabled
                && readerState.getProtocol() != TagProtocol.ISO_18000_6B);
        maskApplyButton.setEnabled(formEnabled && readerState.isConnected());
        maskClearButton.setEnabled(formEnabled && readerState.isConnected() && activeMask != null);
        maskStatusView.setVisibility(activeMask == null ? View.GONE : View.VISIBLE);
        if (activeMask != null) {
            Object bank = maskBankSpinner.getSelectedItem();
            String bankLabel = bank == null ? "" : bank.toString();
            maskStatusView.setText(getString(R.string.inventory_mask_active, bankLabel,
                    activeMask.offsetBits, activeMask.lengthBits));
        }
    }

    private void setEnabledRecursively(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof ViewGroup group)) { return; }
        for (int i = 0; i < group.getChildCount(); i++) {
            setEnabledRecursively(group.getChildAt(i), enabled);
        }
    }

    private void setMaskSwitchChecked(boolean checked) {
        bindingMaskSwitch = true;
        maskSwitch.setChecked(checked);
        bindingMaskSwitch = false;
    }

    private void dismissMaskKeyboard() {
        View focused = requireActivity().getCurrentFocus();
        if (focused != null) {
            hideKeyboard(focused);
            focused.clearFocus();
        }
    }

    // ========== Data export ==========

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
