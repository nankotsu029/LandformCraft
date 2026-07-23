package com.github.nankotsu029.landformcraft.core.v2.job;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.export.ExportBudgetV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobKindV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobSnapshotV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobStateV2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-12-09 asynchronous v2 export lifecycle. Closes coverage-audit finding F3: v1 could run a
 * generation as a job and watch or cancel it, while v2 had only one synchronous call.
 */
class ExportJobServiceV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json");

    private GenerationExecutors executors;

    @AfterEach
    void closeExecutors() {
        if (executors != null) {
            executors.close();
        }
    }

    @Test
    void anAsynchronousExportReachesPublishedAndBecomesACandidate(@TempDir Path root) throws Exception {
        ExportJobServiceV2 jobs = service(root);

        ExportJobSnapshotV2 queued = jobs.submit(submission(root, "async-export", ExportJobKindV2.EXPORT));

        assertEquals(ExportJobStateV2.QUEUED, queued.state());
        assertEquals("harbor-cove-64-honored", queued.requestId());
        ExportJobSnapshotV2 finished = awaitTerminal(jobs, queued.jobId());
        assertEquals(ExportJobStateV2.PUBLISHED, finished.state(), finished.message());
        assertEquals(1_000_000, finished.progressMillionths());
        assertTrue(Files.isDirectory(root.resolve("exports").resolve("async-export")));
        assertTrue(Files.isRegularFile(root.resolve("exports").resolve("async-export.zip")));

        // The published job is what `v2 candidate list <request-id>` enumerates.
        List<ExportJobSnapshotV2> candidates = jobs.candidates("harbor-cove-64-honored");
        assertEquals(List.of(queued.jobId()), candidates.stream().map(ExportJobSnapshotV2::jobId).toList());
        assertEquals(List.of(), jobs.candidates("some-other-request"));
    }

    @Test
    void generateKindPublishesTheDirectoryWithoutAZip(@TempDir Path root) throws Exception {
        ExportJobServiceV2 jobs = service(root);

        ExportJobSnapshotV2 queued = jobs.submit(submission(root, "async-generate", ExportJobKindV2.GENERATE));

        assertEquals(ExportJobStateV2.PUBLISHED, awaitTerminal(jobs, queued.jobId()).state());
        assertTrue(Files.isDirectory(root.resolve("exports").resolve("async-generate")));
        assertFalse(Files.exists(root.resolve("exports").resolve("async-generate.zip")));
    }

    @Test
    void cancellingMidFlightPublishesNothing(@TempDir Path root) throws Exception {
        ExportJobServiceV2 jobs = service(root);
        ExportJobSnapshotV2 queued = jobs.submit(submission(root, "cancelled-export", ExportJobKindV2.EXPORT));

        ExportJobSnapshotV2 cancelled = jobs.cancel(queued.jobId());

        assertEquals(ExportJobStateV2.CANCELLED, cancelled.state());
        assertTrue(cancelled.message().contains("no Release was published"), cancelled.message());
        // Let the worker unwind, then confirm the commit point was never reached.
        Thread.sleep(1_500);
        assertEquals(ExportJobStateV2.CANCELLED, jobs.status(queued.jobId()).state());
        assertFalse(Files.exists(root.resolve("exports").resolve("cancelled-export")),
                "a cancelled job must not leave a published Release");
        assertFalse(Files.exists(root.resolve("exports").resolve("cancelled-export.zip")));
    }

    @Test
    void cancellingIsIdempotentAndNeverLeavesATerminalState(@TempDir Path root) throws Exception {
        ExportJobServiceV2 jobs = service(root);
        ExportJobSnapshotV2 queued = jobs.submit(submission(root, "twice-cancelled", ExportJobKindV2.GENERATE));

        ExportJobSnapshotV2 first = jobs.cancel(queued.jobId());
        ExportJobSnapshotV2 second = jobs.cancel(queued.jobId());

        assertEquals(ExportJobStateV2.CANCELLED, first.state());
        assertEquals(first, second, "a second cancel reports the state instead of transitioning again");
    }

    @Test
    void statusIsDurableAndReadableByAFreshService(@TempDir Path root) throws Exception {
        ExportJobServiceV2 jobs = service(root);
        ExportJobSnapshotV2 queued = jobs.submit(submission(root, "durable-export", ExportJobKindV2.GENERATE));
        awaitTerminal(jobs, queued.jobId());

        // A new service over the same store answers from disk, as it would after a restart.
        ExportJobServiceV2 restarted = new ExportJobServiceV2(
                executors, new ExportJobStoreV2(root.resolve("jobs")), Clock.systemUTC());

        assertEquals(ExportJobStateV2.PUBLISHED, restarted.status(queued.jobId()).state());
    }

    @Test
    void anUnknownOrMalformedJobIdIsRejected(@TempDir Path root) {
        ExportJobServiceV2 jobs = service(root);

        assertThrows(IllegalArgumentException.class,
                () -> jobs.status(UUID.randomUUID().toString()));
        // Traversal and non-canonical forms never reach the filesystem.
        assertThrows(IllegalArgumentException.class, () -> jobs.status("../escape"));
        assertThrows(IllegalArgumentException.class,
                () -> jobs.status("6F1D9E2A-3C4B-4A7D-8E51-2B9C7D0A4F63"));
        assertThrows(IllegalArgumentException.class, () -> jobs.status("not-a-uuid"));
    }

    @Test
    void candidateOrderIsDeterministic(@TempDir Path root) throws Exception {
        ExportJobServiceV2 jobs = service(root);
        for (String releaseId : List.of("release-a", "release-b", "release-c")) {
            ExportJobSnapshotV2 queued = jobs.submit(submission(root, releaseId, ExportJobKindV2.GENERATE));
            awaitTerminal(jobs, queued.jobId());
        }

        List<String> first = jobs.candidates("harbor-cove-64-honored").stream()
                .map(ExportJobSnapshotV2::jobId).toList();
        List<String> second = jobs.candidates("harbor-cove-64-honored").stream()
                .map(ExportJobSnapshotV2::jobId).toList();

        assertEquals(3, first.size());
        assertEquals(first, second);
        assertEquals(first.stream().sorted().toList(), first, "candidates are ordered by job id");
    }

    @Test
    void theJobStoreAdmitsAgainstItsBound(@TempDir Path root) throws Exception {
        ExportJobStoreV2 store = new ExportJobStoreV2(root.resolve("jobs"));
        Instant at = Instant.parse("2026-07-21T00:00:00Z");
        // Fill the store directly; submitting thousands of real exports is not the point under test.
        for (int index = 0; index < ExportJobStoreV2.MAXIMUM_JOBS; index++) {
            store.save(new ExportJobSnapshotV2(ExportJobSnapshotV2.VERSION, UUID.randomUUID().toString(),
                    "harbor-cove-64", "filler", ExportJobKindV2.GENERATE, ExportJobStateV2.PUBLISHED,
                    1_000_000, at.toString(), "filler"));
        }

        IOException failure = assertThrows(IOException.class,
                () -> store.save(new ExportJobSnapshotV2(ExportJobSnapshotV2.VERSION,
                        UUID.randomUUID().toString(), "harbor-cove-64", "overflow",
                        ExportJobKindV2.GENERATE, ExportJobStateV2.QUEUED, 0, at.toString(), "queued")));

        assertTrue(failure.getMessage().contains("full"), failure.getMessage());
    }

    @Test
    void aTerminalJobCannotBeTransitionedAgain() {
        ExportJobSnapshotV2 published = new ExportJobSnapshotV2(ExportJobSnapshotV2.VERSION,
                UUID.randomUUID().toString(), "harbor-cove-64", "release", ExportJobKindV2.EXPORT,
                ExportJobStateV2.PUBLISHED, 1_000_000, "2026-07-21T00:00:00Z", "published");

        assertThrows(IllegalStateException.class, () -> published.transitionedTo(
                ExportJobStateV2.RUNNING, 0, Instant.parse("2026-07-21T00:01:00Z"), "restart"));
    }

    @Test
    void snapshotsRejectNonCanonicalIdentifiers() {
        Instant at = Instant.parse("2026-07-21T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new ExportJobSnapshotV2(1,
                UUID.randomUUID().toString(), "harbor-cove-64", "r", ExportJobKindV2.EXPORT,
                ExportJobStateV2.QUEUED, 0, at.toString(), "queued"));
        assertThrows(IllegalArgumentException.class, () -> new ExportJobSnapshotV2(
                ExportJobSnapshotV2.VERSION, "not-a-uuid", "harbor-cove-64", "r",
                ExportJobKindV2.EXPORT, ExportJobStateV2.QUEUED, 0, at.toString(), "queued"));
        assertThrows(IllegalArgumentException.class, () -> new ExportJobSnapshotV2(
                ExportJobSnapshotV2.VERSION, UUID.randomUUID().toString(), "Harbor-Cove", "r",
                ExportJobKindV2.EXPORT, ExportJobStateV2.QUEUED, 0, at.toString(), "queued"));
        assertThrows(IllegalArgumentException.class, () -> new ExportJobSnapshotV2(
                ExportJobSnapshotV2.VERSION, UUID.randomUUID().toString(), "harbor-cove-64", "r",
                ExportJobKindV2.EXPORT, ExportJobStateV2.QUEUED, 1_000_001, at.toString(), "queued"));
        assertThrows(IllegalArgumentException.class, () -> new ExportJobSnapshotV2(
                ExportJobSnapshotV2.VERSION, UUID.randomUUID().toString(), "harbor-cove-64", "r",
                ExportJobKindV2.EXPORT, ExportJobStateV2.QUEUED, 0, "yesterday", "queued"));
    }

    @Test
    void progressAndTimestampsComeFromTheInjectedClock(@TempDir Path root) throws Exception {
        executors = GenerationExecutors.create(4, 2, 16);
        Clock fixed = Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC);
        ExportJobServiceV2 jobs = new ExportJobServiceV2(
                executors, new ExportJobStoreV2(root.resolve("jobs")), fixed);

        ExportJobSnapshotV2 queued = jobs.submit(submission(root, "clocked", ExportJobKindV2.GENERATE));

        assertEquals("2026-07-21T12:00:00Z", queued.updatedAt());
        assertNotEquals(ExportJobStateV2.FAILED, awaitTerminal(jobs, queued.jobId()).state());
    }

    private ExportJobServiceV2 service(Path root) {
        executors = GenerationExecutors.create(4, 4, 32);
        return new ExportJobServiceV2(
                executors, new ExportJobStoreV2(root.resolve("jobs")), Clock.systemUTC());
    }

    private ExportJobSubmissionV2 submission(Path root, String releaseId, ExportJobKindV2 kind) {
        return new ExportJobSubmissionV2(
                "harbor-cove-64-honored",
                releaseId,
                kind,
                REQUEST,
                INTENT,
                root.resolve("work").resolve(releaseId),
                root.resolve("exports"),
                new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                ExportBudgetV2.defaults());
    }

    private static ExportJobSnapshotV2 awaitTerminal(ExportJobServiceV2 jobs, String jobId)
            throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(deadline)) {
            ExportJobSnapshotV2 snapshot = jobs.status(jobId);
            if (snapshot.state().terminal()) {
                return snapshot;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("v2 export job did not reach a terminal state: " + jobs.status(jobId));
    }
}
