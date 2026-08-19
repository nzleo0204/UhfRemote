package com.leo.uhf.business.stock.data.model;

import java.util.List;

/**
 * 表示库存列表中的一条库存记录。
 */
public final class StockItem {
    public String productName;
    public String chipModel;
    public int availableQty;
    public int reservedQty;
    public String warehouse;
    public String spec;
    public String imageUrl;
    public long updateTime;
    public double unitPrice;
    public List<String> tags;

    public StockItem(String productName, String chipModel, int availableQty, int reservedQty,
            String warehouse, String spec, String imageUrl, long updateTime) {
        this(productName, chipModel, availableQty, reservedQty, warehouse, spec, imageUrl,
                updateTime, 0.0, List.of());
    }

    public StockItem(String productName, String chipModel, int availableQty, int reservedQty,
            String warehouse, String spec, String imageUrl, long updateTime, double unitPrice,
            List<String> tags) {
        this.productName = productName;
        this.chipModel = chipModel;
        this.availableQty = availableQty;
        this.reservedQty = reservedQty;
        this.warehouse = warehouse;
        this.spec = spec;
        this.imageUrl = imageUrl;
        this.updateTime = updateTime;
        this.unitPrice = unitPrice;
        this.tags = tags;
    }
}
