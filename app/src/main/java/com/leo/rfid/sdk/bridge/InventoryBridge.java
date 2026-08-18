package com.leo.rfid.sdk.bridge;

import com.leo.rfid.sdk.model.*;

/**
 * 定义标签盘点及盘点参数设置的底层能力。
 */
public interface InventoryBridge {
    interface InventoryListener { void onTag(ReaderTag tag); }
    interface InventoryStopListener { void onInventoryStopped(int status); }

    int applyInventoryParams(TagProtocol protocol, int area, int address, int wordLen);
    int startInventory(int mode, int maskFlag);
    int stopInventory();
    void setInventoryListener(InventoryListener listener);
    void setInventoryStopListener(InventoryStopListener listener);
    int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime, int inventoryOffTime);
    int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
            InventoryMaskConfig config);
    int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype, int selected);
    int setTargetMask(TagProtocol protocol, ModuleSubtype subtype, ReaderTag tag);
    int clearTargetMask(TagProtocol protocol, ModuleSubtype subtype, int selected);
}
