package com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.BreakwaterHarborPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** V2-2-05 integer-only local 2.5D BREAKWATER_HARBOR field generator. */
public final class BreakwaterHarborGeneratorV2 {
    public static final String VERSION = "breakwater-fixed-v1";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int NO_DATA = Integer.MIN_VALUE;
    public static final int MAXIMUM_CORE_EXTENT = 256;
    public static final int MAXIMUM_HALO_XZ = 64;
    public static final long MAXIMUM_WINDOW_RETAINED_BYTES = 8L * 1024L * 1024L;
    public static final long MAXIMUM_SOLID_BLOCKS = 4_000_000L;

    private final BreakwaterHarborPlanV2 plan;
    private final CoastalFeaturePlanV2 coastalPlan;
    private final int width;
    private final int length;
    private final List<ArmRuntime> arms;

    public BreakwaterHarborGeneratorV2(
            BreakwaterHarborPlanV2 plan,
            CoastalFeaturePlanV2 coastalPlan,
            int width,
            int length
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.coastalPlan = Objects.requireNonNull(coastalPlan, "coastalPlan");
        if (!plan.featureId().equals(coastalPlan.featureId())
                || coastalPlan.kind() != TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR
                || coastalPlan.geometryRole() != CoastalFeaturePlanV2.GeometryRole.STRUCTURE_CENTERLINES) {
            throw failure("v2.breakwater-plan-binding", "breakwater and coastal plans do not match");
        }
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
            throw failure("v2.breakwater-dimensions", "dimensions must be within 1..1000");
        }
        this.width = width;
        this.length = length;
        Map<String, BreakwaterHarborPlanV2.ArmPlan> plansById = new HashMap<>();
        plan.arms().forEach(arm -> plansById.put(arm.armId(), arm));
        this.arms = BreakwaterPathKernelV2.flatten(coastalPlan).stream().map(geometry -> {
            BreakwaterHarborPlanV2.ArmPlan armPlan = plansById.get(geometry.armId());
            if (armPlan == null || armPlan.lengthMillionths() != geometry.lengthMillionths()) {
                throw failure("v2.breakwater-plan-binding", "breakwater arm geometry differs from its frozen plan");
            }
            return new ArmRuntime(armPlan, geometry);
        }).toList();
    }

    public BreakwaterHarborPlanV2 plan() { return plan; }
    public int width() { return width; }
    public int length() { return length; }

    public BreakwaterSample sampleAt(int globalX, int globalZ) {
        requireCoordinate(globalX, globalZ);
        try {
            long sampleX = Math.multiplyExact((long) globalX, BreakwaterPathKernelV2.GEOMETRY_SCALE);
            long sampleZ = Math.multiplyExact((long) globalZ, BreakwaterPathKernelV2.GEOMETRY_SCALE);
            Candidate nearest = null;
            for (ArmRuntime arm : arms) {
                if (beyondOpening(arm, sampleX, sampleZ)) continue;
                for (BreakwaterPathKernelV2.Segment segment : arm.geometry().segments()) {
                    Candidate candidate = nearest(arm.plan(), segment, sampleX, sampleZ);
                    if (nearest == null || candidate.distanceSquared() < nearest.distanceSquared()
                            || (candidate.distanceSquared() == nearest.distanceSquared()
                            && candidate.arm().armOrder() < nearest.arm().armOrder())) {
                        nearest = candidate;
                    }
                }
            }
            if (nearest == null) return BreakwaterSample.OUTSIDE;
            long distanceQ = BreakwaterFixedMathV2.integerSquareRoot(nearest.distanceSquared());
            long distance = BreakwaterFixedMathV2.roundDivide(
                    Math.multiplyExact(distanceQ, (long) FIXED_SCALE), BreakwaterPathKernelV2.GEOMETRY_SCALE);
            long halfCrest = Math.multiplyExact((long) plan.crestWidthBlocks(), FIXED_SCALE) / 2L;
            boolean inner = isInner(nearest);
            int depthBlocks = inner ? plan.innerDepthBlocks() : plan.outerDepthBlocks();
            long toeDistance = Math.addExact(halfCrest,
                    Math.multiplyExact((long) depthBlocks, plan.foundationSideSlopeRunPerRiseMillionths()));
            if (distance > toeDistance) return BreakwaterSample.OUTSIDE;

            BreakwaterRegion region;
            long top;
            if (distance <= halfCrest) {
                region = BreakwaterRegion.CREST;
                top = Math.multiplyExact((long) plan.waterLevel() + plan.crestAboveWaterBlocks(), FIXED_SCALE);
            } else {
                region = inner ? BreakwaterRegion.INNER_FOUNDATION : BreakwaterRegion.OUTER_FOUNDATION;
                long run = distance - halfCrest;
                long drop = BreakwaterFixedMathV2.roundDivide(
                        Math.multiplyExact(run, FIXED_SCALE), plan.foundationSideSlopeRunPerRiseMillionths());
                top = Math.subtractExact(Math.multiplyExact((long) plan.waterLevel(), FIXED_SCALE), drop);
            }
            long bottom = Math.multiplyExact((long) plan.waterLevel() - depthBlocks, FIXED_SCALE);
            if (top < bottom) top = bottom;
            return new BreakwaterSample(region, nearest.arm().armOrder(),
                    Math.toIntExact(top), Math.toIntExact(bottom));
        } catch (ArithmeticException exception) {
            throw new BreakwaterGenerationException(
                    "v2.breakwater-overflow", "breakwater field arithmetic overflow", exception);
        }
    }

    public BreakwaterWindowV2 renderWindow(
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            CancellationToken token
    ) {
        Objects.requireNonNull(token, "token");
        BreakwaterWindowV2.Bounds bounds = windowBounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength, haloXZ);
        int cells = Math.multiplyExact(bounds.width(), bounds.length());
        long retained = estimateWindowRetainedBytes(bounds.width(), bounds.length());
        if (retained > MAXIMUM_WINDOW_RETAINED_BYTES) {
            throw failure("v2.breakwater-budget", "breakwater window exceeds retained-memory budget");
        }
        int[][] fields = new int[BreakwaterField.values().length][cells];
        for (int localZ = 0; localZ < bounds.length(); localZ++) {
            token.throwIfCancellationRequested();
            for (int localX = 0; localX < bounds.width(); localX++) {
                BreakwaterSample sample = sampleAt(bounds.originX() + localX, bounds.originZ() + localZ);
                int index = localZ * bounds.width() + localX;
                for (BreakwaterField field : BreakwaterField.values()) {
                    fields[field.ordinal()][index] = sample.rawValue(field);
                }
            }
        }
        return new BreakwaterWindowV2(bounds, fields, retained);
    }

    public Map<BreakwaterField, String> fieldChecksums(CancellationToken token) {
        return fieldChecksumsFrom(this::sampleAt, token);
    }

    public Map<BreakwaterField, String> fieldChecksumsFrom(CellSource source, CancellationToken token) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        EnumMap<BreakwaterField, MessageDigest> digests = new EnumMap<>(BreakwaterField.class);
        for (BreakwaterField field : BreakwaterField.values()) {
            MessageDigest digest = sha256();
            digest.update((VERSION + '\0' + field.name() + '\0').getBytes(StandardCharsets.UTF_8));
            updateInt(digest, width);
            updateInt(digest, length);
            digests.put(field, digest);
        }
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                BreakwaterSample sample = Objects.requireNonNull(source.sampleAt(x, z), "breakwater sample");
                for (BreakwaterField field : BreakwaterField.values()) {
                    updateInt(digests.get(field), sample.rawValue(field));
                }
            }
        }
        EnumMap<BreakwaterField, String> result = new EnumMap<>(BreakwaterField.class);
        for (BreakwaterField field : BreakwaterField.values()) {
            result.put(field, HexFormat.of().formatHex(digests.get(field).digest()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Streaming Task-local hard metrics; the independent feature validator remains V2-2-08. */
    public BreakwaterMetrics evaluate(CancellationToken token) {
        Objects.requireNonNull(token, "token");
        long crest = 0L;
        long inner = 0L;
        long outer = 0L;
        long solidBlocks = 0L;
        long[] armCells = new long[arms.size() + 1];
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                BreakwaterSample sample = sampleAt(x, z);
                switch (sample.region()) {
                    case CREST -> crest++;
                    case INNER_FOUNDATION -> inner++;
                    case OUTER_FOUNDATION -> outer++;
                    case OUTSIDE -> { continue; }
                }
                armCells[sample.armIndex()]++;
                long topBlock = Math.floorDiv(sample.topHeightMillionths(), FIXED_SCALE);
                long bottomBlock = -Math.floorDiv(-(long) sample.bottomHeightMillionths(), FIXED_SCALE);
                solidBlocks = addSolidBlocks(
                        solidBlocks, Math.max(0L, topBlock - bottomBlock + 1L));
            }
        }
        if (crest == 0L || inner == 0L || outer == 0L) {
            throw failure("v2.breakwater-profile", "breakwater crest or foundation profile is missing");
        }
        for (BreakwaterHarborPlanV2.ArmPlan arm : plan.arms()) {
            if (armCells[arm.armOrder()] == 0L) {
                throw failure("v2.breakwater-arm-length", "a breakwater arm has no raster cells");
            }
        }
        return new BreakwaterMetrics(
                plan.arms().stream().map(BreakwaterHarborPlanV2.ArmPlan::lengthMillionths).toList(),
                plan.actualClearOpeningWidthMillionths(), crest, inner, outer, solidBlocks);
    }

    public static long estimateWindowRetainedBytes(int width, int length) {
        if (width < 1 || length < 1) throw new IllegalArgumentException("window dimensions must be positive");
        return Math.addExact(64L * 1024L, Math.multiplyExact(
                Math.multiplyExact((long) width, length), (long) BreakwaterField.values().length * Integer.BYTES));
    }

    static long addSolidBlocks(long current, long additional) {
        if (current < 0L || additional < 0L) throw new IllegalArgumentException("block counts must be non-negative");
        long result;
        try {
            result = Math.addExact(current, additional);
        } catch (ArithmeticException exception) {
            throw new BreakwaterGenerationException(
                    "v2.breakwater-block-budget", "breakwater solid block stream exceeds budget", exception);
        }
        if (result > MAXIMUM_SOLID_BLOCKS) {
            throw failure("v2.breakwater-block-budget", "breakwater solid block stream exceeds budget");
        }
        return result;
    }

    private BreakwaterWindowV2.Bounds windowBounds(
            int coreOriginX, int coreOriginZ, int coreWidth, int coreLength, int haloXZ
    ) {
        if (coreOriginX < 0 || coreOriginZ < 0 || coreWidth < 1 || coreLength < 1
                || coreWidth > MAXIMUM_CORE_EXTENT || coreLength > MAXIMUM_CORE_EXTENT
                || (long) coreOriginX + coreWidth > width || (long) coreOriginZ + coreLength > length) {
            throw failure("v2.breakwater-window", "core window is outside bounds or exceeds 256 cells");
        }
        if (haloXZ < 0 || haloXZ > MAXIMUM_HALO_XZ) {
            throw failure("v2.breakwater-support", "window halo is outside 0..64");
        }
        int originX = Math.max(0, coreOriginX - haloXZ);
        int originZ = Math.max(0, coreOriginZ - haloXZ);
        int endX = Math.min(width, Math.addExact(Math.addExact(coreOriginX, coreWidth), haloXZ));
        int endZ = Math.min(length, Math.addExact(Math.addExact(coreOriginZ, coreLength), haloXZ));
        return new BreakwaterWindowV2.Bounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength,
                originX, originZ, endX - originX, endZ - originZ, haloXZ);
    }

    private static Candidate nearest(
            BreakwaterHarborPlanV2.ArmPlan arm,
            BreakwaterPathKernelV2.Segment segment,
            long sampleX,
            long sampleZ
    ) {
        long relativeX = sampleX - segment.a().x();
        long relativeZ = sampleZ - segment.a().z();
        long dot = Math.addExact(
                Math.multiplyExact(relativeX, segment.dx()), Math.multiplyExact(relativeZ, segment.dz()));
        long t = Math.max(0L, Math.min(BreakwaterPathKernelV2.GEOMETRY_SCALE,
                BreakwaterFixedMathV2.roundDivide(
                        Math.multiplyExact(dot, BreakwaterPathKernelV2.GEOMETRY_SCALE),
                        segment.lengthSquared())));
        long closestX = Math.addExact(segment.a().x(), BreakwaterFixedMathV2.roundDivide(
                Math.multiplyExact(segment.dx(), t), BreakwaterPathKernelV2.GEOMETRY_SCALE));
        long closestZ = Math.addExact(segment.a().z(), BreakwaterFixedMathV2.roundDivide(
                Math.multiplyExact(segment.dz(), t), BreakwaterPathKernelV2.GEOMETRY_SCALE));
        long dx = sampleX - closestX;
        long dz = sampleZ - closestZ;
        long squared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long cross = Math.subtractExact(
                Math.multiplyExact(segment.dx(), relativeZ), Math.multiplyExact(segment.dz(), relativeX));
        return new Candidate(arm, squared, cross, segment);
    }

    private static boolean beyondOpening(ArmRuntime arm, long sampleX, long sampleZ) {
        List<BreakwaterPathKernelV2.Segment> segments = arm.geometry().segments();
        BreakwaterPathKernelV2.Point endpoint;
        long beyondX;
        long beyondZ;
        if (arm.plan().openingAtStart()) {
            BreakwaterPathKernelV2.Segment first = segments.getFirst();
            endpoint = first.a();
            beyondX = -first.dx();
            beyondZ = -first.dz();
        } else {
            BreakwaterPathKernelV2.Segment last = segments.getLast();
            endpoint = last.b();
            beyondX = last.dx();
            beyondZ = last.dz();
        }
        long dot = Math.addExact(
                Math.multiplyExact(sampleX - endpoint.x(), beyondX),
                Math.multiplyExact(sampleZ - endpoint.z(), beyondZ));
        return dot > 0L;
    }

    private boolean isInner(Candidate candidate) {
        long innerX = switch (plan.innerSide()) {
            case EAST -> 1L;
            case WEST -> -1L;
            default -> 0L;
        };
        long innerZ = switch (plan.innerSide()) {
            case SOUTH -> 1L;
            case NORTH -> -1L;
            default -> 0L;
        };
        long leftNormalDot = Math.addExact(
                Math.multiplyExact(-candidate.segment().dz(), innerX),
                Math.multiplyExact(candidate.segment().dx(), innerZ));
        if (candidate.cross() == 0L) return true;
        return Long.signum(candidate.cross()) == Long.signum(leftNormalDot);
    }

    private void requireCoordinate(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("coordinate outside breakwater field");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static BreakwaterGenerationException failure(String ruleId, String message) {
        return new BreakwaterGenerationException(ruleId, message);
    }

    public enum BreakwaterField { REGION, ARM_INDEX, TOP_HEIGHT, BOTTOM_HEIGHT }

    public enum BreakwaterRegion {
        OUTSIDE(0), CREST(1), INNER_FOUNDATION(2), OUTER_FOUNDATION(3);

        private final int rawValue;
        BreakwaterRegion(int rawValue) { this.rawValue = rawValue; }
        public int rawValue() { return rawValue; }
    }

    public record BreakwaterSample(
            BreakwaterRegion region,
            int armIndex,
            int topHeightMillionths,
            int bottomHeightMillionths
    ) {
        static final BreakwaterSample OUTSIDE = new BreakwaterSample(
                BreakwaterRegion.OUTSIDE, 0, NO_DATA, NO_DATA);

        public BreakwaterSample { Objects.requireNonNull(region, "region"); }

        public int rawValue(BreakwaterField field) {
            return switch (Objects.requireNonNull(field, "field")) {
                case REGION -> region.rawValue();
                case ARM_INDEX -> armIndex;
                case TOP_HEIGHT -> topHeightMillionths;
                case BOTTOM_HEIGHT -> bottomHeightMillionths;
            };
        }
    }

    @FunctionalInterface
    public interface CellSource { BreakwaterSample sampleAt(int globalX, int globalZ); }

    public record BreakwaterMetrics(
            List<Long> armLengthsMillionths,
            long clearOpeningWidthMillionths,
            long crestCells,
            long innerFoundationCells,
            long outerFoundationCells,
            long solidBlocks
    ) { }

    private record ArmRuntime(
            BreakwaterHarborPlanV2.ArmPlan plan,
            BreakwaterPathKernelV2.ArmGeometry geometry
    ) { }

    private record Candidate(
            BreakwaterHarborPlanV2.ArmPlan arm,
            long distanceSquared,
            long cross,
            BreakwaterPathKernelV2.Segment segment
    ) { }
}
