package com.leo.rfid.sdk.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.leo.rfid.sdk.model.ConnectionPhase;
import com.leo.rfid.sdk.model.ReaderException;
import com.leo.rfid.sdk.model.ReaderState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 验证 SDK 串行命令执行、Future 完成和错误处理。
 */
public class ReaderCommandExecutorTest {
    private ExecutorService executor;
    private AtomicReference<ReaderState> state;
    private AtomicReference<ReaderException> disconnectedBy;
    private ReaderCommandExecutor commands;

    @Before
    public void setUp() {
        executor = Executors.newSingleThreadExecutor();
        state = new AtomicReference<>(new ReaderState.Builder()
                .phase(ConnectionPhase.CONNECTED).build());
        disconnectedBy = new AtomicReference<>();
        commands = new ReaderCommandExecutor(executor, state::get, disconnectedBy::set);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void rejectsOperationWhenReaderIsNotConnected() throws Exception {
        state.set(ReaderState.disconnected());
        AtomicBoolean called = new AtomicBoolean();

        CompletableFuture<Integer> future = commands.submitConnected(() -> {
            called.set(true);
            return 1;
        }, true);

        ReaderException failure = readerFailure(future);
        assertEquals(-50, failure.getErrorCode());
        assertFalse(called.get());
        assertEquals(null, disconnectedBy.get());
    }

    @Test
    public void readerErrorRequestsDisconnectButValidationErrorDoesNot() throws Exception {
        ReaderException sdkFailure = new ReaderException("sdk", 9);
        ReaderException returned = readerFailure(commands.submitConnected(() -> {
            throw sdkFailure;
        }, true));
        assertSame(sdkFailure, returned);
        assertSame(sdkFailure, disconnectedBy.get());

        disconnectedBy.set(null);
        ReaderException validation = new ReaderException("validation", -40);
        assertSame(validation, readerFailure(commands.submitConnected(() -> {
            throw validation;
        }, true)));
        assertEquals(null, disconnectedBy.get());
    }

    @Test
    public void unexpectedDisconnectFailsPendingFuture() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> future = commands.submitConnected(() -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return 7;
        }, true);
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        ReaderException disconnect = new ReaderException("lost", -63);
        commands.failPending(disconnect);
        assertSame(disconnect, readerFailure(future));
        release.countDown();
    }

    private static ReaderException readerFailure(CompletableFuture<?> future) throws Exception {
        try {
            future.get(2, TimeUnit.SECONDS);
            throw new AssertionError("Expected future to fail");
        } catch (ExecutionException error) {
            assertTrue(error.getCause() instanceof ReaderException);
            return (ReaderException) error.getCause();
        }
    }
}
