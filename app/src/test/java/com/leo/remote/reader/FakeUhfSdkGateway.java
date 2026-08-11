package com.leo.remote.reader;

final class FakeUhfSdkGateway implements UhfSdkGateway {
    int powerStatus;
    int blfStatus;
    int sessionStatus;
    int qStatus;
    int inventoryAreaStatus;
    int lastPower;
    int lastBlf;
    int lastSession;
    int lastSelected;
    int lastQ;
    int lastInventoryArea;
    boolean magicQueryUsed;

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
            int wordLen) { return 0; }
    @Override public int startInventory(int mode, int maskFlag) { return 0; }
    @Override public int stopInventory() { return 0; }
    @Override public void setInventoryListener(InventoryListener listener) {}
    @Override public void setInventoryStopListener(InventoryStopListener listener) {}
    @Override public int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime,
            int inventoryOffTime) { return 0; }
    @Override public ReaderTag inventoryOnce(int timeoutMs) { return null; }
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
    public int setQ(boolean dynamic, int qValue, int minQValue, int maxQValue, int retryCount,
            int thresholdMultiplier, int toggleTarget, int repeatUntilNoTags) {
        lastQ = qValue;
        return qStatus;
    }

    @Override
    public int setMagicQuery(int session, int target, int qValue) {
        magicQueryUsed = true;
        lastQ = qValue;
        return qStatus;
    }

    @Override
    public int setInventoryArea(int area, int address, int wordLen) {
        lastInventoryArea = area;
        return inventoryAreaStatus;
    }

    @Override public int[] getInventoryArea() { return new int[] {0, 0, 0}; }
    @Override public Integer getPowerTenthsDbm() { return lastPower; }
    @Override public Integer getBlfProfile() { return lastBlf; }
    @Override public int[] getQueryValues(ModuleSubtype subtype) { return new int[] {0, 0, 0}; }
    @Override public ReaderQParams getQParams(ModuleSubtype subtype) {
        return ReaderQParams.fixed(lastQ, 0, 1, 0);
    }
    @Override public int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
            InventoryMaskConfig config) { return 0; }
    @Override public int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
            int selected) { return 0; }
    @Override public int setTargetMask(TagProtocol protocol, ModuleSubtype subtype,
            ReaderTag tag) { return 0; }
    @Override public int clearTargetMask(TagProtocol protocol, ModuleSubtype subtype,
            int selected) { return 0; }
    @Override public byte[] readTag(TagProtocol protocol, int length, int address, int bank,
            byte[] password, int timeoutMs) { return new byte[0]; }
    @Override public int writeTag(TagProtocol protocol, int length, int address, int bank,
            byte[] password, byte[] data, int timeoutMs) { return 0; }
    @Override public int lockTag(byte[] password, int bank, int policy, int timeoutMs) { return 0; }
    @Override public int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs) {
        return 0;
    }
}
