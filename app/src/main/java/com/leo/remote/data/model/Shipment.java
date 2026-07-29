package com.leo.remote.data.model;

import java.util.List;

public final class Shipment {
    public String orderNo;
    public String batchNo;
    public int quantity;
    public String trackingNo;
    public String carrier;
    public ShipmentStatus status;
    public long shipTime;
    public List<TimelineNode> timeline;

    public Shipment(String orderNo, String batchNo, int quantity, String trackingNo,
            String carrier, ShipmentStatus status, long shipTime, List<TimelineNode> timeline) {
        this.orderNo = orderNo;
        this.batchNo = batchNo;
        this.quantity = quantity;
        this.trackingNo = trackingNo;
        this.carrier = carrier;
        this.status = status;
        this.shipTime = shipTime;
        this.timeline = timeline;
    }
}
