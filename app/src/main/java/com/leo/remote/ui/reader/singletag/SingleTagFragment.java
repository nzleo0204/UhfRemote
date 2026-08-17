package com.leo.remote.ui.reader.singletag;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.remote.aop.SingleClick;
import com.leo.remote.reader.model.HexCodec;
import com.leo.remote.reader.model.InventoryMaskConfig;
import com.leo.remote.reader.inventory.InventoryMaskFormParser;
import com.leo.remote.reader.model.ProtocolEncoding;
import com.leo.remote.reader.model.ReaderConfiguration;
import com.leo.remote.reader.session.ReaderObserver;
import com.leo.remote.reader.session.ReaderSessionManager;
import com.leo.remote.reader.model.ReaderState;
import com.leo.remote.reader.model.ReaderTag;
import com.leo.remote.reader.model.TagReadResult;
import com.leo.remote.reader.model.TagProtocol;
import com.leo.remote.reader.tag.SingleTagReadFormatter;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.dialog.common.MessageDialog;
import com.leo.remote.ui.reader.common.InventoryMaskPanelController;
import com.leo.remote.ui.reader.common.ReaderFragment;
import com.leo.remote.ui.reader.singletag.TagKillDialog;
import com.leo.remote.ui.reader.singletag.TagLockDialog;
import com.leo.remote.ui.reader.singletag.TagWriteDialog;
import com.leo.remote.util.ThrowableUtils;
import java.util.concurrent.CompletableFuture;

/** Single-target RFID operations. */
@SuppressLint("LogNotTimber")
public final class SingleTagFragment extends ReaderFragment<HomeActivity> implements ReaderObserver {
    private static final String TAG = "UhfReader/SingleTag";

    // ========== Fields ==========
    private ReaderSessionManager session;

    // 读取参数控件
    private Spinner readBankSpinner;
    private Spinner gbSubBankSpinner;
    private EditText readAddressView;
    private EditText readLengthView;
    private EditText readPasswordView;
    private EditText auxiliaryView;
    private View auxiliaryGroup;
    private View gbSubBankGroup;
    private TextView auxiliaryLabel;
    private TextView readLengthLabel;
    private MaterialButton readButton;

    // 读取结果控件
    private View readResultPanel;
    private View readDataGroup;
    private TextView readDataView;
    private TextView fillDataMaskButton;
    private View chipGroup;
    private View rssiGroup;
    private TextView epcView;
    private TextView dataLabelView;
    private TextView chipView;
    private TextView rssiView;

    private View writeAction;
    private View updateEpcAction;
    private View lockAction;
    private View destroyAction;
    private InventoryMaskPanelController maskPanel;
    private ReaderTag currentTag;
    private ReaderState readerState = ReaderState.disconnected();

    private int lastReadBankPosition = -1;
    private AlertDialog activeDialog;

    public static SingleTagFragment newInstance() { return new SingleTagFragment(); }

    @Override
    protected int getLayoutId() { return R.layout.single_tag_fragment; }

    @Override
    protected void initView() {
        // 读取参数控件
        readBankSpinner = findViewById(R.id.sp_single_read_bank);
        gbSubBankSpinner = findViewById(R.id.sp_single_gb_sub_bank);
        readAddressView = findViewById(R.id.et_single_read_address);
        readLengthView = findViewById(R.id.et_single_read_length);
        readPasswordView = findViewById(R.id.et_single_read_password);
        auxiliaryView = findViewById(R.id.et_single_auxiliary);
        auxiliaryGroup = findViewById(R.id.group_single_auxiliary);
        gbSubBankGroup = findViewById(R.id.group_single_gb_sub_bank);
        auxiliaryLabel = findViewById(R.id.tv_single_auxiliary_label);
        readLengthLabel = findViewById(R.id.tv_single_read_length_label);
        readButton = findViewById(R.id.btn_single_read);

        // 读取结果控件
        readResultPanel = findViewById(R.id.group_single_read_result);
        readDataGroup = findViewById(R.id.group_single_read_data);
        readDataView = findViewById(R.id.tv_single_read_data);
        fillDataMaskButton = findViewById(R.id.btn_single_fill_read_data_mask);
        epcView = findViewById(R.id.tv_single_epc);
        dataLabelView = findViewById(R.id.tv_single_data_label);
        chipView = findViewById(R.id.tv_single_chip);
        rssiView = findViewById(R.id.tv_single_rssi);
        chipGroup = (View) chipView.getParent();
        rssiGroup = (View) rssiView.getParent();
        readResultPanel.setVisibility(View.GONE);

        writeAction = findViewById(R.id.ll_single_write);
        updateEpcAction = findViewById(R.id.ll_single_update_epc);
        lockAction = findViewById(R.id.ll_single_lock);
        destroyAction = findViewById(R.id.ll_single_destroy);
        maskPanel = new InventoryMaskPanelController(this,
                findViewById(R.id.single_tag_root),
                InventoryMaskPanelController.Appearance.SINGLE_TAG,
                new InventoryMaskPanelController.Listener() {
                    @Override public void onApplyMask() { applyMask(); }
                    @Override public void onClearMask() { clearMask(); }
                });

        // 初始化读取参数
        initReadParams();

        readButton.setOnClickListener(view -> readTag());
        fillDataMaskButton.setOnClickListener(view -> fillMaskFromReadData());
        writeAction.setOnClickListener(view -> showWriteDialog(false));
        updateEpcAction.setOnClickListener(view -> showWriteDialog(true));
        lockAction.setOnClickListener(view -> showLockDialog());
        destroyAction.setOnClickListener(view -> showKillDialog());
        findViewById(R.id.single_tag_root).setOnClickListener(view -> dismissMaskKeyboard());
        refreshOperations();
        maskPanel.setConnected(false);
    }

    @Override
    protected void initData() {
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        session.addObserver(this);
    }

    @Override
    public void onDestroyView() {
        if (session != null) { session.removeObserver(this); }
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }
        hideLoadingDialog();
        super.onDestroyView();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        TagProtocol previousProtocol = readerState.getProtocol();
        readerState = state;
        if (!isViewReady()) { return; }
        if (previousProtocol != state.getProtocol()) {
            maskPanel.updateProtocol(state.getProtocol());
            updateReadBanks(state.getProtocol());
        }
        if (!state.isConnected()) {
            currentTag = null;
            readResultPanel.setVisibility(View.GONE);
        }
        refreshOperations();
        maskPanel.setConnected(state.isConnected());
    }

    @Override
    public void onReaderConfigurationChanged(ReaderConfiguration value) {
    }

    @Override
    public void onCurrentTagChanged(ReaderTag tag) {
        currentTag = tag;
        if (!isViewReady()) { return; }
        refreshOperations();
    }

    @Override
    public void onSingleTagMaskChanged(@Nullable InventoryMaskConfig config) {
        if (!isViewReady()) { return; }
        maskPanel.setActiveMask(config);
    }

    private void initReadParams() {
        TagProtocol protocol = readerState.getProtocol();

        // 设置 Bank 选项
        String[] banks = bankLabels(protocol);
        readBankSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, banks));

        // 设置国标子区选项
        gbSubBankSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.single_gb_sub_bank_labels)));

        // 设置默认值
        readBankSpinner.setSelection(protocol == TagProtocol.ISO_18000_6C ? 1 : 0);
        readAddressView.setText(protocol == TagProtocol.ISO_18000_6C ? "2" : "0");
        readLengthView.setText(protocol == TagProtocol.ISO_18000_6B ? "8" : "4");
        readPasswordView.setText("00000000");
        auxiliaryView.setText(protocol == TagProtocol.ISO_18000_6B ? "3" : "4");

        // 协议联动
        updateReadParamsForProtocol(protocol);

        // Bank 选择监听
        readBankSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateGbSubBankVisibility(position);
                updateReadDefaults(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateReadParamsForProtocol(TagProtocol protocol) {
        // 更新长度标签
        if (protocol == TagProtocol.ISO_18000_6B) {
            readLengthLabel.setText(R.string.single_read_length_byte);
        } else {
            readLengthLabel.setText(R.string.single_read_length_word);
        }

        // 显示/隐藏辅助参数
        if (protocol == TagProtocol.ISO_18000_6C) {
            auxiliaryGroup.setVisibility(View.GONE);
        } else {
            auxiliaryGroup.setVisibility(View.VISIBLE);
            if (protocol == TagProtocol.ISO_18000_6B) {
                auxiliaryLabel.setText(R.string.single_retry_count_hint);
            } else {
                auxiliaryLabel.setText(R.string.single_block_length_hint);
            }
        }

        // 更新国标子区可见性
        updateGbSubBankVisibility(readBankSpinner.getSelectedItemPosition());
    }

    private void updateGbSubBankVisibility(int bankPosition) {
        TagProtocol protocol = readerState.getProtocol();
        boolean showGbSubBank = (protocol == TagProtocol.GB_T_29768) && (bankPosition == 3);
        gbSubBankGroup.setVisibility(showGbSubBank ? View.VISIBLE : View.GONE);
    }

    private void updateReadDefaults(int bankPosition) {
        TagProtocol protocol = readerState.getProtocol();
        if (protocol != TagProtocol.ISO_18000_6C) {
            return;
        }

        switch (bankPosition) {
            case 0:  // Reserved
                readAddressView.setText("0");
                readLengthView.setText("2");
                break;
            case 1:  // EPC
                readAddressView.setText("2");
                readLengthView.setText("6");
                break;
            case 2:  // TID
                readAddressView.setText("0");
                readLengthView.setText("6");
                break;
            case 3:  // USER
                readAddressView.setText("0");
                readLengthView.setText("8");
                break;
        }
    }

    private void updateReadBanks(TagProtocol protocol) {
        String[] banks = bankLabels(protocol);
        readBankSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, banks));
        readBankSpinner.setSelection(protocol == TagProtocol.ISO_18000_6C ? 1 : 0);
        updateReadParamsForProtocol(protocol);
    }

    @SingleClick
    private void readTag() {
        if (session == null || !session.getState().isConnected()) {
            requireReaderOnline();
            return;
        }

        try {
            // 解析参数
            TagProtocol protocol = readerState.getProtocol();
            int selectedBankPosition = readBankSpinner.getSelectedItemPosition();
            byte[] password = parsePassword(readPasswordView.getText().toString());
            int bank = ProtocolEncoding.encodeBank(protocol,
                    selectedBankPosition,
                    gbSubBankSpinner.getSelectedItemPosition());
            int address = parseUnsigned(readAddressView, R.string.single_start_address);
            int length = parseReadLength(readLengthView, protocol, selectedBankPosition);
            int blockOrRetry = protocol == TagProtocol.ISO_18000_6C ? 0
                    : parseUnsigned(auxiliaryView, R.string.single_block_or_retry);
            int encodedAddress = ProtocolEncoding.encodeAddress(protocol, address, blockOrRetry);

            // 执行读取
            readButton.setEnabled(false);
            readButton.setText(R.string.single_reading);

            session.readCurrentTag(protocol, length, encodedAddress, bank, password)
                    .whenComplete((result, error) -> {
                        runOnViewThread(() -> {
                        readButton.setEnabled(true);
                        readButton.setText(R.string.single_read_tag);

                        if (error != null) {
                            toast(getString(R.string.single_read_failed,
                                    ThrowableUtils.rootMessage(error)));
                            readResultPanel.setVisibility(View.GONE);
                            readDataGroup.setVisibility(View.GONE);
                        } else {
                            displayReadResult(result, selectedBankPosition, protocol, length);
                            toast(R.string.single_read_success);
                        }
                        });
                    });
        } catch (IllegalArgumentException error) {
            toast(error.getMessage());
        }
    }

    private void displayReadResult(TagReadResult result, int bankPosition, TagProtocol protocol,
            int requestedLength) {
        lastReadBankPosition = bankPosition;
        readResultPanel.setVisibility(View.VISIBLE);
        SingleTagReadFormatter.Presentation presentation = SingleTagReadFormatter.format(
                result, protocol, bankPosition, requestedLength);
        dataLabelView.setText(presentation.bankLabel);
        readDataView.setText(presentation.dataHex);
        readDataGroup.setVisibility(View.VISIBLE);
        fillDataMaskButton.setVisibility(
                presentation.dataHex.isEmpty() ? View.GONE : View.VISIBLE);

        chipGroup.setVisibility(presentation.chipModel.isEmpty() ? View.GONE : View.VISIBLE);
        chipView.setText(presentation.chipModel);

        rssiGroup.setVisibility(presentation.rssi == 0 ? View.GONE : View.VISIBLE);
        rssiView.setText(presentation.rssi == 0 ? "-" : presentation.rssi + " dBm");
        if (presentation.fullEpcHex.isEmpty()) {
            epcView.setVisibility(View.GONE);
        } else {
            epcView.setText("标签epc全值：" + presentation.fullEpcHex);
            epcView.setVisibility(View.VISIBLE);
        }
    }

    private int parseReadLength(EditText view, TagProtocol protocol, int bankPosition) {
        if (view.getText().toString().trim().isEmpty()) {
            int defaultLength = defaultReadLength(protocol, bankPosition);
            view.setText(String.valueOf(defaultLength));
            return defaultLength;
        }
        return parseUnsigned(view, R.string.single_read_length);
    }

    private int defaultReadLength(TagProtocol protocol, int bankPosition) {
        return SingleTagReadFormatter.defaultLength(protocol, bankPosition);
    }

    private void fillMaskFromReadData() {
        fillMask(lastReadBankPosition, readDataView.getText().toString());
    }

    private void fillMask(int bankPosition, String hex) {
        if (maskPanel.getActiveMask() != null) {
            toast(R.string.single_mask_active_warning);
            return;
        }
        if (bankPosition < 0 || hex.isEmpty() || "-".equals(hex)) {
            toast(R.string.single_mask_empty_warning);
            return;
        }
        maskPanel.fill(bankPosition, hex);
    }

    @SingleClick
    private void showWriteDialog(boolean updateEpc) {
        if (!ensureTarget()) { return; }
        TagProtocol protocol = readerState.getProtocol();
        AlertDialog dialog = TagWriteDialog.create(this, protocol, updateEpc, currentTag.id,
                (formDialog, form) -> {
                    try {
                        byte[] password = parsePassword(form.password);
                        byte[] inputData = HexCodec.decode(form.data);
                        int bank = updateEpc ? 1 : ProtocolEncoding.encodeBank(protocol,
                                form.bankPosition, form.gbSubBankPosition);
                        int address = parseUnsigned(form.address, R.string.single_start_address);
                        int blockOrRetry = protocol == TagProtocol.ISO_18000_6C || updateEpc
                                ? 0 : parseUnsigned(form.auxiliary,
                                        R.string.single_block_or_retry);
                        byte[] writeData = inputData;
                        int writeLength;
                        if (updateEpc && protocol == TagProtocol.ISO_18000_6C) {
                            writeData = withEpcPcWord(inputData);
                            address = 1;
                            writeLength = writeData.length / 2;
                        } else {
                            address = ProtocolEncoding.encodeAddress(protocol, address, blockOrRetry);
                            writeLength = ProtocolEncoding.writeLength(protocol, inputData);
                        }
                        int finalAddress = address;
                        int finalWriteLength = writeLength;
                        byte[] finalWriteData = writeData;
                        confirmSingleTagMask(() -> executeStatus(session.writeCurrentTag(
                                        finalWriteLength, finalAddress, bank, password, finalWriteData),
                                updateEpc ? R.string.single_update_epc_operation
                                        : R.string.single_write_operation, formDialog));
                    } catch (IllegalArgumentException error) {
                        toast(error.getMessage());
                    }
                });
        trackDialog(dialog);
        dialog.show();
    }

    @SingleClick
    private void showLockDialog() {
        if (!ensureTarget()) { return; }
        AlertDialog dialog = TagLockDialog.create(this, (formDialog, bank, policy, password) -> {
            try {
                byte[] parsedPassword = parsePassword(password);
                confirmSingleTagMask(() -> executeStatus(session.lockCurrentTag(parsedPassword,
                                bank, policy), R.string.single_lock_operation, formDialog));
            } catch (IllegalArgumentException error) { toast(error.getMessage()); }
        });
        trackDialog(dialog);
        dialog.show();
    }

    @SingleClick
    private void showKillDialog() {
        if (!ensureTarget()) { return; }
        AlertDialog form = TagKillDialog.create(this, (formDialog, accessValue, killValue) -> {
            try {
                byte[] access = parsePassword(accessValue);
                byte[] kill = parsePassword(killValue);
                AlertDialog confirmation = new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.single_kill_confirm_title)
                        .setMessage(R.string.single_kill_confirm_message)
                        .setNegativeButton(R.string.common_cancel, null)
                        .setPositiveButton(R.string.single_kill_confirm, (dialog, which) -> {
                            confirmSingleTagMask(() -> {
                                formDialog.dismiss();
                                executeStatus(session.killCurrentTag(access, kill),
                                        R.string.single_kill_operation, null);
                            });
                        }).create();
                trackDialog(confirmation);
                confirmation.show();
            } catch (IllegalArgumentException error) { toast(error.getMessage()); }
        });
        trackDialog(form);
        form.show();
    }

    private void confirmSingleTagMask(Runnable action) {
        if (session.getSingleTagMask() != null) {
            action.run();
            return;
        }
        new MessageDialog.Builder(requireActivity())
                .setTitle(R.string.single_tag_no_mask_warning_title)
                .setMessage(R.string.single_tag_no_mask_warning)
                .setCancel(R.string.common_cancel)
                .setConfirm(R.string.common_confirm)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(@NonNull BaseDialog dialog) {
                        action.run();
                    }
                })
                .show();
    }

    // ========== Mask management ==========

    private void applyMask() {
        if (!readerState.isConnected()) {
            requireReaderOnline();
            return;
        }
        InventoryMaskFormParser.Result parsed = maskPanel.parse();
        if (!parsed.isSuccess()) {
            maskPanel.setExpanded(true);
            maskPanel.focus(parsed.getError());
            toast(maskErrorMessage(parsed.getError()));
            return;
        }
        InventoryMaskConfig config = parsed.getConfig();
        session.setSingleTagMask(config);
        Log.i(TAG, "single-tag mask set bank=" + config.bank + " offsetBits="
                + config.offsetBits + " lengthBits=" + config.lengthBits);
        toast(R.string.inventory_mask_applied);
    }

    private void clearMask() {
        session.setSingleTagMask(null);
        Log.i(TAG, "single-tag mask cleared");
        toast(R.string.inventory_mask_cleared);
    }

    private void dismissMaskKeyboard() {
        View focused = requireActivity().getCurrentFocus();
        if (focused != null) {
            hideKeyboard(focused);
            focused.clearFocus();
        }
    }

    private void executeStatus(CompletableFuture<Integer> future, @StringRes int operationRes,
            AlertDialog dialog) {
        showLoadingDialog();
        future.whenComplete((status, error) -> {
            runOnViewThread(() -> {
            hideLoadingDialog();
            String operation = getString(operationRes);
            if (error != null) {
                toast(getString(R.string.single_operation_failed, operation,
                        ThrowableUtils.rootMessage(error)));
            } else if (status != 0) {
                toast(getString(R.string.single_operation_error_code, operation, status));
            } else {
                toast(getString(R.string.single_operation_success, operation));
                if (dialog != null) { dialog.dismiss(); }
            }
            });
        });
    }

    private void trackDialog(AlertDialog dialog) {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
        }
        activeDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) { activeDialog = null; }
        });
    }

    private boolean ensureTarget() {
        if (readerState == null || !readerState.isConnected()) {
            requireReaderOnline();
            return false;
        }
        if (currentTag == null) {
            toast(R.string.single_no_tag_hint);
            return false;
        }
        return true;
    }

    private void refreshOperations() {
        boolean supports6cOperations = readerState == null || !readerState.isConnected()
                || readerState.getProtocol() == TagProtocol.ISO_18000_6C;
        updateEpcAction.setVisibility(supports6cOperations ? View.VISIBLE : View.GONE);
        lockAction.setVisibility(supports6cOperations ? View.VISIBLE : View.GONE);
        destroyAction.setVisibility(supports6cOperations ? View.VISIBLE : View.GONE);
        for (View view : new View[]{writeAction, updateEpcAction, lockAction, destroyAction}) {
            view.setEnabled(true);
            view.setAlpha(1f);
        }
    }

    private byte[] parsePassword(String value) {
        byte[] password = HexCodec.decode(value);
        if (password.length != 4) {
            throw new IllegalArgumentException(getString(R.string.single_password_invalid));
        }
        return password;
    }

    private int parseUnsigned(EditText view, @StringRes int nameRes) {
        return parseUnsigned(view.getText().toString(), nameRes);
    }

    private int parseUnsigned(String text, @StringRes int nameRes) {
        try {
            int value = Integer.parseInt(text);
            if (value < 0) { throw new NumberFormatException(); }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(getString(
                    R.string.single_field_must_be_unsigned, getString(nameRes)));
        }
    }

    private byte[] withEpcPcWord(byte[] epc) {
        if ((epc.length & 1) != 0 || epc.length == 0 || epc.length > 62) {
            throw new IllegalArgumentException(getString(R.string.single_epc_length_invalid));
        }
        int pc = (epc.length / 2) << 11;
        byte[] result = new byte[epc.length + 2];
        result[0] = (byte) (pc >> 8);
        result[1] = (byte) pc;
        System.arraycopy(epc, 0, result, 2, epc.length);
        return result;
    }

    private String[] bankLabels(TagProtocol protocol) {
        int labelsRes = switch (protocol) {
            case ISO_18000_6C -> R.array.single_bank_labels_6c;
            case ISO_18000_6B -> R.array.single_bank_labels_6b;
            case GJB_7377_1, GB_T_29768 -> R.array.single_bank_labels_gb;
        };
        return getResources().getStringArray(labelsRes);
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
}
