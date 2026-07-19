package com.github.nankotsu029.landformcraft.validation.v2.volume;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.MetricResultV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.VolumeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.validation.v2.volume.VolumeFeatureSnapshotV2.VolumeFeatureKind;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * V2-5-15 independent volume validator.
 *
 * <p>It measures public {@link VolumeFeatureSnapshotV2} descriptors only and never imports
 * volume generators or allocates dense voxel grids. Feature traversal is sorted by featureId.</p>
 */
public final class VolumeValidatorV2 {
    public static final String VERSION = VolumeValidationArtifactV2.VALIDATOR_VERSION;
    public static final String VALIDATOR_ID = VolumeValidationArtifactV2.VALIDATOR_ID;

    public static final int MINIMUM_ROOF_BLOCKS = 2;
    public static final int MINIMUM_ARCH_CLEARANCE_BLOCKS = 2;
    public static final int MAXIMUM_MATERIAL_CLASS = 16;

    public VolumeValidationReportV2 validate(
            VolumeValidationInputV2 input,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        List<VolumeFeatureSnapshotV2> features = List.copyOf(input.features().features());
        if (features.size() > VolumeValidationInputV2.MAXIMUM_FEATURES) {
            throw new IllegalArgumentException("volume validation feature count exceeds budget");
        }
        features = features.stream()
                .sorted(Comparator.comparing(VolumeFeatureSnapshotV2::featureId))
                .toList();

        long isolatedCave = 0;
        long thinRoof = 0;
        long fluidLeak = 0;
        long floatingOverhang = 0;
        long brokenArch = 0;
        long mergedSky = 0;
        long fallDiscontinuity = 0;
        long solidFluidConflict = 0;
        long unknownMaterial = 0;
        long checksumMismatch = 0;
        long featureCount = features.size();

        for (int index = 0; index < features.size(); index++) {
            if ((index & 7) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }
            VolumeFeatureSnapshotV2 feature = Objects.requireNonNull(features.get(index), "feature");
            if (!input.sourcePlanChecksum().equals(feature.planChecksum())) {
                checksumMismatch++;
            }
            if (isIsolatedCave(feature)) {
                isolatedCave++;
            }
            if (isThinRoof(feature)) {
                thinRoof++;
            }
            if (feature.fluidLeakSamples() > 0) {
                fluidLeak++;
            }
            if (isFloatingOverhang(feature)) {
                floatingOverhang++;
            }
            if (isBrokenArch(feature)) {
                brokenArch++;
            }
            if (isMergedSkyIsland(feature)) {
                mergedSky++;
            }
            if (isFallDiscontinuity(feature)) {
                fallDiscontinuity++;
            }
            if (feature.solidFluidConflictSamples() > 0) {
                solidFluidConflict++;
            }
            if (isUnknownMaterial(feature)) {
                unknownMaterial++;
            }
        }

        List<MetricResultV2> metrics = new ArrayList<>();
        List<DiagnosticIssueV2> issues = new ArrayList<>();
        int[] sequence = {0};
        metric(metrics, issues, sequence, "volume-cave",
                "volume.cave.isolated-features", isolatedCave,
                "volume.topology.connectivity", "volume.cave.isolated-component");
        metric(metrics, issues, sequence, "volume-cave",
                "volume.cave.thin-roof-features", thinRoof,
                "volume.topology.roof", "volume.cave.thin-roof");
        metric(metrics, issues, sequence, "volume-fluid",
                "volume.fluid.leak-features", fluidLeak,
                "volume.fluid.leak", "volume.fluid.leak");
        metric(metrics, issues, sequence, "volume-overhang",
                "volume.overhang.floating-features", floatingOverhang,
                "volume.support.anchor", "volume.overhang.floating");
        metric(metrics, issues, sequence, "volume-arch",
                "volume.arch.broken-features", brokenArch,
                "volume.topology.clearance", "volume.arch.broken-clearance");
        metric(metrics, issues, sequence, "volume-sky",
                "volume.sky.merged-features", mergedSky,
                "volume.topology.component", "volume.sky.merged-islands");
        metric(metrics, issues, sequence, "volume-waterfall",
                "volume.waterfall.discontinuous-features", fallDiscontinuity,
                "volume.waterfall.continuity", "volume.waterfall.fall-discontinuity");
        metric(metrics, issues, sequence, "volume-fluid",
                "volume.fluid.solid-conflict-features", solidFluidConflict,
                "volume.occupancy.solid-fluid", "volume.fluid.solid-conflict");
        metric(metrics, issues, sequence, "volume-material",
                "volume.material.unknown-features", unknownMaterial,
                "volume.surface.class", "volume.material.unknown-class");
        metric(metrics, issues, sequence, "volume-plan",
                "volume.plan.checksum-mismatch-features", checksumMismatch,
                "volume.plan.checksum", "volume.plan.checksum-mismatch");
        metric(metrics, issues, sequence, "volume-summary",
                "volume.feature.count", featureCount,
                "volume.topology.component", "volume.feature.count", false);
        return new VolumeValidationReportV2(metrics, issues);
    }

    public VolumeValidationArtifactV2 toArtifact(
            String sourcePlanChecksum,
            VolumeValidationReportV2 report
    ) {
        Objects.requireNonNull(report, "report");
        return new VolumeValidationArtifactV2(
                sourcePlanChecksum,
                new VolumeValidationArtifactV2.VolumeValidationReport(
                        report.metrics(), report.issues()));
    }

    public static boolean isCaveKind(VolumeFeatureKind kind) {
        return kind == VolumeFeatureKind.CAVE_NETWORK
                || kind == VolumeFeatureKind.LUSH_CAVE
                || kind == VolumeFeatureKind.UNDERGROUND_LAKE
                || kind == VolumeFeatureKind.SEA_CAVE;
    }

    public static boolean isIsolatedCave(VolumeFeatureSnapshotV2 feature) {
        return isCaveKind(feature.kind())
                && (feature.connectedComponents() != 1 || !feature.entranceReachable());
    }

    public static boolean isThinRoof(VolumeFeatureSnapshotV2 feature) {
        return isCaveKind(feature.kind()) && feature.minRoofBlocks() < MINIMUM_ROOF_BLOCKS;
    }

    public static boolean isFloatingOverhang(VolumeFeatureSnapshotV2 feature) {
        return feature.kind() == VolumeFeatureKind.OVERHANG && !feature.supportPresent();
    }

    public static boolean isBrokenArch(VolumeFeatureSnapshotV2 feature) {
        return feature.kind() == VolumeFeatureKind.NATURAL_ARCH
                && (feature.clearanceBlocks() < MINIMUM_ARCH_CLEARANCE_BLOCKS
                || feature.connectedComponents() != 1);
    }

    public static boolean isMergedSkyIsland(VolumeFeatureSnapshotV2 feature) {
        return feature.kind() == VolumeFeatureKind.SKY_ISLAND_GROUP && feature.islandsMerged();
    }

    public static boolean isFallDiscontinuity(VolumeFeatureSnapshotV2 feature) {
        return feature.kind() == VolumeFeatureKind.WATERFALL_VOLUME && !feature.fallContinuous();
    }

    public static boolean isUnknownMaterial(VolumeFeatureSnapshotV2 feature) {
        return feature.materialClassCode() < 1 || feature.materialClassCode() > MAXIMUM_MATERIAL_CLASS;
    }

    public static boolean hasConstraintError(VolumeFeatureSnapshotV2 feature) {
        return isIsolatedCave(feature)
                || isThinRoof(feature)
                || feature.fluidLeakSamples() > 0
                || isFloatingOverhang(feature)
                || isBrokenArch(feature)
                || isMergedSkyIsland(feature)
                || isFallDiscontinuity(feature)
                || feature.solidFluidConflictSamples() > 0
                || isUnknownMaterial(feature);
    }

    private static void metric(
            List<MetricResultV2> metrics,
            List<DiagnosticIssueV2> issues,
            int[] sequence,
            String subject,
            String metricId,
            long actual,
            String layer,
            String ruleId
    ) {
        metric(metrics, issues, sequence, subject, metricId, actual, layer, ruleId, true);
    }

    private static void metric(
            List<MetricResultV2> metrics,
            List<DiagnosticIssueV2> issues,
            int[] sequence,
            String subject,
            String metricId,
            long actual,
            String layer,
            String ruleId,
            boolean hardZero
    ) {
        TerrainIntentV2.FixedRange expected = hardZero
                ? exact(0)
                : new TerrainIntentV2.FixedRange(0, Long.MAX_VALUE / 4);
        boolean passed = hardZero ? actual == 0 : actual >= 0;
        String checksum = evidence(metricId, subject, actual, expected, 0);
        metrics.add(new MetricResultV2(
                metricId, 1, subject, actual, expected, 0, "feature-count", passed, checksum));
        if (hardZero && !passed) {
            issues.add(new DiagnosticIssueV2(
                    "volume-" + (++sequence[0]),
                    ruleId,
                    1,
                    DiagnosticIssueV2.Severity.ERROR,
                    TerrainIntentV2.Strength.HARD,
                    List.of(new DiagnosticIssueV2.Reference(
                            DiagnosticIssueV2.ReferenceType.FIELD, layer)),
                    List.of(new DiagnosticIssueV2.MetricEvidence(
                            metricId, expected.minimumMillionths(), expected.maximumMillionths(),
                            actual, 0)),
                    ruleId,
                    List.of(layer)));
        }
    }

    private static TerrainIntentV2.FixedRange exact(long value) {
        return new TerrainIntentV2.FixedRange(value, value);
    }

    private static String evidence(
            String metric,
            String subject,
            long actual,
            TerrainIntentV2.FixedRange expected,
            long tolerance
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((VERSION + '\0' + metric + '\0' + subject + '\0')
                    .getBytes(StandardCharsets.UTF_8));
            for (long value : List.of(
                    actual, expected.minimumMillionths(), expected.maximumMillionths(), tolerance)) {
                for (int shift = 56; shift >= 0; shift -= 8) {
                    digest.update((byte) (value >>> shift));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
