package com.leo.uhf.core.network.http.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

/**
 * 表示服务端返回的单对象数据包装。
 */
public class HttpData<T> {
    @Nullable
    private Map<String, String> responseHeaders;
    private int code;
    @Nullable
    private String msg;
    @Nullable
    private T data;

    public void setResponseHeaders(@Nullable Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    @Nullable
    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public int getCode() {
        return code;
    }

    @NonNull
    public String getMessage() {
        return msg == null ? "" : msg;
    }

    @Nullable
    public T getData() {
        return data;
    }

    public boolean isRequestSuccess() {
        return code == 200;
    }

    public boolean isTokenInvalidation() {
        return code == 1001;
    }
}
