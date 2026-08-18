package com.leo.remote.core.ui.dialog;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2019/09/21
 *    desc   : 项目通用样式 Dialog 布局封装
 */
public final class StyleDialog {

    @SuppressWarnings("unchecked")
    public static class Builder<B extends StyleDialog.Builder<?>>
            extends BaseDialog.Builder<B> {

        private boolean clickDismiss = true;

        @NonNull
        private final ViewGroup containerLayout;
        @NonNull
        private final TextView titleView;

        @NonNull
        private final TextView cancelView;
        @NonNull
        private final View lineView;
        @NonNull
        private final TextView confirmView;

        public Builder(@NonNull Context context) {
            super(context);

            setContentView(R.layout.ui_dialog);
            setAnimStyle(BaseDialog.ANIM_IOS);
            setGravity(Gravity.CENTER);

            containerLayout = findViewById(R.id.ll_ui_container);
            titleView = findViewById(R.id.tv_ui_title);
            cancelView  = findViewById(R.id.tv_ui_cancel);
            lineView = findViewById(R.id.v_ui_line);
            confirmView  = findViewById(R.id.tv_ui_confirm);
            setOnClickListener(cancelView, confirmView);
        }

        public B setCustomView(@LayoutRes int id) {
            return setCustomView(LayoutInflater.from(getContext()).inflate(id, containerLayout, false));
        }

        public B setCustomView(View view) {
            containerLayout.addView(view, 1);
            return (B) this;
        }

        public B setTitle(@StringRes int id) {
            return setTitle(getString(id));
        }
        public B setTitle(CharSequence text) {
            titleView.setText(text);
            return (B) this;
        }

        public B setCancel(@StringRes int id) {
            return setCancel(getString(id));
        }
        public B setCancel(CharSequence text) {
            cancelView.setText(text);
            lineView.setVisibility((text == null || "".equals(text.toString())) ? View.GONE : View.VISIBLE);
            return (B) this;
        }

        public B setConfirm(@StringRes int id) {
            return setConfirm(getString(id));
        }
        public B setConfirm(CharSequence text) {
            confirmView.setText(text);
            return (B) this;
        }

        public B setClickDismiss(boolean enable) {
            clickDismiss = enable;
            return (B) this;
        }

        public void performClickDismiss() {
            if (!clickDismiss) {
              return;
            }
            dismiss();
        }
    }
}
