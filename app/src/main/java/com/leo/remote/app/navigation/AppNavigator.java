package com.leo.remote.app.navigation;

import android.content.Context;
import androidx.annotation.NonNull;
import com.leo.remote.business.common.navigation.BusinessNavigator;
import com.leo.remote.business.feedback.ui.FeedbackActivity;
import com.leo.remote.business.order.ui.OrderListActivity;
import com.leo.remote.business.shipment.ui.ShipmentQueryActivity;

/** Application-owned destinations for independently packaged business features. */
public final class AppNavigator implements BusinessNavigator {
    @Override
    public void openOrders(@NonNull Context context) {
        OrderListActivity.start(context);
    }

    @Override
    public void openShipments(@NonNull Context context) {
        ShipmentQueryActivity.start(context);
    }

    @Override
    public void openFeedback(@NonNull Context context) {
        FeedbackActivity.start(context);
    }
}
