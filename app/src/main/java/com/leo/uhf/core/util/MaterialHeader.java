package com.leo.uhf.core.util;

import static android.view.View.MeasureSpec.getSize;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.ImageView;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.leo.uhf.R;
import com.hjq.smallest.width.SmallestWidthAdaptation;
import com.scwang.smart.refresh.header.material.CircleImageView;
import com.scwang.smart.refresh.header.material.MaterialProgressDrawable;
import com.scwang.smart.refresh.layout.api.RefreshHeader;
import com.scwang.smart.refresh.layout.api.RefreshKernel;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.constant.RefreshState;
import com.scwang.smart.refresh.layout.constant.SpinnerStyle;
import com.scwang.smart.refresh.layout.simple.SimpleComponent;

/** Material 风格的刷新球，参考 {@link com.scwang.smart.refresh.header.MaterialHeader} */
public final class MaterialHeader extends SimpleComponent implements RefreshHeader {

    /** 刷新球大样式 */
    public static final int BALL_STYLE_LARGE = 0;
    /** 刷新球默认样式 */
    public static final int BALL_STYLE_DEFAULT = 1;

    private static final int CIRCLE_BG_LIGHT = Color.parseColor("#FAFAFA");
    private static final float MAX_PROGRESS_ANGLE = 0.8f;

    private boolean finished;
    private int circleDiameter;
    private final ImageView circleView;
    private final MaterialProgressDrawable progressDrawable;

    private int waveHeight;
    private int headHeight;
    private final Path bezierPath;
    private final Paint bezierPaint;
    private RefreshState refreshState;
    private boolean showBezierWave = false;
    private boolean scrollableWhenRefreshing = true;

    public MaterialHeader(@NonNull Context context) {
        this(context, null);
    }

    public MaterialHeader(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, 0);

        mSpinnerStyle = SpinnerStyle.MatchLayout;
        setMinimumHeight((int) SmallestWidthAdaptation.dp2px(context, 100));

        progressDrawable = new MaterialProgressDrawable(this);
        progressDrawable.setColorSchemeColors(
                Color.parseColor("#0099CC"),
                Color.parseColor("#FF4444"),
                Color.parseColor("#669900"),
                Color.parseColor("#AA66CC"),
                Color.parseColor("#FF8800"));
        circleView = new CircleImageView(context, CIRCLE_BG_LIGHT);
        circleView.setImageDrawable(progressDrawable);
        circleView.setAlpha(0f);
        addView(circleView);

        circleDiameter = (int) SmallestWidthAdaptation.dp2px(context, 40);

        bezierPath = new Path();
        bezierPaint = new Paint();
        bezierPaint.setAntiAlias(true);
        bezierPaint.setStyle(Paint.Style.FILL);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.MaterialHeader);
        showBezierWave = typedArray.getBoolean(R.styleable.MaterialHeader_srlShowBezierWave, showBezierWave);
        scrollableWhenRefreshing = typedArray.getBoolean(R.styleable.MaterialHeader_srlScrollableWhenRefreshing, scrollableWhenRefreshing);
        bezierPaint.setColor(typedArray.getColor(R.styleable.MaterialHeader_srlPrimaryColor, Color.parseColor("#11BBFF")));
        if (typedArray.hasValue(R.styleable.MaterialHeader_srlShadowRadius)) {
            int radius = typedArray.getDimensionPixelOffset(R.styleable.MaterialHeader_srlShadowRadius, 0);
            int color = typedArray.getColor(R.styleable.MaterialHeader_mhShadowColor, Color.parseColor("#000000"));
            bezierPaint.setShadowLayer(radius, 0, 0, color);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        showBezierWave = typedArray.getBoolean(R.styleable.MaterialHeader_mhShowBezierWave, showBezierWave);
        scrollableWhenRefreshing = typedArray.getBoolean(R.styleable.MaterialHeader_mhScrollableWhenRefreshing, scrollableWhenRefreshing);
        if (typedArray.hasValue(R.styleable.MaterialHeader_mhPrimaryColor)) {
            bezierPaint.setColor(typedArray.getColor(R.styleable.MaterialHeader_mhPrimaryColor, Color.parseColor("#11BBFF")));
        }
        if (typedArray.hasValue(R.styleable.MaterialHeader_mhShadowRadius)) {
            int radius = typedArray.getDimensionPixelOffset(R.styleable.MaterialHeader_mhShadowRadius, 0);
            int color = typedArray.getColor(R.styleable.MaterialHeader_mhShadowColor, Color.parseColor("#000000"));
            bezierPaint.setShadowLayer(radius, 0, 0, color);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        typedArray.recycle();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.setMeasuredDimension(getSize(widthMeasureSpec), getSize(heightMeasureSpec));
        circleView.measure(MeasureSpec.makeMeasureSpec(circleDiameter, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(circleDiameter, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() == 0) {
            return;
        }
        final int width = getMeasuredWidth();
        int circleWidth = circleView.getMeasuredWidth();
        int circleHeight = circleView.getMeasuredHeight();

        if (isInEditMode() && headHeight > 0) {
            int circleTop = headHeight - circleHeight / 2;
            circleView.layout((width / 2 - circleWidth / 2), circleTop,
                    (width / 2 + circleWidth / 2), circleTop + circleHeight);

            progressDrawable.showArrow(true);
            progressDrawable.setStartEndTrim(0f, MAX_PROGRESS_ANGLE);
            progressDrawable.setArrowScale(1);
            circleView.setAlpha(1f);
            circleView.setVisibility(VISIBLE);
        } else {
            circleView.layout((width / 2 - circleWidth / 2), -circleHeight, (width / 2 + circleWidth / 2), 0);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (showBezierWave) {
            // 重置画笔
            bezierPath.reset();
            bezierPath.lineTo(0, headHeight);
            // 绘制贝塞尔曲线
            bezierPath.quadTo(getMeasuredWidth() / 2f, headHeight + waveHeight * 1.9f, getMeasuredWidth(), headHeight);
            bezierPath.lineTo(getMeasuredWidth(), 0);
            canvas.drawPath(bezierPath, bezierPaint);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void onInitialized(@NonNull RefreshKernel kernel, int height, int maxDragHeight) {
        if (!showBezierWave) {
            kernel.requestDefaultTranslationContentFor(this, false);
        }
        if (isInEditMode()) {
            waveHeight = headHeight = height / 2;
        }
    }

    @Override
    public void onMoving(boolean dragging, float percent, int offset, int height, int maxDragHeight) {
        if (refreshState == RefreshState.Refreshing) {
            return;
        }

        if (showBezierWave) {
            headHeight = Math.min(offset, height);
            waveHeight = Math.max(0, offset - height);
            postInvalidate();
        }

        if (dragging || (!progressDrawable.isRunning() && !finished)) {

            if (refreshState != RefreshState.Refreshing) {
                float originalDragPercent = 1f * offset / height;

                float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
                float adjustedPercent = (float) Math.max(dragPercent - .4, 0) * 5 / 3;
                float extraOs = Math.abs(offset) - height;
                float tensionSlingshotPercent = Math.max(0, Math.min(extraOs, (float) height * 2)
                        / (float) height);
                float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(
                        (tensionSlingshotPercent / 4), 2)) * 2f;
                float strokeStart = adjustedPercent * .8f;
                progressDrawable.showArrow(true);
                progressDrawable.setStartEndTrim(0f, Math.min(MAX_PROGRESS_ANGLE, strokeStart));
                progressDrawable.setArrowScale(Math.min(1f, adjustedPercent));

                float rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f;
                progressDrawable.setProgressRotation(rotation);
            }

            float targetY = offset / 2f + circleDiameter / 2f;
            circleView.setTranslationY(Math.min(offset, targetY));
            circleView.setAlpha(Math.min(1f, 4f * offset / circleDiameter));
        }
    }

    @Override
    public void onReleased(@NonNull RefreshLayout layout, int height, int maxDragHeight) {
        progressDrawable.start();
    }

    @Override
    public void onStateChanged(@NonNull RefreshLayout refreshLayout, @NonNull RefreshState oldState, @NonNull RefreshState newState) {
        refreshState = newState;
        if (newState == RefreshState.PullDownToRefresh) {
            finished = false;
            circleView.setVisibility(VISIBLE);
            circleView.setTranslationY(0);
            circleView.setScaleX(1);
            circleView.setScaleY(1);
        }
    }

    @Override
    public int onFinish(@NonNull RefreshLayout layout, boolean success) {
        progressDrawable.stop();
        circleView.animate().scaleX(0).scaleY(0);
        finished = true;
        return 0;
    }

    /**
     * 设置背景色
     */
    public MaterialHeader setProgressBackgroundResource(@ColorRes int id) {
        setProgressBackgroundColor(ContextCompat.getColor(getContext(), id));
        return this;
    }

    public MaterialHeader setProgressBackgroundColor(@ColorInt int color) {
        circleView.setBackgroundColor(color);
        return this;
    }

    /**
     * 设置 ColorScheme
     *
     * @param colors ColorScheme
     */
    public MaterialHeader setColorSchemeColors(@ColorInt int... colors) {
        progressDrawable.setColorSchemeColors(colors);
        return this;
    }

    /**
     * 设置 ColorScheme
     *
     * @param ids ColorSchemeResources
     */
    public MaterialHeader setColorSchemeResources(@ColorRes int... ids) {
        int[] colors = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            colors[i] = ContextCompat.getColor(getContext(), ids[i]);
        }
        return setColorSchemeColors(colors);
    }

    /**
     * 设置刷新球样式
     *
     * @param style         可传入：{@link #BALL_STYLE_LARGE，#BALL_STYLE_DEFAULT}
     */
    public MaterialHeader setBallStyle(int style) {
        if (style != BALL_STYLE_LARGE && style != BALL_STYLE_DEFAULT) {
            return this;
        }
        if (style == BALL_STYLE_LARGE) {
            circleDiameter = (int) SmallestWidthAdaptation.dp2px(getContext(), 56);
        } else {
            circleDiameter = (int) SmallestWidthAdaptation.dp2px(getContext(), 40);
        }
        // force the bounds of the progress circle inside the circle view to
        // update by setting it to null before updating its size and then
        // re-setting it
        circleView.setImageDrawable(null);
        progressDrawable.updateSizes(style);
        circleView.setImageDrawable(progressDrawable);
        return this;
    }

    /**
     * 是否显示贝塞尔图形
     */
    public MaterialHeader setShowBezierWave(boolean show) {
        showBezierWave = show;
        return this;
    }

    /**
     * 设置实在正在刷新的时候可以上下滚动 Header
     */
    public MaterialHeader setScrollableWhenRefreshing(boolean scrollable) {
        scrollableWhenRefreshing = scrollable;
        return this;
    }
}
