package com.leo.rfid.sdk.connect.serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 串口物理层传输，负责端口打开、收发数据和异常断开通知。 */
public final class SerialTransport {
    public interface Listener {
        void onInboundData(byte[] data);
        void onDisconnected(String message, int errorCode);
    }

    private final SerialConfig config;
    private final SerialPortManager portManager;
    private final SerialPowerController powerController;
    private final Listener listener;
    private final Object lock = new Object();
    private volatile boolean connected;
    private SerialPortManager.Port port;
    private Thread readerThread;

    public SerialTransport(SerialConfig config, SerialPortManager portManager,
            SerialPowerController powerController, Listener listener) {
        if (config == null || portManager == null || powerController == null || listener == null) {
            throw new NullPointerException("串口连接参数不能为空");
        }
        this.config = config;
        this.portManager = portManager;
        this.powerController = powerController;
        this.listener = listener;
    }

    public void connect() throws IOException {
        synchronized (lock) {
            if (connected) { return; }
            powerController.powerOn();
            try {
                sleepAfterPowerOn();
                port = portManager.openPort(config.portPath, config.baudRate);
                connected = true;
                readerThread = new Thread(this::readLoop, "uhf-serial-reader");
                readerThread.setDaemon(true);
                readerThread.start();
            } catch (IOException | RuntimeException error) {
                powerController.powerOff();
                throw error;
            }
        }
    }

    public boolean isConnected() { return connected; }

    public void write(byte[] data) throws IOException {
        if (data == null) { throw new NullPointerException("data"); }
        SerialPortManager.Port current;
        synchronized (lock) { current = port; }
        if (!connected || current == null) {
            throw new IOException("串口未连接");
        }
        OutputStream output = current.output();
        output.write(data);
        output.flush();
    }

    public void disconnect() {
        SerialPortManager.Port current;
        synchronized (lock) {
            connected = false;
            current = port;
            port = null;
        }
        closeQuietly(current);
        powerController.powerOff();
    }

    private void readLoop() {
        try {
            SerialPortManager.Port current;
            synchronized (lock) { current = port; }
            if (current == null) { return; }
            InputStream input = current.input();
            byte[] buffer = new byte[1024];
            int count;
            while (connected && (count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    byte[] data = new byte[count];
                    System.arraycopy(buffer, 0, data, 0, count);
                    listener.onInboundData(data);
                }
            }
            if (connected) { notifyDisconnected("串口已关闭", -64); }
        } catch (IOException error) {
            if (connected) { notifyDisconnected(error.getMessage(), -65); }
        }
    }

    private void notifyDisconnected(String message, int errorCode) {
        disconnect();
        listener.onDisconnected(message == null ? "串口连接异常" : message, errorCode);
    }

    private void sleepAfterPowerOn() throws IOException {
        try {
            Thread.sleep(powerController.getDelayAfterPowerOn());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("串口上电等待被中断", error);
        }
    }

    private void closeQuietly(SerialPortManager.Port current) {
        if (current == null) { return; }
        try { portManager.closePort(current); } catch (IOException ignored) { }
    }
}
