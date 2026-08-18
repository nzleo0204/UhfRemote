package com.leo.remote.data.repository.mock;

import android.text.TextUtils;
import com.leo.remote.core.data.DataCallback;
import com.leo.remote.data.model.FeedbackDraft;
import com.leo.remote.data.repository.FeedbackRepository;
import com.leo.remote.core.data.mock.BaseMockRepository;

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
