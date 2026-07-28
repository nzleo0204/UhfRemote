package com.leo.remote.reader;

import java.util.List;

public interface ReaderObserver {
    default void onReaderStateChanged(ReaderState state) {}
    default void onReaderConfigurationChanged(ReaderConfiguration configuration) {}
    default void onInventoryChanged(List<InventoryItem> items, long totalReads) {}
    default void onCurrentTagChanged(ReaderTag tag) {}
}
