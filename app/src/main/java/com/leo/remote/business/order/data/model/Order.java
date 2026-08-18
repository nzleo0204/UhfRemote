package com.leo.remote.business.order.data.model;

import java.util.List;

public final class Order {
    public String orderNo;
    public String productName;
    public int quantity;
    public String customRequirement;
    public OrderStatus status;
    public int progress;
    public int shippedQty;
    public int pendingQty;
    public List<String> processImages;
    public String imageUrl;
    public long submitTime;
    public long finishTime;

    public Order(String orderNo, String productName, int quantity, String customRequirement,
            OrderStatus status, int progress, int shippedQty, int pendingQty,
            List<String> processImages, String imageUrl, long submitTime, long finishTime) {
        this.orderNo = orderNo;
        this.productName = productName;
        this.quantity = quantity;
        this.customRequirement = customRequirement;
        this.status = status;
        this.progress = progress;
        this.shippedQty = shippedQty;
        this.pendingQty = pendingQty;
        this.processImages = processImages;
        this.imageUrl = imageUrl;
        this.submitTime = submitTime;
        this.finishTime = finishTime;
    }
}
