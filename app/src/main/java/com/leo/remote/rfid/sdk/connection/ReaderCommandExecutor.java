package com.leo.remote.rfid.sdk.connection;

import com.leo.remote.rfid.sdk.model.ReaderException;
import com.leo.remote.rfid.sdk.model.ReaderState;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 串行执行 SDK 命令，并统一处理 Future 完成和异常断连。
 */
final class ReaderCommandExecutor {
    private final ExecutorService executor;
    private final Supplier<ReaderState> stateSupplier;
    private final Consumer<ReaderException> readerErrorHandler;
    private final CopyOnWriteArraySet<CompletableFuture<?>> pending =
            new CopyOnWriteArraySet<>();

    ReaderCommandExecutor(ExecutorService executor, Supplier<ReaderState> stateSupplier,
            Consumer<ReaderException> readerErrorHandler) {
        this.executor = executor;
        this.stateSupplier = stateSupplier;
        this.readerErrorHandler = readerErrorHandler;
    }

    void execute(Runnable action) { executor.execute(action); }

    <T> CompletableFuture<T> submitConnected(Callable<T> operation,
            boolean disconnectOnReaderError) {
        CompletableFuture<T> future = new CompletableFuture<>();
        pending.add(future);
        future.whenComplete((value, error) -> pending.remove(future));
        try {
            executor.execute(() -> {
                try {
                    if (!stateSupplier.get().isConnected()) {
                        throw new ReaderException("Reader is not connected", -50);
                    }
                    future.complete(operation.call());
                } catch (Throwable error) {
                    if (disconnectOnReaderError && shouldDisconnect(error)) {
                        readerErrorHandler.accept((ReaderException) error);
                    }
                    future.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    void failPending(ReaderException failure) {
        for (CompletableFuture<?> future : pending) { future.completeExceptionally(failure); }
    }

    private static boolean shouldDisconnect(Throwable error) {
        if (!(error instanceof ReaderException reader)) { return false; }
        int code = reader.getErrorCode();
        return code != -40 && code != -50 && code != -41;
    }
}
