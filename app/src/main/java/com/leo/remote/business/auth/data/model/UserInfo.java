package com.leo.remote.business.auth.data.model;

/**
 * 表示当前登录用户的基础信息。
 */
public final class UserInfo {
    public String username;
    public String role;
    public boolean logged;

    public UserInfo(String username, String role, boolean logged) {
        this.username = username;
        this.role = role;
        this.logged = logged;
    }
}
