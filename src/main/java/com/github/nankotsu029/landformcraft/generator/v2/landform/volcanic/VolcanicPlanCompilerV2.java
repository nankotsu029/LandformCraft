package com.github.nankotsu029.landformcraft.generator.v2.landform.volcanic;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Integer-only compiler for stable volcanic island masses and child-plan hooks. */
public final class VolcanicPlanCompilerV2 {
    private static final long MINIMUM_DRY_GAP_MILLIONTHS = 12L * TerrainIntentV2.FIXED_SCALE;

    public VolcanicPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO) {
            throw failure("v2.volcanic-kind", "feature kind is not VOLCANIC_ARCHIPELAGO");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.MultiPointGeometry geometry)) {
            throw failure("v2.volcanic-geometry", "volcanic archipelago requires MULTI_POINT geometry");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.VolcanicArchipelagoParameters parameters)) {
            throw failure("v2.volcanic-parameters", "volcanic archipelago requires typed parameters");
        }
        try {
            List<VolcanicPlanV2.IslandMass> islands = compileIslands(parameters, geometry, bounds);
            requireDryGaps(islands);
            int dominant = requireDominance(islands);
            int saddleDepth = midpoint(parameters.submarineSaddleDepthBlocks());
            List<VolcanicPlanV2.SubmarineSaddle> saddles = compileSaddles(islands, saddleDepth);
            VolcanicPlanV2.CalderaPlanHook caldera = compileCaldera(feature, intent, islands.get(dominant), bounds);
            VolcanicPlanV2.LavaPlanHook lava = compileLava(intent, caldera);
            int support = islands.stream().mapToInt(VolcanicPlanV2.IslandMass::radiusBlocks).max().orElseThrow();
            long work = Math.multiplyExact(Math.multiplyExact((long) bounds.width(), bounds.length()), 6L);
            if (support > LandformVolcanicModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ
                    || work > VolcanicPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.volcanic-budget", "volcanic support or raster work budget exceeded");
            }
            return new VolcanicPlanV2(
                    VolcanicPlanV2.VERSION, feature.id(), islands, saddles, caldera, lava, saddleDepth,
                    dominant, bounds.minY(), bounds.maxY(), bounds.waterLevel(), bounds.width(), bounds.length(),
                    LandformVolcanicModuleV2.ISLAND_MASK_FIELD_ID,
                    LandformVolcanicModuleV2.ISLAND_INDEX_FIELD_ID,
                    LandformVolcanicModuleV2.SUMMIT_RELIEF_FIELD_ID,
                    LandformVolcanicModuleV2.SUBMARINE_SADDLE_MASK_FIELD_ID,
                    LandformVolcanicModuleV2.RADIAL_DRAINAGE_FIELD_ID,
                    LandformVolcanicModuleV2.PROVISIONAL_SURFACE_FIELD_ID,
                    support, work, geometryChecksum);
        } catch (ArithmeticException exception) {
            throw new VolcanicGenerationException("v2.volcanic-budget", "volcanic arithmetic overflow", exception);
        }
    }

    private static List<VolcanicPlanV2.IslandMass> compileIslands(
            TerrainIntentV2.VolcanicArchipelagoParameters parameters,
            TerrainIntentV2.MultiPointGeometry geometry,
            WorldBlueprintV2.Bounds bounds
    ) {
        Map<String, TerrainIntentV2.Point2> points = new HashMap<>();
        geometry.points().forEach(point -> points.put(point.id(), point.point()));
        long maxX = Math.multiplyExact((long) bounds.width() - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) bounds.length() - 1L, TerrainIntentV2.FIXED_SCALE);
        List<VolcanicPlanV2.IslandMass> islands = new ArrayList<>();
        int index = 1;
        for (TerrainIntentV2.IslandSpec spec : parameters.islands()) {
            TerrainIntentV2.Point2 point = points.get(spec.pointId());
            if (point == null) {
                throw failure("v2.volcanic-unknown-point", "island references unknown MULTI_POINT id");
            }
            long x = Math.multiplyExact((long) point.xMillionths(), bounds.width() - 1L);
            long z = Math.multiplyExact((long) point.zMillionths(), bounds.length() - 1L);
            long radius = Math.multiplyExact((long) spec.radiusBlocks(), TerrainIntentV2.FIXED_SCALE);
            if (x - radius < 0 || x + radius > maxX || z - radius < 0 || z + radius > maxZ) {
                throw failure("v2.volcanic-bounds", "volcanic island disk leaves world bounds");
            }
            islands.add(new VolcanicPlanV2.IslandMass(
                    spec.pointId(), x, z, spec.radiusBlocks(), spec.summitHeightBlocksAboveSea(), index++));
        }
        return List.copyOf(islands);
    }

    private static void requireDryGaps(List<VolcanicPlanV2.IslandMass> islands) {
        for (int i = 0; i < islands.size(); i++) {
            for (int j = i + 1; j < islands.size(); j++) {
                VolcanicPlanV2.IslandMass a = islands.get(i);
                VolcanicPlanV2.IslandMass b = islands.get(j);
                long radii = Math.multiplyExact((long) a.radiusBlocks() + b.radiusBlocks(),
                        TerrainIntentV2.FIXED_SCALE);
                long gap = Math.subtractExact(hypot(
                        a.xMillionths() - b.xMillionths(), a.zMillionths() - b.zMillionths()), radii);
                if (gap < MINIMUM_DRY_GAP_MILLIONTHS) {
                    throw failure("v2.volcanic-merged-islands", "volcanic islands violate minimum dry-land gap");
                }
            }
        }
    }

    private static int requireDominance(List<VolcanicPlanV2.IslandMass> islands) {
        List<Integer> byArea = new ArrayList<>();
        for (int i = 0; i < islands.size(); i++) byArea.add(i);
        byArea.sort(Comparator.<Integer>comparingLong(i -> area(islands.get(i))).reversed()
                .thenComparing(i -> islands.get(i).pointId()));
        long largest = area(islands.get(byArea.get(0)));
        long second = area(islands.get(byArea.get(1)));
        if (Math.multiplyExact(largest, 2L) < Math.multiplyExact(second, 3L)) {
            throw failure("v2.volcanic-dominance", "largest volcanic island is not centrally dominant");
        }
        return byArea.get(0);
    }

    private static long area(VolcanicPlanV2.IslandMass island) {
        return Math.multiplyExact((long) island.radiusBlocks(), island.radiusBlocks());
    }

    private static List<VolcanicPlanV2.SubmarineSaddle> compileSaddles(
            List<VolcanicPlanV2.IslandMass> islands, int depth
    ) {
        List<VolcanicPlanV2.IslandMass> ordered = islands.stream()
                .sorted(Comparator.comparingLong(VolcanicPlanV2.IslandMass::xMillionths)
                        .thenComparingLong(VolcanicPlanV2.IslandMass::zMillionths)
                        .thenComparing(VolcanicPlanV2.IslandMass::pointId))
                .toList();
        List<VolcanicPlanV2.SubmarineSaddle> result = new ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            VolcanicPlanV2.IslandMass a = ordered.get(i - 1);
            VolcanicPlanV2.IslandMass b = ordered.get(i);
            result.add(new VolcanicPlanV2.SubmarineSaddle(
                    "saddle-" + a.pointId() + "-" + b.pointId(), a.pointId(), b.pointId(),
                    (a.xMillionths() + b.xMillionths()) / 2L,
                    (a.zMillionths() + b.zMillionths()) / 2L, depth));
        }
        return List.copyOf(result);
    }

    private static VolcanicPlanV2.CalderaPlanHook compileCaldera(
            TerrainIntentV2.Feature archipelago,
            TerrainIntentV2 intent,
            VolcanicPlanV2.IslandMass dominant,
            WorldBlueprintV2.Bounds bounds
    ) {
        String target = "feature:" + archipelago.id();
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.kind() == TerrainIntentV2.RelationKind.WITHIN
                        && relation.to().equals(target))
                .toList();
        if (relations.size() > 1) {
            throw failure("v2.volcanic-orphan-caldera", "archipelago has multiple HARD caldera children");
        }
        if (relations.isEmpty()) return null;
        TerrainIntentV2.Relation relation = relations.getFirst();
        TerrainIntentV2.Feature child = featureByEndpoint(intent, relation.from());
        if (child == null) {
            throw failure("v2.volcanic-orphan-caldera", "caldera relation references a missing feature");
        }
        if (child.kind() != TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA
                || !(child.parameters() instanceof TerrainIntentV2.VolcanicCalderaParameters parameters)
                || !(child.geometry() instanceof TerrainIntentV2.PointGeometry pointGeometry)) {
            throw failure("v2.volcanic-unknown-child", "WITHIN child is not a typed volcanic caldera");
        }
        long x = Math.multiplyExact((long) pointGeometry.point().xMillionths(), bounds.width() - 1L);
        long z = Math.multiplyExact((long) pointGeometry.point().zMillionths(), bounds.length() - 1L);
        long radius = Math.multiplyExact((long) dominant.radiusBlocks(), TerrainIntentV2.FIXED_SCALE);
        if (hypot(x - dominant.xMillionths(), z - dominant.zMillionths()) > radius) {
            throw failure("v2.volcanic-orphan-caldera", "caldera is outside the dominant island");
        }
        return new VolcanicPlanV2.CalderaPlanHook(
                child.id(), relation.id(), dominant.pointId(), parameters.rimRadiusBlocks(),
                parameters.rimReliefBlocks(), parameters.craterFloorDepthBlocks(), parameters.breachDirection());
    }

    private static VolcanicPlanV2.LavaPlanHook compileLava(
            TerrainIntentV2 intent, VolcanicPlanV2.CalderaPlanHook caldera
    ) {
        if (caldera == null) {
            boolean hasLava = intent.features().stream()
                    .anyMatch(feature -> feature.kind() == TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD);
            if (hasLava) throw failure("v2.volcanic-unknown-child", "lava hook requires a hooked caldera");
            return null;
        }
        String target = "feature:" + caldera.calderaFeatureId();
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD
                        && relation.kind() == TerrainIntentV2.RelationKind.ORIGINATES_AT
                        && relation.to().equals(target))
                .toList();
        if (relations.size() > 1) {
            throw failure("v2.volcanic-unknown-child", "caldera has multiple HARD lava children");
        }
        if (relations.isEmpty()) return null;
        TerrainIntentV2.Relation relation = relations.getFirst();
        TerrainIntentV2.Feature child = featureByEndpoint(intent, relation.from());
        if (child == null || child.kind() != TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD
                || !(child.parameters() instanceof TerrainIntentV2.LavaFlowParameters parameters)) {
            throw failure("v2.volcanic-unknown-child", "ORIGINATES_AT child is not a typed lava flow");
        }
        return new VolcanicPlanV2.LavaPlanHook(
                child.id(), relation.id(), caldera.calderaFeatureId(), midpoint(parameters.widthBlocks()),
                parameters.surfaceRoughnessMillionths());
    }

    private static TerrainIntentV2.Feature featureByEndpoint(TerrainIntentV2 intent, String endpoint) {
        if (!endpoint.startsWith("feature:")) return null;
        String id = endpoint.substring("feature:".length());
        return intent.features().stream().filter(feature -> feature.id().equals(id)).findFirst().orElse(null);
    }

    static long hypot(long x, long z) {
        return isqrt(Math.addExact(Math.multiplyExact(x, x), Math.multiplyExact(z, z)));
    }

    private static long isqrt(long value) {
        long low = 0L;
        long high = Math.min(value, 3_037_000_499L);
        while (low <= high) {
            long mid = (low + high) >>> 1;
            if (mid != 0 && mid > value / mid) high = mid - 1;
            else low = mid + 1;
        }
        return high;
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static VolcanicGenerationException failure(String ruleId, String message) {
        return new VolcanicGenerationException(ruleId, message);
    }
}
