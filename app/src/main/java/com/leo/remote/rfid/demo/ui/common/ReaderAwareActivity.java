package com.leo.remote.rfid.demo.ui.common;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.remote.rfid.sdk.model.DisconnectReason;
import com.leo.remote.rfid.sdk.model.ReaderConnectionStatus;
import com.leo.remote.rfid.sdk.connection.ReaderObserver;
import com.leo.remote.rfid.sdk.connection.ReaderSessionManager;
import com.leo.remote.app.MainActivity;
import com.leo.remote.rfid.demo.ui.config.ReaderConfigFragment;
import com.leo.remote.core.ui.dialog.MessageDialog;
import com.leo.remote.core.ui.base.BaseActivity;
import java.lang.ref.WeakReference;

/** Activity base for screens that participate in the process-wide reader session. */
public abstract class ReaderAwareActivity extends BaseActivity implements ReaderObserver {
    private static WeakReference<ReaderAwareActivity> resumed = new WeakReference<>(null);

    private ReaderSessionManager readerSession;
    private MessageDialog.Builder disconnectDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readerSession = ReaderSessionManager.getInstance(getApplication());
        readerSession.addObserver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = new WeakReference<>(this);
        if (readerSession.isPendingDisconnectAlert()) {
            showDisconnectDialog(readerSession.getLastUnexpectedReason());
        }
    }

    @Override
    protected void onPause() {
        if (resumed.get() == this) { resumed.clear(); }
        super.onPause();
    }

    @Override
    public void onReaderUnexpectedDisconnect(DisconnectReason reason) {
        if (resumed.get() == this) { showDisconnectDialog(reason); }
    }

    public final boolean requireReaderOnline() {
        if (readerSession.getState().isConnected()) { return true; }
        showDisconnectDialog(readerSession.getLastUnexpectedReason());
        return false;
    }

    private void showDisconnectDialog(DisconnectReason reason) {
        if (isFinishing() || isDestroyed()
                || (disconnectDialog != null && disconnectDialog.isShowing())) { return; }
        MessageDialog.Builder builder = new MessageDialog.Builder(this)
                .setTitle(R.string.reader_disconnected_title)
                .setMessage(disconnectReasonText(reason))
                .setCancelable(false)
                .setCanceledOnTouchOutside(false)
                .setCancel(R.string.reader_goto_connect)
                .setConfirm(R.string.reader_acknowledged)
                .setListener(new MessageDialog.OnListener() {
                    @Override public void onConfirm(@NonNull BaseDialog dialog) {
                        acknowledgeDisconnectDialog();
                    }

                    @Override public void onCancel(@NonNull BaseDialog dialog) {
                        acknowledgeDisconnectDialog();
                        openReaderConfig();
                    }
                });
        disconnectDialog = builder;
        builder.show();
    }

    private void acknowledgeDisconnectDialog() {
        readerSession.acknowledgeDisconnect();
        disconnectDialog = null;
    }

    private void openReaderConfig() {
        if (this instanceof MainActivity homeActivity) {
            homeActivity.showReaderConfig();
        } else {
            MainActivity.start(this, ReaderConfigFragment.class);
        }
    }

    @StringRes
    public static int readerStatusText(ReaderConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> R.string.config_status_connected;
            case DISCONNECTED -> R.string.config_status_disconnected;
            case FAILED -> R.string.config_status_failed;
            case NOT_CONNECTED -> R.string.config_status_not_connected;
        };
    }

    public static int readerStatusBackground(ReaderConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> R.drawable.rfid_chip_green_bg;
            case DISCONNECTED, FAILED -> R.drawable.rfid_chip_red_bg;
            case NOT_CONNECTED -> R.drawable.rfid_chip_gray_bg;
        };
    }

    @StringRes
    public static int disconnectReasonText(DisconnectReason reason) {
        return switch (reason) {
            case LINK_LOST -> R.string.reader_disconnected_link_lost;
            case BLUETOOTH_OFF -> R.string.reader_disconnected_bluetooth_off;
            case WIFI_LOST -> R.string.reader_disconnected_wifi_lost;
            case SDK_ERROR -> R.string.reader_disconnected_sdk_error;
            default -> R.string.reader_offline_action_blocked;
        };
    }

    @Override
    protected void onDestroy() {
        readerSession.removeObserver(this);
        if (resumed.get() == this) { resumed.clear(); }
        if (disconnectDialog != null) { disconnectDialog.dismiss(); }
        disconnectDialog = null;
        super.onDestroy();
    }
}
