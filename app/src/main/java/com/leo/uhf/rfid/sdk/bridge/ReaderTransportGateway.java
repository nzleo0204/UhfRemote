package com.leo.uhf.rfid.sdk.bridge;

import com.leo.uhf.rfid.sdk.model.*;

/**
 * 定义原生 SDK 传输初始化、释放和模块信息读取能力。
 */
public interface ReaderTransportGateway {
    interface OutboundDataListener { void onOutboundData(byte[] data); }

    int initialize();
    void deinitialize();
    void useRm70xx();
    /** 设置直连串口模块的 SDK 解析类型。 */
    default void setModuleSubtype(ModuleSubtype subtype) { }
    void setTransport(ConnectionType transport);
    int connectNetwork(String address, int port);
    int closeNetwork();
    /** 打开由原生 Linkage 管理的串口设备节点。 */
    int openSerial(String path, int baudRate);
    /** 关闭当前原生串口设备节点。 */
    int closeSerial();
    void setOutboundDataListener(OutboundDataListener listener);
    void pushRemoteData(byte[] data);
    ReaderModuleInfo readModuleInfo() throws ReaderException;
}
