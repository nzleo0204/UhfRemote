package com.leo.remote.business.shipment.data.model;

public final class ShipmentBatch {
    public String title;
    public int quantity;
    public String carrier;
    public String status;
    public String detail;
    public boolean received;

    public ShipmentBatch(String title, int quantity, String carrier, String status,
            String detail, boolean received) {
        this.title = title;
        this.quantity = quantity;
        this.carrier = carrier;
        this.status = status;
        this.detail = detail;
        this.received = received;
    }
}
