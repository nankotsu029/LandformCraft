package com.github.nankotsu029.landformcraft.model.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Version-2 semantic intent. It contains no provider, filesystem, or Minecraft runtime types. */
public record TerrainIntentV2(
        int intentVersion,
        String intentId,
        String theme,
        CoordinateSystem coordinateSystem,
        List<Feature> features,
        List<Relation> relations,
        List<Constraint> constraints,
        EnvironmentDescriptor environment,
        List<ConstraintMapBinding> mapReferences,
        List<StructureRequest> structures,
        Provenance provenance
) {
    public static final int VERSION = 2;
    public static final int FIXED_SCALE = 1_000_000;
    private static final Pattern ENDPOINT = Pattern.compile("(?:feature:[a-z0-9][a-z0-9._-]{0,63}|boundary:(?:NORTH|EAST|SOUTH|WEST))");
    private static final Pattern SOURCE_REFERENCE = Pattern.compile("constraint-source:[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern ARTIFACT_REFERENCE = Pattern.compile("constraint:[a-z0-9][a-z0-9._-]{0,63}:sha256-[0-9a-f]{64}");

    public TerrainIntentV2 {
        if (intentVersion != VERSION) {
            throw new IllegalArgumentException("intentVersion must be exactly 2");
        }
        intentId = V2Validation.slug(intentId, "intentId");
        theme = V2Validation.nonBlank(theme, "theme", 1_000);
        Objects.requireNonNull(coordinateSystem, "coordinateSystem");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(provenance, "provenance");
        features = V2Validation.sorted(features, "features", 256, Comparator.comparing(Feature::id));
        relations = V2Validation.sorted(relations, "relations", 512, Comparator.comparing(Relation::id));
        constraints = V2Validation.sorted(constraints, "constraints", 512, Comparator.comparing(Constraint::id));
        mapReferences = V2Validation.sorted(
                mapReferences, "mapReferences", 32, Comparator.comparing(ConstraintMapBinding::id));
        structures = V2Validation.sorted(structures, "structures", 64, Comparator.comparing(StructureRequest::id));
        validateIdsAndReferences(features, relations, constraints, mapReferences, structures);
        validateRelationCycles(relations);
        validateHardConflicts(constraints);
    }

    private static void validateIdsAndReferences(
            List<Feature> features,
            List<Relation> relations,
            List<Constraint> constraints,
            List<ConstraintMapBinding> mapReferences,
            List<StructureRequest> structures
    ) {
        Set<String> featureIds = uniqueIds(features.stream().map(Feature::id).toList(), "feature");
        uniqueIds(relations.stream().map(Relation::id).toList(), "relation");
        uniqueIds(constraints.stream().map(Constraint::id).toList(), "constraint");
        uniqueIds(mapReferences.stream().map(ConstraintMapBinding::id).toList(), "map reference");
        uniqueIds(mapReferences.stream().map(ConstraintMapBinding::sourceId).toList(), "map source binding");
        uniqueIds(mapReferences.stream().map(ConstraintMapBinding::artifactId).toList(), "map artifact binding");
        uniqueIds(structures.stream().map(StructureRequest::id).toList(), "structure");

        Set<String> subgeometryIds = new HashSet<>();
        for (Feature feature : features) {
            feature.geometry().collectStableIds(subgeometryIds);
        }
        for (Feature feature : features) {
            if (feature.parameters() instanceof BreakwaterHarborParameters breakwater) {
                requireKnownSubgeometryIds(breakwater.opening().betweenEndpointIds(), subgeometryIds, feature.id());
            } else if (feature.parameters() instanceof HarborBasinParameters basin) {
                requireKnownSubgeometryIds(basin.entranceEndpointIds(), subgeometryIds, feature.id());
            }
        }
        for (Relation relation : relations) {
            validateEndpoint(relation.from(), featureIds, relation.id());
            validateEndpoint(relation.to(), featureIds, relation.id());
            validateRelationEndpointTypes(relation, features);
        }
        for (Constraint constraint : constraints) {
            validateSubject(constraint.subject(), featureIds, constraint.id());
        }
        for (StructureRequest structure : structures) {
            if (!featureIds.contains(structure.preferredFeatureId())) {
                throw new IllegalArgumentException("structure references unknown feature: "
                        + structure.preferredFeatureId());
            }
        }
    }

    private static Set<String> uniqueIds(List<String> ids, String kind) {
        Set<String> result = new HashSet<>();
        for (String id : ids) {
            if (!result.add(id)) {
                throw new IllegalArgumentException("duplicate " + kind + " id: " + id);
            }
        }
        return Set.copyOf(result);
    }

    private static void validateEndpoint(String endpoint, Set<String> featureIds, String relationId) {
        if (!ENDPOINT.matcher(endpoint).matches()) {
            throw new IllegalArgumentException("invalid endpoint grammar in relation " + relationId + ": " + endpoint);
        }
        if (endpoint.startsWith("feature:") && !featureIds.contains(endpoint.substring("feature:".length()))) {
            throw new IllegalArgumentException("unknown relation endpoint in " + relationId + ": " + endpoint);
        }
    }

    private static void validateRelationEndpointTypes(Relation relation, List<Feature> features) {
        if (relation.kind() == RelationKind.EMPTIES_INTO
                && relation.from().startsWith("feature:")
                && relation.to().startsWith("feature:")) {
            String fromId = relation.from().substring("feature:".length());
            String toId = relation.to().substring("feature:".length());
            FeatureKind fromKind = featureKindOrNull(features, fromId);
            FeatureKind toKind = featureKindOrNull(features, toId);
            if (fromKind == FeatureKind.SPRING && toKind == FeatureKind.RIVER) {
                return;
            }
        }
        relation.validateEndpointTypes();
    }

    private static FeatureKind featureKindOrNull(List<Feature> features, String featureId) {
        return features.stream()
                .filter(feature -> feature.id().equals(featureId))
                .map(Feature::kind)
                .findFirst()
                .orElse(null);
    }

    private static void validateSubject(String subject, Set<String> featureIds, String constraintId) {
        if ("world".equals(subject)) {
            return;
        }
        if (!subject.startsWith("feature:") || !featureIds.contains(subject.substring("feature:".length()))) {
            throw new IllegalArgumentException("unknown constraint subject in " + constraintId + ": " + subject);
        }
    }

    private static void validateRelationCycles(List<Relation> relations) {
        Map<String, List<String>> graph = new HashMap<>();
        for (Relation relation : relations) {
            if (!relation.kind().requiresDag() || !relation.from().startsWith("feature:")
                    || !relation.to().startsWith("feature:")) {
                continue;
            }
            graph.computeIfAbsent(relation.from(), ignored -> new ArrayList<>()).add(relation.to());
        }
        Map<String, Integer> state = new HashMap<>();
        for (String node : graph.keySet()) {
            if (hasCycle(node, graph, state)) {
                throw new IllegalArgumentException("relation DAG contains a cycle");
            }
        }
    }

    private static boolean hasCycle(String node, Map<String, List<String>> graph, Map<String, Integer> state) {
        int current = state.getOrDefault(node, 0);
        if (current == 1) {
            return true;
        }
        if (current == 2) {
            return false;
        }
        state.put(node, 1);
        for (String next : graph.getOrDefault(node, List.of())) {
            if (hasCycle(next, graph, state)) {
                return true;
            }
        }
        state.put(node, 2);
        return false;
    }

    private static void validateHardConflicts(List<Constraint> constraints) {
        Map<String, FixedRange> hardRanges = new HashMap<>();
        Map<String, EdgeClassification> edgeClassifications = new HashMap<>();
        for (Constraint constraint : constraints) {
            if (constraint.strength() != Strength.HARD) {
                continue;
            }
            if (constraint instanceof MetricRangeConstraint metric) {
                String key = metric.subject() + '\n' + metric.metric();
                FixedRange previous = hardRanges.putIfAbsent(key, metric.range());
                if (previous != null && !previous.overlaps(metric.range())) {
                    throw new IllegalArgumentException("conflicting HARD metric constraints for " + key);
                }
            } else if (constraint instanceof EdgeClassificationConstraint edge) {
                EdgeClassification previous = edgeClassifications.putIfAbsent(edge.edge().name(), edge.classification());
                if (previous != null && previous != edge.classification()) {
                    throw new IllegalArgumentException("conflicting HARD edge classifications for " + edge.edge());
                }
            }
        }
    }

    public enum HorizontalCoordinates { NORMALIZED_XZ }
    public enum CoordinateOrigin { NORTH_WEST }
    public enum XAxis { EAST }
    public enum ZAxis { SOUTH }
    public enum VerticalCoordinates { BLOCK_Y_OR_SURFACE_OFFSET }
    public enum GeometryType { POINT, MULTI_POINT, SPLINE, MULTI_SPLINE, POLYGON, VOLUME_GUIDE }
    public enum Interpolation { POLYLINE, CATMULL_ROM }
    public enum VerticalMode { ABSOLUTE_Y, SURFACE_OFFSET, WATER_LEVEL_OFFSET }
    public enum Strength { HARD, SOFT }
    public enum Edge { NORTH, EAST, SOUTH, WEST }
    public enum PlateauProfile { MESA, BUTTE, GENERIC }
    public enum EdgeClassification { LAND, SEA }
    public enum Sampling { NEAREST, BILINEAR_FIXED }
    public enum ConstraintMapRole { LAND_WATER_MASK, HEIGHT_GUIDE, ZONE_LABEL_MAP }
    public enum ProvenanceSource { MANUAL, AI_DRAFT, PRESET, UPGRADED_V1 }
    public enum ConfirmationState { CONFIRMED, UNCONFIRMED }
    public enum StructureKind { SMALL_PIER, SMALL_BRIDGE, FISHING_HUT, STONE_RUIN, PATH, RETAINING_WALL, STONE_STEPS, FENCE }
    public enum Measurement { CLEAR_EDGE_TO_EDGE }
    public enum InnerSide { NORTH, EAST, SOUTH, WEST }
    public enum BreakwaterCrestProfile { FLAT }
    public enum BreakwaterFoundationProfile { LINEAR_SIDE_SLOPE }
    public enum CapeMode { TWO_POINT_FIVE_D_ONLY, LOCAL_VOLUME_REQUIRED }
    public enum LandSide { LEFT, RIGHT }
    public enum TransitionProfile { NONE, PRIORITY_BLEND }
    public enum DischargeClass { SMALL, MEDIUM, LARGE }
    public enum RiverVariant { RIVER, MEANDERING_RIVER }
    public enum LakeTerminalPolicy { OPEN_SPILL, CLOSED }
    public enum LakeSpillSelection { DECLARED_EDGE, LOWEST_RIM_SADDLE }
    public enum LakeFloorProfile { EDGE_TO_CENTER_LINEAR }
    public enum CanyonCrossSection { V, U, TERRACED_V, TERRACED_U }
    public enum DeltaFanProfile { APEX_TO_SEA_LINEAR }
    public enum TidalEdgeKind { BIDIRECTIONAL }
    public enum FjordCrossSection { GLACIAL_U }
    /** V2-9-03 general valley cross-section; distinct from specialized {@link FjordCrossSection}. */
    public enum ValleyCrossSection { V_PROFILE, U_PROFILE }
    /** Optional valley connection role for fjord/river handoff inspection (not a FeatureKind). */
    public enum ValleyConnectionRole { NONE, FJORD_HEAD, RIVER_CORRIDOR }
    public enum MountainVariant { ALPINE, GLACIAL }
    public enum CalderaBreachDirection {
        NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST
    }

    public enum FeatureKind {
        SANDY_BEACH,
        BREAKWATER_HARBOR,
        HARBOR_BASIN,
        ROCKY_CAPE,
        ROCKY_COAST,
        BACKSHORE_PLAINS,
        PLAIN,
        HILL_RANGE,
        MOUNTAIN_RANGE,
        VALLEY,
        RIVER,
        FLOODPLAIN,
        MARSH,
        FJORD,
        GLACIAL_MOUNTAIN_RANGE,
        MEANDERING_RIVER,
        LAKE,
        DELTA,
        VOLCANIC_ARCHIPELAGO,
        VOLCANIC_CALDERA,
        LAVA_FLOW_FIELD,
        SINGLE_ISLAND,
        ARCHIPELAGO,
        VOLCANIC_CONE,
        OCEAN_BASIN,
        ABYSSAL_PLAIN,
        SEAMOUNT,
        CONTINENTAL_SHELF,
        CONTINENTAL_SLOPE,
        SUBMARINE_CANYON,
        CANYON,
        BEDROCK_RIVER,
        WATERFALL,
        MANGROVE_WETLAND,
        TIDAL_CHANNEL_NETWORK,
        ALPINE_MOUNTAIN_RANGE,
        GLACIAL_CIRQUE_FIELD,
        CORAL_REEF,
        LAGOON,
        REEF_PASS,
        CAVE_NETWORK,
        CAVE_ENTRANCE,
        UNDERGROUND_RIVER,
        SINKHOLE,
        KARST_SPRING,
        FLOODED_CAVE,
        LUSH_CAVE,
        SEA_CLIFF,
        OVERHANG,
        SKY_ISLAND_GROUP,
        VALLEY_GLACIER,
        ICE_CAP,
        ICE_SHEET,
        MORAINE_FIELD,
        OUTWASH_PLAIN,
        ESCARPMENT,
        PLATEAU,
        LAVA_TUBE,
        SPRING,
        OXBOW_LAKE
    }

    public enum RelationKind {
        CONNECTS_TO(false), DRAINS_TO(true), EMPTIES_INTO(true), WITHIN(true), FLANKS(true),
        ADJACENT_TO(false), ENCLOSED_BY(true), ENCLOSES(true), ON_PATH_OF(true), ORIGINATES_AT(true),
        REACHABLE_FROM(true), ENTRANCE_OF(true),         CARVES_FLANK_OF(true), CARVES_THROUGH(true),
        SUPPORTED_BY(true), SUPPORTS_FROM(true), OVERLAPS(false), EXCLUDES(false), UPSTREAM_OF(true);

        private final boolean requiresDag;

        RelationKind(boolean requiresDag) {
            this.requiresDag = requiresDag;
        }

        public boolean requiresDag() {
            return requiresDag;
        }

        public boolean symmetric() {
            return this == ADJACENT_TO || this == OVERLAPS || this == EXCLUDES;
        }
    }

    public record CoordinateSystem(
            HorizontalCoordinates horizontal,
            CoordinateOrigin origin,
            XAxis xAxis,
            ZAxis zAxis,
            VerticalCoordinates vertical
    ) {
        public CoordinateSystem {
            Objects.requireNonNull(horizontal, "horizontal");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(xAxis, "xAxis");
            Objects.requireNonNull(zAxis, "zAxis");
            Objects.requireNonNull(vertical, "vertical");
        }
    }

    /** Normalized coordinate stored as millionths to avoid platform floating-point formatting. */
    public record Point2(int xMillionths, int zMillionths) {
        public Point2 {
            if (xMillionths < 0 || xMillionths > FIXED_SCALE || zMillionths < 0 || zMillionths > FIXED_SCALE) {
                throw new IllegalArgumentException("normalized coordinates must be between 0 and 1");
            }
        }
    }

    public sealed interface Geometry permits PointGeometry, MultiPointGeometry, SplineGeometry,
            MultiSplineGeometry, PolygonGeometry, VolumeGuideGeometry {
        GeometryType type();

        default void collectStableIds(Set<String> target) {
            Objects.requireNonNull(target, "target");
        }
    }

    public record PointGeometry(Point2 point) implements Geometry {
        public PointGeometry { Objects.requireNonNull(point, "point"); }
        @Override public GeometryType type() { return GeometryType.POINT; }
    }

    public record NamedPoint(String id, Point2 point) {
        public NamedPoint {
            id = V2Validation.slug(id, "point id");
            Objects.requireNonNull(point, "point");
        }
    }

    public record MultiPointGeometry(List<NamedPoint> points) implements Geometry {
        public MultiPointGeometry {
            points = V2Validation.sorted(points, "points", 256, Comparator.comparing(NamedPoint::id));
            requireUnique(points.stream().map(NamedPoint::id).toList(), "point");
            if (points.isEmpty()) throw new IllegalArgumentException("MULTI_POINT requires points");
        }
        @Override public GeometryType type() { return GeometryType.MULTI_POINT; }
        @Override public void collectStableIds(Set<String> target) {
            for (NamedPoint point : points) if (!target.add(point.id())) throw new IllegalArgumentException("duplicate subgeometry id: " + point.id());
        }
    }

    public record SplineGeometry(List<Point2> points, Interpolation interpolation) implements Geometry {
        public SplineGeometry {
            points = V2Validation.immutable(points, "spline points", 1_024);
            Objects.requireNonNull(interpolation, "interpolation");
            if (points.size() < 2) throw new IllegalArgumentException("SPLINE requires at least two points");
            for (int index = 1; index < points.size(); index++) {
                if (points.get(index - 1).equals(points.get(index))) throw new IllegalArgumentException("SPLINE has a zero-length segment");
            }
        }
        @Override public GeometryType type() { return GeometryType.SPLINE; }
    }

    public record NamedPath(String id, String startEndpointId, String endEndpointId, List<Point2> points) {
        public NamedPath {
            id = V2Validation.slug(id, "path id");
            startEndpointId = optionalSlug(startEndpointId, "startEndpointId");
            endEndpointId = optionalSlug(endEndpointId, "endEndpointId");
            points = V2Validation.immutable(points, "path points", 1_024);
            if (points.size() < 2) throw new IllegalArgumentException("path requires at least two points");
        }
    }

    public record MultiSplineGeometry(List<NamedPath> paths, Interpolation interpolation) implements Geometry {
        public MultiSplineGeometry {
            paths = V2Validation.sorted(paths, "paths", 128, Comparator.comparing(NamedPath::id));
            requireUnique(paths.stream().map(NamedPath::id).toList(), "path");
            if (paths.isEmpty()) throw new IllegalArgumentException("MULTI_SPLINE requires paths");
            Objects.requireNonNull(interpolation, "interpolation");
        }
        @Override public GeometryType type() { return GeometryType.MULTI_SPLINE; }
        @Override public void collectStableIds(Set<String> target) {
            for (NamedPath path : paths) {
                if (!target.add(path.id())) throw new IllegalArgumentException("duplicate subgeometry id: " + path.id());
                addOptionalStableId(target, path.startEndpointId());
                addOptionalStableId(target, path.endEndpointId());
            }
        }
    }

    public record PolygonGeometry(List<List<Point2>> rings) implements Geometry {
        public PolygonGeometry {
            rings = V2Validation.immutable(rings, "rings", 64).stream()
                    .map(ring -> V2Validation.immutable(ring, "ring", 2_048)).toList();
            if (rings.isEmpty()) throw new IllegalArgumentException("POLYGON requires an outer ring");
            for (List<Point2> ring : rings) validateRing(ring);
            for (int index = 1; index < rings.size(); index++) validateHole(rings.getFirst(), rings.get(index));
        }
        @Override public GeometryType type() { return GeometryType.POLYGON; }
    }

    public record VerticalGuide(VerticalMode mode, int minimum, int maximum) {
        public VerticalGuide {
            Objects.requireNonNull(mode, "mode");
            if (minimum > maximum) throw new IllegalArgumentException("vertical minimum exceeds maximum");
        }
    }

    public record VolumeGuideGeometry(PolygonGeometry footprint, VerticalGuide vertical) implements Geometry {
        public VolumeGuideGeometry {
            Objects.requireNonNull(footprint, "footprint");
            Objects.requireNonNull(vertical, "vertical");
        }
        @Override public GeometryType type() { return GeometryType.VOLUME_GUIDE; }
    }

    public sealed interface FeatureParameters permits SandyBeachParameters, BreakwaterHarborParameters,
            HarborBasinParameters, RockyCapeParameters, RockyCoastParameters, BackshorePlainsParameters,
            PlainParameters, HillRangeParameters, MountainRangeParameters, ValleyParameters, RiverParameters,
            FloodplainParameters, MarshParameters, MeanderingRiverParameters, SeaCliffParameters,
            LakeParameters, CanyonParameters, WaterfallParameters, DeltaParameters, TidalChannelParameters,
            FjordParameters, MountainParameters, VolcanicArchipelagoParameters, VolcanicCalderaParameters,
            LavaFlowParameters, SingleIslandParameters, ArchipelagoParameters, VolcanicConeParameters,
            OceanBasinParameters, ContinentalShelfParameters, ContinentalSlopeParameters,
            SubmarineCanyonParameters, CaveEntranceParameters,
            UndergroundRiverParameters, FloodedCaveParameters,
            SinkholeParameters, KarstSpringParameters,
            GlacialIceParameters,
            MoraineFieldParameters, OutwashPlainParameters,
            EscarpmentParameters, PlateauParameters, LavaTubeParameters, SpringParameters,
            OxbowLakeParameters,
            AbyssalPlainParameters, SeamountParameters,
            MangroveWetlandParameters, CoralReefParameters, LagoonParameters,
            ReefPassParameters, NoParameters { }

    public record IntRange(int minimum, int maximum) {
        public IntRange { if (minimum > maximum) throw new IllegalArgumentException("range minimum exceeds maximum"); }
    }

    public record FixedRange(long minimumMillionths, long maximumMillionths) {
        public FixedRange { if (minimumMillionths > maximumMillionths) throw new IllegalArgumentException("range minimum exceeds maximum"); }
        public boolean overlaps(FixedRange other) { return minimumMillionths <= other.maximumMillionths && other.minimumMillionths <= maximumMillionths; }
    }

    public record NearshoreDepth(int atDistance, int target) {
        public NearshoreDepth {
            if (atDistance < 1 || atDistance > 63 || target < 1 || target > 64) {
                throw new IllegalArgumentException("nearshore distance/target outside 1..63/1..64");
            }
        }
    }

    public record SandyBeachParameters(
            IntRange widthBlocks,
            FixedRange shoreSlopeDegrees,
            NearshoreDepth nearshoreDepthBlocks,
            int foreshoreShareMillionths,
            int endpointTaperBlocks,
            LandSide landSide
    )
            implements FeatureParameters {
        public SandyBeachParameters {
            Objects.requireNonNull(widthBlocks, "widthBlocks");
            Objects.requireNonNull(shoreSlopeDegrees, "shoreSlopeDegrees");
            Objects.requireNonNull(nearshoreDepthBlocks, "nearshoreDepthBlocks");
            Objects.requireNonNull(landSide, "landSide");
            requirePositiveRange(widthBlocks, "widthBlocks");
            if (shoreSlopeDegrees.minimumMillionths() <= 0
                    || shoreSlopeDegrees.maximumMillionths() > 30L * FIXED_SCALE) {
                throw new IllegalArgumentException("shoreSlopeDegrees outside (0..30]");
            }
            if (foreshoreShareMillionths < 100_000 || foreshoreShareMillionths > 900_000) {
                throw new IllegalArgumentException("foreshoreShare01 outside 0.1..0.9");
            }
            if (endpointTaperBlocks < 1 || endpointTaperBlocks > 64) {
                throw new IllegalArgumentException("endpointTaperBlocks outside 1..64");
            }
        }
    }

    public record HarborOpening(List<String> betweenEndpointIds, int widthBlocks, Measurement measurement) {
        public HarborOpening {
            betweenEndpointIds = V2Validation.immutable(betweenEndpointIds, "betweenEndpointIds", 2).stream()
                    .map(value -> V2Validation.slug(value, "endpoint id")).toList();
            if (betweenEndpointIds.size() != 2
                    || betweenEndpointIds.getFirst().equals(betweenEndpointIds.getLast())
                    || widthBlocks < 2 || widthBlocks > 64) {
                throw new IllegalArgumentException("harbor opening is invalid");
            }
            Objects.requireNonNull(measurement, "measurement");
        }
    }

    public record BreakwaterHarborParameters(
            int crestWidthBlocks,
            int crestAboveWaterBlocks,
            int outerDepthBlocks,
            BreakwaterCrestProfile crestProfile,
            BreakwaterFoundationProfile foundationProfile,
            int foundationSideSlopeRunPerRiseMillionths,
            HarborOpening opening,
            InnerSide innerSide
    ) implements FeatureParameters {
        public BreakwaterHarborParameters {
            if (crestWidthBlocks < 1 || crestWidthBlocks > 64
                    || crestAboveWaterBlocks < 0 || crestAboveWaterBlocks > 32
                    || outerDepthBlocks < 1 || outerDepthBlocks > 64
                    || foundationSideSlopeRunPerRiseMillionths < 250_000
                    || foundationSideSlopeRunPerRiseMillionths > 4_000_000) {
                throw new IllegalArgumentException("breakwater dimensions are invalid");
            }
            Objects.requireNonNull(crestProfile, "crestProfile");
            Objects.requireNonNull(foundationProfile, "foundationProfile");
            Objects.requireNonNull(opening, "opening");
            Objects.requireNonNull(innerSide, "innerSide");
        }
    }

    public record HarborBasinParameters(
            IntRange waterDepthBlocks,
            List<String> entranceEndpointIds,
            int entranceCorridorLengthBlocks,
            HarborBottomProfile bottomProfile,
            int profileTransitionBlocks
    )
            implements FeatureParameters {
        public HarborBasinParameters {
            Objects.requireNonNull(waterDepthBlocks, "waterDepthBlocks");
            requirePositiveRange(waterDepthBlocks, "waterDepthBlocks");
            if (waterDepthBlocks.maximum() > 64) {
                throw new IllegalArgumentException("waterDepthBlocks outside 1..64");
            }
            entranceEndpointIds = V2Validation.immutable(entranceEndpointIds, "entranceEndpointIds", 2).stream()
                    .map(value -> V2Validation.slug(value, "endpoint id")).toList();
            if (entranceEndpointIds.size() != 2
                    || entranceEndpointIds.getFirst().equals(entranceEndpointIds.getLast())) {
                throw new IllegalArgumentException("harbor basin requires exactly two unique entrance endpoints");
            }
            if (entranceCorridorLengthBlocks < 1 || entranceCorridorLengthBlocks > 64
                    || profileTransitionBlocks < 1 || profileTransitionBlocks > 64) {
                throw new IllegalArgumentException("harbor basin corridor/profile outside 1..64");
            }
            Objects.requireNonNull(bottomProfile, "bottomProfile");
        }
    }

    public enum HarborBottomProfile { EDGE_TO_CENTER_LINEAR }

    public record RockyCapeParameters(
            IntRange cliffHeightBlocks,
            IntRange localReliefAboveSeaBlocks,
            IntRange cliffBandWidthBlocks,
            IntRange seaStackCount,
            IntRange seaStackRadiusBlocks,
            IntRange seaStackOffshoreDistanceBlocks,
            IntRange channelCount,
            IntRange channelWidthBlocks,
            IntRange channelLengthBlocks,
            IntRange channelDepthBlocks,
            FixedRange rockExposure01,
            Edge seawardSide,
            CapeMode capeMode
    ) implements FeatureParameters {
        public RockyCapeParameters {
            Objects.requireNonNull(cliffHeightBlocks, "cliffHeightBlocks");
            Objects.requireNonNull(localReliefAboveSeaBlocks, "localReliefAboveSeaBlocks");
            Objects.requireNonNull(cliffBandWidthBlocks, "cliffBandWidthBlocks");
            Objects.requireNonNull(seaStackCount, "seaStackCount");
            Objects.requireNonNull(seaStackRadiusBlocks, "seaStackRadiusBlocks");
            Objects.requireNonNull(seaStackOffshoreDistanceBlocks, "seaStackOffshoreDistanceBlocks");
            Objects.requireNonNull(channelCount, "channelCount");
            Objects.requireNonNull(channelWidthBlocks, "channelWidthBlocks");
            Objects.requireNonNull(channelLengthBlocks, "channelLengthBlocks");
            Objects.requireNonNull(channelDepthBlocks, "channelDepthBlocks");
            requireBoundedPositiveRange(cliffHeightBlocks, "cliffHeightBlocks", 64);
            requireBoundedPositiveRange(localReliefAboveSeaBlocks, "localReliefAboveSeaBlocks", 64);
            requireBoundedPositiveRange(cliffBandWidthBlocks, "cliffBandWidthBlocks", 64);
            requireBoundedPositiveRange(seaStackCount, "seaStackCount", 12);
            requireBoundedPositiveRange(seaStackRadiusBlocks, "seaStackRadiusBlocks", 8);
            requireBoundedPositiveRange(
                    seaStackOffshoreDistanceBlocks, "seaStackOffshoreDistanceBlocks", 64);
            requireBoundedPositiveRange(channelCount, "channelCount", 4);
            requireBoundedPositiveRange(channelWidthBlocks, "channelWidthBlocks", 8);
            requireBoundedPositiveRange(channelLengthBlocks, "channelLengthBlocks", 64);
            requireBoundedPositiveRange(channelDepthBlocks, "channelDepthBlocks", 16);
            if (channelWidthBlocks.minimum() < 2 || channelLengthBlocks.minimum() < 4
                    || localReliefAboveSeaBlocks.maximum() < cliffHeightBlocks.minimum()
                    || seaStackOffshoreDistanceBlocks.minimum() < seaStackRadiusBlocks.maximum() + 2
                    || Math.max(cliffBandWidthBlocks.maximum(), Math.max(
                    channelLengthBlocks.maximum() + (channelWidthBlocks.maximum() + 1) / 2,
                    seaStackOffshoreDistanceBlocks.maximum() + seaStackRadiusBlocks.maximum())) > 64) {
                throw new IllegalArgumentException("rocky cape profile cannot be realized within bounded 2.5D support");
            }
            requireUnitRange(rockExposure01, "rockExposure01");
            if (rockExposure01.minimumMillionths() < 50_000) {
                throw new IllegalArgumentException("rockExposure01 must be at least 0.05");
            }
            Objects.requireNonNull(seawardSide, "seawardSide");
            Objects.requireNonNull(capeMode, "capeMode");
        }
    }

    /**
     * V2-9-06 ROCKY_COAST foundation parameters: rock shelf, exposure, shore side, and cape/beach
     * transition band. Distinct from {@link RockyCapeParameters}.
     */
    public record RockyCoastParameters(
            IntRange rockShelfWidthBlocks,
            FixedRange rockExposure01,
            Edge shoreSide,
            IntRange channelCount,
            int capeOrBeachTransitionBandBlocks,
            IntRange talusHandoffDepthBlocks
    ) implements FeatureParameters {
        public RockyCoastParameters {
            Objects.requireNonNull(rockShelfWidthBlocks, "rockShelfWidthBlocks");
            Objects.requireNonNull(rockExposure01, "rockExposure01");
            Objects.requireNonNull(shoreSide, "shoreSide");
            Objects.requireNonNull(channelCount, "channelCount");
            Objects.requireNonNull(talusHandoffDepthBlocks, "talusHandoffDepthBlocks");
            requireBoundedPositiveRange(rockShelfWidthBlocks, "rockShelfWidthBlocks", 64);
            requireUnitRange(rockExposure01, "rockExposure01");
            if (rockExposure01.minimumMillionths() < 50_000L) {
                throw new IllegalArgumentException("rocky coast rockExposure01 minimum must be at least 0.05");
            }
            requireBoundedPositiveRange(channelCount, "channelCount", 8);
            if (capeOrBeachTransitionBandBlocks < 1 || capeOrBeachTransitionBandBlocks > 32) {
                throw new IllegalArgumentException("capeOrBeachTransitionBandBlocks outside 1..32");
            }
            requireBoundedPositiveRange(talusHandoffDepthBlocks, "talusHandoffDepthBlocks", 32);
        }
    }

    /**
     * V2-9-06 SEA_CLIFF foundation parameters: cliff face, talus, wave-cut notch, and volume host support.
     * Replaces diagnostic-only empty parameters for this kind.
     */
    public record SeaCliffParameters(
            IntRange cliffHeightBlocks,
            IntRange talusWidthBlocks,
            IntRange notchDepthBlocks,
            Edge seawardSide,
            IntRange supportHalfExtentXZBlocks,
            int coastTransitionBandBlocks
    ) implements FeatureParameters {
        public SeaCliffParameters {
            Objects.requireNonNull(cliffHeightBlocks, "cliffHeightBlocks");
            Objects.requireNonNull(talusWidthBlocks, "talusWidthBlocks");
            Objects.requireNonNull(notchDepthBlocks, "notchDepthBlocks");
            Objects.requireNonNull(seawardSide, "seawardSide");
            Objects.requireNonNull(supportHalfExtentXZBlocks, "supportHalfExtentXZBlocks");
            requireBoundedPositiveRange(cliffHeightBlocks, "cliffHeightBlocks", 128);
            requireBoundedPositiveRange(talusWidthBlocks, "talusWidthBlocks", 64);
            requireBoundedPositiveRange(notchDepthBlocks, "notchDepthBlocks", 32);
            requireBoundedPositiveRange(supportHalfExtentXZBlocks, "supportHalfExtentXZBlocks", 64);
            if (coastTransitionBandBlocks < 1 || coastTransitionBandBlocks > 32) {
                throw new IllegalArgumentException("coastTransitionBandBlocks outside 1..32");
            }
        }
    }

    public record LakeParameters(
            IntRange targetDepthBlocks,
            int shoreWidthBlocks,
            LakeTerminalPolicy terminalPolicy,
            LakeSpillSelection spillSelection,
            int spillEdgeStartIndex,
            int spillwayWidthBlocks,
            int spillwayCorridorLengthBlocks,
            LakeFloorProfile floorProfile
    ) implements FeatureParameters {
        public LakeParameters {
            Objects.requireNonNull(targetDepthBlocks, "targetDepthBlocks");
            Objects.requireNonNull(terminalPolicy, "terminalPolicy");
            Objects.requireNonNull(spillSelection, "spillSelection");
            Objects.requireNonNull(floorProfile, "floorProfile");
            requireBoundedPositiveRange(targetDepthBlocks, "targetDepthBlocks", 64);
            if (shoreWidthBlocks < 1 || shoreWidthBlocks > 16) {
                throw new IllegalArgumentException("shoreWidthBlocks outside 1..16");
            }
            if (terminalPolicy == LakeTerminalPolicy.CLOSED) {
                if (spillSelection != LakeSpillSelection.DECLARED_EDGE
                        || spillEdgeStartIndex != -1
                        || spillwayWidthBlocks != 0
                        || spillwayCorridorLengthBlocks != 0) {
                    throw new IllegalArgumentException("closed lake must not declare a spillway");
                }
            } else {
                if (spillwayWidthBlocks < 2 || spillwayWidthBlocks > 16
                        || spillwayCorridorLengthBlocks < 1 || spillwayCorridorLengthBlocks > 32) {
                    throw new IllegalArgumentException("open lake spillway dimensions are invalid");
                }
                if (spillSelection == LakeSpillSelection.DECLARED_EDGE) {
                    if (spillEdgeStartIndex < 0 || spillEdgeStartIndex > 1_024) {
                        throw new IllegalArgumentException("spillEdgeStartIndex outside 0..1024");
                    }
                } else if (spillEdgeStartIndex != -1) {
                    throw new IllegalArgumentException("lowest-rim spill selection must not declare an edge index");
                }
            }
            if (floorProfile != LakeFloorProfile.EDGE_TO_CENTER_LINEAR) {
                throw new IllegalArgumentException("unsupported lake floor profile");
            }
        }
    }

    public record CanyonParameters(
            IntRange floorWidthBlocks,
            IntRange rimWidthBlocks,
            IntRange depthBlocks,
            CanyonCrossSection crossSection,
            int terraceCount,
            int terraceWidthBlocks
    ) implements FeatureParameters {
        public CanyonParameters {
            Objects.requireNonNull(floorWidthBlocks, "floorWidthBlocks");
            Objects.requireNonNull(rimWidthBlocks, "rimWidthBlocks");
            Objects.requireNonNull(depthBlocks, "depthBlocks");
            Objects.requireNonNull(crossSection, "crossSection");
            requireBoundedPositiveRange(floorWidthBlocks, "floorWidthBlocks", 64);
            if (floorWidthBlocks.minimum() < 2) {
                throw new IllegalArgumentException("floorWidthBlocks must be at least 2");
            }
            requireBoundedPositiveRange(rimWidthBlocks, "rimWidthBlocks", 256);
            requireBoundedPositiveRange(depthBlocks, "depthBlocks", 128);
            if (rimWidthBlocks.minimum() < floorWidthBlocks.maximum() + 2) {
                throw new IllegalArgumentException("rimWidthBlocks must leave at least two blocks of wall outside the floor");
            }
            boolean terraced = crossSection == CanyonCrossSection.TERRACED_V
                    || crossSection == CanyonCrossSection.TERRACED_U;
            if (terraced) {
                if (terraceCount < 1 || terraceCount > 4
                        || terraceWidthBlocks < 1 || terraceWidthBlocks > 32) {
                    throw new IllegalArgumentException("terraced canyon requires terraceCount 1..4 and terraceWidthBlocks 1..32");
                }
            } else if (terraceCount != 0 || terraceWidthBlocks != 0) {
                throw new IllegalArgumentException("non-terraced canyon must not declare terraces");
            }
        }
    }

    public record WaterfallParameters(
            IntRange dropBlocks,
            int lipWidthBlocks,
            int plungePoolRadiusBlocks,
            int behindFallClearanceBlocks
    ) implements FeatureParameters {
        public WaterfallParameters {
            Objects.requireNonNull(dropBlocks, "dropBlocks");
            requireBoundedPositiveRange(dropBlocks, "dropBlocks", 128);
            if (lipWidthBlocks < 1 || lipWidthBlocks > 32) {
                throw new IllegalArgumentException("lipWidthBlocks outside 1..32");
            }
            if (plungePoolRadiusBlocks < 2 || plungePoolRadiusBlocks > 64) {
                throw new IllegalArgumentException("plungePoolRadiusBlocks outside 2..64");
            }
            if (behindFallClearanceBlocks < 0 || behindFallClearanceBlocks > 32) {
                throw new IllegalArgumentException("behindFallClearanceBlocks outside 0..32");
            }
        }
    }

    public record DeltaParameters(
            IntRange distributaryCount,
            FixedRange fanOpeningDegrees,
            IntRange fanReliefBlocks,
            IntRange sandbarCount,
            IntRange shallowSeaDepthBlocks,
            DeltaFanProfile fanProfile
    ) implements FeatureParameters {
        public DeltaParameters {
            Objects.requireNonNull(distributaryCount, "distributaryCount");
            Objects.requireNonNull(fanOpeningDegrees, "fanOpeningDegrees");
            Objects.requireNonNull(fanReliefBlocks, "fanReliefBlocks");
            Objects.requireNonNull(sandbarCount, "sandbarCount");
            Objects.requireNonNull(shallowSeaDepthBlocks, "shallowSeaDepthBlocks");
            Objects.requireNonNull(fanProfile, "fanProfile");
            requireBoundedPositiveRange(distributaryCount, "distributaryCount", 16);
            if (distributaryCount.minimum() < 2) {
                throw new IllegalArgumentException("delta requires at least two distributaries");
            }
            if (fanOpeningDegrees.minimumMillionths() < 10L * FIXED_SCALE
                    || fanOpeningDegrees.maximumMillionths() > 170L * FIXED_SCALE) {
                throw new IllegalArgumentException("fanOpeningDegrees outside 10..170");
            }
            requireBoundedPositiveRange(fanReliefBlocks, "fanReliefBlocks", 16);
            requireNonNegativeRange(sandbarCount, "sandbarCount");
            if (sandbarCount.maximum() > 32) {
                throw new IllegalArgumentException("sandbarCount exceeds 32");
            }
            requireBoundedPositiveRange(shallowSeaDepthBlocks, "shallowSeaDepthBlocks", 16);
            if (fanProfile != DeltaFanProfile.APEX_TO_SEA_LINEAR) {
                throw new IllegalArgumentException("unsupported delta fan profile");
            }
        }
    }

    public record TidalChannelParameters(
            IntRange widthBlocks,
            int tidalRangeBlocks,
            TidalEdgeKind edgeKind
    ) implements FeatureParameters {
        public TidalChannelParameters {
            Objects.requireNonNull(widthBlocks, "widthBlocks");
            Objects.requireNonNull(edgeKind, "edgeKind");
            requireBoundedPositiveRange(widthBlocks, "widthBlocks", 32);
            if (widthBlocks.minimum() < 2) {
                throw new IllegalArgumentException("tidal channel width requires at least 2");
            }
            if (tidalRangeBlocks < 1 || tidalRangeBlocks > 8) {
                throw new IllegalArgumentException("tidalRangeBlocks outside 1..8");
            }
            if (edgeKind != TidalEdgeKind.BIDIRECTIONAL) {
                throw new IllegalArgumentException("unsupported tidal edge kind");
            }
        }
    }

    /** V2-3-09 parameters for the bounded 2.5D fjord profile. */
    public record FjordParameters(
            IntRange surfaceWidthBlocks,
            IntRange channelDepthBlocks,
            FjordCrossSection crossSection,
            int headBasinRadiusBlocks
    ) implements FeatureParameters {
        public FjordParameters {
            Objects.requireNonNull(surfaceWidthBlocks, "surfaceWidthBlocks");
            Objects.requireNonNull(channelDepthBlocks, "channelDepthBlocks");
            Objects.requireNonNull(crossSection, "crossSection");
            requireBoundedPositiveRange(surfaceWidthBlocks, "surfaceWidthBlocks", 128);
            requireBoundedPositiveRange(channelDepthBlocks, "channelDepthBlocks", 64);
            if (surfaceWidthBlocks.minimum() < 16) {
                throw new IllegalArgumentException("fjord surfaceWidthBlocks must be at least 16");
            }
            if (channelDepthBlocks.minimum() < 8) {
                throw new IllegalArgumentException("fjord channelDepthBlocks must be at least 8");
            }
            if (crossSection != FjordCrossSection.GLACIAL_U) {
                throw new IllegalArgumentException("unsupported fjord cross section");
            }
            if (headBasinRadiusBlocks < 8 || headBasinRadiusBlocks > 128) {
                throw new IllegalArgumentException("fjord headBasinRadiusBlocks outside 8..128");
            }
        }
    }

    /** V2-3-10 shared ridge skeleton parameters. Snowline and material are out of scope. */
    public record MountainParameters(
            IntRange peakCount,
            IntRange ridgeHalfWidthBlocks,
            IntRange maxReliefBlocks,
            int spurCount,
            long ridgeSharpnessMillionths
    ) implements FeatureParameters {
        public MountainParameters {
            Objects.requireNonNull(peakCount, "peakCount");
            Objects.requireNonNull(ridgeHalfWidthBlocks, "ridgeHalfWidthBlocks");
            Objects.requireNonNull(maxReliefBlocks, "maxReliefBlocks");
            requireBoundedPositiveRange(peakCount, "peakCount", 16);
            requireBoundedPositiveRange(ridgeHalfWidthBlocks, "ridgeHalfWidthBlocks", 64);
            requireBoundedPositiveRange(maxReliefBlocks, "maxReliefBlocks", 256);
            if (peakCount.minimum() < 2) {
                throw new IllegalArgumentException("mountain peakCount must be at least 2");
            }
            if (ridgeHalfWidthBlocks.minimum() < 4) {
                throw new IllegalArgumentException("mountain ridgeHalfWidthBlocks must be at least 4");
            }
            if (maxReliefBlocks.minimum() < 16) {
                throw new IllegalArgumentException("mountain maxReliefBlocks must be at least 16");
            }
            if (spurCount < 0 || spurCount > 8) {
                throw new IllegalArgumentException("mountain spurCount outside 0..8");
            }
            if (ridgeSharpnessMillionths < 100_000 || ridgeSharpnessMillionths > FIXED_SCALE) {
                throw new IllegalArgumentException("mountain ridgeSharpnessMillionths outside 0.1..1");
            }
        }
    }

    /** V2-3-11 archipelago island mass. Material/geology are out of scope. */
    public record IslandSpec(String pointId, int radiusBlocks, int summitHeightBlocksAboveSea) {
        public IslandSpec {
            pointId = V2Validation.slug(pointId, "pointId");
            if (radiusBlocks < 8 || radiusBlocks > 256) {
                throw new IllegalArgumentException("island radiusBlocks outside 8..256");
            }
            if (summitHeightBlocksAboveSea < 8 || summitHeightBlocksAboveSea > 256) {
                throw new IllegalArgumentException("summitHeightBlocksAboveSea outside 8..256");
            }
        }
    }

    public record VolcanicArchipelagoParameters(
            List<IslandSpec> islands,
            IntRange submarineSaddleDepthBlocks
    ) implements FeatureParameters {
        public VolcanicArchipelagoParameters {
            islands = V2Validation.sorted(islands, "islands", 64, Comparator.comparing(IslandSpec::pointId));
            Objects.requireNonNull(submarineSaddleDepthBlocks, "submarineSaddleDepthBlocks");
            if (islands.size() < 2) {
                throw new IllegalArgumentException("volcanic archipelago needs at least two islands");
            }
            requireUnique(islands.stream().map(IslandSpec::pointId).toList(), "island pointId");
            requireBoundedPositiveRange(submarineSaddleDepthBlocks, "submarineSaddleDepthBlocks", 64);
            if (submarineSaddleDepthBlocks.minimum() < 4) {
                throw new IllegalArgumentException("submarineSaddleDepthBlocks must be at least 4");
            }
        }
    }

    public record VolcanicCalderaParameters(
            int rimRadiusBlocks,
            int rimReliefBlocks,
            int craterFloorDepthBlocks,
            CalderaBreachDirection breachDirection
    ) implements FeatureParameters {
        public VolcanicCalderaParameters {
            Objects.requireNonNull(breachDirection, "breachDirection");
            if (rimRadiusBlocks < 8 || rimRadiusBlocks > 128) {
                throw new IllegalArgumentException("rimRadiusBlocks outside 8..128");
            }
            if (rimReliefBlocks < 4 || rimReliefBlocks > 128) {
                throw new IllegalArgumentException("rimReliefBlocks outside 4..128");
            }
            if (craterFloorDepthBlocks < 4 || craterFloorDepthBlocks > 64) {
                throw new IllegalArgumentException("craterFloorDepthBlocks outside 4..64");
            }
            if (craterFloorDepthBlocks > rimReliefBlocks) {
                throw new IllegalArgumentException("craterFloorDepthBlocks exceeds rimReliefBlocks");
            }
        }
    }

    public record LavaFlowParameters(
            IntRange widthBlocks,
            long surfaceRoughnessMillionths
    ) implements FeatureParameters {
        public LavaFlowParameters {
            Objects.requireNonNull(widthBlocks, "widthBlocks");
            requireBoundedPositiveRange(widthBlocks, "widthBlocks", 64);
            if (widthBlocks.minimum() < 4) {
                throw new IllegalArgumentException("lava widthBlocks must be at least 4");
            }
            if (surfaceRoughnessMillionths < 0 || surfaceRoughnessMillionths > FIXED_SCALE) {
                throw new IllegalArgumentException("surfaceRoughness01 outside 0..1");
            }
        }
    }

    /**
     * V2-9-07 SINGLE_ISLAND foundation parameters (non-volcanic island mass／shore／drainage).
     */
    public record SingleIslandParameters(
            IntRange radiusBlocks,
            IntRange summitHeightBlocksAboveSea,
            IntRange shoreBandWidthBlocks,
            FixedRange radialDrainage01,
            IntRange submarineApronDepthBlocks
    ) implements FeatureParameters {
        public SingleIslandParameters {
            Objects.requireNonNull(radiusBlocks, "radiusBlocks");
            Objects.requireNonNull(summitHeightBlocksAboveSea, "summitHeightBlocksAboveSea");
            Objects.requireNonNull(shoreBandWidthBlocks, "shoreBandWidthBlocks");
            Objects.requireNonNull(radialDrainage01, "radialDrainage01");
            Objects.requireNonNull(submarineApronDepthBlocks, "submarineApronDepthBlocks");
            requireBoundedPositiveRange(radiusBlocks, "radiusBlocks", 256);
            if (radiusBlocks.minimum() < 8) {
                throw new IllegalArgumentException("single island radiusBlocks must be at least 8");
            }
            requireBoundedPositiveRange(summitHeightBlocksAboveSea, "summitHeightBlocksAboveSea", 256);
            if (summitHeightBlocksAboveSea.minimum() < 8) {
                throw new IllegalArgumentException("summitHeightBlocksAboveSea must be at least 8");
            }
            requireBoundedPositiveRange(shoreBandWidthBlocks, "shoreBandWidthBlocks", 32);
            requireUnitRange(radialDrainage01, "radialDrainage01");
            requireBoundedPositiveRange(submarineApronDepthBlocks, "submarineApronDepthBlocks", 64);
            if (submarineApronDepthBlocks.minimum() < 4) {
                throw new IllegalArgumentException("submarineApronDepthBlocks must be at least 4");
            }
        }
    }

    /**
     * V2-9-07 ARCHIPELAGO foundation parameters (non-volcanic group spacing／dominance／saddles).
     * Reuses {@link IslandSpec} shape; distinct from {@link VolcanicArchipelagoParameters}.
     */
    public record ArchipelagoParameters(
            List<IslandSpec> islands,
            IntRange submarineSaddleDepthBlocks,
            int minDryLandGapBlocks
    ) implements FeatureParameters {
        public ArchipelagoParameters {
            islands = V2Validation.sorted(islands, "islands", 64, Comparator.comparing(IslandSpec::pointId));
            Objects.requireNonNull(submarineSaddleDepthBlocks, "submarineSaddleDepthBlocks");
            if (islands.size() < 2) {
                throw new IllegalArgumentException("archipelago needs at least two islands");
            }
            requireUnique(islands.stream().map(IslandSpec::pointId).toList(), "island pointId");
            requireBoundedPositiveRange(submarineSaddleDepthBlocks, "submarineSaddleDepthBlocks", 64);
            if (submarineSaddleDepthBlocks.minimum() < 4) {
                throw new IllegalArgumentException("submarineSaddleDepthBlocks must be at least 4");
            }
            if (minDryLandGapBlocks < 4 || minDryLandGapBlocks > 64) {
                throw new IllegalArgumentException("minDryLandGapBlocks outside 4..64");
            }
        }
    }

    /**
     * V2-9-07 VOLCANIC_CONE foundation parameters (cone／crater／radial drainage core).
     * Does not promote {@link VolcanicCalderaParameters} to standalone.
     */
    public record VolcanicConeParameters(
            IntRange baseRadiusBlocks,
            IntRange summitHeightBlocksAboveSea,
            IntRange craterRadiusBlocks,
            IntRange craterFloorDepthBlocks,
            FixedRange radialDrainage01
    ) implements FeatureParameters {
        public VolcanicConeParameters {
            Objects.requireNonNull(baseRadiusBlocks, "baseRadiusBlocks");
            Objects.requireNonNull(summitHeightBlocksAboveSea, "summitHeightBlocksAboveSea");
            Objects.requireNonNull(craterRadiusBlocks, "craterRadiusBlocks");
            Objects.requireNonNull(craterFloorDepthBlocks, "craterFloorDepthBlocks");
            Objects.requireNonNull(radialDrainage01, "radialDrainage01");
            requireBoundedPositiveRange(baseRadiusBlocks, "baseRadiusBlocks", 256);
            if (baseRadiusBlocks.minimum() < 8) {
                throw new IllegalArgumentException("cone baseRadiusBlocks must be at least 8");
            }
            requireBoundedPositiveRange(summitHeightBlocksAboveSea, "summitHeightBlocksAboveSea", 256);
            if (summitHeightBlocksAboveSea.minimum() < 8) {
                throw new IllegalArgumentException("summitHeightBlocksAboveSea must be at least 8");
            }
            requireBoundedPositiveRange(craterRadiusBlocks, "craterRadiusBlocks", 64);
            requireBoundedPositiveRange(craterFloorDepthBlocks, "craterFloorDepthBlocks", 64);
            if (craterRadiusBlocks.maximum() >= baseRadiusBlocks.minimum()) {
                throw new IllegalArgumentException("craterRadiusBlocks must be strictly inside baseRadiusBlocks");
            }
            if (craterFloorDepthBlocks.maximum() > summitHeightBlocksAboveSea.minimum()) {
                throw new IllegalArgumentException("craterFloorDepthBlocks exceeds summit height");
            }
            requireUnitRange(radialDrainage01, "radialDrainage01");
        }
    }

    /**
     * V2-10-04 ABYSSAL_PLAIN foundation parameters: deep flat floor within an ocean basin host.
     */
    public record AbyssalPlainParameters(
            IntRange floorDepthBlocksBelowSea,
            IntRange floorReliefBlocks
    ) implements FeatureParameters {
        public AbyssalPlainParameters {
            Objects.requireNonNull(floorDepthBlocksBelowSea, "floorDepthBlocksBelowSea");
            Objects.requireNonNull(floorReliefBlocks, "floorReliefBlocks");
            requireBoundedPositiveRange(floorDepthBlocksBelowSea, "floorDepthBlocksBelowSea", 256);
            if (floorDepthBlocksBelowSea.minimum() < 16) {
                throw new IllegalArgumentException("abyssal plain floorDepthBlocksBelowSea must be at least 16");
            }
            requireNonNegativeRange(floorReliefBlocks, "floorReliefBlocks");
            if (floorReliefBlocks.maximum() > 8) {
                throw new IllegalArgumentException("abyssal plain floorReliefBlocks exceeds 8");
            }
        }
    }

    /**
     * V2-10-04 SEAMOUNT foundation parameters: underwater relief cone within an ocean basin host.
     */
    public record SeamountParameters(
            IntRange baseRadiusBlocks,
            IntRange reliefBlocks,
            IntRange summitDepthBlocksBelowSea
    ) implements FeatureParameters {
        public SeamountParameters {
            Objects.requireNonNull(baseRadiusBlocks, "baseRadiusBlocks");
            Objects.requireNonNull(reliefBlocks, "reliefBlocks");
            Objects.requireNonNull(summitDepthBlocksBelowSea, "summitDepthBlocksBelowSea");
            requireBoundedPositiveRange(baseRadiusBlocks, "baseRadiusBlocks", 64);
            if (baseRadiusBlocks.minimum() < 4) {
                throw new IllegalArgumentException("seamount baseRadiusBlocks must be at least 4");
            }
            requireBoundedPositiveRange(reliefBlocks, "reliefBlocks", 64);
            if (reliefBlocks.minimum() < 4) {
                throw new IllegalArgumentException("seamount reliefBlocks must be at least 4");
            }
            requireBoundedPositiveRange(summitDepthBlocksBelowSea, "summitDepthBlocksBelowSea", 240);
            if (summitDepthBlocksBelowSea.minimum() < 8) {
                throw new IllegalArgumentException("seamount summitDepthBlocksBelowSea must be at least 8");
            }
        }
    }

    /**
     * V2-9-08 OCEAN_BASIN foundation parameters: deep-water depth ownership (2.5D only).
     */
    public record OceanBasinParameters(
            IntRange maxDepthBlocksBelowSea,
            IntRange floorReliefBlocks
    ) implements FeatureParameters {
        public OceanBasinParameters {
            Objects.requireNonNull(maxDepthBlocksBelowSea, "maxDepthBlocksBelowSea");
            Objects.requireNonNull(floorReliefBlocks, "floorReliefBlocks");
            requireBoundedPositiveRange(maxDepthBlocksBelowSea, "maxDepthBlocksBelowSea", 256);
            if (maxDepthBlocksBelowSea.minimum() < 16) {
                throw new IllegalArgumentException("ocean basin maxDepthBlocksBelowSea must be at least 16");
            }
            requireBoundedPositiveRange(floorReliefBlocks, "floorReliefBlocks", 16);
        }
    }

    /**
     * V2-9-08 CONTINENTAL_SHELF foundation parameters: coast→shelf shallow depth belt.
     */
    public record ContinentalShelfParameters(
            IntRange shelfWidthBlocks,
            IntRange shelfDepthBlocksBelowSea,
            IntRange coastDistanceBandBlocks,
            Edge seawardSide
    ) implements FeatureParameters {
        public ContinentalShelfParameters {
            Objects.requireNonNull(shelfWidthBlocks, "shelfWidthBlocks");
            Objects.requireNonNull(shelfDepthBlocksBelowSea, "shelfDepthBlocksBelowSea");
            Objects.requireNonNull(coastDistanceBandBlocks, "coastDistanceBandBlocks");
            Objects.requireNonNull(seawardSide, "seawardSide");
            requireBoundedPositiveRange(shelfWidthBlocks, "shelfWidthBlocks", 128);
            if (shelfWidthBlocks.minimum() < 8) {
                throw new IllegalArgumentException("shelfWidthBlocks must be at least 8");
            }
            requireBoundedPositiveRange(shelfDepthBlocksBelowSea, "shelfDepthBlocksBelowSea", 64);
            if (shelfDepthBlocksBelowSea.minimum() < 2) {
                throw new IllegalArgumentException("shelfDepthBlocksBelowSea must be at least 2");
            }
            requireBoundedPositiveRange(coastDistanceBandBlocks, "coastDistanceBandBlocks", 32);
        }
    }

    /**
     * V2-9-08 CONTINENTAL_SLOPE foundation parameters: shelf→basin monotone depth belt.
     */
    public record ContinentalSlopeParameters(
            IntRange slopeWidthBlocks,
            IntRange upperDepthBlocksBelowSea,
            IntRange lowerDepthBlocksBelowSea,
            FixedRange maxGradient01
    ) implements FeatureParameters {
        public ContinentalSlopeParameters {
            Objects.requireNonNull(slopeWidthBlocks, "slopeWidthBlocks");
            Objects.requireNonNull(upperDepthBlocksBelowSea, "upperDepthBlocksBelowSea");
            Objects.requireNonNull(lowerDepthBlocksBelowSea, "lowerDepthBlocksBelowSea");
            Objects.requireNonNull(maxGradient01, "maxGradient01");
            requireBoundedPositiveRange(slopeWidthBlocks, "slopeWidthBlocks", 128);
            if (slopeWidthBlocks.minimum() < 8) {
                throw new IllegalArgumentException("slopeWidthBlocks must be at least 8");
            }
            requireBoundedPositiveRange(upperDepthBlocksBelowSea, "upperDepthBlocksBelowSea", 128);
            requireBoundedPositiveRange(lowerDepthBlocksBelowSea, "lowerDepthBlocksBelowSea", 256);
            if (lowerDepthBlocksBelowSea.minimum() <= upperDepthBlocksBelowSea.maximum()) {
                throw new IllegalArgumentException(
                        "continental slope lowerDepth must be strictly deeper than upperDepth");
            }
            requireUnitRange(maxGradient01, "maxGradient01");
            if (maxGradient01.minimumMillionths() < 10_000L) {
                throw new IllegalArgumentException("maxGradient01 minimum must be at least 0.01");
            }
        }
    }

    /**
     * V2-9-09 SUBMARINE_CANYON foundation parameters: bathymetric carve distinct from surface {@link CanyonParameters}.
     */
    public record SubmarineCanyonParameters(
            IntRange floorWidthBlocks,
            IntRange rimWidthBlocks,
            IntRange additionalCarveDepthBlocks,
            CanyonCrossSection crossSection,
            int terraceCount,
            int terraceWidthBlocks
    ) implements FeatureParameters {
        public SubmarineCanyonParameters {
            Objects.requireNonNull(floorWidthBlocks, "floorWidthBlocks");
            Objects.requireNonNull(rimWidthBlocks, "rimWidthBlocks");
            Objects.requireNonNull(additionalCarveDepthBlocks, "additionalCarveDepthBlocks");
            Objects.requireNonNull(crossSection, "crossSection");
            requireBoundedPositiveRange(floorWidthBlocks, "floorWidthBlocks", 64);
            if (floorWidthBlocks.minimum() < 2) {
                throw new IllegalArgumentException("floorWidthBlocks must be at least 2");
            }
            requireBoundedPositiveRange(rimWidthBlocks, "rimWidthBlocks", 256);
            requireBoundedPositiveRange(additionalCarveDepthBlocks, "additionalCarveDepthBlocks", 64);
            if (additionalCarveDepthBlocks.minimum() < 1) {
                throw new IllegalArgumentException("additionalCarveDepthBlocks must be at least 1");
            }
            if (rimWidthBlocks.minimum() < floorWidthBlocks.maximum() + 2) {
                throw new IllegalArgumentException(
                        "rimWidthBlocks must leave at least two blocks of wall outside the floor");
            }
            boolean terraced = crossSection == CanyonCrossSection.TERRACED_V
                    || crossSection == CanyonCrossSection.TERRACED_U;
            if (terraced) {
                if (terraceCount < 1 || terraceCount > 4
                        || terraceWidthBlocks < 1 || terraceWidthBlocks > 32) {
                    throw new IllegalArgumentException(
                            "terraced submarine canyon requires terraceCount 1..4 and terraceWidthBlocks 1..32");
                }
            } else if (terraceCount != 0 || terraceWidthBlocks != 0) {
                throw new IllegalArgumentException("non-terraced submarine canyon must not declare terraces");
            }
        }
    }

    /**
     * V2-9-10 CAVE_ENTRANCE surface-volume connector parameters. Opening sits below local surface
     * and approaches a host {@code CAVE_NETWORK} ENTRANCE node.
     */
    public record CaveEntranceParameters(
            int surfaceOffsetBlocks,
            int minimumOpeningBlocks,
            int approachLengthBlocks,
            int roofClearanceBlocks,
            String targetEntranceNodeId
    ) implements FeatureParameters {
        public CaveEntranceParameters {
            if (surfaceOffsetBlocks > -1 || surfaceOffsetBlocks < -64) {
                throw new IllegalArgumentException("surfaceOffsetBlocks must be in -64..-1");
            }
            if (minimumOpeningBlocks < 2 || minimumOpeningBlocks > 16) {
                throw new IllegalArgumentException("minimumOpeningBlocks must be in 2..16");
            }
            if (approachLengthBlocks < 2 || approachLengthBlocks > 32) {
                throw new IllegalArgumentException("approachLengthBlocks must be in 2..32");
            }
            if (roofClearanceBlocks < 1 || roofClearanceBlocks > 16) {
                throw new IllegalArgumentException("roofClearanceBlocks must be in 1..16");
            }
            targetEntranceNodeId = V2Validation.slug(targetEntranceNodeId, "targetEntranceNodeId");
        }
    }

    /**
     * V2-9-11 UNDERGROUND_RIVER parameters. Static channel carve plus single-owner ADD_FLUID
     * along a host cave path; fluidBodyId must match the outlet underground-lake owner.
     */
    public record UndergroundRiverParameters(
            IntRange channelRadiusBlocks,
            IntRange fluidDepthBlocks,
            int minimumAirPocketBlocks,
            String sourceCaveNodeId,
            String outletCaveNodeId,
            String fluidBodyId
    ) implements FeatureParameters {
        public UndergroundRiverParameters {
            Objects.requireNonNull(channelRadiusBlocks, "channelRadiusBlocks");
            Objects.requireNonNull(fluidDepthBlocks, "fluidDepthBlocks");
            requireBoundedPositiveRange(channelRadiusBlocks, "channelRadiusBlocks", 8);
            requireBoundedPositiveRange(fluidDepthBlocks, "fluidDepthBlocks", 16);
            if (minimumAirPocketBlocks < 1 || minimumAirPocketBlocks > 8) {
                throw new IllegalArgumentException("minimumAirPocketBlocks must be in 1..8");
            }
            sourceCaveNodeId = V2Validation.slug(sourceCaveNodeId, "sourceCaveNodeId");
            outletCaveNodeId = V2Validation.slug(outletCaveNodeId, "outletCaveNodeId");
            fluidBodyId = V2Validation.slug(fluidBodyId, "fluidBodyId");
            if (sourceCaveNodeId.equals(outletCaveNodeId)) {
                throw new IllegalArgumentException("sourceCaveNodeId and outletCaveNodeId must differ");
            }
        }
    }

    /**
     * V2-10-03 SINKHOLE surface collapse parameters. Static loss volume balances paired
     * {@code KARST_SPRING} discharge in the karst hydrology graph.
     */
    public record SinkholeParameters(
            IntRange collapseRadiusBlocks,
            IntRange roofClearanceBlocks,
            IntRange lossVolumeBlocks,
            String targetEntranceNodeId
    ) implements FeatureParameters {
        public SinkholeParameters {
            Objects.requireNonNull(collapseRadiusBlocks, "collapseRadiusBlocks");
            Objects.requireNonNull(roofClearanceBlocks, "roofClearanceBlocks");
            Objects.requireNonNull(lossVolumeBlocks, "lossVolumeBlocks");
            requireBoundedPositiveRange(collapseRadiusBlocks, "collapseRadiusBlocks", 16);
            if (collapseRadiusBlocks.minimum() < 2) {
                throw new IllegalArgumentException("collapseRadiusBlocks minimum must be >= 2");
            }
            requireBoundedPositiveRange(roofClearanceBlocks, "roofClearanceBlocks", 16);
            if (roofClearanceBlocks.minimum() < 1) {
                throw new IllegalArgumentException("roofClearanceBlocks minimum must be >= 1");
            }
            requireBoundedPositiveRange(lossVolumeBlocks, "lossVolumeBlocks", 1_000_000);
            targetEntranceNodeId = V2Validation.slug(targetEntranceNodeId, "targetEntranceNodeId");
        }
    }

    /**
     * V2-10-03 KARST_SPRING outlet parameters. Discharge must match paired sinkhole loss volume.
     */
    public record KarstSpringParameters(
            IntRange springDischargeBlocks,
            String outletHint
    ) implements FeatureParameters {
        public KarstSpringParameters {
            Objects.requireNonNull(springDischargeBlocks, "springDischargeBlocks");
            requireBoundedPositiveRange(springDischargeBlocks, "springDischargeBlocks", 1_000_000);
            if (outletHint == null || outletHint.isBlank()) {
                outletHint = "";
            } else {
                outletHint = V2Validation.slug(outletHint, "outletHint");
            }
        }
    }

    /**
     * V2-9-11 FLOODED_CAVE fluid-region hook parameters. Marks a static flooded region owned by
     * the same fluidBodyId as the underground river / lake.
     */
    public record FloodedCaveParameters(
            String fluidBodyId,
            int waterSurfaceYBlocks,
            String hostCaveFeatureIdHint
    ) implements FeatureParameters {
        public FloodedCaveParameters {
            fluidBodyId = V2Validation.slug(fluidBodyId, "fluidBodyId");
            if (waterSurfaceYBlocks < -512 || waterSurfaceYBlocks > 512) {
                throw new IllegalArgumentException("waterSurfaceYBlocks out of range");
            }
            if (hostCaveFeatureIdHint == null || hostCaveFeatureIdHint.isBlank()) {
                hostCaveFeatureIdHint = "";
            } else {
                hostCaveFeatureIdHint = V2Validation.slug(hostCaveFeatureIdHint, "hostCaveFeatureIdHint");
            }
        }
    }

    /**
     * V2-10-01 glacial ice parameters shared by {@code VALLEY_GLACIER}, {@code ICE_CAP},
     * and {@code ICE_SHEET}. Cold climate binding is required; meltwater handoff is optional.
     */
    public record GlacialIceParameters(
            IntRange thicknessBlocks,
            IntRange halfWidthBlocks,
            int flowAzimuthDegrees,
            String climatePreset,
            String meltwaterHandoffFeatureIdHint
    ) implements FeatureParameters {
        public GlacialIceParameters {
            Objects.requireNonNull(thicknessBlocks, "thicknessBlocks");
            Objects.requireNonNull(halfWidthBlocks, "halfWidthBlocks");
            requireBoundedPositiveRange(thicknessBlocks, "thicknessBlocks", 64);
            if (thicknessBlocks.minimum() < 2) {
                throw new IllegalArgumentException("thicknessBlocks minimum must be >= 2");
            }
            requireBoundedPositiveRange(halfWidthBlocks, "halfWidthBlocks", 128);
            if (halfWidthBlocks.minimum() < 2) {
                throw new IllegalArgumentException("halfWidthBlocks minimum must be >= 2");
            }
            if (flowAzimuthDegrees < 0 || flowAzimuthDegrees > 359) {
                throw new IllegalArgumentException("flowAzimuthDegrees must be in 0..359");
            }
            climatePreset = requiredSymbol(climatePreset, "climatePreset");
            if (!Set.of("COLD_ALPINE", "COLD_MARITIME", "COOL_HIGH_ALTITUDE").contains(climatePreset)) {
                throw new IllegalArgumentException("glacial ice climatePreset must be cold");
            }
            if (meltwaterHandoffFeatureIdHint == null || meltwaterHandoffFeatureIdHint.isBlank()) {
                meltwaterHandoffFeatureIdHint = "";
            } else {
                meltwaterHandoffFeatureIdHint = V2Validation.slug(
                        meltwaterHandoffFeatureIdHint, "meltwaterHandoffFeatureIdHint");
            }
        }
    }

    /** V2-10-02 moraine-field sediment ridge parameters. */
    public record MoraineFieldParameters(
            IntRange ridgeCount,
            IntRange ridgeHalfWidthBlocks,
            IntRange sedimentThicknessBlocks,
            int flowAzimuthDegrees
    ) implements FeatureParameters {
        public MoraineFieldParameters {
            Objects.requireNonNull(ridgeCount, "ridgeCount");
            Objects.requireNonNull(ridgeHalfWidthBlocks, "ridgeHalfWidthBlocks");
            Objects.requireNonNull(sedimentThicknessBlocks, "sedimentThicknessBlocks");
            requireBoundedPositiveRange(ridgeCount, "ridgeCount", 16);
            if (ridgeCount.minimum() < 1) {
                throw new IllegalArgumentException("ridgeCount minimum must be >= 1");
            }
            requireBoundedPositiveRange(ridgeHalfWidthBlocks, "ridgeHalfWidthBlocks", 32);
            if (ridgeHalfWidthBlocks.minimum() < 2) {
                throw new IllegalArgumentException("ridgeHalfWidthBlocks minimum must be >= 2");
            }
            requireBoundedPositiveRange(sedimentThicknessBlocks, "sedimentThicknessBlocks", 16);
            if (sedimentThicknessBlocks.minimum() < 1) {
                throw new IllegalArgumentException("sedimentThicknessBlocks minimum must be >= 1");
            }
            if (flowAzimuthDegrees < 0 || flowAzimuthDegrees > 359) {
                throw new IllegalArgumentException("flowAzimuthDegrees must be in 0..359");
            }
        }
    }

    /** V2-10-02 outwash-plain proglacial sediment/flow parameters. */
    public record OutwashPlainParameters(
            IntRange sedimentThicknessBlocks,
            IntRange channelSpacingBlocks,
            int flowAzimuthDegrees,
            String meltwaterHandoffFeatureIdHint
    ) implements FeatureParameters {
        public OutwashPlainParameters {
            Objects.requireNonNull(sedimentThicknessBlocks, "sedimentThicknessBlocks");
            Objects.requireNonNull(channelSpacingBlocks, "channelSpacingBlocks");
            requireBoundedPositiveRange(sedimentThicknessBlocks, "sedimentThicknessBlocks", 16);
            if (sedimentThicknessBlocks.minimum() < 1) {
                throw new IllegalArgumentException("sedimentThicknessBlocks minimum must be >= 1");
            }
            requireBoundedPositiveRange(channelSpacingBlocks, "channelSpacingBlocks", 64);
            if (channelSpacingBlocks.minimum() < 2) {
                throw new IllegalArgumentException("channelSpacingBlocks minimum must be >= 2");
            }
            if (flowAzimuthDegrees < 0 || flowAzimuthDegrees > 359) {
                throw new IllegalArgumentException("flowAzimuthDegrees must be in 0..359");
            }
            if (meltwaterHandoffFeatureIdHint == null || meltwaterHandoffFeatureIdHint.isBlank()) {
                meltwaterHandoffFeatureIdHint = "";
            } else {
                meltwaterHandoffFeatureIdHint = V2Validation.slug(
                        meltwaterHandoffFeatureIdHint, "meltwaterHandoffFeatureIdHint");
            }
        }
    }

    /**
     * V2-10-10 surface SPRING parameters. Groundwater outflow node bound to a general RIVER source;
     * distinct from {@code KARST_SPRING} limestone karst graph ownership.
     */
    public record SpringParameters(
            IntRange outflowRadiusBlocks,
            IntRange dischargeBlocks,
            IntRange supportRadiusBlocks
    ) implements FeatureParameters {
        public SpringParameters {
            Objects.requireNonNull(outflowRadiusBlocks, "outflowRadiusBlocks");
            Objects.requireNonNull(dischargeBlocks, "dischargeBlocks");
            Objects.requireNonNull(supportRadiusBlocks, "supportRadiusBlocks");
            requireBoundedPositiveRange(outflowRadiusBlocks, "outflowRadiusBlocks", 16);
            if (outflowRadiusBlocks.minimum() < 1) {
                throw new IllegalArgumentException("outflowRadiusBlocks minimum must be >= 1");
            }
            requireBoundedPositiveRange(dischargeBlocks, "dischargeBlocks", 1_000_000);
            requireBoundedPositiveRange(supportRadiusBlocks, "supportRadiusBlocks", 32);
            if (supportRadiusBlocks.minimum() < 2) {
                throw new IllegalArgumentException("supportRadiusBlocks minimum must be >= 2");
            }
        }
    }

    /**
     * V2-10-11 OXBOW_LAKE parameters. Reach-cutoff stagnant basin with wetland handoff;
     * distinct from open-spill {@code LAKE} and barrier {@code DAM_RESERVOIR}.
     */
    public record OxbowLakeParameters(
            IntRange targetDepthBlocks,
            int shoreWidthBlocks,
            IntRange wetlandHandoffWidthBlocks,
            IntRange supportRadiusBlocks,
            String cutoffReachIdHint
    ) implements FeatureParameters {
        public OxbowLakeParameters {
            Objects.requireNonNull(targetDepthBlocks, "targetDepthBlocks");
            Objects.requireNonNull(wetlandHandoffWidthBlocks, "wetlandHandoffWidthBlocks");
            Objects.requireNonNull(supportRadiusBlocks, "supportRadiusBlocks");
            requireBoundedPositiveRange(targetDepthBlocks, "targetDepthBlocks", 32);
            if (targetDepthBlocks.minimum() < 1) {
                throw new IllegalArgumentException("targetDepthBlocks minimum must be >= 1");
            }
            if (shoreWidthBlocks < 1 || shoreWidthBlocks > 16) {
                throw new IllegalArgumentException("shoreWidthBlocks must be in 1..16");
            }
            requireBoundedPositiveRange(wetlandHandoffWidthBlocks, "wetlandHandoffWidthBlocks", 64);
            if (wetlandHandoffWidthBlocks.minimum() < shoreWidthBlocks) {
                throw new IllegalArgumentException("wetlandHandoffWidthBlocks minimum must be >= shoreWidthBlocks");
            }
            requireBoundedPositiveRange(supportRadiusBlocks, "supportRadiusBlocks", 64);
            if (supportRadiusBlocks.minimum() < 4) {
                throw new IllegalArgumentException("supportRadiusBlocks minimum must be >= 4");
            }
            if (cutoffReachIdHint == null || cutoffReachIdHint.isBlank()) {
                cutoffReachIdHint = "";
            } else {
                cutoffReachIdHint = V2Validation.slug(cutoffReachIdHint, "cutoffReachIdHint");
            }
        }
    }

    /**
     * V2-10-07 LAVA_TUBE parameters. Swept tunnel carve within a volcanic cone with roof/support
     * clearance; provenance binds to caldera or lava-flow child features only.
     */
    public record LavaTubeParameters(
            IntRange tubeRadiusBlocks,
            IntRange roofClearanceBlocks,
            IntRange supportRadiusBlocks,
            int entranceOffsetBlocks
    ) implements FeatureParameters {
        public LavaTubeParameters {
            Objects.requireNonNull(tubeRadiusBlocks, "tubeRadiusBlocks");
            Objects.requireNonNull(roofClearanceBlocks, "roofClearanceBlocks");
            Objects.requireNonNull(supportRadiusBlocks, "supportRadiusBlocks");
            requireBoundedPositiveRange(tubeRadiusBlocks, "tubeRadiusBlocks", 8);
            if (tubeRadiusBlocks.minimum() < 2) {
                throw new IllegalArgumentException("tubeRadiusBlocks minimum must be >= 2");
            }
            requireBoundedPositiveRange(roofClearanceBlocks, "roofClearanceBlocks", 16);
            if (roofClearanceBlocks.minimum() < 2) {
                throw new IllegalArgumentException("roofClearanceBlocks minimum must be >= 2");
            }
            requireBoundedPositiveRange(supportRadiusBlocks, "supportRadiusBlocks", 16);
            if (supportRadiusBlocks.minimum() < 2) {
                throw new IllegalArgumentException("supportRadiusBlocks minimum must be >= 2");
            }
            if (entranceOffsetBlocks < 0 || entranceOffsetBlocks > 32) {
                throw new IllegalArgumentException("entranceOffsetBlocks must be in 0..32");
            }
        }

        public LavaTubeParameters(
                IntRange tubeRadiusBlocks,
                IntRange roofClearanceBlocks,
                IntRange supportRadiusBlocks
        ) {
            this(tubeRadiusBlocks, roofClearanceBlocks, supportRadiusBlocks, 0);
        }
    }

    /** V2-10-06 ESCARPMENT foundation parameters: scarp face, talus, floor drop, and plateau transition. */
    public record EscarpmentParameters(
            IntRange scarpHeightBlocks,
            IntRange talusWidthBlocks,
            IntRange floorDropBlocks,
            Edge dropSide,
            int plateauTransitionBandBlocks
    ) implements FeatureParameters {
        public EscarpmentParameters {
            Objects.requireNonNull(scarpHeightBlocks, "scarpHeightBlocks");
            Objects.requireNonNull(talusWidthBlocks, "talusWidthBlocks");
            Objects.requireNonNull(floorDropBlocks, "floorDropBlocks");
            Objects.requireNonNull(dropSide, "dropSide");
            requireBoundedPositiveRange(scarpHeightBlocks, "scarpHeightBlocks", 128);
            if (scarpHeightBlocks.minimum() < 4) {
                throw new IllegalArgumentException("scarpHeightBlocks minimum must be >= 4");
            }
            requireBoundedPositiveRange(talusWidthBlocks, "talusWidthBlocks", 64);
            if (talusWidthBlocks.minimum() < 2) {
                throw new IllegalArgumentException("talusWidthBlocks minimum must be >= 2");
            }
            requireBoundedPositiveRange(floorDropBlocks, "floorDropBlocks", 128);
            if (floorDropBlocks.minimum() < 4) {
                throw new IllegalArgumentException("floorDropBlocks minimum must be >= 4");
            }
            if (plateauTransitionBandBlocks < 2 || plateauTransitionBandBlocks > 32) {
                throw new IllegalArgumentException("plateauTransitionBandBlocks outside 2..32");
            }
        }
    }

    /** V2-10-06 PLATEAU foundation parameters: cap elevation/relief and escarpment transition band. */
    public record PlateauParameters(
            IntRange capElevationBlocks,
            IntRange capReliefBlocks,
            PlateauProfile profile,
            int escarpmentTransitionBandBlocks
    ) implements FeatureParameters {
        public PlateauParameters {
            Objects.requireNonNull(capElevationBlocks, "capElevationBlocks");
            Objects.requireNonNull(capReliefBlocks, "capReliefBlocks");
            Objects.requireNonNull(profile, "profile");
            requireBoundedPositiveRange(capElevationBlocks, "capElevationBlocks", 96);
            if (capElevationBlocks.minimum() < 8) {
                throw new IllegalArgumentException("capElevationBlocks minimum must be >= 8");
            }
            if (capReliefBlocks.minimum() < 0 || capReliefBlocks.maximum() > 8
                    || capReliefBlocks.minimum() > capReliefBlocks.maximum()) {
                throw new IllegalArgumentException("capReliefBlocks outside 0..8");
            }
            if (escarpmentTransitionBandBlocks < 2 || escarpmentTransitionBandBlocks > 32) {
                throw new IllegalArgumentException("escarpmentTransitionBandBlocks outside 2..32");
            }
        }
    }

    public record MeanderingRiverParameters(
            IntRange bankfullWidthBlocks,
            DischargeClass dischargeClass,
            long minimumBedSlopeMillionths,
            RiverVariant variant
    ) implements FeatureParameters {
        public MeanderingRiverParameters {
            Objects.requireNonNull(bankfullWidthBlocks, "bankfullWidthBlocks");
            Objects.requireNonNull(dischargeClass, "dischargeClass");
            Objects.requireNonNull(variant, "variant");
            requireBoundedPositiveRange(bankfullWidthBlocks, "bankfullWidthBlocks", 64);
            if (minimumBedSlopeMillionths < 1 || minimumBedSlopeMillionths > FIXED_SCALE) {
                throw new IllegalArgumentException("minimumBedSlope01 outside (0..1]");
            }
            if (variant == RiverVariant.MEANDERING_RIVER && bankfullWidthBlocks.maximum() < 2) {
                throw new IllegalArgumentException("meandering river requires bankfull width at least 2");
            }
        }
    }

    /**
     * V2-9-04 general RIVER foundation parameters. Distinct from legacy
     * {@link MeanderingRiverParameters}; MEANDERING_RIVER serialization remains unchanged.
     */
    public record RiverParameters(
            IntRange bankfullWidthBlocks,
            DischargeClass dischargeClass,
            long minimumBedSlopeMillionths,
            IntRange floodplainHandoffWidthBlocks,
            int maxReachCount,
            int maxNodeDegree
    ) implements FeatureParameters {
        public RiverParameters {
            Objects.requireNonNull(bankfullWidthBlocks, "bankfullWidthBlocks");
            Objects.requireNonNull(dischargeClass, "dischargeClass");
            Objects.requireNonNull(floodplainHandoffWidthBlocks, "floodplainHandoffWidthBlocks");
            requireBoundedPositiveRange(bankfullWidthBlocks, "bankfullWidthBlocks", 64);
            if (bankfullWidthBlocks.minimum() < 2) {
                throw new IllegalArgumentException("river bankfullWidthBlocks must be at least 2");
            }
            if (minimumBedSlopeMillionths < 1 || minimumBedSlopeMillionths > FIXED_SCALE) {
                throw new IllegalArgumentException("minimumBedSlope01 outside (0..1]");
            }
            requireBoundedPositiveRange(floodplainHandoffWidthBlocks, "floodplainHandoffWidthBlocks", 128);
            if (floodplainHandoffWidthBlocks.minimum() < bankfullWidthBlocks.minimum()) {
                throw new IllegalArgumentException("floodplain handoff must be at least bankfull width");
            }
            if (maxReachCount < 1 || maxReachCount > 8) {
                throw new IllegalArgumentException("maxReachCount outside 1..8");
            }
            if (maxNodeDegree < 1 || maxNodeDegree > 4) {
                throw new IllegalArgumentException("maxNodeDegree outside 1..4");
            }
        }
    }

    /**
     * V2-9-05 FLOODPLAIN foundation parameters with river adjacency and groundwater handoff.
     * Distinct from river-owned {@code foundation.river.floodplain-mask}.
     */
    public record FloodplainParameters(
            IntRange riverAdjacencyBandBlocks,
            IntRange groundwaterHandoffDepthBlocks,
            IntRange microReliefBlocks
    ) implements FeatureParameters {
        public FloodplainParameters {
            Objects.requireNonNull(riverAdjacencyBandBlocks, "riverAdjacencyBandBlocks");
            Objects.requireNonNull(groundwaterHandoffDepthBlocks, "groundwaterHandoffDepthBlocks");
            Objects.requireNonNull(microReliefBlocks, "microReliefBlocks");
            requireBoundedPositiveRange(riverAdjacencyBandBlocks, "riverAdjacencyBandBlocks", 32);
            requireBoundedPositiveRange(groundwaterHandoffDepthBlocks, "groundwaterHandoffDepthBlocks", 32);
            requireBoundedPositiveRange(microReliefBlocks, "microReliefBlocks", 8);
            if (microReliefBlocks.minimum() < 1) {
                throw new IllegalArgumentException("floodplain microReliefBlocks must be at least 1");
            }
        }
    }

    /**
     * V2-9-05 MARSH foundation parameters with hydroperiod／wetness／open-water.
     * MANGROVE_WETLAND remains a separate kind and is not rewritten here.
     */
    public record MarshParameters(
            IntRange hydroperiodBlocks,
            FixedRange wetness01,
            FixedRange openWaterShare01,
            IntRange microReliefBlocks,
            IntRange groundwaterMinDepthBlocks
    ) implements FeatureParameters {
        public MarshParameters {
            Objects.requireNonNull(hydroperiodBlocks, "hydroperiodBlocks");
            Objects.requireNonNull(wetness01, "wetness01");
            Objects.requireNonNull(openWaterShare01, "openWaterShare01");
            Objects.requireNonNull(microReliefBlocks, "microReliefBlocks");
            Objects.requireNonNull(groundwaterMinDepthBlocks, "groundwaterMinDepthBlocks");
            requireBoundedPositiveRange(hydroperiodBlocks, "hydroperiodBlocks", 64);
            if (hydroperiodBlocks.minimum() < 1) {
                throw new IllegalArgumentException("marsh hydroperiodBlocks must be at least 1");
            }
            requireUnitRange(wetness01, "wetness01");
            if (wetness01.minimumMillionths() < 200_000L) {
                throw new IllegalArgumentException("marsh wetness01 minimum must be at least 0.2");
            }
            requireUnitRange(openWaterShare01, "openWaterShare01");
            if (openWaterShare01.maximumMillionths() > 700_000L) {
                throw new IllegalArgumentException("marsh openWaterShare01 maximum must be at most 0.7");
            }
            requireBoundedPositiveRange(microReliefBlocks, "microReliefBlocks", 8);
            if (microReliefBlocks.minimum() < 1) {
                throw new IllegalArgumentException("marsh microReliefBlocks must be at least 1");
            }
            requireBoundedPositiveRange(groundwaterMinDepthBlocks, "groundwaterMinDepthBlocks", 32);
        }
    }

    public record BackshorePlainsParameters(IntRange elevationAboveWaterBlocks, FixedRange grassCover01)
            implements FeatureParameters {
        public BackshorePlainsParameters {
            Objects.requireNonNull(elevationAboveWaterBlocks, "elevationAboveWaterBlocks");
            requireNonNegativeRange(elevationAboveWaterBlocks, "elevationAboveWaterBlocks");
            requireUnitRange(grassCover01, "grassCover01");
        }
    }

    /**
     * V2-9-02 PLAIN foundation parameters. Distinct from legacy {@link BackshorePlainsParameters};
     * BACKSHORE_PLAINS remains a diagnostic alias candidate and is not deleted.
     */
    public record PlainParameters(
            IntRange baseElevationAboveDatumBlocks,
            IntRange microReliefBlocks,
            IntRange groundwaterHandoffDepthBlocks
    ) implements FeatureParameters {
        public PlainParameters {
            Objects.requireNonNull(baseElevationAboveDatumBlocks, "baseElevationAboveDatumBlocks");
            Objects.requireNonNull(microReliefBlocks, "microReliefBlocks");
            Objects.requireNonNull(groundwaterHandoffDepthBlocks, "groundwaterHandoffDepthBlocks");
            requireNonNegativeRange(baseElevationAboveDatumBlocks, "baseElevationAboveDatumBlocks");
            requireBoundedPositiveRange(microReliefBlocks, "microReliefBlocks", 8);
            if (microReliefBlocks.minimum() < 1) {
                throw new IllegalArgumentException("plain microReliefBlocks must be at least 1");
            }
            requireBoundedPositiveRange(groundwaterHandoffDepthBlocks, "groundwaterHandoffDepthBlocks", 32);
        }
    }

    /** V2-9-02 HILL_RANGE foundation parameters with closed ridge/saddle budgets. */
    public record HillRangeParameters(
            IntRange ridgeHalfWidthBlocks,
            IntRange maxReliefBlocks,
            IntRange ridgeStationCount,
            IntRange saddleCount,
            int plainTransitionBandBlocks
    ) implements FeatureParameters {
        public HillRangeParameters {
            Objects.requireNonNull(ridgeHalfWidthBlocks, "ridgeHalfWidthBlocks");
            Objects.requireNonNull(maxReliefBlocks, "maxReliefBlocks");
            Objects.requireNonNull(ridgeStationCount, "ridgeStationCount");
            Objects.requireNonNull(saddleCount, "saddleCount");
            requireBoundedPositiveRange(ridgeHalfWidthBlocks, "ridgeHalfWidthBlocks", 32);
            requireBoundedPositiveRange(maxReliefBlocks, "maxReliefBlocks", 64);
            requireBoundedPositiveRange(ridgeStationCount, "ridgeStationCount", 8);
            if (ridgeStationCount.minimum() < 2) {
                throw new IllegalArgumentException("hill ridgeStationCount must be at least 2");
            }
            requireBoundedPositiveRange(saddleCount, "saddleCount", 7);
            if (saddleCount.maximum() > ridgeStationCount.maximum() - 1) {
                throw new IllegalArgumentException("hill saddleCount must fit under ridge stations");
            }
            if (plainTransitionBandBlocks < 1 || plainTransitionBandBlocks > 32) {
                throw new IllegalArgumentException("plainTransitionBandBlocks outside 1..32");
            }
        }
    }

    /**
     * V2-9-03 general MOUNTAIN_RANGE foundation parameters. Distinct from specialized
     * {@link MountainParameters} used by ALPINE/GLACIAL modules; does not reinterpret those seeds.
     */
    public record MountainRangeParameters(
            IntRange peakCount,
            IntRange ridgeHalfWidthBlocks,
            IntRange maxReliefBlocks,
            IntRange saddleCount,
            int spurCount,
            int passCount,
            int foothillBandBlocks,
            int valleyTransitionBandBlocks
    ) implements FeatureParameters {
        public MountainRangeParameters {
            Objects.requireNonNull(peakCount, "peakCount");
            Objects.requireNonNull(ridgeHalfWidthBlocks, "ridgeHalfWidthBlocks");
            Objects.requireNonNull(maxReliefBlocks, "maxReliefBlocks");
            Objects.requireNonNull(saddleCount, "saddleCount");
            requireBoundedPositiveRange(peakCount, "peakCount", 8);
            if (peakCount.minimum() < 2) {
                throw new IllegalArgumentException("mountain range peakCount must be at least 2");
            }
            requireBoundedPositiveRange(ridgeHalfWidthBlocks, "ridgeHalfWidthBlocks", 64);
            if (ridgeHalfWidthBlocks.minimum() < 4) {
                throw new IllegalArgumentException("mountain range ridgeHalfWidthBlocks must be at least 4");
            }
            requireBoundedPositiveRange(maxReliefBlocks, "maxReliefBlocks", 128);
            if (maxReliefBlocks.minimum() < 16) {
                throw new IllegalArgumentException("mountain range maxReliefBlocks must be at least 16");
            }
            requireBoundedPositiveRange(saddleCount, "saddleCount", 7);
            if (saddleCount.maximum() > peakCount.maximum() - 1) {
                throw new IllegalArgumentException("mountain range saddleCount must fit under peaks");
            }
            if (spurCount < 0 || spurCount > 8) {
                throw new IllegalArgumentException("mountain range spurCount outside 0..8");
            }
            if (passCount < 0 || passCount > peakCount.maximum() - 1) {
                throw new IllegalArgumentException("mountain range passCount outside 0..peakCount-1");
            }
            if (foothillBandBlocks < 1 || foothillBandBlocks > 32) {
                throw new IllegalArgumentException("foothillBandBlocks outside 1..32");
            }
            if (valleyTransitionBandBlocks < 1 || valleyTransitionBandBlocks > 32) {
                throw new IllegalArgumentException("valleyTransitionBandBlocks outside 1..32");
            }
        }
    }

    /**
     * V2-9-03 general VALLEY foundation parameters with V/U cross-section and connection role.
     * Specialized {@link FjordParameters} remain unchanged.
     */
    public record ValleyParameters(
            ValleyCrossSection crossSection,
            IntRange floorHalfWidthBlocks,
            IntRange shoulderWidthBlocks,
            IntRange maxDepthBlocks,
            int mountainTransitionBandBlocks,
            ValleyConnectionRole connectionRole
    ) implements FeatureParameters {
        public ValleyParameters {
            Objects.requireNonNull(crossSection, "crossSection");
            Objects.requireNonNull(floorHalfWidthBlocks, "floorHalfWidthBlocks");
            Objects.requireNonNull(shoulderWidthBlocks, "shoulderWidthBlocks");
            Objects.requireNonNull(maxDepthBlocks, "maxDepthBlocks");
            Objects.requireNonNull(connectionRole, "connectionRole");
            requireBoundedPositiveRange(floorHalfWidthBlocks, "floorHalfWidthBlocks", 64);
            if (floorHalfWidthBlocks.minimum() < 2) {
                throw new IllegalArgumentException("valley floorHalfWidthBlocks must be at least 2");
            }
            requireBoundedPositiveRange(shoulderWidthBlocks, "shoulderWidthBlocks", 32);
            requireBoundedPositiveRange(maxDepthBlocks, "maxDepthBlocks", 64);
            if (maxDepthBlocks.minimum() < 4) {
                throw new IllegalArgumentException("valley maxDepthBlocks must be at least 4");
            }
            if (mountainTransitionBandBlocks < 1 || mountainTransitionBandBlocks > 32) {
                throw new IllegalArgumentException("mountainTransitionBandBlocks outside 1..32");
            }
        }
    }

    /** V2-4-09 parameters for bounded mangrove wetland micro-relief and hydroperiod gaps. */
    public record MangroveWetlandParameters(
            IntRange microReliefBlocks,
            FixedRange waterloggedShare01
    ) implements FeatureParameters {
        public MangroveWetlandParameters {
            Objects.requireNonNull(microReliefBlocks, "microReliefBlocks");
            Objects.requireNonNull(waterloggedShare01, "waterloggedShare01");
            requireBoundedPositiveRange(microReliefBlocks, "microReliefBlocks", 4);
            if (microReliefBlocks.minimum() < 1) {
                throw new IllegalArgumentException("microReliefBlocks must be at least 1");
            }
            if (waterloggedShare01.minimumMillionths() < 100_000L
                    || waterloggedShare01.maximumMillionths() > 900_000L) {
                throw new IllegalArgumentException("waterloggedShare01 outside 0.1..0.9");
            }
        }
    }

    /** V2-4-10 parameters for coral reef crest, width, and outer slope bathymetry. */
    public record CoralReefParameters(
            IntRange reefCrestDepthBlocks,
            IntRange reefWidthBlocks,
            IntRange outerSlopeDegrees
    ) implements FeatureParameters {
        public CoralReefParameters {
            Objects.requireNonNull(reefCrestDepthBlocks, "reefCrestDepthBlocks");
            Objects.requireNonNull(reefWidthBlocks, "reefWidthBlocks");
            Objects.requireNonNull(outerSlopeDegrees, "outerSlopeDegrees");
            requireBoundedPositiveRange(reefCrestDepthBlocks, "reefCrestDepthBlocks", 4);
            requireBoundedPositiveRange(reefWidthBlocks, "reefWidthBlocks", 46);
            if (reefCrestDepthBlocks.minimum() < 1 || reefWidthBlocks.minimum() < 18
                    || outerSlopeDegrees.minimum() < 18 || outerSlopeDegrees.maximum() > 42) {
                throw new IllegalArgumentException("coral reef parameter range is invalid");
            }
        }
    }

    /** V2-4-10 parameters for enclosed lagoon bathymetry linked to a parent CORAL_REEF. */
    public record LagoonParameters(IntRange depthBlocks) implements FeatureParameters {
        public LagoonParameters {
            Objects.requireNonNull(depthBlocks, "depthBlocks");
            requireBoundedPositiveRange(depthBlocks, "depthBlocks", 14);
            if (depthBlocks.minimum() < 5) {
                throw new IllegalArgumentException("lagoon depthBlocks must be at least 5");
            }
        }
    }

    /** V2-4-10 parameters for a navigable reef pass corridor. */
    public record ReefPassParameters(
            IntRange widthBlocks,
            IntRange waterDepthBlocks
    ) implements FeatureParameters {
        public ReefPassParameters {
            Objects.requireNonNull(widthBlocks, "widthBlocks");
            Objects.requireNonNull(waterDepthBlocks, "waterDepthBlocks");
            requireBoundedPositiveRange(widthBlocks, "widthBlocks", 14);
            requireBoundedPositiveRange(waterDepthBlocks, "waterDepthBlocks", 7);
            if (widthBlocks.minimum() < 10 || waterDepthBlocks.minimum() < 4) {
                throw new IllegalArgumentException("reef pass parameter range is invalid");
            }
        }
    }

    public record NoParameters() implements FeatureParameters { }

    public record Feature(
            String id,
            FeatureKind kind,
            Geometry geometry,
            FeatureParameters parameters,
            int priority,
            Provenance provenance
    ) {
        public Feature {
            id = V2Validation.slug(id, "feature id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(provenance, "provenance");
            if (priority < -100 || priority > 100) throw new IllegalArgumentException("priority outside -100..100");
            validateParameterType(kind, parameters);
            validateGeometryType(kind, geometry.type());
        }
    }

    public record TransitionPolicy(int transitionVersion, TransitionProfile profile, int bandBlocks) {
        public static final int VERSION = 1;
        public static final TransitionPolicy NONE = new TransitionPolicy(VERSION, TransitionProfile.NONE, 0);

        public TransitionPolicy {
            if (transitionVersion != VERSION) {
                throw new IllegalArgumentException("transitionVersion must be exactly 1");
            }
            Objects.requireNonNull(profile, "profile");
            if ((profile == TransitionProfile.NONE && bandBlocks != 0)
                    || (profile == TransitionProfile.PRIORITY_BLEND && (bandBlocks < 1 || bandBlocks > 32))) {
                throw new IllegalArgumentException("transition band does not match profile");
            }
        }
    }

    public record Relation(
            String id,
            RelationKind kind,
            String from,
            String to,
            Strength strength,
            TransitionPolicy transition
    ) {
        public Relation(String id, RelationKind kind, String from, String to, Strength strength) {
            this(id, kind, from, to, strength, TransitionPolicy.NONE);
        }

        public Relation {
            id = V2Validation.slug(id, "relation id");
            Objects.requireNonNull(kind, "kind");
            from = V2Validation.nonBlank(from, "from", 96);
            to = V2Validation.nonBlank(to, "to", 96);
            Objects.requireNonNull(strength, "strength");
            Objects.requireNonNull(transition, "transition");
            if (from.equals(to)) throw new IllegalArgumentException("relation must not self-reference");
            if (transition.profile() != TransitionProfile.NONE
                    && kind != RelationKind.ADJACENT_TO && kind != RelationKind.OVERLAPS) {
                throw new IllegalArgumentException("transition policy requires ADJACENT_TO or OVERLAPS");
            }
            if (kind.symmetric() && from.compareTo(to) > 0) {
                String swap = from;
                from = to;
                to = swap;
            }
        }

        void validateEndpointTypes() {
            boolean fromFeature = from.startsWith("feature:");
            boolean toFeature = to.startsWith("feature:");
            boolean toBoundary = to.startsWith("boundary:");
            if (!fromFeature) throw new IllegalArgumentException("relation source must be a feature: " + id);
            if (kind == RelationKind.EMPTIES_INTO && !toBoundary) throw new IllegalArgumentException("EMPTIES_INTO must target a boundary");
            if (kind != RelationKind.EMPTIES_INTO && kind != RelationKind.DRAINS_TO && !toFeature) throw new IllegalArgumentException(kind + " must target a feature");
            if (kind == RelationKind.DRAINS_TO && !toFeature && !toBoundary) throw new IllegalArgumentException("DRAINS_TO target is invalid");
        }
    }

    public sealed interface Constraint permits MetricRangeConstraint, EdgeClassificationConstraint {
        String id();
        Strength strength();
        String subject();
        int weightMillionths();
    }

    public record MetricRangeConstraint(
            String id,
            Strength strength,
            String subject,
            String metric,
            FixedRange range,
            long toleranceMillionths,
            int weightMillionths
    ) implements Constraint {
        public MetricRangeConstraint {
            id = V2Validation.slug(id, "constraint id");
            Objects.requireNonNull(strength, "strength");
            subject = V2Validation.nonBlank(subject, "subject", 96);
            metric = requiredSymbol(metric, "metric");
            Objects.requireNonNull(range, "range");
            if (toleranceMillionths < 0) throw new IllegalArgumentException("tolerance must be non-negative");
            validateWeight(strength, weightMillionths);
        }
    }

    public record EdgeClassificationConstraint(
            String id,
            Strength strength,
            String subject,
            Edge edge,
            EdgeClassification classification,
            int minimumShareMillionths,
            int weightMillionths
    ) implements Constraint {
        public EdgeClassificationConstraint {
            id = V2Validation.slug(id, "constraint id");
            Objects.requireNonNull(strength, "strength");
            subject = V2Validation.nonBlank(subject, "subject", 96);
            Objects.requireNonNull(edge, "edge");
            Objects.requireNonNull(classification, "classification");
            if (!"world".equals(subject)) throw new IllegalArgumentException("edge classification subject must be world");
            if (minimumShareMillionths < 0 || minimumShareMillionths > FIXED_SCALE) throw new IllegalArgumentException("minimum share outside 0..1");
            validateWeight(strength, weightMillionths);
        }
    }

    public record EnvironmentDescriptor(String geologyPreset, String climatePreset, String ecologyPreset) {
        public EnvironmentDescriptor {
            geologyPreset = optionalSymbol(geologyPreset, "geologyPreset");
            climatePreset = optionalSymbol(climatePreset, "climatePreset");
            ecologyPreset = optionalSymbol(ecologyPreset, "ecologyPreset");
        }
    }

    public record ConstraintMapBinding(
            String id,
            String sourceId,
            ConstraintMapRole role,
            String artifactId,
            Strength strength,
            Sampling sampling,
            int toleranceBlocks,
            int weightMillionths
    ) {
        public ConstraintMapBinding {
            id = V2Validation.slug(id, "map reference id");
            sourceId = V2Validation.nonBlank(sourceId, "sourceId", 96);
            artifactId = V2Validation.nonBlank(artifactId, "artifactId", 192);
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(strength, "strength");
            Objects.requireNonNull(sampling, "sampling");
            if (!SOURCE_REFERENCE.matcher(sourceId).matches() || !ARTIFACT_REFERENCE.matcher(artifactId).matches()) {
                throw new IllegalArgumentException("map binding must use canonical artifact references");
            }
            String expectedArtifactPrefix = switch (role) {
                case LAND_WATER_MASK -> "constraint:land-water:sha256-";
                case HEIGHT_GUIDE -> "constraint:height-guide:sha256-";
                case ZONE_LABEL_MAP -> "constraint:zone-label-map:sha256-";
            };
            if (!artifactId.startsWith(expectedArtifactPrefix)) {
                throw new IllegalArgumentException("map artifact semantic does not match binding role");
            }
            if (toleranceBlocks < 0 || toleranceBlocks > 1_000) throw new IllegalArgumentException("toleranceBlocks outside range");
            if ((role == ConstraintMapRole.LAND_WATER_MASK || role == ConstraintMapRole.ZONE_LABEL_MAP)
                    && sampling != Sampling.NEAREST) {
                throw new IllegalArgumentException("categorical constraint maps require NEAREST sampling");
            }
            if (role == ConstraintMapRole.LAND_WATER_MASK && strength == Strength.HARD
                    && toleranceBlocks != 0) {
                throw new IllegalArgumentException("HARD LAND_WATER_MASK requires zero tolerance");
            }
            if (role == ConstraintMapRole.ZONE_LABEL_MAP && toleranceBlocks != 0) {
                throw new IllegalArgumentException("ZONE_LABEL_MAP requires zero tolerance");
            }
            validateWeight(strength, weightMillionths);
        }
    }

    public record StructureRequest(String id, StructureKind kind, int count, String preferredFeatureId) {
        public StructureRequest {
            id = V2Validation.slug(id, "structure id");
            Objects.requireNonNull(kind, "kind");
            if (count < 1 || count > 64) throw new IllegalArgumentException("structure count outside 1..64");
            preferredFeatureId = V2Validation.slug(preferredFeatureId, "preferredFeatureId");
        }
    }

    public record Provenance(
            ProvenanceSource source,
            String sourceId,
            int confidenceMillionths,
            ConfirmationState confirmationState
    ) {
        public Provenance {
            Objects.requireNonNull(source, "source");
            sourceId = V2Validation.slug(sourceId, "provenance sourceId");
            if (confidenceMillionths < 0 || confidenceMillionths > FIXED_SCALE) throw new IllegalArgumentException("confidence outside 0..1");
            Objects.requireNonNull(confirmationState, "confirmationState");
        }

        public static Provenance confirmedManual(String sourceId) {
            return new Provenance(ProvenanceSource.MANUAL, sourceId, FIXED_SCALE, ConfirmationState.CONFIRMED);
        }
    }

    private static void validateParameterType(FeatureKind kind, FeatureParameters parameters) {
        boolean valid = switch (kind) {
            case SANDY_BEACH -> parameters instanceof SandyBeachParameters;
            case BREAKWATER_HARBOR -> parameters instanceof BreakwaterHarborParameters;
            case HARBOR_BASIN -> parameters instanceof HarborBasinParameters;
            case ROCKY_CAPE -> parameters instanceof RockyCapeParameters;
            case ROCKY_COAST -> parameters instanceof RockyCoastParameters;
            case BACKSHORE_PLAINS -> parameters instanceof BackshorePlainsParameters;
            case PLAIN -> parameters instanceof PlainParameters;
            case HILL_RANGE -> parameters instanceof HillRangeParameters;
            case MOUNTAIN_RANGE -> parameters instanceof MountainRangeParameters;
            case VALLEY -> parameters instanceof ValleyParameters;
            case RIVER -> parameters instanceof RiverParameters;
            case FLOODPLAIN -> parameters instanceof FloodplainParameters;
            case MARSH -> parameters instanceof MarshParameters;
            case SEA_CLIFF -> parameters instanceof SeaCliffParameters;
            case MEANDERING_RIVER -> parameters instanceof MeanderingRiverParameters;
            case LAKE -> parameters instanceof LakeParameters;
            case CANYON -> parameters instanceof CanyonParameters;
            case WATERFALL -> parameters instanceof WaterfallParameters;
            case DELTA -> parameters instanceof DeltaParameters;
            case TIDAL_CHANNEL_NETWORK -> parameters instanceof TidalChannelParameters;
            case FJORD -> parameters instanceof FjordParameters;
            case MANGROVE_WETLAND -> parameters instanceof MangroveWetlandParameters;
            case CORAL_REEF -> parameters instanceof CoralReefParameters;
            case LAGOON -> parameters instanceof LagoonParameters;
            case REEF_PASS -> parameters instanceof ReefPassParameters;
            case ALPINE_MOUNTAIN_RANGE, GLACIAL_MOUNTAIN_RANGE -> parameters instanceof MountainParameters;
            case VOLCANIC_ARCHIPELAGO -> parameters instanceof VolcanicArchipelagoParameters;
            case VOLCANIC_CALDERA -> parameters instanceof VolcanicCalderaParameters;
            case LAVA_FLOW_FIELD -> parameters instanceof LavaFlowParameters;
            case SINGLE_ISLAND -> parameters instanceof SingleIslandParameters;
            case ARCHIPELAGO -> parameters instanceof ArchipelagoParameters;
            case VOLCANIC_CONE -> parameters instanceof VolcanicConeParameters;
            case OCEAN_BASIN -> parameters instanceof OceanBasinParameters;
            case ABYSSAL_PLAIN -> parameters instanceof AbyssalPlainParameters;
            case SEAMOUNT -> parameters instanceof SeamountParameters;
            case CONTINENTAL_SHELF -> parameters instanceof ContinentalShelfParameters;
            case CONTINENTAL_SLOPE -> parameters instanceof ContinentalSlopeParameters;
            case SUBMARINE_CANYON -> parameters instanceof SubmarineCanyonParameters;
            case CAVE_ENTRANCE -> parameters instanceof CaveEntranceParameters;
            case UNDERGROUND_RIVER -> parameters instanceof UndergroundRiverParameters;
            case SINKHOLE -> parameters instanceof SinkholeParameters;
            case KARST_SPRING -> parameters instanceof KarstSpringParameters;
            case FLOODED_CAVE -> parameters instanceof FloodedCaveParameters;
            case VALLEY_GLACIER, ICE_CAP, ICE_SHEET -> parameters instanceof GlacialIceParameters;
            case MORAINE_FIELD -> parameters instanceof MoraineFieldParameters;
            case OUTWASH_PLAIN -> parameters instanceof OutwashPlainParameters;
            case ESCARPMENT -> parameters instanceof EscarpmentParameters;
            case PLATEAU -> parameters instanceof PlateauParameters;
            case LAVA_TUBE -> parameters instanceof LavaTubeParameters;
            case SPRING -> parameters instanceof SpringParameters;
            case OXBOW_LAKE -> parameters instanceof OxbowLakeParameters;
            default -> parameters instanceof NoParameters;
        };
        if (!valid) throw new IllegalArgumentException("parameter type does not match feature kind " + kind);
    }

    private static void validateGeometryType(FeatureKind kind, GeometryType type) {
        boolean valid = switch (kind) {
            case SANDY_BEACH, FJORD, MEANDERING_RIVER, RIVER, LAVA_FLOW_FIELD, CANYON, SUBMARINE_CANYON,
                    BEDROCK_RIVER, ALPINE_MOUNTAIN_RANGE, SEA_CLIFF, ESCARPMENT, ROCKY_COAST, REEF_PASS,
                    HILL_RANGE, MOUNTAIN_RANGE, VALLEY, UNDERGROUND_RIVER, LAVA_TUBE, VALLEY_GLACIER
                    -> type == GeometryType.SPLINE;
            case BREAKWATER_HARBOR, TIDAL_CHANNEL_NETWORK -> type == GeometryType.MULTI_SPLINE;
            case HARBOR_BASIN, ROCKY_CAPE, BACKSHORE_PLAINS, PLAIN, PLATEAU, FLOODPLAIN, MARSH,
                    GLACIAL_MOUNTAIN_RANGE, DELTA, OCEAN_BASIN, ABYSSAL_PLAIN, CONTINENTAL_SHELF,
                    CONTINENTAL_SLOPE, MANGROVE_WETLAND, CORAL_REEF, LAGOON, LAKE, OXBOW_LAKE, ICE_SHEET,
                    MORAINE_FIELD, OUTWASH_PLAIN -> type == GeometryType.POLYGON;
            case VOLCANIC_ARCHIPELAGO, ARCHIPELAGO, GLACIAL_CIRQUE_FIELD -> type == GeometryType.MULTI_POINT;
            case VOLCANIC_CALDERA, WATERFALL, CAVE_ENTRANCE, SINKHOLE, KARST_SPRING, SPRING,
                    FLOODED_CAVE, SINGLE_ISLAND, VOLCANIC_CONE, ICE_CAP, SEAMOUNT
                    -> type == GeometryType.POINT;
            case CAVE_NETWORK, LUSH_CAVE, OVERHANG, SKY_ISLAND_GROUP -> type == GeometryType.VOLUME_GUIDE;
        };
        if (!valid) throw new IllegalArgumentException("geometry type " + type + " is invalid for " + kind);
    }

    private static void validateWeight(Strength strength, int weightMillionths) {
        if (strength == Strength.HARD && weightMillionths != 0) throw new IllegalArgumentException("HARD constraint must not have weight");
        if (strength == Strength.SOFT && (weightMillionths < 1 || weightMillionths > FIXED_SCALE)) throw new IllegalArgumentException("SOFT constraint requires weight in (0,1]");
    }

    private static void requireUnitRange(FixedRange range, String field) {
        Objects.requireNonNull(range, field);
        if (range.minimumMillionths() < 0 || range.maximumMillionths() > FIXED_SCALE) throw new IllegalArgumentException(field + " outside 0..1");
    }

    private static void requireNonNegativeRange(IntRange range, String field) {
        if (range.minimum() < 0) throw new IllegalArgumentException(field + " must be non-negative");
    }

    private static void requirePositiveRange(IntRange range, String field) {
        if (range.minimum() < 1) throw new IllegalArgumentException(field + " must be positive");
    }

    private static void requireBoundedPositiveRange(IntRange range, String field, int maximum) {
        requirePositiveRange(range, field);
        if (range.maximum() > maximum) throw new IllegalArgumentException(field + " exceeds " + maximum);
    }

    private static void validateRing(List<Point2> ring) {
        if (ring.size() < 4 || !ring.getFirst().equals(ring.getLast())) throw new IllegalArgumentException("polygon ring must be closed and contain at least three vertices");
        for (int first = 0; first < ring.size() - 1; first++) {
            for (int second = first + 1; second < ring.size() - 1; second++) {
                if (Math.abs(first - second) <= 1 || (first == 0 && second == ring.size() - 2)) continue;
                if (segmentsIntersect(ring.get(first), ring.get(first + 1), ring.get(second), ring.get(second + 1))) {
                    throw new IllegalArgumentException("polygon ring self-intersects");
                }
            }
        }
        if (signedAreaTwice(ring) == 0) throw new IllegalArgumentException("polygon ring has zero area");
    }

    private static void validateHole(List<Point2> outer, List<Point2> hole) {
        if (!pointInside(outer, hole.getFirst())) throw new IllegalArgumentException("polygon hole is not inside outer ring");
        for (int outerIndex = 0; outerIndex < outer.size() - 1; outerIndex++) {
            for (int holeIndex = 0; holeIndex < hole.size() - 1; holeIndex++) {
                if (segmentsIntersect(outer.get(outerIndex), outer.get(outerIndex + 1), hole.get(holeIndex), hole.get(holeIndex + 1))) {
                    throw new IllegalArgumentException("polygon hole intersects outer ring");
                }
            }
        }
    }

    private static long signedAreaTwice(List<Point2> ring) {
        long area = 0;
        for (int index = 0; index < ring.size() - 1; index++) {
            Point2 first = ring.get(index);
            Point2 second = ring.get(index + 1);
            area += (long) first.xMillionths() * second.zMillionths() - (long) second.xMillionths() * first.zMillionths();
        }
        return area;
    }

    private static boolean pointInside(List<Point2> ring, Point2 point) {
        boolean inside = false;
        for (int current = 0, previous = ring.size() - 2; current < ring.size() - 1; previous = current++) {
            Point2 a = ring.get(current);
            Point2 b = ring.get(previous);
            boolean crosses = (a.zMillionths() > point.zMillionths()) != (b.zMillionths() > point.zMillionths());
            if (crosses) {
                long left = (long) (point.xMillionths() - a.xMillionths())
                        * (b.zMillionths() - a.zMillionths());
                long right = (long) (b.xMillionths() - a.xMillionths())
                        * (point.zMillionths() - a.zMillionths());
                boolean leftOfIntersection = b.zMillionths() > a.zMillionths() ? left < right : left > right;
                if (leftOfIntersection) inside = !inside;
            }
        }
        return inside;
    }

    private static boolean segmentsIntersect(Point2 a, Point2 b, Point2 c, Point2 d) {
        long first = orientation(a, b, c);
        long second = orientation(a, b, d);
        long third = orientation(c, d, a);
        long fourth = orientation(c, d, b);
        if (first == 0 && onSegment(a, c, b)) return true;
        if (second == 0 && onSegment(a, d, b)) return true;
        if (third == 0 && onSegment(c, a, d)) return true;
        if (fourth == 0 && onSegment(c, b, d)) return true;
        return Long.signum(first) != Long.signum(second) && Long.signum(third) != Long.signum(fourth);
    }

    private static long orientation(Point2 a, Point2 b, Point2 c) {
        return (long) (b.xMillionths() - a.xMillionths()) * (c.zMillionths() - a.zMillionths())
                - (long) (b.zMillionths() - a.zMillionths()) * (c.xMillionths() - a.xMillionths());
    }

    private static boolean onSegment(Point2 start, Point2 point, Point2 end) {
        return point.xMillionths() >= Math.min(start.xMillionths(), end.xMillionths())
                && point.xMillionths() <= Math.max(start.xMillionths(), end.xMillionths())
                && point.zMillionths() >= Math.min(start.zMillionths(), end.zMillionths())
                && point.zMillionths() <= Math.max(start.zMillionths(), end.zMillionths());
    }

    private static void requireUnique(List<String> values, String kind) {
        if (new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("duplicate " + kind + " id");
    }

    private static String optionalSlug(String value, String field) {
        return value == null || value.isEmpty() ? "" : V2Validation.slug(value, field);
    }

    private static String optionalSymbol(String value, String field) {
        if (value == null || value.isEmpty()) return "";
        if (!value.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException(field + " must be an uppercase symbol");
        return value;
    }

    private static String requiredSymbol(String value, String field) {
        value = V2Validation.nonBlank(value, field, 96);
        if (!value.matches("[A-Z][A-Z0-9_]{0,95}")) {
            throw new IllegalArgumentException(field + " must be an uppercase symbol");
        }
        return value;
    }

    private static void addOptionalStableId(Set<String> target, String value) {
        if (!value.isEmpty() && !target.add(value)) throw new IllegalArgumentException("duplicate subgeometry id: " + value);
    }

    private static void requireKnownSubgeometryIds(List<String> ids, Set<String> known, String featureId) {
        for (String id : ids) {
            if (!known.contains(id)) throw new IllegalArgumentException("feature " + featureId + " references unknown subgeometry id: " + id);
        }
    }
}
