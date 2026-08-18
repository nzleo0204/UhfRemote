package com.leo.remote.data.repository;

import com.leo.remote.core.data.DataCallback;
import com.leo.remote.data.model.FeedbackDraft;

public interface FeedbackRepository {
    void submitFeedback(FeedbackDraft draft, DataCallback<Boolean> callback);
}
