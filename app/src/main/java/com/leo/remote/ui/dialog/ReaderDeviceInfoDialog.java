package com.leo.remote.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.leo.remote.R;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.TransportType;

public final class ReaderDeviceInfoDialog extends DialogFragment implements ReaderObserver {
    private ReaderSessionManager session;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.reader_device_info_dialog,
                new FrameLayout(requireContext()), false);
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        bind(view, session.getState());
        return new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .setPositiveButton(R.string.device_info_close, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (session != null) { session.addObserver(this); }
    }

    @Override
    public void onStop() {
        if (session != null) { session.removeObserver(this); }
        super.onStop();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        if (!state.isConnected()) { dismissAllowingStateLoss(); }
    }

    private void bind(View view, ReaderState state) {
        boolean ble = state.getTransport() == TransportType.BLE;
        ((ImageView) view.findViewById(R.id.iv_device_info_icon)).setImageResource(
                ble ? R.drawable.rfid_bluetooth_ic : R.drawable.rfid_wifi_ic);
        set(view, R.id.tv_device_info_name,
                ble ? state.getDeviceName() : getString(R.string.device_info_name_wifi));
        set(view, R.id.tv_device_info_protocol, state.getProtocol().getDisplayName());
        set(view, R.id.tv_device_info_transport, getString(ble
                ? R.string.device_info_transport_ble : R.string.device_info_transport_wifi));
        set(view, R.id.tv_device_info_address, state.getAddress());
        set(view, R.id.tv_device_info_board_serial, state.getBoardSerial());
        set(view, R.id.tv_device_info_board_version, state.getBoardVersion());
        set(view, R.id.tv_device_info_subtype, state.getModuleSubtype().getDisplayName());
        set(view, R.id.tv_device_info_module_serial, state.getModuleSerial());
        set(view, R.id.tv_device_info_module_version, state.getModuleVersion());
    }

    private static void set(View view, int id, String value) {
        ((TextView) view.findViewById(id)).setText(value == null || value.trim().isEmpty() ? "--" : value);
    }
}
