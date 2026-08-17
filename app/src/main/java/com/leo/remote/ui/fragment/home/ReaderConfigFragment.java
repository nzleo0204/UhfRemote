package com.leo.remote.ui.fragment.home;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.Build;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.PermissionLists;
import com.leo.remote.R;
import com.leo.remote.app.AppActivity;
import com.leo.remote.app.AppFragment;
import com.leo.remote.reader.ConnectionPhase;
import com.leo.remote.reader.DisconnectReason;
import com.leo.remote.reader.InventoryArea;
import com.leo.remote.reader.Rm610PowerLevels;
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
import com.leo.remote.ui.dialog.common.InventoryRangeDialog;
import com.leo.remote.ui.dialog.common.WaitDialog;
import com.leo.remote.ui.view.IpAddressInputView;
import com.hjq.base.BaseDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** RFID reader connection and parameter configuration page. */
@SuppressLint({"LogNotTimber", "ClickableViewAccessibility"})
public final class ReaderConfigFragment extends AppFragment<HomeActivity> implements ReaderObserver {
    private static final String TAG = "UhfReader/Config";
    private static final String CONNECTION_DIALOG_TAG = "reader_connection";
    private static final String DEVICE_INFO_DIALOG_TAG = "reader_device_info";

    private ReaderSessionManager session;
    private TextView statusView;
    private TextView disconnectReasonView;
    private TextView powerValueView;
    private TextView protocolView;
    private TextView workModeView;
    private TextView sessionView;
    private TextView blfView;
    // Q值相关已移除
    private TextView inventoryAreaView;
    private TextView bleDeviceView;
    private View deviceInfoButton;
    private IpAddressInputView wifiAddressView;
    private SeekBar powerSeekBar;
    private View powerRangeView;
    private TextView powerMaxView;
    private View hardwareSection;
    private View protocolSection;
    private View rateSection;
    private View bleActions;
    private View wifiActions;
    private View disconnectRow;
    private TextView connectedTargetView;
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
    private WaitDialog.Builder settingWaitDialog;
    private WaitDialog.Builder parameterUpdateDialog;
    private int powerProgressBeforeDrag;
    private int imeInsetBottom;
    private int configScrollBaseBottomPadding;

    public static ReaderConfigFragment newInstance() { return new ReaderConfigFragment(); }

    @Override
    protected int getLayoutId() { return R.layout.reader_config_fragment; }

    @Override
    protected void initView() {
        statusView = findViewById(R.id.tv_config_status);
        disconnectReasonView = findViewById(R.id.tv_config_disconnect_reason);
        powerValueView = findViewById(R.id.tv_config_power_value);
        protocolView = findViewById(R.id.tv_config_protocol);
        workModeView = findViewById(R.id.tv_config_work_mode);
        sessionView = findViewById(R.id.tv_config_session);
        blfView = findViewById(R.id.tv_config_blf);
        // Q值相关已移除
        inventoryAreaView = findViewById(R.id.tv_config_inventory_area);
        bleDeviceView = findViewById(R.id.tv_config_ble_device);
        deviceInfoButton = findViewById(R.id.ibtn_config_device_info);
        wifiAddressView = findViewById(R.id.et_config_wifi_ip);
        powerSeekBar = findViewById(R.id.sb_config_power);
        powerRangeView = findViewById(R.id.row_config_power_range);
        powerMaxView = findViewById(R.id.tv_config_power_max);
        hardwareSection = findViewById(R.id.ll_config_hardware);
        protocolSection = findViewById(R.id.ll_config_protocol);
        rateSection = findViewById(R.id.ll_config_rate);
        bleActions = findViewById(R.id.ll_config_ble_actions);
        wifiActions = findViewById(R.id.ll_config_wifi_actions);
        disconnectRow = findViewById(R.id.ll_config_disconnect);
        connectedTargetView = findViewById(R.id.tv_config_connected_target);
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
            bindTransportRows(true, readerState.isConnected());
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
            bindTransportRows(false, readerState.isConnected());
        });
        deviceInfoButton.setOnClickListener(view -> showDeviceInfo());
        findViewById(R.id.btn_config_ble_scan).setOnClickListener(view -> showBleDevices());
        findViewById(R.id.btn_config_wifi_connect).setOnClickListener(view -> connectWifi());
        findViewById(R.id.btn_config_disconnect).setOnClickListener(view -> disconnectDevice());
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
        findViewById(R.id.row_config_inventory_area)
                .setOnClickListener(view -> showInventoryAreaDialog());
        workModeRow.setOnClickListener(view -> showWorkModeDialog());
        findViewById(R.id.row_config_session).setOnClickListener(view -> showSessionDialog());
        blfRow.setOnClickListener(view -> showBlfDialog());
        // Q值相关已移除
        findViewById(R.id.btn_config_refresh).setOnClickListener(view -> refreshParameters());
        powerValueView.setOnClickListener(view -> {
            if (readerState.getModuleSubtype() == ModuleSubtype.RM610
                    && !Rm610PowerLevels.isCmtVersion(readerState.getModuleSerial())) {
                showRm610PowerDialog();
            }
        });
        // 功率行整行可点击
        View powerRow = findViewById(R.id.row_config_power);
        if (powerRow != null) {
            powerRow.setOnClickListener(view -> {
                if (readerState.getModuleSubtype() == ModuleSubtype.RM610
                        && !Rm610PowerLevels.isCmtVersion(readerState.getModuleSerial())) {
                    showRm610PowerDialog();
                }
            });
        }

        powerSeekBar.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
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
    public void onDestroyView() {
        dismissSettingWaitDialog();
        dismissParameterUpdateDialog();
        dismissConnectionDialog();
        dismissFragmentDialog(DEVICE_INFO_DIALOG_TAG);
        dismissFragmentDialog("ble_devices");
        if (session != null) { session.removeObserver(this); }
        super.onDestroyView();
    }

    @Override
    public void onReaderStateChanged(ReaderState state) {
        readerState = state;
        if (!isViewReady()) { return; }
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
        boolean parameterUpdating = state.getPhase() == ConnectionPhase.UPDATING_PARAMETERS;
        bindTransportRows(bleSwitch.isChecked(), state.hasTransportLink());
        setHardwareEnabled(connected);
        if (state.getTransport() == TransportType.BLE && !state.getAddress().isEmpty()) {
            bleDeviceView.setText(bleDisplayName(state.getDeviceName()));
        }

        if (isConnectingPhase(state.getPhase())) {
            showOrUpdateConnectionDialog(state);
        } else if (parameterUpdating) {
            dismissConnectionDialog();
            showOrUpdateParameterUpdateDialog(state);
        } else if (state.getPhase() == ConnectionPhase.FAILED
                && !session.isConnectionFailureAcknowledged(state)) {
            dismissParameterUpdateDialog();
            showOrUpdateConnectionDialog(state);
        } else {
            dismissConnectionDialog();
            dismissParameterUpdateDialog();
        }
        applyModuleUi(state.getModuleSubtype());
        // The connection dialog ends before the parameter initialization dialog starts.
    }

    @Override
    public void onReaderConfigurationChanged(ReaderConfiguration value) {
        configuration = value;
        if (!isViewReady()) { return; }
        bindingUi = true;
        ModuleSubtype subtype = readerState.getModuleSubtype();
        boolean rm610Cmt = subtype == ModuleSubtype.RM610
                && Rm610PowerLevels.isCmtVersion(readerState.getModuleSerial());
        if (subtype == ModuleSubtype.RM610 && !rm610Cmt) {
            powerSeekBar.setVisibility(View.GONE);
            powerRangeView.setVisibility(View.GONE);
            powerValueView.setText(Rm610PowerLevels.formatNonCmt(value.powerTenthsDbm));
        } else {
            powerSeekBar.setVisibility(View.VISIBLE);
            powerRangeView.setVisibility(View.VISIBLE);
            powerSeekBar.setProgress(Math.max(0, Math.min(powerSeekBar.getMax(),
                    value.powerTenthsDbm / 10)));
            powerValueView.setText(formatPower(value.powerTenthsDbm));
        }
        blfView.setText(blfLabel(value.blfProfile));
        sessionView.setText(getString(R.string.config_session_value, value.session));
        // Q值相关已移除
        updateInventoryAreaView(value);
        workModeView.setText(readerState.getModuleSubtype().supportsInventoryModeSwitch()
                ? workModeLabel(value.inventoryMode) : workModeLabel(1));
        bindingUi = false;
    }

    private void bindTransportRows(boolean ble, boolean connected) {
        bleActions.setVisibility(!connected && ble ? View.VISIBLE : View.GONE);
        wifiActions.setVisibility(!connected && !ble ? View.VISIBLE : View.GONE);
        disconnectRow.setVisibility(connected ? View.VISIBLE : View.GONE);
        bleSwitch.setEnabled(!connected);
        wifiSwitch.setEnabled(!connected);
        if (connected) {
            String target = readerState.getTransport() == TransportType.BLE
                    ? bleDisplayName(readerState.getDeviceName()) : readerState.getAddress();
            connectedTargetView.setText(target);
        }
    }

    private void disconnectDevice() {
        if (session != null) {
            session.disconnect(DisconnectReason.USER);
        }
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
                    if (!isViewReady()) { return; }
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
        wifiAddressView.postDelayed(() -> runOnViewThread(() -> {
            Rect inputBounds = new Rect();
            wifiAddressView.getDrawingRect(inputBounds);
            wifiAddressView.requestRectangleOnScreen(inputBounds, false);
            keepWifiInputAboveKeyboard();
        }), 120);
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
                .setCustomTitle(createDialogTitle(R.string.config_protocol))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_protocol, labels[which],
                            () -> session.setProtocol(protocols.get(which)),
                            R.string.config_protocol_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private void refreshParameters() {
        if (!requireReaderOnline()) { return; }
        dismissSettingWaitDialog();
        settingWaitDialog = new WaitDialog.Builder(requireActivity())
                .setMessage(R.string.config_refreshing);
        settingWaitDialog.show();
        session.refreshConfiguration().whenComplete((status, error) ->
                runOnViewThread(() -> {
                    dismissSettingWaitDialog();
                    if (error != null) {
                        Throwable cause = error;
                        while (cause.getCause() != null) { cause = cause.getCause(); }
                        toast(cause.getMessage() == null
                                ? getString(R.string.config_refresh_params) : cause.getMessage());
                    } else if (status != null && status != 0) {
                        toast(getString(R.string.config_error_code,
                                getString(R.string.config_refresh_params), status));
                    } else {
                        toast(R.string.config_refresh_success);
                    }
                }));
    }

    private void showInventoryAreaDialog() {
        if (!requireReaderOnline()) { return; }
        List<InventoryArea> areas = InventoryArea.forProtocol(readerState.getProtocol());
        // 去掉"盘点"两个字，只保留区域名称
        String[] labels = areas.stream()
                .map(area -> area.getDisplayName().replace("盘点", ""))
                .toArray(String[]::new);
        int currentValue = configuration == null ? 0 : configuration.inventoryArea;
        int selected = Math.max(0, areas.indexOf(InventoryArea.of(readerState.getProtocol(),
                currentValue)));
        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitle(R.string.config_inventory_area))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    InventoryArea target = areas.get(which);
                    // 所有选项都弹出输入弹窗
                    showInventoryRangeDialog(target);
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void showInventoryRangeDialog(InventoryArea target) {
        int defaultAddress = defaultAddress(target);
        int defaultWordLen = defaultWordLen(target);
        // 去掉 "EPC/" 前缀，只保留后面的部分
        String columnHeader = target.getColumnHeader();
        if (columnHeader.startsWith("EPC/")) {
            columnHeader = columnHeader.substring(4); // 去掉 "EPC/"
        }
        String title = columnHeader + " 设置";

        // 如果是仅EPC，显示简单的确认弹窗
        if (target.isBaseOnly()) {
            new MessageDialog.Builder(requireActivity())
                    .setTitle(title)
                    .setMessage("确认设置盘点区域为 " + columnHeader + "？")
                    .setCancel(R.string.common_cancel)
                    .setConfirm(R.string.common_confirm)
                    .setListener(new MessageDialog.OnListener() {
                        @Override
                        public void onConfirm(@NonNull BaseDialog dialog) {
                            applyInventoryArea(target, 0, 0);
                        }
                    })
                    .show();
            return;
        }

        // 非仅EPC，显示地址和长度输入弹窗
        new InventoryRangeDialog.Builder(requireContext())
                .setTitle(title)
                .setAddress(defaultAddress)
                .setLength(defaultWordLen)
                .setListener(new InventoryRangeDialog.Builder.OnListener() {
                    @Override
                    public void onConfirm(@NonNull BaseDialog dialog, int address, int length) {
                        applyInventoryArea(target, address, length);
                    }

                    @Override
                    public void onInvalid(@NonNull BaseDialog dialog) {
                        toast(getString(R.string.config_inventory_range_invalid,
                                ReaderConfiguration.MAX_INVENTORY_WORD_LEN));
                    }
                })
                .show();
    }

    private void applyInventoryArea(InventoryArea target, int address, int length) {
        settingWaitDialog = new WaitDialog.Builder(requireActivity())
                .setMessage(R.string.config_setting_wait);
        settingWaitDialog.show();
        session.setInventoryArea(target.getValue(), address, length)
                .thenApply(status -> {
                    if (status == 0) { session.clearInventory(); }
                    return status;
                })
                .whenComplete((status, error) -> {
                    runOnViewThread(() -> {
                        dismissSettingWaitDialog();
                        if (error != null || status == null || status != 0) {
                            int errorCode = status == null ? -1 : status;
                            Throwable cause = error;
                            while (cause != null && cause.getCause() != null) {
                                cause = cause.getCause();
                            }
                            if (cause instanceof ReaderException) {
                                errorCode = ((ReaderException) cause).getErrorCode();
                            }
                            new MessageDialog.Builder(requireActivity())
                                    .setTitle(R.string.config_inventory_area_set_failed)
                                    .setMessage(getString(R.string.config_error_code,
                                            getString(R.string.config_inventory_area_set_failed),
                                            errorCode))
                                    .setCancel((CharSequence) null)
                                    .setConfirm(R.string.common_confirm)
                                    .show();
                            return;
                        }
                        toast(getString(R.string.config_setting_success,
                                getString(R.string.config_inventory_area),
                                target.getDisplayName()));
                    });
                });
    }

    private void updateInventoryAreaView(ReaderConfiguration value) {
        InventoryArea area = InventoryArea.of(readerState.getProtocol(), value.inventoryArea);
        inventoryAreaView.setText(area.getDisplayName());
    }

    private static int defaultAddress(InventoryArea area) {
        return 0;
    }

    private static int defaultWordLen(InventoryArea area) {
        return area == InventoryArea.C_EPC_RESERVED
                ? 4 : ReaderConfiguration.DEFAULT_INVENTORY_WORD_LEN;
    }

    private void showWorkModeDialog() {
        if (!requireReaderOnline()) { return; }
        // RM610 同属于 R2000 系列，支持切换
        ModuleSubtype subtype = readerState.getModuleSubtype();
        if (!subtype.supportsInventoryModeSwitch()) {
            toast(R.string.config_work_mode_unsupported);
            return;
        }
        String[] labels = getResources().getStringArray(R.array.config_work_mode_labels);
        int selected = configuration == null ? 1 : configuration.inventoryMode;
        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitle(R.string.config_work_mode))
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
        if (!supportsSession(readerState.getProtocol())) {
            toast(R.string.config_session_unsupported);
            return;
        }
        String[] labels = getResources().getStringArray(R.array.config_session_labels);
        int selected = configuration == null ? 0 : configuration.session;
        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitle(R.string.config_session))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_session, labels[which],
                            () -> session.setSession(which),
                            R.string.config_query_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    private static boolean supportsSession(TagProtocol protocol) {
        return protocol == TagProtocol.ISO_18000_6C || protocol == TagProtocol.GB_T_29768;
    }

    private void showBlfDialog() {
        if (!requireReaderOnline()) { return; }
        String[] labels = getResources().getStringArray(R.array.config_blf_labels);
        int selected = configuration == null ? 1 : configuration.blfProfile;
        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitle(R.string.config_blf_rate))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_blf_rate, labels[which],
                            () -> session.setBlf(which), R.string.config_blf_set_failed, () -> {});
                }).setNegativeButton(R.string.common_cancel, null).show();
    }

    // Q值设置相关方法已移除 (showQDialog 完整删除)

    private void showRm610PowerDialog() {
        if (!requireReaderOnline()) { return; }
        String[] values = Rm610PowerLevels.nonCmtLabels();
        int selected = configuration == null ? -1 : configuration.powerTenthsDbm;

        new MaterialAlertDialogBuilder(requireContext())
                .setCustomTitle(createDialogTitle(R.string.config_transmit_power))
                .setSingleChoiceItems(values, selected, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == selected) { return; }
                    confirmAndApply(R.string.config_transmit_power, values[which],
                            () -> session.setPower(which), R.string.config_power_set_failed,
                            () -> {});
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    /** 创建居中显示的弹窗标题 */
    private TextView createDialogTitle(@StringRes int titleRes) {
        TextView titleView = new TextView(requireContext());
        titleView.setText(titleRes);
        titleView.setTextSize(15); // 标题字号 15sp
        titleView.setGravity(android.view.Gravity.CENTER);
        titleView.setPadding(0, 40, 0, 20);
        titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.rfid_text));
        return titleView;
    }

    // Q值参数输入方法已移除 (qParameterInput)

    // ========== Module-aware UI ==========

    /** Keeps module-specific controls consistent after connection and subtype changes. */
    private void applyModuleUi(ModuleSubtype subtype) {
        boolean isRm8011 = subtype == ModuleSubtype.RM8011;
        boolean isRm610Discrete = subtype == ModuleSubtype.RM610
                && !Rm610PowerLevels.isCmtVersion(readerState.getModuleSerial());
        boolean connected = readerState.isConnected();
        int maxDbm = subtype == ModuleSubtype.RM8011 || subtype == ModuleSubtype.RM610 ? 20 : 30;
        powerSeekBar.setMax(maxDbm);
        powerSeekBar.setVisibility(isRm610Discrete ? View.GONE : View.VISIBLE);
        powerRangeView.setVisibility(isRm610Discrete ? View.GONE : View.VISIBLE);
        powerMaxView.setText(maxDbm == 20
                ? R.string.config_power_max_rm610 : R.string.config_power_max);
        powerValueView.setClickable(isRm610Discrete && connected);
        blfRow.setVisibility(isRm8011 ? View.GONE : View.VISIBLE);
        // RM610 同属于 R2000 系列，支持工作模式切换
        boolean supportsModeSwitch = subtype.supportsInventoryModeSwitch()
                || subtype == ModuleSubtype.RM610;
        workModeRow.setEnabled(supportsModeSwitch && connected);
        if (!supportsModeSwitch) {
            workModeView.setText(workModeLabel(1));
        }
        protocolView.setText(readerState.getProtocol().getDisplayName());
        boolean sessionEnabled = supportsSession(readerState.getProtocol()) && connected;
        View sessionRow = findViewById(R.id.row_config_session);
        sessionRow.setEnabled(sessionEnabled);
        sessionRow.setAlpha(sessionEnabled ? 1f : 0.45f);
        Log.d(TAG, "applyModuleUi subtype=" + subtype + " isRm8011=" + isRm8011
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

        // 构建带红色高亮的确认消息
        String settingText = getString(settingName);
        String template = power
                ? getString(R.string.config_power_confirm_message, "%s")
                : getString(R.string.config_setting_confirm_message, settingText, "%s");

        // 创建带颜色的消息
        android.text.SpannableString message = new android.text.SpannableString(
                template.replace("%s", newValueLabel));
        int start = message.toString().indexOf(newValueLabel.toString());
        if (start >= 0) {
            message.setSpan(new android.text.style.ForegroundColorSpan(
                    ContextCompat.getColor(requireContext(), R.color.rfid_danger)),
                    start, start + newValueLabel.length(),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        new MessageDialog.Builder(requireActivity())
                .setTitle(power ? R.string.config_power_confirm_title
                        : settingName)  // 使用具体的设置名称作为标题
                .setMessage(message)
                .setCancel(R.string.common_cancel)
                .setConfirm(R.string.common_confirm)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(@NonNull BaseDialog dialog) {
                        settingWaitDialog = new WaitDialog.Builder(requireActivity())
                                .setMessage(power ? R.string.config_power_set_wait
                                        : R.string.config_setting_wait);
                        settingWaitDialog.show();
                        action.get().whenComplete((status, error) -> {
                            runOnViewThread(() -> {
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
                                    new MessageDialog.Builder(requireActivity())
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

    private void showOrUpdateParameterUpdateDialog(ReaderState state) {
        if (parameterUpdateDialog == null || !parameterUpdateDialog.isShowing()) {
            parameterUpdateDialog = new WaitDialog.Builder(requireActivity())
                    .setMessage(state.getMessage());
            parameterUpdateDialog.show();
        } else {
            parameterUpdateDialog.setMessage(state.getMessage());
        }
    }

    private void dismissParameterUpdateDialog() {
        if (parameterUpdateDialog != null && parameterUpdateDialog.isShowing()) {
            parameterUpdateDialog.dismiss();
        }
        parameterUpdateDialog = null;
    }

    private void showOrUpdateConnectionDialog(ReaderState state) {
        ReaderConnectionDialog dialog = (ReaderConnectionDialog) getParentFragmentManager()
                .findFragmentByTag(CONNECTION_DIALOG_TAG);
        if (dialog == null) {
            dialog = new ReaderConnectionDialog();
            dialog.show(getParentFragmentManager(), CONNECTION_DIALOG_TAG);
        }
        dialog.setOnFailureDismissed(() -> session.acknowledgeConnectionFailure(state));
        dialog.update(state.getPhase(), state.getMessage(), state.getConnectionFailure());
    }

    private void dismissConnectionDialog() {
        ReaderConnectionDialog dialog = (ReaderConnectionDialog) getParentFragmentManager()
                .findFragmentByTag(CONNECTION_DIALOG_TAG);
        if (dialog != null) { dialog.dismissAllowingStateLoss(); }
    }

    private void dismissFragmentDialog(String tag) {
        Fragment fragment = getParentFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof DialogFragment dialog) {
            dialog.dismissAllowingStateLoss();
        }
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
