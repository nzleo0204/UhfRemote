package com.leo.uhf.business.common.data;

import com.leo.uhf.business.auth.data.AuthRepository;
import com.leo.uhf.business.feedback.data.FeedbackRepository;
import com.leo.uhf.business.order.data.OrderRepository;
import com.leo.uhf.business.shipment.data.ShipmentRepository;
import com.leo.uhf.business.stock.data.StockRepository;

/** Repository contract supplied by the application composition root. */
public interface BusinessRepositoryProvider {
    AuthRepository auth();

    StockRepository stock();

    OrderRepository order();

    ShipmentRepository shipment();

    FeedbackRepository feedback();
}
