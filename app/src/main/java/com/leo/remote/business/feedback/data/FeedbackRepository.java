package com.leo.remote.business.feedback.data;

import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.feedback.data.model.FeedbackDraft;

public interface FeedbackRepository {
    void submitFeedback(FeedbackDraft draft, DataCallback<Boolean> callback);
}
