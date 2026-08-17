package com.leo.remote.ui.reader.singletag;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.leo.remote.R;

public final class TagKillDialog {
    public interface Listener {
        void onNext(AlertDialog dialog, String accessPassword, String killPassword);
    }

    private TagKillDialog() {}

    public static AlertDialog create(Fragment fragment, Listener listener) {
        View content = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.tag_kill_dialog, null, false);
        EditText accessPassword = content.findViewById(R.id.et_tag_kill_access_password);
        EditText killPassword = content.findViewById(R.id.et_tag_kill_password);
        accessPassword.setText("00000000");
        killPassword.setText("00000000");
        AlertDialog dialog = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.single_kill_title).setView(content)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.single_next_step, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> listener.onNext(dialog,
                        accessPassword.getText().toString(), killPassword.getText().toString())));
        return dialog;
    }
}
