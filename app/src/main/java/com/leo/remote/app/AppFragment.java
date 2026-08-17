package com.leo.remote.app;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hjq.base.BaseFragment;
import com.leo.remote.action.ToastAction;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2018/10/18
 *    desc   : Fragment 业务基类
 */
public abstract class AppFragment<A extends AppActivity> extends BaseFragment<A> implements ToastAction {

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
