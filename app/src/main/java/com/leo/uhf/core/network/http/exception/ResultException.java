package com.leo.uhf.core.network.http.exception;

import androidx.annotation.NonNull;
import com.hjq.http.exception.HttpException;
import com.leo.uhf.core.network.http.model.HttpData;

/**
 * 表示服务端返回业务失败结果。
 */
public final class ResultException extends HttpException {
    @NonNull
    private final HttpData<?> data;

    public ResultException(@NonNull String message, @NonNull HttpData<?> data) {
        super(message);
        this.data = data;
    }

    @NonNull
    public HttpData<?> getHttpData() {
        return data;
    }
}
