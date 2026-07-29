package com.leo.remote.http.exception;

import androidx.annotation.NonNull;
import com.hjq.http.exception.HttpException;
import com.leo.remote.http.model.HttpData;

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
