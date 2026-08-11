package com.leo.remote.reader;

public interface UhfSdkGateway {
    interface OutboundDataListener { void onOutboundData(byte[] data); }
    interface InventoryListener { void onTag(ReaderTag tag); }
    interface InventoryStopListener { void onInventoryStopped(int status); }

    int initialize();
    void deinitialize();
    void useRm70xx();
    void setTransport(TransportType transport);
    int connectNetwork(String address, int port);
    int closeNetwork();
    void setOutboundDataListener(OutboundDataListener listener);
    void pushRemoteData(byte[] data);
    ReaderModuleInfo readModuleInfo() throws ReaderException;
    int setProtocol(TagProtocol protocol);
    int applyInventoryParams(TagProtocol protocol, int area, int address, int wordLen);
    int startInventory(int mode, int maskFlag);
    int stopInventory();
    void setInventoryListener(InventoryListener listener);
    void setInventoryStopListener(InventoryStopListener listener);
    int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime, int inventoryOffTime);
    ReaderTag inventoryOnce(int timeoutMs) throws ReaderException;
    ReaderConfiguration readConfiguration(ModuleSubtype subtype) throws ReaderException;
    int setPowerTenthsDbm(int powerTenthsDbm);
    int setBlfProfile(int profile);
    /** Changes session while preserving the supplied target and selected values. */
    int setSession(ModuleSubtype subtype, int session, int target, int selected);
    int setQ(boolean dynamic, int qValue, int minQValue, int maxQValue, int retryCount,
            int thresholdMultiplier, int toggleTarget, int repeatUntilNoTags);
    int setMagicQuery(int session, int target, int qValue);
    int setInventoryArea(int area, int address, int wordLen);
    int[] getInventoryArea();
    Integer getPowerTenthsDbm();
    Integer getBlfProfile();
    /** Returns {session, target, selected}, or null when the read fails. */
    int[] getQueryValues(ModuleSubtype subtype);
    ReaderQParams getQParams(ModuleSubtype subtype);
    int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype, InventoryMaskConfig config);
    int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype, int selected);
    int setTargetMask(TagProtocol protocol, ModuleSubtype subtype, ReaderTag tag);
    int clearTargetMask(TagProtocol protocol, ModuleSubtype subtype, int selected);
    byte[] readTag(TagProtocol protocol, int length, int address, int bank, byte[] password,
            int timeoutMs) throws ReaderException;
    int writeTag(TagProtocol protocol, int length, int address, int bank, byte[] password,
            byte[] data, int timeoutMs);
    int lockTag(byte[] password, int bank, int policy, int timeoutMs);
    int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs);
}
