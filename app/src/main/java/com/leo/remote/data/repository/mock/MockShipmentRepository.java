package com.leo.remote.data.repository.mock;

import android.text.TextUtils;
import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.Shipment;
import com.leo.remote.data.model.ShipmentStatus;
import com.leo.remote.data.model.TimelineNode;
import com.leo.remote.data.repository.ShipmentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MockShipmentRepository extends BaseMockRepository implements ShipmentRepository {
    @Override
    public void queryShipments(String keyword, ShipmentStatus filter, DataCallback<List<Shipment>> callback) {
        List<Shipment> result = new ArrayList<>();
        String query = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        for (Shipment shipment : data()) {
            boolean keywordMatched = TextUtils.isEmpty(query)
                    || shipment.orderNo.toLowerCase(Locale.ROOT).contains(query)
                    || shipment.batchNo.toLowerCase(Locale.ROOT).contains(query)
                    || shipment.trackingNo.toLowerCase(Locale.ROOT).contains(query);
            boolean statusMatched = filter == null || shipment.status == filter;
            if (keywordMatched && statusMatched) {
                result.add(shipment);
            }
        }
        respond(callback, result, List.of());
    }

    private static List<Shipment> data() {
        long now = System.currentTimeMillis();
        return List.of(
                new Shipment("ORD-2024-0732", "BATCH-2024-07A", 12000,
                        "SF1234567890", "顺丰速运", ShipmentStatus.IN_TRANSIT,
                        now - 86_400_000L, List.of(
                        new TimelineNode("已发货", "深圳仓已出库", now - 86_400_000L, true),
                        new TimelineNode("运输中", "到达华东转运中心", now - 43_200_000L, true),
                        new TimelineNode("派送中", "等待末端派送", now + 21_600_000L, false))),
                new Shipment("ORD-2024-0615", "BATCH-2024-06C", 30000,
                        "YT9876543210", "圆通速递", ShipmentStatus.DELIVERED,
                        now - 604_800_000L, List.of(
                        new TimelineNode("已发货", "上海仓已出库", now - 604_800_000L, true),
                        new TimelineNode("运输中", "到达目的地城市", now - 518_400_000L, true),
                        new TimelineNode("已签收", "客户仓库签收", now - 432_000_000L, true))),
                new Shipment("ORD-2024-0851", "BATCH-2024-08B", 18000,
                        "待生成", "待安排", ShipmentStatus.PREPARING,
                        now, List.of(new TimelineNode("备货中", "生产完成后安排发货", now, true))));
    }
}
