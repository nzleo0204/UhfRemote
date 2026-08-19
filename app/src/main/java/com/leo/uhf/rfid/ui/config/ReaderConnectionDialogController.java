package com.leo.uhf.rfid.ui.config;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.leo.uhf.rfid.sdk.model.ConnectionPhase;
import com.leo.uhf.rfid.sdk.connection.ReaderSessionManager;
import com.leo.uhf.rfid.sdk.model.ReaderState;
import com.leo.uhf.rfid.ui.connection.ConnectionDialog;
import com.leo.uhf.core.ui.dialog.WaitDialog;

/**
 * 管理连接进度与参数初始化进度弹窗。
 */
public final class ReaderConnectionDialogController {
    private static final String CONNECTION_TAG = "reader_connection";
    private static final String DEVICE_INFO_TAG = "reader_device_info";
    private static final String BLE_DEVICES_TAG = "ble_devices";

    private final Fragment fragment;
    private WaitDialog.Builder parameterUpdateDialog;

    public ReaderConnectionDialogController(Fragment fragment) { this.fragment = fragment; }

    public void render(ReaderState state, ReaderSessionManager session) {
        if (isConnecting(state.getPhase())) {
            showOrUpdateConnection(state, session);
        } else if (state.isInitializing()
                && state.getPhase() == ConnectionPhase.UPDATING_PARAMETERS) {
            dismissConnection();
            showOrUpdateParameterUpdate(state);
        } else if (state.getPhase() == ConnectionPhase.FAILED
                && !session.isConnectionFailureAcknowledged(state)) {
            dismissParameterUpdate();
            showOrUpdateConnection(state, session);
        } else {
            dismissConnection();
            dismissParameterUpdate();
        }
    }

    public void dismissAll() {
        dismissParameterUpdate();
        dismissConnection();
        dismissFragmentDialog(DEVICE_INFO_TAG);
        dismissFragmentDialog(BLE_DEVICES_TAG);
    }

    private void showOrUpdateParameterUpdate(ReaderState state) {
        if (parameterUpdateDialog == null || !parameterUpdateDialog.isShowing()) {
            parameterUpdateDialog = new WaitDialog.Builder(fragment.requireActivity())
                    .setMessage(state.getMessage());
            parameterUpdateDialog.show();
        } else {
            parameterUpdateDialog.setMessage(state.getMessage());
        }
    }

    private void dismissParameterUpdate() {
        if (parameterUpdateDialog != null && parameterUpdateDialog.isShowing()) {
            parameterUpdateDialog.dismiss();
        }
        parameterUpdateDialog = null;
    }

    private void showOrUpdateConnection(ReaderState state, ReaderSessionManager session) {
        ConnectionDialog dialog = (ConnectionDialog) fragment
                .getParentFragmentManager().findFragmentByTag(CONNECTION_TAG);
        if (dialog == null) {
            dialog = new ConnectionDialog();
            dialog.show(fragment.getParentFragmentManager(), CONNECTION_TAG);
        }
        dialog.setOnFailureDismissed(() -> session.acknowledgeConnectionFailure(state));
        dialog.update(state.getPhase(), state.getMessage(), state.getConnectionFailure());
    }

    private void dismissConnection() {
        Fragment existing = fragment.getParentFragmentManager()
                .findFragmentByTag(CONNECTION_TAG);
        if (existing instanceof ConnectionDialog dialog) {
            dialog.dismissAllowingStateLoss();
        }
    }

    private void dismissFragmentDialog(String tag) {
        Fragment existing = fragment.getParentFragmentManager().findFragmentByTag(tag);
        if (existing instanceof DialogFragment dialog) { dialog.dismissAllowingStateLoss(); }
    }

    private static boolean isConnecting(ConnectionPhase phase) {
        return phase == ConnectionPhase.CONNECTING || phase == ConnectionPhase.DISCOVERING_SERVICES
                || phase == ConnectionPhase.ENABLING_NOTIFICATIONS
                || phase == ConnectionPhase.CONNECTING_DATA_CHANNEL
                || phase == ConnectionPhase.VERIFYING_MODULE;
    }
}
