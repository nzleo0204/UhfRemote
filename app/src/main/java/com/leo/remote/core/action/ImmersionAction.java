package com.leo.remote.core.action;

import android.view.View;
import androidx.annotation.Nullable;
import com.hjq.bar.OnTitleBarListener;

/**



 *    沉浸式意图
 */
public interface ImmersionAction extends OnTitleBarListener {

    /**
     * 获取需要沉浸的顶部 View 对象
     */
    @Nullable
    default View getImmersionTopView() {
        return null;
    }

    /**
     * 获取需要沉浸的底部 View 对象
     */
    @Nullable
    default View getImmersionBottomView() {
        return null;
    }
}