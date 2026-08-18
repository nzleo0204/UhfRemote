package com.leo.remote.business.feedback.data.mock;

import android.text.TextUtils;
import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.feedback.data.model.FeedbackDraft;
import com.leo.remote.business.feedback.data.FeedbackRepository;
import com.leo.remote.core.data.mock.BaseMockRepository;

/**
 * 提供反馈页面使用的本地模拟数据仓库。
 */
public final class MockFeedbackRepository extends BaseMockRepository implements FeedbackRepository {
    @Override
    public void submitFeedback(FeedbackDraft draft, DataCallback<Boolean> callback) {
        if (draft == null || TextUtils.isEmpty(draft.title) || TextUtils.isEmpty(draft.detail)) {
            respond(callback, Boolean.FALSE, Boolean.FALSE);
            return;
        }
        respond(callback, Boolean.TRUE, Boolean.TRUE);
    }
}
