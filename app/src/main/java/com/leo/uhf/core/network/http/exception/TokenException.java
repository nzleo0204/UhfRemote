package com.leo.uhf.core.network.http.exception;

import androidx.annotation.NonNull;
import com.hjq.http.exception.HttpException;

/**
 * 表示网络请求因登录令牌失效而失败。
 */
public final class TokenException extends HttpException {
    public TokenException(@NonNull String message) {
        super(message);
    }
}
