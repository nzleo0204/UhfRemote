package com.leo.remote.business.common.data;

import com.leo.remote.business.auth.data.AuthRepository;
import com.leo.remote.business.feedback.data.FeedbackRepository;
import com.leo.remote.business.order.data.OrderRepository;
import com.leo.remote.business.shipment.data.ShipmentRepository;
import com.leo.remote.business.stock.data.StockRepository;

/** Repository contract supplied by the application composition root. */
public interface BusinessRepositoryProvider {
    AuthRepository auth();

    StockRepository stock();

    OrderRepository order();

    ShipmentRepository shipment();

    FeedbackRepository feedback();
}
