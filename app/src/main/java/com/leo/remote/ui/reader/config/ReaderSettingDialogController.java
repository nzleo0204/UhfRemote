package com.leo.remote.ui.reader.config;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.remote.rfid.sdk.model.ReaderException;
import com.leo.remote.ui.dialog.common.MessageDialog;
import com.leo.remote.ui.dialog.common.WaitDialog;
import com.leo.remote.util.ThrowableUtils;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the shared confirm, progress, failure, and rollback flow for reader settings. */
public final class ReaderSettingDialogController {
    private final Fragment fragment;
    private final Consumer<Runnable> uiDispatcher;
    private final Consumer<CharSequence> toast;
    private WaitDialog.Builder waitDialog;

    public ReaderSettingDialogController(Fragment fragment, Consumer<Runnable> uiDispatcher,
            Consumer<CharSequence> toast) {
        this.fragment = fragment;
        this.uiDispatcher = uiDispatcher;
        this.toast = toast;
    }

    public void confirmAndApply(@StringRes int settingName, CharSequence newValueLabel,
            Supplier<CompletableFuture<Integer>> action, @StringRes int failureMessage,
            Runnable rollback) {
        boolean power = settingName == R.string.config_transmit_power;
        String settingText = fragment.getString(settingName);
        String template = power
                ? fragment.getString(R.string.config_power_confirm_message, "%s")
                : fragment.getString(R.string.config_setting_confirm_message, settingText, "%s");
        SpannableString message = new SpannableString(template.replace("%s", newValueLabel));
        int start = message.toString().indexOf(newValueLabel.toString());
        if (start >= 0) {
            message.setSpan(new ForegroundColorSpan(ContextCompat.getColor(
                            fragment.requireContext(), R.color.rfid_danger)),
                    start, start + newValueLabel.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        new MessageDialog.Builder(fragment.requireActivity())
                .setTitle(power ? R.string.config_power_confirm_title : settingName)
                .setMessage(message)
                .setCancel(R.string.common_cancel)
                .setConfirm(R.string.common_confirm)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(BaseDialog dialog) {
                        showWait(power ? R.string.config_power_set_wait
                                : R.string.config_setting_wait);
                        action.get().whenComplete((status, error) -> uiDispatcher.accept(() -> {
                            dismiss();
                            if (error != null || status == null || status != 0) {
                                rollback.run();
                                int code = status == null ? -1 : status;
                                if (error != null
                                        && ThrowableUtils.rootCause(error) instanceof ReaderException reader) {
                                    code = reader.getErrorCode();
                                }
                                new MessageDialog.Builder(fragment.requireActivity())
                                        .setTitle(failureMessage)
                                        .setMessage(fragment.getString(R.string.config_error_code,
                                                fragment.getString(failureMessage), code))
                                        .setCancel((CharSequence) null)
                                        .setConfirm(R.string.common_confirm)
                                        .show();
                                return;
                            }
                            toast.accept(power
                                    ? fragment.getString(R.string.config_power_set_success,
                                            newValueLabel)
                                    : fragment.getString(R.string.config_setting_success,
                                            fragment.getString(settingName), newValueLabel));
                        }));
                    }

                    @Override public void onCancel(BaseDialog dialog) { rollback.run(); }
                })
                .show();
    }

    public void showWait(@StringRes int message) {
        dismiss();
        waitDialog = new WaitDialog.Builder(fragment.requireActivity()).setMessage(message);
        waitDialog.show();
    }

    public void dismiss() {
        if (waitDialog != null && waitDialog.isShowing()) { waitDialog.dismiss(); }
        waitDialog = null;
    }
}
