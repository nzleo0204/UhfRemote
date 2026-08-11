package com.leo.remote.reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** Owns inventory accumulation, mask state, and synchronous inventory SDK operations. */
public final class ReaderInventoryController {
    private static final int STATUS_UNSUPPORTED = -1001;

    private final UhfSdkGateway gateway;
    private final ReaderConfigurationStore cache;
    private final ReaderStatePublisher publisher;
    private final BiConsumer<Runnable, Long> delayedDispatcher;
    private final InventoryAccumulator accumulator = new InventoryAccumulator();
    private final AtomicBoolean updateScheduled = new AtomicBoolean();
    private final InventoryMaskSelection maskSelection = new InventoryMaskSelection();

    private volatile InventoryMaskConfig mask;
    private volatile TagProtocol maskProtocol;
    private volatile boolean maskApplied;

    ReaderInventoryController(@NonNull UhfSdkGateway gateway,
            @NonNull ReaderConfigurationStore cache, @NonNull ReaderStatePublisher publisher,
            @NonNull BiConsumer<Runnable, Long> delayedDispatcher) {
        this.gateway = gateway;
        this.cache = cache;
        this.publisher = publisher;
        this.delayedDispatcher = delayedDispatcher;
    }

    void onTag(@NonNull ReaderTag tag) {
        accumulator.add(tag.id, tag.data, tag.rssi, tag.count, ChipModelFormatter.format(tag));
        if (updateScheduled.compareAndSet(false, true)) {
            delayedDispatcher.accept(() -> {
                updateScheduled.set(false);
                publishInventory();
            }, 100L);
        }
    }

    int start(@NonNull TagProtocol protocol, @NonNull ModuleSubtype subtype,
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

    int stop(boolean running) {
        return running ? gateway.stopInventory() : 0;
    }

    void clearInventory() {
        resetInventory();
        publishInventory();
    }

    void resetInventory() {
        accumulator.clear();
    }

    @NonNull
    List<InventoryItem> snapshot() {
        return accumulator.snapshot();
    }

    long getTotalReads() {
        return accumulator.getTotalReads();
    }

    @Nullable
    InventoryMaskConfig getMask() {
        return mask;
    }

    @Nullable
    TagProtocol getMaskProtocol() {
        return maskProtocol;
    }

    boolean isMaskApplied() {
        return maskApplied;
    }

    void setMaskApplied(boolean applied) {
        maskApplied = applied;
    }

    void activateMask(@NonNull InventoryMaskConfig config, @NonNull TagProtocol protocol) {
        mask = config;
        maskProtocol = protocol;
        maskApplied = true;
        publisher.publishMask(config);
    }

    void discardMask() {
        mask = null;
        maskProtocol = null;
        maskApplied = false;
        maskSelection.clear();
        publisher.publishMask(null);
    }

    boolean isSelectionCaptured() {
        return maskSelection.isCaptured();
    }

    boolean captureSelection(@NonNull ModuleSubtype subtype) {
        try {
            if (!maskSelection.capture(gateway.getQueryValues(subtype))) { return false; }
            cache.saveSelected(subtype, maskSelection.restoreValue());
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    int restoreValue(@NonNull ModuleSubtype subtype) {
        return maskSelection.isCaptured()
                ? maskSelection.restoreValue() : cache.loadSelected(subtype);
    }

    void clearSelection() {
        maskSelection.clear();
    }

    void publishInventory() {
        publisher.publishInventoryUpdate(accumulator.snapshot(), accumulator.getTotalReads());
    }
}
