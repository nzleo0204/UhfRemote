package com.leo.uhf.rfid.ui.tag;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import com.hjq.base.BaseDialog;
import com.leo.uhf.R;
import com.leo.uhf.core.ui.dialog.StyleDialog;

/**
 * 提供单标签销毁操作的表单弹窗。
 */
public final class TagKillDialog {
    public interface Listener {
        void onNext(BaseDialog dialog, String accessPassword, String killPassword);
    }

    private TagKillDialog() {}

    public static BaseDialog create(Fragment fragment, Listener listener) {
        View content = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.tag_kill_dialog,
                        new FrameLayout(fragment.requireContext()), false);
        EditText accessPassword = content.findViewById(R.id.et_tag_kill_access_password);
        EditText killPassword = content.findViewById(R.id.et_tag_kill_password);
        accessPassword.setText("00000000");
        killPassword.setText("00000000");
        StyleDialog.Builder<?> builder = new StyleDialog.Builder<>(fragment.requireContext())
                .setTitle(R.string.single_kill_title).setCustomView(content)
                .setCancel(R.string.common_cancel).setConfirm(R.string.single_next_step);
        builder.setOnClickListenerByView(R.id.tv_ui_confirm,
                (BaseDialog.OnClickListener<View>) (dialog, view) -> listener.onNext(dialog,
                        accessPassword.getText().toString(), killPassword.getText().toString()));
        return builder.create();
    }
}
