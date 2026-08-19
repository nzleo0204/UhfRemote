package com.leo.uhf.business.feedback.data;

import com.leo.uhf.core.data.DataCallback;
import com.leo.uhf.business.feedback.data.model.FeedbackDraft;

/**
 * 定义意见反馈业务所需的数据访问能力。
 */
public interface FeedbackRepository {
    void submitFeedback(FeedbackDraft draft, DataCallback<Boolean> callback);
}
