package com.leo.remote.core.util;

import com.hjq.toast.ToastLogInterceptor;
import com.leo.remote.core.aop.Log;

/**



 *    自定义 Toast 拦截器（用于追踪 Toast 调用的位置）
 */
public final class ToastInterceptor extends ToastLogInterceptor {

    @Override
    protected boolean isLogEnable() {
        return AppConfig.isLogEnable();
    }

    @Log("Toaster")
    @Override
    protected void printLog(String msg) {
    }
}
