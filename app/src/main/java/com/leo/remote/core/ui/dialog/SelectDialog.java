package com.leo.remote.core.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import java.util.Arrays;
import java.util.List;

/** Framework-style single-choice dialog with immediate selection. */
public final class SelectDialog {
    public static final class Builder extends StyleDialog.Builder<Builder> {
        private final LinearLayout listView;
        private List<String> items = List.of();
        private int selectedPosition = -1;
        @Nullable
        private OnListener listener;

        public Builder(@NonNull Context context) {
            super(context);
            listView = new LinearLayout(context);
            listView.setOrientation(LinearLayout.VERTICAL);
            int horizontal = dp(20);
            int vertical = dp(8);
            listView.setPadding(horizontal, vertical, horizontal, vertical);
            setCustomView(listView);
            setCancel(R.string.common_cancel);
            setConfirm((CharSequence) null);
        }

        public Builder setList(String... values) {
            return setList(Arrays.asList(values));
        }

        public Builder setList(List<String> values) {
            items = List.copyOf(values);
            renderItems();
            return this;
        }

        public Builder setSelect(int position) {
            selectedPosition = position;
            renderItems();
            return this;
        }

        public Builder setListener(@Nullable OnListener selectListener) {
            listener = selectListener;
            return this;
        }

        private void renderItems() {
            listView.removeAllViews();
            for (int position = 0; position < items.size(); position++) {
                RadioButton itemView = new RadioButton(getContext());
                itemView.setText(items.get(position));
                itemView.setTextColor(ContextCompat.getColor(getContext(), R.color.rfid_text));
                itemView.setTextSize(15);
                itemView.setGravity(android.view.Gravity.CENTER_VERTICAL);
                itemView.setMinHeight(dp(48));
                itemView.setChecked(position == selectedPosition);
                int selected = position;
                itemView.setOnClickListener(view -> select(selected));
                listView.addView(itemView, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }

        private void select(int position) {
            if (position < 0 || position >= items.size()) {
                return;
            }
            performClickDismiss();
            if (listener != null) {
                listener.onSelected(getDialog(), position, items.get(position));
            }
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    public interface OnListener {
        void onSelected(@NonNull BaseDialog dialog, int position, @NonNull String value);
    }

    private SelectDialog() {}
}
