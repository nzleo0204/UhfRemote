package com.leo.remote.business.order.data;

import com.leo.remote.data.DataCallback;
import com.leo.remote.business.order.data.model.Order;
import com.leo.remote.business.order.data.model.OrderStatus;
import java.util.List;

public interface OrderRepository {
    void queryOrders(String keyword, OrderStatus filter, DataCallback<List<Order>> callback);
}
