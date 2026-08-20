package com.leo.uhf.rfid.demo.config;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hjq.base.BaseDialog;
import com.hjq.custom.widget.view.RegexEditText;
import com.leo.uhf.R;
import com.leo.uhf.core.aop.SingleClick;
import com.leo.uhf.rfid.api.model.ReaderConfiguration;
import com.leo.uhf.core.ui.dialog.StyleDialog;

/** 盘点区域的起始地址与长度输入弹窗。 */
public final class InventoryRangeDialog {

    public static final class Builder extends StyleDialog.Builder<Builder> {
        private final RegexEditText addressView;
        private final RegexEditText lengthView;
        @Nullable
        private OnListener listener;
        private int defaultAddress = 0;
        private int defaultLength = 0;

        public Builder(@NonNull Context context) {
            super(context);
            setCustomView(R.layout.dialog_inventory_range_input);
            // 增大弹窗宽度，避免误触
            setWidth((int) (context.getResources().getDisplayMetrics().widthPixels * 0.8));
            addressView = findViewById(R.id.et_inventory_range_addr);
            lengthView = findViewById(R.id.et_inventory_range_len);
        }

        public Builder setAddress(int address) {
            this.defaultAddress = address;
            // 使用 post 延迟设置，确保视图已创建
            if (addressView != null) {
                addressView.post(() -> addressView.setText(String.valueOf(address)));
            }
            return this;
        }

        public Builder setLength(int length) {
            this.defaultLength = length;
            // 使用 post 延迟设置，确保视图已创建
            if (lengthView != null) {
                lengthView.post(() -> lengthView.setText(String.valueOf(length)));
            }
            return this;
        }

        public Builder setListener(@Nullable OnListener listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public void onClick(@NonNull View view) {
            if (view.getId() == R.id.tv_ui_cancel) {
                performClickDismiss();
                if (listener != null) { listener.onCancel(getDialog()); }
                return;
            }
            if (view.getId() != R.id.tv_ui_confirm) { return; }

            handleConfirm();
        }

        @SingleClick
        private void handleConfirm() {

            // 解析用户输入，如果输入框为空则使用默认值
            Integer address = parse(addressView);
            Integer length = parse(lengthView);

            // 如果用户没有输入，使用默认值
            if (address == null) { address = defaultAddress; }
            if (length == null) { length = defaultLength; }

            if (address < 0 || length < 0
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
