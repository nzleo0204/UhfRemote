package com.leo.remote.business.shipment.data.mock;

import android.text.TextUtils;
import com.leo.remote.core.data.DataCallback;
import com.leo.remote.business.shipment.data.model.Shipment;
import com.leo.remote.business.shipment.data.model.ShipmentStatus;
import com.leo.remote.business.shipment.data.model.ShipmentBatch;
import com.leo.remote.business.shipment.data.model.TimelineNode;
import com.leo.remote.business.shipment.data.ShipmentRepository;
import com.leo.remote.core.data.mock.BaseMockRepository;
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
                new Shipment("ORD-2024-0851", "BATCH-2024-08A", 50000,
                        "SF1234567890123", "顺丰速运", ShipmentStatus.SHIPPED,
                        now - 86_400_000L, List.of(
                        new TimelineNode("已发货", "深圳仓已出库", now - 86_400_000L, true)),
                        "H47 Monza R6 定制款", List.of()),
                new Shipment("ORD-2024-0732", "BATCH-2024-07A", 20000,
                        "", "", ShipmentStatus.PARTIAL,
                        now - 172_800_000L, List.of(), "Alien H3 标准型", List.of(
                        new ShipmentBatch("第1批", 6000, "顺丰速运", "已签收",
                                "发出: 2024-12-10  签收: 2024-12-12", true),
                        new ShipmentBatch("第2批", 6000, "中通快递", "运输中",
                                "发出: 2024-12-15  预计: -", false),
                        new ShipmentBatch("第3批", 8000, "", "待发货",
                                "预计发出: -", false))),
                new Shipment("ORD-2024-0615", "BATCH-2024-06C", 30000,
                        "YT9876543210", "圆通速递", ShipmentStatus.DELIVERED,
                        now - 604_800_000L, List.of(
                        new TimelineNode("已发货", "上海仓已出库", now - 604_800_000L, true),
                        new TimelineNode("运输中", "到达目的地城市", now - 518_400_000L, true),
                        new TimelineNode("已签收", "客户仓库签收", now - 432_000_000L, true)),
                        "Impinj E710 标准型", List.of()),
                new Shipment("ORD-2024-0851", "BATCH-2024-08B", 18000,
                        "待生成", "待安排", ShipmentStatus.PREPARING,
                        now, List.of(new TimelineNode("备货中", "生产完成后安排发货", now, true)),
                        "H47 Monza R6 定制款", List.of()),
                new Shipment("ORD-2024-0588", "BATCH-2024-05D", 18000,
                        "JDVA123456789", "京东物流", ShipmentStatus.DELIVERED,
                        now - 777_600_000L, List.of(
                        new TimelineNode("已发货", "深圳仓已出库", now - 777_600_000L, true),
                        new TimelineNode("已签收", "客户仓库签收", now - 691_200_000L, true)),
                        "Alien H3 标准型", List.of()));
    }
}
