package com.leo.rfid.sdk.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;

/**
 * 保存 SDK 单标签读取返回的数据、完整 EPC 和附加信息。
 */
public final class TagReadResult {
    private final byte[] data;
    private final byte[] epc;
    private final int rssi;
    private final String chipModel;
    private final int tidPrefix;

    public TagReadResult(@Nullable byte[] data, @Nullable byte[] epc, int rssi) {
        this(data, epc, rssi, "", 0);
    }

    public TagReadResult(@Nullable byte[] data, @Nullable byte[] epc, int rssi,
            String chipModel, int tidPrefix) {
        this.data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
        this.epc = epc == null ? new byte[0] : Arrays.copyOf(epc, epc.length);
        this.rssi = rssi;
        this.chipModel = chipModel == null ? "" : chipModel;
        this.tidPrefix = tidPrefix;
    }

    @NonNull
    public byte[] getData() { return Arrays.copyOf(data, data.length); }

    @NonNull
    public byte[] getEpc() { return Arrays.copyOf(epc, epc.length); }

    public int getRssi() { return rssi; }

    @NonNull
    public String getChipModel() { return chipModel; }

    public int getTidPrefix() { return tidPrefix; }
}
