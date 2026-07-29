package com.leo.remote.ui.fragment.home;

import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.leo.remote.R;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.ConnectionPhase;
import com.leo.remote.reader.DisconnectReason;
import com.leo.remote.reader.MagicPowerLevels;
import com.leo.remote.reader.ModuleSubtype;
import com.leo.remote.reader.ReaderConfiguration;
import com.leo.remote.reader.ReaderConnectionStatus;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.TagProtocol;
import com.leo.remote.reader.TransportType;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.dialog.BleDeviceSheet;
import com.leo.remote.ui.dialog.ReaderConnectionDialog;
import com.leo.remote.ui.dialog.ReaderDeviceInfoDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** RFID reader connection and parameter configuration page. */
public final class ReaderConfigFragment extends AppFragment<HomeActivity> implements ReaderObserver {
    private static final String CONNECTION_DIALOG_TAG = "reader_connection";
    private static final String DEVICE_INFO_DIALOG_TAG = "reader_device_info";

    private ReaderSessionManager session;
    private TextView statusView;
    private TextView connectionTargetView;
    private TextView powerValueView;
    private TextView protocolView;
    private TextView workModeView;
    private TextView sessionView;
    private TextView blfView;
    private TextView qView;
    private TextView bleDeviceView;
    private EditText wifiAddressView;
    private SeekBar powerSeekBar;
    private View hardwareSection;
    private View protocolSection;
    private View rateSection;
    private View bleActions;
    private View wifiActions;
    private SwitchMaterial bleSwitch;
    private SwitchMaterial wifiSwitch;
    private ReaderConfiguration configuration;
    private ReaderState readerState = ReaderState.disconnected();
    private boolean bindingUi;

    public static ReaderConfigFragment newInstance() { return new ReaderConfigFragment(); }

    @Override
    protected int getLayoutId() { return R.layout.reader_config_fragment; }

    @Override
    protected void initView() {
        statusView = findViewById(R.id.tv_config_status);
        connectionTargetView = findViewById(R.id.tv_config_connection_target);
        powerValueView = findViewById(R.id.tv_config_power_value);
        protocolView = findViewById(R.id.tv_config_protocol);
        workModeView = findViewById(R.id.tv_config_work_mode);
        sessionView = findViewById(R.id.tv_config_session);
        blfView = findViewById(R.id.tv_config_blf);
        qView = findViewById(R.id.tv_config_q);
        bleDeviceView = findViewById(R.id.tv_config_ble_device);
        wifiAddressView = findViewById(R.id.et_config_wifi_ip);
        powerSeekBar = findViewById(R.id.sb_config_power);
        hardwareSection = findViewById(R.id.ll_config_hardware);
        protocolSection = findViewById(R.id.ll_config_protocol);
        rateSection = findViewById(R.id.ll_config_rate);
        bleActions = findViewById(R.id.ll_config_ble_actions);
        wifiActions = findViewById(R.id.ll_config_wifi_actions);
        bleSwitch = findViewById(R.id.sw_config_ble);
        wifiSwitch = findViewById(R.id.sw_config_wifi);

        bindingUi = true;
        bleSwitch.setChecked(true);
        wifiSwitch.setChecked(false);
        bindingUi = false;
        bindTransportRows(true, false);
        setHardwareEnabled(false);

        bleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingUi) { return; }
            if (!isChecked && !wifiSwitch.isChecked()) {
                buttonView.setChecked(true);
                return;
            }
            if (!isChecked) { return; }
            bindingUi = true;
            wifiSwitch.setChecked(false);
            bindingUi = false;
            bindTransportRows(true, false);
            if (session != null && session.getState().getTransport() != TransportType.NONE) {
                session.disconnect(DisconnectReason.TRANSPORT_SWITCH);
            }
        });
        wifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingUi) { return; }
            if (!isChecked && !bleSwitch.isChecked()) {
                buttonView.setChecked(true);
                return;
            }
            if (!isChecked) { return; }
            bindingUi = true;
            bleSwitch.setChecked(false);
            bindingUi = false;
            bindTransportRows(false, false);
            if (session != null && session.getState().getTransport() != TransportType.NONE) {
                session.disconnect(DisconnectReason.TRANSPORT_SWITCH);
            }
        });
        findViewById(R.id.row_config_ble).setOnClickListener(view -> bleSwitch.setChecked(true));
        findViewById(R.id.row_config_wifi).setOnClickListener(view -> wifiSwitch.setChecked(true));
        statusView.setOnClickListener(view -> showDeviceInfo());
        findViewById(R.id.btn_config_ble_scan).setOnClickListener(view -> showBleDevices());
        findViewById(R.id.btn_config_wifi_connect).setOnClickListener(view ->
                session.connectWifi(wifiAddressView.getText().toString()));
        findViewById(R.id.row_config_protocol).setOnClickListener(view -> showProtocolDialog());
        findViewById(R.id.row_config_work_mode).setOnClickListener(view -> showWorkModeDialog());
        findViewById(R.id.row_config_session).setOnClickListener(view -> showSessionDialog());
        findViewById(R.id.row_config_blf).setOnClickListener(view -> showBlfDialog());
        findViewById(R.id.row_config_q).setOnClickListener(view -> showQDialog());
        powerValueView.setOnClickListener(view -> {
            if (readerState.getModuleSubtype() == ModuleSubtype.MAGIC_RF) { showMagicPowerDialog(); }
        });

        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull SeekBar seekBar, int progress, boolean fromUser) {
                powerValueView.setText(getString(R.string.rfid_power_value, progress));
            }

            @Override public void onStartTrackingTouch(@NonNull SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(@NonNull SeekBar seekBar) {
                handleResult(session.setPower(seekBar.getProgress() * 10), R.string.config_power_set_failed);
            }
        });
    }

    @Override
    protected void initData() {
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        wifiAddressView.setText(session.getSavedWifiAddress());
        session.addObserver(this);
    }

    @Override
    public void onDestroy() {
        if (session != null) { session.removeObserver(this); }
        super.onDestroy();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        readerState = state;
        boolean connected = state.isConnected();
        ReaderConnectionStatus connectionStatus = state.getConnectionStatus();
        statusView.setText(statusText(connectionStatus));
        statusView.setBackgroundResource(statusBackground(connectionStatus));
        statusView.setEnabled(connected);
        bindConnectionTarget(state);
        if (state.getTransport() == TransportType.BLE) {
            bindingUi = true;
            bleSwitch.setChecked(true);
            bindingUi = false;
        } else if (state.getTransport() == TransportType.WIFI) {
            bindingUi = true;
            wifiSwitch.setChecked(true);
            bindingUi = false;
        }
        bindTransportRows(bleSwitch.isChecked(), connected);
        setHardwareEnabled(connected);
        if (state.getTransport() == TransportType.BLE && !state.getAddress().isEmpty()) {
            bleDeviceView.setText(getString(R.string.config_device_address,
                    state.getDeviceName().isEmpty() ? getString(R.string.config_unnamed_device)
                            : state.getDeviceName(), state.getAddress()));
        }

        if (isConnectionDialogPhase(state.getPhase())) {
            showOrUpdateConnectionDialog(state);
        } else {
            dismissConnectionDialog();
        }
        applyModuleUi(state.getModuleSubtype());
    }

    @Override
    public void onReaderConfigurationChanged(ReaderConfiguration value) {
        configuration = value;
        bindingUi = true;
        powerSeekBar.setProgress(value.powerTenthsDbm / 10);
        powerValueView.setText(formatPower(value.powerTenthsDbm));
        blfView.setText(blfLabel(value.blfProfile));
        sessionView.setText(getString(R.string.config_session_value, value.session));
        qView.setText(value.dynamicQ ? getString(R.string.config_adaptive)
                : getString(R.string.config_q_value, value.qValue));
        workModeView.setText(workModeLabel(value.inventoryMode));
        bindingUi = false;
    }

    private void bindTransportRows(boolean ble, boolean connected) {
        bleActions.setVisibility(!connected && ble ? View.VISIBLE : View.GONE);
        wifiActions.setVisibility(!connected && !ble ? View.VISIBLE : View.GONE);
    }

    private void showBleDevices() {
        BleDeviceSheet sheet = new BleDeviceSheet();
        sheet.setListener(device -> {
            bleDeviceView.setText(getString(R.string.config_device_address,
                    TextUtils.isEmpty(device.getName()) ? getString(R.string.config_unnamed_device)
                            : device.getName(), device.getAddress()));
            session.connectBle(device);
        });
        sheet.show(getParentFragmentManager(), "ble_devices");
    }

    private void showDeviceInfo() {
        if (!readerState.isConnected()) { return; }
        if (getParentFragmentManager().findFragmentByTag(DEVICE_INFO_DIALOG_TAG) == null) {
            new ReaderDeviceInfoDialog().show(getParentFragmentManager(), DEVICE_INFO_DIALOG_TAG);
        }
    }

    private void bindConnectionTarget(ReaderState state) {
        if (state.getAddress().isEmpty()) {
            connectionTargetView.setVisibility(View.GONE);
            connectionTargetView.setText("");
            return;
        }
        String target = state.getTransport() == TransportType.BLE
                ? (state.getDeviceName().isEmpty() ? state.getAddress()
                : getString(R.string.config_connection_target, state.getDeviceName(), state.getAddress()))
                : state.getAddress();
        connectionTargetView.setText(target);
        connectionTargetView.setVisibility(View.VISIBLE);
    }

    private void showProtocolDialog() {
        List<TagProtocol> protocols = new ArrayList<>(readerState.getModuleSubtype().supportedProtocols());
        String[] labels = protocols.stream().map(TagProtocol::getDisplayName).toArray(String[]::new);
        int selected = Math.max(0, protocols.indexOf(readerState.getProtocol()));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_protocol)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    handleResult(session.setProtocol(protocols.get(which)), R.string.config_protocol_set_failed);
                    dialog.dismiss();
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showWorkModeDialog() {
        String[] labels = getResources().getStringArray(R.array.config_work_mode_labels);
        int selected = configuration == null ? 1 : configuration.inventoryMode;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_work_mode)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    session.setInventoryMode(which);
                    workModeView.setText(workModeLabel(which));
                    dialog.dismiss();
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showSessionDialog() {
        String[] labels = getResources().getStringArray(R.array.config_session_labels);
        int selected = configuration == null ? 0 : configuration.session * 2 + configuration.target;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_session_target)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    handleResult(session.setSessionTarget(which / 2, which % 2), R.string.config_query_set_failed);
                    dialog.dismiss();
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showBlfDialog() {
        String[] labels = getResources().getStringArray(R.array.config_blf_labels);
        int selected = configuration == null ? 1 : configuration.blfProfile;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_blf_rate)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    handleResult(session.setBlf(which), R.string.config_blf_set_failed);
                    dialog.dismiss();
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showQDialog() {
        String[] labels = new String[32];
        for (int i = 0; i < 16; i++) {
            labels[i] = getString(R.string.config_fixed_q, i);
            labels[16 + i] = getString(R.string.config_dynamic_q, i);
        }
        if (readerState.getModuleSubtype() == ModuleSubtype.MAGIC_RF) {
            labels = new String[16];
            for (int i = 0; i < 16; i++) { labels[i] = getString(R.string.config_q_value, i); }
        }
        String[] choices = labels;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_q_parameter)
                .setSingleChoiceItems(choices, -1, (dialog, which) -> {
                    boolean dynamic = choices.length == 32 && which >= 16;
                    handleResult(session.setQ(dynamic, which % 16), R.string.config_q_set_failed);
                    dialog.dismiss();
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showMagicPowerDialog() {
        int[] levels = MagicPowerLevels.forModule(readerState.getModuleSerial(),
                readerState.getModuleVersion());
        String[] values = new String[levels.length];
        for (int i = 0; i < values.length; i++) { values[i] = formatPower(levels[i]); }
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_magic_power)
                .setSingleChoiceItems(values, -1, (dialog, which) -> {
                    handleResult(session.setPower(levels[which]), R.string.config_power_set_failed);
                    dialog.dismiss();
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void applyModuleUi(ModuleSubtype subtype) {
        boolean magic = subtype == ModuleSubtype.MAGIC_RF;
        findViewById(R.id.row_config_blf).setVisibility(magic ? View.GONE : View.VISIBLE);
        powerSeekBar.setVisibility(magic ? View.GONE : View.VISIBLE);
        findViewById(R.id.row_config_work_mode).setEnabled(!magic && readerState.isConnected());
        if (magic) {
            workModeView.setText(workModeLabel(1));
        }
        protocolView.setText(readerState.getProtocol().getDisplayName());
    }

    private void setHardwareEnabled(boolean enabled) {
        setEnabledRecursive(hardwareSection, enabled);
        setEnabledRecursive(protocolSection, enabled);
        setEnabledRecursive(rateSection, enabled);
        float alpha = enabled ? 1f : 0.45f;
        hardwareSection.setAlpha(alpha);
        protocolSection.setAlpha(alpha);
        rateSection.setAlpha(alpha);
    }

    private static void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabledRecursive(group.getChildAt(i), enabled);
            }
        }
    }

    private void handleResult(CompletableFuture<Integer> future, @StringRes int failureMessage) {
        future.whenComplete((status, error) -> requireActivity().runOnUiThread(() -> {
            if (error != null) { toast(error.getCause() == null ? error.getMessage() : error.getCause().getMessage()); }
            else if (status != 0) { toast(getString(R.string.config_error_code, getString(failureMessage), status)); }
        }));
    }

    private void showOrUpdateConnectionDialog(ReaderState state) {
        ReaderConnectionDialog dialog = (ReaderConnectionDialog) getParentFragmentManager()
                .findFragmentByTag(CONNECTION_DIALOG_TAG);
        if (dialog == null) {
            dialog = new ReaderConnectionDialog();
            dialog.show(getParentFragmentManager(), CONNECTION_DIALOG_TAG);
        }
        dialog.update(state.getPhase(), state.getMessage(), state.getErrorCode());
    }

    private void dismissConnectionDialog() {
        ReaderConnectionDialog dialog = (ReaderConnectionDialog) getParentFragmentManager()
                .findFragmentByTag(CONNECTION_DIALOG_TAG);
        if (dialog != null) { dialog.dismissAllowingStateLoss(); }
    }

    private static boolean isConnectionDialogPhase(ConnectionPhase phase) {
        return phase == ConnectionPhase.CONNECTING || phase == ConnectionPhase.DISCOVERING_SERVICES
                || phase == ConnectionPhase.ENABLING_NOTIFICATIONS
                || phase == ConnectionPhase.CONNECTING_DATA_CHANNEL
                || phase == ConnectionPhase.VERIFYING_MODULE || phase == ConnectionPhase.CONNECTED
                || phase == ConnectionPhase.FAILED || phase == ConnectionPhase.DISCONNECTING;
    }

    @StringRes
    private static int statusText(ReaderConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> R.string.config_status_connected;
            case DISCONNECTED -> R.string.config_status_disconnected;
            case FAILED -> R.string.config_status_failed;
            case NOT_CONNECTED -> R.string.config_status_not_connected;
        };
    }

    private static int statusBackground(ReaderConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> R.drawable.rfid_chip_green_bg;
            case DISCONNECTED, FAILED -> R.drawable.rfid_chip_red_bg;
            case NOT_CONNECTED -> R.drawable.rfid_chip_gray_bg;
        };
    }

    private String blfLabel(int profile) {
        return switch (profile) {
            case 0 -> getString(R.string.config_blf_40);
            case 1 -> getString(R.string.config_blf_256);
            case 2 -> getString(R.string.config_blf_300);
            case 3 -> getString(R.string.config_blf_400);
            default -> getString(R.string.config_unknown_value, profile);
        };
    }

    private String workModeLabel(int mode) {
        return switch (mode) {
            case 0 -> getString(R.string.config_work_mode_single);
            case 2 -> getString(R.string.config_work_mode_low_power);
            default -> getString(R.string.config_work_mode_continuous);
        };
    }

    private String formatPower(int tenthsDbm) {
        return tenthsDbm % 10 == 0 ? getString(R.string.rfid_power_value, tenthsDbm / 10)
                : getString(R.string.config_power_decimal, tenthsDbm / 10,
                Math.abs(tenthsDbm % 10));
    }
}
