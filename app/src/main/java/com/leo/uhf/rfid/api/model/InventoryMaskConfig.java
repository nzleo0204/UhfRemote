package com.leo.uhf.rfid.api.model;

import java.util.Arrays;

/**
 * 表示提交给读写器 Select 条件的不可变盘点掩码。
 */
public final class InventoryMaskConfig {
    public final int bank;
    public final int offsetBits;
    public final int lengthBits;
    private final byte[] mask;

    public InventoryMaskConfig(int bank, int offsetBits, int lengthBits, byte[] mask) {
        if (bank < 0 || bank > 3 || offsetBits < 0 || offsetBits > 0x00FFFFFF
                || lengthBits <= 0 || mask == null || mask.length == 0 || mask.length > 64
                || lengthBits > mask.length * 8) {
            throw new IllegalArgumentException("Invalid inventory mask configuration");
        }
        this.bank = bank;
        this.offsetBits = offsetBits;
        this.lengthBits = lengthBits;
        this.mask = Arrays.copyOf(mask, mask.length);
    }

    public byte[] getMask() {
        return Arrays.copyOf(mask, mask.length);
    }

    public int getMaskByteLength() {
        return mask.length;
    }
}
