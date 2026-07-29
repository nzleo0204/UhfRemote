package com.leo.remote.data.repository;

import com.leo.remote.data.repository.mock.MockFeedbackRepository;
import com.leo.remote.data.repository.mock.MockAuthRepository;
import com.leo.remote.data.repository.mock.MockOrderRepository;
import com.leo.remote.data.repository.mock.MockShipmentRepository;
import com.leo.remote.data.repository.mock.MockStockRepository;

public final class RepositoryProvider {
    private static final AuthRepository AUTH = new MockAuthRepository();
    private static final StockRepository STOCK = new MockStockRepository();
    private static final OrderRepository ORDER = new MockOrderRepository();
    private static final ShipmentRepository SHIPMENT = new MockShipmentRepository();
    private static final FeedbackRepository FEEDBACK = new MockFeedbackRepository();

    private RepositoryProvider() {}

    public static AuthRepository auth() {
        return AUTH;
    }

    public static StockRepository stock() {
        return STOCK;
    }

    public static OrderRepository order() {
        return ORDER;
    }

    public static ShipmentRepository shipment() {
        return SHIPMENT;
    }

    public static FeedbackRepository feedback() {
        return FEEDBACK;
    }
}
