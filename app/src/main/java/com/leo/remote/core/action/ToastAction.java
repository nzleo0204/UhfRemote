package com.leo.remote.core.action;

import androidx.annotation.StringRes;
import com.hjq.toast.Toaster;

/**



 *    吐司意图
 */
public interface ToastAction {

    default void toast(CharSequence text) {
        Toaster.show(text);
    }

    default void toast(@StringRes int id) {
        Toaster.show(id);
    }

    default void toast(Object object) {
        Toaster.show(object);
    }
}