package com.leo.rfid.sdk.inventory;

import com.leo.rfid.sdk.model.*;


/** Matches displayed inventory data against the active reader mask. */
public final class InventoryMaskMatcher {
    private InventoryMaskMatcher() {}

    public static boolean matches(InventoryMaskConfig config, TagProtocol protocol,
            InventoryArea area, int inventoryAddress, InventoryItem item) {
        if (config == null || protocol == null || area == null || item == null
                || area.getProtocol() != protocol) {
            return false;
        }
        Source source = sourceFor(config.bank, protocol, area, inventoryAddress, item);
        if (source == null) { return false; }
        int relativeOffset = config.offsetBits - source.baseOffsetBits;
        if (relativeOffset < 0 || relativeOffset + config.lengthBits > source.data.length * 8) {
            return false;
        }
        byte[] mask = config.getMask();
        for (int bit = 0; bit < config.lengthBits; bit++) {
            if (bitAt(source.data, relativeOffset + bit) != bitAt(mask, bit)) { return false; }
        }
        return true;
    }

    private static Source sourceFor(int bank, TagProtocol protocol, InventoryArea area,
            int inventoryAddress, InventoryItem item) {
        String value;
        int baseOffsetBits;
        switch (protocol) {
            case ISO_18000_6C -> {
                if (bank == 1) {
                    value = item.getId();
                    baseOffsetBits = 32;
                } else if ((bank == 0 && area == InventoryArea.C_EPC_RESERVED)
                        || (bank == 2 && area == InventoryArea.C_EPC_TID)
                        || (bank == 3 && area == InventoryArea.C_EPC_USER)) {
                    value = item.getData();
                    baseOffsetBits = inventoryAddress * 16;
                } else {
                    return null;
                }
            }
            case ISO_18000_6B -> {
                if (bank != 0) { return null; }
                value = item.getId();
                baseOffsetBits = 0;
            }
            case GJB_7377_1 -> {
                if (bank != 1) { return null; }
                value = item.getId();
                baseOffsetBits = 0;
            }
            case GB_T_29768 -> {
                if (bank == 1) {
                    value = item.getId();
                    baseOffsetBits = 0;
                } else if ((bank == 0 && area == InventoryArea.GB_CODE_INFO)
                        || (bank == 3 && area == InventoryArea.GB_CODE_USER)) {
                    value = item.getData();
                    baseOffsetBits = inventoryAddress * 16;
                } else {
                    return null;
                }
            }
            default -> {
                return null;
            }
        }
        try {
            return new Source(HexCodec.decode(value), baseOffsetBits);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static int bitAt(byte[] value, int bit) {
        return (value[bit / 8] >> (7 - bit % 8)) & 1;
    }

    private static final class Source {
        final byte[] data;
        final int baseOffsetBits;

        Source(byte[] data, int baseOffsetBits) {
            this.data = data;
            this.baseOffsetBits = baseOffsetBits;
        }
    }
}
