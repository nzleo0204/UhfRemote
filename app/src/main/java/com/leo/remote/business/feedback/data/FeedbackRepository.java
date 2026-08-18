package com.leo.remote.business.feedback.data;

import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.feedback.data.model.FeedbackDraft;

/**
 * 定义意见反馈业务所需的数据访问能力。
 */
public interface FeedbackRepository {
    void submitFeedback(FeedbackDraft draft, DataCallback<Boolean> callback);
}
