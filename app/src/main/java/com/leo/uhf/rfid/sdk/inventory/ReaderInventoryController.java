package com.leo.uhf.rfid.sdk.inventory;

import com.leo.uhf.rfid.sdk.model.*;
import com.leo.uhf.rfid.sdk.storage.ReaderConfigurationStore;
import com.leo.uhf.rfid.sdk.bridge.ReaderConfigurationGateway;
import com.leo.uhf.rfid.sdk.bridge.InventoryBridge;
import com.leo.uhf.rfid.sdk.connection.ReaderStatePublisher;
import com.leo.uhf.rfid.sdk.tag.ChipModelFormatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * 管理盘点结果聚合、掩码状态及同步盘点 SDK 操作。
 */
public final class ReaderInventoryController {
    private static final int STATUS_UNSUPPORTED = -1001;

    private final InventoryBridge gateway;
    private final ReaderConfigurationGateway configurationGateway;
    private final ReaderConfigurationStore cache;
    private final ReaderStatePublisher publisher;
    private final BiConsumer<Runnable, Long> delayedDispatcher;
    private final InventoryAccumulator accumulator = new InventoryAccumulator();
    private final AtomicBoolean updateScheduled = new AtomicBoolean();
    private final InventoryMaskSelection maskSelection = new InventoryMaskSelection();

    private volatile InventoryMaskConfig mask;
    private volatile TagProtocol maskProtocol;
    private volatile boolean maskApplied;

    public ReaderInventoryController(@NonNull InventoryBridge gateway,
            @NonNull ReaderConfigurationGateway configurationGateway,
            @NonNull ReaderConfigurationStore cache, @NonNull ReaderStatePublisher publisher,
            @NonNull BiConsumer<Runnable, Long> delayedDispatcher) {
        this.gateway = gateway;
        this.configurationGateway = configurationGateway;
        this.cache = cache;
        this.publisher = publisher;
        this.delayedDispatcher = delayedDispatcher;
    }

    public void onTag(@NonNull ReaderTag tag) {
        accumulator.add(tag.id, tag.data, tag.rssi, tag.count, ChipModelFormatter.format(tag));
        if (updateScheduled.compareAndSet(false, true)) {
            delayedDispatcher.accept(() -> {
                updateScheduled.set(false);
                publishInventory();
            }, 100L);
        }
    }

    public int start(@NonNull TagProtocol protocol, @NonNull ModuleSubtype subtype,
            @Nullable ReaderConfiguration configuration, int inventoryMode) {
        int area = configuration == null ? 0 : configuration.inventoryArea;
        int address = configuration == null ? 0 : configuration.inventoryAddress;
        int wordLen = configuration == null ? 0 : configuration.inventoryWordLen;
        int status = gateway.applyInventoryParams(protocol, area,
                area == 0 ? 0 : address, area == 0 ? 0 : wordLen);
        InventoryMaskConfig activeMask = mask;
        if (status == 0 && activeMask != null) {
            if (maskProtocol != protocol) {
                discardMask();
            } else {
                status = gateway.applyInventoryMask(protocol, subtype, activeMask);
                maskApplied = status == 0;
            }
        }
        if (status == 0 && inventoryMode == 2) {
            int schedulerStatus = gateway.setLowPowerScheduler(0, 30, 100);
            if (schedulerStatus != STATUS_UNSUPPORTED && schedulerStatus != 0) {
                return schedulerStatus;
            }
        }
        return status == 0 ? gateway.startInventory(inventoryMode, maskApplied ? 1 : 0) : status;
    }

    public int stop(boolean running) {
        return running ? gateway.stopInventory() : 0;
    }

    public void clearInventory() {
        resetInventory();
        publishInventory();
    }

    public void resetInventory() {
        accumulator.clear();
    }

    @NonNull
    public List<InventoryItem> snapshot() {
        return accumulator.snapshot();
    }

    public long getTotalReads() {
        return accumulator.getTotalReads();
    }

    @Nullable
    public InventoryMaskConfig getMask() {
        return mask;
    }

    @Nullable
    public TagProtocol getMaskProtocol() {
        return maskProtocol;
    }

    public boolean isMaskApplied() {
        return maskApplied;
    }

    public void setMaskApplied(boolean applied) {
        maskApplied = applied;
    }

    public void activateMask(@NonNull InventoryMaskConfig config, @NonNull TagProtocol protocol) {
        mask = config;
        maskProtocol = protocol;
        maskApplied = true;
        publisher.publishMask(config);
    }

    public void discardMask() {
        mask = null;
        maskProtocol = null;
        maskApplied = false;
        maskSelection.clear();
        publisher.publishMask(null);
    }

    public boolean isSelectionCaptured() {
        return maskSelection.isCaptured();
    }

    public boolean captureSelection(@NonNull ModuleSubtype subtype) {
        try {
            if (!maskSelection.capture(configurationGateway.getQueryValues(subtype))) { return false; }
            cache.saveSelected(subtype, maskSelection.restoreValue());
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public int restoreValue(@NonNull ModuleSubtype subtype) {
        return maskSelection.isCaptured()
                ? maskSelection.restoreValue() : cache.loadSelected(subtype);
    }

    public void clearSelection() {
        maskSelection.clear();
    }

    public void publishInventory() {
        publisher.publishInventoryUpdate(accumulator.snapshot(), accumulator.getTotalReads());
    }
}
