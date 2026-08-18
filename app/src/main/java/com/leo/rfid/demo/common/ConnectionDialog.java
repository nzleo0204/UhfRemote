package com.leo.rfid.demo.common;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.airbnb.lottie.LottieAnimationView;
import com.hjq.base.BaseDialog;
import com.leo.remote.R;
import com.leo.rfid.sdk.model.ConnectionPhase;
import com.leo.rfid.sdk.model.DisconnectReason;
import com.leo.rfid.sdk.model.ReaderConnectionFailure;
import com.leo.rfid.sdk.connect.ReaderSessionManager;

/**
 * 展示读写器连接、握手和失败状态的进度弹窗。
 */
public final class ConnectionDialog extends DialogFragment {
    private TextView phaseView;
    private TextView detailView;
    private View rootView;
    private View cancelButton;
    private View closeButton;
    private ImageView resultView;
    private LottieAnimationView animationView;
    private ConnectionPhase currentPhase = ConnectionPhase.CONNECTING;
    private String currentDetail = "";
    private ReaderConnectionFailure currentFailure = ReaderConnectionFailure.NONE;
    @Nullable
    private Runnable onFailureDismissed;
    private final Runnable dismissSuccess = this::dismissAllowingStateLoss;

    public ConnectionDialog() {
        setCancelable(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.reader_connection_dialog,
                new FrameLayout(requireContext()), false);
        rootView = view;
        phaseView = view.findViewById(R.id.tv_reader_connect_phase);
        detailView = view.findViewById(R.id.tv_reader_connect_detail);
        animationView = view.findViewById(R.id.lav_reader_connect);
        resultView = view.findViewById(R.id.iv_reader_connect_result);
        cancelButton = view.findViewById(R.id.btn_reader_connect_cancel);
        closeButton = view.findViewById(R.id.btn_reader_connect_close);
        cancelButton.setOnClickListener(ignored -> {
            update(ConnectionPhase.DISCONNECTING, "正在取消连接", ReaderConnectionFailure.NONE);
            ReaderSessionManager.getInstance(requireActivity().getApplication())
                    .disconnect(DisconnectReason.CANCELED);
        });
        closeButton.setOnClickListener(ignored -> {
            if (currentPhase == ConnectionPhase.FAILED && onFailureDismissed != null) {
                onFailureDismissed.run();
            }
            dismissAllowingStateLoss();
        });
        BaseDialog dialog = new BaseDialog.Builder<>(requireContext())
                .setContentView(view)
                .setAnimStyle(BaseDialog.ANIM_IOS)
                .setGravity(android.view.Gravity.CENTER)
                .setCanceledOnTouchOutside(false)
                .create();
        Window window = dialog.getWindow();
        if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); }
        update(currentPhase, currentDetail, currentFailure);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) { return; }
        int width = Math.round(getResources().getDisplayMetrics().widthPixels * 2f / 3f);
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public void update(ConnectionPhase phase, String detail, ReaderConnectionFailure failureType) {
        currentPhase = phase;
        currentDetail = detail == null ? "" : detail;
        currentFailure = failureType == null ? ReaderConnectionFailure.NONE : failureType;
        if (phaseView == null || detailView == null) { return; }
        if (rootView != null) { rootView.removeCallbacks(dismissSuccess); }

        boolean success = phase == ConnectionPhase.CONNECTED;
        boolean failure = phase == ConnectionPhase.FAILED;
        boolean terminal = success || failure;
        animationView.setVisibility(terminal ? View.GONE : View.VISIBLE);
        resultView.setVisibility(terminal ? View.VISIBLE : View.GONE);
        if (terminal) {
            animationView.cancelAnimation();
            resultView.setImageResource(success
                    ? R.drawable.rfid_connection_success_ic : R.drawable.rfid_connection_error_ic);
        } else {
            animationView.playAnimation();
        }

        cancelButton.setVisibility(!terminal && phase != ConnectionPhase.DISCONNECTING
                && phase != ConnectionPhase.VERIFYING_MODULE
                ? View.VISIBLE : View.GONE);
        closeButton.setVisibility(failure ? View.VISIBLE : View.GONE);
        phaseView.setText(switch (phase) {
            case CONNECTING -> "正在连接";
            case DISCOVERING_SERVICES -> "正在发现服务";
            case ENABLING_NOTIFICATIONS -> "正在启用通知";
            case CONNECTING_DATA_CHANNEL -> "正在建立数据通道";
            case VERIFYING_MODULE -> "正在获取设备版本信息";
            case UPDATING_PARAMETERS -> "正在更新设备参数";
            case CONNECTED -> "连接成功";
            case FAILED -> "连接失败";
            case DISCONNECTING -> "正在取消连接";
            default -> "正在连接读写器";
        });
        String message = getString(switch (phase) {
            case CONNECTING -> R.string.reader_connecting_detail;
            case DISCOVERING_SERVICES -> R.string.reader_discovering_detail;
            case ENABLING_NOTIFICATIONS -> R.string.reader_notifications_detail;
            case CONNECTING_DATA_CHANNEL -> R.string.reader_data_channel_detail;
            case VERIFYING_MODULE -> R.string.reader_verifying_detail;
            case UPDATING_PARAMETERS -> R.string.handshake_updating_params;
            case FAILED -> currentFailure == ReaderConnectionFailure.BLUETOOTH
                    ? R.string.reader_bluetooth_failed_detail : R.string.reader_failed_detail;
            case DISCONNECTING -> R.string.reader_disconnecting_detail;
            default -> R.string.reader_connecting_detail;
        });
        if (phase == ConnectionPhase.VERIFYING_MODULE && !currentDetail.isEmpty()) {
            message = currentDetail;
        }
        detailView.setText(message);
        if (success && rootView != null) { rootView.postDelayed(dismissSuccess, 1000); }
    }

    public void setOnFailureDismissed(@Nullable Runnable listener) {
        onFailureDismissed = listener;
    }

    @Override
    public void onDestroyView() {
        if (rootView != null) { rootView.removeCallbacks(dismissSuccess); }
        rootView = null;
        phaseView = null;
        detailView = null;
        animationView = null;
        resultView = null;
        cancelButton = null;
        closeButton = null;
        super.onDestroyView();
    }
}
