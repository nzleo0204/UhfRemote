package com.leo.remote.data.repository;

import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.UserInfo;

public interface AuthRepository {
    void login(String username, String password, DataCallback<UserInfo> callback);
}
