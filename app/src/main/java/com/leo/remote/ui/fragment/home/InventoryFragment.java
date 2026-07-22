package com.leo.remote.ui.fragment.home;

import android.widget.TextView;
import com.leo.remote.R;
import com.leo.remote.app.AppFragment;
import com.leo.remote.ui.activity.HomeActivity;

/**
 * RFID 标签盘点页。
 */
public final class InventoryFragment extends AppFragment<HomeActivity> {

    private boolean mRunning;
    private TextView mStartView;

    public static InventoryFragment newInstance() {
        return new InventoryFragment();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.inventory_fragment;
    }

    @Override
    protected void initView() {
        mStartView = findViewById(R.id.tv_inventory_start);
        mStartView.setOnClickListener(v -> {
            mRunning = !mRunning;
            mStartView.setText(mRunning ? "停止盘点" : "开始盘点");
            mStartView.setBackgroundResource(mRunning ? R.drawable.rfid_danger_outline_bg : R.drawable.rfid_success_button_bg);
        });
        findViewById(R.id.tv_inventory_clear).setOnClickListener(v -> toast("已清除盘点列表"));
        findViewById(R.id.tv_inventory_export).setOnClickListener(v -> toast("盘点数据已准备导出"));
    }

    @Override
    protected void initData() {
        // Static preview data is rendered in XML.
    }
}
