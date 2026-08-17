package com.leo.remote.reader.persistence;

import com.leo.remote.reader.model.*;


import com.tencent.mmkv.MMKV;

/** Module-scoped persistent fallback values for reader configuration. */
public final class ReaderConfigCache implements ReaderConfigurationStore {
    private static final String MMKV_ID = "reader_config_cache";
    private final MMKV mmkv;

    public ReaderConfigCache() {
        mmkv = MMKV.mmkvWithID(MMKV_ID);
    }

    @Override
    public void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration config) {
        String prefix = prefix(subtype);
        mmkv.encode(prefix + "power", config.powerTenthsDbm);
        mmkv.encode(prefix + "inventoryMode", config.inventoryMode);
        mmkv.encode(prefix + "blfProfile", config.blfProfile);
        mmkv.encode(prefix + "session", config.session);
        mmkv.encode(prefix + "target", config.target);
        mmkv.encode(prefix + "dynamicQ", config.dynamicQ);
        mmkv.encode(prefix + "qValue", config.qValue);
        mmkv.encode(prefix + "qMinValue", config.qMinValue);
        mmkv.encode(prefix + "qMaxValue", config.qMaxValue);
        mmkv.encode(prefix + "qRetryCount", config.qRetryCount);
        mmkv.encode(prefix + "qThresholdMultiplier", config.qThresholdMultiplier);
        mmkv.encode(prefix + "qToggleTarget", config.qToggleTarget);
        mmkv.encode(prefix + "qRepeatUntilNoTags", config.qRepeatUntilNoTags);
        mmkv.encode(prefix + "inventoryArea", config.inventoryArea);
        mmkv.encode(prefix + "inventoryAddress", config.inventoryAddress);
        mmkv.encode(prefix + "inventoryWordLen", config.inventoryWordLen);
    }

    @Override
    public ReaderConfiguration loadConfiguration(ModuleSubtype subtype) {
        String prefix = prefix(subtype);
        if (!mmkv.contains(prefix + "power")) { return null; }
        return new ReaderConfiguration(
                mmkv.decodeInt(prefix + "power", 200),
                mmkv.decodeInt(prefix + "inventoryMode", 0),
                mmkv.decodeInt(prefix + "blfProfile", 0),
                mmkv.decodeInt(prefix + "session", 0),
                mmkv.decodeInt(prefix + "target", 0),
                mmkv.decodeBool(prefix + "dynamicQ", true),
                mmkv.decodeInt(prefix + "qValue", 7),
                mmkv.decodeInt(prefix + "qMinValue", 0),
                mmkv.decodeInt(prefix + "qMaxValue", 15),
                mmkv.decodeInt(prefix + "qRetryCount", 0),
                mmkv.decodeInt(prefix + "qThresholdMultiplier", 1),
                mmkv.decodeInt(prefix + "qToggleTarget", 1),
                mmkv.decodeInt(prefix + "qRepeatUntilNoTags", 0),
                mmkv.decodeInt(prefix + "inventoryArea", 0),
                mmkv.decodeInt(prefix + "inventoryAddress", 0),
                mmkv.decodeInt(prefix + "inventoryWordLen",
                        ReaderConfiguration.DEFAULT_INVENTORY_WORD_LEN));
    }

    /** Keeps the handshake Sel value separate from the user-visible configuration. */
    @Override
    public void saveSelected(ModuleSubtype subtype, int selected) {
        mmkv.encode(prefix(subtype) + "selected", selected);
    }

    @Override
    public int loadSelected(ModuleSubtype subtype) {
        return mmkv.decodeInt(prefix(subtype) + "selected", 0);
    }

    public static ReaderConfiguration getDefaultConfiguration(ModuleSubtype subtype) {
        return ReaderConfiguration.defaultsFor(subtype);
    }

    private static String prefix(ModuleSubtype subtype) {
        return subtype.name() + "_";
    }
}
