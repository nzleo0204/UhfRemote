package com.leo.uhf.rfid.sdk.connection.serial;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.leo.uhf.rfid.sdk.connection.serial.jni.SerialPort;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

/** 验证串口管理器的可替换端口工厂和可用性判断。 */
public class SerialPortManagerTest {
    @Test public void delegatesOpenToFactory() throws Exception {
        SerialPort port = new FakePort();
        SerialPortManager manager = new SerialPortManager((path, baud) -> port);
        assertTrue(manager.openPort("/dev/test", 115200) == port);
        assertFalse(new SerialPortManager().isPortAvailable("/not/existing"));
    }

    private static final class FakePort implements SerialPort {
        @Override public java.io.InputStream input() { return new ByteArrayInputStream(new byte[0]); }
        @Override public java.io.OutputStream output() { return new ByteArrayOutputStream(); }
        @Override public void close() throws IOException { }
    }
}
