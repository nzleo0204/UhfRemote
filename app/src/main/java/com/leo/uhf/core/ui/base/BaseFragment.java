package com.leo.uhf.core.ui.base;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.leo.uhf.core.action.ToastAction;

/** Fragment 业务基类 */
public abstract class BaseFragment<A extends BaseActivity>
        extends com.hjq.base.BaseFragment<A> implements ToastAction {

    private int viewGeneration;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewGeneration++;
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        viewGeneration++;
        super.onDestroyView();
    }

    /** Runs UI work only while the View instance that scheduled it is still active. */
    protected final void runOnViewThread(@NonNull Runnable action) {
        A activity = getAttachActivity();
        if (activity == null) { return; }
        int scheduledGeneration = viewGeneration;
        activity.runOnUiThread(() -> {
            if (scheduledGeneration == viewGeneration && isViewReady()) {
                action.run();
            }
        });
    }

    protected final boolean isViewReady() {
        return isAdded() && getView() != null && getAttachActivity() != null;
    }

    /**
     * 当前加载对话框是否在显示中
     */
    public boolean isShowDialog() {
        A activity = getAttachActivity();
        if (activity == null) {
            return false;
        }
        return activity.isShowDialog();
    }

    /**
     * 显示加载对话框
     */
    public void showLoadingDialog() {
        A activity = getAttachActivity();
        if (activity == null) {
            return;
        }
        activity.showLoadingDialog();
    }

    /**
     * 隐藏加载对话框
     */
    public void hideLoadingDialog() {
        A activity = getAttachActivity();
        if (activity == null) {
            return;
        }
        activity.hideLoadingDialog();
    }

}
