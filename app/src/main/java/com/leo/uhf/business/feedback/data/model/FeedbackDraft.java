package com.leo.uhf.business.feedback.data.model;

import java.util.List;

/**
 * 保存用户尚未提交的反馈内容。
 */
public final class FeedbackDraft {
    public FeedbackType type;
    public String relatedOrderNo;
    public String title;
    public String detail;
    public List<String> imagePaths;

    public FeedbackDraft(FeedbackType type, String relatedOrderNo, String title,
            String detail, List<String> imagePaths) {
        this.type = type;
        this.relatedOrderNo = relatedOrderNo;
        this.title = title;
        this.detail = detail;
        this.imagePaths = imagePaths;
    }
}
