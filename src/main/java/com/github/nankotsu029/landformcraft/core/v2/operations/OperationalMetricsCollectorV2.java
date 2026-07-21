package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricLabelV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricUnitV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalMetricsSnapshotV2;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Collects a bounded operational metrics snapshot from executor load, disk, heap, and optional
 * placement/settle/verify counters (V2-6-13).
 */
public final class OperationalMetricsCollectorV2 {
    /**
     * Closed set of labels emitted by every runtime {@link #collect} snapshot. The placement-stage
     * duration labels added in V2-13-01 are intentionally excluded: they are wall-clock stage
     * durations populated only by the offline measurement runner, never by the runtime collector.
     * Keeping the runtime output fixed to this set preserves the canonical checksums of existing
     * runtime snapshots after the enum grew.
     */
    public static final Set<OperationalMetricLabelV2> RUNTIME_LABELS = Set.copyOf(EnumSet.of(
            OperationalMetricLabelV2.GENERATION_ACTIVE_TASKS,
            OperationalMetricLabelV2.GENERATION_QUEUE_DEPTH,
            OperationalMetricLabelV2.GENERATION_QUEUE_CAPACITY,
            OperationalMetricLabelV2.IO_AVAILABLE_PERMITS,
            OperationalMetricLabelV2.IN_FLIGHT_TASKS,
            OperationalMetricLabelV2.DISK_USABLE_BYTES,
            OperationalMetricLabelV2.MEMORY_HEAP_USED_BYTES,
            OperationalMetricLabelV2.MEMORY_HEAP_MAX_BYTES,
            OperationalMetricLabelV2.PLACEMENT_STAGE_PLANNED,
            OperationalMetricLabelV2.PLACEMENT_STAGE_APPLYING,
            OperationalMetricLabelV2.PLACEMENT_STAGE_SETTLING,
            OperationalMetricLabelV2.PLACEMENT_STAGE_VERIFYING,
            OperationalMetricLabelV2.PLACEMENT_STAGE_RECOVERY_REQUIRED,
            OperationalMetricLabelV2.PLACEMENT_STAGE_TERMINAL,
            OperationalMetricLabelV2.SETTLE_TICKS_OBSERVED,
            OperationalMetricLabelV2.VERIFY_SCANNED_BLOCKS,
            OperationalMetricLabelV2.RUNNING_GENERATION_JOBS));

    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock;
    private final MemoryMXBean memory;

    public OperationalMetricsCollectorV2() {
        this(Clock.systemUTC(), ManagementFactory.getMemoryMXBean());
    }

    public OperationalMetricsCollectorV2(Clock clock, MemoryMXBean memory) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    public OperationalMetricsSnapshotV2 collect(
            GenerationExecutors.ExecutorLoadSnapshotV2 load,
            long diskUsableBytes,
            PlacementStageCountsV2 stages,
            long settleTicksObserved,
            long verifyScannedBlocks,
            int runningGenerationJobs
    ) {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(stages, "stages");
        if (diskUsableBytes < 0L || settleTicksObserved < 0L || verifyScannedBlocks < 0L
                || runningGenerationJobs < 0) {
            throw new IllegalArgumentException("metric inputs must be >= 0");
        }
        List<OperationalMetricsSnapshotV2.SampleV2> samples = new ArrayList<>();
        samples.add(count(OperationalMetricLabelV2.GENERATION_ACTIVE_TASKS, load.generationActiveTasks()));
        samples.add(count(OperationalMetricLabelV2.GENERATION_QUEUE_DEPTH, load.generationQueueDepth()));
        samples.add(count(OperationalMetricLabelV2.GENERATION_QUEUE_CAPACITY, load.generationQueueCapacity()));
        samples.add(count(OperationalMetricLabelV2.IO_AVAILABLE_PERMITS, load.ioAvailablePermits()));
        samples.add(count(OperationalMetricLabelV2.IN_FLIGHT_TASKS, load.inFlightTasks()));
        samples.add(bytes(OperationalMetricLabelV2.DISK_USABLE_BYTES, diskUsableBytes));
        samples.add(bytes(OperationalMetricLabelV2.MEMORY_HEAP_USED_BYTES,
                Math.max(0L, memory.getHeapMemoryUsage().getUsed())));
        samples.add(bytes(OperationalMetricLabelV2.MEMORY_HEAP_MAX_BYTES,
                Math.max(0L, memory.getHeapMemoryUsage().getMax())));
        samples.add(count(OperationalMetricLabelV2.PLACEMENT_STAGE_PLANNED, stages.planned()));
        samples.add(count(OperationalMetricLabelV2.PLACEMENT_STAGE_APPLYING, stages.applying()));
        samples.add(count(OperationalMetricLabelV2.PLACEMENT_STAGE_SETTLING, stages.settling()));
        samples.add(count(OperationalMetricLabelV2.PLACEMENT_STAGE_VERIFYING, stages.verifying()));
        samples.add(count(OperationalMetricLabelV2.PLACEMENT_STAGE_RECOVERY_REQUIRED, stages.recoveryRequired()));
        samples.add(count(OperationalMetricLabelV2.PLACEMENT_STAGE_TERMINAL, stages.terminal()));
        samples.add(new OperationalMetricsSnapshotV2.SampleV2(
                OperationalMetricLabelV2.SETTLE_TICKS_OBSERVED, OperationalMetricUnitV2.TICKS, settleTicksObserved));
        samples.add(count(OperationalMetricLabelV2.VERIFY_SCANNED_BLOCKS, verifyScannedBlocks));
        samples.add(count(OperationalMetricLabelV2.RUNNING_GENERATION_JOBS, runningGenerationJobs));

        Instant now = clock.instant();
        OperationalMetricsSnapshotV2 draft = OperationalMetricsSnapshotV2.draft(now.toString(), samples);
        return draft.withCanonicalChecksum(canonicalChecksum(draft));
    }

    public String canonicalChecksum(OperationalMetricsSnapshotV2 snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        ObjectNode tree = mapper.valueToTree(snapshot.withCanonicalChecksum(
                OperationalMetricsSnapshotV2.PENDING_CHECKSUM));
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    private static OperationalMetricsSnapshotV2.SampleV2 count(OperationalMetricLabelV2 label, long value) {
        return new OperationalMetricsSnapshotV2.SampleV2(label, OperationalMetricUnitV2.COUNT, value);
    }

    private static OperationalMetricsSnapshotV2.SampleV2 bytes(OperationalMetricLabelV2 label, long value) {
        return new OperationalMetricsSnapshotV2.SampleV2(label, OperationalMetricUnitV2.BYTES, value);
    }

    /** Bounded placement-stage counters used as metrics inputs. */
    public record PlacementStageCountsV2(
            int planned,
            int applying,
            int settling,
            int verifying,
            int recoveryRequired,
            int terminal
    ) {
        public PlacementStageCountsV2 {
            if (planned < 0 || applying < 0 || settling < 0 || verifying < 0
                    || recoveryRequired < 0 || terminal < 0) {
                throw new IllegalArgumentException("stage counts must be >= 0");
            }
        }

        public static PlacementStageCountsV2 zeros() {
            return new PlacementStageCountsV2(0, 0, 0, 0, 0, 0);
        }
    }
}
