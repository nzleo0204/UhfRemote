package com.leo.remote.http.model;

import androidx.annotation.NonNull;
import com.hjq.http.config.IHttpBodyStrategy;
import com.hjq.http.config.IRequestServer;
import com.hjq.http.model.RequestBodyType;
import com.leo.remote.util.AppConfig;

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
