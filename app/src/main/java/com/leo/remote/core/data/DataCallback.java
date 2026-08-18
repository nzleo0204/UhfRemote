package com.leo.remote.core.data;

public interface DataCallback<T> {
    void onSuccess(T data);

    void onFail(Exception e);
}
