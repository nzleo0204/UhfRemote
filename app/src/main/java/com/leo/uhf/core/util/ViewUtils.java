package com.leo.uhf.core.util;

import android.view.View;
import android.view.ViewGroup;

/**
 * View 工具类
 *
 * 提供 View 相关的通用工具方法，包括批量设置状态、递归操作等。
 *
 */
public final class ViewUtils {

    private ViewUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 递归设置 View 及其子 View 的 enabled 状态
     *
     * 用于批量启用/禁用一组控件，常用于表单场景。
     * 如果 view 是 ViewGroup，会递归设置所有子 View。
     *
     * 使用示例：
     * <pre>
     * // 禁用整个表单
     * ViewUtils.setEnabledRecursively(formLayout, false);
     *
     * // 启用整个表单
     * ViewUtils.setEnabledRecursively(formLayout, true);
     * </pre>
     *
     * @param view 目标 View（可以是 ViewGroup）
     * @param enabled true 启用，false 禁用
     */
    public static void setEnabledRecursively(View view, boolean enabled) {
        if (view == null) {
            return;
        }

        view.setEnabled(enabled);

        if (!(view instanceof ViewGroup)) {
            return;
        }

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            setEnabledRecursively(group.getChildAt(i), enabled);
        }
    }

    /**
     * 递归设置 View 及其子 View 的可见性
     *
     * 如果 view 是 ViewGroup，会递归设置所有子 View。
     *
     * @param view 目标 View
     * @param visibility View.VISIBLE, View.INVISIBLE, 或 View.GONE
     */
    public static void setVisibilityRecursively(View view, int visibility) {
        if (view == null) {
            return;
        }

        view.setVisibility(visibility);

        if (!(view instanceof ViewGroup)) {
            return;
        }

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            setVisibilityRecursively(group.getChildAt(i), visibility);
        }
    }

    /**
     * 检查 View 是否可见
     *
     * @param view 目标 View
     * @return true 如果 view 不为 null 且可见性为 VISIBLE
     */
    public static boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /**
     * 切换 View 的可见性
     *
     * 如果当前是 VISIBLE 则切换为 GONE，反之亦然。
     *
     * @param view 目标 View
     */
    public static void toggleVisibility(View view) {
        if (view == null) {
            return;
        }
        view.setVisibility(view.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }
}
