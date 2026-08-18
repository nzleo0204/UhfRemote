package com.leo.rfid.sdk.connect;

import com.leo.rfid.sdk.model.*;

import androidx.annotation.Nullable;
import java.util.List;

/**
 * 接收读写器会话状态、盘点结果和参数变化的观察者接口。
 */
public interface ReaderObserver {
    default void onReaderStateChanged(ReaderState state) {}
    default void onReaderConfigurationChanged(ReaderConfiguration configuration) {}
    default void onInventoryChanged(List<InventoryItem> items, long totalReads) {}
    default void onInventoryMaskChanged(@Nullable InventoryMaskConfig config) {}
    /** Single-tag masks are independent from inventory masks. */
    default void onSingleTagMaskChanged(@Nullable InventoryMaskConfig config) {}
    default void onCurrentTagChanged(ReaderTag tag) {}
    /** Called when a live reader link disappears without a user disconnect request. */
    default void onReaderUnexpectedDisconnect(DisconnectReason reason) {}
}
