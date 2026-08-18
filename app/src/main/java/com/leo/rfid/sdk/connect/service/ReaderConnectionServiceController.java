package com.leo.rfid.sdk.connect.service;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.leo.rfid.sdk.model.ReaderState;
import com.leo.rfid.sdk.connect.ReaderService;

/**
 * 管理 Android 前台连接服务的生命周期和状态转发。
 */
public final class ReaderConnectionServiceController implements ReaderService {
    private static final String TAG = "UhfReader";

    private final Application application;
    private final Object lock = new Object();
    private volatile ReaderConnectionService service;
    private volatile ReaderState state = ReaderState.disconnected();

    public ReaderConnectionServiceController(Application application) {
        this.application = application;
    }

    @Override
    public void start() {
        try {
            Intent intent = new Intent(application, ReaderConnectionService.class)
                    .setAction(ReaderConnectionService.ACTION_START);
            ContextCompat.startForegroundService(application, intent);
        } catch (Throwable error) {
            Log.e(TAG, "Unable to start reader connection service", error);
        }
    }

    @Override
    public void stop() {
        application.stopService(new Intent(application, ReaderConnectionService.class));
    }

    @Override
    public void update(ReaderState updated) {
        state = updated;
        ReaderConnectionService current = service;
        if (current != null) { current.updateReaderState(updated); }
    }

    public void onServiceCreated(ReaderConnectionService created) {
        synchronized (lock) {
            service = created;
        }
        created.updateReaderState(state);
        Log.i(TAG, "reader connection foreground service created");
    }

    public void onServiceDestroyed(ReaderConnectionService destroyed) {
        synchronized (lock) {
            if (service == destroyed) { service = null; }
        }
        Log.i(TAG, "reader connection foreground service destroyed");
    }
}
