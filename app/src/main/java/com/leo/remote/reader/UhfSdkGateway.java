package com.leo.remote.reader;

public interface UhfSdkGateway {
    interface OutboundDataListener { void onOutboundData(byte[] data); }
    interface InventoryListener { void onTag(ReaderTag tag); }

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
    int configureDefaultInventory(TagProtocol protocol);
    int startInventory(int mode, int maskFlag);
    int stopInventory();
    void setInventoryListener(InventoryListener listener);
    ReaderTag inventoryOnce(int timeoutMs) throws ReaderException;
    ReaderConfiguration readConfiguration(ModuleSubtype subtype) throws ReaderException;
    int setPowerTenthsDbm(int powerTenthsDbm);
    int setBlfProfile(int profile);
    int setQueryGroup(int session, int target);
    int setQ(boolean dynamic, int qValue, int minQValue, int maxQValue, int retryCount,
            int thresholdMultiplier, int toggleTarget, int repeatUntilNoTags);
    int setMagicQuery(int session, int target, int qValue);
    int applyInventoryMask(TagProtocol protocol, InventoryMaskConfig config);
    int clearInventoryMask(TagProtocol protocol);
    int setTargetMask(TagProtocol protocol, ReaderTag tag);
    int clearTargetMask(TagProtocol protocol);
    byte[] readTag(TagProtocol protocol, int length, int address, int bank, byte[] password,
            int timeoutMs) throws ReaderException;
    int writeTag(TagProtocol protocol, int length, int address, int bank, byte[] password,
            byte[] data, int timeoutMs);
    int lockTag(byte[] password, int bank, int policy, int timeoutMs);
    int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs);
}
