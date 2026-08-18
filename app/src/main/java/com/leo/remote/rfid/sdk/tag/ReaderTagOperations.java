package com.leo.remote.rfid.sdk.tag;

import com.leo.remote.rfid.sdk.model.*;
import com.leo.remote.rfid.sdk.nativebridge.ReaderTagGateway;
import com.leo.remote.rfid.sdk.connection.ReaderStatePublisher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Owns the selected tag, single-tag mask, and direct tag SDK operations. */
public final class ReaderTagOperations {
    private final ReaderTagGateway gateway;
    private final ReaderStatePublisher publisher;

    private volatile ReaderTag currentTag;
    private volatile InventoryMaskConfig singleTagMask;

    public ReaderTagOperations(@NonNull ReaderTagGateway gateway,
            @NonNull ReaderStatePublisher publisher) {
        this.gateway = gateway;
        this.publisher = publisher;
    }

    @Nullable
    public ReaderTag getCurrentTag() {
        return currentTag;
    }

    @Nullable
    public InventoryMaskConfig getSingleTagMask() {
        return singleTagMask;
    }

    public void setSingleTagMask(@Nullable InventoryMaskConfig config) {
        singleTagMask = config;
        publisher.publishSingleTagMask(config);
    }

    public void clearCurrentTag() {
        if (singleTagMask != null) { setSingleTagMask(null); }
        if (currentTag != null) {
            currentTag = null;
            publisher.publishCurrentTag(null);
        }
    }

    public TagReadResult read(TagProtocol protocol, int length, int address, int bank, byte[] password)
            throws ReaderException {
        TagReadResult result = gateway.readTag(protocol, length, address, bank, password, 2000);
        byte[] epc = result.getEpc();
        if (epc.length == 0 && protocol == TagProtocol.ISO_18000_6C && bank == 1) {
            epc = result.getData();
        }
        if (epc.length > 0) {
            ReaderTag tag = new ReaderTag(HexCodec.encode(epc, epc.length), "", result.getRssi(),
                    0, 1, result.getChipModel(), result.getTidPrefix());
            currentTag = tag;
            publisher.publishCurrentTag(tag);
        }
        return result;
    }

    public int write(TagProtocol protocol, int length, int address, int bank, byte[] password,
            byte[] data) {
        return gateway.writeTag(protocol, length, address, bank, password, data, 2500);
    }

    public int lock(byte[] password, int bank, int policy) {
        return gateway.lockTag(password, bank, policy, 2500);
    }

    public int kill(byte[] accessPassword, byte[] killPassword) {
        return gateway.killTag(accessPassword, killPassword, 2500);
    }
}
