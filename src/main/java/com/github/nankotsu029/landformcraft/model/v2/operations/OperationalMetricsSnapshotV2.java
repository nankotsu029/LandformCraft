package com.github.nankotsu029.landformcraft.model.v2.operations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Bounded, sealed operational metrics snapshot for Release 2 generation/placement/recovery
 * evidence (V2-6-13). Labels are a closed enum; unknown labels and unbounded maps are refused.
 */
public record OperationalMetricsSnapshotV2(
        int schemaVersion,
        String contractVersion,
        String capturedAt,
        List<SampleV2> samples,
        String canonicalChecksum
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String CONTRACT_VERSION = "operational-metrics-snapshot-v1";
    public static final String PENDING_CHECKSUM = "0".repeat(64);
    public static final int MAXIMUM_SAMPLES = OperationalMetricLabelV2.values().length;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    public OperationalMetricsSnapshotV2 {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("operational metrics schemaVersion must be 1");
        }
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown operational metrics contract");
        }
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        if (!INSTANT.matcher(capturedAt).matches()) {
            throw new IllegalArgumentException("capturedAt must be a UTC ISO-8601 instant");
        }
        samples = normalize(samples);
        canonicalChecksum = Objects.requireNonNull(canonicalChecksum, "canonicalChecksum");
        if (!PENDING_CHECKSUM.equals(canonicalChecksum) && !CHECKSUM.matcher(canonicalChecksum).matches()) {
            throw new IllegalArgumentException("canonicalChecksum must be lowercase sha-256");
        }
    }

    public static OperationalMetricsSnapshotV2 draft(String capturedAt, List<SampleV2> samples) {
        return new OperationalMetricsSnapshotV2(
                SCHEMA_VERSION, CONTRACT_VERSION, capturedAt, samples, PENDING_CHECKSUM);
    }

    public OperationalMetricsSnapshotV2 withCanonicalChecksum(String checksum) {
        return new OperationalMetricsSnapshotV2(
                schemaVersion, contractVersion, capturedAt, samples, checksum);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CHECKSUM.equals(canonicalChecksum);
    }

    public long valueOf(OperationalMetricLabelV2 label) {
        Objects.requireNonNull(label, "label");
        for (SampleV2 sample : samples) {
            if (sample.label() == label) {
                return sample.value();
            }
        }
        throw new IllegalArgumentException("metric label absent: " + label);
    }

    private static List<SampleV2> normalize(List<SampleV2> samples) {
        Objects.requireNonNull(samples, "samples");
        if (samples.size() > MAXIMUM_SAMPLES) {
            throw new IllegalArgumentException("operational metrics sample count exceeds closed label set");
        }
        EnumMap<OperationalMetricLabelV2, SampleV2> unique = new EnumMap<>(OperationalMetricLabelV2.class);
        for (SampleV2 sample : samples) {
            Objects.requireNonNull(sample, "sample");
            if (unique.put(sample.label(), sample) != null) {
                throw new IllegalArgumentException("duplicate operational metric label: " + sample.label());
            }
        }
        List<SampleV2> ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator.comparing(sample -> sample.label().name()));
        return List.copyOf(ordered);
    }

    public record SampleV2(
            OperationalMetricLabelV2 label,
            OperationalMetricUnitV2 unit,
            long value
    ) {
        public SampleV2 {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(unit, "unit");
            if (value < 0L) {
                throw new IllegalArgumentException("metric value must be >= 0");
            }
            requireUnit(label, unit);
        }

        private static void requireUnit(OperationalMetricLabelV2 label, OperationalMetricUnitV2 unit) {
            OperationalMetricUnitV2 expected = switch (label) {
                case GENERATION_QUEUE_DEPTH, GENERATION_ACTIVE_TASKS, GENERATION_QUEUE_CAPACITY,
                     IO_AVAILABLE_PERMITS, IN_FLIGHT_TASKS,
                     PLACEMENT_STAGE_PLANNED, PLACEMENT_STAGE_APPLYING, PLACEMENT_STAGE_SETTLING,
                     PLACEMENT_STAGE_VERIFYING, PLACEMENT_STAGE_RECOVERY_REQUIRED,
                     PLACEMENT_STAGE_TERMINAL, RUNNING_GENERATION_JOBS,
                     VERIFY_SCANNED_BLOCKS -> OperationalMetricUnitV2.COUNT;
                case DISK_USABLE_BYTES, MEMORY_HEAP_USED_BYTES, MEMORY_HEAP_MAX_BYTES
                        -> OperationalMetricUnitV2.BYTES;
                case SETTLE_TICKS_OBSERVED -> OperationalMetricUnitV2.TICKS;
            };
            if (unit != expected) {
                throw new IllegalArgumentException(
                        "metric unit mismatch for " + label.name().toLowerCase(Locale.ROOT)
                                + ": expected " + expected);
            }
        }
    }
}
