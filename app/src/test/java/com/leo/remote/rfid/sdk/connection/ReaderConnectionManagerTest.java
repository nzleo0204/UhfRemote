package com.leo.remote.rfid.sdk.connection;

import com.leo.remote.rfid.sdk.model.*;
import com.leo.remote.rfid.sdk.persistence.*;
import com.leo.remote.rfid.native_bridge.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public final class ReaderConnectionManagerTest {
    private RecordingObserver observer;
    private List<ReaderState> consumedStates;
    private ReaderConnectionManager manager;

    @Before
    public void setUp() {
        observer = new RecordingObserver();
        consumedStates = new ArrayList<>();
        ReaderStatePublisher publisher = new ReaderStatePublisher(Runnable::run);
        publisher.addObserver(observer);
        manager = new ReaderConnectionManager(publisher, consumedStates::add);
    }

    @Test
    public void newAttempt_invalidatesOlderAsyncResults() {
        long first = manager.beginAttempt();
        long second = manager.beginAttempt();

        assertFalse(manager.isCurrent(first));
        assertTrue(manager.isCurrent(second));
        assertEquals(second, manager.getGeneration());
    }

    @Test
    public void publish_updatesStateServiceConsumerAndObservers() {
        ReaderState connected = new ReaderState.Builder()
                .phase(ConnectionPhase.CONNECTED).build();

        manager.publish(connected);

        assertSame(connected, manager.getState());
        assertSame(connected, consumedStates.get(0));
        assertSame(connected, observer.state);
    }

    @Test
    public void staleAttempt_cannotPublishProgressOrFailure() {
        long stale = manager.beginAttempt();
        long current = manager.beginAttempt();
        ReaderState staleFailure = new ReaderState.Builder()
                .phase(ConnectionPhase.FAILED).build();
        ReaderState currentProgress = new ReaderState.Builder()
                .phase(ConnectionPhase.VERIFYING_MODULE).build();

        assertFalse(manager.publishIfCurrent(stale, staleFailure));
        assertTrue(manager.publishIfCurrent(current, currentProgress));
        assertSame(currentProgress, manager.getState());
        assertEquals(1, consumedStates.size());
    }

    @Test
    public void unexpectedDisconnect_setsAlertUntilAcknowledged() {
        ReaderState disconnected = new ReaderState.Builder()
                .phase(ConnectionPhase.DISCONNECTED)
                .disconnectReason(DisconnectReason.LINK_LOST).build();

        manager.publishUnexpectedDisconnect(disconnected, DisconnectReason.LINK_LOST);

        assertTrue(manager.isPendingDisconnectAlert());
        assertEquals(DisconnectReason.LINK_LOST, manager.getLastUnexpectedReason());
        assertEquals(DisconnectReason.LINK_LOST, observer.reason);

        manager.acknowledgeDisconnect();
        assertFalse(manager.isPendingDisconnectAlert());
    }

    @Test
    public void acknowledgedConnectionFailure_isNotReplayedForTheSameState() {
        manager.beginAttempt();
        ReaderState failure = new ReaderState.Builder()
                .phase(ConnectionPhase.FAILED).build();
        manager.publish(failure);

        assertFalse(manager.isConnectionFailureAcknowledged(failure));

        manager.acknowledgeConnectionFailure(failure);

        assertTrue(manager.isConnectionFailureAcknowledged(failure));
    }

    @Test
    public void newConnectionFailure_isNotAcknowledgedByAnOlderDismissal() {
        manager.beginAttempt();
        ReaderState firstFailure = new ReaderState.Builder()
                .phase(ConnectionPhase.FAILED).build();
        manager.publish(firstFailure);
        manager.acknowledgeConnectionFailure(firstFailure);

        manager.beginAttempt();
        ReaderState secondFailure = new ReaderState.Builder()
                .phase(ConnectionPhase.FAILED).build();
        manager.publish(secondFailure);

        assertFalse(manager.isConnectionFailureAcknowledged(secondFailure));
    }

    private static final class RecordingObserver implements ReaderObserver {
        private ReaderState state;
        private DisconnectReason reason;
        @Override public void onReaderStateChanged(ReaderState state) { this.state = state; }
        @Override public void onReaderUnexpectedDisconnect(DisconnectReason reason) {
            this.reason = reason;
        }
    }
}
