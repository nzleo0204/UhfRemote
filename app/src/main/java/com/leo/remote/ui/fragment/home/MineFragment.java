package com.leo.remote.ui.fragment.home;

import android.content.Intent;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.leo.remote.R;
import com.leo.remote.app.AppFragment;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.UserInfo;
import com.leo.remote.data.repository.AuthRepository;
import com.leo.remote.data.repository.RepositoryProvider;
import com.leo.remote.ui.activity.FeedbackActivity;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.activity.OrderProgressActivity;
import com.leo.remote.ui.activity.ShipmentQueryActivity;
import com.leo.remote.ui.activity.StockQueryActivity;
import com.leo.remote.util.ThemeModeManager;
import com.tencent.mmkv.MMKV;

/**
 * 我的页面，承载查询和反馈入口。
 */
public final class MineFragment extends AppFragment<HomeActivity> {
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

        findViewById(R.id.ll_mine_stock).setOnClickListener(v -> startActivity(new Intent(getAttachActivity(), StockQueryActivity.class)));
        findViewById(R.id.ll_mine_order).setOnClickListener(v -> startActivity(new Intent(getAttachActivity(), OrderProgressActivity.class)));
        findViewById(R.id.ll_mine_shipment).setOnClickListener(v -> startActivity(new Intent(getAttachActivity(), ShipmentQueryActivity.class)));
        findViewById(R.id.ll_mine_feedback).setOnClickListener(v -> startActivity(new Intent(getAttachActivity(), FeedbackActivity.class)));
        loginButton.setOnClickListener(v -> login());
        findViewById(R.id.ll_mine_theme_setting).setOnClickListener(v -> showThemeDialog());
    }

    @Override
    protected void initData() {
        authStorage = MMKV.mmkvWithID(MMKV_ID);
        bindAuthState();
        bindThemeState();
    }

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
        loggedGroup.setVisibility(logged ? android.view.View.VISIBLE : android.view.View.GONE);
        accountDivider.setVisibility(logged ? android.view.View.VISIBLE : android.view.View.GONE);
        usernameInput.setVisibility(logged ? android.view.View.GONE : android.view.View.VISIBLE);
        passwordInput.setVisibility(logged ? android.view.View.GONE : android.view.View.VISIBLE);
        loginButton.setVisibility(logged ? android.view.View.GONE : android.view.View.VISIBLE);
        if (logged) {
            usernameView.setText(authStorage.decodeString(KEY_USERNAME, "admin"));
            roleView.setText(getString(R.string.mine_logged_role,
                    authStorage.decodeString(KEY_ROLE, getString(R.string.mine_default_role))));
        }
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
