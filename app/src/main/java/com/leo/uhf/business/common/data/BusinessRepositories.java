package com.leo.uhf.business.common.data;

import androidx.annotation.NonNull;
import com.leo.uhf.business.auth.data.AuthRepository;
import com.leo.uhf.business.feedback.data.FeedbackRepository;
import com.leo.uhf.business.order.data.OrderRepository;
import com.leo.uhf.business.shipment.data.ShipmentRepository;
import com.leo.uhf.business.stock.data.StockRepository;

/** Business-facing repository access without depending on the application package. */
public final class BusinessRepositories {
    private static volatile BusinessRepositoryProvider provider;

    private BusinessRepositories() {}

    public static void initialize(@NonNull BusinessRepositoryProvider repositoryProvider) {
        provider = repositoryProvider;
    }

    public static AuthRepository auth() {
        return requireProvider().auth();
    }

    public static StockRepository stock() {
        return requireProvider().stock();
    }

    public static OrderRepository order() {
        return requireProvider().order();
    }

    public static ShipmentRepository shipment() {
        return requireProvider().shipment();
    }

    public static FeedbackRepository feedback() {
        return requireProvider().feedback();
    }

    private static BusinessRepositoryProvider requireProvider() {
        BusinessRepositoryProvider current = provider;
        if (current == null) {
            throw new IllegalStateException("Business repositories are not initialized");
        }
        return current;
    }
}
