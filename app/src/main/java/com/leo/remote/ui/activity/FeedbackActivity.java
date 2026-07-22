package com.leo.remote.ui.activity;

import com.leo.remote.R;

public final class FeedbackActivity extends RfidPageActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.feedback_activity;
    }

    @Override
    protected void initPageView() {
        findViewById(R.id.tv_feedback_submit).setOnClickListener(v -> toast("问题已提交"));
    }

    @Override
    protected void initData() {
        // Static preview data is rendered in XML.
    }
}
