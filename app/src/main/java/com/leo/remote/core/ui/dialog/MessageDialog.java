package com.leo.remote.core.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.remote.core.aop.SingleClick;

/**



 *    消息对话框
 */
public final class MessageDialog {

    public static class Builder
            extends StyleDialog.Builder<Builder> {

        @Nullable
        private OnListener listener;

        @NonNull
        private final TextView messageView;

        public Builder(@NonNull Context context) {
            super(context);
            setCustomView(R.layout.message_dialog);
            messageView = findViewById(R.id.tv_message_message);

            // 让 TextView 支持滚动
            messageView.setMovementMethod(new ScrollingMovementMethod());
        }

        public Builder setMessage(@StringRes int id) {
            return setMessage(getString(id));
        }
        public Builder setMessage(CharSequence text) {
            messageView.setText(text);
            return this;
        }

        public Builder setListener(@Nullable OnListener listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public BaseDialog create() {
            // 如果内容为空就抛出异常
            if (TextUtils.isEmpty(messageView.getText().toString())) {
                throw new IllegalArgumentException("Dialog message not null");
            }
            return super.create();
        }

        @SingleClick
        @Override
        public void onClick(@NonNull View view) {
            int viewId = view.getId();
            if (viewId == R.id.tv_ui_confirm) {
                performClickDismiss();
                if (listener == null) {
                    return;
                }
                listener.onConfirm(getDialog());
            } else if (viewId == R.id.tv_ui_cancel) {
                performClickDismiss();
                if (listener == null) {
                    return;
                }
                listener.onCancel(getDialog());
            }
        }

        @NonNull
        public TextView getMessageView() {
            return messageView;
        }
    }

    public interface OnListener {

        /**
         * 点击确定时回调
         */
        void onConfirm(@NonNull BaseDialog dialog);

        /**
         * 点击取消时回调
         */
        default void onCancel(@NonNull BaseDialog dialog) {
            // default implementation ignored
        }
    }
}
