package com.leo.remote.business.auth.data;

import com.leo.remote.data.DataCallback;
import com.leo.remote.business.auth.data.model.UserInfo;

public interface AuthRepository {
    void login(String username, String password, DataCallback<UserInfo> callback);
}
