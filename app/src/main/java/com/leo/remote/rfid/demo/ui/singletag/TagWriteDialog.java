package com.leo.remote.rfid.demo.ui.singletag;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import androidx.fragment.app.Fragment;
import com.hjq.base.BaseDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.leo.remote.R;
import com.leo.remote.core.ui.dialog.StyleDialog;
import com.leo.remote.rfid.sdk.model.TagProtocol;

public final class TagWriteDialog {
    public interface Listener { void onSubmit(BaseDialog dialog, Form form); }

    public static final class Form {
        public final int bankPosition;
        public final int gbSubBankPosition;
        public final String address;
        public final String auxiliary;
        public final String data;
        public final String password;

        private Form(int bankPosition, int gbSubBankPosition, String address,
                String auxiliary, String data, String password) {
            this.bankPosition = bankPosition;
            this.gbSubBankPosition = gbSubBankPosition;
            this.address = address;
            this.auxiliary = auxiliary;
            this.data = data;
            this.password = password;
        }
    }

    private TagWriteDialog() {}

    public static BaseDialog create(Fragment fragment, TagProtocol protocol, boolean updateEpc,
            String currentEpc, Listener listener) {
        View content = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.tag_write_dialog,
                        new FrameLayout(fragment.requireContext()), false);
        Spinner bank = content.findViewById(R.id.sp_tag_bank);
        Spinner gbSubBank = content.findViewById(R.id.sp_tag_gb_sub_bank);
        View gbSubBankGroup = content.findViewById(R.id.group_tag_gb_sub_bank);
        TextInputLayout auxiliaryInput = content.findViewById(R.id.til_tag_length);
        EditText address = content.findViewById(R.id.et_tag_address);
        EditText auxiliary = content.findViewById(R.id.et_tag_length);
        EditText data = content.findViewById(R.id.et_tag_data);
        EditText password = content.findViewById(R.id.et_tag_password);
        String[] banks = bankLabels(fragment, protocol);
        bank.setAdapter(new ArrayAdapter<>(fragment.requireContext(),
                android.R.layout.simple_spinner_dropdown_item, banks));
        gbSubBank.setAdapter(new ArrayAdapter<>(fragment.requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                fragment.getResources().getStringArray(R.array.single_gb_sub_bank_labels)));
        address.setText(updateEpc ? "1" : "0");
        auxiliaryInput.setHint(protocol == TagProtocol.ISO_18000_6B
                ? fragment.getString(R.string.single_retry_count_hint)
                : fragment.getString(R.string.single_block_length_hint));
        auxiliaryInput.setVisibility(protocol == TagProtocol.ISO_18000_6C || updateEpc
                ? View.GONE : View.VISIBLE);
        auxiliary.setText(protocol == TagProtocol.ISO_18000_6B ? "3" : "1");
        password.setText("00000000");
        bank.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                gbSubBankGroup.setVisibility(protocol == TagProtocol.GB_T_29768 && position == 3
                        ? View.VISIBLE : View.GONE);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        if (updateEpc) {
            bank.setSelection(Math.min(1, banks.length - 1));
            bank.setEnabled(false);
            data.setText(currentEpc);
        }
        StyleDialog.Builder<?> builder = new StyleDialog.Builder<>(fragment.requireContext())
                .setTitle(updateEpc ? R.string.single_update_epc_title : R.string.single_write_title)
                .setCustomView(content)
                .setCancel(R.string.common_cancel)
                .setConfirm(R.string.single_execute);
        builder.setOnClickListenerByView(R.id.tv_ui_confirm,
                (BaseDialog.OnClickListener<View>) (dialog, view) -> listener.onSubmit(dialog, new Form(
                        bank.getSelectedItemPosition(), gbSubBank.getSelectedItemPosition(),
                        address.getText().toString(), auxiliary.getText().toString(),
                        data.getText().toString(), password.getText().toString())));
        return builder.create();
    }

    private static String[] bankLabels(Fragment fragment, TagProtocol protocol) {
        int labels = switch (protocol) {
            case ISO_18000_6C -> R.array.single_bank_labels_6c;
            case ISO_18000_6B -> R.array.single_bank_labels_6b;
            case GJB_7377_1, GB_T_29768 -> R.array.single_bank_labels_gb;
        };
        return fragment.getResources().getStringArray(labels);
    }
}
