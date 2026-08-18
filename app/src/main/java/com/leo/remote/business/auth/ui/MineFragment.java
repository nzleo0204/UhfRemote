package com.leo.remote.business.auth.ui;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.leo.remote.R;
import com.leo.remote.core.aop.SingleClick;
import com.leo.remote.core.ui.base.BaseFragment;
import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.auth.data.model.UserInfo;
import com.leo.remote.business.auth.data.AuthRepository;
import com.leo.remote.core.data.RepositoryProvider;
import com.leo.remote.business.feedback.ui.FeedbackActivity;
import com.leo.remote.app.MainActivity;
import com.leo.remote.business.order.ui.OrderListActivity;
import com.leo.remote.business.shipment.ui.ShipmentQueryActivity;
import com.leo.remote.core.util.ThemeModeManager;
import com.tencent.mmkv.MMKV;

/**
 * 我的页面，承载查询和反馈入口。
 */
public final class MineFragment extends BaseFragment<MainActivity> {
    private static final String MMKV_ID = "auth_config";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";

    private final AuthRepository authRepository = RepositoryProvider.auth();
    private MMKV authStorage;
    private TextView usernameView;
    private TextView roleView;
    private TextView themeValueView;
    private EditText usernameInput;
    private EditText passwordInput;
    private android.view.View loggedGroup;
    private android.view.View accountDivider;
    private android.view.View loginButton;
    private View accountCard;
    private View inputGuard;
    private View rootView;

    public static MineFragment newInstance() {
        return new MineFragment();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.mine_fragment;
    }

    @Override
    protected void initView() {
        loggedGroup = findViewById(R.id.ll_mine_logged);
        accountDivider = findViewById(R.id.v_mine_account_divider);
        loginButton = findViewById(R.id.tv_mine_login);
        usernameView = findViewById(R.id.tv_mine_username);
        roleView = findViewById(R.id.tv_mine_role);
        usernameInput = findViewById(R.id.et_mine_username);
        passwordInput = findViewById(R.id.et_mine_password);
        themeValueView = findViewById(R.id.tv_mine_theme_value);
        accountCard = findViewById(R.id.ll_mine_account_card);
        inputGuard = findViewById(R.id.v_mine_input_guard);
        rootView = findViewById(R.id.fl_mine_root);

        findViewById(R.id.ll_mine_order).setOnClickListener(v -> OrderListActivity.start(getAttachActivity()));
        findViewById(R.id.ll_mine_shipment).setOnClickListener(
                v -> ShipmentQueryActivity.start(getAttachActivity()));
        findViewById(R.id.ll_mine_feedback).setOnClickListener(v -> FeedbackActivity.start(getAttachActivity()));
        loginButton.setOnClickListener(v -> login());
        findViewById(R.id.ll_mine_theme_setting).setOnClickListener(v -> showThemeDialog());
        bindLoginInputGuard();
    }

    @Override
    protected void initData() {
        authStorage = MMKV.mmkvWithID(MMKV_ID);
        bindAuthState();
        bindThemeState();
    }

    @SingleClick
    private void login() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        authRepository.login(username, password, new DataCallback<>() {
            @Override
            public void onSuccess(UserInfo data) {
                authStorage.encode(KEY_TOKEN, "mock-token-" + data.username);
                authStorage.encode(KEY_USERNAME, data.username);
                authStorage.encode(KEY_ROLE, data.role);
                bindAuthState();
                dismissLoginKeyboard();
                toast("登录成功");
            }

            @Override
            public void onFail(Exception e) {
                toast(e.getMessage());
            }
        });
    }

    private void bindAuthState() {
        boolean logged = authStorage != null && !authStorage.decodeString(KEY_TOKEN, "").isEmpty();
        loggedGroup.setVisibility(View.VISIBLE);
        accountDivider.setVisibility(View.VISIBLE);
        usernameInput.setVisibility(logged ? android.view.View.GONE : android.view.View.VISIBLE);
        passwordInput.setVisibility(logged ? android.view.View.GONE : android.view.View.VISIBLE);
        loginButton.setVisibility(logged ? android.view.View.GONE : android.view.View.VISIBLE);
        if (logged) {
            usernameView.setText(authStorage.decodeString(KEY_USERNAME, "admin"));
            roleView.setText(getString(R.string.mine_logged_role,
                    authStorage.decodeString(KEY_ROLE, getString(R.string.mine_default_role))));
            roleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.rfid_success));
        } else {
            usernameView.setText(R.string.mine_guest);
            roleView.setText(R.string.mine_not_logged_in);
            roleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.rfid_text_muted));
        }
    }

    private void bindLoginInputGuard() {
        View.OnFocusChangeListener listener = (view, hasFocus) -> {
            if (hasFocus) {
                showInputGuard();
            } else {
                view.post(() -> {
                    if (!usernameInput.hasFocus() && !passwordInput.hasFocus()) {
                        inputGuard.setVisibility(View.GONE);
                    }
                });
            }
        };
        usernameInput.setOnFocusChangeListener(listener);
        passwordInput.setOnFocusChangeListener(listener);
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) { return false; }
            login();
            return true;
        });
        inputGuard.setOnClickListener(view -> dismissLoginKeyboard());
        findViewById(R.id.sv_mine_content).setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.performClick();
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN && isLoginInputFocused()
                    && !isTouchInside(usernameInput, event) && !isTouchInside(passwordInput, event)) {
                dismissLoginKeyboard();
            }
            return false;
        });
    }

    private void showInputGuard() {
        accountCard.post(() -> {
            int[] rootLocation = new int[2];
            int[] cardLocation = new int[2];
            rootView.getLocationOnScreen(rootLocation);
            accountCard.getLocationOnScreen(cardLocation);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) inputGuard.getLayoutParams();
            params.topMargin = Math.max(0,
                    cardLocation[1] - rootLocation[1] + accountCard.getHeight());
            inputGuard.setLayoutParams(params);
            inputGuard.setVisibility(View.VISIBLE);
        });
    }

    private void dismissLoginKeyboard() {
        View focused = passwordInput.hasFocus() ? passwordInput : usernameInput;
        hideKeyboard(focused);
        usernameInput.clearFocus();
        passwordInput.clearFocus();
        inputGuard.setVisibility(View.GONE);
    }

    private boolean isLoginInputFocused() {
        return usernameInput.hasFocus() || passwordInput.hasFocus();
    }

    private boolean isTouchInside(View target, MotionEvent event) {
        Rect bounds = new Rect();
        return target.getGlobalVisibleRect(bounds)
                && bounds.contains((int) event.getRawX(), (int) event.getRawY());
    }

    private void bindThemeState() {
        themeValueView.setText(ThemeModeManager.getModeNameRes(ThemeModeManager.getStoredMode()));
    }

    private void showThemeDialog() {
        int[] modes = {ThemeModeManager.MODE_SYSTEM, ThemeModeManager.MODE_LIGHT, ThemeModeManager.MODE_DARK};
        String[] labels = getResources().getStringArray(R.array.theme_mode_labels);
        int current = ThemeModeManager.getStoredMode();
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme_setting_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    ThemeModeManager.setMode(modes[which]);
                    themeValueView.setText(labels[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }
}
