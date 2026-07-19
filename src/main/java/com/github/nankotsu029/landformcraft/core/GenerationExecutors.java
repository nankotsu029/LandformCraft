package com.github.nankotsu029.landformcraft.core;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Owns bounded execution resources used by the application layer.
 * Blocking I/O uses admitted virtual threads; CPU-heavy generation uses a bounded queue and platform-thread pool.
 */
public final class GenerationExecutors implements AutoCloseable {
    public static final int DEFAULT_IO_CONCURRENCY = 32;
    public static final int MAX_IO_CONCURRENCY = 256;
    public static final int MAX_GENERATION_PARALLELISM = 64;
    public static final int MAX_GENERATION_QUEUE_CAPACITY = 4_096;

    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final ExecutorService ioExecutor;
    private final ThreadPoolExecutor generationExecutor;
    private final Semaphore ioPermits;
    private final Set<TrackedTask<?>> inFlight = new HashSet<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    private GenerationExecutors(
            ExecutorService ioExecutor,
            ThreadPoolExecutor generationExecutor,
            int ioConcurrency
    ) {
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.generationExecutor = Objects.requireNonNull(generationExecutor, "generationExecutor");
        this.ioPermits = new Semaphore(ioConcurrency);
    }

    public static GenerationExecutors createDefault(int generationParallelism) {
        int queueCapacity = Math.max(16, generationParallelism * 8);
        return create(DEFAULT_IO_CONCURRENCY, generationParallelism, queueCapacity);
    }

    public static GenerationExecutors create(
            int ioConcurrency,
            int generationParallelism,
            int generationQueueCapacity
    ) {
        requireRange(ioConcurrency, 1, MAX_IO_CONCURRENCY, "ioConcurrency");
        requireRange(
                generationParallelism,
                1,
                MAX_GENERATION_PARALLELISM,
                "generationParallelism"
        );
        requireRange(
                generationQueueCapacity,
                1,
                MAX_GENERATION_QUEUE_CAPACITY,
                "generationQueueCapacity"
        );

        ThreadFactory ioThreads = Thread.ofVirtual().name("landformcraft-io-", 0).factory();
        ThreadFactory generationThreads = Thread.ofPlatform()
                .daemon(true)
                .name("landformcraft-generation-", 0)
                .factory();
        ThreadPoolExecutor generationExecutor = new ThreadPoolExecutor(
                generationParallelism,
                generationParallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(generationQueueCapacity),
                generationThreads,
                new ThreadPoolExecutor.AbortPolicy()
        );

        return new GenerationExecutors(
                Executors.newThreadPerTaskExecutor(ioThreads),
                generationExecutor,
                ioConcurrency
        );
    }

    public <T> CompletableFuture<T> supplyIo(Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        if (closed.get()) {
            return rejectedFuture("executors are closed");
        }
        if (!ioPermits.tryAcquire()) {
            return rejectedFuture("I/O concurrency limit reached");
        }
        return submit(ioExecutor, ignoredToken -> task.get(), ioPermits::release);
    }

    public CompletableFuture<Void> runIo(Runnable task) {
        Objects.requireNonNull(task, "task");
        return supplyIo(() -> {
            task.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> supplyGeneration(GenerationTask<T> task) {
        Objects.requireNonNull(task, "task");
        if (closed.get()) {
            return rejectedFuture("executors are closed");
        }
        return submit(generationExecutor, task::run, () -> { });
    }

    public CompletableFuture<Void> runGeneration(Consumer<CancellationToken> task) {
        Objects.requireNonNull(task, "task");
        return supplyGeneration(cancellationToken -> {
            task.accept(cancellationToken);
            return null;
        });
    }

    public boolean isShutdown() {
        return closed.get();
    }

    public boolean isTerminated() {
        return ioExecutor.isTerminated() && generationExecutor.isTerminated();
    }

    /**
     * Bounded executor load snapshot for operational metrics (V2-6-13). Does not expose task
     * payloads, paths, or secrets.
     */
    public ExecutorLoadSnapshotV2 snapshotLoad() {
        synchronized (lifecycleLock) {
            return new ExecutorLoadSnapshotV2(
                    generationExecutor.getActiveCount(),
                    generationExecutor.getQueue().size(),
                    generationExecutor.getQueue().remainingCapacity()
                            + generationExecutor.getQueue().size(),
                    ioPermits.availablePermits(),
                    inFlight.size(),
                    closed.get());
        }
    }

    /** Fixed-label executor load view used by {@code core.v2.operations}. */
    public record ExecutorLoadSnapshotV2(
            int generationActiveTasks,
            int generationQueueDepth,
            int generationQueueCapacity,
            int ioAvailablePermits,
            int inFlightTasks,
            boolean closed
    ) {
        public ExecutorLoadSnapshotV2 {
            if (generationActiveTasks < 0 || generationQueueDepth < 0 || generationQueueCapacity < 0
                    || ioAvailablePermits < 0 || inFlightTasks < 0) {
                throw new IllegalArgumentException("executor load counts must be >= 0");
            }
        }
    }

    /**
     * Closes admission, cancels every accepted task, and waits for both pools within one shared deadline.
     * Generation loops and blocking clients must cooperate with cancellation, interruption, and transport timeouts.
     *
     * @return true only when both executors terminated before the deadline
     */
    public boolean shutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        List<TrackedTask<?>> acceptedTasks = List.of();
        synchronized (lifecycleLock) {
            if (closed.compareAndSet(false, true)) {
                acceptedTasks = List.copyOf(inFlight);
                generationExecutor.shutdownNow();
                ioExecutor.shutdownNow();
            }
        }
        for (TrackedTask<?> task : acceptedTasks) {
            task.future().cancel(true);
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        awaitUntil(generationExecutor, deadline);
        awaitUntil(ioExecutor, deadline);
        return isTerminated();
    }

    @Override
    public void close() {
        if (!shutdown(DEFAULT_SHUTDOWN_TIMEOUT)) {
            throw new IllegalStateException("executors did not terminate within the shutdown timeout");
        }
    }

    private <T> CompletableFuture<T> submit(
            ExecutorService executor,
            Function<CancellationToken, T> operation,
            Runnable releaseAdmission
    ) {
        TrackedFuture<T> future = new TrackedFuture<>();
        TrackedTask<T> task = new TrackedTask<>(executor, operation, future, releaseAdmission);
        future.bind(task);

        synchronized (lifecycleLock) {
            if (closed.get()) {
                future.fail(new RejectedExecutionException("executors are closed"));
                task.finish();
                return future;
            }

            inFlight.add(task);
            try {
                executor.execute(task);
            } catch (RuntimeException exception) {
                future.fail(exception);
                task.finish();
            }
        }
        return future;
    }

    private static void awaitUntil(ExecutorService executor, long deadline) {
        boolean interrupted = false;
        try {
            while (!executor.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    return;
                }
                try {
                    if (executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                        return;
                    }
                } catch (InterruptedException exception) {
                    interrupted = true;
                    return;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String fieldName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + minimum + " and " + maximum
            );
        }
    }

    private static <T> CompletableFuture<T> rejectedFuture(String message) {
        return CompletableFuture.failedFuture(new RejectedExecutionException(message));
    }

    private final class TrackedTask<T> implements Runnable, TaskCancellation {
        private final ExecutorService executor;
        private final Function<CancellationToken, T> operation;
        private final TrackedFuture<T> result;
        private final Runnable releaseAdmission;
        private final AtomicReference<Thread> runner = new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();

        private TrackedTask(
                ExecutorService executor,
                Function<CancellationToken, T> operation,
                TrackedFuture<T> result,
                Runnable releaseAdmission
        ) {
            this.executor = executor;
            this.operation = operation;
            this.result = result;
            this.releaseAdmission = releaseAdmission;
        }

        @Override
        public void run() {
            runner.set(Thread.currentThread());
            try {
                if (result.isDone()) {
                    return;
                }
                CancellationToken token = () -> result.isCancelled() || Thread.currentThread().isInterrupted();
                result.succeed(operation.apply(token));
            } catch (Throwable throwable) {
                result.fail(throwable);
                if (throwable instanceof Error error) {
                    throw error;
                }
            } finally {
                runner.set(null);
                finish();
            }
        }

        @Override
        public void cancel(boolean mayInterruptIfRunning) {
            Thread runningThread = runner.get();
            if (mayInterruptIfRunning && runningThread != null) {
                runningThread.interrupt();
            }
            boolean removedFromQueue = executor instanceof ThreadPoolExecutor threadPool
                    && threadPool.remove(this);
            if (removedFromQueue || (runningThread == null && executor.isShutdown())) {
                finish();
            }
        }

        TrackedFuture<T> future() {
            return result;
        }

        void finish() {
            if (finished.compareAndSet(false, true)) {
                synchronized (lifecycleLock) {
                    inFlight.remove(this);
                }
                releaseAdmission.run();
            }
        }
    }

    private interface TaskCancellation {
        void cancel(boolean mayInterruptIfRunning);
    }

    private static final class TrackedFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<TaskCancellation> delegate = new AtomicReference<>();

        void bind(TaskCancellation task) {
            if (!delegate.compareAndSet(null, task)) {
                throw new IllegalStateException("delegate already bound");
            }
            if (isCancelled()) {
                task.cancel(true);
            }
        }

        boolean succeed(T value) {
            return super.complete(value);
        }

        boolean fail(Throwable failure) {
            return super.completeExceptionally(failure);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            TaskCancellation task = delegate.get();
            if (cancelled && task != null) {
                task.cancel(mayInterruptIfRunning);
            }
            return cancelled;
        }
    }
}
