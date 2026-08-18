package com.leo.remote.business.common.navigation;

import android.content.Context;
import androidx.annotation.NonNull;

/** Host navigation contract used by business feature pages. */
public interface BusinessNavigator {
    void openOrders(@NonNull Context context);

    void openShipments(@NonNull Context context);

    void openFeedback(@NonNull Context context);
}
