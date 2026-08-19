package com.leo.rfid.demo.connection;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.remote.core.aop.SingleClick;
import com.leo.remote.core.ui.dialog.StyleDialog;
import com.leo.rfid.sdk.connect.serial.SerialConfig;
import com.leo.rfid.sdk.storage.MmkvSerialConfigStore;
import com.leo.rfid.sdk.storage.SerialConfigStore;
import com.leo.rfid.sdk.model.ModuleSubtype;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.PermissionLists;
import com.hjq.permissions.permission.base.IPermission;

/** 收集串口路径、波特率、模块型号和上电延时。 */
public final class SerialConfigDialog {
    public static final class Builder extends StyleDialog.Builder<Builder> {
        private final AutoCompleteTextView portView;
        private final Spinner baudRateView;
        private final Spinner moduleTypeView;
        private final EditText powerDelayView;
        private final SerialConfigStore configStore;
        @Nullable private OnListener listener;

        public Builder(@NonNull Context context) {
            this(context, new MmkvSerialConfigStore());
        }

        public Builder(@NonNull Context context, @NonNull SerialConfigStore configStore) {
            super(context);
            this.configStore = configStore;
            setTitle(R.string.serial_config_title);
            setCancel(R.string.common_cancel);
            setConfirm(R.string.serial_connect);
            setCustomView(R.layout.serial_config_dialog);
            setWidth((int) (context.getResources().getDisplayMetrics().widthPixels * 0.84f));
            portView = findViewById(R.id.et_serial_port_path);
            baudRateView = findViewById(R.id.sp_serial_baud_rate);
            moduleTypeView = findViewById(R.id.sp_serial_module_type);
            powerDelayView = findViewById(R.id.et_serial_power_delay);
            portView.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_dropdown_item_1line,
                    context.getResources().getStringArray(R.array.serial_port_paths)));
            bindSavedConfig(configStore.load());
        }

        public Builder setListener(@Nullable OnListener listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public void onClick(@NonNull View view) {
            if (view.getId() == R.id.tv_ui_cancel) {
                performClickDismiss();
                return;
            }
            if (view.getId() == R.id.tv_ui_confirm) { confirm(); }
        }

        @SingleClick
        private void confirm() {
            try {
                String path = String.valueOf(portView.getText()).trim();
                int baudRate = Integer.parseInt(String.valueOf(baudRateView.getSelectedItem()));
                int delay = Integer.parseInt(String.valueOf(powerDelayView.getText()).trim());
                ModuleSubtype subtype = switch (moduleTypeView.getSelectedItemPosition()) {
                    case 0 -> ModuleSubtype.R2000;
                    case 1 -> ModuleSubtype.R2000_PLUS;
                    case 2 -> ModuleSubtype.RM610;
                    case 3 -> ModuleSubtype.RM8011;
                    default -> ModuleSubtype.UNKNOWN;
                };
                SerialConfig config = new SerialConfig(path, baudRate, subtype, delay);
                requestStoragePermissionIfNeeded(config);
            } catch (IllegalArgumentException error) {
                if (listener != null) { listener.onInvalid(getDialog(), error.getMessage()); }
            }
        }

        private void bindSavedConfig(@Nullable SerialConfig saved) {
            SerialConfig config = saved == null ? SerialConfig.defaults() : saved;
            portView.setText(config.portPath, false);
            String[] baudRates = getContext().getResources()
                    .getStringArray(R.array.serial_baud_rates);
            for (int index = 0; index < baudRates.length; index++) {
                if (Integer.parseInt(baudRates[index]) == config.baudRate) {
                    baudRateView.setSelection(index);
                    break;
                }
            }
            moduleTypeView.setSelection(moduleIndex(config.moduleSubtype));
            powerDelayView.setText(String.valueOf(config.powerDelayMs));
        }

        private void requestStoragePermissionIfNeeded(@NonNull SerialConfig config) {
            Context context = getContext();
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S
                    || config.portPath.startsWith("/dev/")) {
                deliver(config);
                return;
            }
            if (!(context instanceof Activity)) {
                if (listener != null) {
                    listener.onInvalid(getDialog(), getString(R.string.error_serial_permission_denied));
                }
                return;
            }
            XXPermissions.with((Activity) context)
                    .permissions(new IPermission[]{
                            PermissionLists.getReadExternalStoragePermission(),
                            PermissionLists.getWriteExternalStoragePermission()})
                    .request((grantedList, deniedList) -> {
                        if (deniedList.isEmpty()) {
                            deliver(config);
                        } else if (listener != null) {
                            listener.onInvalid(getDialog(),
                                    getString(R.string.error_serial_permission_denied));
                        }
                    });
        }

        private void deliver(@NonNull SerialConfig config) {
            configStore.save(config);
            performClickDismiss();
            if (listener != null) { listener.onConfirm(getDialog(), config); }
        }

        private static int moduleIndex(@NonNull ModuleSubtype subtype) {
            return switch (subtype) {
                case R2000 -> 0;
                case R2000_PLUS -> 1;
                case RM610 -> 2;
                case RM8011 -> 3;
                case UNKNOWN -> 0;
            };
        }

        public interface OnListener {
            void onConfirm(@NonNull BaseDialog dialog, @NonNull SerialConfig config);
            default void onInvalid(@NonNull BaseDialog dialog, @Nullable String message) { }
        }
    }
}
