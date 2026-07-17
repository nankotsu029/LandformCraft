package com.github.nankotsu029.landformcraft.generator.v2.hydrology.core;

import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;

import java.util.List;
import java.util.Objects;

/** Immutable primitive routing result retained after the full-resolution working fields are released. */
public final class HydrologyRoutingResultV2 {
    private final int width;
    private final int length;
    private final String sourceHydrologyPlanChecksum;
    private final String sourceSurfaceChecksum;
    private final String fixedPriorChecksum;
    private final List<HydrologyRoutingArtifactV2.Outlet> outlets;
    private final List<HydrologyRoutingArtifactV2.BasinSummary> basins;
    private final byte[] flowDirection;
    private final int[] flowAccumulation;
    private final Metrics metrics;
    private final String graphChecksum;
    private final String flowDirectionSemanticChecksum;
    private final String flowAccumulationSemanticChecksum;
    private final String routingChecksum;

    HydrologyRoutingResultV2(
            int width,
            int length,
            String sourceHydrologyPlanChecksum,
            String sourceSurfaceChecksum,
            String fixedPriorChecksum,
            List<HydrologyRoutingArtifactV2.Outlet> outlets,
            List<HydrologyRoutingArtifactV2.BasinSummary> basins,
            byte[] flowDirection,
            int[] flowAccumulation,
            Metrics metrics,
            String graphChecksum,
            String flowDirectionSemanticChecksum,
            String flowAccumulationSemanticChecksum,
            String routingChecksum
    ) {
        this.width = width;
        this.length = length;
        this.sourceHydrologyPlanChecksum = Objects.requireNonNull(
                sourceHydrologyPlanChecksum, "sourceHydrologyPlanChecksum");
        this.sourceSurfaceChecksum = Objects.requireNonNull(sourceSurfaceChecksum, "sourceSurfaceChecksum");
        this.fixedPriorChecksum = Objects.requireNonNull(fixedPriorChecksum, "fixedPriorChecksum");
        this.outlets = List.copyOf(outlets);
        this.basins = List.copyOf(basins);
        this.flowDirection = Objects.requireNonNull(flowDirection, "flowDirection");
        this.flowAccumulation = Objects.requireNonNull(flowAccumulation, "flowAccumulation");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.graphChecksum = Objects.requireNonNull(graphChecksum, "graphChecksum");
        this.flowDirectionSemanticChecksum = Objects.requireNonNull(
                flowDirectionSemanticChecksum, "flowDirectionSemanticChecksum");
        this.flowAccumulationSemanticChecksum = Objects.requireNonNull(
                flowAccumulationSemanticChecksum, "flowAccumulationSemanticChecksum");
        this.routingChecksum = Objects.requireNonNull(routingChecksum, "routingChecksum");
        if (flowDirection.length != Math.multiplyExact(width, length)
                || flowAccumulation.length != flowDirection.length) {
            throw new IllegalArgumentException("routing result field dimensions are inconsistent");
        }
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public String sourceHydrologyPlanChecksum() {
        return sourceHydrologyPlanChecksum;
    }

    public String sourceSurfaceChecksum() {
        return sourceSurfaceChecksum;
    }

    public String fixedPriorChecksum() {
        return fixedPriorChecksum;
    }

    public List<HydrologyRoutingArtifactV2.Outlet> outlets() {
        return outlets;
    }

    public List<HydrologyRoutingArtifactV2.BasinSummary> basins() {
        return basins;
    }

    public int flowDirectionCodeAt(int x, int z) {
        return Byte.toUnsignedInt(flowDirection[index(x, z)]);
    }

    public int flowAccumulationAt(int x, int z) {
        return flowAccumulation[index(x, z)];
    }

    public Metrics metrics() {
        return metrics;
    }

    public String graphChecksum() {
        return graphChecksum;
    }

    public String flowDirectionSemanticChecksum() {
        return flowDirectionSemanticChecksum;
    }

    public String flowAccumulationSemanticChecksum() {
        return flowAccumulationSemanticChecksum;
    }

    public String routingChecksum() {
        return routingChecksum;
    }

    private int index(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("coordinate outside hydrology routing result");
        }
        return z * width + x;
    }

    public record Metrics(
            long globalCellCount,
            long routableCellCount,
            long cpuWorkUnits,
            long peakWorkingBytes,
            long retainedResultBytes,
            int maximumHeapCells,
            HydrologyRoutingRequestV2.ResourceBudget budget
    ) {
        public Metrics {
            Objects.requireNonNull(budget, "budget");
            if (globalCellCount < 1 || routableCellCount < 1 || routableCellCount > globalCellCount
                    || cpuWorkUnits < 1 || cpuWorkUnits > budget.maximumCpuWorkUnits()
                    || peakWorkingBytes < 1 || peakWorkingBytes > budget.maximumWorkingBytes()
                    || retainedResultBytes < 1
                    || retainedResultBytes > budget.maximumRetainedResultBytes()
                    || maximumHeapCells < 1 || maximumHeapCells > globalCellCount) {
                throw new IllegalArgumentException("routing metrics exceed the declared resource budget");
            }
        }
    }
}
