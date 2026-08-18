package com.leo.remote.business.auth.data;

import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.auth.data.model.UserInfo;

/**
 * 定义认证和用户信息业务所需的数据访问能力。
 */
public interface AuthRepository {
    void login(String username, String password, DataCallback<UserInfo> callback);
}
