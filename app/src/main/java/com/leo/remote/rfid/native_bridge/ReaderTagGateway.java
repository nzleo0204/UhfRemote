package com.leo.remote.rfid.native_bridge;

import com.leo.remote.rfid.sdk.model.*;


public interface ReaderTagGateway {
    TagReadResult readTag(TagProtocol protocol, int length, int address, int bank,
            byte[] password, int timeoutMs) throws ReaderException;
    int writeTag(TagProtocol protocol, int length, int address, int bank, byte[] password,
            byte[] data, int timeoutMs);
    int lockTag(byte[] password, int bank, int policy, int timeoutMs);
    int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs);
}
