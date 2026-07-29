package com.leo.remote.http.exception;

import androidx.annotation.NonNull;
import com.hjq.http.exception.HttpException;

public final class TokenException extends HttpException {
    public TokenException(@NonNull String message) {
        super(message);
    }
}
