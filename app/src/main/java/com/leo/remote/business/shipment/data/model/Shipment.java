package com.leo.remote.business.shipment.data.model;

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
    public String productName;
    public List<ShipmentBatch> batches;

    public Shipment(String orderNo, String batchNo, int quantity, String trackingNo,
            String carrier, ShipmentStatus status, long shipTime, List<TimelineNode> timeline) {
        this(orderNo, batchNo, quantity, trackingNo, carrier, status, shipTime, timeline,
                "", List.of());
    }

    public Shipment(String orderNo, String batchNo, int quantity, String trackingNo,
            String carrier, ShipmentStatus status, long shipTime, List<TimelineNode> timeline,
            String productName, List<ShipmentBatch> batches) {
        this.orderNo = orderNo;
        this.batchNo = batchNo;
        this.quantity = quantity;
        this.trackingNo = trackingNo;
        this.carrier = carrier;
        this.status = status;
        this.shipTime = shipTime;
        this.timeline = timeline;
        this.productName = productName;
        this.batches = batches;
    }
}
