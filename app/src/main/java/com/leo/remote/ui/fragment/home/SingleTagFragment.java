package com.leo.remote.ui.fragment.home;

import com.leo.remote.R;
import com.leo.remote.app.AppFragment;
import com.leo.remote.ui.activity.HomeActivity;

/**
 * 单标签读写操作页。
 */
public final class SingleTagFragment extends AppFragment<HomeActivity> {

    public static SingleTagFragment newInstance() {
        return new SingleTagFragment();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.single_tag_fragment;
    }

    @Override
    protected void initView() {
        findViewById(R.id.tv_single_read).setOnClickListener(v -> toast("已读取当前标签"));
        findViewById(R.id.ll_single_write).setOnClickListener(v -> toast("进入写入数据流程"));
        findViewById(R.id.ll_single_update_epc).setOnClickListener(v -> toast("进入 EPC 修改流程"));
        findViewById(R.id.ll_single_lock).setOnClickListener(v -> toast("进入标签锁定流程"));
        findViewById(R.id.ll_single_destroy).setOnClickListener(v -> toast("销毁标签需二次确认"));
    }

    @Override
    protected void initData() {
        // Static preview data is rendered in XML.
    }
}
