package com.leo.uhf.app.navigation;

import android.content.Context;
import androidx.annotation.NonNull;
import com.leo.uhf.business.common.navigation.BusinessNavigator;
import com.leo.uhf.business.feedback.ui.FeedbackActivity;
import com.leo.uhf.business.order.ui.OrderListActivity;
import com.leo.uhf.business.shipment.ui.ShipmentQueryActivity;

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
