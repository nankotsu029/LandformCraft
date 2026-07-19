package com.github.nankotsu029.landformcraft.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Routes Bukkit/Paper world operations through the Paper scheduler.
 * The returned minimal stage is observational: callers cannot cancel an operation after it has been accepted.
 */
public final class PaperMainThreadDispatcher implements AutoCloseable, PaperSchedulerV2 {
    private final JavaPlugin plugin;
    private final Set<ScheduledOperation<?>> pending = new HashSet<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    public PaperMainThreadDispatcher(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public CompletionStage<Void> run(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        return supply(() -> {
            operation.run();
            return null;
        });
    }

    /** True only while code is executing on the Paper primary thread. */
    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    /**
     * Schedules an operation and returns a read-only stage.
     * Execution start is the commit point. Dispatcher close may cancel a pending task and its stage together;
     * observers cannot cancel either side independently. Higher layers must validate and confirm before submission.
     */
    @Override
    public <T> CompletionStage<T> supply(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failedStage(new CancellationException("Paper dispatcher is closed"));
            }

            ScheduledOperation<T> scheduled = new ScheduledOperation<>(operation);
            pending.add(scheduled);
            scheduled.stage().whenComplete((ignoredValue, ignoredFailure) -> {
                synchronized (lifecycleLock) {
                    pending.remove(scheduled);
                }
            });
            try {
                BukkitTask task = Bukkit.getScheduler().runTask(plugin, scheduled::execute);
                scheduled.bind(task);
            } catch (RuntimeException exception) {
                scheduled.cancelPending(exception);
            }
            return scheduled.stage();
        }
    }

    @Override
    public void close() {
        Set<ScheduledOperation<?>> accepted;
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            accepted = Set.copyOf(pending);
        }
        CancellationException cancellation = new CancellationException("Paper dispatcher closed before execution");
        for (ScheduledOperation<?> operation : accepted) {
            operation.cancelPending(cancellation);
        }
    }

    private static <T> CompletionStage<T> failedStage(Throwable failure) {
        return CompletableFuture.<T>failedFuture(failure).minimalCompletionStage();
    }

    private static final class ScheduledOperation<T> {
        private final Supplier<T> operation;
        private final CompletableFuture<T> result = new CompletableFuture<>();

        private State state = State.PENDING;
        private BukkitTask task;

        private ScheduledOperation(Supplier<T> operation) {
            this.operation = operation;
        }

        CompletionStage<T> stage() {
            return result.minimalCompletionStage();
        }

        synchronized void bind(BukkitTask scheduledTask) {
            Objects.requireNonNull(scheduledTask, "scheduledTask");
            if (state == State.FINISHED) {
                scheduledTask.cancel();
            } else {
                task = scheduledTask;
            }
        }

        void execute() {
            synchronized (this) {
                if (state != State.PENDING) {
                    return;
                }
                state = State.RUNNING;
            }

            try {
                result.complete(operation.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
                if (throwable instanceof Error error) {
                    throw error;
                }
            } finally {
                synchronized (this) {
                    state = State.FINISHED;
                    task = null;
                }
            }
        }

        void cancelPending(Throwable failure) {
            BukkitTask taskToCancel;
            synchronized (this) {
                if (state != State.PENDING) {
                    return;
                }
                state = State.FINISHED;
                taskToCancel = task;
                task = null;
            }
            if (taskToCancel != null) {
                taskToCancel.cancel();
            }
            result.completeExceptionally(failure);
        }

        private enum State {
            PENDING,
            RUNNING,
            FINISHED
        }
    }
}
