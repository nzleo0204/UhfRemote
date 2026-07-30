package com.hjq.core.action;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2020/03/08
 *    desc   : 软键盘相关意图
 */
public interface KeyboardAction {

    /**
     * 显示软键盘，需要先 requestFocus 获取焦点，如果是在 Activity Create，那么需要延迟一段时间
     */
    default void showKeyboard(View view) {
        if (view == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager == null) {
            return;
        }
        manager.showSoftInput(view, 0);
    }

    /**
     * 隐藏软键盘
     */
    default void hideKeyboard(View view) {
        if (view == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager == null) {
            return;
        }
        manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /** 隐藏输入框以外区域点击时的软键盘，并继续派发当前触摸事件。 */
    default void hideKeyboardIfTouchOutside(View root, MotionEvent event) {
        if (root == null || event == null || event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return;
        }
        View focusedView = root.findFocus();
        if (!(focusedView instanceof EditText)
                || isEditTextAt(root, event.getRawX(), event.getRawY())) {
            return;
        }
        hideKeyboard(focusedView);
        focusedView.clearFocus();
    }

    /** 支持复合输入控件，例如四段式 IPv4 输入框。 */
    default boolean isEditTextAt(View view, float rawX, float rawY) {
        if (view instanceof EditText) {
            Rect bounds = new Rect();
            if (view.getGlobalVisibleRect(bounds) && bounds.contains((int) rawX, (int) rawY)) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (isEditTextAt(group.getChildAt(index), rawX, rawY)) {
                    return true;
                }
            }
        }
        return false;
    }
}
