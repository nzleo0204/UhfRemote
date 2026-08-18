package com.leo.remote.rfid.sdk.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cn.wandersnail.ble.Device;
import com.leo.remote.rfid.sdk.inventory.ReaderInventoryController;
import com.leo.remote.rfid.sdk.model.*;
import com.leo.remote.rfid.sdk.persistence.ReaderConfigurationStore;
import com.leo.remote.rfid.sdk.persistence.ReaderConnectionStore;
import com.leo.remote.rfid.native_bridge.ReaderConfigurationGateway;
import com.leo.remote.rfid.native_bridge.ReaderInventoryGateway;
import com.leo.remote.rfid.native_bridge.ReaderTagGateway;
import com.leo.remote.rfid.native_bridge.ReaderTransportGateway;
import com.leo.remote.rfid.sdk.tag.ReaderTagOperations;
import com.leo.remote.rfid.sdk.connection.transport.ReaderBleTransport;
import com.leo.remote.rfid.sdk.connection.transport.ReaderWifiMonitor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ReaderConnectionOrchestratorTest {
    private ExecutorService executor;
    private TestGateway gateway;
    private TestStore store;
    private TestBleTransport ble;
    private ReaderConnectionManager connections;
    private ReaderConnectionOrchestrator orchestrator;
    private List<ReaderState> states;

    @Before
    public void setUp() {
        executor = Executors.newSingleThreadExecutor();
        gateway = new TestGateway();
        store = new TestStore();
        ble = new TestBleTransport();
        states = new CopyOnWriteArrayList<>();
        ReaderStatePublisher publisher = new ReaderStatePublisher(Runnable::run);
        connections = new ReaderConnectionManager(publisher, states::add);
        ReaderCommandExecutor commands = new ReaderCommandExecutor(executor,
                connections::getState, ignored -> {});
        ReaderConfigurationManager configuration = new ReaderConfigurationManager(
                gateway, store, publisher);
        ReaderTagOperations tags = new ReaderTagOperations(gateway, publisher);
        ReaderInventoryController inventory = new ReaderInventoryController(gateway, gateway,
                store, publisher, (action, delay) -> action.run());
        TestMainThread mainThread = new TestMainThread();
        TestWifiMonitor wifi = new TestWifiMonitor();
        ReaderSessionDependencies dependencies = new ReaderSessionDependencies(() -> executor,
                mainThread, listener -> {
                    ble.listener = listener;
                    return ble;
                }, listener -> {
                    wifi.listener = listener;
                    return wifi;
                }, store, store, resourceId -> "message:" + resourceId);
        orchestrator = new ReaderConnectionOrchestrator(gateway, gateway, gateway, store, store,
                configuration, tags, inventory, connections, commands, dependencies,
                () -> {}, () -> {});
        assertNotNull(ble.listener);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void wifiConnectsAndReportsNetworkFailure() throws Exception {
        orchestrator.connectWifi("192.168.1.20");
        awaitPhase(ConnectionPhase.CONNECTED);
        assertEquals(TransportType.WIFI, connections.getState().getTransport());
        assertEquals("192.168.1.20", store.wifiAddress);

        gateway.networkStatus = 8;
        orchestrator.connectWifi("192.168.1.21");
        awaitPhase(ConnectionPhase.FAILED);
        assertEquals(8, connections.getState().getErrorCode());
        assertEquals(ReaderConnectionFailure.READER,
                connections.getState().getConnectionFailure());
        assertTrue(gateway.closeNetworkCalls.get() >= 1);
    }

    @Test
    public void bleHandshakeConnectsAndReaderFailureIsCategorized() throws Exception {
        long successGeneration = beginBleAttempt("AA:01");
        ble.listener.onReady(successGeneration);
        awaitPhase(ConnectionPhase.CONNECTED);
        assertEquals(TransportType.BLE, connections.getState().getTransport());

        gateway.failModuleReads.set(1);
        long failureGeneration = beginBleAttempt("AA:02");
        ble.listener.onReady(failureGeneration);
        awaitPhase(ConnectionPhase.FAILED);
        assertEquals(ReaderConnectionFailure.READER,
                connections.getState().getConnectionFailure());
        assertTrue(ble.disconnectCalls.get() >= 1);
    }

    @Test
    public void staleBleProgressAndFailureDoNotChangeNewAttempt() {
        long oldGeneration = beginBleAttempt("AA:01");
        connections.beginAttempt();
        connections.publish(new ReaderState.Builder().transport(TransportType.WIFI)
                .phase(ConnectionPhase.CONNECTING).device("Wi-Fi reader", "192.168.1.30")
                .build());

        ble.listener.onPhase(oldGeneration, ConnectionPhase.DISCOVERING_SERVICES, "stale");
        ble.listener.onDisconnected(oldGeneration, "stale failure", 4,
                DisconnectReason.LINK_LOST);

        assertEquals(ConnectionPhase.CONNECTING, connections.getState().getPhase());
        assertEquals(TransportType.WIFI, connections.getState().getTransport());
        assertEquals("192.168.1.30", connections.getState().getAddress());
    }

    @Test
    public void cancelDuringModuleVerificationCannotPublishSuccessOrFailure() throws Exception {
        gateway.moduleBlock = new CountDownLatch(1);
        orchestrator.connectWifi("192.168.1.40");
        assertTrue(gateway.moduleReadStarted.await(2, TimeUnit.SECONDS));

        orchestrator.disconnect(DisconnectReason.USER);
        gateway.moduleBlock.countDown();

        awaitPhase(ConnectionPhase.DISCONNECTED);
        assertFalse(hasTerminalStateFor("192.168.1.40"));
    }

    @Test
    public void cancelDuringParameterUpdateCannotPublishConnected() throws Exception {
        gateway.protocolBlock = new CountDownLatch(1);
        orchestrator.connectWifi("192.168.1.41");
        assertTrue(gateway.protocolWriteStarted.await(2, TimeUnit.SECONDS));
        awaitPhase(ConnectionPhase.UPDATING_PARAMETERS);

        orchestrator.disconnect(DisconnectReason.USER);
        gateway.protocolBlock.countDown();

        awaitPhase(ConnectionPhase.DISCONNECTED);
        assertFalse(states.stream().anyMatch(state -> state.getPhase() == ConnectionPhase.CONNECTED
                && "192.168.1.41".equals(state.getAddress())));
    }

    @Test
    public void oldHandshakeFailureDoesNotPolluteReplacementConnection() throws Exception {
        gateway.moduleBlock = new CountDownLatch(1);
        gateway.failModuleReads.set(1);
        orchestrator.connectWifi("192.168.1.50");
        assertTrue(gateway.moduleReadStarted.await(2, TimeUnit.SECONDS));

        orchestrator.connectWifi("192.168.1.51");
        gateway.moduleBlock.countDown();

        awaitCondition(() -> connections.getState().getPhase() == ConnectionPhase.CONNECTED
                && "192.168.1.51".equals(connections.getState().getAddress()));
        assertFalse(states.stream().anyMatch(state -> state.getPhase() == ConnectionPhase.FAILED
                && "192.168.1.51".equals(state.getAddress())));
    }

    private long beginBleAttempt(String address) {
        long generation = connections.beginAttempt();
        connections.publish(new ReaderState.Builder().transport(TransportType.BLE)
                .phase(ConnectionPhase.CONNECTING).device("BLE reader", address).build());
        return generation;
    }

    private boolean hasTerminalStateFor(String address) {
        return states.stream().anyMatch(state -> address.equals(state.getAddress())
                && (state.getPhase() == ConnectionPhase.CONNECTED
                || state.getPhase() == ConnectionPhase.FAILED));
    }

    private void awaitPhase(ConnectionPhase phase) throws Exception {
        awaitCondition(() -> connections.getState().getPhase() == phase);
    }

    private static void awaitCondition(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean());
    }

    private static final class TestMainThread implements ReaderMainThreadDispatcher {
        @Override public void post(Runnable action) { action.run(); }
        @Override public void postDelayed(Runnable action, long delayMillis) {}
        @Override public void removeCallbacks(Runnable action) {}
        @Override public boolean isMainThread() { return true; }
    }

    private static final class TestBleTransport implements ReaderBleTransport {
        private Listener listener;
        private final AtomicInteger disconnectCalls = new AtomicInteger();

        @Override public void connect(Device target, long attemptId) {}
        @Override public void disconnect() { disconnectCalls.incrementAndGet(); }
        @Override public void write(byte[] data) {}
    }

    private static final class TestWifiMonitor implements ReaderWifiMonitor {
        private Listener listener;
        @Override public void start() {}
        @Override public boolean hasWifiNetwork() { return true; }
    }

    private static final class TestStore
            implements ReaderConfigurationStore, ReaderConnectionStore {
        private ReaderConfiguration configuration;
        private int selected;
        private String wifiAddress = "";

        @Override public void saveConfiguration(ModuleSubtype subtype,
                ReaderConfiguration value) { configuration = value; }
        @Override public ReaderConfiguration loadConfiguration(ModuleSubtype subtype) {
            return configuration == null ? ReaderConfiguration.defaultsFor(subtype) : configuration;
        }
        @Override public void saveSelected(ModuleSubtype subtype, int value) { selected = value; }
        @Override public int loadSelected(ModuleSubtype subtype) { return selected; }
        @Override public String getWifiAddress() { return wifiAddress; }
        @Override public void saveWifiAddress(String address) { wifiAddress = address; }
    }

    private static final class TestGateway implements ReaderTransportGateway,
            ReaderConfigurationGateway, ReaderInventoryGateway, ReaderTagGateway {
        private final AtomicInteger closeNetworkCalls = new AtomicInteger();
        private final AtomicInteger failModuleReads = new AtomicInteger();
        private final CountDownLatch moduleReadStarted = new CountDownLatch(1);
        private final CountDownLatch protocolWriteStarted = new CountDownLatch(1);
        private volatile CountDownLatch moduleBlock;
        private volatile CountDownLatch protocolBlock;
        private volatile int networkStatus;

        @Override public int initialize() { return 0; }
        @Override public void deinitialize() {}
        @Override public void useRm70xx() {}
        @Override public void setTransport(TransportType transport) {}
        @Override public int connectNetwork(String address, int port) { return networkStatus; }
        @Override public int closeNetwork() {
            closeNetworkCalls.incrementAndGet();
            return 0;
        }
        @Override public void setOutboundDataListener(OutboundDataListener listener) {}
        @Override public void pushRemoteData(byte[] data) {}
        @Override public ReaderModuleInfo readModuleInfo() throws ReaderException {
            moduleReadStarted.countDown();
            await(moduleBlock);
            if (failModuleReads.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                throw new ReaderException("module failure", 12);
            }
            return new ReaderModuleInfo(ModuleSubtype.RM610, 6,
                    "board-sn", "board-version", "module-sn", "module-version");
        }
        @Override public ReaderConfiguration readConfiguration(ModuleSubtype subtype) {
            return ReaderConfiguration.defaultsFor(subtype);
        }
        @Override public int setProtocol(TagProtocol protocol) {
            protocolWriteStarted.countDown();
            await(protocolBlock);
            return 0;
        }
        @Override public int setPowerTenthsDbm(int powerTenthsDbm) { return 0; }
        @Override public int setBlfProfile(int profile) { return 0; }
        @Override public int setSession(ModuleSubtype subtype, int session, int target,
                int selected) { return 0; }
        @Override public int setInventoryArea(int area, int address, int wordLen) { return 0; }
        @Override public int[] getInventoryArea() { return new int[] {0, 0, 0}; }
        @Override public Integer getPowerTenthsDbm() { return 200; }
        @Override public Integer getBlfProfile() { return 0; }
        @Override public int[] getQueryValues(ModuleSubtype subtype) {
            return new int[] {0, 0, 0};
        }
        @Override public ReaderQParams getQParams(ModuleSubtype subtype) {
            return ReaderQParams.fixed(7, 0, 1, 0);
        }
        @Override public int applyInventoryParams(TagProtocol protocol, int area, int address,
                int wordLen) { return 0; }
        @Override public int startInventory(int mode, int maskFlag) { return 0; }
        @Override public int stopInventory() { return 0; }
        @Override public void setInventoryListener(InventoryListener listener) {}
        @Override public void setInventoryStopListener(InventoryStopListener listener) {}
        @Override public int setLowPowerScheduler(int highPerformanceTime, int inventoryOnTime,
                int inventoryOffTime) { return 0; }
        @Override public int applyInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
                InventoryMaskConfig config) { return 0; }
        @Override public int clearInventoryMask(TagProtocol protocol, ModuleSubtype subtype,
                int selected) { return 0; }
        @Override public int setTargetMask(TagProtocol protocol, ModuleSubtype subtype,
                ReaderTag tag) { return 0; }
        @Override public int clearTargetMask(TagProtocol protocol, ModuleSubtype subtype,
                int selected) { return 0; }
        @Override public TagReadResult readTag(TagProtocol protocol, int length, int address,
                int bank, byte[] password, int timeoutMs) {
            return new TagReadResult(new byte[0], new byte[0], 0);
        }
        @Override public int writeTag(TagProtocol protocol, int length, int address, int bank,
                byte[] password, byte[] data, int timeoutMs) { return 0; }
        @Override public int lockTag(byte[] password, int bank, int policy, int timeoutMs) {
            return 0;
        }
        @Override public int killTag(byte[] accessPassword, byte[] killPassword, int timeoutMs) {
            return 0;
        }

        private static void await(CountDownLatch latch) {
            if (latch == null) { return; }
            try {
                latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
