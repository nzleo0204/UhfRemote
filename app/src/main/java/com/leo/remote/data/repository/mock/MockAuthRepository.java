package com.leo.remote.data.repository.mock;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.UserInfo;
import com.leo.remote.data.repository.AuthRepository;
import java.util.Random;

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
