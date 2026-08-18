package com.leo.remote.rfid.sdk.nativebridge;

import com.leo.remote.rfid.sdk.model.*;

/**
 * 定义单标签读取、写入、锁定和销毁操作的底层能力。
 */
public interface ReaderTagGateway {
    TagReadResult readTag(TagProtocol protocol, int length, int address, int bank,
            byte[] password, int timeoutMs) throws ReaderException;
    int writeTag(TagProtocol protocol, int length, int address, int bank, byte[] password,
            byte[] data, int timeoutMs);
    int lockTag(byte[] password, int bank, int policy, int timeoutMs);
    int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs);
}
