package com.leo.remote.data.repository;

import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.Shipment;
import com.leo.remote.data.model.ShipmentStatus;
import java.util.List;

public interface ShipmentRepository {
    void queryShipments(String keyword, ShipmentStatus filter, DataCallback<List<Shipment>> callback);
}
