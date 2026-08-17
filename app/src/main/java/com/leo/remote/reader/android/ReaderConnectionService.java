package com.leo.remote.reader.android;

import com.leo.remote.reader.model.*;
import com.leo.remote.reader.session.ReaderSessionManager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.leo.remote.R;

/**
 * Reader 连接服务
 *
 * 保持 Reader 会话活跃，并通过前台通知展示当前连接状态。
 * 使用前台服务可以防止系统在后台时杀死连接，确保长时间稳定运行。
 *
 * 主要功能：
 * - 维持与 RFID Reader 的连接
 * - 显示前台通知展示连接状态
 * - 管理 Wi-Fi 锁，防止 Wi-Fi 休眠
 * - 提供断开连接的操作入口
 */
public final class ReaderConnectionService extends Service {
    private static final String TAG = "UhfRemote/ConnectionService";
    public static final String ACTION_START = "com.leo.remote.reader.action.START";
    static final String ACTION_DISCONNECT = "com.leo.remote.reader.action.DISCONNECT";
    private static final String CHANNEL_ID = "reader_connection";
    private static final int NOTIFICATION_ID = 701;

    private ReaderSessionManager session;
    private ReaderState state = ReaderState.disconnected();
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "创建 Reader 连接服务");
        session = ReaderSessionManager.getInstance(getApplication());
        WifiManager wifiManager = getSystemService(WifiManager.class);
        if (wifiManager != null) {
            wifiLock = createWifiLock(wifiManager);
            wifiLock.setReferenceCounted(false);
        }
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        session.onConnectionServiceCreated(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction()) && session != null) {
            session.disconnect();
        } else if (session != null) {
            updateReaderState(session.getState());
        }
        return START_STICKY;
    }

    public void updateReaderState(ReaderState updated) {
        Log.d(TAG, "更新 Reader 状态: phase=" + updated.getPhase()
                + ", connected=" + updated.isConnected());
        state = updated;
        updateWifiLock(updated);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) { manager.notify(NOTIFICATION_ID, buildNotification()); }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "销毁 Reader 连接服务");
        if (session != null) { session.onConnectionServiceDestroyed(this); }
        if (wifiLock != null && wifiLock.isHeld()) {
            Log.d(TAG, "释放 Wi-Fi 锁");
            wifiLock.release();
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        String content;
        if (state.isConnected()) {
            String target = state.getTransport() == TransportType.WIFI
                    ? state.getAddress() : state.getDeviceName();
            content = getString(R.string.reader_service_connected, target);
        } else if (state.getPhase() == ConnectionPhase.CONNECTING
                || state.getPhase() == ConnectionPhase.DISCOVERING_SERVICES
                || state.getPhase() == ConnectionPhase.ENABLING_NOTIFICATIONS
                || state.getPhase() == ConnectionPhase.CONNECTING_DATA_CHANNEL
                || state.getPhase() == ConnectionPhase.VERIFYING_MODULE
                || state.getPhase() == ConnectionPhase.UPDATING_PARAMETERS) {
            content = getString(R.string.reader_service_connecting);
        } else {
            content = getString(R.string.reader_service_disconnected);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.launcher_ic)
                .setContentTitle(getString(R.string.reader_service_channel_name))
                .setContentText(content)
                .setContentIntent(contentIntent())
                .addAction(new NotificationCompat.Action.Builder(0,
                        getString(R.string.reader_service_action_disconnect), disconnectIntent()).build())
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateWifiLock(ReaderState updated) {
        if (wifiLock == null) { return; }
        boolean shouldHold = updated.getTransport() == TransportType.WIFI
                && updated.getPhase() != ConnectionPhase.DISCONNECTED
                && updated.getPhase() != ConnectionPhase.FAILED;
        if (shouldHold && !wifiLock.isHeld()) {
            wifiLock.acquire();
        } else if (!shouldHold && wifiLock.isHeld()) {
            wifiLock.release();
        }
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(this, com.leo.remote.ui.activity.HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 702, intent, pendingIntentFlags());
    }

    private PendingIntent disconnectIntent() {
        Intent intent = new Intent(this, ReaderConnectionService.class).setAction(ACTION_DISCONNECT);
        return PendingIntent.getService(this, 703, intent, pendingIntentFlags());
    }

    private static int pendingIntentFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    @SuppressWarnings("deprecation")
    private static WifiManager.WifiLock createWifiLock(WifiManager wifiManager) {
        // Keep the API 26 background Wi-Fi behavior; newer Android versions remap this mode.
        return wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "UhfRemote:reader-connection");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { return; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    getString(R.string.reader_service_channel_name), NotificationManager.IMPORTANCE_LOW));
        }
    }
}
