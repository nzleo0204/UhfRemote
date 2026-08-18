package com.leo.remote.rfid.demo.ui.singletag;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import androidx.fragment.app.Fragment;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.remote.core.ui.dialog.StyleDialog;

/**
 * 提供单标签锁定操作的表单弹窗。
 */
public final class TagLockDialog {
    public interface Listener {
        void onSubmit(BaseDialog dialog, int bank, int policy, String password);
    }

    private TagLockDialog() {}

    public static BaseDialog create(Fragment fragment, Listener listener) {
        View content = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.tag_lock_dialog,
                        new FrameLayout(fragment.requireContext()), false);
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
        StyleDialog.Builder<?> builder = new StyleDialog.Builder<>(fragment.requireContext())
                .setTitle(R.string.single_lock_title).setCustomView(content)
                .setCancel(R.string.common_cancel).setConfirm(R.string.single_execute);
        builder.setOnClickListenerByView(R.id.tv_ui_confirm,
                (BaseDialog.OnClickListener<View>) (dialog, view) -> listener.onSubmit(dialog,
                        bank.getSelectedItemPosition(), policy.getSelectedItemPosition(),
                        password.getText().toString()));
        return builder.create();
    }
}
