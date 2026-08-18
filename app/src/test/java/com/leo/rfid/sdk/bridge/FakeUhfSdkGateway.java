package com.leo.rfid.sdk.bridge;

import com.leo.rfid.sdk.model.*;

/**
 * 为 JVM 测试提供不依赖原生库的读写器网关 Fake。
 */
public final class FakeUhfSdkGateway implements ReaderTransportGateway,
        ReaderConfigurationGateway, InventoryBridge, ReaderTagGateway {
    public int powerStatus;
    public int blfStatus;
    public int sessionStatus;
    public int inventoryAreaStatus;
    public int applyInventoryStatus;
    public int startStatus;
    public int stopStatus;
    public int lowPowerStatus;
    public int applyMaskStatus;
    public int clearMaskStatus;
    public int writeStatus;
    public int lockStatus;
    public int killStatus;
    public int lastPower;
    public int lastBlf;
    public int lastSession;
    public int lastSelected;
    public int lastQ;
    public int lastInventoryArea;
    public int lastInventoryMode;
    public int lastMaskFlag;
    public TagReadResult readResult = new TagReadResult(new byte[0], new byte[0], 0);
    public int[] queryValues = new int[] {0, 0, 0};

    @Override public int initialize() { return 0; }
    @Override public void deinitialize() {}
    @Override public void useRm70xx() {}
    @Override public void setTransport(TransportType transport) {}
    @Override public int connectNetwork(String address, int port) { return 0; }
    @Override public int closeNetwork() { return 0; }
    @Override public void setOutboundDataListener(OutboundDataListener listener) {}
    @Override public void pushRemoteData(byte[] data) {}
    @Override public ReaderModuleInfo readModuleInfo() { return null; }
    @Override public int setProtocol(TagProtocol protocol) { return 0; }
    @Override public int applyInventoryParams(TagProtocol protocol, int area, int address,
            int wordLen) { return applyInventoryStatus; }
    @Override public int startInventory(int mode, int maskFlag) {
        lastInventoryMode = mode;
        lastMaskFlag = maskFlag;
        return startStatus;
    }
    @Override public int stopInventory() { return stopStatus; }
    @Override public void setInventoryListener(InventoryListener listener) {}
    @Override public void setInventoryStopListener(InventoryStopListener listener) {}
    @Override public int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime,
            int inventoryOffTime) { return lowPowerStatus; }
    @Override public ReaderConfiguration readConfiguration(ModuleSubtype subtype) { return null; }

    @Override
    public int setPowerTenthsDbm(int powerTenthsDbm) {
        lastPower = powerTenthsDbm;
        return powerStatus;
    }

    @Override
    public int setBlfProfile(int profile) {
        lastBlf = profile;
        return blfStatus;
    }

    @Override
    public int setSession(ModuleSubtype subtype, int session, int target, int selected) {
        lastSession = session;
        lastSelected = selected;
        return sessionStatus;
    }

    @Override
    public int setInventoryArea(int area, int address, int wordLen) {
        lastInventoryArea = area;
        return inventoryAreaStatus;
    }

    @Override public int[] getInventoryArea() { return new int[] {0, 0, 0}; }
    @Override public Integer getPowerTenthsDbm() { return lastPower; }
    @Override public Integer getBlfProfile() { return lastBlf; }
    @Override public int[] getQueryValues(ModuleSubtype subtype) { return queryValues; }
    @Override public ReaderQParams getQParams(ModuleSubtype subtype) {
        return ReaderQParams.fixed(lastQ, 0, 1, 0);
    }
    @Override public int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
            InventoryMaskConfig config) { return applyMaskStatus; }
    @Override public int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
            int selected) { return clearMaskStatus; }
    @Override public int setTargetMask(TagProtocol protocol, ModuleSubtype subtype,
            ReaderTag tag) { return 0; }
    @Override public int clearTargetMask(TagProtocol protocol, ModuleSubtype subtype,
            int selected) { return 0; }
    @Override public TagReadResult readTag(TagProtocol protocol, int length, int address, int bank,
            byte[] password, int timeoutMs) { return readResult; }
    @Override public int writeTag(TagProtocol protocol, int length, int address, int bank,
            byte[] password, byte[] data, int timeoutMs) { return writeStatus; }
    @Override public int lockTag(byte[] password, int bank, int policy, int timeoutMs) {
        return lockStatus;
    }
    @Override public int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs) {
        return killStatus;
    }
}
