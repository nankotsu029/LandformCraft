package com.github.nankotsu029.landformcraft.generator.v2.coast.cape;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.RockyCapePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Freezes bounded channel and sea-stack descriptors for one strictly 2.5D ROCKY_CAPE. */
public final class RockyCapePlanCompilerV2 {
    private static final int CANDIDATE_COUNT = 128;
    private static final long CLIFF_SALT = 0x18c7_8d8b_0d4c_51f1L;
    private static final long RELIEF_SALT = 0xd7d5_5ea3_93f0_921dL;
    private static final long BAND_SALT = 0x8ea4_53b5_9b48_d2efL;
    private static final long EXPOSURE_SALT = 0x750e_a9e6_ebf2_71abL;
    private static final long STACK_COUNT_SALT = 0x14d0_d8ab_7ce9_2b51L;
    private static final long CHANNEL_COUNT_SALT = 0x7f3d_b72d_9fd8_6481L;

    public RockyCapePlanV2 compile(
            TerrainIntentV2.Feature feature,
            CoastalFeaturePlanV2 coastalPlan,
            WorldBlueprintV2.Bounds bounds,
            long namedSeed
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(coastalPlan, "coastalPlan");
        Objects.requireNonNull(bounds, "bounds");
        if (feature.kind() != TerrainIntentV2.FeatureKind.ROCKY_CAPE
                || !(feature.parameters() instanceof TerrainIntentV2.RockyCapeParameters parameters)
                || !feature.id().equals(coastalPlan.featureId())
                || coastalPlan.kind() != TerrainIntentV2.FeatureKind.ROCKY_CAPE
                || coastalPlan.geometryRole() != CoastalFeaturePlanV2.GeometryRole.LAND_REGION) {
            throw failure("v2.cape-plan-binding", "ROCKY_CAPE feature and coastal plan do not match");
        }
        if (parameters.capeMode() != TerrainIntentV2.CapeMode.TWO_POINT_FIVE_D_ONLY) {
            throw failure("v2.cape-volume-required", "ROCKY_CAPE does not fall back to a 3D representation");
        }
        if (coastalPlan.geometry().rings().size() != 1 || !coastalPlan.geometry().paths().isEmpty()) {
            throw failure("v2.cape-holes-unsupported", "V2-2-06 requires exactly one closed cape polygon ring");
        }
        try {
            List<CoastalFeaturePlanV2.BlockPoint> ring = coastalPlan.geometry().rings().getFirst().points();
            int turningCount = RockyCapeGeometryV2.nonCollinearTurningCount(ring);
            if (turningCount < 3) {
                throw failure("v2.cape-coast-complexity", "cape polygon must contain at least three turns");
            }

            int cliffHeight = select(
                    parameters.cliffHeightBlocks().minimum(),
                    Math.min(parameters.cliffHeightBlocks().maximum(),
                            parameters.localReliefAboveSeaBlocks().maximum()),
                    namedSeed, CLIFF_SALT);
            int relief = select(
                    Math.max(cliffHeight, parameters.localReliefAboveSeaBlocks().minimum()),
                    parameters.localReliefAboveSeaBlocks().maximum(),
                    namedSeed, RELIEF_SALT);
            int cliffBand = select(parameters.cliffBandWidthBlocks(), namedSeed, BAND_SALT);
            int exposure = select(parameters.rockExposure01(), namedSeed, EXPOSURE_SALT);
            int stackCount = select(parameters.seaStackCount(), namedSeed, STACK_COUNT_SALT);
            int channelCount = select(parameters.channelCount(), namedSeed, CHANNEL_COUNT_SALT);
            if ((long) bounds.waterLevel() + relief > bounds.maxY()
                    || (long) bounds.waterLevel() - parameters.channelDepthBlocks().maximum() < bounds.minY()) {
                throw failure("v2.cape-vertical-bounds", "cape profile exceeds request vertical bounds");
            }

            List<RockyCapePlanV2.ChannelDescriptor> channels = channels(
                    feature.id(), ring, parameters, namedSeed, channelCount, bounds);
            List<RockyCapePlanV2.SeaStackDescriptor> stacks = stacks(
                    feature.id(), ring, parameters, namedSeed, stackCount, relief, bounds);
            int support = Math.max(parameters.cliffBandWidthBlocks().maximum(), Math.max(
                    parameters.channelLengthBlocks().maximum()
                            + (parameters.channelWidthBlocks().maximum() + 1) / 2,
                    parameters.seaStackOffshoreDistanceBlocks().maximum()
                            + parameters.seaStackRadiusBlocks().maximum()));
            if (support != coastalPlan.supportRadiusXZ() || support > 64) {
                throw failure("v2.cape-support", "cape descriptor support differs from coastal foundation halo");
            }
            return new RockyCapePlanV2(
                    RockyCapePlanV2.VERSION, feature.id(),
                    RockyCapePlanV2.ProfileKind.LINEAR_CLIFF_TO_INTERIOR,
                    parameters.capeMode(), parameters.seawardSide(), cliffHeight, relief, cliffBand,
                    Math.toIntExact(parameters.rockExposure01().minimumMillionths()),
                    Math.toIntExact(parameters.rockExposure01().maximumMillionths()), exposure,
                    RockyCapeFixedMathV2.mix64(namedSeed ^ 0xb0ac_8a85_ba93_9945L),
                    parameters.seaStackCount().minimum(), parameters.seaStackCount().maximum(), stacks,
                    parameters.channelCount().minimum(), parameters.channelCount().maximum(), channels,
                    bounds.minY(), bounds.maxY(), bounds.waterLevel(),
                    CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID,
                    CoastalFoundationModuleV2.CAPE_SURFACE_HEIGHT_FIELD_ID,
                    CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID,
                    CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID,
                    support, turningCount);
        } catch (RockyCapeGenerationException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw new RockyCapeGenerationException(
                    "v2.cape-overflow", "rocky cape plan arithmetic overflow", exception);
        }
    }

    private static List<RockyCapePlanV2.ChannelDescriptor> channels(
            String featureId,
            List<CoastalFeaturePlanV2.BlockPoint> ring,
            TerrainIntentV2.RockyCapeParameters parameters,
            long namedSeed,
            int requestedCount,
            WorldBlueprintV2.Bounds bounds
    ) {
        Axis axis = axis(parameters.seawardSide(), ring);
        List<Candidate> candidates = candidates(axis.minimumTangent(), axis.maximumTangent(),
                namedSeed ^ 0x7485_efa1_bf02_2259L);
        List<RockyCapePlanV2.ChannelDescriptor> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int order = result.size() + 1;
            int width = select(parameters.channelWidthBlocks(), namedSeed,
                    0x6da0_9ea2_8ba0_80c3L ^ order);
            int channelLength = select(parameters.channelLengthBlocks(), namedSeed,
                    0xc3b6_77a0_9ac4_3b29L ^ order);
            int depth = select(parameters.channelDepthBlocks(), namedSeed,
                    0x6e96_5ec7_ee2c_5e9bL ^ order);
            Long boundary = RockyCapeGeometryV2.seawardBoundary(
                    ring, parameters.seawardSide(), candidate.tangent());
            if (boundary == null || !separatedChannel(candidate.tangent(), width, result, parameters.seawardSide())) {
                continue;
            }
            CoastalFeaturePlanV2.BlockPoint mouth = point(
                    parameters.seawardSide(), boundary, candidate.tangent());
            CoastalFeaturePlanV2.BlockPoint inland = offsetWithin(
                    mouth, -outwardX(parameters.seawardSide()), -outwardZ(parameters.seawardSide()),
                    channelLength, bounds);
            if (inland == null
                    || !channelFits(ring, mouth, inland, width, parameters.seawardSide())) {
                continue;
            }
            result.add(new RockyCapePlanV2.ChannelDescriptor(
                    order, descriptorId(featureId, "channel", order), mouth, inland,
                    width, channelLength, depth));
            if (result.size() == requestedCount) break;
        }
        if (result.size() != requestedCount) {
            throw failure("v2.cape-thin-land-bridge",
                    "cape polygon cannot contain the requested bounded channel descriptors");
        }
        return List.copyOf(result);
    }

    private static List<RockyCapePlanV2.SeaStackDescriptor> stacks(
            String featureId,
            List<CoastalFeaturePlanV2.BlockPoint> ring,
            TerrainIntentV2.RockyCapeParameters parameters,
            long namedSeed,
            int requestedCount,
            int relief,
            WorldBlueprintV2.Bounds bounds
    ) {
        Axis axis = axis(parameters.seawardSide(), ring);
        List<Candidate> candidates = candidates(axis.minimumTangent(), axis.maximumTangent(),
                namedSeed ^ 0x3bc6_22ad_5037_a2c9L);
        List<RockyCapePlanV2.SeaStackDescriptor> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int order = result.size() + 1;
            int radius = select(parameters.seaStackRadiusBlocks(), namedSeed,
                    0x78f3_ec32_52aa_844fL ^ order);
            int offshore = select(parameters.seaStackOffshoreDistanceBlocks(), namedSeed,
                    0x8725_163a_f811_0a53L ^ order);
            Long boundary = RockyCapeGeometryV2.seawardBoundary(
                    ring, parameters.seawardSide(), candidate.tangent());
            if (boundary == null) continue;
            CoastalFeaturePlanV2.BlockPoint center = offsetWithin(
                    point(parameters.seawardSide(), boundary, candidate.tangent()),
                    outwardX(parameters.seawardSide()), outwardZ(parameters.seawardSide()), offshore, bounds);
            if (center == null || !circleInsideBounds(center, radius, bounds)
                    || RockyCapeGeometryV2.contains(ring, center.xMillionths(), center.zMillionths())
                    || RockyCapeGeometryV2.distanceToBoundary(
                    ring, center.xMillionths(), center.zMillionths())
                    < (long) (radius + 1) * RockyCapePlanV2.FIXED_SCALE
                    || !separatedStack(center, radius, result)) {
                continue;
            }
            result.add(new RockyCapePlanV2.SeaStackDescriptor(
                    1_024 + order, descriptorId(featureId, "stack", order), center,
                    radius, relief, offshore));
            if (result.size() == requestedCount) break;
        }
        if (result.size() != requestedCount) {
            throw failure("v2.cape-isolated-stack",
                    "cape polygon has no bounded offshore space for the requested isolated sea stacks");
        }
        return List.copyOf(result);
    }

    private static boolean channelFits(
            List<CoastalFeaturePlanV2.BlockPoint> ring,
            CoastalFeaturePlanV2.BlockPoint mouth,
            CoastalFeaturePlanV2.BlockPoint end,
            int width,
            TerrainIntentV2.Edge side
    ) {
        long halfWidth = (long) (width + 1) / 2 * RockyCapePlanV2.FIXED_SCALE;
        long tangentX = side == TerrainIntentV2.Edge.NORTH || side == TerrainIntentV2.Edge.SOUTH ? 1L : 0L;
        long tangentZ = tangentX == 0L ? 1L : 0L;
        long middleX = RockyCapeFixedMathV2.roundDivide(mouth.xMillionths() + end.xMillionths(), 2L);
        long middleZ = RockyCapeFixedMathV2.roundDivide(mouth.zMillionths() + end.zMillionths(), 2L);
        for (long sign : new long[]{-1L, 1L}) {
            if (!RockyCapeGeometryV2.contains(ring,
                    end.xMillionths() + sign * tangentX * halfWidth,
                    end.zMillionths() + sign * tangentZ * halfWidth)
                    || !RockyCapeGeometryV2.contains(ring,
                    middleX + sign * tangentX * halfWidth,
                    middleZ + sign * tangentZ * halfWidth)) {
                return false;
            }
        }
        return RockyCapeGeometryV2.contains(ring, end.xMillionths(), end.zMillionths());
    }

    private static boolean separatedChannel(
            long tangent,
            int width,
            List<RockyCapePlanV2.ChannelDescriptor> existing,
            TerrainIntentV2.Edge side
    ) {
        for (RockyCapePlanV2.ChannelDescriptor channel : existing) {
            long other = side == TerrainIntentV2.Edge.EAST || side == TerrainIntentV2.Edge.WEST
                    ? channel.mouth().zMillionths() : channel.mouth().xMillionths();
            long required = (long) ((width + channel.widthBlocks() + 1) / 2 + 3)
                    * RockyCapePlanV2.FIXED_SCALE;
            if (Math.abs(tangent - other) < required) return false;
        }
        return true;
    }

    private static boolean separatedStack(
            CoastalFeaturePlanV2.BlockPoint center,
            int radius,
            List<RockyCapePlanV2.SeaStackDescriptor> existing
    ) {
        for (RockyCapePlanV2.SeaStackDescriptor stack : existing) {
            long dx = center.xMillionths() - stack.center().xMillionths();
            long dz = center.zMillionths() - stack.center().zMillionths();
            long distance = RockyCapeFixedMathV2.integerSquareRoot(
                    Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz)));
            if (distance < (long) (radius + stack.radiusBlocks() + 2) * RockyCapePlanV2.FIXED_SCALE) {
                return false;
            }
        }
        return true;
    }

    private static List<Candidate> candidates(long minimum, long maximum, long seed) {
        if (maximum <= minimum) throw failure("v2.cape-degenerate", "cape has no cardinal seaward span");
        List<Candidate> result = new ArrayList<>(CANDIDATE_COUNT);
        long span = maximum - minimum;
        for (int index = 0; index < CANDIDATE_COUNT; index++) {
            long tangent = minimum + span * (index + 1L) / (CANDIDATE_COUNT + 1L);
            result.add(new Candidate(tangent, RockyCapeFixedMathV2.mix64(seed ^ index)));
        }
        result.sort((first, second) -> {
            int byScore = Long.compareUnsigned(first.score(), second.score());
            return byScore != 0 ? byScore : Long.compare(first.tangent(), second.tangent());
        });
        return List.copyOf(result);
    }

    private static Axis axis(TerrainIntentV2.Edge side, List<CoastalFeaturePlanV2.BlockPoint> ring) {
        boolean verticalBoundary = side == TerrainIntentV2.Edge.EAST || side == TerrainIntentV2.Edge.WEST;
        long minimum = ring.stream().limit(ring.size() - 1L).mapToLong(point ->
                verticalBoundary ? point.zMillionths() : point.xMillionths()).min().orElseThrow();
        long maximum = ring.stream().limit(ring.size() - 1L).mapToLong(point ->
                verticalBoundary ? point.zMillionths() : point.xMillionths()).max().orElseThrow();
        return new Axis(minimum, maximum);
    }

    private static CoastalFeaturePlanV2.BlockPoint point(
            TerrainIntentV2.Edge side, long boundary, long tangent
    ) {
        return side == TerrainIntentV2.Edge.EAST || side == TerrainIntentV2.Edge.WEST
                ? new CoastalFeaturePlanV2.BlockPoint(boundary, tangent)
                : new CoastalFeaturePlanV2.BlockPoint(tangent, boundary);
    }

    private static CoastalFeaturePlanV2.BlockPoint offsetWithin(
            CoastalFeaturePlanV2.BlockPoint point,
            long directionX,
            long directionZ,
            int blocks,
            WorldBlueprintV2.Bounds bounds
    ) {
        long x = point.xMillionths() + directionX * blocks * RockyCapePlanV2.FIXED_SCALE;
        long z = point.zMillionths() + directionZ * blocks * RockyCapePlanV2.FIXED_SCALE;
        if (x < 0L || z < 0L
                || x > (long) (bounds.width() - 1) * RockyCapePlanV2.FIXED_SCALE
                || z > (long) (bounds.length() - 1) * RockyCapePlanV2.FIXED_SCALE) {
            return null;
        }
        return new CoastalFeaturePlanV2.BlockPoint(x, z);
    }

    private static long outwardX(TerrainIntentV2.Edge side) {
        return switch (side) { case EAST -> 1L; case WEST -> -1L; default -> 0L; };
    }

    private static long outwardZ(TerrainIntentV2.Edge side) {
        return switch (side) { case SOUTH -> 1L; case NORTH -> -1L; default -> 0L; };
    }

    private static boolean circleInsideBounds(
            CoastalFeaturePlanV2.BlockPoint center, int radius, WorldBlueprintV2.Bounds bounds
    ) {
        long fixedRadius = (long) radius * RockyCapePlanV2.FIXED_SCALE;
        return center.xMillionths() - fixedRadius >= 0L && center.zMillionths() - fixedRadius >= 0L
                && center.xMillionths() + fixedRadius <= (long) (bounds.width() - 1) * RockyCapePlanV2.FIXED_SCALE
                && center.zMillionths() + fixedRadius <= (long) (bounds.length() - 1) * RockyCapePlanV2.FIXED_SCALE;
    }

    private static int select(TerrainIntentV2.IntRange range, long seed, long salt) {
        return select(range.minimum(), range.maximum(), seed, salt);
    }

    private static int select(int minimum, int maximum, long seed, long salt) {
        if (minimum > maximum) throw failure("v2.cape-relief", "cape cliff and relief ranges do not overlap");
        int span = maximum - minimum + 1;
        return minimum + Math.floorMod(RockyCapeFixedMathV2.mix64(seed ^ salt), span);
    }

    private static int select(TerrainIntentV2.FixedRange range, long seed, long salt) {
        long span = range.maximumMillionths() - range.minimumMillionths() + 1L;
        return Math.toIntExact(range.minimumMillionths()
                + Math.floorMod(RockyCapeFixedMathV2.mix64(seed ^ salt), span));
    }

    private static String descriptorId(String featureId, String kind, int order) {
        String prefix = featureId.substring(0, Math.min(40, featureId.length()));
        return prefix + '-' + kind + '-' + (order < 10 ? "0" : "") + order;
    }

    private static RockyCapeGenerationException failure(String ruleId, String message) {
        return new RockyCapeGenerationException(ruleId, message);
    }

    private record Candidate(long tangent, long score) { }
    private record Axis(long minimumTangent, long maximumTangent) { }
}
