package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BarrierIslandPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SingleIslandPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Compiles the {@code BARRIER_ISLAND} COMPOSITE_PRESET from SINGLE_ISLAND + LAGOON child features.
 * Does not invent a FeatureKind or dedicated world generator.
 */
public final class BarrierIslandPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public BarrierIslandPlanV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            SingleIslandPlanV2 islandPlan
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(islandPlan, "islandPlan");

        TerrainIntentV2.Feature island = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SINGLE_ISLAND)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException(
                        "v2.barrier-island-missing-island", "BARRIER_ISLAND requires a SINGLE_ISLAND feature"));

        TerrainIntentV2.Feature lagoon = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.LAGOON)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException(
                        "v2.barrier-island-missing-lagoon", "BARRIER_ISLAND requires a LAGOON feature"));

        boolean marineConnectivityOk = resolveShoreParallelRelation(intent, island.id(), lagoon.id());

        String islandGeometryChecksum = codec.geometryChecksum(island.geometry());
        String lagoonGeometryChecksum = codec.geometryChecksum(lagoon.geometry());
        int azimuth = computeShoreParallelAzimuth(island, lagoon, bounds);
        int ridgeHalfWidth = resolveRidgeHalfWidth(island);
        int support = Math.min(64, Math.max(islandPlan.supportRadiusXZ(), 4));
        long work = Math.max(1L, islandPlan.estimatedRasterWorkUnits());
        String geometryChecksum = bindingChecksum(
                islandGeometryChecksum, lagoonGeometryChecksum, islandPlan.canonicalChecksum(), azimuth);

        return new BarrierIslandPlanV2(
                BarrierIslandPlanV2.VERSION,
                "barrier-island." + island.id(),
                BarrierIslandPlanV2.CONTRACT_VERSION,
                island.id(),
                islandGeometryChecksum,
                lagoon.id(),
                lagoonGeometryChecksum,
                azimuth,
                ridgeHalfWidth,
                marineConnectivityOk,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    static boolean resolveShoreParallelRelation(
            TerrainIntentV2 intent,
            String islandId,
            String lagoonId
    ) {
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.FLANKS
                        || relation.kind() == TerrainIntentV2.RelationKind.OVERLAPS)
                .filter(relation -> references(relation.from(), islandId, lagoonId)
                        && references(relation.to(), islandId, lagoonId))
                .toList();
        if (relations.isEmpty()) {
            throw new FoundationSliceException("v2.barrier-island-missing-relation",
                    "BARRIER_ISLAND requires HARD ADJACENT_TO, FLANKS, or OVERLAPS between island and lagoon");
        }
        return true;
    }

    static int computeShoreParallelAzimuth(
            TerrainIntentV2.Feature island,
            TerrainIntentV2.Feature lagoon,
            WorldBlueprintV2.Bounds bounds
    ) {
        long[] islandCenter = geometryCentroid(island.geometry(), bounds);
        long[] lagoonCenter = geometryCentroid(lagoon.geometry(), bounds);
        long dx = lagoonCenter[0] - islandCenter[0];
        long dz = lagoonCenter[1] - islandCenter[1];
        if (dx == 0L && dz == 0L) {
            throw new FoundationSliceException("v2.barrier-island-degenerate",
                    "island and lagoon centroids coincide");
        }
        double radians = Math.atan2(dz, dx);
        int degrees = (int) Math.floor(Math.toDegrees(radians));
        if (degrees < 0) {
            degrees += 360;
        }
        return degrees;
    }

    private static int resolveRidgeHalfWidth(TerrainIntentV2.Feature island) {
        if (island.parameters() instanceof TerrainIntentV2.SingleIslandParameters parameters) {
            int radius = midpoint(parameters.radiusBlocks());
            int shore = midpoint(parameters.shoreBandWidthBlocks());
            return Math.max(1, Math.min(128, radius / 2 + shore));
        }
        return 8;
    }

    static long[] geometryCentroid(TerrainIntentV2.Geometry geometry, WorldBlueprintV2.Bounds bounds) {
        return switch (geometry) {
            case TerrainIntentV2.PointGeometry point -> new long[]{
                    scaleCoordinate(point.point().xMillionths(), bounds.width()),
                    scaleCoordinate(point.point().zMillionths(), bounds.length())};
            case TerrainIntentV2.PolygonGeometry polygon -> polygonCentroid(polygon, bounds);
            case TerrainIntentV2.SplineGeometry spline -> splineCentroid(spline, bounds);
            default -> throw new FoundationSliceException("v2.barrier-island-geometry",
                    "unsupported geometry for centroid: " + geometry.type());
        };
    }

    private static long[] polygonCentroid(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<TerrainIntentV2.Point2> ring = polygon.rings().getFirst();
        long sumX = 0L;
        long sumZ = 0L;
        for (TerrainIntentV2.Point2 point : ring) {
            sumX = Math.addExact(sumX, scaleCoordinate(point.xMillionths(), bounds.width()));
            sumZ = Math.addExact(sumZ, scaleCoordinate(point.zMillionths(), bounds.length()));
        }
        return new long[]{sumX / ring.size(), sumZ / ring.size()};
    }

    private static long[] splineCentroid(
            TerrainIntentV2.SplineGeometry spline,
            WorldBlueprintV2.Bounds bounds
    ) {
        long sumX = 0L;
        long sumZ = 0L;
        for (TerrainIntentV2.Point2 point : spline.points()) {
            sumX = Math.addExact(sumX, scaleCoordinate(point.xMillionths(), bounds.width()));
            sumZ = Math.addExact(sumZ, scaleCoordinate(point.zMillionths(), bounds.length()));
        }
        return new long[]{sumX / spline.points().size(), sumZ / spline.points().size()};
    }

    private static long scaleCoordinate(int normalizedMillionths, int span) {
        return Math.multiplyExact((long) normalizedMillionths, span - 1L);
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static boolean references(String endpoint, String firstId, String secondId) {
        String featureRef = endpoint.startsWith("feature:") ? endpoint.substring("feature:".length()) : "";
        return featureRef.equals(firstId) || featureRef.equals(secondId);
    }

    static String bindingChecksum(String islandGeometry, String lagoonGeometry, String islandPlanChecksum, int azimuth) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(islandGeometry.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(lagoonGeometry.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(islandPlanChecksum.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(Integer.toString(azimuth).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
