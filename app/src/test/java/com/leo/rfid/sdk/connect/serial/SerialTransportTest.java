package com.leo.rfid.sdk.connect.serial;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.leo.rfid.sdk.model.ModuleSubtype;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/** 验证串口传输的连接、发送和关闭行为。 */
public class SerialTransportTest {
    @Test public void connectsWritesAndDisconnects() throws Exception {
        FakePort port = new FakePort();
        AtomicReference<String> failure = new AtomicReference<>();
        SerialConfig config = new SerialConfig("/dev/test", 115200, ModuleSubtype.R2000, 0);
        SerialTransport transport = new SerialTransport(config,
                new SerialPortManager((path, baud) -> port), new DelayPowerController(0),
                new SerialTransport.Listener() {
                    @Override public void onInboundData(byte[] data) { }
                    @Override public void onDisconnected(String message, int errorCode) {
                        failure.set(message);
                    }
                });

        transport.connect();
        assertTrue(transport.isConnected());
        transport.write(new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, port.output.toByteArray());
        transport.disconnect();
        assertFalse(transport.isConnected());
        assertTrue(failure.get() == null);
    }

    @Test public void rejectsWriteBeforeConnect() throws Exception {
        SerialConfig config = new SerialConfig("/dev/test", 115200, ModuleSubtype.R2000, 0);
        SerialTransport transport = new SerialTransport(config,
                new SerialPortManager((path, baud) -> new FakePort()), new DelayPowerController(0),
                new NoOpListener());
        try {
            transport.write(new byte[]{1});
            fail("write should fail while disconnected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("未连接"));
        }
    }

    private static final class NoOpListener implements SerialTransport.Listener {
        @Override public void onInboundData(byte[] data) { }
        @Override public void onDisconnected(String message, int errorCode) { }
    }

    private static final class FakePort implements SerialPortManager.Port {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private volatile boolean closed;

        @Override public InputStream input() {
            return new InputStream() {
                @Override public int read() throws IOException {
                    while (!closed) {
                        try { Thread.sleep(10); } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IOException(error);
                        }
                    }
                    return -1;
                }
            };
        }

        @Override public ByteArrayOutputStream output() { return output; }

        @Override public void close() {
            closed = true;
        }
    }
}
