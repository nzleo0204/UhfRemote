package com.leo.remote.reader;

import androidx.annotation.Nullable;
import java.util.List;

public interface ReaderObserver {
    default void onReaderStateChanged(ReaderState state) {}
    default void onReaderConfigurationChanged(ReaderConfiguration configuration) {}
    default void onInventoryChanged(List<InventoryItem> items, long totalReads) {}
    default void onInventoryMaskChanged(@Nullable InventoryMaskConfig config) {}
    default void onCurrentTagChanged(ReaderTag tag) {}
    /** Called when a live reader link disappears without a user disconnect request. */
    default void onReaderUnexpectedDisconnect(DisconnectReason reason) {}
}
