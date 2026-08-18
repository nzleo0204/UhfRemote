package com.leo.remote.app.composition;

import com.leo.remote.business.auth.data.AuthRepository;
import com.leo.remote.business.auth.data.mock.MockAuthRepository;
import com.leo.remote.business.common.data.BusinessRepositoryProvider;
import com.leo.remote.business.feedback.data.FeedbackRepository;
import com.leo.remote.business.feedback.data.mock.MockFeedbackRepository;
import com.leo.remote.business.order.data.OrderRepository;
import com.leo.remote.business.order.data.mock.MockOrderRepository;
import com.leo.remote.business.shipment.data.ShipmentRepository;
import com.leo.remote.business.shipment.data.mock.MockShipmentRepository;
import com.leo.remote.business.stock.data.StockRepository;
import com.leo.remote.business.stock.data.mock.MockStockRepository;

/** Production composition of the business repositories used by this application. */
public final class RepositoryProvider implements BusinessRepositoryProvider {
    private final AuthRepository auth = new MockAuthRepository();
    private final StockRepository stock = new MockStockRepository();
    private final OrderRepository order = new MockOrderRepository();
    private final ShipmentRepository shipment = new MockShipmentRepository();
    private final FeedbackRepository feedback = new MockFeedbackRepository();

    @Override
    public AuthRepository auth() {
        return auth;
    }

    @Override
    public StockRepository stock() {
        return stock;
    }

    @Override
    public OrderRepository order() {
        return order;
    }

    @Override
    public ShipmentRepository shipment() {
        return shipment;
    }

    @Override
    public FeedbackRepository feedback() {
        return feedback;
    }
}
