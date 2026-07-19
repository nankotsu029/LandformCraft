package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Frozen V2-10-07 execution plan for an EXPERIMENTAL lava-tube swept tunnel carve. */
public record LavaTubePlanV2(
        int planVersion,
        String featureId,
        String volcanicConeFeatureId,
        String coneGeometryChecksum,
        String coneRelationId,
        String provenanceFeatureId,
        ProvenanceKind provenanceKind,
        String provenanceGeometryChecksum,
        String provenanceRelationId,
        List<TubeSample> centerline,
        int selectedTubeRadiusBlocks,
        int selectedRoofClearanceBlocks,
        int selectedSupportRadiusBlocks,
        String entranceNodeId,
        String materialProfileId,
        VolumeSdfAabbV2 aabb,
        List<OrderedCarveOp> orderedCarveOps,
        ArtifactBinding sdfPlanBinding,
        ArtifactBinding csgPlanBinding,
        String tubeMaskFieldId,
        String roofClearanceFieldId,
        String supportFieldId,
        String ownershipFieldId,
        String continuityFieldId,
        String materialHandoffFieldId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.lava-tube";
    public static final String MODULE_VERSION = "0.1.0-v2-10-07";
    public static final String CONTRACT = "lava-tube-plan-contract-v1";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;
    public static final int MAXIMUM_CARVE_OPS = 4;
    public static final String MATERIAL_PROFILE_ID = "volcanic-basalt-tube-profile";
    public static final String TUBE_MASK_FIELD_ID = "foundation.lava-tube.tube-mask";
    public static final String ROOF_CLEARANCE_FIELD_ID = "foundation.lava-tube.roof-clearance";
    public static final String SUPPORT_FIELD_ID = "foundation.lava-tube.support";
    public static final String OWNERSHIP_FIELD_ID = "foundation.lava-tube.ownership";
    public static final String CONTINUITY_FIELD_ID = "foundation.lava-tube.continuity";
    public static final String MATERIAL_HANDOFF_FIELD_ID = "foundation.lava-tube.material-handoff";

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    public enum ProvenanceKind {
        CALDERA,
        LAVA_FLOW
    }

    public LavaTubePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("lava tube planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        volcanicConeFeatureId = FoundationValidationV2.slug(volcanicConeFeatureId, "volcanicConeFeatureId");
        coneGeometryChecksum = requireChecksum(coneGeometryChecksum, "coneGeometryChecksum");
        coneRelationId = FoundationValidationV2.slug(coneRelationId, "coneRelationId");
        provenanceFeatureId = FoundationValidationV2.slug(provenanceFeatureId, "provenanceFeatureId");
        Objects.requireNonNull(provenanceKind, "provenanceKind");
        provenanceGeometryChecksum = requireChecksum(provenanceGeometryChecksum, "provenanceGeometryChecksum");
        provenanceRelationId = FoundationValidationV2.slug(provenanceRelationId, "provenanceRelationId");
        centerline = FoundationValidationV2.sorted(
                centerline, "centerline", 32,
                Comparator.comparingInt(TubeSample::ordinal));
        if (centerline.size() < 2) {
            throw new IllegalArgumentException("lava tube requires at least two centerline samples");
        }
        if (selectedTubeRadiusBlocks < 2 || selectedTubeRadiusBlocks > 8
                || selectedRoofClearanceBlocks < 2 || selectedRoofClearanceBlocks > 16
                || selectedSupportRadiusBlocks < 2 || selectedSupportRadiusBlocks > 16) {
            throw new IllegalArgumentException("lava tube selected dimensions are invalid");
        }
        entranceNodeId = FoundationValidationV2.qualified(entranceNodeId, "entranceNodeId");
        materialProfileId = FoundationValidationV2.nonBlank(materialProfileId, "materialProfileId", 64);
        if (!MATERIAL_PROFILE_ID.equals(materialProfileId)) {
            throw new IllegalArgumentException("lava tube materialProfileId must be volcanic-basalt-tube-profile");
        }
        Objects.requireNonNull(aabb, "aabb");
        orderedCarveOps = FoundationValidationV2.sorted(
                orderedCarveOps, "orderedCarveOps", MAXIMUM_CARVE_OPS,
                Comparator.comparingInt(OrderedCarveOp::ordinal).thenComparing(OrderedCarveOp::opId));
        if (orderedCarveOps.isEmpty()) {
            throw new IllegalArgumentException("lava tube requires at least one ordered carve op");
        }
        Objects.requireNonNull(sdfPlanBinding, "sdfPlanBinding");
        Objects.requireNonNull(csgPlanBinding, "csgPlanBinding");
        tubeMaskFieldId = FoundationValidationV2.qualified(tubeMaskFieldId, "tubeMaskFieldId");
        roofClearanceFieldId = FoundationValidationV2.qualified(roofClearanceFieldId, "roofClearanceFieldId");
        supportFieldId = FoundationValidationV2.qualified(supportFieldId, "supportFieldId");
        ownershipFieldId = FoundationValidationV2.qualified(ownershipFieldId, "ownershipFieldId");
        continuityFieldId = FoundationValidationV2.qualified(continuityFieldId, "continuityFieldId");
        materialHandoffFieldId = FoundationValidationV2.qualified(materialHandoffFieldId, "materialHandoffFieldId");
        if (supportRadiusXZ < 2 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("lava tube support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public LavaTubePlanV2 withCanonicalChecksum(String checksum) {
        return new LavaTubePlanV2(
                planVersion, featureId, volcanicConeFeatureId, coneGeometryChecksum, coneRelationId,
                provenanceFeatureId, provenanceKind, provenanceGeometryChecksum, provenanceRelationId,
                centerline, selectedTubeRadiusBlocks, selectedRoofClearanceBlocks, selectedSupportRadiusBlocks,
                entranceNodeId, materialProfileId, aabb, orderedCarveOps, sdfPlanBinding, csgPlanBinding,
                tubeMaskFieldId, roofClearanceFieldId, supportFieldId, ownershipFieldId, continuityFieldId,
                materialHandoffFieldId, supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }

    public record TubeSample(
            int ordinal,
            long xMillionths,
            long yMillionths,
            long zMillionths,
            long radiusMillionths
    ) {
        public TubeSample {
            if (ordinal < 0 || ordinal > 1_000) {
                throw new IllegalArgumentException("tube sample ordinal out of range");
            }
            if (radiusMillionths < 2_000_000L || radiusMillionths > 8_000_000L) {
                throw new IllegalArgumentException("tube sample radius out of range");
            }
        }
    }

    public record OrderedCarveOp(
            String opId,
            int ordinal,
            String operationKind,
            String primitiveId
    ) {
        public static final String CARVE_SOLID = "CARVE_SOLID";

        public OrderedCarveOp {
            opId = FoundationValidationV2.qualified(opId, "opId");
            if (ordinal < 0 || ordinal > 1_000) {
                throw new IllegalArgumentException("ordered carve ordinal out of range");
            }
            operationKind = FoundationValidationV2.nonBlank(operationKind, "operationKind", 32);
            if (!CARVE_SOLID.equals(operationKind)) {
                throw new IllegalArgumentException("lava tube carve op must be CARVE_SOLID");
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
        public static final String SDF_CONTRACT = "lava-tube-sdf-binding-v1";
        public static final String CSG_CONTRACT = "lava-tube-csg-binding-v1";

        public ArtifactBinding {
            if (bindingVersion != VERSION) {
                throw new IllegalArgumentException("lava tube artifact bindingVersion must be 1");
            }
            sourceArtifactChecksum = requireChecksum(sourceArtifactChecksum, "sourceArtifactChecksum");
            bindingContractVersion = FoundationValidationV2.nonBlank(
                    bindingContractVersion, "bindingContractVersion", 64);
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
