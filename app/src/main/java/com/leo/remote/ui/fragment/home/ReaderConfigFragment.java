package com.leo.remote.ui.fragment.home;

import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private View deviceInfoButton;
    private MaterialButtonToggleGroup transportGroup;
    private MaterialButtonToggleGroup workModeGroup;
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
        deviceInfoButton = findViewById(R.id.btn_config_device_info);
        transportGroup = findViewById(R.id.tg_config_transport);
        workModeGroup = findViewById(R.id.tg_config_work_mode);

        bindingUi = true;
        transportGroup.check(R.id.btn_config_ble);
        workModeGroup.check(R.id.btn_work_fast);
        bindingUi = false;
        setHardwareEnabled(false);

        transportGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || bindingUi) { return; }
            boolean ble = checkedId == R.id.btn_config_ble;
            bleActions.setVisibility(ble ? View.VISIBLE : View.GONE);
            wifiActions.setVisibility(ble ? View.GONE : View.VISIBLE);
            if (session != null && session.getState().getTransport() != TransportType.NONE) {
                session.disconnect(DisconnectReason.TRANSPORT_SWITCH);
            }
        });
        deviceInfoButton.setOnClickListener(view -> showDeviceInfo());
        findViewById(R.id.btn_config_ble_scan).setOnClickListener(view -> showBleDevices());
        findViewById(R.id.btn_config_wifi_connect).setOnClickListener(view ->
                session.connectWifi(wifiAddressView.getText().toString()));
        findViewById(R.id.row_config_protocol).setOnClickListener(view -> showProtocolDialog());
        findViewById(R.id.row_config_session).setOnClickListener(view -> showSessionDialog());
        findViewById(R.id.row_config_blf).setOnClickListener(view -> showBlfDialog());
        findViewById(R.id.row_config_q).setOnClickListener(view -> showQDialog());
        powerValueView.setOnClickListener(view -> {
            if (readerState.getModuleSubtype() == ModuleSubtype.MAGIC_RF) { showMagicPowerDialog(); }
        });

        workModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || bindingUi || session == null) { return; }
            int mode = checkedId == R.id.btn_work_single ? 0
                    : checkedId == R.id.btn_work_low_power ? 2 : 1;
            session.setInventoryMode(mode);
        });
        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull SeekBar seekBar, int progress, boolean fromUser) {
                powerValueView.setText(getString(R.string.rfid_power_value, progress));
            }

            @Override public void onStartTrackingTouch(@NonNull SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(@NonNull SeekBar seekBar) {
                handleResult(session.setPower(seekBar.getProgress() * 10), "功率设置失败");
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
        deviceInfoButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        deviceInfoButton.setEnabled(connected);
        bindConnectionTarget(state);
        setHardwareEnabled(connected);
        if (state.getTransport() == TransportType.BLE && !state.getAddress().isEmpty()) {
            bleDeviceView.setText((state.getDeviceName().isEmpty() ? "未命名设备" : state.getDeviceName())
                    + "\n" + state.getAddress());
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
        sessionView.setText("S" + value.session + " / " + (value.target == 0 ? "A" : "B"));
        qView.setText(value.dynamicQ ? "动态 Q" + value.qValue : "固定 Q" + value.qValue);
        workModeGroup.check(value.inventoryMode == 0 ? R.id.btn_work_single
                : value.inventoryMode == 2 ? R.id.btn_work_low_power : R.id.btn_work_fast);
        bindingUi = false;
    }

    private void showBleDevices() {
        BleDeviceSheet sheet = new BleDeviceSheet();
        sheet.setListener(device -> {
            bleDeviceView.setText((TextUtils.isEmpty(device.getName()) ? "未命名设备" : device.getName())
                    + "\n" + device.getAddress());
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
                : state.getDeviceName() + " · " + state.getAddress())
                : state.getAddress();
        connectionTargetView.setText(target);
        connectionTargetView.setVisibility(View.VISIBLE);
    }

    private void showProtocolDialog() {
        List<TagProtocol> protocols = new ArrayList<>(readerState.getModuleSubtype().supportedProtocols());
        String[] labels = protocols.stream().map(TagProtocol::getDisplayName).toArray(String[]::new);
        int selected = Math.max(0, protocols.indexOf(readerState.getProtocol()));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("射频协议")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    handleResult(session.setProtocol(protocols.get(which)), "协议切换失败");
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
    }

    private void showSessionDialog() {
        String[] labels = {"S0 / A", "S0 / B", "S1 / A", "S1 / B", "S2 / A", "S2 / B", "S3 / A", "S3 / B"};
        int selected = configuration == null ? 0 : configuration.session * 2 + configuration.target;
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Session / Target")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    handleResult(session.setSessionTarget(which / 2, which % 2), "Query 参数设置失败");
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
    }

    private void showBlfDialog() {
        String[] labels = {"40 kHz", "250 kHz", "300 kHz", "400 kHz"};
        int selected = configuration == null ? 1 : configuration.blfProfile;
        new MaterialAlertDialogBuilder(requireContext()).setTitle("BLF 速率")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    handleResult(session.setBlf(which), "BLF 设置失败");
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
    }

    private void showQDialog() {
        String[] labels = new String[32];
        for (int i = 0; i < 16; i++) {
            labels[i] = "固定 Q" + i;
            labels[16 + i] = "动态 Q" + i;
        }
        if (readerState.getModuleSubtype() == ModuleSubtype.MAGIC_RF) {
            labels = new String[16];
            for (int i = 0; i < 16; i++) { labels[i] = "Q" + i; }
        }
        String[] choices = labels;
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Q 参数")
                .setSingleChoiceItems(choices, -1, (dialog, which) -> {
                    boolean dynamic = choices.length == 32 && which >= 16;
                    handleResult(session.setQ(dynamic, which % 16), "Q 参数设置失败");
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
    }

    private void showMagicPowerDialog() {
        int[] levels = MagicPowerLevels.forModule(readerState.getModuleSerial(),
                readerState.getModuleVersion());
        String[] values = new String[levels.length];
        for (int i = 0; i < values.length; i++) { values[i] = formatPower(levels[i]); }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("MagicRF 功率")
                .setSingleChoiceItems(values, -1, (dialog, which) -> {
                    handleResult(session.setPower(levels[which]), "功率设置失败");
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
    }

    private void applyModuleUi(ModuleSubtype subtype) {
        boolean magic = subtype == ModuleSubtype.MAGIC_RF;
        findViewById(R.id.row_config_blf).setVisibility(magic ? View.GONE : View.VISIBLE);
        powerSeekBar.setVisibility(magic ? View.GONE : View.VISIBLE);
        workModeGroup.setEnabled(!magic && readerState.isConnected());
        if (magic) {
            bindingUi = true;
            workModeGroup.check(R.id.btn_work_fast);
            bindingUi = false;
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

    private void handleResult(CompletableFuture<Integer> future, String failureMessage) {
        future.whenComplete((status, error) -> requireActivity().runOnUiThread(() -> {
            if (error != null) { toast(error.getCause() == null ? error.getMessage() : error.getCause().getMessage()); }
            else if (status != 0) { toast(failureMessage + "（错误码 " + status + "）"); }
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

    private static String statusText(ReaderConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> "● 已连接";
            case DISCONNECTED -> "● 已断开";
            case FAILED -> "● 连接失败";
            case NOT_CONNECTED -> "● 未连接";
        };
    }

    private static int statusBackground(ReaderConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> R.drawable.rfid_chip_green_bg;
            case DISCONNECTED, FAILED -> R.drawable.rfid_chip_red_bg;
            case NOT_CONNECTED -> R.drawable.rfid_chip_gray_bg;
        };
    }

    private static String blfLabel(int profile) {
        return switch (profile) {
            case 0 -> "40 kHz";
            case 1 -> "250 kHz";
            case 2 -> "300 kHz";
            case 3 -> "400 kHz";
            default -> "未知 (" + profile + ")";
        };
    }

    private static String formatPower(int tenthsDbm) {
        return tenthsDbm % 10 == 0 ? (tenthsDbm / 10) + " dBm"
                : (tenthsDbm / 10) + "." + Math.abs(tenthsDbm % 10) + " dBm";
    }
}
