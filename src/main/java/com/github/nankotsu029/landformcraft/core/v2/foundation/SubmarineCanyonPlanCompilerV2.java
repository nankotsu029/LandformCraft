package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformSubmarineCanyonModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.BathymetryFixedMathV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.BathymetrySampleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.ContinentalShelfGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.ContinentalSlopeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.OceanBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry.SubmarineCanyonFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalShelfPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ContinentalSlopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SubmarineCanyonPlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Integer-only compiler for one SUBMARINE_CANYON spline feature bound to shelf/slope/basin hosts. */
public final class SubmarineCanyonPlanCompilerV2 {
    private static final int MAXIMUM_POLYLINE_SEGMENTS = 8_192;

    public SubmarineCanyonPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            ContinentalShelfPlanV2 shelfPlan,
            ContinentalSlopePlanV2 slopePlan,
            OceanBasinPlanV2 basinPlan,
            String shelfGeometryChecksum,
            String slopeGeometryChecksum,
            String basinGeometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(shelfPlan, "shelfPlan");
        Objects.requireNonNull(slopePlan, "slopePlan");
        Objects.requireNonNull(basinPlan, "basinPlan");
        Objects.requireNonNull(shelfGeometryChecksum, "shelfGeometryChecksum");
        Objects.requireNonNull(slopeGeometryChecksum, "slopeGeometryChecksum");
        Objects.requireNonNull(basinGeometryChecksum, "basinGeometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.SUBMARINE_CANYON) {
            throw failure("v2.submarine-canyon-kind", "feature kind is not SUBMARINE_CANYON");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)
                || spline.points().size() < 2) {
            throw failure("v2.submarine-canyon-geometry",
                    "submarine canyon requires SPLINE geometry with at least two points");
        }
        TerrainIntentV2.SubmarineCanyonParameters parameters =
                (TerrainIntentV2.SubmarineCanyonParameters) feature.parameters();
        HostBinding binding = resolveHostBinding(feature, intent);
        if (!binding.shelfFeatureId().equals(shelfPlan.featureId())
                || !binding.slopeFeatureId().equals(slopePlan.featureId())
                || !binding.basinFeatureId().equals(basinPlan.featureId())) {
            throw failure("v2.submarine-canyon-missing-relation",
                    "compiled host plans do not match resolved submarine canyon relations");
        }
        try {
            int selectedFloor = midpoint(parameters.floorWidthBlocks());
            int selectedRim = midpoint(parameters.rimWidthBlocks());
            int selectedCarve = midpoint(parameters.additionalCarveDepthBlocks());
            if (selectedRim < selectedFloor + 2) {
                throw failure("v2.submarine-canyon-thin-wall", "submarine canyon wall thickness collapsed");
            }
            ContinentalShelfGeneratorV2 shelfGenerator = new ContinentalShelfGeneratorV2(shelfPlan);
            ContinentalSlopeGeneratorV2 slopeGenerator = new ContinentalSlopeGeneratorV2(slopePlan);
            OceanBasinGeneratorV2 basinGenerator = new OceanBasinGeneratorV2(basinPlan);

            List<long[]> polyline = flattenGuide(toBlockPoints(spline.points(), bounds.width(), bounds.length()));
            if (polyline.size() < 2) {
                throw failure("v2.submarine-canyon-geometry", "submarine canyon centerline collapsed");
            }
            List<SubmarineCanyonPlanV2.CenterlineSample> centerline = buildCenterline(
                    polyline, selectedCarve, shelfPlan, slopePlan, basinPlan,
                    shelfGenerator, slopeGenerator, basinGenerator, bounds);

            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > SubmarineCanyonPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.submarine-canyon-budget", "submarine canyon profile/raster budget exceeded");
            }
            int support = Math.min(
                    LandformSubmarineCanyonModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ,
                    Math.max(8, (selectedRim + 1) / 2));
            return new SubmarineCanyonPlanV2(
                    SubmarineCanyonPlanV2.VERSION,
                    feature.id(),
                    binding.shelfFeatureId(),
                    binding.slopeFeatureId(),
                    binding.basinFeatureId(),
                    binding.headRelationId(),
                    binding.crossingRelationId(),
                    binding.outletRelationId(),
                    parameters.crossSection(),
                    parameters.terraceCount(),
                    parameters.terraceWidthBlocks(),
                    centerline,
                    centerline.getLast().arcLengthMillionths(),
                    selectedFloor,
                    selectedRim,
                    selectedCarve,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    SubmarineCanyonPlanV2.MASK_FIELD_ID,
                    SubmarineCanyonPlanV2.FLOOR_DEPTH_FIELD_ID,
                    SubmarineCanyonPlanV2.OWNERSHIP_FIELD_ID,
                    SubmarineCanyonPlanV2.HOST_HANDOFF_FIELD_ID,
                    SubmarineCanyonPlanV2.FLUID_COLUMN_HINT_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    shelfGeometryChecksum,
                    slopeGeometryChecksum,
                    basinGeometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.submarine-canyon-budget", "submarine canyon arithmetic overflow", exception);
        }
    }

    public HostBinding resolveHostBinding(TerrainIntentV2.Feature canyon, TerrainIntentV2 intent) {
        String canyonEndpoint = "feature:" + canyon.id();
        Optional<TerrainIntentV2.Relation> head = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> isHeadRelation(relation, canyonEndpoint, intent))
                .findFirst();
        Optional<TerrainIntentV2.Relation> crossing = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> isCrossingRelation(relation, canyonEndpoint, intent))
                .findFirst();
        Optional<TerrainIntentV2.Relation> outlet = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> isOutletRelation(relation, canyonEndpoint, intent))
                .findFirst();
        if (head.isEmpty() || crossing.isEmpty() || outlet.isEmpty()) {
            throw failure("v2.submarine-canyon-missing-relation",
                    "submarine canyon requires HARD head/crossing/outlet host relations");
        }
        return new HostBinding(
                head.get().id(),
                crossing.get().id(),
                outlet.get().id(),
                hostFeatureId(head.get(), canyonEndpoint),
                hostFeatureId(crossing.get(), canyonEndpoint),
                hostFeatureId(outlet.get(), canyonEndpoint));
    }

    private static boolean isHeadRelation(
            TerrainIntentV2.Relation relation,
            String canyonEndpoint,
            TerrainIntentV2 intent
    ) {
        TerrainIntentV2.RelationKind kind = relation.kind();
        if (kind != TerrainIntentV2.RelationKind.ORIGINATES_AT
                && kind != TerrainIntentV2.RelationKind.WITHIN
                && kind != TerrainIntentV2.RelationKind.ADJACENT_TO) {
            return false;
        }
        String hostId = otherEndpoint(relation, canyonEndpoint);
        if (hostId == null) {
            return false;
        }
        return featureKind(intent, hostId) == TerrainIntentV2.FeatureKind.CONTINENTAL_SHELF;
    }

    private static boolean isCrossingRelation(
            TerrainIntentV2.Relation relation,
            String canyonEndpoint,
            TerrainIntentV2 intent
    ) {
        if (relation.kind() != TerrainIntentV2.RelationKind.CARVES_THROUGH) {
            return false;
        }
        if (!relation.from().equals(canyonEndpoint)) {
            return false;
        }
        String hostId = endpointFeatureId(relation.to());
        return hostId != null && featureKind(intent, hostId) == TerrainIntentV2.FeatureKind.CONTINENTAL_SLOPE;
    }

    private static boolean isOutletRelation(
            TerrainIntentV2.Relation relation,
            String canyonEndpoint,
            TerrainIntentV2 intent
    ) {
        TerrainIntentV2.RelationKind kind = relation.kind();
        if (kind != TerrainIntentV2.RelationKind.EMPTIES_INTO
                && kind != TerrainIntentV2.RelationKind.DRAINS_TO) {
            return false;
        }
        if (!relation.from().equals(canyonEndpoint)) {
            return false;
        }
        String hostId = endpointFeatureId(relation.to());
        return hostId != null && featureKind(intent, hostId) == TerrainIntentV2.FeatureKind.OCEAN_BASIN;
    }

    private static String hostFeatureId(TerrainIntentV2.Relation relation, String canyonEndpoint) {
        String hostId = otherEndpoint(relation, canyonEndpoint);
        if (hostId == null) {
            throw failure("v2.submarine-canyon-missing-relation", "host relation endpoint is invalid");
        }
        return hostId;
    }

    private static String otherEndpoint(TerrainIntentV2.Relation relation, String canyonEndpoint) {
        if (relation.from().equals(canyonEndpoint)) {
            return endpointFeatureId(relation.to());
        }
        if (relation.to().equals(canyonEndpoint)) {
            return endpointFeatureId(relation.from());
        }
        return null;
    }

    private static String endpointFeatureId(String endpoint) {
        if (endpoint == null || !endpoint.startsWith("feature:")) {
            return null;
        }
        return endpoint.substring("feature:".length());
    }

    private static TerrainIntentV2.FeatureKind featureKind(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId))
                .map(TerrainIntentV2.Feature::kind)
                .findFirst()
                .orElseThrow(() -> failure("v2.submarine-canyon-missing-relation",
                        "host feature is missing: " + featureId));
    }

    private static List<SubmarineCanyonPlanV2.CenterlineSample> buildCenterline(
            List<long[]> polyline,
            int additionalCarve,
            ContinentalShelfPlanV2 shelfPlan,
            ContinentalSlopePlanV2 slopePlan,
            OceanBasinPlanV2 basinPlan,
            ContinentalShelfGeneratorV2 shelfGenerator,
            ContinentalSlopeGeneratorV2 slopeGenerator,
            OceanBasinGeneratorV2 basinGenerator,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<SubmarineCanyonPlanV2.CenterlineSample> samples = new ArrayList<>();
        long travelled = 0L;
        int previousDepth = -1;
        boolean sawSlope = false;
        for (int index = 0; index < polyline.size(); index++) {
            long x = polyline.get(index)[0];
            long z = polyline.get(index)[1];
            if (index > 0) {
                travelled = Math.addExact(travelled, SubmarineCanyonFixedMathV2.hypotMillionths(
                        x - polyline.get(index - 1)[0], z - polyline.get(index - 1)[1]));
            }
            int blockX = Math.toIntExact(SubmarineCanyonFixedMathV2.roundDivide(
                    x, SubmarineCanyonFixedMathV2.FIXED_SCALE));
            int blockZ = Math.toIntExact(SubmarineCanyonFixedMathV2.roundDivide(
                    z, SubmarineCanyonFixedMathV2.FIXED_SCALE));
            blockX = Math.max(0, Math.min(bounds.width() - 1, blockX));
            blockZ = Math.max(0, Math.min(bounds.length() - 1, blockZ));

            boolean inShelf = BathymetryFixedMathV2.contains(shelfPlan.rings(), x, z);
            boolean inSlope = BathymetryFixedMathV2.contains(slopePlan.rings(), x, z);
            boolean inBasin = BathymetryFixedMathV2.contains(basinPlan.rings(), x, z);
            if (!inShelf && !inSlope && !inBasin) {
                throw failure("v2.submarine-canyon-out-of-host",
                        "submarine canyon centerline leaves shelf/slope/basin hosts");
            }
            SubmarineCanyonPlanV2.HostRole role = assignHostRole(inShelf, inSlope, inBasin);
            if (role == SubmarineCanyonPlanV2.HostRole.SLOPE_CROSSING) {
                sawSlope = true;
            }
            BathymetrySampleV2 hostSample = switch (role) {
                case HEAD_SHELF -> shelfGenerator.sampleAt(blockX, blockZ);
                case SLOPE_CROSSING -> slopeGenerator.sampleAt(blockX, blockZ);
                case OUTLET_BASIN -> basinGenerator.sampleAt(blockX, blockZ);
            };
            if (!hostSample.owned()) {
                throw failure("v2.submarine-canyon-out-of-host",
                        "submarine canyon host bathymetry sample is unowned");
            }
            int floorDepth = Math.addExact(hostSample.depthBlocksBelowSea(), additionalCarve);
            if (previousDepth >= 0 && floorDepth < previousDepth) {
                throw failure("v2.submarine-canyon-up-gradient",
                        "submarine canyon floor depth rises seaward along arc");
            }
            previousDepth = floorDepth;
            samples.add(new SubmarineCanyonPlanV2.CenterlineSample(
                    index, x, z, travelled, role, floorDepth));
        }
        if (samples.getFirst().hostRole() != SubmarineCanyonPlanV2.HostRole.HEAD_SHELF) {
            throw failure("v2.submarine-canyon-out-of-host",
                    "submarine canyon head sample must lie in continental shelf");
        }
        if (samples.getLast().hostRole() != SubmarineCanyonPlanV2.HostRole.OUTLET_BASIN) {
            throw failure("v2.submarine-canyon-out-of-host",
                    "submarine canyon outlet sample must lie in ocean basin");
        }
        if (!sawSlope) {
            throw failure("v2.submarine-canyon-out-of-host",
                    "submarine canyon requires at least one slope-crossing sample");
        }
        if (travelled < SubmarineCanyonFixedMathV2.FIXED_SCALE) {
            throw failure("v2.submarine-canyon-geometry", "submarine canyon centerline shorter than one block");
        }
        return List.copyOf(samples);
    }

    private static SubmarineCanyonPlanV2.HostRole assignHostRole(
            boolean inShelf,
            boolean inSlope,
            boolean inBasin
    ) {
        // Prefer basin > slope > shelf in overlap so seaward handoff progresses shelf→slope→basin.
        if (inBasin) {
            return SubmarineCanyonPlanV2.HostRole.OUTLET_BASIN;
        }
        if (inSlope) {
            return SubmarineCanyonPlanV2.HostRole.SLOPE_CROSSING;
        }
        if (inShelf) {
            return SubmarineCanyonPlanV2.HostRole.HEAD_SHELF;
        }
        throw failure("v2.submarine-canyon-out-of-host", "submarine canyon sample has no host polygon");
    }

    private static List<long[]> toBlockPoints(List<TerrainIntentV2.Point2> points, int width, int length) {
        List<long[]> result = new ArrayList<>(points.size());
        for (TerrainIntentV2.Point2 point : points) {
            result.add(new long[] {
                    Math.multiplyExact((long) point.xMillionths(), width - 1L),
                    Math.multiplyExact((long) point.zMillionths(), length - 1L)
            });
        }
        return result;
    }

    private static List<long[]> flattenGuide(List<long[]> guide) {
        List<long[]> result = new ArrayList<>();
        result.add(guide.getFirst());
        for (int index = 1; index < guide.size(); index++) {
            long[] from = guide.get(index - 1);
            long[] to = guide.get(index);
            long length = SubmarineCanyonFixedMathV2.hypotMillionths(to[0] - from[0], to[1] - from[1]);
            int steps = Math.toIntExact(Math.max(1L,
                    SubmarineCanyonFixedMathV2.roundDivide(length, SubmarineCanyonFixedMathV2.FIXED_SCALE)));
            if (result.size() + steps > MAXIMUM_POLYLINE_SEGMENTS) {
                throw failure("v2.submarine-canyon-budget", "submarine canyon centerline exceeds sample budget");
            }
            for (int step = 1; step <= steps; step++) {
                long x = from[0] + SubmarineCanyonFixedMathV2.roundDivide(
                        Math.multiplyExact(to[0] - from[0], step), steps);
                long z = from[1] + SubmarineCanyonFixedMathV2.roundDivide(
                        Math.multiplyExact(to[1] - from[1], step), steps);
                result.add(new long[] {x, z});
            }
        }
        return result;
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record HostBinding(
            String headRelationId,
            String crossingRelationId,
            String outletRelationId,
            String shelfFeatureId,
            String slopeFeatureId,
            String basinFeatureId
    ) {
    }
}
