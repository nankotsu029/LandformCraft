package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformLavaTubeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.cone.VolcanicConeGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.LavaTubePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.VolcanicConePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles a V2-10-07 LAVA_TUBE plan bound to a frozen volcanic cone host and provenance child. */
public final class LavaTubePlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long FIXED = TerrainIntentV2.FIXED_SCALE;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public HostBinding resolveHostBinding(TerrainIntentV2.Feature tube, TerrainIntentV2 intent) {
        Objects.requireNonNull(tube, "tube");
        Objects.requireNonNull(intent, "intent");
        String endpoint = "feature:" + tube.id();

        List<TerrainIntentV2.Relation> coneHosts = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.WITHIN)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isVolcanicCone(intent, relation.to()))
                .toList();
        if (coneHosts.isEmpty()) {
            throw failure("v2.lava-tube-missing-cone",
                    "lava tube requires exactly one HARD WITHIN volcanic cone");
        }
        if (coneHosts.size() > 1) {
            throw failure("v2.lava-tube-orphan", "lava tube has multiple HARD WITHIN volcanic cone hosts");
        }

        List<TerrainIntentV2.Relation> provenance = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ORIGINATES_AT)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isProvenance(intent, relation.to()))
                .toList();
        if (provenance.isEmpty()) {
            throw failure("v2.lava-tube-missing-provenance",
                    "lava tube requires exactly one HARD ORIGINATES_AT caldera or lava flow");
        }
        if (provenance.size() > 1) {
            throw failure("v2.lava-tube-missing-provenance",
                    "lava tube has multiple HARD ORIGINATES_AT provenance targets");
        }

        TerrainIntentV2.Relation cone = coneHosts.getFirst();
        TerrainIntentV2.Relation prov = provenance.getFirst();
        String coneId = cone.to().substring("feature:".length());
        String provId = prov.to().substring("feature:".length());
        LavaTubePlanV2.ProvenanceKind provKind = featureKind(intent, provId)
                == TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA
                ? LavaTubePlanV2.ProvenanceKind.CALDERA
                : LavaTubePlanV2.ProvenanceKind.LAVA_FLOW;
        return new HostBinding(
                cone.id(), coneId,
                prov.id(), provId, provKind);
    }

    public LavaTubePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            VolcanicConePlanV2 conePlan
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(conePlan, "conePlan");
        if (feature.kind() != TerrainIntentV2.FeatureKind.LAVA_TUBE) {
            throw failure("v2.lava-tube-missing-cone", "feature kind is not LAVA_TUBE");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.LavaTubeParameters parameters)) {
            throw failure("v2.lava-tube-missing-cone", "lava tube requires LavaTubeParameters");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)
                || spline.points().size() < 2) {
            throw failure("v2.lava-tube-missing-cone",
                    "lava tube requires SPLINE geometry with at least 2 points");
        }

        HostBinding binding = resolveHostBinding(feature, intent);
        if (!binding.volcanicConeFeatureId().equals(conePlan.featureId())) {
            throw failure("v2.lava-tube-orphan", "WITHIN target does not match frozen volcanic cone featureId");
        }
        if (!conePlan.geometryChecksum().equals(codec.geometryChecksum(
                requireFeature(intent, binding.volcanicConeFeatureId()).geometry()))) {
            throw failure("v2.lava-tube-orphan", "cone geometry checksum mismatch against frozen plan");
        }

        TerrainIntentV2.Feature provenanceFeature = requireFeature(intent, binding.provenanceFeatureId());
        String provenanceGeometryChecksum = codec.geometryChecksum(provenanceFeature.geometry());

        int tubeRadius = midpoint(parameters.tubeRadiusBlocks());
        int roofClearance = midpoint(parameters.roofClearanceBlocks());
        int supportRadius = midpoint(parameters.supportRadiusBlocks());
        if (roofClearance < 2) {
            throw failure("v2.lava-tube-budget", "roof clearance must be at least 2 blocks");
        }

        VolcanicConeGeneratorV2 coneGenerator = new VolcanicConeGeneratorV2(conePlan);
        int summitY = conePlan.waterLevel() + conePlan.selectedSummitHeightBlocksAboveSea();
        List<LavaTubePlanV2.TubeSample> samples = new ArrayList<>();
        List<VolumeSdfVec3V2> controlPoints = new ArrayList<>();
        int lastIndex = spline.points().size() - 1;
        for (int index = 0; index < spline.points().size(); index++) {
            TerrainIntentV2.Point2 point = spline.points().get(index);
            long xM = scaleCoordinate(point.xMillionths(), bounds.width());
            long zM = scaleCoordinate(point.zMillionths(), bounds.length());
            int blockX = clampBlock(roundDiv(xM, FIXED), bounds.width());
            int blockZ = clampBlock(roundDiv(zM, FIXED), bounds.length());
            VolcanicConeGeneratorV2.ConeSample coneSample = coneGenerator.sampleAt(blockX, blockZ);
            if (!coneSample.active()) {
                throw failure("v2.lava-tube-orphan", "tube path leaves volcanic cone solid");
            }
            long tNumerator = index;
            long tDenominator = Math.max(1, lastIndex);
            int blendedSurface = summitY - Math.toIntExact(
                    (long) (summitY - coneSample.elevationBlocks()) * tNumerator / tDenominator);
            int yBlocks = blendedSurface - roofClearance - tubeRadius;
            if (parameters.entranceOffsetBlocks() > 0 && index == 0) {
                yBlocks = Math.addExact(yBlocks, parameters.entranceOffsetBlocks());
            }
            if (yBlocks < bounds.minY()) {
                throw failure("v2.lava-tube-budget", "tube center drops below world minY");
            }
            long yM = Math.multiplyExact((long) yBlocks, FIXED);
            long radiusM = Math.multiplyExact((long) tubeRadius, FIXED);
            samples.add(new LavaTubePlanV2.TubeSample(index, xM, yM, zM, radiusM));
            controlPoints.add(new VolumeSdfVec3V2(xM, yM, zM));
        }

        String primitiveId = "prim.lava-tube.swept." + feature.id();
        VolumeSdfPrimitiveV2 swept = new VolumeSdfPrimitiveV2.SweptSpline(
                primitiveId, controlPoints, Math.multiplyExact((long) tubeRadius, FIXED));
        VolumeSdfAabbV2 aabb = swept.conservativeBounds();
        long work = Math.addExact(
                Math.multiplyExact(aabb.extentXBlocks() + 1L, aabb.extentZBlocks() + 1L),
                Math.multiplyExact(aabb.extentYBlocks() + 1L, 4L));
        if (work > LavaTubePlanV2.MAXIMUM_WORK_UNITS
                || aabb.extentXBlocks() > 256
                || aabb.extentYBlocks() > 128
                || aabb.extentZBlocks() > 256) {
            throw failure("v2.lava-tube-budget", "lava tube AABB/support budget exceeded");
        }

        VolumeSdfPrimitivePlanV2 sdfPlan = codec.sealVolumeSdfPrimitivePlan(new VolumeSdfPrimitivePlanV2(
                1,
                "volume-sdf-primitive-contract-v1",
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                List.of(swept),
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        "volume-sdf-primitive-budget-v1",
                        1,
                        64,
                        2048,
                        65536,
                        65536),
                ZERO));
        String carveOpId = "op.carve.lava-tube." + feature.id();
        VolumeCsgPlanV2 csgPlan = codec.sealVolumeCsgPlan(new VolumeCsgPlanV2(
                1,
                "volume-csg-contract-v1",
                new VolumeCsgPlanV2.PrimitivePlanBinding(
                        1, sdfPlan.canonicalChecksum(), "volume-csg-primitive-binding-v1"),
                VolumeCsgPlanV2.Kernel.standard(),
                List.of(new VolumeCsgPlanV2.Operator(
                        carveOpId,
                        0,
                        VolumeCsgPlanV2.OperationKind.CARVE_SOLID,
                        primitiveId,
                        VolumeCsgPlanV2.MaskMode.NONE,
                        "",
                        List.of(),
                        "")),
                new VolumeCsgPlanV2.ResourceBudget(
                        "volume-csg-budget-v1",
                        64,
                        4,
                        1024L,
                        1_048_576L,
                        4096,
                        65536,
                        65536),
                ZERO));

        int support = Math.min(
                LandformLavaTubeModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                Math.max(supportRadius, tubeRadius + 2));

        List<LavaTubePlanV2.OrderedCarveOp> carveOps = List.of(
                new LavaTubePlanV2.OrderedCarveOp(
                        carveOpId, 0, LavaTubePlanV2.OrderedCarveOp.CARVE_SOLID, primitiveId));

        return new LavaTubePlanV2(
                LavaTubePlanV2.VERSION,
                feature.id(),
                binding.volcanicConeFeatureId(),
                conePlan.geometryChecksum(),
                binding.coneRelationId(),
                binding.provenanceFeatureId(),
                binding.provenanceKind(),
                provenanceGeometryChecksum,
                binding.provenanceRelationId(),
                samples,
                tubeRadius,
                roofClearance,
                supportRadius,
                "node.tube.entrance." + feature.id(),
                LavaTubePlanV2.MATERIAL_PROFILE_ID,
                aabb,
                carveOps,
                new LavaTubePlanV2.ArtifactBinding(
                        LavaTubePlanV2.ArtifactBinding.VERSION,
                        sdfPlan.canonicalChecksum(),
                        LavaTubePlanV2.ArtifactBinding.SDF_CONTRACT),
                new LavaTubePlanV2.ArtifactBinding(
                        LavaTubePlanV2.ArtifactBinding.VERSION,
                        csgPlan.canonicalChecksum(),
                        LavaTubePlanV2.ArtifactBinding.CSG_CONTRACT),
                LavaTubePlanV2.TUBE_MASK_FIELD_ID,
                LavaTubePlanV2.ROOF_CLEARANCE_FIELD_ID,
                LavaTubePlanV2.SUPPORT_FIELD_ID,
                LavaTubePlanV2.OWNERSHIP_FIELD_ID,
                LavaTubePlanV2.CONTINUITY_FIELD_ID,
                LavaTubePlanV2.MATERIAL_HANDOFF_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    public record HostBinding(
            String coneRelationId,
            String volcanicConeFeatureId,
            String provenanceRelationId,
            String provenanceFeatureId,
            LavaTubePlanV2.ProvenanceKind provenanceKind
    ) {
    }

    private static boolean isVolcanicCone(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        return featureKind(intent, endpoint.substring("feature:".length()))
                == TerrainIntentV2.FeatureKind.VOLCANIC_CONE;
    }

    private static boolean isProvenance(TerrainIntentV2 intent, String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return false;
        }
        TerrainIntentV2.FeatureKind kind = featureKind(intent, endpoint.substring("feature:".length()));
        return kind == TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA
                || kind == TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD;
    }

    private static TerrainIntentV2.FeatureKind featureKind(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId))
                .map(TerrainIntentV2.Feature::kind)
                .findFirst()
                .orElseThrow(() -> failure("v2.lava-tube-orphan",
                        "required host feature is missing: " + featureId));
    }

    private static TerrainIntentV2.Feature requireFeature(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId))
                .findFirst()
                .orElseThrow(() -> failure("v2.lava-tube-orphan",
                        "required feature is missing: " + featureId));
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static long scaleCoordinate(int normalizedMillionths, int span) {
        return Math.multiplyExact((long) normalizedMillionths, span - 1L);
    }

    private static int roundDiv(long value, long divisor) {
        if (value >= 0L) {
            return Math.toIntExact((value + divisor / 2L) / divisor);
        }
        return Math.toIntExact((value - divisor / 2L) / divisor);
    }

    private static int clampBlock(int value, int span) {
        return Math.max(0, Math.min(span - 1, value));
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
