package com.leo.remote.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReaderHandshakeTest {
    @Test
    public void completesOnlyAfterModuleProtocolInventoryAndConfiguration() throws Exception {
        FakeGateway gateway = new FakeGateway();
        ReaderHandshake.Result result = ReaderHandshake.perform(gateway);
        assertEquals(ModuleSubtype.R2000_PLUS, result.moduleInfo.subtype);
        assertEquals(270, result.configuration.powerTenthsDbm);
        assertTrue(gateway.protocolSelected);
        assertTrue(gateway.inventoryConfigured);
    }

    @Test(expected = ReaderException.class)
    public void failsWhenProtocolSelectionFails() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.protocolStatus = 9;
        ReaderHandshake.perform(gateway);
    }

    @Test(expected = ReaderException.class)
    public void failsWhenRm70xxInformationIsIncomplete() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.moduleInfo = new ReaderModuleInfo(ModuleSubtype.R2000, 0,
                "board", "1.0", "", "2.0");
        ReaderHandshake.perform(gateway);
    }

    private static final class FakeGateway implements UhfSdkGateway {
        boolean protocolSelected;
        boolean inventoryConfigured;
        int protocolStatus;
        ReaderModuleInfo moduleInfo = new ReaderModuleInfo(
                ModuleSubtype.R2000_PLUS, 3, "board", "1.0", "rf", "2.0");

        @Override public ReaderModuleInfo readModuleInfo() {
            return moduleInfo;
        }
        @Override public int setProtocol(TagProtocol protocol) { protocolSelected = true; return protocolStatus; }
        @Override public int configureDefaultInventory(TagProtocol protocol) { inventoryConfigured = true; return 0; }
        @Override public ReaderConfiguration readConfiguration(ModuleSubtype subtype) {
            return new ReaderConfiguration(270, 1, 1, 1, 0, true, 4);
        }
        @Override public int initialize() { return 0; }
        @Override public void deinitialize() {}
        @Override public void useRm70xx() {}
        @Override public void setTransport(TransportType transport) {}
        @Override public int connectNetwork(String address, int port) { return 0; }
        @Override public int closeNetwork() { return 0; }
        @Override public void setOutboundDataListener(OutboundDataListener listener) {}
        @Override public void pushRemoteData(byte[] data) {}
        @Override public int startInventory(int mode, int maskFlag) { return 0; }
        @Override public int stopInventory() { return 0; }
        @Override public void setInventoryListener(InventoryListener listener) {}
        @Override public ReaderTag inventoryOnce(int timeoutMs) { return null; }
        @Override public int setPowerTenthsDbm(int powerTenthsDbm) { return 0; }
        @Override public int setBlfProfile(int profile) { return 0; }
        @Override public int setQueryGroup(int session, int target) { return 0; }
        @Override public int setQ(boolean dynamic, int qValue) { return 0; }
        @Override public int setMagicQuery(int session, int target, int qValue) { return 0; }
        @Override public int applyInventoryMask(TagProtocol protocol, InventoryMaskConfig config) { return 0; }
        @Override public int clearInventoryMask(TagProtocol protocol) { return 0; }
        @Override public int setTargetMask(TagProtocol protocol, ReaderTag tag) { return 0; }
        @Override public int clearTargetMask(TagProtocol protocol) { return 0; }
        @Override public byte[] readTag(TagProtocol protocol, int length, int address, int bank, byte[] password, int timeoutMs) { return new byte[0]; }
        @Override public int writeTag(TagProtocol protocol, int length, int address, int bank, byte[] password, byte[] data, int timeoutMs) { return 0; }
        @Override public int lockTag(byte[] password, int bank, int policy, int timeoutMs) { return 0; }
        @Override public int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs) { return 0; }
    }
}
