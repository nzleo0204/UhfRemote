package com.leo.remote.rfid.sdk.nativebridge;

import com.leo.remote.rfid.sdk.model.*;

/**
 * 定义原生 SDK 传输初始化、释放和模块信息读取能力。
 */
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
