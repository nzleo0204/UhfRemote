package com.leo.uhf.core.util;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import com.leo.uhf.R;
import com.hjq.smallest.width.SmallestWidthAdaptation;
import com.scwang.smart.refresh.layout.api.RefreshFooter;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.constant.SpinnerStyle;
import com.scwang.smart.refresh.layout.simple.SimpleComponent;

/** 球脉冲底部加载组件 */
public final class SmartBallPulseFooter extends SimpleComponent implements RefreshFooter {

    private final TimeInterpolator interpolator = new AccelerateDecelerateInterpolator();

    private boolean noMoreData;

    private boolean manualNormalColor;
    private boolean manualAnimationColor;

    private final Paint paint;

    private int normalColor = Color.parseColor("#EEEEEE");
    private int[] animatingColor = {
            Color.parseColor("#30B399"),
            Color.parseColor("#FF4600"),
            Color.parseColor("#142DCC")};

    private final float circleSpacing;

    private long startTime = 0;
    private boolean started = false;

    private final float textWidth;

    public SmartBallPulseFooter(@NonNull Context context) {
        this(context, null);
    }

    public SmartBallPulseFooter(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, 0);

        setMinimumHeight((int) SmallestWidthAdaptation.dp2px(context, 60));

        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        mSpinnerStyle = SpinnerStyle.Translate;

        circleSpacing = SmallestWidthAdaptation.dp2px(context, 2);
        paint.setTextSize(SmallestWidthAdaptation.sp2px(context, 14));
        textWidth = paint.measureText(getContext().getString(R.string.common_no_more_data));
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final int width = getWidth();
        final int height = getHeight();
        if (noMoreData) {
            paint.setColor(Color.parseColor("#898989"));
            canvas.drawText(getContext().getString(R.string.common_no_more_data),(width - textWidth) / 2,(height - paint.getTextSize()) / 2, paint);
        } else {
            float radius = (Math.min(width, height) - circleSpacing * 2) / 7;
            float x = width / 2f - (radius * 2 + circleSpacing);
            float y = height / 2f;
            final long now = System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                long time = now - startTime - 120 * (i + 1);
                float percent = time > 0 ? ((time % 750) / 750f) : 0;
                percent = interpolator.getInterpolation(percent);
                canvas.save();
                float translateX = x + (radius * 2) * i + circleSpacing * i;

                if (percent < 0.5) {
                    float scale = 1 - percent * 2 * 0.7f;
                    float translateY = y - scale * 10;
                    canvas.translate(translateX, translateY);
                } else {
                    float scale = percent * 2 * 0.7f - 0.4f;
                    float translateY = y + scale * 10;
                    canvas.translate(translateX, translateY);
                }

                paint.setColor(animatingColor[i % animatingColor.length]);
                canvas.drawCircle(0, 0, radius / 3, paint);
                canvas.restore();
            }
        }

        if (started) {
            postInvalidate();
        }
    }

    @Override
    public void onStartAnimator(@NonNull RefreshLayout layout, int height, int maxDragHeight) {
        if (started) {
            return;
        }

        invalidate();
        started = true;
        startTime = System.currentTimeMillis();
    }

    @Override
    public int onFinish(@NonNull RefreshLayout layout, boolean success) {
        started = false;
        startTime = 0;
        paint.setColor(normalColor);
        return 0;
    }

    @Override
    public void setPrimaryColors(@ColorInt int... colors) {
        if (!manualAnimationColor && colors.length > 1) {
            setAnimatingColor(colors[0]);
            manualAnimationColor = false;
        }
        if (!manualNormalColor) {
            if (colors.length > 1) {
                setNormalColor(colors[1]);
            } else if (colors.length > 0) {
                setNormalColor(ColorUtils.compositeColors(Color.parseColor("#99FFFFFF"), colors[0]));
            }
            manualNormalColor = false;
        }
    }

    @Override
    public boolean setNoMoreData(boolean noMoreData) {
        this.noMoreData = noMoreData;
        return true;
    }

    public SmartBallPulseFooter setSpinnerStyle(SpinnerStyle style) {
        mSpinnerStyle = style;
        return this;
    }

    public SmartBallPulseFooter setNormalColor(@ColorInt int color) {
        normalColor = color;
        manualNormalColor = true;
        if (!started) {
            paint.setColor(color);
        }
        return this;
    }

    public SmartBallPulseFooter setAnimatingColor(@ColorInt int color) {
        animatingColor = new int[]{color};
        manualAnimationColor = true;
        if (started) {
            paint.setColor(color);
        }
        return this;
    }
}
