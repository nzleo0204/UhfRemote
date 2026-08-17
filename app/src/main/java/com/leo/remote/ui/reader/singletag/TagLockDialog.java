package com.leo.remote.ui.reader.singletag;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.leo.remote.R;

public final class TagLockDialog {
    public interface Listener {
        void onSubmit(AlertDialog dialog, int bank, int policy, String password);
    }

    private TagLockDialog() {}

    public static AlertDialog create(Fragment fragment, Listener listener) {
        View content = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.tag_lock_dialog, null, false);
        Spinner bank = content.findViewById(R.id.sp_tag_lock_bank);
        Spinner policy = content.findViewById(R.id.sp_tag_lock_policy);
        EditText password = content.findViewById(R.id.et_tag_lock_password);
        bank.setAdapter(new ArrayAdapter<>(fragment.requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                fragment.getResources().getStringArray(R.array.single_lock_bank_labels)));
        policy.setAdapter(new ArrayAdapter<>(fragment.requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                fragment.getResources().getStringArray(R.array.single_lock_policy_labels)));
        password.setText("00000000");
        AlertDialog dialog = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.single_lock_title).setView(content)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.single_execute, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> listener.onSubmit(dialog,
                        bank.getSelectedItemPosition(), policy.getSelectedItemPosition(),
                        password.getText().toString())));
        return dialog;
    }
}
