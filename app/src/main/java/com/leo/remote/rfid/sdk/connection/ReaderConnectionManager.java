package com.leo.remote.rfid.sdk.connection;

import com.leo.remote.rfid.sdk.model.*;

import androidx.annotation.NonNull;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Owns connection state, attempt generations, and unexpected-disconnect alerts. */
public final class ReaderConnectionManager {
    private final ReaderStatePublisher publisher;
    private final Consumer<ReaderState> stateConsumer;
    private final AtomicLong generation = new AtomicLong();

    private volatile ReaderState state = ReaderState.disconnected();
    private volatile ReaderState acknowledgedConnectionFailure;
    private volatile boolean pendingDisconnectAlert;
    private volatile DisconnectReason lastUnexpectedReason = DisconnectReason.NONE;

    ReaderConnectionManager(@NonNull ReaderStatePublisher publisher,
            @NonNull Consumer<ReaderState> stateConsumer) {
        this.publisher = publisher;
        this.stateConsumer = stateConsumer;
    }

    @NonNull
    public ReaderState getState() {
        return state;
    }

    long beginAttempt() {
        acknowledgedConnectionFailure = null;
        return generation.incrementAndGet();
    }

    long getGeneration() {
        return generation.get();
    }

    boolean isCurrent(long attemptGeneration) {
        return generation.get() == attemptGeneration;
    }

    void publish(@NonNull ReaderState updated) {
        state = updated;
        stateConsumer.accept(updated);
        publisher.publishState(updated);
    }

    boolean publishIfCurrent(long attemptGeneration, @NonNull ReaderState updated) {
        if (!isCurrent(attemptGeneration)) { return false; }
        publish(updated);
        return true;
    }

    boolean isPendingDisconnectAlert() {
        return pendingDisconnectAlert;
    }

    @NonNull
    DisconnectReason getLastUnexpectedReason() {
        return lastUnexpectedReason;
    }

    void acknowledgeDisconnect() {
        pendingDisconnectAlert = false;
    }

    boolean isConnectionFailureAcknowledged(@NonNull ReaderState failure) {
        return failure.getPhase() == ConnectionPhase.FAILED
                && acknowledgedConnectionFailure == failure;
    }

    void acknowledgeConnectionFailure(@NonNull ReaderState failure) {
        if (state == failure && failure.getPhase() == ConnectionPhase.FAILED) {
            acknowledgedConnectionFailure = failure;
        }
    }

    void clearUnexpectedDisconnect() {
        pendingDisconnectAlert = false;
        lastUnexpectedReason = DisconnectReason.NONE;
    }

    void publishUnexpectedDisconnect(@NonNull ReaderState disconnectedState,
            @NonNull DisconnectReason reason) {
        pendingDisconnectAlert = true;
        lastUnexpectedReason = reason;
        publish(disconnectedState);
        publisher.notifyUnexpectedDisconnect(reason);
    }
}
