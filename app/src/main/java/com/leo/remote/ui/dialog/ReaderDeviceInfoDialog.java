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
import com.leo.remote.reader.ModuleSubtype;
import com.leo.remote.reader.ReaderConfiguration;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.Rm610PowerLevels;
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

        // 设置关闭按钮点击事件
        view.findViewById(R.id.btn_device_info_close).setOnClickListener(v -> dismiss());

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
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
        ((TextView) view.findViewById(R.id.tv_device_info_address_label)).setText(getString(ble
                ? R.string.device_info_ble_address : R.string.device_info_wifi_address));
        set(view, R.id.tv_device_info_address, state.getAddress());
        set(view, R.id.tv_device_info_board_serial, state.getBoardSerial());
        set(view, R.id.tv_device_info_board_version, state.getBoardVersion());
        set(view, R.id.tv_device_info_subtype, state.getModuleSubtype().getDisplayName());
        set(view, R.id.tv_device_info_module_serial, state.getModuleSerial());
        set(view, R.id.tv_device_info_module_version, state.getModuleVersion());

        // 设置射频协议和发射功率
        set(view, R.id.tv_device_info_rf_protocol, state.getProtocol().getDisplayName());
        // 功率从配置缓存中获取
        ReaderConfiguration config = session.getConfiguration();
        if (config != null && config.powerTenthsDbm >= 0) {
            // 判断是否为非 CMT 的 RM610
            ModuleSubtype subtype = state.getModuleSubtype();
            boolean rm610NonCmt = subtype == ModuleSubtype.RM610
                    && !Rm610PowerLevels.isCmtVersion(state.getModuleSerial());

            if (rm610NonCmt) {
                // 非 CMT 的 RM610：powerTenthsDbm 是索引值
                set(view, R.id.tv_device_info_rf_power,
                    Rm610PowerLevels.formatNonCmt(config.powerTenthsDbm));
            } else {
                // 其他模块：powerTenthsDbm 是十分之一 dBm
                double powerDbm = config.powerTenthsDbm / 10.0;
                set(view, R.id.tv_device_info_rf_power,
                    String.format("%.1f dBm", powerDbm));
            }
        } else {
            set(view, R.id.tv_device_info_rf_power, "--");
        }
    }

    private static void set(View view, int id, String value) {
        ((TextView) view.findViewById(id)).setText(value == null || value.trim().isEmpty() ? "--" : value);
    }
}
