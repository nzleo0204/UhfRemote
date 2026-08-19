package com.leo.uhf.core.data;

/**
 * 定义异步数据请求的成功与失败回调。
 */
public interface DataCallback<T> {
    void onSuccess(T data);

    void onFail(Exception e);
}
