package com.leo.remote.ui.fragment.home;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.remote.aop.SingleClick;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.ChipModelFormatter;
import com.leo.remote.reader.HexCodec;
import com.leo.remote.reader.InventoryArea;
import com.leo.remote.reader.InventoryMaskConfig;
import com.leo.remote.reader.ProtocolEncoding;
import com.leo.remote.reader.ReaderConfiguration;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.ReaderTag;
import com.leo.remote.reader.TagProtocol;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.dialog.common.MessageDialog;
import java.util.concurrent.CompletableFuture;

/** Single-target RFID operations. */
@SuppressLint("LogNotTimber")
public final class SingleTagFragment extends AppFragment<HomeActivity> implements ReaderObserver {
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
    private View readDataGroup;
    private TextView readDataView;
    private TextView copyDataButton;
    private TextView idLabelView;
    private TextView epcView;
    private TextView dataLabelView;
    private TextView tidView;
    private TextView chipView;
    private TextView rssiView;

    private View writeAction;
    private View updateEpcAction;
    private View lockAction;
    private View destroyAction;
    private View maskPanelContent;
    private Spinner maskBankSpinner;
    private EditText maskOffsetView;
    private EditText maskLengthView;
    private EditText maskHexView;
    private MaterialButton maskToggleButton;
    private TextView maskLengthHintView;
    private TextView maskStatusView;
    private ImageView maskExpandView;
    private ImageView maskLockIcon;
    private ReaderTag currentTag;
    private ReaderState readerState = ReaderState.disconnected();
    private ReaderConfiguration configuration;
    private InventoryMaskConfig activeMask;
    private boolean maskExpanded;

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
        readDataGroup = findViewById(R.id.group_single_read_data);
        readDataView = findViewById(R.id.tv_single_read_data);
        copyDataButton = findViewById(R.id.tv_single_copy_data);
        idLabelView = findViewById(R.id.tv_single_id_label);
        epcView = findViewById(R.id.tv_single_epc);
        dataLabelView = findViewById(R.id.tv_single_data_label);
        tidView = findViewById(R.id.tv_single_tid);
        chipView = findViewById(R.id.tv_single_chip);
        rssiView = findViewById(R.id.tv_single_rssi);

        writeAction = findViewById(R.id.ll_single_write);
        updateEpcAction = findViewById(R.id.ll_single_update_epc);
        lockAction = findViewById(R.id.ll_single_lock);
        destroyAction = findViewById(R.id.ll_single_destroy);
        maskPanelContent = findViewById(R.id.ll_inventory_mask_content);
        maskBankSpinner = findViewById(R.id.sp_inventory_mask_bank);
        maskOffsetView = findViewById(R.id.et_inventory_mask_offset);
        maskLengthView = findViewById(R.id.et_inventory_mask_length);
        maskHexView = findViewById(R.id.et_inventory_mask_hex);
        maskToggleButton = findViewById(R.id.btn_inventory_mask_toggle);
        maskLengthHintView = findViewById(R.id.tv_inventory_mask_length_hint);
        maskStatusView = findViewById(R.id.tv_inventory_mask_status);
        maskExpandView = findViewById(R.id.iv_inventory_mask_expand);
        maskLockIcon = findViewById(R.id.iv_single_mask_lock);

        // 初始化读取参数
        initReadParams();

        maskBankSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                maskOffsetView.setText(String.valueOf(ProtocolEncoding.defaultMaskOffsetBits(
                        readerState.getProtocol(), position)));
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
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

        readButton.setOnClickListener(view -> readTag());
        copyDataButton.setOnClickListener(view -> copyReadData());
        writeAction.setOnClickListener(view -> showWriteDialog(false));
        updateEpcAction.setOnClickListener(view -> showWriteDialog(true));
        lockAction.setOnClickListener(view -> showLockDialog());
        destroyAction.setOnClickListener(view -> showKillDialog());
        findViewById(R.id.row_inventory_mask_toggle).setOnClickListener(view -> {
            dismissMaskKeyboard();
            maskExpanded = !maskExpanded;
            updateMaskControls();
        });
        maskToggleButton.setOnClickListener(view -> toggleMask());
        findViewById(R.id.single_tag_root).setOnClickListener(view -> dismissMaskKeyboard());
        bindTag(null);
        refreshOperations();
        updateMaskControls();
    }

    @Override
    protected void initData() {
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        session.addObserver(this);
    }

    @Override
    public void onDestroy() {
        if (session != null) { session.removeObserver(this); }
        super.onDestroy();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        TagProtocol previousProtocol = readerState.getProtocol();
        readerState = state;
        if (previousProtocol != state.getProtocol()) {
            updateMaskBanks(state.getProtocol());
            updateReadBanks(state.getProtocol());
        }
        if (!state.isConnected()) {
            currentTag = null;
            bindTag(null);
        }
        updateTagDisplay();
        refreshOperations();
        updateMaskControls();
    }

    @Override
    public void onReaderConfigurationChanged(ReaderConfiguration value) {
        configuration = value;
        updateTagDisplay();
    }

    @Override
    public void onCurrentTagChanged(ReaderTag tag) {
        currentTag = tag;
        updateTagDisplay();
        refreshOperations();
    }

    @Override
    public void onSingleTagMaskChanged(@Nullable InventoryMaskConfig config) {
        activeMask = config;
        if (config != null) { bindMaskForm(config); }
        updateMaskControls();
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
            byte[] password = parsePassword(readPasswordView.getText().toString());
            int bank = ProtocolEncoding.encodeBank(protocol,
                    readBankSpinner.getSelectedItemPosition(),
                    gbSubBankSpinner.getSelectedItemPosition());
            int address = parseUnsigned(readAddressView, R.string.single_start_address);
            int length = parseUnsigned(readLengthView, R.string.single_read_length);
            int blockOrRetry = protocol == TagProtocol.ISO_18000_6C ? 0
                    : parseUnsigned(auxiliaryView, R.string.single_block_or_retry);
            int encodedAddress = ProtocolEncoding.encodeAddress(protocol, address, blockOrRetry);

            int selectedBankPosition = readBankSpinner.getSelectedItemPosition();

            // 执行读取
            readButton.setEnabled(false);
            readButton.setText(R.string.single_reading);

            session.readCurrentTag(protocol, length, encodedAddress, bank, password)
                    .whenComplete((data, error) -> requireActivity().runOnUiThread(() -> {
                        readButton.setEnabled(true);
                        readButton.setText(R.string.single_read_tag);

                        if (error != null) {
                            toast(getString(R.string.single_read_failed, rootMessage(error)));
                            readDataGroup.setVisibility(View.GONE);
                        } else {
                            displayReadResult(data, selectedBankPosition, protocol);
                            toast(R.string.single_read_success);
                        }
                    }));
        } catch (IllegalArgumentException error) {
            toast(error.getMessage());
        }
    }

    private void displayReadResult(byte[] data, int bankPosition, TagProtocol protocol) {
        // 显示十六进制数据
        String hexData = HexCodec.encode(data);
        readDataView.setText(hexData);
        readDataGroup.setVisibility(View.VISIBLE);

        // 更新 EPC 显示（如果是读取 EPC 区域）
        if (protocol == TagProtocol.ISO_18000_6C && bankPosition == 1) {
            epcView.setText(hexData);
        }

        // 更新 TID 显示（如果是读取 TID 区域）
        if (protocol == TagProtocol.ISO_18000_6C && bankPosition == 2) {
            tidView.setText(hexData);

            // 显示芯片型号
            String chipModel = ChipModelFormatter.formatFromTid(hexData);
            if (!chipModel.isEmpty()) {
                chipView.setText(chipModel);
                chipView.setVisibility(View.VISIBLE);
            }
        } else {
            chipView.setVisibility(View.GONE);
        }

        // RSSI 暂不显示（读取数据时不返回 RSSI）
        rssiView.setVisibility(View.GONE);
    }

    private void copyReadData() {
        String data = readDataView.getText().toString();
        if (data.isEmpty()) {
            toast(R.string.single_no_data_to_copy);
            return;
        }

        ClipboardManager clipboard = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("RFID Data", data));
        toast(R.string.common_copied);
    }

    @SingleClick
    private void showWriteDialog(boolean updateEpc) {
        if (!ensureTarget()) { return; }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.tag_write_dialog, null, false);
        Spinner bankSpinner = content.findViewById(R.id.sp_tag_bank);
        Spinner gbSubBankSpinner = content.findViewById(R.id.sp_tag_gb_sub_bank);
        View gbSubBankGroup = content.findViewById(R.id.group_tag_gb_sub_bank);
        TextInputLayout auxiliaryInput = content.findViewById(R.id.til_tag_length);
        EditText addressView = content.findViewById(R.id.et_tag_address);
        EditText lengthView = content.findViewById(R.id.et_tag_length);
        EditText dataView = content.findViewById(R.id.et_tag_data);
        EditText passwordView = content.findViewById(R.id.et_tag_password);
        String[] banks = bankLabels(readerState.getProtocol());
        bankSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, banks));
        gbSubBankSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.single_gb_sub_bank_labels)));
        addressView.setText(updateEpc ? "1" : "0");
        TagProtocol protocol = readerState.getProtocol();
        auxiliaryInput.setHint(protocol == TagProtocol.ISO_18000_6B
                ? getString(R.string.single_retry_count_hint)
                : getString(R.string.single_block_length_hint));
        auxiliaryInput.setVisibility(protocol == TagProtocol.ISO_18000_6C || updateEpc
                ? View.GONE : View.VISIBLE);
        lengthView.setText(protocol == TagProtocol.ISO_18000_6B ? "3" : "1");
        passwordView.setText("00000000");
        bankSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                gbSubBankGroup.setVisibility(protocol == TagProtocol.GB_T_29768 && position == 3
                        ? View.VISIBLE : View.GONE);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        if (updateEpc) {
            bankSpinner.setSelection(Math.min(1, banks.length - 1));
            bankSpinner.setEnabled(false);
            dataView.setText(currentTag.id);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(updateEpc ? R.string.single_update_epc_title : R.string.single_write_title)
                .setView(content)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.single_execute, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        byte[] password = parsePassword(passwordView.getText().toString());
                        byte[] inputData = HexCodec.decode(dataView.getText().toString());
                        int bank = updateEpc ? 1 : ProtocolEncoding.encodeBank(protocol,
                                bankSpinner.getSelectedItemPosition(), gbSubBankSpinner.getSelectedItemPosition());
                        int address = parseUnsigned(addressView, R.string.single_start_address);
                        int blockOrRetry = protocol == TagProtocol.ISO_18000_6C || updateEpc
                                ? 0 : parseUnsigned(lengthView, R.string.single_block_or_retry);
                        byte[] writeData = inputData;
                        int writeLength;
                        if (updateEpc && readerState.getProtocol() == TagProtocol.ISO_18000_6C) {
                            writeData = withEpcPcWord(inputData);
                            address = 1;
                            writeLength = writeData.length / 2;
                        } else {
                            address = ProtocolEncoding.encodeAddress(protocol, address, blockOrRetry);
                            writeLength = ProtocolEncoding.writeLength(protocol, inputData);
                        }
                        int finalWriteLength = writeLength;
                        int finalAddress = address;
                        byte[] finalWriteData = writeData;
                        confirmSingleTagMask(() -> executeStatus(session.writeCurrentTag(
                                        finalWriteLength, finalAddress, bank, password, finalWriteData),
                                updateEpc ? R.string.single_update_epc_operation
                                        : R.string.single_write_operation, dialog));
                    } catch (IllegalArgumentException error) {
                        toast(error.getMessage());
                    }
                }));
        dialog.show();
    }

    @SingleClick
    private void showLockDialog() {
        if (!ensureTarget()) { return; }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.tag_lock_dialog, null, false);
        Spinner bank = content.findViewById(R.id.sp_tag_lock_bank);
        Spinner policy = content.findViewById(R.id.sp_tag_lock_policy);
        EditText password = content.findViewById(R.id.et_tag_lock_password);
        bank.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.single_lock_bank_labels)));
        policy.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.single_lock_policy_labels)));
        password.setText("00000000");
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.single_lock_title)
                .setView(content).setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.single_execute, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        byte[] parsedPassword = parsePassword(password.getText().toString());
                        int selectedBank = bank.getSelectedItemPosition();
                        int selectedPolicy = policy.getSelectedItemPosition();
                        confirmSingleTagMask(() -> executeStatus(session.lockCurrentTag(parsedPassword,
                                        selectedBank, selectedPolicy),
                                R.string.single_lock_operation, dialog));
                    } catch (IllegalArgumentException error) { toast(error.getMessage()); }
                }));
        dialog.show();
    }

    @SingleClick
    private void showKillDialog() {
        if (!ensureTarget()) { return; }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.tag_kill_dialog, null, false);
        EditText accessPassword = content.findViewById(R.id.et_tag_kill_access_password);
        EditText killPassword = content.findViewById(R.id.et_tag_kill_password);
        accessPassword.setText("00000000");
        AlertDialog form = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.single_kill_title)
                .setView(content).setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.single_next_step, null).create();
        form.setOnShowListener(ignored -> form.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        byte[] access = parsePassword(accessPassword.getText().toString());
                        byte[] kill = parsePassword(killPassword.getText().toString());
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.single_kill_confirm_title)
                                .setMessage(R.string.single_kill_confirm_message)
                                .setNegativeButton(R.string.common_cancel, null)
                                .setPositiveButton(R.string.single_kill_confirm, (dialog, which) -> {
                                    confirmSingleTagMask(() -> {
                                        form.dismiss();
                                        executeStatus(session.killCurrentTag(access, kill),
                                                R.string.single_kill_operation, null);
                                    });
                                }).show();
                    } catch (IllegalArgumentException error) { toast(error.getMessage()); }
                }));
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
            session.setSingleTagMask(config);
            Log.i(TAG, "single-tag mask set bank=" + config.bank + " offsetBits="
                    + config.offsetBits + " lengthBits=" + config.lengthBits);
            toast(R.string.inventory_mask_applied);
        } catch (IllegalArgumentException error) {
            maskExpanded = true;
            focusInvalidMaskField();
            toast(error.getMessage());
        }
        updateMaskControls();
    }

    private void clearMask() {
        session.setSingleTagMask(null);
        Log.i(TAG, "single-tag mask cleared");
        toast(R.string.inventory_mask_cleared);
        updateMaskControls();
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
        Log.d(TAG, "single-tag mask banks updated protocol=" + protocol);
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
        if (maskPanelContent == null) { return; }
        boolean connected = readerState.isConnected();
        boolean formValid = updateMaskLengthHint();
        maskPanelContent.setVisibility(maskExpanded ? View.VISIBLE : View.GONE);
        maskPanelContent.setAlpha(connected ? 1f : 0.48f);
        maskExpandView.setImageResource(maskExpanded
                ? R.drawable.arrows_top_ic : R.drawable.arrows_bottom_ic);
        setEnabledRecursively(maskPanelContent, connected);
        maskOffsetView.setEnabled(connected
                && readerState.getProtocol() != TagProtocol.ISO_18000_6B);
        boolean masked = activeMask != null;
        maskToggleButton.setText(masked
                ? R.string.inventory_mask_cancel : R.string.inventory_mask_apply);
        maskToggleButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(),
                masked ? R.color.rfid_danger_button_background
                        : R.color.rfid_primary_button_background));
        maskToggleButton.setTextColor(ContextCompat.getColorStateList(requireContext(),
                R.color.rfid_primary_button_text));
        maskToggleButton.setEnabled(connected && (masked || formValid));
        maskStatusView.setVisibility(View.VISIBLE);
        if (masked) {
            Object bank = maskBankSpinner.getSelectedItem();
            String bankLabel = bank == null ? "" : bank.toString();
            maskStatusView.setBackgroundResource(R.drawable.rfid_chip_green_bg);
            maskStatusView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
            maskStatusView.setText(getString(R.string.inventory_mask_active, bankLabel,
                    activeMask.offsetBits, activeMask.lengthBits));
        } else {
            maskStatusView.setBackgroundResource(R.drawable.rfid_chip_gray_bg);
            maskStatusView.setTextColor(ContextCompat.getColor(
                    requireContext(), R.color.rfid_text_secondary));
            maskStatusView.setText(R.string.inventory_mask_inactive);
        }
        updateMaskLockIcon(masked);
    }

    private void updateMaskLockIcon(boolean masked) {
        maskLockIcon.setImageResource(masked
                ? R.drawable.rfid_lock_closed_ic : R.drawable.rfid_lock_open_ic);
        maskLockIcon.setImageTintList(ContextCompat.getColorStateList(requireContext(),
                masked ? R.color.rfid_warning : R.color.rfid_text_muted));
        maskLockIcon.setContentDescription(getString(masked
                ? R.string.inventory_mask_lock_closed : R.string.inventory_mask_lock_open));
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

    private void setEnabledRecursively(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof ViewGroup group)) { return; }
        for (int i = 0; i < group.getChildCount(); i++) {
            setEnabledRecursively(group.getChildAt(i), enabled);
        }
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
        future.whenComplete((status, error) -> requireActivity().runOnUiThread(() -> {
            hideLoadingDialog();
            String operation = getString(operationRes);
            if (error != null) {
                toast(getString(R.string.single_operation_failed, operation, rootMessage(error)));
            } else if (status != 0) {
                toast(getString(R.string.single_operation_error_code, operation, status));
            } else {
                toast(getString(R.string.single_operation_success, operation));
                if (dialog != null) { dialog.dismiss(); }
            }
        }));
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

    private void updateTagDisplay() {
        InventoryArea area = InventoryArea.of(readerState.getProtocol(),
                configuration == null ? 0 : configuration.inventoryArea);
        String[] headers = area.getColumnHeader().split("/", 2);
        idLabelView.setText(headers[0]);
        dataLabelView.setText(headers.length == 2 ? headers[1]
                : getString(R.string.single_data_label));
        bindTag(currentTag);
    }

    private void bindTag(ReaderTag tag) {
        if (tag == null) {
            targetHintView.setText(R.string.single_no_target_hint);
            targetHintView.setTextColor(ContextCompat.getColor(
                    requireContext(), R.color.rfid_text_muted));
            epcView.setText(R.string.single_preview_epc);
            tidView.setText(R.string.single_preview_tid);
            chipView.setText(R.string.single_preview_chip);
            rssiView.setText(R.string.single_preview_rssi);
            return;
        }
        targetHintView.setText(getString(R.string.single_target_locked, tag.id));
        targetHintView.setTextColor(ContextCompat.getColor(
                requireContext(), R.color.rfid_primary_soft));
        epcView.setText(tag.id.isEmpty() ? "-" : tag.id);
        tidView.setText(tag.data.isEmpty() ? "-" : tag.data);
        chipView.setText(chipLabel(tag.data));
        rssiView.setText(tag.rssi + " dBm");
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
        try {
            int value = Integer.parseInt(view.getText().toString());
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

    private static String chipLabel(String tid) {
        return tid != null && (tid.startsWith("E28011") || tid.startsWith("E28012"))
                ? "Impinj Monza" : "-";
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error.getCause();
        return cause == null ? error.getMessage() : cause.getMessage();
    }
}
