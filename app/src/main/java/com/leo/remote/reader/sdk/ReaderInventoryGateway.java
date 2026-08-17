package com.leo.remote.reader.sdk;

import com.leo.remote.reader.model.*;


public interface ReaderInventoryGateway {
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
