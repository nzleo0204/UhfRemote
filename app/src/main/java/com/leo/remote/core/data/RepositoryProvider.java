package com.leo.remote.core.data;

import com.leo.remote.business.auth.data.AuthRepository;
import com.leo.remote.business.feedback.data.mock.MockFeedbackRepository;
import com.leo.remote.business.auth.data.mock.MockAuthRepository;
import com.leo.remote.business.order.data.OrderRepository;
import com.leo.remote.business.order.data.mock.MockOrderRepository;
import com.leo.remote.business.shipment.data.ShipmentRepository;
import com.leo.remote.business.shipment.data.mock.MockShipmentRepository;
import com.leo.remote.business.stock.data.StockRepository;
import com.leo.remote.business.stock.data.mock.MockStockRepository;
import com.leo.remote.business.feedback.data.FeedbackRepository;

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
