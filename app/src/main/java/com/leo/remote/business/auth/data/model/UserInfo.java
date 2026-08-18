package com.leo.remote.business.auth.data.model;

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
