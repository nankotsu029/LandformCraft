package com.github.nankotsu029.landformcraft.generator.v2.coast;

import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.ArrayList;
import java.util.List;

/** Descriptor compiler and field ownership contract for the V2-2 coastal foundation. */
public final class CoastalFoundationModuleV2 implements CoastalLandformModuleV2 {
    public static final String MODULE_ID = "v2.coast.foundation";
    public static final String MODULE_VERSION = "0.6.0-v2-2-06";
    public static final String STAGE_ID = "generate.coastal-raster";
    public static final String ACTUAL_LAND_WATER_FIELD_ID = "coastal.actual-land-water";
    public static final String COAST_SIDE_FIELD_ID = "coastal.coast-side";
    public static final String SIGNED_DISTANCE_FIELD_ID = "coastal.signed-distance";
    public static final String NORMAL_X_FIELD_ID = "coastal.normal-x";
    public static final String NORMAL_Z_FIELD_ID = "coastal.normal-z";
    public static final String NEARSHORE_PROFILE_FIELD_ID = "coastal.nearshore-profile";
    public static final String BEACH_LOCAL_WIDTH_FIELD_ID = "coastal.beach.local-width";
    public static final String BEACH_SURFACE_HEIGHT_FIELD_ID = "coastal.beach.surface-height";
    public static final String BEACH_BAND_FIELD_ID = "coastal.beach.band";
    public static final String BEACH_SEMANTIC_SAND_FIELD_ID = "coastal.beach.semantic-sand";
    public static final String HARBOR_REGION_FIELD_ID = "coastal.harbor-basin.region";
    public static final String HARBOR_WATER_FIELD_ID = "coastal.harbor-basin.water";
    public static final String HARBOR_DEPTH_FIELD_ID = "coastal.harbor-basin.water-depth";
    public static final String HARBOR_BOTTOM_HEIGHT_FIELD_ID = "coastal.harbor-basin.bottom-height";
    public static final String BREAKWATER_REGION_FIELD_ID = "coastal.breakwater.region";
    public static final String BREAKWATER_ARM_INDEX_FIELD_ID = "coastal.breakwater.arm-index";
    public static final String BREAKWATER_TOP_HEIGHT_FIELD_ID = "coastal.breakwater.top-height";
    public static final String BREAKWATER_BOTTOM_HEIGHT_FIELD_ID = "coastal.breakwater.bottom-height";
    public static final String CAPE_REGION_FIELD_ID = "coastal.cape.region";
    public static final String CAPE_SURFACE_HEIGHT_FIELD_ID = "coastal.cape.surface-height";
    public static final String CAPE_ROCK_EXPOSURE_FIELD_ID = "coastal.cape.rock-exposure";
    public static final String CAPE_DESCRIPTOR_INDEX_FIELD_ID = "coastal.cape.descriptor-index";
    public static final int REQUIRED_HALO_XZ = 64;

    private final ModuleDescriptorV2 descriptor;

    public CoastalFoundationModuleV2(String requiredFieldId) {
        descriptor = new ModuleDescriptorV2(
                MODULE_ID,
                MODULE_VERSION,
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                List.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE),
                List.of(requiredFieldId),
                List.of(
                        ACTUAL_LAND_WATER_FIELD_ID, COAST_SIDE_FIELD_ID, SIGNED_DISTANCE_FIELD_ID,
                        NORMAL_X_FIELD_ID, NORMAL_Z_FIELD_ID, NEARSHORE_PROFILE_FIELD_ID,
                        BEACH_LOCAL_WIDTH_FIELD_ID, BEACH_SURFACE_HEIGHT_FIELD_ID,
                        BEACH_BAND_FIELD_ID, BEACH_SEMANTIC_SAND_FIELD_ID,
                        HARBOR_REGION_FIELD_ID, HARBOR_WATER_FIELD_ID,
                        HARBOR_DEPTH_FIELD_ID, HARBOR_BOTTOM_HEIGHT_FIELD_ID,
                        BREAKWATER_REGION_FIELD_ID, BREAKWATER_ARM_INDEX_FIELD_ID,
                        BREAKWATER_TOP_HEIGHT_FIELD_ID, BREAKWATER_BOTTOM_HEIGHT_FIELD_ID,
                        CAPE_REGION_FIELD_ID, CAPE_SURFACE_HEIGHT_FIELD_ID,
                        CAPE_ROCK_EXPOSURE_FIELD_ID, CAPE_DESCRIPTOR_INDEX_FIELD_ID),
                List.of(
                        new ModuleDescriptorV2.FieldWrite(
                                ACTUAL_LAND_WATER_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                COAST_SIDE_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                SIGNED_DISTANCE_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                NORMAL_X_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                NORMAL_Z_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                NEARSHORE_PROFILE_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                BEACH_LOCAL_WIDTH_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                BEACH_SURFACE_HEIGHT_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                BEACH_BAND_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                BEACH_SEMANTIC_SAND_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                HARBOR_REGION_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                HARBOR_WATER_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                HARBOR_DEPTH_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                HARBOR_BOTTOM_HEIGHT_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                BREAKWATER_REGION_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                BREAKWATER_ARM_INDEX_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                BREAKWATER_TOP_HEIGHT_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                BREAKWATER_BOTTOM_HEIGHT_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                CAPE_REGION_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                CAPE_SURFACE_HEIGHT_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                CAPE_ROCK_EXPOSURE_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER),
                        new ModuleDescriptorV2.FieldWrite(
                                CAPE_DESCRIPTOR_INDEX_FIELD_ID, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)),
                STAGE_ID,
                REQUIRED_HALO_XZ,
                0,
                ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
                List.of(),
                List.of());
    }

    @Override
    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }

    @Override
    public CoastalFeaturePlanV2 compileFoundation(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            int width,
            int length,
            String geometryChecksum
    ) {
        if (!descriptor.supportedFeatureKinds().contains(feature.kind())) {
            throw new CoastalFoundationException(
                    "v2.coastal-unknown-kind", "coastal foundation does not support " + feature.kind());
        }
        validateRelations(feature, intent);
        CoastalFeaturePlanV2.BlockGeometry geometry = compileGeometry(
                feature.geometry(), width, length, geometryChecksum);

        CoastalFeaturePlanV2 plan = switch (feature.kind()) {
            case SANDY_BEACH -> beach(feature, geometry);
            case BREAKWATER_HARBOR -> breakwater(feature, geometry, intent);
            case HARBOR_BASIN -> basin(feature, geometry);
            case ROCKY_CAPE -> cape(feature, geometry);
            default -> throw new CoastalFoundationException(
                    "v2.coastal-unknown-kind", "coastal foundation does not support " + feature.kind());
        };
        if (plan.supportRadiusXZ() > descriptor.requiredHaloXZ()) {
            throw new CoastalFoundationException(
                    "v2.coastal-halo-exceeded",
                    "feature " + feature.id() + " requires halo " + plan.supportRadiusXZ()
                            + " but module declares " + descriptor.requiredHaloXZ());
        }
        return plan;
    }

    private static CoastalFeaturePlanV2 beach(
            TerrainIntentV2.Feature feature,
            CoastalFeaturePlanV2.BlockGeometry geometry
    ) {
        TerrainIntentV2.SandyBeachParameters parameters =
                (TerrainIntentV2.SandyBeachParameters) feature.parameters();
        int nearshoreSupport = parameters.nearshoreDepthBlocks().atDistance() + 1;
        int support = Math.max(
                parameters.widthBlocks().maximum(), nearshoreSupport);
        requireSupportedRadius(feature.id(), support);
        return new CoastalFeaturePlanV2(
                CoastalFeaturePlanV2.VERSION,
                feature.id(),
                feature.kind(),
                geometry,
                CoastalFeaturePlanV2.GeometryRole.COASTLINE,
                COAST_SIDE_FIELD_ID,
                parameters.landSide() == TerrainIntentV2.LandSide.LEFT
                        ? CoastalFeaturePlanV2.CoastSide.LAND_LEFT
                        : CoastalFeaturePlanV2.CoastSide.LAND_RIGHT,
                distance(CoastalFeaturePlanV2.DistanceSign.POSITIVE_ON_LAND_SIDE, support),
                new CoastalFeaturePlanV2.NearshoreProfileDescriptor(
                        NEARSHORE_PROFILE_FIELD_ID,
                        CoastalFeaturePlanV2.NearshoreProfileKind.LINEAR_DEPTH_TARGET,
                        parameters.nearshoreDepthBlocks().atDistance(),
                        parameters.nearshoreDepthBlocks().target()),
                support);
    }

    private static CoastalFeaturePlanV2 breakwater(
            TerrainIntentV2.Feature feature,
            CoastalFeaturePlanV2.BlockGeometry geometry,
            TerrainIntentV2 intent
    ) {
        TerrainIntentV2.BreakwaterHarborParameters parameters =
                (TerrainIntentV2.BreakwaterHarborParameters) feature.parameters();
        int innerDepth = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> (relation.kind() == TerrainIntentV2.RelationKind.ENCLOSES
                        && relation.from().equals("feature:" + feature.id()))
                        || (relation.kind() == TerrainIntentV2.RelationKind.ENCLOSED_BY
                        && relation.to().equals("feature:" + feature.id())))
                .map(relation -> relation.kind() == TerrainIntentV2.RelationKind.ENCLOSES
                        ? relation.to() : relation.from())
                .filter(endpoint -> endpoint.startsWith("feature:"))
                .map(endpoint -> endpoint.substring("feature:".length()))
                .flatMap(id -> intent.features().stream().filter(candidate -> candidate.id().equals(id)))
                .filter(candidate -> candidate.kind() == TerrainIntentV2.FeatureKind.HARBOR_BASIN)
                .map(candidate -> (TerrainIntentV2.HarborBasinParameters) candidate.parameters())
                .mapToInt(candidate -> candidate.waterDepthBlocks().maximum())
                .max().orElse(parameters.outerDepthBlocks());
        long maximumDepth = Math.max(parameters.outerDepthBlocks(), innerDepth);
        long halfCrestMillionths = (long) parameters.crestWidthBlocks() * TerrainIntentV2.FIXED_SCALE / 2L;
        long toeRunMillionths = Math.multiplyExact(
                maximumDepth, parameters.foundationSideSlopeRunPerRiseMillionths());
        int support = Math.toIntExact((halfCrestMillionths + toeRunMillionths
                + TerrainIntentV2.FIXED_SCALE - 1L) / TerrainIntentV2.FIXED_SCALE);
        requireSupportedRadius(feature.id(), support);
        return plan(feature, geometry, CoastalFeaturePlanV2.GeometryRole.STRUCTURE_CENTERLINES,
                CoastalFeaturePlanV2.CoastSide.NOT_APPLICABLE, CoastalFeaturePlanV2.DistanceSign.UNSIGNED,
                support);
    }

    private static CoastalFeaturePlanV2 basin(
            TerrainIntentV2.Feature feature,
            CoastalFeaturePlanV2.BlockGeometry geometry
    ) {
        TerrainIntentV2.HarborBasinParameters parameters =
                (TerrainIntentV2.HarborBasinParameters) feature.parameters();
        int support = Math.max(parameters.profileTransitionBlocks(), parameters.entranceCorridorLengthBlocks());
        requireSupportedRadius(feature.id(), support);
        return plan(feature, geometry, CoastalFeaturePlanV2.GeometryRole.WATER_REGION,
                CoastalFeaturePlanV2.CoastSide.INTERIOR_WATER, CoastalFeaturePlanV2.DistanceSign.NEGATIVE_INSIDE,
                support);
    }

    private static CoastalFeaturePlanV2 cape(
            TerrainIntentV2.Feature feature,
            CoastalFeaturePlanV2.BlockGeometry geometry
    ) {
        TerrainIntentV2.RockyCapeParameters parameters =
                (TerrainIntentV2.RockyCapeParameters) feature.parameters();
        int support = Math.max(parameters.cliffBandWidthBlocks().maximum(), Math.max(
                parameters.channelLengthBlocks().maximum()
                        + (parameters.channelWidthBlocks().maximum() + 1) / 2,
                parameters.seaStackOffshoreDistanceBlocks().maximum()
                        + parameters.seaStackRadiusBlocks().maximum()));
        requireSupportedRadius(feature.id(), support);
        return plan(feature, geometry, CoastalFeaturePlanV2.GeometryRole.LAND_REGION,
                CoastalFeaturePlanV2.CoastSide.INTERIOR_LAND, CoastalFeaturePlanV2.DistanceSign.POSITIVE_INSIDE,
                support);
    }

    private static CoastalFeaturePlanV2 plan(
            TerrainIntentV2.Feature feature,
            CoastalFeaturePlanV2.BlockGeometry geometry,
            CoastalFeaturePlanV2.GeometryRole role,
            CoastalFeaturePlanV2.CoastSide side,
            CoastalFeaturePlanV2.DistanceSign sign,
            int support
    ) {
        return new CoastalFeaturePlanV2(
                CoastalFeaturePlanV2.VERSION, feature.id(), feature.kind(), geometry, role,
                COAST_SIDE_FIELD_ID, side,
                distance(sign, support),
                new CoastalFeaturePlanV2.NearshoreProfileDescriptor(
                        NEARSHORE_PROFILE_FIELD_ID, CoastalFeaturePlanV2.NearshoreProfileKind.NONE, 0, 0),
                support);
    }

    private static CoastalFeaturePlanV2.SignedDistanceDescriptor distance(
            CoastalFeaturePlanV2.DistanceSign sign,
            int support
    ) {
        return new CoastalFeaturePlanV2.SignedDistanceDescriptor(
                SIGNED_DISTANCE_FIELD_ID, sign, Math.max(1, support));
    }

    private static void requireSupportedRadius(String featureId, int support) {
        if (support > REQUIRED_HALO_XZ) {
            throw new CoastalFoundationException(
                    "v2.coastal-halo-exceeded",
                    "feature " + featureId + " requires halo " + support
                            + " but module declares " + REQUIRED_HALO_XZ);
        }
    }

    private static CoastalFeaturePlanV2.BlockGeometry compileGeometry(
            TerrainIntentV2.Geometry geometry,
            int width,
            int length,
            String geometryChecksum
    ) {
        List<CoastalFeaturePlanV2.BlockPath> paths = new ArrayList<>();
        List<CoastalFeaturePlanV2.BlockRing> rings = new ArrayList<>();
        if (geometry instanceof TerrainIntentV2.SplineGeometry spline) {
            rejectSelfIntersection(spline.points());
            paths.add(new CoastalFeaturePlanV2.BlockPath(
                    "coastline", spline.interpolation(), points(spline.points(), width, length)));
        } else if (geometry instanceof TerrainIntentV2.MultiSplineGeometry multi) {
            for (TerrainIntentV2.NamedPath path : multi.paths()) {
                rejectSelfIntersection(path.points());
                paths.add(new CoastalFeaturePlanV2.BlockPath(
                        path.id(), multi.interpolation(), points(path.points(), width, length)));
            }
        } else if (geometry instanceof TerrainIntentV2.PolygonGeometry polygon) {
            for (int index = 0; index < polygon.rings().size(); index++) {
                rings.add(new CoastalFeaturePlanV2.BlockRing(
                        index, points(polygon.rings().get(index), width, length)));
            }
        } else {
            throw new CoastalFoundationException(
                    "v2.coastal-geometry", "unsupported coastal geometry type " + geometry.type());
        }
        return new CoastalFeaturePlanV2.BlockGeometry(
                CoastalFeaturePlanV2.BlockGeometry.VERSION, geometry.type(), paths, rings, geometryChecksum);
    }

    private static List<CoastalFeaturePlanV2.BlockPoint> points(
            List<TerrainIntentV2.Point2> points,
            int width,
            int length
    ) {
        return points.stream().map(point -> new CoastalFeaturePlanV2.BlockPoint(
                Math.multiplyExact((long) point.xMillionths(), width - 1L),
                Math.multiplyExact((long) point.zMillionths(), length - 1L))).toList();
    }

    private static void validateRelations(TerrainIntentV2.Feature feature, TerrainIntentV2 intent) {
        String endpoint = "feature:" + feature.id();
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            if (!relation.from().equals(endpoint) && !relation.to().equals(endpoint)) continue;
            boolean allowed = switch (relation.kind()) {
                case ADJACENT_TO, OVERLAPS, EXCLUDES -> true;
                case ENCLOSES -> isKind(intent, relation.from(), TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR)
                        && isKind(intent, relation.to(), TerrainIntentV2.FeatureKind.HARBOR_BASIN);
                case ENCLOSED_BY -> isKind(intent, relation.from(), TerrainIntentV2.FeatureKind.HARBOR_BASIN)
                        && isKind(intent, relation.to(), TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR);
                default -> false;
            };
            if (!allowed) {
                throw new CoastalFoundationException(
                        "v2.coastal-relation", "unsupported coastal relation " + relation.id());
            }
        }
    }

    private static boolean isKind(
            TerrainIntentV2 intent,
            String endpoint,
            TerrainIntentV2.FeatureKind expected
    ) {
        if (!endpoint.startsWith("feature:")) return false;
        String id = endpoint.substring("feature:".length());
        return intent.features().stream().anyMatch(feature -> feature.id().equals(id) && feature.kind() == expected);
    }

    private static void rejectSelfIntersection(List<TerrainIntentV2.Point2> points) {
        for (int first = 0; first < points.size() - 1; first++) {
            for (int second = first + 2; second < points.size() - 1; second++) {
                if (segmentsIntersect(
                        points.get(first), points.get(first + 1), points.get(second), points.get(second + 1))) {
                    throw new CoastalFoundationException(
                            "v2.coastal-self-intersection", "coastal spline control line self-intersects");
                }
            }
        }
    }

    private static boolean segmentsIntersect(
            TerrainIntentV2.Point2 a,
            TerrainIntentV2.Point2 b,
            TerrainIntentV2.Point2 c,
            TerrainIntentV2.Point2 d
    ) {
        long abC = cross(a, b, c);
        long abD = cross(a, b, d);
        long cdA = cross(c, d, a);
        long cdB = cross(c, d, b);
        return Long.signum(abC) != Long.signum(abD) && Long.signum(cdA) != Long.signum(cdB);
    }

    private static long cross(TerrainIntentV2.Point2 a, TerrainIntentV2.Point2 b, TerrainIntentV2.Point2 c) {
        return (long) (b.xMillionths() - a.xMillionths()) * (c.zMillionths() - a.zMillionths())
                - (long) (b.zMillionths() - a.zMillionths()) * (c.xMillionths() - a.xMillionths());
    }
}
