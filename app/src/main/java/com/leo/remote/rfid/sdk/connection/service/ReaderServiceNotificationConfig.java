package com.leo.remote.rfid.sdk.connection.service;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

/** Host-provided notification strings and destination for the optional reader service. */
public final class ReaderServiceNotificationConfig {
    @DrawableRes
    public final int smallIcon;
    public final String channelName;
    public final String connectedMessage;
    public final String connectingMessage;
    public final String disconnectedMessage;
    public final String disconnectAction;
    @NonNull
    public final Intent contentIntent;

    public ReaderServiceNotificationConfig(@DrawableRes int smallIcon,
            @NonNull String channelName, @NonNull String connectedMessage,
            @NonNull String connectingMessage, @NonNull String disconnectedMessage,
            @NonNull String disconnectAction, @NonNull Intent contentIntent) {
        this.smallIcon = smallIcon;
        this.channelName = channelName;
        this.connectedMessage = connectedMessage;
        this.connectingMessage = connectingMessage;
        this.disconnectedMessage = disconnectedMessage;
        this.disconnectAction = disconnectAction;
        this.contentIntent = new Intent(contentIntent);
    }

    public static ReaderServiceNotificationConfig defaultConfig(@NonNull Context context) {
        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent == null) {
            launchIntent = new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName());
        }
        return new ReaderServiceNotificationConfig(android.R.drawable.stat_notify_sync,
                "Reader connection", "Reader connected", "Connecting reader",
                "Reader disconnected", "Disconnect", launchIntent);
    }
}
