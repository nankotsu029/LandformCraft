package com.github.nankotsu029.landformcraft.generator.v2.hydrology.core;

import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Trusted, non-serialized invocation contract for one global V2-3-02 routing solve. */
public record HydrologyRoutingRequestV2(
        int requestVersion,
        int width,
        int length,
        HydrologyPlanV2 hydrologyPlan,
        ProvisionalSurfaceV2 surface,
        List<HydrologyRoutingArtifactV2.Outlet> outletCandidates,
        ResourceBudget resourceBudget
) {
    public static final int VERSION = 1;

    public HydrologyRoutingRequestV2 {
        if (requestVersion != VERSION) {
            throw new IllegalArgumentException("hydrology routing requestVersion must be 1");
        }
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
            throw new IllegalArgumentException("hydrology routing dimensions must be within 1.." + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
        }
        Objects.requireNonNull(hydrologyPlan, "hydrologyPlan");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(resourceBudget, "resourceBudget");
        long cells = Math.multiplyExact((long) width, length);
        if (hydrologyPlan.budget().globalCellCount() != cells) {
            throw new IllegalArgumentException("routing dimensions do not match HydrologyPlan budget");
        }
        if (!hydrologyPlan.basins().isEmpty() || !hydrologyPlan.nodes().isEmpty()
                || !hydrologyPlan.reaches().isEmpty() || !hydrologyPlan.waterBodies().isEmpty()
                || !hydrologyPlan.fallPlans().isEmpty()) {
            throw new IllegalArgumentException("V2-3-02 requires the pre-feature empty HydrologyPlan graph");
        }
        outletCandidates = Objects.requireNonNull(outletCandidates, "outletCandidates").stream()
                .peek(Objects::requireNonNull)
                .sorted(Comparator.comparingInt(HydrologyRoutingArtifactV2.Outlet::z)
                        .thenComparingInt(HydrologyRoutingArtifactV2.Outlet::x)
                        .thenComparing(HydrologyRoutingArtifactV2.Outlet::outletId))
                .toList();
        if (outletCandidates.isEmpty()
                || outletCandidates.size() > hydrologyPlan.budget().maximumBasins()
                || outletCandidates.size() > resourceBudget.maximumOutlets()) {
            throw new IllegalArgumentException("routing outlet candidates exceed the declared basin budget");
        }
        Set<String> ids = new HashSet<>();
        Set<Integer> cellsByOutlet = new HashSet<>();
        for (HydrologyRoutingArtifactV2.Outlet outlet : outletCandidates) {
            if (outlet.x() >= width || outlet.z() >= length
                    || !ids.add(outlet.outletId())
                    || !cellsByOutlet.add(outlet.cellId(width))) {
                throw new IllegalArgumentException("routing outlet candidates contain an invalid duplicate");
            }
            if (outlet.kind() == HydrologyRoutingArtifactV2.OutletKind.BOUNDARY
                    && outlet.x() != 0 && outlet.z() != 0
                    && outlet.x() != width - 1 && outlet.z() != length - 1) {
                throw new IllegalArgumentException("BOUNDARY outlet candidate is not on the global boundary");
            }
        }
        if (resourceBudget.maximumCpuWorkUnits() > hydrologyPlan.budget().estimatedCpuWorkUnits()
                || resourceBudget.maximumWorkingBytes() > hydrologyPlan.budget().estimatedResidentBytes()) {
            throw new IllegalArgumentException("routing resource budget exceeds the compiled HydrologyPlan budget");
        }
    }

    public static HydrologyRoutingRequestV2 create(
            int width,
            int length,
            HydrologyPlanV2 plan,
            ProvisionalSurfaceV2 surface,
            List<HydrologyRoutingArtifactV2.Outlet> outlets
    ) {
        return new HydrologyRoutingRequestV2(
                VERSION, width, length, plan, surface, outlets, ResourceBudget.fromPlan(plan));
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumOutlets,
            long maximumCpuWorkUnits,
            long maximumWorkingBytes,
            long maximumRetainedResultBytes,
            long maximumFieldArtifactBytes
    ) {
        public ResourceBudget {
            if (!HydrologyRoutingArtifactV2.BUDGET_VERSION.equals(budgetVersion)
                    || maximumOutlets < 1 || maximumOutlets > 256
                    || maximumCpuWorkUnits < 1 || maximumCpuWorkUnits > 100_000_000L
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 512L * 1024L * 1024L
                    || maximumRetainedResultBytes < 1
                    || maximumRetainedResultBytes > 64L * 1024L * 1024L
                    || maximumFieldArtifactBytes < 1
                    || maximumFieldArtifactBytes > 64L * 1024L * 1024L) {
                throw new IllegalArgumentException("invalid hydrology routing resource budget");
            }
        }

        public static ResourceBudget fromPlan(HydrologyPlanV2 plan) {
            Objects.requireNonNull(plan, "plan");
            long cells = plan.budget().globalCellCount();
            long retained = Math.addExact(
                    Math.addExact(Math.multiplyExact(cells, 5L),
                            Math.multiplyExact((long) plan.budget().maximumBasins(), 128L)),
                    64L * 1024L);
            long artifact = Math.addExact(Math.multiplyExact(cells, 5L), 64L * 1024L);
            return new ResourceBudget(
                    HydrologyRoutingArtifactV2.BUDGET_VERSION,
                    plan.budget().maximumBasins(),
                    plan.budget().estimatedCpuWorkUnits(),
                    plan.budget().estimatedResidentBytes(),
                    retained,
                    artifact);
        }
    }

    public record ExecutionProfile(int tileSize, TileOrder tileOrder, int workerCount) {
        public ExecutionProfile {
            if (tileSize < 32 || tileSize > 256 || Integer.bitCount(tileSize) != 1) {
                throw new IllegalArgumentException("routing execution tileSize must be a power of two within 32..256");
            }
            Objects.requireNonNull(tileOrder, "tileOrder");
            if (workerCount < 1 || workerCount > 8) {
                throw new IllegalArgumentException("routing workerCount must be within 1..8");
            }
        }

        public static ExecutionProfile canonical() {
            return new ExecutionProfile(128, TileOrder.FORWARD, 1);
        }
    }

    public enum TileOrder { FORWARD, REVERSE }
}
