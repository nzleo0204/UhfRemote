package com.leo.rfid.sdk.connect.serial;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 封装串口文件节点和可替换的客户 JNI 串口实现。 */
public final class SerialPortManager {
    /** 可由测试或客户 JNI 适配器实现的端口句柄。 */
    public interface Port {
        InputStream input() throws IOException;
        OutputStream output() throws IOException;
        void close() throws IOException;
    }

    /** 端口打开工厂，避免核心会话依赖具体 JNI。 */
    public interface Factory {
        Port open(String path, int baudRate) throws IOException;
    }

    private final Factory factory;

    public SerialPortManager() {
        this(SerialPortManager::openFilePort);
    }

    public SerialPortManager(Factory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        this.factory = factory;
    }

    public Port openPort(String path, int baudRate) throws IOException {
        return factory.open(path, baudRate);
    }

    public void closePort(Port port) throws IOException {
        if (port != null) {
            port.close();
        }
    }

    public boolean isPortAvailable(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.canRead() && file.canWrite();
    }

    private static Port openFilePort(String path, int baudRate) throws IOException {
        if (baudRate != SerialConfig.DEFAULT_BAUD_RATE) {
            throw new IOException("默认串口实现仅支持 115200 波特率");
        }
        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("串口不存在: " + path);
        }
        if (!file.canRead() || !file.canWrite()) {
            throw new IOException("串口权限不足: " + path);
        }
        return new FilePort(new FileInputStream(file), new FileOutputStream(file, true));
    }

    private static final class FilePort implements Port {
        private final InputStream input;
        private final OutputStream output;

        private FilePort(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override public InputStream input() { return input; }
        @Override public OutputStream output() { return output; }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try { input.close(); } catch (IOException error) { failure = error; }
            try { output.close(); } catch (IOException error) {
                if (failure == null) { failure = error; }
            }
            if (failure != null) { throw failure; }
        }
    }
}
