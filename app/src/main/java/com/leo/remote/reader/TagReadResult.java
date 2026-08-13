package com.leo.remote.reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;

/** Complete result returned by the SDK read operation. */
public final class TagReadResult {
    private final byte[] data;
    private final byte[] epc;
    private final int rssi;

    public TagReadResult(@Nullable byte[] data, @Nullable byte[] epc, int rssi) {
        this.data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
        this.epc = epc == null ? new byte[0] : Arrays.copyOf(epc, epc.length);
        this.rssi = rssi;
    }

    @NonNull
    public byte[] getData() { return Arrays.copyOf(data, data.length); }

    @NonNull
    public byte[] getEpc() { return Arrays.copyOf(epc, epc.length); }

    public int getRssi() { return rssi; }
}
