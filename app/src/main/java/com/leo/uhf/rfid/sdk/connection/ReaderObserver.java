package com.leo.uhf.rfid.sdk.connection;

import com.leo.uhf.rfid.sdk.model.*;

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
    /** 单标签掩码与盘点掩码相互独立。 */
    default void onSingleTagMaskChanged(@Nullable InventoryMaskConfig config) {}
    default void onCurrentTagChanged(ReaderTag tag) {}
    /** 设备链路未收到用户断开请求却意外消失时回调。 */
    default void onReaderUnexpectedDisconnect(DisconnectReason reason) {}
}
