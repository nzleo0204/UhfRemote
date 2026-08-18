package com.leo.remote.business.auth.data.mock;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.auth.data.model.UserInfo;
import com.leo.remote.business.auth.data.AuthRepository;
import java.util.Random;

/**
 * 提供认证和用户页面使用的本地模拟数据。
 */
public final class MockAuthRepository implements AuthRepository {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    @Override
    public void login(String username, String password, DataCallback<UserInfo> callback) {
        int delay = 300 + random.nextInt(501);
        handler.postDelayed(() -> {
            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                callback.onFail(new IllegalArgumentException("请输入用户名和密码"));
                return;
            }
            callback.onSuccess(new UserInfo(username, "管理员", true));
        }, delay);
    }
}
