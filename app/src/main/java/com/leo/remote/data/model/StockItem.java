package com.leo.remote.data.model;

public final class StockItem {
    public String productName;
    public String chipModel;
    public int availableQty;
    public int reservedQty;
    public String warehouse;
    public String spec;
    public String imageUrl;
    public long updateTime;

    public StockItem(String productName, String chipModel, int availableQty, int reservedQty,
            String warehouse, String spec, String imageUrl, long updateTime) {
        this.productName = productName;
        this.chipModel = chipModel;
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.warehouse = warehouse;
        this.spec = spec;
        this.imageUrl = imageUrl;
        this.updateTime = updateTime;
    }
}
