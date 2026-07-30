package com.leo.remote.data.model;

public enum ShipmentStatus {
    SHIPPED("已发货"),
    IN_TRANSIT("运输中"),
    PARTIAL("分批发货"),
    DELIVERED("已签收"),
    PREPARING("备货中");

    public final String label;

    ShipmentStatus(String label) {
        this.label = label;
    }
}
