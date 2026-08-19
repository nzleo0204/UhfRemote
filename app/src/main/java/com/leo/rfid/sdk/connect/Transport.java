package com.leo.rfid.sdk.connect;

import java.io.IOException;

/**
 * 物理连接的最小生命周期抽象。
 *
 * <p>当前串口实现使用该接口；BLE 和 Wi-Fi 还需要各自的异步连接回调，因此继续
 * 使用对应的专用传输接口。</p>
 */
public interface Transport {
    /** 建立物理连接。 */
    void connect() throws IOException;

    /** 关闭物理连接并释放资源。 */
    void disconnect();

    /** 返回物理连接是否可用。 */
    boolean isConnected();

    /** 向物理连接写入数据。 */
    void write(byte[] data) throws IOException;
}
