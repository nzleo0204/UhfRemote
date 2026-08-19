package com.leo.uhf.rfid.sdk.connection.serial.jni;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 串口平台适配接口。
 *
 * <p>客户设备可以用 JNI、系统串口文件或其他硬件 SDK 实现此接口，RFID 会话层不依赖
 * 具体的原生库名称和包名。</p>
 */
public interface SerialPort {
    /** 返回串口输入流。 */
    InputStream input() throws IOException;

    /** 返回串口输出流。 */
    OutputStream output() throws IOException;

    /** 关闭串口并释放底层句柄。 */
    void close() throws IOException;
}
