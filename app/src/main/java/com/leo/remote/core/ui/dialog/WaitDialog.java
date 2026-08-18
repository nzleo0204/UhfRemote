package com.leo.remote.core.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2018/12/2
 *    desc   : 等待加载对话框
 */
public final class WaitDialog {

    public static final class Builder
            extends BaseDialog.Builder<Builder> {

        @NonNull
        private final TextView messageView;

        public Builder(@NonNull Context context) {
            super(context);
            setContentView(R.layout.wait_dialog);
            setAnimStyle(BaseDialog.ANIM_TOAST);
            setBackgroundDimEnabled(false);
            setCancelable(false);

            messageView = findViewById(R.id.tv_wait_message);
        }

        public Builder setMessage(@StringRes int id) {
            return setMessage(getString(id));
        }

        public Builder setMessage(@Nullable CharSequence text) {
            messageView.setText(text);
            messageView.setVisibility(text == null ? View.GONE : View.VISIBLE);
            return this;
        }
    }
}
