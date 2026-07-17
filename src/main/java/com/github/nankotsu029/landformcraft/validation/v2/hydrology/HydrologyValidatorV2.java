package com.github.nankotsu029.landformcraft.validation.v2.hydrology;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.DeltaPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.FjordPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MetricResultV2;
import com.github.nankotsu029.landformcraft.model.v2.MountainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TidalChannelPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyFlowDirectionV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * V2-3-13 independent hydrology validator.
 *
 * <p>It measures a finalized field sampler and optional reconciliation artifact, and never imports
 * or calls a feature generator. Output-corruption fixtures can replace individual field values
 * without sharing a generator's local metric implementation. Scans are row-major or plan-ordered
 * and retain only fixed-size counters and result records.</p>
 */
public final class HydrologyValidatorV2 {
    public static final String VERSION = "hydrology-validator-v1";
    private static final int FIXED_SCALE = 1_000_000;

    public HydrologyValidationReportV2 validate(
            HydrologyValidationInputV2 input,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        WorldBlueprintV2 blueprint = input.blueprint();
        List<MetricResultV2> metrics = new ArrayList<>();
        List<DiagnosticIssueV2> issues = new ArrayList<>();
        int[] issueSequence = {0};

        for (MeanderingRiverPlanV2 plan : blueprint.meanderingRiverPlans()) {
            RiverEvidence evidence = measureRiver(input.fields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.river.channel-gaps", 1,
                    evidence.channelGaps(), exact(0), 0, "cell-count",
                    "hydrology.reach.graph", "hydrology.river.isolated-reach");
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.river.reverse-gradient-cells", 1,
                    evidence.reverseGradientCells(), exact(0), 0, "cell-count",
                    "hydrology.bed.elevation", "hydrology.river.reverse-gradient");
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.river.source-mouth-reachable", 1,
                    evidence.sourceToMouthReachable() ? 1 : 0, exact(1), 0, "boolean",
                    "hydrology.reach.graph", "hydrology.river.reachability");
        }
        FlowEvidence flow = measureFlowCycles(input.fields(), blueprint, cancellationToken);
        if (flow.sampledCells() > 0) {
            metric(metrics, issues, issueSequence, "hydrology-flow", "hydrology.flow.cycle-cells", 1,
                    flow.cycleCells(), exact(0), 0, "cell-count",
                    "hydrology.flow.direction", "hydrology.flow.cycle");
        }
        for (LakePlanV2 plan : blueprint.lakePlans()) {
            LakeEvidence evidence = measureLake(input.fields(), plan, cancellationToken);
            if (plan.terminalPolicy() == TerrainIntentV2.LakeTerminalPolicy.OPEN_SPILL) {
                metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.lake.spillway-cells", 1,
                        evidence.spillwayCells(), new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                        "cell-count", "hydrology.lake.rim-spill", "hydrology.lake.leaking");
            }
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.lake.basin-cells", 1,
                    evidence.basinCells(), new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                    "cell-count", "hydrology.lake.rim-spill", "hydrology.lake.basin");
        }
        for (DeltaPlanV2 plan : blueprint.deltaPlans()) {
            DeltaEvidence evidence = measureDelta(input.fields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.delta.mouth-connection", 1,
                    evidence.connectedMouths(), exact(plan.branches().size()), 0, "branch-count",
                    "hydrology.delta.distributary", "hydrology.delta.dead-branch");
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.delta.channel-cells", 1,
                    evidence.channelCells(), new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                    "cell-count", "hydrology.delta.distributary", "hydrology.delta.channel");
        }
        for (TidalChannelPlanV2 plan : blueprint.tidalChannelPlans()) {
            long marine = countMask(input.fields(), plan.marineConnectionFieldId(), cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.tidal.marine-cells", 1,
                    marine, new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0, "cell-count",
                    "hydrology.reach.graph", "hydrology.tidal.marine-connection");
        }
        for (FjordPlanV2 plan : blueprint.fjordPlans()) {
            FjordEvidence evidence = measureFjord(input.fields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.fjord.mouth-connection", 1,
                    evidence.mouthConnected() ? 1 : 0, exact(1), 0, "boolean",
                    "hydrology.fjord.thalweg", "hydrology.fjord.broken-outlet");
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.fjord.channel-cells", 1,
                    evidence.channelCells(), new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                    "cell-count", "hydrology.fjord.thalweg", "hydrology.fjord.channel");
        }
        for (WaterfallPlanV2 plan : blueprint.waterfallPlans()) {
            WaterfallEvidence evidence = measureWaterfall(input.fields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.waterfall.drop-millionths", 1,
                    evidence.dropMillionths(),
                    new TerrainIntentV2.FixedRange(
                            (long) plan.minimumDropBlocks() * FIXED_SCALE,
                            (long) plan.maximumDropBlocks() * FIXED_SCALE),
                    0, "block-millionths", "hydrology.waterfall.envelope", "hydrology.waterfall.fall-mismatch");
            require(evidence.lipCells() > 0 && evidence.baseCells() > 0 && evidence.plungeCells() > 0,
                    issues, issueSequence, plan.featureId(), "hydrology.waterfall.masks",
                    "hydrology.waterfall.masks", "hydrology.waterfall.envelope");
        }
        for (MountainPlanV2 plan : blueprint.mountainPlans()) {
            MountainEvidence evidence = measureMountain(input.fields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.mountain.ridge-cells", 1,
                    evidence.ridgeCells(), new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                    "cell-count", "hydrology.reach.graph", "hydrology.mountain.ridge");
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.mountain.peak-cells", 1,
                    evidence.peakCells(),
                    new TerrainIntentV2.FixedRange(Math.max(1, plan.selectedPeakCount()), Long.MAX_VALUE / 4),
                    0, "cell-count", "hydrology.reach.graph", "hydrology.mountain.peak");
        }
        for (VolcanicPlanV2 plan : blueprint.volcanicPlans()) {
            VolcanicEvidence evidence = measureVolcanic(input.fields(), plan, cancellationToken);
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.volcanic.island-cells", 1,
                    evidence.islandCells(), new TerrainIntentV2.FixedRange(1, Long.MAX_VALUE / 4), 0,
                    "cell-count", "hydrology.reach.graph", "hydrology.volcanic.island");
            metric(metrics, issues, issueSequence, plan.featureId(), "hydrology.volcanic.island-components", 1,
                    evidence.seenIslandIndexes(),
                    new TerrainIntentV2.FixedRange(plan.islands().size(), plan.islands().size()),
                    0, "component-count", "hydrology.reach.graph", "hydrology.volcanic.components");
        }
        input.reconciliation().ifPresent(artifact -> validateReconciliation(
                artifact, metrics, issues, issueSequence));
        return new HydrologyValidationReportV2(metrics, issues);
    }

    private static RiverEvidence measureRiver(
            HydrologyFieldSamplerV2 fields, MeanderingRiverPlanV2 plan, CancellationToken token
    ) {
        long gaps = 0;
        long reverse = 0;
        long previousBed = Long.MIN_VALUE / 4;
        boolean previousPresent = false;
        boolean sourcePresent = false;
        boolean mouthPresent = false;
        MeanderingRiverPlanV2.CenterlineSample first = plan.centerline().getFirst();
        MeanderingRiverPlanV2.CenterlineSample last = plan.centerline().getLast();
        for (MeanderingRiverPlanV2.CenterlineSample sample : plan.centerline()) {
            token.throwIfCancellationRequested();
            int x = block(sample.xMillionths());
            int z = block(sample.zMillionths());
            if (!inBounds(fields, x, z)) {
                gaps++;
                continue;
            }
            int channel = fields.valueAt(plan.channelMaskFieldId(), x, z);
            if (channel != 1) gaps++;
            int bed = fields.valueAt(plan.bedElevationFieldId(), x, z);
            if (bed != HydrologyValidationInputV2.NO_DATA) {
                if (previousPresent && bed > previousBed) reverse++;
                previousBed = bed;
                previousPresent = true;
            }
            if (x == block(first.xMillionths()) && z == block(first.zMillionths()) && channel == 1) {
                sourcePresent = true;
            }
            if (x == block(last.xMillionths()) && z == block(last.zMillionths()) && channel == 1) {
                mouthPresent = true;
            }
        }
        return new RiverEvidence(gaps, reverse, sourcePresent && mouthPresent && gaps == 0);
    }

    private static FlowEvidence measureFlowCycles(
            HydrologyFieldSamplerV2 fields, WorldBlueprintV2 blueprint, CancellationToken token
    ) {
        if (blueprint.meanderingRiverPlans().isEmpty()
                && blueprint.deltaPlans().isEmpty()
                && blueprint.tidalChannelPlans().isEmpty()
                && blueprint.fjordPlans().isEmpty()) {
            return new FlowEvidence(0, 0);
        }
        long sampled = 0;
        long cycles = 0;
        int width = fields.width();
        int length = fields.length();
        int maximumSteps = Math.multiplyExact(width, length);
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int directionCode = fields.valueAt(HydrologyIrModuleV2.FLOW_DIRECTION_FIELD, x, z);
                if (directionCode == HydrologyValidationInputV2.NO_DATA
                        || directionCode == HydrologyFlowDirectionV2.NO_DATA.code()
                        || directionCode == HydrologyFlowDirectionV2.TERMINAL.code()) {
                    continue;
                }
                sampled++;
                if (followsCycle(fields, x, z, maximumSteps)) cycles++;
            }
        }
        return new FlowEvidence(sampled, cycles);
    }

    private static boolean followsCycle(HydrologyFieldSamplerV2 fields, int startX, int startZ, int maximumSteps) {
        Set<Long> visited = new HashSet<>();
        int x = startX;
        int z = startZ;
        for (int step = 0; step < maximumSteps; step++) {
            long key = (((long) z) << 32) | (x & 0xffffffffL);
            if (!visited.add(key)) return true;
            int code = fields.valueAt(HydrologyIrModuleV2.FLOW_DIRECTION_FIELD, x, z);
            if (code == HydrologyValidationInputV2.NO_DATA
                    || code == HydrologyFlowDirectionV2.NO_DATA.code()
                    || code == HydrologyFlowDirectionV2.TERMINAL.code()) {
                return false;
            }
            HydrologyFlowDirectionV2 direction = HydrologyFlowDirectionV2.fromCode(code);
            int nextX = x + direction.deltaX();
            int nextZ = z + direction.deltaZ();
            if (!inBounds(fields, nextX, nextZ)) return false;
            x = nextX;
            z = nextZ;
        }
        return true;
    }

    private static LakeEvidence measureLake(
            HydrologyFieldSamplerV2 fields, LakePlanV2 plan, CancellationToken token
    ) {
        long basin = 0;
        long spillway = 0;
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                if (fields.valueAt(plan.basinMaskFieldId(), x, z) == 1) basin++;
                if (fields.valueAt(plan.spillwayMaskFieldId(), x, z) == 1) spillway++;
            }
        }
        return new LakeEvidence(basin, spillway);
    }

    private static DeltaEvidence measureDelta(
            HydrologyFieldSamplerV2 fields, DeltaPlanV2 plan, CancellationToken token
    ) {
        long channel = countMask(fields, plan.channelMaskFieldId(), token);
        long connected = 0;
        for (DeltaPlanV2.DistributaryBranch branch : plan.branches()) {
            token.throwIfCancellationRequested();
            DeltaPlanV2.FanPoint mouth = branch.path().getLast();
            int x = block(mouth.xMillionths());
            int z = block(mouth.zMillionths());
            if (inBounds(fields, x, z)
                    && (fields.valueAt(plan.channelMaskFieldId(), x, z) == 1
                    || fields.valueAt(plan.shallowSeaDepthFieldId(), x, z) > 0)) {
                connected++;
            }
        }
        return new DeltaEvidence(channel, connected);
    }

    private static FjordEvidence measureFjord(
            HydrologyFieldSamplerV2 fields, FjordPlanV2 plan, CancellationToken token
    ) {
        long channel = countMask(fields, plan.channelMaskFieldId(), token);
        FjordPlanV2.CenterlinePoint mouth = plan.centerline().getFirst();
        int x = block(mouth.xMillionths());
        int z = block(mouth.zMillionths());
        boolean connected = inBounds(fields, x, z) && fields.valueAt(plan.channelMaskFieldId(), x, z) == 1;
        return new FjordEvidence(channel, connected);
    }

    private static WaterfallEvidence measureWaterfall(
            HydrologyFieldSamplerV2 fields, WaterfallPlanV2 plan, CancellationToken token
    ) {
        int lipX = block(plan.lipXMillionths());
        int lipZ = block(plan.lipZMillionths());
        int baseX = block(plan.baseXMillionths());
        int baseZ = block(plan.baseZMillionths());
        long lipCells = 0;
        long baseCells = 0;
        long plungeCells = 0;
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                if (fields.valueAt(plan.lipMaskFieldId(), x, z) == 1) lipCells++;
                if (fields.valueAt(plan.baseMaskFieldId(), x, z) == 1) baseCells++;
                if (fields.valueAt(plan.plungePoolMaskFieldId(), x, z) == 1) plungeCells++;
            }
        }
        long lipElevation = fields.valueAt(plan.lipElevationFieldId(), lipX, lipZ);
        long baseElevation = fields.valueAt(plan.baseElevationFieldId(), baseX, baseZ);
        long drop = 0;
        if (lipElevation != HydrologyValidationInputV2.NO_DATA
                && baseElevation != HydrologyValidationInputV2.NO_DATA) {
            drop = Math.max(0L, lipElevation - baseElevation);
        }
        return new WaterfallEvidence(drop, lipCells, baseCells, plungeCells);
    }

    private static MountainEvidence measureMountain(
            HydrologyFieldSamplerV2 fields, MountainPlanV2 plan, CancellationToken token
    ) {
        long ridge = 0;
        long peak = 0;
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                if (fields.valueAt(plan.ridgeMaskFieldId(), x, z) == 1) ridge++;
                if (fields.valueAt(plan.peakMaskFieldId(), x, z) == 1) peak++;
            }
        }
        return new MountainEvidence(ridge, peak);
    }

    private static VolcanicEvidence measureVolcanic(
            HydrologyFieldSamplerV2 fields, VolcanicPlanV2 plan, CancellationToken token
    ) {
        long island = 0;
        Set<Integer> indexes = new HashSet<>();
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                if (fields.valueAt(plan.islandMaskFieldId(), x, z) == 1) {
                    island++;
                    int index = fields.valueAt(plan.islandIndexFieldId(), x, z);
                    if (index > 0) indexes.add(index);
                }
            }
        }
        return new VolcanicEvidence(island, indexes.size());
    }

    private static void validateReconciliation(
            HydrologyReconciliationArtifactV2 artifact,
            List<MetricResultV2> metrics,
            List<DiagnosticIssueV2> issues,
            int[] issueSequence
    ) {
        long unsatisfied = artifact.residuals().stream().filter(residual -> !residual.satisfied()).count();
        metric(metrics, issues, issueSequence, "hydrology-reconcile", "hydrology.reconcile.unsatisfied", 1,
                unsatisfied, exact(0), 0, "constraint-count",
                "hydrology.constraint.residual", "hydrology.reconcile.residual");
        require(artifact.status() == HydrologyReconciliationArtifactV2.Status.SATISFIED,
                issues, issueSequence, "hydrology-reconcile", "hydrology.reconcile.status",
                "hydrology.reconcile.status", "hydrology.constraint.residual");
    }

    private static long countMask(HydrologyFieldSamplerV2 fields, String fieldId, CancellationToken token) {
        long count = 0;
        for (int z = 0; z < fields.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < fields.width(); x++) {
                if (fields.valueAt(fieldId, x, z) == 1) count++;
            }
        }
        return count;
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
                "hydrology-" + sequence, ruleId, 1, DiagnosticIssueV2.Severity.ERROR,
                TerrainIntentV2.Strength.HARD,
                List.of(new DiagnosticIssueV2.Reference(DiagnosticIssueV2.ReferenceType.FEATURE, subject)),
                List.of(new DiagnosticIssueV2.MetricEvidence(
                        metric, expected.minimumMillionths(), expected.maximumMillionths(), actual, tolerance)),
                ruleId, List.of(layer));
    }

    private static TerrainIntentV2.FixedRange exact(long value) {
        return new TerrainIntentV2.FixedRange(value, value);
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

    private static int block(long millionths) {
        return Math.toIntExact(Math.floorDiv(millionths, FIXED_SCALE));
    }

    private static boolean inBounds(HydrologyFieldSamplerV2 fields, int x, int z) {
        return x >= 0 && z >= 0 && x < fields.width() && z < fields.length();
    }

    private record RiverEvidence(long channelGaps, long reverseGradientCells, boolean sourceToMouthReachable) { }
    private record FlowEvidence(long sampledCells, long cycleCells) { }
    private record LakeEvidence(long basinCells, long spillwayCells) { }
    private record DeltaEvidence(long channelCells, long connectedMouths) { }
    private record FjordEvidence(long channelCells, boolean mouthConnected) { }
    private record WaterfallEvidence(long dropMillionths, long lipCells, long baseCells, long plungeCells) { }
    private record MountainEvidence(long ridgeCells, long peakCells) { }
    private record VolcanicEvidence(long islandCells, int seenIslandIndexes) { }
}
