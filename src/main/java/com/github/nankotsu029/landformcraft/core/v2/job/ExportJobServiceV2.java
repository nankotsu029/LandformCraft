package com.github.nankotsu029.landformcraft.core.v2.job;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobKindV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobSnapshotV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobStateV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous v2 export lifecycle (V2-12-09).
 *
 * <p>Closes coverage-audit finding F3: v1 could start a generation as a job and then watch or cancel
 * it, while v2 only offered one synchronous call. The synchronous form stays — this service wraps it
 * rather than replacing it, so {@code v2 export} and {@code v2 generate} keep working unchanged.</p>
 *
 * <p>Cancellation is linked end to end as AGENTS.md §11 requires: {@link #cancel} flips the token the
 * export pipeline polls, cancels the {@link CompletableFuture} with interruption, and records the
 * terminal state. Because the publisher observes the token right up to its atomic move — the commit
 * point — a cancelled job never leaves a published Release behind.</p>
 */
public final class ExportJobServiceV2 {
    private final Release2ExportApplicationServiceV2 exportService;
    private final ExportJobStoreV2 store;
    private final GenerationExecutors executors;
    private final Clock clock;
    private final Map<UUID, RunningJob> running = new ConcurrentHashMap<>();

    public ExportJobServiceV2(GenerationExecutors executors, ExportJobStoreV2 store, Clock clock) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.exportService = new Release2ExportApplicationServiceV2(executors);
    }

    /**
     * Queues one export and returns immediately with its {@code QUEUED} snapshot. The heavy work runs
     * on the bounded executors, never on the caller's thread.
     */
    public ExportJobSnapshotV2 submit(ExportJobSubmissionV2 submission) throws IOException {
        Objects.requireNonNull(submission, "submission");
        UUID jobId = UUID.randomUUID();
        ExportJobSnapshotV2 queued = new ExportJobSnapshotV2(
                ExportJobSnapshotV2.VERSION,
                jobId.toString(),
                submission.requestId(),
                submission.releaseId(),
                submission.kind(),
                ExportJobStateV2.QUEUED,
                0,
                clock.instant().toString(),
                "queued");
        // Admission happens before any work is scheduled, so a full store rejects the job rather
        // than leaving an untracked export running.
        store.save(queued);

        AtomicBoolean cancelled = new AtomicBoolean();
        CancellationToken token = cancelled::get;
        Release2ExportRequestV2 request = submission.toExportRequest(token);
        CompletableFuture<Release2ExportResultV2> future = executors.supplyIo(() -> {
            try {
                transition(jobId, ExportJobStateV2.RUNNING, 100_000, "generating and publishing");
                Release2ExportResultV2 result = exportService.exportNow(request);
                transition(jobId, ExportJobStateV2.PUBLISHED, 1_000_000,
                        "published verified Release 2 container");
                return result;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        future.whenComplete((result, failure) -> {
            running.remove(jobId);
            if (failure == null) {
                return;
            }
            // A cancelled job is already terminal; only genuine failures transition here.
            if (cancelled.get()) {
                settleCancelled(jobId);
            } else {
                settleFailed(jobId, failure);
            }
        });
        running.put(jobId, new RunningJob(cancelled, future));
        return queued;
    }

    /** Durable state of one job, readable after a restart because every transition was persisted. */
    public ExportJobSnapshotV2 status(String jobId) throws IOException {
        return store.find(jobId).orElseThrow(() -> new IllegalArgumentException(
                "v2 export job '" + jobId + "' was not found"));
    }

    /**
     * Cancels a job. Idempotent for a job that already reached a terminal state, so a second
     * {@code job cancel} reports the state instead of inventing a transition.
     */
    public ExportJobSnapshotV2 cancel(String jobId) throws IOException {
        ExportJobSnapshotV2 current = status(jobId);
        if (current.state().terminal()) {
            return current;
        }
        RunningJob job = running.get(current.jobUuid());
        if (job != null) {
            job.cancelled().set(true);
            job.future().cancel(true);
        }
        ExportJobSnapshotV2 cancelledSnapshot = current.transitionedTo(
                ExportJobStateV2.CANCELLED, current.progressMillionths(), clock.instant(),
                "cancelled by operator; no Release was published");
        store.save(cancelledSnapshot);
        return cancelledSnapshot;
    }

    /** Published jobs of one request, in deterministic order. The v2 equivalent of v1 candidates. */
    public List<ExportJobSnapshotV2> candidates(String requestId) throws IOException {
        return store.candidatesOf(requestId);
    }

    public List<ExportJobSnapshotV2> list() throws IOException {
        return store.list();
    }

    public ExportJobStoreV2 store() {
        return store;
    }

    private void transition(UUID jobId, ExportJobStateV2 next, int progress, String message) {
        try {
            Optional<ExportJobSnapshotV2> current = store.find(jobId.toString());
            if (current.isEmpty() || current.get().state().terminal()) {
                // Cancelled between transitions: the terminal state wins and the worker unwinds.
                return;
            }
            store.save(current.get().transitionedTo(next, progress, clock.instant(), message));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void settleCancelled(UUID jobId) {
        try {
            Optional<ExportJobSnapshotV2> current = store.find(jobId.toString());
            if (current.isPresent() && !current.get().state().terminal()) {
                store.save(current.get().transitionedTo(ExportJobStateV2.CANCELLED,
                        current.get().progressMillionths(), clock.instant(),
                        "cancelled by operator; no Release was published"));
            }
        } catch (IOException ignored) {
            // The cancel path already recorded the terminal state; a lost race is not a new failure.
        }
    }

    private void settleFailed(UUID jobId, Throwable failure) {
        try {
            Optional<ExportJobSnapshotV2> current = store.find(jobId.toString());
            if (current.isPresent() && !current.get().state().terminal()) {
                store.save(current.get().transitionedTo(ExportJobStateV2.FAILED,
                        current.get().progressMillionths(), clock.instant(), safeMessage(failure)));
            }
        } catch (IOException ignored) {
            // Nothing more can be recorded; the job stays in its last durable state.
        }
    }

    /** Failure text without paths or payloads (AGENTS.md §12), bounded to the contract length. */
    private static String safeMessage(Throwable failure) {
        Throwable actual = failure;
        while (actual.getCause() != null
                && (actual instanceof java.util.concurrent.CompletionException
                || actual instanceof UncheckedIOException)) {
            actual = actual.getCause();
        }
        String text = "export failed: " + actual.getClass().getSimpleName();
        return text.length() > ExportJobSnapshotV2.MAX_MESSAGE_LENGTH
                ? text.substring(0, ExportJobSnapshotV2.MAX_MESSAGE_LENGTH) : text;
    }

    private record RunningJob(AtomicBoolean cancelled, CompletableFuture<Release2ExportResultV2> future) {
    }
}
