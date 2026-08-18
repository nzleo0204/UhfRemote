package com.leo.remote.core.network.http.model;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.annotation.NonNull;
import com.hjq.gson.factory.GsonFactory;
import com.hjq.http.EasyLog;
import com.hjq.http.config.IRequestHandler;
import com.hjq.http.exception.CancelException;
import com.hjq.http.exception.DataException;
import com.hjq.http.exception.FileMd5Exception;
import com.hjq.http.exception.HttpException;
import com.hjq.http.exception.NetworkException;
import com.hjq.http.exception.NullBodyException;
import com.hjq.http.exception.ResponseException;
import com.hjq.http.exception.ServerException;
import com.hjq.http.exception.TimeoutException;
import com.hjq.http.request.HttpRequest;
import com.leo.remote.R;
import com.leo.remote.core.network.http.exception.ResultException;
import com.leo.remote.core.network.http.exception.TokenException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Headers;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class RequestHandler implements IRequestHandler {
    private final Application application;

    public RequestHandler(Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    public Object requestSuccess(@NonNull HttpRequest<?> httpRequest,
            @NonNull Response response, @NonNull Type type) throws Throwable {
        if (Response.class.equals(type)) {
            return response;
        }
        if (!response.isSuccessful()) {
            throw new ResponseException(String.format(application.getString(R.string.http_response_error),
                    response.code(), response.message()), response);
        }
        if (Object.class.equals(type)) {
            return "";
        }
        if (Headers.class.equals(type)) {
            return response.headers();
        }
        ResponseBody body = response.body();
        if (body == null) {
            throw new NullBodyException(application.getString(R.string.http_response_null_body));
        }
        if (InputStream.class.equals(type)) {
            return body.byteStream();
        }
        if (Bitmap.class.equals(type)) {
            return BitmapFactory.decodeStream(body.byteStream());
        }

        String text;
        try {
            text = body.string();
        } catch (IOException e) {
            throw new DataException(application.getString(R.string.http_data_explain_error), e);
        }
        EasyLog.printJson(httpRequest, text);
        if (String.class.equals(type)) {
            return text;
        }

        final Object result;
        try {
            result = GsonFactory.getSingletonGson().fromJson(text, type);
        } catch (Exception e) {
            throw new DataException(application.getString(R.string.http_data_explain_error), e);
        }

        if (result instanceof HttpData<?> model) {
            Headers headers = response.headers();
            Map<String, String> headersMap = new HashMap<>(headers.size());
            for (int i = 0; i < headers.size(); i++) {
                headersMap.put(headers.name(i), headers.value(i));
            }
            model.setResponseHeaders(headersMap);
            if (model.isRequestSuccess()) {
                return result;
            }
            if (model.isTokenInvalidation()) {
                throw new TokenException(application.getString(R.string.http_token_error));
            }
            throw new ResultException(model.getMessage(), model);
        }
        return result;
    }

    @NonNull
    @Override
    public Throwable requestFail(@NonNull HttpRequest<?> httpRequest, @NonNull Throwable throwable) {
        if (throwable instanceof HttpException) {
            return throwable;
        }
        if (throwable instanceof SocketTimeoutException) {
            return new TimeoutException(application.getString(R.string.http_server_out_time), throwable);
        }
        if (throwable instanceof UnknownHostException) {
            NetworkInfo info = ((ConnectivityManager) application.getSystemService(
                    Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            if (info == null || !info.isConnected()) {
                return new NetworkException(application.getString(R.string.http_network_error), throwable);
            }
            return new ServerException(application.getString(R.string.http_server_error), throwable);
        }
        if (throwable instanceof IOException) {
            return new CancelException(application.getString(R.string.http_request_cancel), throwable);
        }
        String message = throwable.getMessage();
        return new HttpException(message != null ? message : "", throwable);
    }

    @NonNull
    @Override
    public Throwable downloadFail(@NonNull HttpRequest<?> httpRequest, @NonNull Throwable throwable) {
        if (throwable instanceof ResponseException responseException) {
            Response response = responseException.getResponse();
            responseException.setMessage(String.format(application.getString(R.string.http_response_error),
                    response.code(), response.message()));
            return responseException;
        }
        if (throwable instanceof NullBodyException nullBodyException) {
            nullBodyException.setMessage(application.getString(R.string.http_response_null_body));
            return nullBodyException;
        }
        if (throwable instanceof FileMd5Exception fileMd5Exception) {
            fileMd5Exception.setMessage(application.getString(R.string.http_response_md5_error));
            return fileMd5Exception;
        }
        return requestFail(httpRequest, throwable);
    }
}
