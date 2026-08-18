package com.leo.remote.core.network.http.model;

import androidx.annotation.NonNull;
import com.hjq.http.config.IHttpBodyStrategy;
import com.hjq.http.config.IRequestServer;
import com.hjq.http.model.RequestBodyType;
import com.leo.remote.core.util.AppConfig;

/**
 * 保存网络请求的服务端地址和请求配置。
 */
public class RequestServer implements IRequestServer {
    @NonNull
    @Override
    public String getHost() {
        return AppConfig.getHostUrl() + "api/";
    }

    @NonNull
    @Override
    public IHttpBodyStrategy getBodyType() {
        return RequestBodyType.FORM;
    }
}
