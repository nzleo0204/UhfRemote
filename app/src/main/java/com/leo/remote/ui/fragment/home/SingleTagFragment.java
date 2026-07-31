package com.leo.remote.ui.fragment.home;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.leo.remote.R;
import com.leo.remote.aop.SingleClick;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.HexCodec;
import com.leo.remote.reader.ProtocolEncoding;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.ReaderTag;
import com.leo.remote.reader.TagProtocol;
import com.leo.remote.ui.activity.HomeActivity;
import java.util.concurrent.CompletableFuture;

/** Single-target RFID operations. */
public final class SingleTagFragment extends AppFragment<HomeActivity> implements ReaderObserver {
    private ReaderSessionManager session;
    private TextView readButton;
    private TextView epcView;
    private TextView tidView;
    private TextView chipView;
    private TextView rssiView;
    private TextView targetHintView;
    private View writeAction;
    private View updateEpcAction;
    private View lockAction;
    private View destroyAction;
    private ReaderTag currentTag;
    private ReaderState readerState;

    public static SingleTagFragment newInstance() { return new SingleTagFragment(); }

    @Override
    protected int getLayoutId() { return R.layout.single_tag_fragment; }

    @Override
    protected void initView() {
        readButton = findViewById(R.id.tv_single_read);
        epcView = findViewById(R.id.tv_single_epc);
        tidView = findViewById(R.id.tv_single_tid);
        chipView = findViewById(R.id.tv_single_chip);
        rssiView = findViewById(R.id.tv_single_rssi);
        targetHintView = findViewById(R.id.tv_single_target_hint);
        writeAction = findViewById(R.id.ll_single_write);
        updateEpcAction = findViewById(R.id.ll_single_update_epc);
        lockAction = findViewById(R.id.ll_single_lock);
        destroyAction = findViewById(R.id.ll_single_destroy);

        readButton.setOnClickListener(view -> readTag());
        writeAction.setOnClickListener(view -> showWriteDialog(false));
        updateEpcAction.setOnClickListener(view -> showWriteDialog(true));
        lockAction.setOnClickListener(view -> showLockDialog());
        destroyAction.setOnClickListener(view -> showKillDialog());
        bindTag(null);
        refreshOperations();
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
        readerState = state;
        if (!state.isConnected()) {
            currentTag = null;
            bindTag(null);
        }
        refreshOperations();
    }

    @Override
    public void onCurrentTagChanged(ReaderTag tag) {
        currentTag = tag;
        bindTag(tag);
        refreshOperations();
    }

    @SingleClick
    private void readTag() {
        if (session == null || !session.getState().isConnected()) {
            requireReaderOnline();
            return;
        }
        readButton.setEnabled(false);
        readButton.setText(R.string.single_reading);
        session.readSingleTag().whenComplete((tag, error) -> requireActivity().runOnUiThread(() -> {
            readButton.setEnabled(true);
            readButton.setText(R.string.single_read_tag);
            if (error != null) { toast(getString(R.string.single_read_failed, rootMessage(error))); }
        }));
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
                        executeStatus(session.writeCurrentTag(writeLength, address, bank, password, writeData),
                                updateEpc ? R.string.single_update_epc_operation
                                        : R.string.single_write_operation, dialog);
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
                        executeStatus(session.lockCurrentTag(parsePassword(password.getText().toString()),
                                bank.getSelectedItemPosition(), policy.getSelectedItemPosition()),
                                R.string.single_lock_operation, dialog);
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
                                    form.dismiss();
                                    executeStatus(session.killCurrentTag(access, kill),
                                            R.string.single_kill_operation, null);
                                }).show();
                    } catch (IllegalArgumentException error) { toast(error.getMessage()); }
                }));
        form.show();
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
