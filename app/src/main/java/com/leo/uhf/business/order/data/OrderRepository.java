package com.leo.uhf.business.order.data;

import com.leo.uhf.core.data.DataCallback;
import com.leo.uhf.business.order.data.model.Order;
import com.leo.uhf.business.order.data.model.OrderStatus;
import java.util.List;

/**
 * 定义订单查询业务所需的数据访问能力。
 */
public interface OrderRepository {
    void queryOrders(String keyword, OrderStatus filter, DataCallback<List<Order>> callback);
}
