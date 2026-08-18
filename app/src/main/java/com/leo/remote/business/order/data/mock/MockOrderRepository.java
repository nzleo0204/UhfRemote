package com.leo.remote.business.order.data.mock;

import android.text.TextUtils;
import com.leo.remote.data.DataCallback;
import com.leo.remote.business.order.data.model.Order;
import com.leo.remote.business.order.data.model.OrderStatus;
import com.leo.remote.business.order.data.OrderRepository;
import com.leo.remote.core.data.mock.BaseMockRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MockOrderRepository extends BaseMockRepository implements OrderRepository {
    @Override
    public void queryOrders(String keyword, OrderStatus filter, DataCallback<List<Order>> callback) {
        List<Order> data = data();
        List<Order> result = new ArrayList<>();
        String query = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        for (Order order : data) {
            boolean keywordMatched = TextUtils.isEmpty(query)
                    || order.orderNo.toLowerCase(Locale.ROOT).contains(query)
                    || order.productName.toLowerCase(Locale.ROOT).contains(query);
            boolean statusMatched = filter == null || order.status == filter;
            if (keywordMatched && statusMatched) {
                result.add(order);
            }
        }
        respond(callback, result, List.of());
    }

    static List<Order> data() {
        long now = System.currentTimeMillis();
        List<String> processImages = List.of("product_monza", "product_alien", "product_ucode");
        return List.of(
                new Order("ORD-2024-0851", "H47 Monza R6 定制款", 50000,
                        "30×15mm 白底黑字背胶", OrderStatus.IN_PRODUCTION, 68,
                        0, 0, processImages, "product_monza", now - 86_400_000L, 0),
                new Order("ORD-2024-0732", "H47 Alien H3 标准款", 20000,
                        "标准白底 无特殊要求", OrderStatus.PARTIAL_SHIPPED, 0,
                        12000, 8000, List.of(), "product_alien", now - 172_800_000L, 0),
                new Order("ORD-2024-0904", "抗金属资产标签", 8000,
                        "70×25mm 抗金属泡棉", OrderStatus.PENDING, 0,
                        0, 8000, List.of(), "product_ucode", now - 43_200_000L, 0),
                new Order("ORD-2024-0615", "H47 Monza R6 定制款", 30000,
                        "标准白底 无特殊要求", OrderStatus.COMPLETED, 100,
                        30000, 0, List.of(), "product_e710", now - 604_800_000L, now - 86_400_000L),
                new Order("ORD-2024-0921", "UHF 抗金属标签", 12000,
                        "70×25mm 抗金属泡棉", OrderStatus.IN_PRODUCTION, 35,
                        0, 12000, processImages, "product_ucode", now - 28_800_000L, 0),
                new Order("ORD-2024-0588", "Alien H3 标准款", 18000,
                        "标准白底 无特殊要求", OrderStatus.COMPLETED, 100,
                        18000, 0, List.of(), "product_alien", now - 864_000_000L,
                        now - 259_200_000L));
    }
}
