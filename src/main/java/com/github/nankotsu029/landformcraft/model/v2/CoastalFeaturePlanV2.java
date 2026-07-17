package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Canonical V2-2 coastal foundation plan. Coordinates are release-local block coordinates stored
 * in millionths; this descriptor contains no raster payload and does not itself generate terrain.
 */
public record CoastalFeaturePlanV2(
        int planVersion,
        String featureId,
        TerrainIntentV2.FeatureKind kind,
        BlockGeometry geometry,
        GeometryRole geometryRole,
        String coastSideFieldId,
        CoastSide coastSide,
        SignedDistanceDescriptor signedDistance,
        NearshoreProfileDescriptor nearshoreProfile,
        int supportRadiusXZ
) {
    public static final int VERSION = 1;

    public CoastalFeaturePlanV2 {
        if (planVersion != VERSION) throw new IllegalArgumentException("coastal planVersion must be exactly 1");
        featureId = V2Validation.slug(featureId, "featureId");
        Objects.requireNonNull(kind, "kind");
        if (!isFoundationKind(kind)) {
            throw new IllegalArgumentException("feature kind is not part of the coastal foundation: " + kind);
        }
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(geometryRole, "geometryRole");
        coastSideFieldId = V2Validation.qualifiedId(coastSideFieldId, "coastSideFieldId");
        Objects.requireNonNull(coastSide, "coastSide");
        Objects.requireNonNull(signedDistance, "signedDistance");
        Objects.requireNonNull(nearshoreProfile, "nearshoreProfile");
        if (supportRadiusXZ < 0 || supportRadiusXZ > 1_000) {
            throw new IllegalArgumentException("coastal support radius outside 0..1000");
        }
        requireKindContract(kind, geometry, geometryRole, coastSide, signedDistance, nearshoreProfile);
    }

    public static boolean isFoundationKind(TerrainIntentV2.FeatureKind kind) {
        return kind == TerrainIntentV2.FeatureKind.SANDY_BEACH
                || kind == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                || kind == TerrainIntentV2.FeatureKind.HARBOR_BASIN
                || kind == TerrainIntentV2.FeatureKind.ROCKY_CAPE;
    }

    private static void requireKindContract(
            TerrainIntentV2.FeatureKind kind,
            BlockGeometry geometry,
            GeometryRole role,
            CoastSide side,
            SignedDistanceDescriptor distance,
            NearshoreProfileDescriptor profile
    ) {
        boolean valid = switch (kind) {
            case SANDY_BEACH -> geometry.geometryType() == TerrainIntentV2.GeometryType.SPLINE
                    && role == GeometryRole.COASTLINE
                    && (side == CoastSide.LAND_LEFT || side == CoastSide.LAND_RIGHT)
                    && distance.sign() == DistanceSign.POSITIVE_ON_LAND_SIDE
                    && profile.kind() == NearshoreProfileKind.LINEAR_DEPTH_TARGET;
            case BREAKWATER_HARBOR -> geometry.geometryType() == TerrainIntentV2.GeometryType.MULTI_SPLINE
                    && role == GeometryRole.STRUCTURE_CENTERLINES
                    && side == CoastSide.NOT_APPLICABLE
                    && distance.sign() == DistanceSign.UNSIGNED
                    && profile.kind() == NearshoreProfileKind.NONE;
            case HARBOR_BASIN -> geometry.geometryType() == TerrainIntentV2.GeometryType.POLYGON
                    && role == GeometryRole.WATER_REGION
                    && side == CoastSide.INTERIOR_WATER
                    && distance.sign() == DistanceSign.NEGATIVE_INSIDE
                    && profile.kind() == NearshoreProfileKind.NONE;
            case ROCKY_CAPE -> geometry.geometryType() == TerrainIntentV2.GeometryType.POLYGON
                    && role == GeometryRole.LAND_REGION
                    && side == CoastSide.INTERIOR_LAND
                    && distance.sign() == DistanceSign.POSITIVE_INSIDE
                    && profile.kind() == NearshoreProfileKind.NONE;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("coastal plan contract does not match " + kind);
    }

    public enum GeometryRole { COASTLINE, STRUCTURE_CENTERLINES, WATER_REGION, LAND_REGION }
    public enum CoastSide { LAND_LEFT, LAND_RIGHT, INTERIOR_WATER, INTERIOR_LAND, NOT_APPLICABLE }
    public enum DistanceSign { POSITIVE_ON_LAND_SIDE, UNSIGNED, NEGATIVE_INSIDE, POSITIVE_INSIDE }
    public enum NearshoreProfileKind { NONE, LINEAR_DEPTH_TARGET }

    public record BlockGeometry(
            int geometryVersion,
            TerrainIntentV2.GeometryType geometryType,
            List<BlockPath> paths,
            List<BlockRing> rings,
            String sourceGeometryChecksum
    ) {
        public static final int VERSION = 1;

        public BlockGeometry {
            if (geometryVersion != VERSION) {
                throw new IllegalArgumentException("coastal geometryVersion must be exactly 1");
            }
            Objects.requireNonNull(geometryType, "geometryType");
            paths = V2Validation.sorted(paths, "coastal paths", 128, Comparator.comparing(BlockPath::pathId));
            rings = V2Validation.sorted(rings, "coastal rings", 64, Comparator.comparingInt(BlockRing::ringIndex));
            sourceGeometryChecksum = V2Validation.checksum(sourceGeometryChecksum, "sourceGeometryChecksum");
            boolean valid = switch (geometryType) {
                case SPLINE -> paths.size() == 1 && rings.isEmpty();
                case MULTI_SPLINE -> !paths.isEmpty() && rings.isEmpty();
                case POLYGON -> paths.isEmpty() && !rings.isEmpty();
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("block geometry payload does not match geometryType");
        }
    }

    public record BlockPoint(long xMillionths, long zMillionths) {
        public BlockPoint {
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("block coordinates must be non-negative");
            }
        }
    }

    public record BlockPath(
            String pathId,
            TerrainIntentV2.Interpolation interpolation,
            List<BlockPoint> points
    ) {
        public BlockPath {
            pathId = V2Validation.slug(pathId, "pathId");
            Objects.requireNonNull(interpolation, "interpolation");
            points = V2Validation.immutable(points, "block path points", 1_024);
            if (points.size() < 2) throw new IllegalArgumentException("block path requires at least two points");
            for (int index = 1; index < points.size(); index++) {
                if (points.get(index - 1).equals(points.get(index))) {
                    throw new IllegalArgumentException("block path has a zero-length segment");
                }
            }
        }
    }

    public record BlockRing(int ringIndex, List<BlockPoint> points) {
        public BlockRing {
            if (ringIndex < 0 || ringIndex >= 64) throw new IllegalArgumentException("invalid ringIndex");
            points = V2Validation.immutable(points, "block ring points", 2_048);
            if (points.size() < 4 || !points.getFirst().equals(points.getLast())) {
                throw new IllegalArgumentException("block ring must be closed");
            }
        }
    }

    public record SignedDistanceDescriptor(String fieldId, DistanceSign sign, int maximumDistanceBlocks) {
        public SignedDistanceDescriptor {
            fieldId = V2Validation.qualifiedId(fieldId, "signed distance fieldId");
            Objects.requireNonNull(sign, "sign");
            if (maximumDistanceBlocks < 1 || maximumDistanceBlocks > 1_000) {
                throw new IllegalArgumentException("maximumDistanceBlocks outside 1..1000");
            }
        }
    }

    public record NearshoreProfileDescriptor(
            String fieldId,
            NearshoreProfileKind kind,
            int distanceBlocks,
            int targetDepthBlocks
    ) {
        public NearshoreProfileDescriptor {
            fieldId = V2Validation.qualifiedId(fieldId, "nearshore fieldId");
            Objects.requireNonNull(kind, "kind");
            if (distanceBlocks < 0 || targetDepthBlocks < 0) {
                throw new IllegalArgumentException("nearshore profile values must be non-negative");
            }
            if (kind == NearshoreProfileKind.NONE && (distanceBlocks != 0 || targetDepthBlocks != 0)) {
                throw new IllegalArgumentException("NONE nearshore profile must use zero values");
            }
            if (kind == NearshoreProfileKind.LINEAR_DEPTH_TARGET && distanceBlocks < 1) {
                throw new IllegalArgumentException("nearshore profile distance must be positive");
            }
        }
    }
}
