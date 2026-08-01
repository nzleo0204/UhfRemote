package com.leo.remote.ui.fragment.home;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.PermissionLists;
import com.leo.remote.R;
import com.leo.remote.app.AppActivity;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.ConnectionPhase;
import com.leo.remote.reader.DisconnectReason;
import com.leo.remote.reader.MagicPowerLevels;
import com.leo.remote.reader.ModuleSubtype;
import com.leo.remote.reader.ReaderConfiguration;
import com.leo.remote.reader.ReaderConnectionStatus;
import com.leo.remote.reader.ReaderException;
import com.leo.remote.reader.ReaderObserver;
import com.leo.remote.reader.ReaderSessionManager;
import com.leo.remote.reader.ReaderState;
import com.leo.remote.reader.TagProtocol;
import com.leo.remote.reader.TransportType;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.dialog.BleDeviceSheet;
import com.leo.remote.ui.dialog.ReaderConnectionDialog;
import com.leo.remote.ui.dialog.ReaderDeviceInfoDialog;
import com.leo.remote.ui.dialog.common.MessageDialog;
import com.leo.remote.ui.dialog.common.WaitDialog;
import com.leo.remote.ui.view.IpAddressInputView;
import com.hjq.base.BaseDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** RFID reader connection and parameter configuration page. */
@SuppressLint("LogNotTimber")
public final class ReaderConfigFragment extends AppFragment<HomeActivity> implements ReaderObserver {
    private static final String TAG = "UhfReader/Config";
    private static final String CONNECTION_DIALOG_TAG = "reader_connection";
    private static final String DEVICE_INFO_DIALOG_TAG = "reader_device_info";

    private ReaderSessionManager session;
    private TextView statusView;
    private TextView connectionTargetView;
    private TextView disconnectReasonView;
    private TextView powerValueView;
    private TextView protocolView;
    private TextView workModeView;
    private TextView sessionView;
    private TextView blfView;
    private TextView qView;
    private TextView bleDeviceView;
    private View deviceInfoButton;
    private IpAddressInputView wifiAddressView;
    private SeekBar powerSeekBar;
    private View hardwareSection;
    private View protocolSection;
    private View rateSection;
    private View bleActions;
    private View wifiActions;
    private View workModeRow;
    private View blfRow;
    private View configRoot;
    private ScrollView configScroll;
    private View inputGuardTop;
    private View inputGuardBottom;
    private SwitchMaterial bleSwitch;
    private SwitchMaterial wifiSwitch;
    private ReaderConfiguration configuration;
    private ReaderState readerState = ReaderState.disconnected();
    private boolean bindingUi;
    private boolean readerStateInitialized;
    private boolean pendingConfigInit;
    private boolean configInitReady;
    private boolean connectionFailureDialogDismissed;
    private long configInitShownAt;
    private WaitDialog.Builder configInitDialog;
    private WaitDialog.Builder settingWaitDialog;
    private final Runnable showConfigInitAction = this::showConfigInitialization;
    private final Runnable completeConfigInitAction = this::completeConfigInitialization;
    private int powerProgressBeforeDrag;
    private int imeInsetBottom;
    private int configScrollBaseBottomPadding;

    public static ReaderConfigFragment newInstance() { return new ReaderConfigFragment(); }

    @Override
    protected int getLayoutId() { return R.layout.reader_config_fragment; }

    @Override
    protected void initView() {
        statusView = findViewById(R.id.tv_config_status);
        connectionTargetView = findViewById(R.id.tv_config_connection_target);
        disconnectReasonView = findViewById(R.id.tv_config_disconnect_reason);
        powerValueView = findViewById(R.id.tv_config_power_value);
        protocolView = findViewById(R.id.tv_config_protocol);
        workModeView = findViewById(R.id.tv_config_work_mode);
        sessionView = findViewById(R.id.tv_config_session);
        blfView = findViewById(R.id.tv_config_blf);
        qView = findViewById(R.id.tv_config_q);
        bleDeviceView = findViewById(R.id.tv_config_ble_device);
        deviceInfoButton = findViewById(R.id.ibtn_config_device_info);
        wifiAddressView = findViewById(R.id.et_config_wifi_ip);
        powerSeekBar = findViewById(R.id.sb_config_power);
        hardwareSection = findViewById(R.id.ll_config_hardware);
        protocolSection = findViewById(R.id.ll_config_protocol);
        rateSection = findViewById(R.id.ll_config_rate);
        bleActions = findViewById(R.id.ll_config_ble_actions);
        wifiActions = findViewById(R.id.ll_config_wifi_actions);
        workModeRow = findViewById(R.id.row_config_work_mode);
        blfRow = findViewById(R.id.row_config_blf);
        configRoot = findViewById(R.id.fl_config_root);
        configScroll = findViewById(R.id.sv_config_root);
        inputGuardTop = findViewById(R.id.v_config_input_guard_top);
        inputGuardBottom = findViewById(R.id.v_config_input_guard_bottom);
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
            dismissWifiKeyboard();
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
        deviceInfoButton.setOnClickListener(view -> showDeviceInfo());
        findViewById(R.id.btn_config_ble_scan).setOnClickListener(view -> showBleDevices());
        findViewById(R.id.btn_config_wifi_connect).setOnClickListener(view -> connectWifi());
        wifiAddressView.setOnDoneAction(this::connectWifi);
        wifiAddressView.setOnEditingChangedListener(editing -> {
            if (editing) {
                showWifiInputGuard();
            } else {
                hideWifiInputGuard();
            }
        });
        inputGuardTop.setOnClickListener(view -> dismissWifiKeyboard());
        inputGuardBottom.setOnClickListener(view -> dismissWifiKeyboard());
        configScrollBaseBottomPadding = configScroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(configRoot, (view, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            imeInsetBottom = imeInsets.bottom;
            configScroll.setPadding(configScroll.getPaddingLeft(), configScroll.getPaddingTop(),
                    configScroll.getPaddingRight(), configScrollBaseBottomPadding + imeInsetBottom);
            if (wifiAddressView.hasInputFocus()) {
                configScroll.post(this::keepWifiInputAboveKeyboard);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(configRoot);
        configRoot.addOnLayoutChangeListener((view, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) -> {
            if (wifiAddressView.hasInputFocus()) {
                updateWifiInputGuardBounds();
            }
        });
        findViewById(R.id.row_config_protocol).setOnClickListener(view -> showProtocolDialog());
        workModeRow.setOnClickListener(view -> showWorkModeDialog());
        findViewById(R.id.row_config_session).setOnClickListener(view -> showSessionDialog());
        blfRow.setOnClickListener(view -> showBlfDialog());
        findViewById(R.id.row_config_q).setOnClickListener(view -> showQDialog());
        powerValueView.setOnClickListener(view -> {
            if (readerState.getModuleSubtype() == ModuleSubtype.MAGIC_RF) { showMagicPowerDialog(); }
        });

        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(@NonNull SeekBar seekBar, int progress, boolean fromUser) {
                powerValueView.setText(getString(R.string.rfid_power_value, progress));
            }

            @Override
            public void onStartTrackingTouch(@NonNull SeekBar seekBar) {
                powerProgressBeforeDrag = seekBar.getProgress();
            }

            @Override
            public void onStopTrackingTouch(@NonNull SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (progress == powerProgressBeforeDrag) { return; }
                if (!requireReaderOnline()) {
                    restorePowerProgress(powerProgressBeforeDrag);
                    return;
                }
                confirmAndApply(R.string.config_transmit_power,
                        getString(R.string.rfid_power_value, progress),
                        () -> session.setPower(progress * 10),
                        R.string.config_power_set_failed,
                        () -> restorePowerProgress(powerProgressBeforeDrag));
            }
        });
    }

    @Override
    protected void initData() {
        session = ReaderSessionManager.getInstance(requireActivity().getApplication());
        session.addObserver(this);
    }

    @Override
    public void onDestroy() {
        cancelConfigInitialization();
        dismissSettingWaitDialog();
        if (session != null) { session.removeObserver(this); }
        super.onDestroy();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        boolean wasConnected = readerState.isConnected();
        boolean hadInitialState = readerStateInitialized;
        readerState = state;
        readerStateInitialized = true;
        boolean connected = state.isConnected();
        ReaderConnectionStatus connectionStatus = state.getConnectionStatus();
        statusView.setText(AppActivity.readerStatusText(connectionStatus));
        statusView.setBackgroundResource(AppActivity.readerStatusBackground(connectionStatus));
        statusView.setTextColor(ContextCompat.getColor(requireContext(),
                connectionStatus == ReaderConnectionStatus.CONNECTED
                        || connectionStatus == ReaderConnectionStatus.DISCONNECTED
                        || connectionStatus == ReaderConnectionStatus.FAILED
                        ? R.color.white : R.color.rfid_text));
        deviceInfoButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        if (!connected && state.getDisconnectReason().isUnexpected()) {
            disconnectReasonView.setText(AppActivity.disconnectReasonText(state.getDisconnectReason()));
            disconnectReasonView.setVisibility(View.VISIBLE);
        } else {
            disconnectReasonView.setVisibility(View.GONE);
        }
        bindConnectionTarget(state);
        if (state.getTransport() == TransportType.BLE) {
            bindingUi = true;
            bleSwitch.setChecked(true);
            wifiSwitch.setChecked(false);
            bindingUi = false;
        } else if (state.getTransport() == TransportType.WIFI) {
            bindingUi = true;
            bleSwitch.setChecked(false);
            wifiSwitch.setChecked(true);
            bindingUi = false;
        }
        bindTransportRows(bleSwitch.isChecked(), connected);
        setHardwareEnabled(connected);
        if (state.getTransport() == TransportType.BLE && !state.getAddress().isEmpty()) {
            bleDeviceView.setText(bleDisplayName(state.getDeviceName()));
        }

        if (isConnectingPhase(state.getPhase())) {
            connectionFailureDialogDismissed = false;
            showOrUpdateConnectionDialog(state);
        } else if (state.getPhase() == ConnectionPhase.FAILED
                && !connectionFailureDialogDismissed) {
            showOrUpdateConnectionDialog(state);
        } else {
            dismissConnectionDialog();
        }
        applyModuleUi(state.getModuleSubtype());
        if (hadInitialState && !wasConnected && connected
                && state.getPhase() == ConnectionPhase.CONNECTED) {
            beginConfigInitialization();
        } else if (!connected) {
            cancelConfigInitialization();
        }
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
        workModeView.setText(readerState.getModuleSubtype() == ModuleSubtype.MAGIC_RF
                ? workModeLabel(1) : workModeLabel(value.inventoryMode));
        bindingUi = false;
        if (pendingConfigInit) {
            configInitReady = true;
            finishConfigInitializationWhenReady();
        }
    }

    private void beginConfigInitialization() {
        if (pendingConfigInit) { return; }
        pendingConfigInit = true;
        configInitReady = false;
        configRoot.removeCallbacks(showConfigInitAction);
        configRoot.postDelayed(showConfigInitAction, 1050);
    }

    /** Starts only after the connection-success dialog has completed its one-second result state. */
    private void showConfigInitialization() {
        if (!pendingConfigInit || !readerState.isConnected() || getAttachActivity() == null) { return; }
        dismissConnectionDialog();
        configInitDialog = new WaitDialog.Builder(requireActivity())
                .setMessage(R.string.config_initializing);
        configInitDialog.show();
        configInitShownAt = SystemClock.elapsedRealtime();
        finishConfigInitializationWhenReady();
    }

    private void finishConfigInitializationWhenReady() {
        if (!pendingConfigInit || !configInitReady || configInitDialog == null
                || !configInitDialog.isShowing()) {
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - configInitShownAt;
        configRoot.removeCallbacks(completeConfigInitAction);
        configRoot.postDelayed(completeConfigInitAction, Math.max(0, 600 - elapsed));
    }

    private void completeConfigInitialization() {
        if (configInitDialog != null && configInitDialog.isShowing()) { configInitDialog.dismiss(); }
        configInitDialog = null;
        pendingConfigInit = false;
        configInitReady = false;
    }

    private void cancelConfigInitialization() {
        if (configRoot != null) {
            configRoot.removeCallbacks(showConfigInitAction);
            configRoot.removeCallbacks(completeConfigInitAction);
        }
        if (configInitDialog != null && configInitDialog.isShowing()) { configInitDialog.dismiss(); }
        configInitDialog = null;
        pendingConfigInit = false;
        configInitReady = false;
    }

    private void bindTransportRows(boolean ble, boolean connected) {
        bleActions.setVisibility(!connected && ble ? View.VISIBLE : View.GONE);
        wifiActions.setVisibility(!connected && !ble ? View.VISIBLE : View.GONE);
    }

    private void showBleDevices() {
        BleDeviceSheet sheet = new BleDeviceSheet();
        sheet.setListener(device -> {
            bleDeviceView.setText(bleDisplayName(device.getName()));
            requestNotificationPermission(() -> session.connectBle(device));
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
                ? bleDisplayName(state.getDeviceName()) : state.getAddress();
        connectionTargetView.setText(target);
        connectionTargetView.setVisibility(View.VISIBLE);
    }

    private String bleDisplayName(String name) {
        return TextUtils.isEmpty(name) ? getString(R.string.config_unnamed_device) : name;
    }

    private void connectWifi() {
        String address = wifiAddressView.getAddress();
        if (!ReaderSessionManager.isValidIpv4(address)) {
            wifiAddressView.setError(getString(R.string.config_reader_ip_invalid));
            return;
        }
        wifiAddressView.clearError();
        dismissWifiKeyboard();
        requestNotificationPermission(() -> session.connectWifi(address));
    }

    private void requestNotificationPermission(Runnable connectAction) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            connectAction.run();
            return;
        }
        XXPermissions.with(this).permission(PermissionLists.getPostNotificationsPermission())
                .request((grantedList, deniedList) -> {
                    if (!deniedList.isEmpty()) {
                        Log.w(TAG, "POST_NOTIFICATIONS denied; foreground notification may be hidden");
                        toast(R.string.reader_notification_permission_denied);
                    }
                    connectAction.run();
                });
    }

    private void dismissWifiKeyboard() {
        View focusedInput = wifiAddressView.getFocusedInput();
        hideKeyboard(focusedInput == null ? wifiAddressView : focusedInput);
        wifiAddressView.clearInputFocus();
        hideWifiInputGuard();
    }

    private void showWifiInputGuard() {
        wifiAddressView.postDelayed(() -> {
            Rect inputBounds = new Rect();
            wifiAddressView.getDrawingRect(inputBounds);
            wifiAddressView.requestRectangleOnScreen(inputBounds, false);
            keepWifiInputAboveKeyboard();
        }, 120);
    }

    private void keepWifiInputAboveKeyboard() {
        if (!wifiAddressView.hasInputFocus()) {
            return;
        }
        int[] rootLocation = new int[2];
        int[] rowLocation = new int[2];
        configRoot.getLocationOnScreen(rootLocation);
        wifiActions.getLocationOnScreen(rowLocation);
        int spacing = getResources().getDimensionPixelSize(R.dimen.dp_16);
        int visibleBottom = rootLocation[1] + configRoot.getHeight() - imeInsetBottom - spacing;
        int rowBottom = rowLocation[1] + wifiActions.getHeight();
        if (rowBottom > visibleBottom) {
            configScroll.smoothScrollBy(0, rowBottom - visibleBottom);
            configScroll.post(this::updateWifiInputGuardBounds);
        } else {
            updateWifiInputGuardBounds();
        }
    }

    private void updateWifiInputGuardBounds() {
        int[] rootLocation = new int[2];
        int[] rowLocation = new int[2];
        configRoot.getLocationOnScreen(rootLocation);
        wifiActions.getLocationOnScreen(rowLocation);
        int rowTop = Math.min(configRoot.getHeight(),
                Math.max(0, rowLocation[1] - rootLocation[1]));
        int rowBottom = Math.min(configRoot.getHeight(), rowTop + wifiActions.getHeight());

        FrameLayout.LayoutParams topParams = (FrameLayout.LayoutParams) inputGuardTop.getLayoutParams();
        topParams.height = rowTop;
        inputGuardTop.setLayoutParams(topParams);

        FrameLayout.LayoutParams bottomParams =
                (FrameLayout.LayoutParams) inputGuardBottom.getLayoutParams();
        bottomParams.topMargin = rowBottom;
        bottomParams.height = Math.max(0, configRoot.getHeight() - rowBottom);
        inputGuardBottom.setLayoutParams(bottomParams);
        inputGuardTop.setVisibility(View.VISIBLE);
        inputGuardBottom.setVisibility(View.VISIBLE);
    }

    private void hideWifiInputGuard() {
        inputGuardTop.setVisibility(View.GONE);
        inputGuardBottom.setVisibility(View.GONE);
    }

    private void showProtocolDialog() {
        if (!requireReaderOnline()) { return; }
        List<TagProtocol> protocols = new ArrayList<>(readerState.getModuleSubtype().supportedProtocols());
        String[] labels = protocols.stream().map(TagProtocol::getDisplayName).toArray(String[]::new);
        int selected = Math.max(0, protocols.indexOf(readerState.getProtocol()));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_protocol)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_protocol, labels[which],
                            () -> session.setProtocol(protocols.get(which)),
                            R.string.config_protocol_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showWorkModeDialog() {
        if (!requireReaderOnline()) { return; }
        String[] labels = getResources().getStringArray(R.array.config_work_mode_labels);
        int selected = configuration == null ? 1 : configuration.inventoryMode;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_work_mode)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_work_mode, labels[which], () -> {
                        session.setInventoryMode(which);
                        return CompletableFuture.completedFuture(0);
                    }, R.string.config_query_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showSessionDialog() {
        if (!requireReaderOnline()) { return; }
        String[] labels = getResources().getStringArray(R.array.config_session_labels);
        int selected = configuration == null ? 0 : configuration.session * 2 + configuration.target;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_session_target)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_session_target, labels[which],
                            () -> session.setSessionTarget(which / 2, which % 2),
                            R.string.config_query_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showBlfDialog() {
        if (!requireReaderOnline()) { return; }
        String[] labels = getResources().getStringArray(R.array.config_blf_labels);
        int selected = configuration == null ? 1 : configuration.blfProfile;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_blf_rate)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_blf_rate, labels[which],
                            () -> session.setBlf(which), R.string.config_blf_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void showQDialog() {
        if (!requireReaderOnline()) { return; }
        boolean magic = readerState.getModuleSubtype() == ModuleSubtype.MAGIC_RF;
        String[] labels = new String[32];
        for (int i = 0; i < 16; i++) {
            labels[i] = getString(R.string.config_fixed_q, i);
            labels[16 + i] = getString(R.string.config_dynamic_q, i);
        }
        if (magic) {
            labels = new String[16];
            for (int i = 0; i < 16; i++) { labels[i] = getString(R.string.config_q_value, i); }
        }
        String[] choices = labels;
        int currentQ = configuration == null ? 4 : configuration.qValue;
        int initialSelection = magic || configuration == null || !configuration.dynamicQ
                ? currentQ : 16 + currentQ;
        int[] selected = {Math.max(0, Math.min(choices.length - 1, initialSelection))};

        LinearLayout parameters = new LinearLayout(requireContext());
        parameters.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.dp_24);
        parameters.setPadding(padding, 0, padding, 0);
        EditText minQ = qParameterInput(R.string.config_q_min_value,
                configuration == null ? 0 : configuration.qMinValue);
        EditText maxQ = qParameterInput(R.string.config_q_max_value,
                configuration == null ? 15 : configuration.qMaxValue);
        EditText retry = qParameterInput(R.string.config_q_retry_count,
                configuration == null ? 0 : configuration.qRetryCount);
        EditText threshold = qParameterInput(R.string.config_q_threshold,
                configuration == null ? 1 : configuration.qThresholdMultiplier);
        parameters.addView(minQ);
        parameters.addView(maxQ);
        parameters.addView(retry);
        parameters.addView(threshold);
        boolean initiallyDynamic = !magic && selected[0] >= 16;
        minQ.setVisibility(initiallyDynamic ? View.VISIBLE : View.GONE);
        maxQ.setVisibility(initiallyDynamic ? View.VISIBLE : View.GONE);
        threshold.setVisibility(initiallyDynamic ? View.VISIBLE : View.GONE);
        parameters.setVisibility(magic ? View.GONE : View.VISIBLE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_q_parameter)
                .setSingleChoiceItems(choices, selected[0], (choiceDialog, which) -> {
                    selected[0] = which;
                    boolean dynamic = !magic && which >= 16;
                    minQ.setVisibility(dynamic ? View.VISIBLE : View.GONE);
                    maxQ.setVisibility(dynamic ? View.VISIBLE : View.GONE);
                    threshold.setVisibility(dynamic ? View.VISIBLE : View.GONE);
                })
                .setView(parameters)
                .setPositiveButton(R.string.common_confirm, null)
                .setNegativeButton(R.string.common_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    boolean dynamic = !magic && selected[0] >= 16;
                    try {
                        int retryCount = magic ? 0 : Integer.parseInt(retry.getText().toString());
                        int minQValue = dynamic ? Integer.parseInt(minQ.getText().toString()) : 0;
                        int maxQValue = dynamic ? Integer.parseInt(maxQ.getText().toString()) : 15;
                        int thresholdValue = dynamic
                                ? Integer.parseInt(threshold.getText().toString()) : 1;
                        if (retryCount < 0 || retryCount > 10) {
                            toast(R.string.config_q_retry_invalid);
                            return;
                        }
                        if (minQValue < 0 || minQValue > 15 || maxQValue < 0
                                || maxQValue > 15 || minQValue > maxQValue) {
                            toast(R.string.config_q_range_invalid);
                            return;
                        }
                        if (thresholdValue < 0 || thresholdValue > 255) {
                            toast(R.string.config_q_threshold_invalid);
                            return;
                        }
                        dialog.dismiss();
                        int qValue = selected[0] % 16;
                        boolean unchanged = configuration != null
                                && configuration.dynamicQ == dynamic
                                && configuration.qValue == qValue
                                && configuration.qRetryCount == retryCount
                                && (!dynamic || (configuration.qMinValue == minQValue
                                && configuration.qMaxValue == maxQValue
                                && configuration.qThresholdMultiplier == thresholdValue));
                        if (unchanged) { return; }
                        confirmAndApply(R.string.config_q_value_label, choices[selected[0]],
                                () -> session.setQ(dynamic, qValue, minQValue, maxQValue,
                                        retryCount, thresholdValue),
                                R.string.config_q_set_failed, () -> {});
                    } catch (NumberFormatException error) {
                        toast(R.string.config_q_value_invalid);
                    }
                }));
        dialog.show();
    }

    private void showMagicPowerDialog() {
        if (!requireReaderOnline()) { return; }
        int[] levels = MagicPowerLevels.levels();
        String[] values = new String[levels.length];
        for (int i = 0; i < values.length; i++) { values[i] = formatPower(levels[i]); }
        int selected = -1;
        if (configuration != null) {
            for (int i = 0; i < levels.length; i++) {
                if (levels[i] == configuration.powerTenthsDbm) { selected = i; break; }
            }
        }
        int initialSelection = selected;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_magic_power)
                .setSingleChoiceItems(values, initialSelection, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == initialSelection) { return; }
                    confirmAndApply(R.string.config_transmit_power, values[which],
                            () -> session.setPower(levels[which]),
                            R.string.config_power_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private EditText qParameterInput(@StringRes int hint, int value) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(String.valueOf(value));
        return input;
    }

    // ========== Module-aware UI ==========

    /** Keeps module-specific controls consistent after connection and subtype changes. */
    private void applyModuleUi(ModuleSubtype subtype) {
        boolean isMagic = subtype == ModuleSubtype.MAGIC_RF;
        boolean connected = readerState.isConnected();
        powerSeekBar.setVisibility(isMagic ? View.GONE : View.VISIBLE);
        powerValueView.setClickable(isMagic && connected);
        blfRow.setVisibility(isMagic ? View.GONE : View.VISIBLE);
        workModeRow.setEnabled(!isMagic && connected);
        if (isMagic) {
            workModeView.setText(workModeLabel(1));
        }
        protocolView.setText(readerState.getProtocol().getDisplayName());
        Log.d(TAG, "applyModuleUi subtype=" + subtype + " isMagic=" + isMagic
                + " connected=" + connected);
    }

    private void setHardwareEnabled(boolean enabled) {
        setEnabledRecursive(hardwareSection, enabled);
        protocolSection.setEnabled(enabled);
        setEnabledRecursive(findViewById(R.id.row_config_protocol), enabled);
        setEnabledRecursive(findViewById(R.id.row_config_session), enabled);
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

    private void restorePowerProgress(int progress) {
        int restored = Math.max(0, Math.min(powerSeekBar.getMax(), progress));
        bindingUi = true;
        powerSeekBar.setProgress(restored);
        powerValueView.setText(getString(R.string.rfid_power_value, restored));
        bindingUi = false;
    }

    // ========== Setting confirmation flow ==========

    /** Keeps every device setting behind the same confirm, loading, and rollback flow. */
    private void confirmAndApply(@StringRes int settingName, CharSequence newValueLabel,
            Supplier<CompletableFuture<Integer>> action, @StringRes int failureMessage,
            Runnable rollback) {
        boolean power = settingName == R.string.config_transmit_power;
        new MessageDialog.Builder(requireActivity())
                .setTitle(power ? R.string.config_power_confirm_title
                        : R.string.config_setting_confirm_title)
                .setMessage(power
                        ? getString(R.string.config_power_confirm_message, newValueLabel)
                        : getString(R.string.config_setting_confirm_message,
                        getString(settingName), newValueLabel))
                .setCancel(R.string.common_cancel)
                .setConfirm(R.string.common_confirm)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(@NonNull BaseDialog dialog) {
                        if (settingName == R.string.config_work_mode) {
                            action.get();
                            toast(R.string.config_work_mode_deferred_hint);
                            return;
                        }
                        settingWaitDialog = new WaitDialog.Builder(requireActivity())
                                .setMessage(power ? R.string.config_power_set_wait
                                        : R.string.config_setting_wait);
                        settingWaitDialog.show();
                        action.get().whenComplete((status, error) -> {
                            HomeActivity activity = getAttachActivity();
                            if (activity == null) { return; }
                            activity.runOnUiThread(() -> {
                                dismissSettingWaitDialog();
                                if (error != null || status == null || status != 0) {
                                    rollback.run();
                                    int errorCode = status == null ? -1 : status;
                                    Throwable cause = error;
                                    while (cause != null && cause.getCause() != null) {
                                        cause = cause.getCause();
                                    }
                                    if (cause instanceof ReaderException) {
                                        errorCode = ((ReaderException) cause).getErrorCode();
                                    }
                                    String message = getString(R.string.config_error_code,
                                            getString(failureMessage), errorCode);
                                    new MessageDialog.Builder(activity)
                                            .setTitle(failureMessage)
                                            .setMessage(message)
                                            .setCancel((CharSequence) null)
                                            .setConfirm(R.string.common_confirm)
                                            .show();
                                    return;
                                }
                                if (power) {
                                    toast(getString(R.string.config_power_set_success, newValueLabel));
                                } else {
                                    toast(getString(R.string.config_setting_success,
                                            getString(settingName), newValueLabel));
                                }
                            });
                        });
                    }

                    @Override
                    public void onCancel(@NonNull BaseDialog dialog) {
                        rollback.run();
                    }
                })
                .show();
    }

    private void dismissSettingWaitDialog() {
        if (settingWaitDialog != null && settingWaitDialog.isShowing()) {
            settingWaitDialog.dismiss();
        }
        settingWaitDialog = null;
    }

    private void showOrUpdateConnectionDialog(ReaderState state) {
        ReaderConnectionDialog dialog = (ReaderConnectionDialog) getParentFragmentManager()
                .findFragmentByTag(CONNECTION_DIALOG_TAG);
        if (dialog == null) {
            dialog = new ReaderConnectionDialog();
            dialog.show(getParentFragmentManager(), CONNECTION_DIALOG_TAG);
        }
        dialog.setOnFailureDismissed(() -> connectionFailureDialogDismissed = true);
        dialog.update(state.getPhase(), state.getMessage(), state.getErrorCode());
    }

    private void dismissConnectionDialog() {
        ReaderConnectionDialog dialog = (ReaderConnectionDialog) getParentFragmentManager()
                .findFragmentByTag(CONNECTION_DIALOG_TAG);
        if (dialog != null) { dialog.dismissAllowingStateLoss(); }
    }

    private static boolean isConnectingPhase(ConnectionPhase phase) {
        return phase == ConnectionPhase.CONNECTING || phase == ConnectionPhase.DISCOVERING_SERVICES
                || phase == ConnectionPhase.ENABLING_NOTIFICATIONS
                || phase == ConnectionPhase.CONNECTING_DATA_CHANNEL
                || phase == ConnectionPhase.VERIFYING_MODULE;
    }

    private String blfLabel(int profile) {
        return switch (profile) {
            case 0 -> getString(R.string.config_blf_fm0_40);
            case 1 -> getString(R.string.config_blf_m2_250);
            case 2 -> getString(R.string.config_blf_m2_300);
            case 3 -> getString(R.string.config_blf_fm0_400);
            default -> getString(R.string.config_unknown_value, profile);
        };
    }

    private String workModeLabel(int mode) {
        return switch (mode) {
            case 0 -> getString(R.string.config_work_mode_single);
            case 1 -> getString(R.string.config_work_mode_module);
            case 2 -> getString(R.string.config_work_mode_user);
            default -> getString(R.string.config_unknown_value, mode);
        };
    }

    private String formatPower(int tenthsDbm) {
        return tenthsDbm % 10 == 0 ? getString(R.string.rfid_power_value, tenthsDbm / 10)
                : getString(R.string.config_power_decimal, tenthsDbm / 10,
                Math.abs(tenthsDbm % 10));
    }
}
