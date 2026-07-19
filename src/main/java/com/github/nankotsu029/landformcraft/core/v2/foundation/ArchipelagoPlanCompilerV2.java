package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.generator.v2.foundation.LandformArchipelagoModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.island.IslandFixedMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.ArchipelagoPlanV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Integer-only compiler for one ARCHIPELAGO multi-point feature. */
public final class ArchipelagoPlanCompilerV2 {
    public ArchipelagoPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.ARCHIPELAGO) {
            throw failure("v2.archipelago-kind", "feature kind is not ARCHIPELAGO");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.MultiPointGeometry multiPoint)) {
            throw failure("v2.archipelago-geometry", "archipelago requires MULTI_POINT geometry");
        }
        TerrainIntentV2.ArchipelagoParameters parameters =
                (TerrainIntentV2.ArchipelagoParameters) feature.parameters();
        try {
            Map<String, TerrainIntentV2.NamedPoint> pointsById = new HashMap<>();
            for (TerrainIntentV2.NamedPoint named : multiPoint.points()) {
                pointsById.put(named.id(), named);
            }
            List<ArchipelagoPlanV2.IslandMass> islands = new ArrayList<>();
            for (TerrainIntentV2.IslandSpec spec : parameters.islands()) {
                TerrainIntentV2.NamedPoint named = pointsById.get(spec.pointId());
                if (named == null) {
                    throw failure("v2.archipelago-geometry",
                            "archipelago island pointId missing from MULTI_POINT: " + spec.pointId());
                }
                islands.add(new ArchipelagoPlanV2.IslandMass(
                        spec.pointId(),
                        scaleCoordinate(named.point().xMillionths(), bounds.width()),
                        scaleCoordinate(named.point().zMillionths(), bounds.length()),
                        spec.radiusBlocks(),
                        spec.summitHeightBlocksAboveSea()));
            }
            islands = islands.stream()
                    .sorted(Comparator.comparing(ArchipelagoPlanV2.IslandMass::pointId))
                    .toList();
            rejectOverlapsAndGap(islands, parameters.minDryLandGapBlocks());

            int saddleDepth = midpoint(parameters.submarineSaddleDepthBlocks());
            List<ArchipelagoPlanV2.Saddle> saddles = new ArrayList<>();
            for (int i = 0; i < islands.size(); i++) {
                for (int j = i + 1; j < islands.size(); j++) {
                    ArchipelagoPlanV2.IslandMass a = islands.get(i);
                    ArchipelagoPlanV2.IslandMass b = islands.get(j);
                    String from = a.pointId().compareTo(b.pointId()) < 0 ? a.pointId() : b.pointId();
                    String to = a.pointId().compareTo(b.pointId()) < 0 ? b.pointId() : a.pointId();
                    saddles.add(new ArchipelagoPlanV2.Saddle(from, to, saddleDepth));
                }
            }
            int dominantIndex = 0;
            for (int i = 1; i < islands.size(); i++) {
                ArchipelagoPlanV2.IslandMass candidate = islands.get(i);
                ArchipelagoPlanV2.IslandMass dominant = islands.get(dominantIndex);
                if (candidate.summitHeightBlocksAboveSea() > dominant.summitHeightBlocksAboveSea()
                        || (candidate.summitHeightBlocksAboveSea() == dominant.summitHeightBlocksAboveSea()
                        && candidate.radiusBlocks() > dominant.radiusBlocks())
                        || (candidate.summitHeightBlocksAboveSea() == dominant.summitHeightBlocksAboveSea()
                        && candidate.radiusBlocks() == dominant.radiusBlocks()
                        && candidate.pointId().compareTo(dominant.pointId()) < 0)) {
                    dominantIndex = i;
                }
            }
            int maxRadius = islands.stream().mapToInt(ArchipelagoPlanV2.IslandMass::radiusBlocks).max().orElse(8);
            int support = Math.min(64, maxRadius);
            if (support > LandformArchipelagoModuleV2.MAXIMUM_SUPPORT_RADIUS_XZ) {
                throw failure("v2.archipelago-budget", "archipelago support radius exceeds trusted halo");
            }
            long work = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), bounds.length()), 5L);
            if (work > ArchipelagoPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
                throw failure("v2.archipelago-budget", "archipelago profile/raster budget exceeded");
            }
            return new ArchipelagoPlanV2(
                    ArchipelagoPlanV2.VERSION,
                    feature.id(),
                    islands,
                    saddles,
                    dominantIndex,
                    parameters.minDryLandGapBlocks(),
                    saddleDepth,
                    bounds.minY(),
                    bounds.maxY(),
                    bounds.waterLevel(),
                    bounds.width(),
                    bounds.length(),
                    ArchipelagoPlanV2.MASS_MASK_FIELD_ID,
                    ArchipelagoPlanV2.SADDLE_FIELD_ID,
                    ArchipelagoPlanV2.DOMINANCE_FIELD_ID,
                    ArchipelagoPlanV2.GAP_FIELD_ID,
                    ArchipelagoPlanV2.SOLID_OWNERSHIP_FIELD_ID,
                    support,
                    work,
                    geometryChecksum,
                    "0".repeat(64));
        } catch (ArithmeticException exception) {
            throw new FoundationSliceException(
                    "v2.archipelago-budget", "archipelago arithmetic overflow", exception);
        }
    }

    private static void rejectOverlapsAndGap(
            List<ArchipelagoPlanV2.IslandMass> islands,
            int minDryLandGapBlocks
    ) {
        long minGap = Math.multiplyExact((long) minDryLandGapBlocks, TerrainIntentV2.FIXED_SCALE);
        for (int i = 0; i < islands.size(); i++) {
            for (int j = i + 1; j < islands.size(); j++) {
                ArchipelagoPlanV2.IslandMass a = islands.get(i);
                ArchipelagoPlanV2.IslandMass b = islands.get(j);
                long distance = IslandFixedMathV2.hypot(
                        a.centerXMillionths() - b.centerXMillionths(),
                        a.centerZMillionths() - b.centerZMillionths());
                long radii = Math.multiplyExact(
                        (long) a.radiusBlocks() + b.radiusBlocks(), TerrainIntentV2.FIXED_SCALE);
                if (distance < radii) {
                    throw failure("v2.archipelago-overlap",
                            "archipelago islands overlap: " + a.pointId() + " / " + b.pointId());
                }
                long dryGap = distance - radii;
                if (dryGap < minGap) {
                    throw failure("v2.archipelago-dry-gap",
                            "archipelago dry-land gap below minDryLandGapBlocks");
                }
            }
        }
    }

    private static long scaleCoordinate(int normalizedMillionths, int span) {
        return Math.multiplyExact((long) normalizedMillionths, span - 1L);
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
