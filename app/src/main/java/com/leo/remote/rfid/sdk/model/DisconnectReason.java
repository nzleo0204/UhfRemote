package com.leo.remote.rfid.sdk.model;

public enum DisconnectReason {
    NONE(false),
    USER(false),
    TRANSPORT_SWITCH(false),
    CANCELED(false),
    APP_EXIT(false),
    LINK_LOST(true),
    BLUETOOTH_OFF(true),
    WIFI_LOST(true),
    SDK_ERROR(true);

    private final boolean unexpected;

    DisconnectReason(boolean unexpected) {
        this.unexpected = unexpected;
    }

    public boolean isUnexpected() {
        return unexpected;
    }
}
