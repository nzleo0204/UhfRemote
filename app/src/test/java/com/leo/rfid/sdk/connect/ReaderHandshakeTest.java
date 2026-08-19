package com.leo.rfid.sdk.connect;

import com.leo.rfid.sdk.model.*;
import com.leo.rfid.sdk.storage.*;
import com.leo.rfid.sdk.bridge.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * 验证握手流程的模块校验、参数读取和缓存回退。
 */
public class ReaderHandshakeTest {
    @Test
    public void completesOnlyAfterModuleProtocolInventoryAndConfiguration() throws Exception {
        FakeGateway gateway = new FakeGateway();
        ReaderHandshake.Result result = ReaderHandshake.perform(gateway, gateway, gateway);
        assertEquals(ModuleSubtype.R2000_PLUS, result.moduleInfo.subtype);
        assertEquals(270, result.configuration.powerTenthsDbm);
        assertTrue(gateway.protocolSelected);
        assertTrue(gateway.inventoryConfigured);
    }

    @Test
    public void stopsInventoryBeforeReadingModuleInformation() throws Exception {
        FakeGateway gateway = new FakeGateway();

        ReaderHandshake.perform(gateway, gateway, gateway);

        assertTrue(gateway.stopInventoryCalled);
        assertFalse(gateway.moduleInfoReadBeforeInventoryStopped);
    }

    @Test(expected = ReaderException.class)
    public void failsWhenProtocolSelectionFails() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.protocolStatus = 9;
        ReaderHandshake.perform(gateway, gateway, gateway);
    }

    @Test(expected = ReaderException.class)
    public void failsWhenRm70xxInformationIsIncomplete() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.moduleInfo = new ReaderModuleInfo(ModuleSubtype.R2000, 0,
                "board", "1.0", "", "2.0");
        ReaderHandshake.perform(gateway, gateway, gateway);
    }

    @Test
    public void configurationProgress_keepsInitialReadsButOmitsQ() {
        FakeGateway gateway = new FakeGateway();
        List<ReaderProgress> progress = new ArrayList<>();

        ReaderConfiguration configuration = ReaderHandshake.readConfigurationStepwise(
                gateway, ModuleSubtype.R2000_PLUS, new InMemoryConfigurationStore(),
                progress::add);

        assertEquals(270, configuration.powerTenthsDbm);
        assertEquals(Arrays.asList(
                ReaderProgress.READING_POWER,
                ReaderProgress.READING_PROTOCOL,
                ReaderProgress.READING_SESSION,
                ReaderProgress.READING_BLF), progress);
    }

    @Test
    public void updatingParametersStartsAfterModuleInfoIsRead() throws Exception {
        FakeGateway gateway = new FakeGateway();

        ReaderHandshake.perform(gateway, gateway, gateway,
                new InMemoryConfigurationStore(), progress ->
                gateway.eventLog.add("progress:" + progress));

        assertTrue(gateway.eventLog.indexOf("moduleInfo") >= 0);
        assertTrue(gateway.eventLog.indexOf("moduleInfo")
                < gateway.eventLog.indexOf("progress:" + ReaderProgress.UPDATING_PARAMETERS));
        assertTrue(gateway.eventLog.indexOf("progress:" + ReaderProgress.UPDATING_PARAMETERS)
                < gateway.eventLog.indexOf("setProtocol"));
    }

    private static final class InMemoryConfigurationStore implements ReaderConfigurationStore {
        private ReaderConfiguration configuration;
        private int selected;

        @Override public void saveConfiguration(ModuleSubtype subtype, ReaderConfiguration value) {
            configuration = value;
        }
        @Override public ReaderConfiguration loadConfiguration(ModuleSubtype subtype) {
            return configuration;
        }
        @Override public void saveSelected(ModuleSubtype subtype, int value) { selected = value; }
        @Override public int loadSelected(ModuleSubtype subtype) { return selected; }
    }

    private static final class FakeGateway implements ReaderTransportGateway,
            ReaderConfigurationGateway, InventoryBridge, ReaderTagGateway {
        boolean protocolSelected;
        boolean inventoryConfigured;
        boolean stopInventoryCalled;
        boolean moduleInfoReadBeforeInventoryStopped;
        int protocolStatus;
        List<String> eventLog = new ArrayList<>();
        ReaderModuleInfo moduleInfo = new ReaderModuleInfo(
                ModuleSubtype.R2000_PLUS, 3, "board", "1.0", "rf", "2.0");

        @Override public ReaderModuleInfo readModuleInfo() {
            moduleInfoReadBeforeInventoryStopped = !stopInventoryCalled;
            eventLog.add("moduleInfo");
            return moduleInfo;
        }
        @Override public int setProtocol(TagProtocol protocol) {
            protocolSelected = true;
            eventLog.add("setProtocol");
            return protocolStatus;
        }
        @Override public int applyInventoryParams(TagProtocol protocol, int area, int address,
                int wordLen) { inventoryConfigured = true; return 0; }
        @Override public ReaderConfiguration readConfiguration(ModuleSubtype subtype) {
            return new ReaderConfiguration(270, 1, 1, 1, 0, true, 4);
        }
        @Override public int initialize() { return 0; }
        @Override public void deinitialize() {}
        @Override public void useRm70xx() {}
        @Override public void setTransport(TransportType transport) {}
        @Override public int connectNetwork(String address, int port) { return 0; }
        @Override public int closeNetwork() { return 0; }
        @Override public int openSerial(String path) { return 0; }
        @Override public int closeSerial() { return 0; }
        @Override public void setOutboundDataListener(OutboundDataListener listener) {}
        @Override public void pushRemoteData(byte[] data) {}
        @Override public int startInventory(int mode, int maskFlag) { return 0; }
        @Override public int stopInventory() { stopInventoryCalled = true; return 0; }
        @Override public void setInventoryListener(InventoryListener listener) {}
        @Override public void setInventoryStopListener(InventoryStopListener listener) {}
        @Override public int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime,
                int inventoryOffTime) { return 0; }
        @Override public int setPowerTenthsDbm(int powerTenthsDbm) { return 0; }
        @Override public int setBlfProfile(int profile) { return 0; }
        @Override public int setSession(ModuleSubtype subtype, int session, int target,
                int selected) { return 0; }
        @Override public Integer getPowerTenthsDbm() { return 270; }
        @Override public Integer getBlfProfile() { return 1; }
        @Override public int[] getQueryValues(ModuleSubtype subtype) { return new int[]{1, 0, 0}; }
        @Override public ReaderQParams getQParams(ModuleSubtype subtype) {
            return ReaderQParams.dynamic(4, 0, 15, 0, 1, 1);
        }
        @Override public int setInventoryArea(int area, int address, int wordLen) { return 0; }
        @Override public int[] getInventoryArea() { return new int[]{0, 0, 0}; }
        @Override public int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
                InventoryMaskConfig config) { return 0; }
        @Override public int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
                int selected) { return 0; }
        @Override public int setTargetMask(TagProtocol protocol, ModuleSubtype subtype,
                ReaderTag tag) { return 0; }
        @Override public int clearTargetMask(TagProtocol protocol, ModuleSubtype subtype,
                int selected) { return 0; }
        @Override public TagReadResult readTag(TagProtocol protocol, int length, int address, int bank, byte[] password, int timeoutMs) { return new TagReadResult(null, null, 0); }
        @Override public int writeTag(TagProtocol protocol, int length, int address, int bank, byte[] password, byte[] data, int timeoutMs) { return 0; }
        @Override public int lockTag(byte[] password, int bank, int policy, int timeoutMs) { return 0; }
        @Override public int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs) { return 0; }
    }
}
