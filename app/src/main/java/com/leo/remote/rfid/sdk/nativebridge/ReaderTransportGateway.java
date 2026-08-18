package com.leo.remote.rfid.sdk.nativebridge;

import com.leo.remote.rfid.sdk.model.*;


public interface ReaderTransportGateway {
    interface OutboundDataListener { void onOutboundData(byte[] data); }

    int initialize();
    void deinitialize();
    void useRm70xx();
    void setTransport(TransportType transport);
    int connectNetwork(String address, int port);
    int closeNetwork();
    void setOutboundDataListener(OutboundDataListener listener);
    void pushRemoteData(byte[] data);
    ReaderModuleInfo readModuleInfo() throws ReaderException;
}
