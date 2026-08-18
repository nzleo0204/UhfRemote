package com.leo.remote.core.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import com.airbnb.lottie.LottieAnimationView;
import com.leo.remote.R;

/**



 *    状态布局（网络错误，异常错误，空数据）
 */
public final class StatusLayout extends FrameLayout {

    /** 主布局 */
    private ViewGroup mainLayout;
    /** 提示图标 */
    private LottieAnimationView lottieView;
    /** 提示文本 */
    private TextView textView;
    /** 重试按钮 */
    private TextView retryView;
    /** 重试监听 */
    private OnRetryListener listener;

    public StatusLayout(@NonNull Context context) {
        this(context, null);
    }

    public StatusLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatusLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 显示
     */
    public void show() {

        if (mainLayout == null) {
            //初始化布局
            initLayout();
        }

        if (isShow()) {
            return;
        }
        retryView.setVisibility(listener == null ? View.INVISIBLE : View.VISIBLE);
        // 显示布局
        mainLayout.setVisibility(VISIBLE);
    }

    /**
     * 隐藏
     */
    public void hide() {
        if (mainLayout == null || !isShow()) {
            return;
        }
        //隐藏布局
        mainLayout.setVisibility(INVISIBLE);
    }

    /**
     * 是否显示了
     */
    public boolean isShow() {
        return mainLayout != null && mainLayout.getVisibility() == VISIBLE;
    }

    /**
     * 设置提示图标，请在show方法之后调用
     */
    public void setIcon(@DrawableRes int id) {
        setIcon(ContextCompat.getDrawable(getContext(), id));
    }

    public void setIcon(Drawable drawable) {
        if (lottieView == null) {
            return;
        }
        if (lottieView.isAnimating()) {
            lottieView.cancelAnimation();
        }
        lottieView.setImageDrawable(drawable);
    }

    /**
     * 设置提示动画
     */
    public void setAnimResource(@RawRes int id) {
        if (lottieView == null) {
            return;
        }

        lottieView.setAnimation(id);
        if (!lottieView.isAnimating()) {
            lottieView.playAnimation();
        }
    }

    /**
     * 设置提示文本，请在show方法之后调用
     */
    public void setHint(@StringRes int id) {
        setHint(getResources().getString(id));
    }

    public void setHint(CharSequence text) {
        if (textView == null) {
            return;
        }
        if (text == null) {
            text = "";
        }
        textView.setText(text);
    }

    /**
     * 初始化提示的布局
     */
    private void initLayout() {

        mainLayout = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.widget_status_layout, this, false);

        lottieView = mainLayout.findViewById(R.id.iv_status_icon);
        textView = mainLayout.findViewById(R.id.tv_status_text);
        retryView = mainLayout.findViewById(R.id.btn_status_retry);

        if (mainLayout.getBackground() == null) {
            // 默认使用 windowBackground 作为背景
            TypedArray typedArray = getContext().obtainStyledAttributes(new int[]{android.R.attr.windowBackground});
            mainLayout.setBackground(typedArray.getDrawable(0));
            mainLayout.setClickable(true);
            typedArray.recycle();
        }

        retryView.setOnClickListener(clickWrapper);

        addView(mainLayout);
    }

    /**
     * 设置重试监听器
     */
    public void setOnRetryListener(OnRetryListener listener) {
        this.listener = listener;
        if (isShow()) {
            retryView.setVisibility(listener == null ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /**
     * 点击事件包装类
     */
    private final OnClickListener clickWrapper = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (listener == null) {
                return;
            }
            listener.onRetry(StatusLayout.this);
        }
    };

    /**
     * 重试监听器
     */
    public interface OnRetryListener {

        /**
         * 点击了重试
         */
        void onRetry(@NonNull StatusLayout layout);
    }
}
