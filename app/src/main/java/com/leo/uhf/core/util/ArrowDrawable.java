package com.leo.uhf.core.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.leo.uhf.R;
import com.hjq.smallest.width.SmallestWidthAdaptation;

/** 带箭头背景的 Drawable */
@SuppressLint("RtlHardcoded")
public final class ArrowDrawable extends Drawable {

    private final Builder builder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path;

    private ArrowDrawable(Builder builder) {
        this.builder = builder;
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (builder.shadowSize > 0) {
            paint.setMaskFilter(new BlurMaskFilter(builder.shadowSize, BlurMaskFilter.Blur.OUTER));
            paint.setColor(builder.shadowColor);
            canvas.drawPath(path, paint);
        }
        paint.setMaskFilter(null);
        paint.setColor(builder.backgroundColor);
        canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    protected void onBoundsChange(@NonNull Rect viewRect) {
        if (path == null) {
            path = new Path();
        } else {
            path.reset();
        }

        RectF excludeShadowRectF = new RectF(viewRect);
        excludeShadowRectF.inset(builder.shadowSize, builder.shadowSize);

        PointF centerPointF = new PointF();

        // 判断箭头的位置
        switch (builder.arrowOrientation) {
            case Gravity.LEFT:
                excludeShadowRectF.left += builder.arrowHeight;
                centerPointF.x = excludeShadowRectF.left;
                break;
            case Gravity.RIGHT:
                excludeShadowRectF.right -= builder.arrowHeight;
                centerPointF.x = excludeShadowRectF.right;
                break;
            case Gravity.TOP:
                excludeShadowRectF.top += builder.arrowHeight;
                centerPointF.y = excludeShadowRectF.top;
                break;
            case Gravity.BOTTOM:
                excludeShadowRectF.bottom -= builder.arrowHeight;
                centerPointF.y = excludeShadowRectF.bottom;
                break;
            default:
                break;
        }

        // 判断箭头的重心
        switch (builder.arrowGravity) {
            case Gravity.LEFT:
                centerPointF.x = excludeShadowRectF.left + builder.arrowHeight;
                break;
            case Gravity.CENTER_HORIZONTAL:
                centerPointF.x = viewRect.width() / 2f;
                break;
            case Gravity.RIGHT:
                centerPointF.x = excludeShadowRectF.right - builder.arrowHeight;
                break;
            case Gravity.TOP:
                centerPointF.y = excludeShadowRectF.top + builder.arrowHeight;
                break;
            case Gravity.CENTER_VERTICAL:
                centerPointF.y = viewRect.height() / 2f;
                break;
            case Gravity.BOTTOM:
                centerPointF.y = excludeShadowRectF.bottom - builder.arrowHeight;
                break;
            default:
                break;
        }

        // 更新箭头偏移量
        centerPointF.x += builder.arrowOffsetX;
        centerPointF.y += builder.arrowOffsetY;

        switch (builder.arrowGravity) {
            case Gravity.LEFT:
            case Gravity.RIGHT:
            case Gravity.CENTER_HORIZONTAL:
                centerPointF.x = Math.max(centerPointF.x, excludeShadowRectF.left + builder.radius + builder.arrowHeight);
                centerPointF.x = Math.min(centerPointF.x, excludeShadowRectF.right - builder.radius - builder.arrowHeight);
                break;
            case Gravity.TOP:
            case Gravity.BOTTOM:
            case Gravity.CENTER_VERTICAL:
                centerPointF.y = Math.max(centerPointF.y, excludeShadowRectF.top + builder.radius + builder.arrowHeight);
                centerPointF.y = Math.min(centerPointF.y, excludeShadowRectF.bottom - builder.radius - builder.arrowHeight);
                break;
            default:
                break;
        }

        switch (builder.arrowOrientation) {
            case Gravity.LEFT:
            case Gravity.RIGHT:
                centerPointF.x = Math.max(centerPointF.x, excludeShadowRectF.left);
                centerPointF.x = Math.min(centerPointF.x, excludeShadowRectF.right);
                break;
            case Gravity.TOP:
            case Gravity.BOTTOM:
                centerPointF.y = Math.max(centerPointF.y, excludeShadowRectF.top);
                centerPointF.y = Math.min(centerPointF.y, excludeShadowRectF.bottom);
                break;
            default:
                break;
        }

        // 箭头区域（其实是旋转了 90 度后的正方形区域）
        Path arrowPath = new Path();
        arrowPath.moveTo(centerPointF.x - builder.arrowHeight, centerPointF.y);
        arrowPath.lineTo(centerPointF.x, centerPointF.y - builder.arrowHeight);
        arrowPath.lineTo(centerPointF.x + builder.arrowHeight, centerPointF.y);
        arrowPath.lineTo(centerPointF.x, centerPointF.y + builder.arrowHeight);
        arrowPath.close();

        path.addRoundRect(excludeShadowRectF, builder.radius, builder.radius, Path.Direction.CW);
        path.addPath(arrowPath);

        invalidateSelf();
    }

    public static final class Builder {

        /** 上下文对象 */
        @NonNull
        private final Context context;
        /** 箭头高度 */
        private int arrowHeight;
        /** 背景圆角大小 */
        private int radius;
        /** 箭头方向 */
        private int arrowOrientation;
        /** 箭头重心 */
        private int arrowGravity;
        /** 箭头水平方向偏移 */
        private int arrowOffsetX;
        /** 箭头垂直方向偏移 */
        private int arrowOffsetY;
        /** 阴影大小 */
        private int shadowSize;
        /** 背景颜色 */
        private int backgroundColor;
        /** 阴影颜色 */
        private int shadowColor;

        public Builder(@NonNull Context context) {
            this.context = context;
            backgroundColor = ContextCompat.getColor(context, R.color.black);
            shadowColor = ContextCompat.getColor(context, R.color.black20);
            arrowHeight = (int) SmallestWidthAdaptation.dp2px(context, 6);
            radius = (int) SmallestWidthAdaptation.dp2px(context, 4);
            shadowSize = 0;
            arrowOffsetX = 0;
            arrowOffsetY = 0;
            arrowOrientation = Gravity.NO_GRAVITY;
            arrowGravity = Gravity.NO_GRAVITY;
        }

        /**
         * 设置背景色
         */
        public Builder setBackgroundColor(@ColorInt int color) {
            backgroundColor = color;
            return this;
        }

        /**
         * 设置阴影色
         */
        public Builder setShadowColor(@ColorInt int color) {
            shadowColor = color;
            return this;
        }

        /**
         * 设置箭头高度
         */
        public Builder setArrowHeight(int height) {
            arrowHeight = height;
            return this;
        }

        /**
         * 设置浮窗圆角半径
         */
        public Builder setRadius(int radius) {
            this.radius = radius;
            return this;
        }

        /**
         * 设置箭头方向（左上右下）
         */
        public Builder setArrowOrientation(int orientation) {
            switch (orientation = Gravity.getAbsoluteGravity(orientation, context.getResources().getConfiguration().getLayoutDirection())) {
                case Gravity.LEFT:
                case Gravity.TOP:
                case Gravity.RIGHT:
                case Gravity.BOTTOM:
                    arrowOrientation = orientation;
                    break;
                default:
                    // 箭头只能在左上右下这四个位置
                    throw new IllegalArgumentException("The arrow can only be in the four positions: left, top, right, and bottom");
            }
            return this;
        }

        /**
         * 设置箭头布局重心
         */
        public Builder setArrowGravity(int gravity) {
            gravity = Gravity.getAbsoluteGravity(gravity, context.getResources().getConfiguration().getLayoutDirection());
            if (gravity == Gravity.CENTER) {
                switch (arrowOrientation) {
                    case Gravity.LEFT:
                    case Gravity.RIGHT:
                        gravity = Gravity.CENTER_VERTICAL;
                        break;
                    case Gravity.TOP:
                    case Gravity.BOTTOM:
                        gravity = Gravity.CENTER_HORIZONTAL;
                        break;
                    default:
                        break;
                }
            }
            switch (gravity) {
                case Gravity.LEFT:
                case Gravity.RIGHT:
                    if (arrowOrientation == Gravity.LEFT || arrowOrientation == Gravity.RIGHT) {
                        throw new IllegalArgumentException("The arrow direction cannot be the same as the arrow gravity");
                    }
                    break;
                case Gravity.TOP:
                case Gravity.BOTTOM:
                    if (arrowOrientation == Gravity.TOP || arrowOrientation == Gravity.BOTTOM) {
                        throw new IllegalArgumentException("The arrow direction cannot be the same as the arrow gravity");
                    }
                    break;
                case Gravity.CENTER_VERTICAL:
                case Gravity.CENTER_HORIZONTAL:
                    break;
                default:
                    // 箭头只能在左上右下这四个位置
                    throw new IllegalArgumentException("The arrow can only be in the four positions: left, top, right, and bottom");
            }
            arrowGravity = gravity;
            return this;
        }

        /**
         * 设置箭头在 x 轴的偏移量
         */
        public Builder setArrowOffsetX(int offsetX) {
            arrowOffsetX = offsetX;
            return this;
        }

        /**
         * 设置箭头在 y 轴的偏移量
         */
        public Builder setArrowOffsetY(int offsetY) {
            arrowOffsetY = offsetY;
            return this;
        }

        /**
         * 设置阴影宽度
         */
        public Builder setShadowSize(int size) {
            shadowSize = size;
            return this;
        }

        /**
         * 构建 Drawable
         */
        public ArrowDrawable build() {
            if (arrowOrientation == Gravity.NO_GRAVITY || arrowGravity == Gravity.NO_GRAVITY) {
                // 必须要先设置箭头的方向及重心
                throw new IllegalArgumentException("You must set the direction and gravity of the arrow");
            }
            return new ArrowDrawable(this);
        }

        /**
         * 应用到 View
         */
        public void apply(View view) {
            view.setBackground(build());
            if (shadowSize > 0 || arrowHeight > 0) {
                if (view.getPaddingTop() == 0 && view.getBottom() == 0 &&
                        view.getPaddingLeft() == 0 && view.getPaddingRight() == 0) {
                    view.setPadding(shadowSize, shadowSize + arrowHeight, shadowSize, shadowSize);
                }
            }
        }
    }
}
