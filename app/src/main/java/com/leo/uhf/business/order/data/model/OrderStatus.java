package com.leo.uhf.business.order.data.model;

import androidx.annotation.ColorRes;
import com.leo.uhf.R;

/**
 * 定义订单当前所处的业务状态。
 */
public enum OrderStatus {
    IN_PRODUCTION("生产中", R.color.rfid_primary_soft),
    PARTIAL_SHIPPED("部分发货", R.color.rfid_orange),
    PENDING("待处理", R.color.rfid_text_muted),
    COMPLETED("已完成", R.color.rfid_success);

    public final String label;
    @ColorRes
    public final int colorRes;

    OrderStatus(String label, int colorRes) {
        this.label = label;
        this.colorRes = colorRes;
    }
}
