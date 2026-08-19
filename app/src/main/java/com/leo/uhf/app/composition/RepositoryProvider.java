package com.leo.uhf.app.composition;

import com.leo.uhf.business.auth.data.AuthRepository;
import com.leo.uhf.business.auth.data.mock.MockAuthRepository;
import com.leo.uhf.business.common.data.BusinessRepositoryProvider;
import com.leo.uhf.business.feedback.data.FeedbackRepository;
import com.leo.uhf.business.feedback.data.mock.MockFeedbackRepository;
import com.leo.uhf.business.order.data.OrderRepository;
import com.leo.uhf.business.order.data.mock.MockOrderRepository;
import com.leo.uhf.business.shipment.data.ShipmentRepository;
import com.leo.uhf.business.shipment.data.mock.MockShipmentRepository;
import com.leo.uhf.business.stock.data.StockRepository;
import com.leo.uhf.business.stock.data.mock.MockStockRepository;

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
