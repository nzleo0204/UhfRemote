package com.leo.remote.business.shipment.data;

import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.shipment.data.model.Shipment;
import com.leo.remote.business.shipment.data.model.ShipmentStatus;
import java.util.List;

/**
 * 定义发运单查询业务所需的数据访问能力。
 */
public interface ShipmentRepository {
    void queryShipments(String keyword, ShipmentStatus filter, DataCallback<List<Shipment>> callback);
}
