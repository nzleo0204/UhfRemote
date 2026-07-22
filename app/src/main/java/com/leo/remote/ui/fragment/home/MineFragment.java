package com.leo.remote.ui.fragment.home;

import android.content.Intent;
import com.leo.remote.R;
import com.leo.remote.app.AppFragment;
import com.leo.remote.ui.activity.FeedbackActivity;
import com.leo.remote.ui.activity.HomeActivity;
import com.leo.remote.ui.activity.OrderProgressActivity;
import com.leo.remote.ui.activity.ShipmentQueryActivity;
import com.leo.remote.ui.activity.StockQueryActivity;

/**
 * 我的页面，承载查询和反馈入口。
 */
public final class MineFragment extends AppFragment<HomeActivity> {

    public static MineFragment newInstance() {
        return new MineFragment();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.mine_fragment;
    }

    @Override
    protected void initView() {
        findViewById(R.id.ll_mine_stock).setOnClickListener(v -> startActivity(new Intent(getAttachActivity(), StockQueryActivity.class)));
        findViewById(R.id.ll_mine_order).setOnClickListener(v -> startActivity(new Intent(getAttachActivity(), OrderProgressActivity.class)));
        findViewById(R.id.ll_mine_shipment).setOnClickListener(v -> startActivity(new Intent(getAttachActivity(), ShipmentQueryActivity.class)));
        findViewById(R.id.ll_mine_feedback).setOnClickListener(v -> startActivity(new Intent(getAttachActivity(), FeedbackActivity.class)));
        findViewById(R.id.tv_mine_login).setOnClickListener(v -> toast("登录信息已提交"));
    }

    @Override
    protected void initData() {
        // Static preview data is rendered in XML.
    }
}
