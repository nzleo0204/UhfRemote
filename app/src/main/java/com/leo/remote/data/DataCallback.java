package com.leo.remote.data;

public interface DataCallback<T> {
    void onSuccess(T data);

    void onFail(Exception e);
}
