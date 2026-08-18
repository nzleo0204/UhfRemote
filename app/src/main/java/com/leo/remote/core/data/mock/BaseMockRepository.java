package com.leo.remote.core.data.mock;

import android.os.Handler;
import android.os.Looper;
import com.leo.remote.core.data.DataCallback;
import java.util.Random;

/**
 * 为本地演示数据仓库提供统一的模拟数据基类。
 */
public abstract class BaseMockRepository {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean failEnabled;
    private boolean emptyEnabled;

    public void setFailEnabled(boolean failEnabled) {
        this.failEnabled = failEnabled;
    }

    public void setEmptyEnabled(boolean emptyEnabled) {
        this.emptyEnabled = emptyEnabled;
    }

    protected final <T> void respond(DataCallback<T> callback, T data, T emptyData) {
        int delay = 300 + random.nextInt(501);
        handler.postDelayed(() -> {
            if (failEnabled) {
                callback.onFail(new IllegalStateException("Mock 请求失败"));
                return;
            }
            callback.onSuccess(emptyEnabled ? emptyData : data);
        }, delay);
    }
}
