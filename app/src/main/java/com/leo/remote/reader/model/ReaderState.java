package com.leo.remote.reader.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ReaderState {

    private final TransportType transport;
    private final ConnectionPhase phase;
    private final ModuleSubtype moduleSubtype;
    private final int rawModuleSubtype;
    private final TagProtocol protocol;
    private final String deviceName;
    private final String address;
    private final String boardSerial;
    private final String boardVersion;
    private final String moduleSerial;
    private final String moduleVersion;
    private final String message;
    private final int errorCode;
    private final ReaderConnectionFailure connectionFailure;
    private final DisconnectReason disconnectReason;
    private final boolean inventoryRunning;

    private ReaderState(Builder builder) {
        transport = builder.transport;
        phase = builder.phase;
        moduleSubtype = builder.moduleSubtype;
        rawModuleSubtype = builder.rawModuleSubtype;
        protocol = builder.protocol;
        deviceName = builder.deviceName;
        address = builder.address;
        boardSerial = builder.boardSerial;
        boardVersion = builder.boardVersion;
        moduleSerial = builder.moduleSerial;
        moduleVersion = builder.moduleVersion;
        message = builder.message;
        errorCode = builder.errorCode;
        connectionFailure = builder.connectionFailure;
        disconnectReason = builder.disconnectReason;
        inventoryRunning = builder.inventoryRunning;
    }

    public static ReaderState disconnected() {
        return new Builder().build();
    }

    public Builder buildUpon() {
        return new Builder(this);
    }

    public boolean isConnected() {
        return phase == ConnectionPhase.CONNECTED;
    }

    /** Returns whether the underlying BLE or Wi-Fi transport is already available. */
    public boolean hasTransportLink() {
        return switch (phase) {
            case CONNECTING_DATA_CHANNEL, VERIFYING_MODULE, UPDATING_PARAMETERS, CONNECTED -> true;
            default -> false;
        };
    }

    /** Returns whether the transport is linked but the reader is not operation-ready yet. */
    public boolean isInitializing() {
        return phase == ConnectionPhase.VERIFYING_MODULE
                || phase == ConnectionPhase.UPDATING_PARAMETERS;
    }

    public TransportType getTransport() { return transport; }
    public ConnectionPhase getPhase() { return phase; }
    public ModuleSubtype getModuleSubtype() { return moduleSubtype; }
    public int getRawModuleSubtype() { return rawModuleSubtype; }
    public TagProtocol getProtocol() { return protocol; }
    public String getDeviceName() { return deviceName; }
    public String getAddress() { return address; }
    public String getBoardSerial() { return boardSerial; }
    public String getBoardVersion() { return boardVersion; }
    public String getModuleSerial() { return moduleSerial; }
    public String getModuleVersion() { return moduleVersion; }
    public String getMessage() { return message; }
    public int getErrorCode() { return errorCode; }
    public ReaderConnectionFailure getConnectionFailure() { return connectionFailure; }
    public DisconnectReason getDisconnectReason() { return disconnectReason; }
    public ReaderConnectionStatus getConnectionStatus() { return ReaderConnectionStatus.from(this); }
    public boolean isInventoryRunning() { return inventoryRunning; }

    public static final class Builder {
        private TransportType transport = TransportType.NONE;
        private ConnectionPhase phase = ConnectionPhase.DISCONNECTED;
        private ModuleSubtype moduleSubtype = ModuleSubtype.UNKNOWN;
        private int rawModuleSubtype = Integer.MIN_VALUE;
        private TagProtocol protocol = TagProtocol.ISO_18000_6C;
        private String deviceName = "";
        private String address = "";
        private String boardSerial = "";
        private String boardVersion = "";
        private String moduleSerial = "";
        private String moduleVersion = "";
        private String message = "";
        private int errorCode;
        private ReaderConnectionFailure connectionFailure = ReaderConnectionFailure.NONE;
        private DisconnectReason disconnectReason = DisconnectReason.NONE;
        private boolean inventoryRunning;

        public Builder() {}

        private Builder(ReaderState state) {
            transport = state.transport;
            phase = state.phase;
            moduleSubtype = state.moduleSubtype;
            rawModuleSubtype = state.rawModuleSubtype;
            protocol = state.protocol;
            deviceName = state.deviceName;
            address = state.address;
            boardSerial = state.boardSerial;
            boardVersion = state.boardVersion;
            moduleSerial = state.moduleSerial;
            moduleVersion = state.moduleVersion;
            message = state.message;
            errorCode = state.errorCode;
            connectionFailure = state.connectionFailure;
            disconnectReason = state.disconnectReason;
            inventoryRunning = state.inventoryRunning;
        }

        public Builder transport(@NonNull TransportType value) { transport = value; return this; }
        public Builder phase(@NonNull ConnectionPhase value) { phase = value; return this; }
        public Builder moduleSubtype(@NonNull ModuleSubtype value, int rawValue) { moduleSubtype = value; rawModuleSubtype = rawValue; return this; }
        public Builder protocol(@NonNull TagProtocol value) { protocol = value; return this; }
        public Builder device(@Nullable String name, @Nullable String value) { deviceName = safe(name); address = safe(value); return this; }
        public Builder versions(@Nullable String boardSerialValue, @Nullable String boardVersionValue,
                @Nullable String moduleSerialValue, @Nullable String moduleVersionValue) {
            boardSerial = safe(boardSerialValue);
            boardVersion = safe(boardVersionValue);
            moduleSerial = safe(moduleSerialValue);
            moduleVersion = safe(moduleVersionValue);
            return this;
        }
        public Builder message(@Nullable String value) { message = safe(value); return this; }
        public Builder errorCode(int value) { errorCode = value; return this; }
        public Builder connectionFailure(@NonNull ReaderConnectionFailure value) {
            connectionFailure = value;
            return this;
        }
        public Builder disconnectReason(@NonNull DisconnectReason value) { disconnectReason = value; return this; }
        public Builder inventoryRunning(boolean value) { inventoryRunning = value; return this; }

        public ReaderState build() { return new ReaderState(this); }

        private static String safe(@Nullable String value) { return value == null ? "" : value; }
    }
}
