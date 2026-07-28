package com.leo.remote.reader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.leo.remote.R;

/** Keeps the BLE transport and JNI data bridge alive while the app is backgrounded. */
public final class ReaderBleService extends Service {
    static final String ACTION_START = "com.leo.remote.reader.action.START";
    private static final String CHANNEL_ID = "reader_ble_transport";
    private static final int NOTIFICATION_ID = 701;

    private ReaderSessionManager session;

    @Override
    public void onCreate() {
        super.onCreate();
        session = ReaderSessionManager.getInstance(getApplication());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        session.onBleServiceCreated(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    void writeToBle(byte[] data) { if (session != null) { session.writeBleData(data); } }

    void pushNotifyToJni(byte[] data) {
        if (session != null) { session.pushInboundDataToSdk(data); }
    }

    @Override
    public void onDestroy() {
        if (session != null) { session.onBleServiceDestroyed(this); }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.launcher_ic)
                .setContentTitle(getString(R.string.reader_ble_service_title))
                .setContentText(getString(R.string.reader_ble_service_running))
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { return; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    getString(R.string.reader_ble_service_channel), NotificationManager.IMPORTANCE_LOW));
        }
    }
}
