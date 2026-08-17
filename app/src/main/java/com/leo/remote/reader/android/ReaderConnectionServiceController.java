package com.leo.remote.reader.android;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.leo.remote.reader.model.ReaderState;
import com.leo.remote.reader.session.ReaderConnectionServiceHost;

/** Owns Android foreground-service lifecycle and state delivery. */
public final class ReaderConnectionServiceController implements ReaderConnectionServiceHost {
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
