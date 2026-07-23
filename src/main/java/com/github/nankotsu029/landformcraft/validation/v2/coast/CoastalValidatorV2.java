package com.github.nankotsu029.landformcraft.validation.v2.coast;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.BreakwaterHarborPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.HarborBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MetricResultV2;
import com.github.nankotsu029.landformcraft.model.v2.RockyCapePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.SandyBeachPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * V2-2-08 independent coastal validator.
 *
 * <p>It measures a finalized field sampler and never imports or calls a feature generator. This
 * is intentional: output-corruption fixtures can replace individual field values without sharing
 * a generator's local metric implementation. All scans are row-major and retain only fixed-size
 * histograms, sets bounded by frozen descriptors, and result records.</p>
 */
public final class CoastalValidatorV2 {
    public static final String VERSION = "coastal-validator-v1";
    private static final int FIXED_SCALE = 1_000_000;
    private static final int CAPE_EXPOSURE_TOLERANCE = 10_000;
    /**
     * V2-18-05 breakwater clear-opening acceptance band. The field measurement (minimum centre-to-centre
     * distance between the two crest components on either side of the entrance) sits a few blocks
     * <em>above</em> the plan's edge-to-edge clear width: the two nearest crest cells lie inside their
     * structure edges, so their separation always exceeds the clear gap. The band is therefore one-sided
     * — the realized gap must be at least the planned clear width (allowing one block of lower rounding
     * tolerance) and no more than {@code UPPER_MARGIN} blocks above it. A blocked or narrowed opening
     * (measured below the plan, or a single/absent crest component reported as 0) fails; a grossly
     * over-wide opening also fails. Measured offsets on the shipped fixtures are +3.09 (400²) and +2.49
     * (64²) blocks, comfortably inside the band.
     */
    private static final long CLEAR_OPENING_UPPER_MARGIN = 6L * FIXED_SCALE;
    private static final long CLEAR_OPENING_LOWER_TOLERANCE = FIXED_SCALE;

    public CoastalValidationReportV2 validate(
            CoastalValidationInputV2 input,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        WorldBlueprintV2 blueprint = input.blueprint();
        List<MetricResultV2> metrics = new ArrayList<>();
        List<DiagnosticIssueV2> issues = new ArrayList<>();
        int[] issueSequence = {0};

        for (SandyBeachPlanV2 plan : blueprint.sandyBeachPlans()) {
            BeachEvidence evidence = measureBeach(input.actualFields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "coastal.beach.width-p50", 1,
                    evidence.widthP50Millionths(), range(plan.minimumWidthBlocks(), plan.maximumWidthBlocks()),
                    0, "block-millionths", "coastal.beach.overlay", "coastal.beach.width");
            require(evidence.foreshoreCells() > 0 && evidence.backshoreCells() > 0 && evidence.nearshoreCells() > 0,
                    issues, issueSequence, plan.featureId(), "v2.coastal.beach-bands", "coastal.beach.bands",
                    "coastal.beach.overlay");
        }
        for (HarborBasinPlanV2 plan : blueprint.harborBasinPlans()) {
            HarborEvidence evidence = measureHarbor(input.actualFields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "coastal.harbor.depth-p50", 1,
                    evidence.depthP50Millionths(), range(plan.minimumDepthBlocks(), plan.maximumDepthBlocks()),
                    0, "block-millionths", "coastal.harbor.overlay", "coastal.harbor.depth");
            metric(metrics, issues, issueSequence, plan.featureId(), "coastal.harbor.depth-max", 1,
                    evidence.maximumDepthMillionths(), exact((long) plan.maximumDepthBlocks() * FIXED_SCALE),
                    0, "block-millionths", "coastal.harbor.overlay", "coastal.harbor.depth");
            require(evidence.interiorCells() > 0 && evidence.entranceWaterCells() > 0 && evidence.outsideCells() > 0,
                    issues, issueSequence, plan.featureId(), "v2.coastal.harbor-opening", "coastal.harbor.opening",
                    "coastal.harbor.overlay");
        }
        for (BreakwaterHarborPlanV2 plan : blueprint.breakwaterHarborPlans()) {
            // V2-18-05: measure the realized clear opening from the generated field (minimum distance
            // between the two arms' crest cells) instead of comparing the plan value to itself.
            long measuredOpening = measureBreakwaterClearOpening(input.actualFields(), plan, cancellationToken);
            long planClearOpening = plan.actualClearOpeningWidthMillionths();
            metric(metrics, issues, issueSequence, plan.featureId(), "coastal.breakwater.clear-opening", 1,
                    measuredOpening,
                    new TerrainIntentV2.FixedRange(planClearOpening, planClearOpening + CLEAR_OPENING_UPPER_MARGIN),
                    CLEAR_OPENING_LOWER_TOLERANCE,
                    "block-millionths", "coastal.breakwater.overlay", "coastal.breakwater.opening");
        }
        for (RockyCapePlanV2 plan : blueprint.rockyCapePlans()) {
            CapeEvidence evidence = measureCape(input.actualFields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "coastal.cape.rock-exposure", 1,
                    evidence.exposureMillionths(), new TerrainIntentV2.FixedRange(
                            plan.minimumRockExposureMillionths(), plan.maximumRockExposureMillionths()),
                    CAPE_EXPOSURE_TOLERANCE, "ratio-millionths", "coastal.cape.overlay", "coastal.cape.exposure");
            metric(metrics, issues, issueSequence, plan.featureId(), "coastal.cape.complexity", 1,
                    evidence.seenDescriptors(), exact(plan.channels().size() + plan.seaStacks().size()), 0,
                    "descriptor-count", "coastal.cape.overlay", "coastal.cape.complexity");
            require(evidence.interiorCells() > 0 && evidence.cliffCells() > 0
                            && evidence.channelCells() > 0 && evidence.stackCells() > 0
                            && evidence.requiredDescriptorsSeen(),
                    issues, issueSequence, plan.featureId(), "v2.coastal.cape-complexity",
                    "coastal.cape.complexity", "coastal.cape.overlay");
        }
        validateTransitions(input.actualFields(), blueprint, cancellationToken, issues, issueSequence, metrics);
        validateResiduals(input, cancellationToken, metrics, issues, issueSequence);
        return new CoastalValidationReportV2(metrics, issues);
    }

    private static BeachEvidence measureBeach(
            CoastalFieldSamplerV2 fields, SandyBeachPlanV2 plan, CancellationToken token
    ) {
        long[] widths = new long[65];
        long foreshore = 0;
        long backshore = 0;
        long nearshore = 0;
        long count = 0;
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                int band = fields.valueAt(plan.bandFieldId(), x, z);
                if (band == SandyBeachGeneratorV2.BeachBand.FORESHORE.rawValue()) {
                    foreshore++;
                    int rawWidth = fields.valueAt(plan.localWidthFieldId(), x, z);
                    int width = Math.toIntExact(Math.max(1L, Math.min(64L, roundDivide(rawWidth, FIXED_SCALE))));
                    widths[width]++;
                    count++;
                } else if (band == SandyBeachGeneratorV2.BeachBand.BACKSHORE.rawValue()) {
                    backshore++;
                } else if (band == SandyBeachGeneratorV2.BeachBand.NEARSHORE.rawValue()) {
                    nearshore++;
                }
            }
        }
        return new BeachEvidence(count == 0 ? 0 : percentile50(widths, count) * (long) FIXED_SCALE,
                foreshore, backshore, nearshore);
    }

    private static HarborEvidence measureHarbor(
            CoastalFieldSamplerV2 fields, HarborBasinPlanV2 plan, CancellationToken token
    ) {
        long[] depths = new long[65];
        long interior = 0;
        long entranceWater = 0;
        long outside = 0;
        long water = 0;
        int maximum = 0;
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                int region = fields.valueAt(plan.regionFieldId(), x, z);
                int waterValue = fields.valueAt(plan.waterFieldId(), x, z);
                if (region == HarborBasinGeneratorV2.HarborRegion.INTERIOR.rawValue()) interior++;
                if (region == HarborBasinGeneratorV2.HarborRegion.ENTRANCE_CORRIDOR.rawValue() && waterValue == 1) {
                    entranceWater++;
                }
                if (region == HarborBasinGeneratorV2.HarborRegion.OUTSIDE.rawValue()) outside++;
                if (waterValue == 1) {
                    int depth = fields.valueAt(plan.depthFieldId(), x, z);
                    int rounded = Math.toIntExact(Math.max(1L, Math.min(64L, roundDivide(depth, FIXED_SCALE))));
                    depths[rounded]++;
                    water++;
                    maximum = Math.max(maximum, depth);
                }
            }
        }
        return new HarborEvidence(water == 0 ? 0 : percentile50(depths, water) * (long) FIXED_SCALE,
                maximum, interior, entranceWater, outside);
    }

    /**
     * V2-18-05 field measurement of the realized breakwater clear opening: the minimum centre-to-centre
     * distance (block-millionths) between the two crest structures on either side of the entrance. The
     * two arms are the two connected components of crest cells (the opening separates them); anything
     * other than exactly two components (a bridged/blocked or fragmented opening) returns 0 so the
     * metric fails closed. The composed field exposes only the crest region, so components are recovered
     * from it rather than from an arm-index field.
     */
    private static long measureBreakwaterClearOpening(
            CoastalFieldSamplerV2 fields, BreakwaterHarborPlanV2 plan, CancellationToken token
    ) {
        int crestValue = BreakwaterHarborGeneratorV2.BreakwaterRegion.CREST.rawValue();
        int width = fields.width();
        Set<Integer> crestCells = new HashSet<>();
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                if (fields.valueAt(plan.regionFieldId(), x, z) == crestValue) {
                    crestCells.add(z * width + x);
                }
            }
        }
        List<List<int[]>> components = crestComponents(crestCells, width, token);
        if (components.size() != 2) {
            return 0L;
        }
        long minSquared = Long.MAX_VALUE;
        for (int[] a : components.get(0)) {
            token.throwIfCancellationRequested();
            for (int[] b : components.get(1)) {
                long dx = (long) a[0] - b[0];
                long dz = (long) a[1] - b[1];
                minSquared = Math.min(minSquared, dx * dx + dz * dz);
            }
        }
        return integerSquareRoot(Math.multiplyExact(minSquared, (long) FIXED_SCALE * FIXED_SCALE));
    }

    /** 8-connected components of the crest-cell set, each as a list of {x, z}. */
    private static List<List<int[]>> crestComponents(Set<Integer> crestCells, int width, CancellationToken token) {
        List<List<int[]>> components = new ArrayList<>();
        Set<Integer> unvisited = new HashSet<>(crestCells);
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        while (!unvisited.isEmpty()) {
            token.throwIfCancellationRequested();
            Integer seed = unvisited.iterator().next();
            unvisited.remove(seed);
            frontier.add(seed);
            List<int[]> component = new ArrayList<>();
            while (!frontier.isEmpty()) {
                int cell = frontier.poll();
                int cx = cell % width;
                int cz = cell / width;
                component.add(new int[] {cx, cz});
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        int nx = cx + dx;
                        if (nx < 0 || nx >= width) {
                            continue;
                        }
                        int neighbour = (cz + dz) * width + nx;
                        if (unvisited.remove(neighbour)) {
                            frontier.add(neighbour);
                        }
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    /** Deterministic floor(sqrt(value)) for non-negative longs (no floating-point in the result). */
    private static long integerSquareRoot(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("integer square root of a negative value");
        }
        long guess = (long) Math.sqrt((double) value);
        while (guess > 0 && guess * guess > value) {
            guess--;
        }
        while ((guess + 1) * (guess + 1) <= value) {
            guess++;
        }
        return guess;
    }

    private static CapeEvidence measureCape(
            CoastalFieldSamplerV2 fields, RockyCapePlanV2 plan, CancellationToken token
    ) {
        long interior = 0;
        long cliff = 0;
        long channel = 0;
        long stack = 0;
        long eligible = 0;
        long exposed = 0;
        Set<Integer> descriptors = new HashSet<>();
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                int region = fields.valueAt(plan.regionFieldId(), x, z);
                if (region == RockyCapeGeneratorV2.CapeRegion.INTERIOR.rawValue()) interior++;
                else if (region == RockyCapeGeneratorV2.CapeRegion.CLIFF.rawValue()) cliff++;
                else if (region == RockyCapeGeneratorV2.CapeRegion.CHANNEL.rawValue()) {
                    channel++;
                    descriptors.add(fields.valueAt(plan.descriptorIndexFieldId(), x, z));
                } else if (region == RockyCapeGeneratorV2.CapeRegion.SEA_STACK.rawValue()) {
                    stack++;
                    descriptors.add(fields.valueAt(plan.descriptorIndexFieldId(), x, z));
                }
                if (region != RockyCapeGeneratorV2.CapeRegion.OUTSIDE.rawValue()
                        && region != RockyCapeGeneratorV2.CapeRegion.CHANNEL.rawValue()) {
                    eligible++;
                    exposed += fields.valueAt(plan.rockExposureFieldId(), x, z);
                }
            }
        }
        Set<Integer> expected = new HashSet<>();
        plan.channels().forEach(value -> expected.add(value.descriptorIndex()));
        plan.seaStacks().forEach(value -> expected.add(value.descriptorIndex()));
        int exposure = eligible == 0 ? 0 : Math.toIntExact(roundDivide(exposed * FIXED_SCALE, eligible));
        return new CapeEvidence(interior, cliff, channel, stack, exposure, descriptors.size(), descriptors.containsAll(expected));
    }

    private static void validateTransitions(
            CoastalFieldSamplerV2 fields,
            WorldBlueprintV2 blueprint,
            CancellationToken token,
            List<DiagnosticIssueV2> issues,
            int[] issueSequence,
            List<MetricResultV2> metrics
    ) {
        if (blueprint.coastalTransitionPlans().isEmpty()) return;
        var plan = blueprint.coastalTransitionPlans().getFirst();
        long conflict = 0;
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                if (fields.valueAt(plan.conflictFieldId(), x, z) != 0) conflict++;
            }
        }
        metric(metrics, issues, issueSequence, "coastal-transition", "coastal.transition.conflict-cells", 1,
                conflict, exact(0), 0, "cell-count", "coastal.constraint-errors", "coastal.transition.conflict");
    }

    private static void validateResiduals(
            CoastalValidationInputV2 input,
            CancellationToken token,
            List<MetricResultV2> metrics,
            List<DiagnosticIssueV2> issues,
            int[] issueSequence
    ) {
        long landWaterResidual = 0;
        long maximumHeightResidual = 0;
        for (int z = 0; z < input.actualFields().length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < input.actualFields().width(); x++) {
                int desiredLand = input.desiredFields().valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z);
                int actualLand = input.actualFields().valueAt(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, x, z);
                if (desiredLand != CoastalValidationInputV2.NO_DATA && actualLand != desiredLand) landWaterResidual++;
                int desiredHeight = input.desiredFields().valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z);
                int actualHeight = input.actualFields().valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z);
                if (desiredHeight != CoastalValidationInputV2.NO_DATA && actualHeight != CoastalValidationInputV2.NO_DATA) {
                    maximumHeightResidual = Math.max(maximumHeightResidual,
                            Math.abs((long) actualHeight - desiredHeight));
                }
            }
        }
        metric(metrics, issues, issueSequence, "coastal-transition", "coastal.land-water.residual-cells", 1,
                landWaterResidual, exact(0), 0, "cell-count", "coastal.constraint-errors", "coastal.land-water.residual");
        metric(metrics, issues, issueSequence, "coastal-transition", "coastal.height.residual-max", 1,
                maximumHeightResidual, new TerrainIntentV2.FixedRange(0, Long.MAX_VALUE / 4),
                Long.MAX_VALUE / 4, "block-millionths", "coastal.height.residual", "coastal.height.residual");
    }

    private static void metric(
            List<MetricResultV2> metrics,
            List<DiagnosticIssueV2> issues,
            int[] issueSequence,
            String subject,
            String metricId,
            int metricVersion,
            long actual,
            TerrainIntentV2.FixedRange expected,
            long tolerance,
            String unit,
            String layer,
            String ruleId
    ) {
        boolean passed = actual >= expected.minimumMillionths() - tolerance
                && actual <= expected.maximumMillionths() + tolerance;
        metrics.add(new MetricResultV2(metricId, metricVersion, subject, actual, expected, tolerance, unit,
                passed, evidence(metricId, subject, actual, expected, tolerance)));
        if (!passed) {
            issues.add(issue(issueSequence[0]++, ruleId, subject, metricId, actual, expected, tolerance, layer));
        }
    }

    private static void require(
            boolean condition,
            List<DiagnosticIssueV2> issues,
            int[] issueSequence,
            String subject,
            String ruleId,
            String metric,
            String layer
    ) {
        if (!condition) {
            issues.add(issue(issueSequence[0]++, ruleId, subject, metric, 0, exact(1), 0, layer));
        }
    }

    private static DiagnosticIssueV2 issue(
            int sequence,
            String ruleId,
            String subject,
            String metric,
            long actual,
            TerrainIntentV2.FixedRange expected,
            long tolerance,
            String layer
    ) {
        return new DiagnosticIssueV2(
                "coastal-" + sequence, ruleId, 1, DiagnosticIssueV2.Severity.ERROR,
                TerrainIntentV2.Strength.HARD,
                List.of(new DiagnosticIssueV2.Reference(DiagnosticIssueV2.ReferenceType.FEATURE, subject)),
                List.of(new DiagnosticIssueV2.MetricEvidence(
                        metric, expected.minimumMillionths(), expected.maximumMillionths(), actual, tolerance)),
                ruleId, List.of(layer));
    }

    private static TerrainIntentV2.FixedRange range(int minimumBlocks, int maximumBlocks) {
        return new TerrainIntentV2.FixedRange((long) minimumBlocks * FIXED_SCALE,
                (long) maximumBlocks * FIXED_SCALE);
    }

    private static TerrainIntentV2.FixedRange exact(long value) {
        return new TerrainIntentV2.FixedRange(value, value);
    }

    private static int percentile50(long[] histogram, long count) {
        long rank = (count + 1L) / 2L;
        long cumulative = 0;
        for (int index = 1; index < histogram.length; index++) {
            cumulative += histogram[index];
            if (cumulative >= rank) return index;
        }
        throw new IllegalStateException("empty coastal histogram");
    }

    private static long roundDivide(long numerator, long denominator) {
        if (numerator >= 0) return Math.floorDiv(Math.addExact(numerator, denominator / 2L), denominator);
        return -Math.floorDiv(Math.addExact(-numerator, denominator / 2L), denominator);
    }

    private static String evidence(
            String metric, String subject, long actual, TerrainIntentV2.FixedRange expected, long tolerance
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((VERSION + '\0' + metric + '\0' + subject + '\0').getBytes(StandardCharsets.UTF_8));
            for (long value : List.of(actual, expected.minimumMillionths(), expected.maximumMillionths(), tolerance)) {
                for (int shift = 56; shift >= 0; shift -= 8) digest.update((byte) (value >>> shift));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private record BeachEvidence(long widthP50Millionths, long foreshoreCells, long backshoreCells, long nearshoreCells) { }
    private record HarborEvidence(long depthP50Millionths, int maximumDepthMillionths, long interiorCells,
                                  long entranceWaterCells, long outsideCells) { }
    private record CapeEvidence(long interiorCells, long cliffCells, long channelCells, long stackCells,
                                int exposureMillionths, int seenDescriptors, boolean requiredDescriptorsSeen) { }
}
