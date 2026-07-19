package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Frozen V2-10-01 common glacial-ice foundation contract shared by
 * {@code VALLEY_GLACIER}, {@code ICE_CAP}, and {@code ICE_SHEET}.
 * Ice mass is a bounded sparse {@code ADD_SOLID} AABB — never a dense voxel store.
 */
public record GlacialIcePlanV2(
        int planVersion,
        String featureId,
        IceKind iceKind,
        String bedHostFeatureId,
        BedHostKind bedHostKind,
        String bedHostRelationId,
        String bedHostGeometryChecksum,
        String climatePreset,
        String climateBindingChecksum,
        String snowProfileId,
        int flowAzimuthDegrees,
        int selectedThicknessBlocks,
        int selectedHalfWidthBlocks,
        long headXMillionths,
        long headZMillionths,
        long terminusXMillionths,
        long terminusZMillionths,
        String meltwaterHandoffFeatureId,
        String meltwaterHandoffChecksum,
        VolumeSdfAabbV2 aabb,
        List<OrderedVolumeOp> orderedOps,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        String surfaceOwnershipFieldId,
        String volumeOwnershipFieldId,
        String thicknessFieldId,
        String flowDirectionFieldId,
        String bedContactFieldId,
        String meltwaterMaskFieldId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.glacial-ice";
    public static final String MODULE_VERSION = "0.1.0-v2-10-01";
    public static final String CONTRACT = "glacial-ice-plan-contract-v1";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final int MAXIMUM_OPS = 8;
    public static final String SURFACE_OWNERSHIP_FIELD_ID = "foundation.glacial-ice.surface-ownership";
    public static final String VOLUME_OWNERSHIP_FIELD_ID = "foundation.glacial-ice.volume-ownership";
    public static final String THICKNESS_FIELD_ID = "foundation.glacial-ice.thickness";
    public static final String FLOW_DIRECTION_FIELD_ID = "foundation.glacial-ice.flow-direction";
    public static final String BED_CONTACT_FIELD_ID = "foundation.glacial-ice.bed-contact";
    public static final String MELTWATER_MASK_FIELD_ID = "foundation.glacial-ice.meltwater-mask";
    public static final String DEFAULT_SNOW_PROFILE_ID = "cold-snow-ice-profile";

    public static final Set<String> COLD_CLIMATE_PRESETS = Set.of(
            "COLD_ALPINE", "COLD_MARITIME", "COOL_HIGH_ALTITUDE");

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    public enum IceKind {
        VALLEY_GLACIER,
        ICE_CAP,
        ICE_SHEET
    }

    public enum BedHostKind {
        VALLEY,
        MOUNTAIN_RANGE,
        PLAIN
    }

    public GlacialIcePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("glacial ice planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        Objects.requireNonNull(iceKind, "iceKind");
        bedHostFeatureId = FoundationValidationV2.slug(bedHostFeatureId, "bedHostFeatureId");
        Objects.requireNonNull(bedHostKind, "bedHostKind");
        requireBedHostForKind(iceKind, bedHostKind);
        bedHostRelationId = FoundationValidationV2.slug(bedHostRelationId, "bedHostRelationId");
        bedHostGeometryChecksum = requireChecksum(bedHostGeometryChecksum, "bedHostGeometryChecksum");
        climatePreset = FoundationValidationV2.nonBlank(climatePreset, "climatePreset", 64);
        if (!COLD_CLIMATE_PRESETS.contains(climatePreset)) {
            throw new IllegalArgumentException("glacial ice requires a cold climatePreset");
        }
        climateBindingChecksum = requireChecksum(climateBindingChecksum, "climateBindingChecksum");
        snowProfileId = FoundationValidationV2.qualified(snowProfileId, "snowProfileId");
        if (flowAzimuthDegrees < 0 || flowAzimuthDegrees > 359) {
            throw new IllegalArgumentException("flowAzimuthDegrees must be in 0..359");
        }
        if (selectedThicknessBlocks < 2 || selectedThicknessBlocks > 64
                || selectedHalfWidthBlocks < 2 || selectedHalfWidthBlocks > 128) {
            throw new IllegalArgumentException("glacial ice selected dimensions are invalid");
        }
        if (headXMillionths == terminusXMillionths && headZMillionths == terminusZMillionths) {
            throw new IllegalArgumentException("glacial ice head and terminus must differ");
        }
        if (meltwaterHandoffFeatureId == null || meltwaterHandoffFeatureId.isBlank()) {
            meltwaterHandoffFeatureId = "";
            meltwaterHandoffChecksum = "";
        } else {
            meltwaterHandoffFeatureId = FoundationValidationV2.slug(
                    meltwaterHandoffFeatureId, "meltwaterHandoffFeatureId");
            meltwaterHandoffChecksum = requireChecksum(meltwaterHandoffChecksum, "meltwaterHandoffChecksum");
        }
        Objects.requireNonNull(aabb, "aabb");
        orderedOps = FoundationValidationV2.sorted(
                orderedOps, "orderedOps", MAXIMUM_OPS,
                Comparator.comparingInt(OrderedVolumeOp::ordinal).thenComparing(OrderedVolumeOp::opId));
        if (orderedOps.isEmpty()) {
            throw new IllegalArgumentException("glacial ice requires at least one ADD_SOLID op");
        }
        requireSingleAddSolid(orderedOps);
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        surfaceOwnershipFieldId = FoundationValidationV2.qualified(
                surfaceOwnershipFieldId, "surfaceOwnershipFieldId");
        volumeOwnershipFieldId = FoundationValidationV2.qualified(
                volumeOwnershipFieldId, "volumeOwnershipFieldId");
        thicknessFieldId = FoundationValidationV2.qualified(thicknessFieldId, "thicknessFieldId");
        flowDirectionFieldId = FoundationValidationV2.qualified(
                flowDirectionFieldId, "flowDirectionFieldId");
        bedContactFieldId = FoundationValidationV2.qualified(bedContactFieldId, "bedContactFieldId");
        meltwaterMaskFieldId = FoundationValidationV2.qualified(
                meltwaterMaskFieldId, "meltwaterMaskFieldId");
        if (supportRadiusXZ < 1 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("glacial ice support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public GlacialIcePlanV2 withCanonicalChecksum(String checksum) {
        return new GlacialIcePlanV2(
                planVersion, featureId, iceKind, bedHostFeatureId, bedHostKind, bedHostRelationId,
                bedHostGeometryChecksum, climatePreset, climateBindingChecksum, snowProfileId,
                flowAzimuthDegrees, selectedThicknessBlocks, selectedHalfWidthBlocks,
                headXMillionths, headZMillionths, terminusXMillionths, terminusZMillionths,
                meltwaterHandoffFeatureId, meltwaterHandoffChecksum, aabb, orderedOps,
                sdfPlanBinding, csgPlanBinding, surfaceOwnershipFieldId, volumeOwnershipFieldId,
                thicknessFieldId, flowDirectionFieldId, bedContactFieldId, meltwaterMaskFieldId,
                supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }

    public record OrderedVolumeOp(
            String opId,
            int ordinal,
            String operationKind,
            String primitiveId
    ) {
        public static final String ADD_SOLID = "ADD_SOLID";

        public OrderedVolumeOp {
            opId = FoundationValidationV2.qualified(opId, "opId");
            if (ordinal < 0 || ordinal > 1_000) {
                throw new IllegalArgumentException("ordered volume ordinal out of range");
            }
            operationKind = FoundationValidationV2.nonBlank(operationKind, "operationKind", 32);
            if (!ADD_SOLID.equals(operationKind)) {
                throw new IllegalArgumentException("glacial ice op must be ADD_SOLID");
            }
            primitiveId = FoundationValidationV2.qualified(primitiveId, "primitiveId");
        }
    }

    public record ArtifactBinding(
            int bindingVersion,
            String sourceArtifactChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String SDF_CONTRACT = "glacial-ice-sdf-binding-v1";
        public static final String CSG_CONTRACT = "glacial-ice-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("glacial ice artifact bindingVersion must be 1");
            }
            sourceArtifactChecksum = requireChecksum(sourceArtifactChecksum, "sourceArtifactChecksum");
            bindingContractVersion = FoundationValidationV2.nonBlank(
                    bindingContractVersion, "bindingContractVersion", 64);
        }
    }

    private static void requireBedHostForKind(IceKind iceKind, BedHostKind bedHostKind) {
        boolean ok = switch (iceKind) {
            case VALLEY_GLACIER -> bedHostKind == BedHostKind.VALLEY;
            case ICE_CAP -> bedHostKind == BedHostKind.MOUNTAIN_RANGE || bedHostKind == BedHostKind.PLAIN;
            case ICE_SHEET -> bedHostKind == BedHostKind.PLAIN;
        };
        if (!ok) {
            throw new IllegalArgumentException("bed host kind is unsupported for " + iceKind);
        }
    }

    private static void requireSingleAddSolid(List<OrderedVolumeOp> ops) {
        long addSolid = ops.stream()
                .filter(op -> OrderedVolumeOp.ADD_SOLID.equals(op.operationKind()))
                .count();
        if (addSolid != ops.size() || addSolid < 1L) {
            throw new IllegalArgumentException("glacial ice requires only ADD_SOLID ops");
        }
    }

    private static String requireChecksum(String value, String field) {
        String checksum = FoundationValidationV2.checksum(value, field);
        if (!CHECKSUM.matcher(checksum).matches()) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex");
        }
        return checksum;
    }

    public static TerrainIntentV2.FeatureKind featureKindOf(IceKind iceKind) {
        return switch (iceKind) {
            case VALLEY_GLACIER -> TerrainIntentV2.FeatureKind.VALLEY_GLACIER;
            case ICE_CAP -> TerrainIntentV2.FeatureKind.ICE_CAP;
            case ICE_SHEET -> TerrainIntentV2.FeatureKind.ICE_SHEET;
        };
    }

    public static IceKind iceKindOf(TerrainIntentV2.FeatureKind kind) {
        return switch (kind) {
            case VALLEY_GLACIER -> IceKind.VALLEY_GLACIER;
            case ICE_CAP -> IceKind.ICE_CAP;
            case ICE_SHEET -> IceKind.ICE_SHEET;
            default -> throw new IllegalArgumentException("not a glacial ice feature kind: " + kind);
        };
    }
}
