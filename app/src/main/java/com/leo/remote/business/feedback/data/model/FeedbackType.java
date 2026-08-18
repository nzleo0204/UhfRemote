package com.leo.remote.business.feedback.data.model;

public enum FeedbackType {
    PRODUCT("产品问题"),
    ORDER("订单问题"),
    REQUIREMENT("新需求");

    public final String label;

    FeedbackType(String label) {
        this.label = label;
    }
}
