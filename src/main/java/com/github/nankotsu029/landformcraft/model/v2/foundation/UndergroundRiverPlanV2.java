package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Frozen V2-9-11 execution plan for an EXPERIMENTAL underground-river flooded-volume connector. */
public record UndergroundRiverPlanV2(
        int planVersion,
        String featureId,
        String hostCaveFeatureId,
        String hostCaveCanonicalChecksum,
        String hostLakeFeatureId,
        String hostLakeCanonicalChecksum,
        String caveEntranceFeatureId,
        String caveEntranceCanonicalChecksum,
        String sourceNodeId,
        String outletNodeId,
        String fluidBodyId,
        FloodedCaveFluidRegionHook floodedCaveHook,
        List<ReachSample> reaches,
        int selectedChannelRadiusBlocks,
        int selectedFluidDepthBlocks,
        int selectedAirPocketBlocks,
        VolumeSdfAabbV2 aabb,
        List<OrderedVolumeOp> orderedOps,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        String channelMaskFieldId,
        String fluidMaskFieldId,
        String ownershipFieldId,
        String reachabilityFieldId,
        String airPocketFieldId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.underground-river";
    public static final String MODULE_VERSION = "0.1.0-v2-9-11";
    public static final String CONTRACT = "underground-river-plan-contract-v1";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final int MAXIMUM_OPS = 32;
    public static final int MAXIMUM_REACHES = 32;
    public static final String CHANNEL_MASK_FIELD_ID = "foundation.underground-river.channel-mask";
    public static final String FLUID_MASK_FIELD_ID = "foundation.underground-river.fluid-mask";
    public static final String OWNERSHIP_FIELD_ID = "foundation.underground-river.ownership";
    public static final String REACHABILITY_FIELD_ID = "foundation.underground-river.reachability";
    public static final String AIR_POCKET_FIELD_ID = "foundation.underground-river.air-pocket";

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    public UndergroundRiverPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("underground river planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        hostCaveFeatureId = FoundationValidationV2.qualified(hostCaveFeatureId, "hostCaveFeatureId");
        hostCaveCanonicalChecksum = requireChecksum(hostCaveCanonicalChecksum, "hostCaveCanonicalChecksum");
        hostLakeFeatureId = FoundationValidationV2.qualified(hostLakeFeatureId, "hostLakeFeatureId");
        hostLakeCanonicalChecksum = requireChecksum(hostLakeCanonicalChecksum, "hostLakeCanonicalChecksum");
        if (caveEntranceFeatureId == null || caveEntranceFeatureId.isBlank()) {
            caveEntranceFeatureId = "";
            caveEntranceCanonicalChecksum = "";
        } else {
            caveEntranceFeatureId = FoundationValidationV2.slug(caveEntranceFeatureId, "caveEntranceFeatureId");
            caveEntranceCanonicalChecksum = requireChecksum(
                    caveEntranceCanonicalChecksum, "caveEntranceCanonicalChecksum");
        }
        sourceNodeId = FoundationValidationV2.qualified(sourceNodeId, "sourceNodeId");
        outletNodeId = FoundationValidationV2.qualified(outletNodeId, "outletNodeId");
        fluidBodyId = FoundationValidationV2.qualified(fluidBodyId, "fluidBodyId");
        Objects.requireNonNull(floodedCaveHook, "floodedCaveHook");
        if (!fluidBodyId.equals(floodedCaveHook.fluidBodyId())) {
            throw new IllegalArgumentException("floodedCaveHook fluidBodyId must match plan fluidBodyId");
        }
        reaches = FoundationValidationV2.sorted(
                reaches, "reaches", MAXIMUM_REACHES,
                Comparator.comparingInt(ReachSample::ordinal).thenComparing(ReachSample::reachId));
        if (reaches.isEmpty()) {
            throw new IllegalArgumentException("underground river requires at least one reach sample");
        }
        if (selectedChannelRadiusBlocks < 1 || selectedChannelRadiusBlocks > 8
                || selectedFluidDepthBlocks < 1 || selectedFluidDepthBlocks > 16
                || selectedAirPocketBlocks < 1 || selectedAirPocketBlocks > 8) {
            throw new IllegalArgumentException("underground river selected dimensions are invalid");
        }
        Objects.requireNonNull(aabb, "aabb");
        orderedOps = FoundationValidationV2.sorted(
                orderedOps, "orderedOps", MAXIMUM_OPS,
                Comparator.comparingInt(OrderedVolumeOp::ordinal).thenComparing(OrderedVolumeOp::opId));
        if (orderedOps.isEmpty()) {
            throw new IllegalArgumentException("underground river requires ordered volume ops");
        }
        requireCarveThenSingleFluid(orderedOps, fluidBodyId);
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        channelMaskFieldId = FoundationValidationV2.qualified(channelMaskFieldId, "channelMaskFieldId");
        fluidMaskFieldId = FoundationValidationV2.qualified(fluidMaskFieldId, "fluidMaskFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        reachabilityFieldId = FoundationValidationV2.qualified(reachabilityFieldId, "reachabilityFieldId");
        airPocketFieldId = FoundationValidationV2.qualified(airPocketFieldId, "airPocketFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("underground river support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public UndergroundRiverPlanV2 withCanonicalChecksum(String checksum) {
        return new UndergroundRiverPlanV2(
                planVersion, featureId, hostCaveFeatureId, hostCaveCanonicalChecksum,
                hostLakeFeatureId, hostLakeCanonicalChecksum,
                caveEntranceFeatureId, caveEntranceCanonicalChecksum,
                sourceNodeId, outletNodeId, fluidBodyId, floodedCaveHook, reaches,
                selectedChannelRadiusBlocks, selectedFluidDepthBlocks, selectedAirPocketBlocks,
                aabb, orderedOps, sdfPlanBinding, csgPlanBinding,
                channelMaskFieldId, fluidMaskFieldId, ownershipFieldId, reachabilityFieldId,
                airPocketFieldId, supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }

    public record FloodedCaveFluidRegionHook(
            String hookKind,
            String fluidBodyId,
            int waterSurfaceYBlocks,
            VolumeSdfAabbV2 regionAabb
    ) {
        public static final String FLOODED_CAVE = "FLOODED_CAVE";

        public FloodedCaveFluidRegionHook {
            hookKind = FoundationValidationV2.nonBlank(hookKind, "hookKind", 32);
            if (!FLOODED_CAVE.equals(hookKind)) {
                throw new IllegalArgumentException("flooded cave hookKind must be FLOODED_CAVE");
            }
            fluidBodyId = FoundationValidationV2.qualified(fluidBodyId, "fluidBodyId");
            if (waterSurfaceYBlocks < -512 || waterSurfaceYBlocks > 512) {
                throw new IllegalArgumentException("flooded cave waterSurfaceYBlocks out of range");
            }
            Objects.requireNonNull(regionAabb, "regionAabb");
        }
    }

    public record ReachSample(
            String reachId,
            int ordinal,
            String fromNodeId,
            String toNodeId,
            long bedYMillionths,
            long centerXMillionths,
            long centerZMillionths
    ) {
        public ReachSample {
            reachId = FoundationValidationV2.qualified(reachId, "reachId");
            if (ordinal < 0 || ordinal > 1_000) {
                throw new IllegalArgumentException("reach ordinal out of range");
            }
            fromNodeId = FoundationValidationV2.qualified(fromNodeId, "fromNodeId");
            toNodeId = FoundationValidationV2.qualified(toNodeId, "toNodeId");
        }
    }

    public record OrderedVolumeOp(
            String opId,
            int ordinal,
            String operationKind,
            String primitiveId,
            String fluidBodyId
    ) {
        public static final String CARVE_SOLID = "CARVE_SOLID";
        public static final String ADD_FLUID = "ADD_FLUID";

        public OrderedVolumeOp {
            opId = FoundationValidationV2.qualified(opId, "opId");
            if (ordinal < 0 || ordinal > 1_000) {
                throw new IllegalArgumentException("ordered volume ordinal out of range");
            }
            operationKind = FoundationValidationV2.nonBlank(operationKind, "operationKind", 32);
            if (!CARVE_SOLID.equals(operationKind) && !ADD_FLUID.equals(operationKind)) {
                throw new IllegalArgumentException("underground river op must be CARVE_SOLID or ADD_FLUID");
            }
            primitiveId = FoundationValidationV2.qualified(primitiveId, "primitiveId");
            if (ADD_FLUID.equals(operationKind)) {
                fluidBodyId = FoundationValidationV2.qualified(fluidBodyId, "fluidBodyId");
            } else {
                if (fluidBodyId != null && !fluidBodyId.isBlank()) {
                    throw new IllegalArgumentException("CARVE_SOLID must not own fluidBodyId");
                }
                fluidBodyId = "";
            }
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "underground-river-sdf-binding-v1";
        public static final String CSG_CONTRACT = "underground-river-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("underground river artifact bindingVersion must be 1");
            }
            sourceArtifactChecksum = requireChecksum(sourceArtifactChecksum, "sourceArtifactChecksum");
            bindingContractVersion = FoundationValidationV2.nonBlank(
                    bindingContractVersion, "bindingContractVersion", 64);
        }
    }

    private static void requireCarveThenSingleFluid(List<OrderedVolumeOp> ops, String expectedFluid) {
        boolean sawCarve = false;
        boolean sawFluid = false;
        for (OrderedVolumeOp op : ops) {
            if (OrderedVolumeOp.CARVE_SOLID.equals(op.operationKind())) {
                if (sawFluid) {
                    throw new IllegalArgumentException("CARVE_SOLID must precede ADD_FLUID");
                }
                sawCarve = true;
            } else if (OrderedVolumeOp.ADD_FLUID.equals(op.operationKind())) {
                if (!sawCarve) {
                    throw new IllegalArgumentException("ADD_FLUID must follow CARVE_SOLID");
                }
                if (sawFluid) {
                    throw new IllegalArgumentException("underground river allows exactly one ADD_FLUID");
                }
                if (!expectedFluid.equals(op.fluidBodyId())) {
                    throw new IllegalArgumentException("ADD_FLUID fluidBodyId mismatch");
                }
                sawFluid = true;
            }
        }
        if (!sawCarve || !sawFluid) {
            throw new IllegalArgumentException("underground river requires carve then single ADD_FLUID");
        }
    }

    private static String requireChecksum(String value, String field) {
        String checksum = FoundationValidationV2.checksum(value, field);
        if (!CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex");
        }
        return checksum;
    }
}
