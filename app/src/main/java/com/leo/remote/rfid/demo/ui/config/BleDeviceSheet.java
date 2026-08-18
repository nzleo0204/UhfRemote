package com.leo.remote.rfid.demo.ui.config;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cn.wandersnail.ble.Device;
import cn.wandersnail.ble.EasyBLE;
import cn.wandersnail.ble.ScannerType;
import cn.wandersnail.ble.callback.ScanListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hjq.base.BottomSheetDialog;
import com.leo.remote.R;
import com.leo.remote.rfid.demo.ui.config.BleDeviceAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 展示 BLE 设备扫描结果并处理设备选择。
 */
public final class BleDeviceSheet extends DialogFragment {
    private static final String TAG = "UhfBleScan";
    private static final int REQUEST_BLE_PERMISSIONS = 201;
    private static final long LIST_UPDATE_INTERVAL_MS = 150L;
    private static final AtomicBoolean ACTIVE_SCAN_OWNER = new AtomicBoolean();

    public interface Listener { void onDeviceSelected(Device device); }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BleDeviceRegistry<Device> devices = new BleDeviceRegistry<>();
    private final AtomicBoolean selectionCommitted = new AtomicBoolean();
    private Listener listener;
    private TextView statusView;
    private BleDeviceAdapter adapter;
    private EasyBLE easyBle;
    private ScanListener scanListener;
    private long activeGeneration;
    private boolean finishing;
    private boolean listUpdatePending;
    private boolean ownsScan;

    private final Runnable publishDevices = () -> {
        listUpdatePending = false;
        if (adapter == null || finishing) { return; }
        List<BleDeviceAdapter.Item> rows = new ArrayList<>();
        for (BleDeviceRegistry.Entry<Device> entry : devices.snapshot(activeGeneration)) {
            rows.add(new BleDeviceAdapter.Item(entry.value, entry.name, entry.address, entry.rssi));
        }
        adapter.submitList(rows);
        if (statusView != null) { statusView.setText("已发现 " + rows.size() + " 个设备"); }
    };

    public BleDeviceSheet() {
        setCancelable(false);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new BottomSheetDialog(requireContext());
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.ble_device_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        statusView = view.findViewById(R.id.tv_ble_scan_status);
        adapter = new BleDeviceAdapter(this::selectDevice);
        RecyclerView recyclerView = view.findViewById(R.id.rv_ble_devices);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter);
        View rescanView = view.findViewById(R.id.btn_ble_rescan);
        rescanView.setOnClickListener(ignored -> startScanWithPermission());
        view.findViewById(R.id.btn_ble_close).setOnClickListener(ignored -> closeSheet());
        easyBle = EasyBLE.getInstance();
        ownsScan = ACTIVE_SCAN_OWNER.compareAndSet(false, true);
        if (ownsScan) {
            startScanWithPermission();
        } else {
            statusView.setText(R.string.ble_scan_already_running);
            rescanView.setEnabled(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog currentDialog = getDialog();
        if (!(currentDialog instanceof BottomSheetDialog bottomSheetDialog)) { return; }
        int targetHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.8f);
        View content = getView();
        if (content != null) {
            ViewGroup.LayoutParams params = content.getLayoutParams();
            if (params == null) {
                params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        targetHeight);
            } else {
                params.height = targetHeight;
            }
            content.setLayoutParams(params);
        }
        BottomSheetBehavior<FrameLayout> behavior = bottomSheetDialog.getBottomSheetBehavior();
        behavior.setPeekHeight(targetHeight, false);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(false);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onDestroyView() {
        finishing = true;
        releaseScanOwner();
        mainHandler.removeCallbacks(publishDevices);
        statusView = null;
        adapter = null;
        super.onDestroyView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BLE_PERMISSIONS || statusView == null) { return; }
        if (grantResults.length == 0) {
            statusView.setText("缺少蓝牙扫描权限");
            return;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                statusView.setText("缺少蓝牙扫描权限");
                return;
            }
        }
        startScan();
    }

    private void startScanWithPermission() {
        if (finishing || !ownsScan) { return; }
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
        }
        // EasyBLE 1.5.8 checks fine location on every supported Android version.
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        if (!missing.isEmpty()) {
            statusView.setText("等待蓝牙权限");
            requestPermissions(missing.toArray(new String[0]), REQUEST_BLE_PERMISSIONS);
            return;
        }
        startScan();
    }

    private void startScan() {
        stopScanAndRemoveListener();
        activeGeneration = devices.beginScan();
        adapter.submitList(List.of());
        if (!easyBle.isBluetoothOn()) {
            statusView.setText("蓝牙已关闭");
            return;
        }
        long generation = activeGeneration;
        scanListener = createScanListener(generation);
        easyBle.addScanListener(scanListener);
        easyBle.scanConfiguration.setScannerType(ScannerType.LE);
        Log.i(TAG, "scan start generation=" + generation);
        easyBle.startScan(requireActivity());
    }

    private ScanListener createScanListener(long generation) {
        return new ScanListener() {
            @Override
            public void onScanStart() {
                if (isActive(generation)) { statusView.setText("正在扫描..."); }
            }

            @Override
            public void onScanStop() {
                if (!isActive(generation)) { return; }
                publishDeviceListNow();
                statusView.setText(devices.size(generation) == 0
                        ? "未发现低功耗蓝牙设备" : "扫描完成");
                Log.i(TAG, "scan stop generation=" + generation + " count=" + devices.size(generation));
            }

            @Override
            public void onScanResult(@NonNull Device device, boolean isConnectedBySys) {
                if (!isActive(generation)) { return; }
                if (devices.addOrUpdate(generation, device.getName(), device.getAddress(),
                        device.getRssi(), SystemClock.elapsedRealtime(), device)) {
                    scheduleDeviceListUpdate();
                }
            }

            @Override
            public void onScanError(int errorCode, @NonNull String errorMsg) {
                if (!isActive(generation)) { return; }
                Log.e(TAG, "scan error generation=" + generation + " code=" + errorCode
                        + " message=" + errorMsg);
                statusView.setText(switch (errorCode) {
                    case ScanListener.ERROR_LACK_LOCATION_PERMISSION,
                            ScanListener.ERROR_LACK_SCAN_PERMISSION,
                            ScanListener.ERROR_LACK_CONNECT_PERMISSION -> "缺少蓝牙扫描权限";
                    case ScanListener.ERROR_LOCATION_SERVICE_CLOSED -> "请开启系统定位服务后重新扫描";
                    case ScanListener.ERROR_BLUETOOTH_OFF -> "蓝牙已关闭";
                    case ScanListener.ERROR_SCANNER_NOT_READY -> "蓝牙扫描器尚未就绪";
                    default -> "扫描失败（错误码 " + errorCode + "）";
                });
            }
        };
    }

    private void scheduleDeviceListUpdate() {
        if (listUpdatePending) { return; }
        listUpdatePending = true;
        mainHandler.postDelayed(publishDevices, LIST_UPDATE_INTERVAL_MS);
    }

    private void publishDeviceListNow() {
        mainHandler.removeCallbacks(publishDevices);
        listUpdatePending = false;
        publishDevices.run();
    }

    private boolean isActive(long generation) {
        return !finishing && statusView != null && generation == activeGeneration;
    }

    private void selectDevice(Device device) {
        if (device == null || !selectionCommitted.compareAndSet(false, true)) { return; }
        Log.i(TAG, "device selected name=" + device.getName() + " address=" + device.getAddress()
                + " generation=" + activeGeneration);
        finishSheet(device);
    }

    private void closeSheet() {
        if (!selectionCommitted.compareAndSet(false, true)) { return; }
        Log.i(TAG, "scan sheet closed generation=" + activeGeneration);
        finishSheet(null);
    }

    private void finishSheet(@Nullable Device selectedDevice) {
        finishing = true;
        releaseScanOwner();
        dismissAllowingStateLoss();
        if (selectedDevice != null && listener != null) {
            listener.onDeviceSelected(selectedDevice);
        }
    }

    private void stopScanAndRemoveListener() {
        devices.invalidate();
        activeGeneration = -1;
        mainHandler.removeCallbacks(publishDevices);
        listUpdatePending = false;
        if (easyBle == null) { return; }
        easyBle.stopScanQuietly();
        if (scanListener != null) {
            easyBle.removeScanListener(scanListener);
            scanListener = null;
        }
    }

    private void releaseScanOwner() {
        if (!ownsScan) { return; }
        stopScanAndRemoveListener();
        ownsScan = false;
        ACTIVE_SCAN_OWNER.set(false);
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (ContextCompat.checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }
}
