package com.leo.remote.data.repository;

import com.leo.remote.data.DataCallback;
import com.leo.remote.data.model.Order;
import com.leo.remote.data.model.OrderStatus;
import java.util.List;

public interface OrderRepository {
    void queryOrders(String keyword, OrderStatus filter, DataCallback<List<Order>> callback);
}
