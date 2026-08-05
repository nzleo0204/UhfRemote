package com.leo.remote.ui.dialog.common;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hjq.base.BaseDialog;
import com.hjq.custom.widget.view.RegexEditText;
import com.leo.remote.R;
import com.leo.remote.aop.SingleClick;
import com.leo.remote.reader.ReaderConfiguration;

/** 盘点区域的起始地址与长度输入弹窗。 */
public final class InventoryRangeDialog {

    public static final class Builder extends StyleDialog.Builder<Builder> {
        private final RegexEditText addressView;
        private final RegexEditText lengthView;
        private final android.widget.TextView hintView;
        @Nullable
        private OnListener listener;

        public Builder(@NonNull Context context) {
            super(context);
            setCustomView(R.layout.dialog_inventory_range_input);
            addressView = findViewById(R.id.et_inventory_range_addr);
            lengthView = findViewById(R.id.et_inventory_range_len);
            hintView = findViewById(R.id.tv_inventory_range_recommendation);
        }

        public Builder setAddress(int address) {
            addressView.setText(String.valueOf(address));
            return this;
        }

        public Builder setLength(int length) {
            lengthView.setText(String.valueOf(length));
            return this;
        }

        public Builder setHint(@Nullable CharSequence hint) {
            hintView.setText(hint);
            return this;
        }

        public Builder setListener(@Nullable OnListener listener) {
            this.listener = listener;
            return this;
        }

        @SingleClick
        @Override
        public void onClick(@NonNull View view) {
            if (view.getId() == R.id.tv_ui_cancel) {
                performClickDismiss();
                if (listener != null) { listener.onCancel(getDialog()); }
                return;
            }
            if (view.getId() != R.id.tv_ui_confirm) { return; }

            Integer address = parse(addressView);
            Integer length = parse(lengthView);
            if (address == null || address < 0 || length == null || length < 0
                    || length > ReaderConfiguration.MAX_INVENTORY_WORD_LEN) {
                if (listener != null) { listener.onInvalid(getDialog()); }
                return;
            }
            performClickDismiss();
            if (listener != null) { listener.onConfirm(getDialog(), address, length); }
        }

        @Nullable
        private static Integer parse(RegexEditText input) {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (value.isEmpty()) { return null; }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException error) {
                return null;
            }
        }

        public interface OnListener {
            void onConfirm(@NonNull BaseDialog dialog, int address, int length);

            default void onCancel(@NonNull BaseDialog dialog) {}

            default void onInvalid(@NonNull BaseDialog dialog) {}
        }
    }
}
