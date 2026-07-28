package com.leo.remote.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.airbnb.lottie.LottieAnimationView;
import com.leo.remote.R;
import com.leo.remote.reader.ConnectionPhase;
import com.leo.remote.reader.DisconnectReason;
import com.leo.remote.reader.ReaderSessionManager;

public final class ReaderConnectionDialog extends DialogFragment {
    private TextView phaseView;
    private TextView detailView;
    private View rootView;
    private View cancelButton;
    private View closeButton;
    private ImageView resultView;
    private LottieAnimationView animationView;
    private ConnectionPhase currentPhase = ConnectionPhase.CONNECTING;
    private String currentDetail = "";
    private int currentErrorCode;
    private final Runnable dismissSuccess = this::dismissAllowingStateLoss;

    public ReaderConnectionDialog() {
        setCancelable(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
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
            update(ConnectionPhase.DISCONNECTING, "正在取消连接", 0);
            ReaderSessionManager.getInstance(requireActivity().getApplication())
                    .disconnect(DisconnectReason.CANCELED);
        });
        closeButton.setOnClickListener(ignored -> dismissAllowingStateLoss());
        dialog.setContentView(view);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); }
        update(currentPhase, currentDetail, currentErrorCode);
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

    public void update(ConnectionPhase phase, String detail, int errorCode) {
        currentPhase = phase;
        currentDetail = detail == null ? "" : detail;
        currentErrorCode = errorCode;
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
                ? View.VISIBLE : View.GONE);
        closeButton.setVisibility(failure ? View.VISIBLE : View.GONE);
        phaseView.setText(switch (phase) {
            case CONNECTING -> "正在连接";
            case DISCOVERING_SERVICES -> "正在发现服务";
            case ENABLING_NOTIFICATIONS -> "正在启用通知";
            case CONNECTING_DATA_CHANNEL -> "正在建立数据通道";
            case VERIFYING_MODULE -> "正在验证 RM70XX";
            case CONNECTED -> "连接成功";
            case FAILED -> "连接失败";
            case DISCONNECTING -> "正在取消连接";
            default -> "正在连接读写器";
        });
        String message = currentDetail;
        if (failure && currentErrorCode != 0) {
            message = message + (message.isEmpty() ? "" : "\n") + "错误码：" + currentErrorCode;
        }
        detailView.setText(message);
        if (success && rootView != null) { rootView.postDelayed(dismissSuccess, 1000); }
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
