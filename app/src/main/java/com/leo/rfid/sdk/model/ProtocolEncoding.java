package com.leo.rfid.sdk.model;

/**
 * 定义协议数据在 SDK 与界面之间使用的编码方式。
 */
public final class ProtocolEncoding {

    private ProtocolEncoding() {}

    public static int encodeAddress(TagProtocol protocol, int address, int blockLengthOrRetry) {
        if (address < 0 || address > 0x00FFFFFF || blockLengthOrRetry < 0 || blockLengthOrRetry > 0xFF) {
            throw new IllegalArgumentException("Address or length is out of range");
        }
        if (protocol == TagProtocol.ISO_18000_6B || protocol == TagProtocol.GJB_7377_1
                || protocol == TagProtocol.GB_T_29768) {
            return (blockLengthOrRetry << 24) | address;
        }
        return address;
    }

    public static int defaultInventoryBank(TagProtocol protocol) {
        return switch (protocol) {
            case ISO_18000_6C -> 1;
            case ISO_18000_6B -> 0;
            case GJB_7377_1, GB_T_29768 -> 2;
        };
    }

    public static int encodeBank(TagProtocol protocol, int bank, int gbUserSubBank) {
        if (bank < 0 || bank > 3) {
            throw new IllegalArgumentException("Bank is out of range");
        }
        if (protocol != TagProtocol.GB_T_29768) {
            return bank;
        }
        if (gbUserSubBank < 0 || gbUserSubBank > 0x0F) {
            throw new IllegalArgumentException("GB user sub-bank is out of range");
        }
        return bank == 3 ? (bank << 4) | gbUserSubBank : bank << 4;
    }

    public static int targetMaskBank(TagProtocol protocol) {
        return encodeBank(protocol, 1, 0);
    }

    public static int targetMaskOffset(TagProtocol protocol) {
        return encodeMaskOffset(protocol, protocol == TagProtocol.ISO_18000_6C ? 32 : 0);
    }

    /** 返回指定掩码存储区在当前协议下的初始位偏移。 */
    public static int defaultMaskOffsetBits(TagProtocol protocol, int bankPosition) {
        return switch (protocol) {
            case ISO_18000_6C -> bankPosition == 1 ? 32 : 0;
            case ISO_18000_6B, GJB_7377_1, GB_T_29768 -> 0;
        };
    }

    public static int encodeMaskOffset(TagProtocol protocol, int offset) {
        if (offset < 0 || offset > 0x00FFFFFF) {
            throw new IllegalArgumentException("Mask offset is out of range");
        }
        if (protocol == TagProtocol.GJB_7377_1 || protocol == TagProtocol.GB_T_29768) {
            if (offset > 0xFF) {
                throw new IllegalArgumentException("GB/GJB mask offset is out of range");
            }
            return offset << 24;
        }
        return offset;
    }

    public static int writeLength(TagProtocol protocol, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Write data is required");
        }
        if (protocol == TagProtocol.ISO_18000_6B) {
            return data.length;
        }
        if ((data.length & 1) != 0) {
            throw new IllegalArgumentException("Write data must contain complete 16-bit words");
        }
        return data.length / 2;
    }
}
