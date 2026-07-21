package com.github.nankotsu029.landformcraft.validation.v2.environment;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.v2.DiagnosticIssueV2;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.MetricResultV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * V2-4-13 independent environment validator.
 *
 * <p>It measures a public {@link EnvironmentFieldSamplerV2} only and never imports feature
 * generators. Corruption fixtures can replace individual cell values without sharing generator
 * private metric code. Scans are row-major and retain only fixed-size counters.</p>
 */
public final class EnvironmentValidatorV2 {
    public static final String VERSION = EnvironmentValidationArtifactV2.VALIDATOR_VERSION;
    public static final String VALIDATOR_ID = EnvironmentValidationArtifactV2.VALIDATOR_ID;

    // Thresholds aligned with EcologyPlanV2.Kernel.standard() / MaterialProfilePlanV2.Kernel.standard().
    public static final int WARM_TEMPERATURE_RAW = 550;
    public static final int SNOW_COVER_THRESHOLD_RAW = 300;
    public static final int MANGROVE_MIN_SALINITY_RAW = 200;
    public static final int MANGROVE_MAX_SALINITY_RAW = 700;
    public static final int MANGROVE_MIN_HYDROPERIOD_RAW = 300;
    public static final int MANGROVE_MIN_WETNESS_RAW = 400;
    public static final int CORAL_MIN_TEMPERATURE_RAW = 600;
    public static final int CORAL_MIN_SALINITY_RAW = 700;
    public static final int CORAL_MIN_DEPTH_RAW = 50;
    public static final int CORAL_MAX_DEPTH_RAW = 400;
    public static final int HABITAT_MANGROVE = EcologyPlanV2.HabitatClass.MANGROVE_WETLAND.compactCode();
    public static final int HABITAT_CORAL = EcologyPlanV2.HabitatClass.CORAL_REEF.compactCode();
    public static final int FEATURE_VOLCANIC_BASALT =
            FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_BASALT_EXPOSED.compactCode();
    public static final int FEATURE_VOLCANIC_ASH =
            FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_ASH_EXPOSED.compactCode();
    public static final int FEATURE_CANYON_STRATA =
            FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_STRATA_EXPOSED.compactCode();
    public static final int FEATURE_CANYON_FLOOR =
            FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_FLOOR_SEDIMENT.compactCode();
    public static final long MAX_SCAN_CELLS = ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS;

    public EnvironmentValidationReportV2 validate(
            EnvironmentValidationInputV2 input,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        long cells = Math.multiplyExact((long) input.width(), input.length());
        if (cells > MAX_SCAN_CELLS) {
            throw new IllegalArgumentException("environment validation scan exceeds cell budget");
        }

        long wrongSnowline = 0;
        long badSalinity = 0;
        long badReefDepth = 0;
        long unsupportedRoot = 0;
        long badMaterialExposure = 0;
        long mangroveCells = 0;
        long coralCells = 0;
        long volcanicCells = 0;
        long canyonCells = 0;
        long strataVisible = 0;

        for (int z = 0; z < input.length(); z++) {
            if ((z & 31) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }
            for (int x = 0; x < input.width(); x++) {
                EnvironmentCellSnapshotV2 cell = Objects.requireNonNull(
                        input.fields().at(x, z), "environment cell");
                if (isWrongSnowline(cell)) {
                    wrongSnowline++;
                }
                if (isMangroveClaim(cell)) {
                    mangroveCells++;
                    if (isBadMangroveSalinity(cell)) {
                        badSalinity++;
                    }
                    if (isUnsupportedRoot(cell)) {
                        unsupportedRoot++;
                    }
                }
                if (isCoralClaim(cell)) {
                    coralCells++;
                    if (isBadReefDepth(cell)) {
                        badReefDepth++;
                    }
                }
                if (isVolcanicCell(cell)) {
                    volcanicCells++;
                }
                if (cell.canyonMask() == 1) {
                    canyonCells++;
                    if (cell.featureMaterialClassCode() == FEATURE_CANYON_STRATA
                            && cell.wallHeightMillionths() > 0) {
                        strataVisible++;
                    }
                }
                if (isBadMaterialExposure(cell)) {
                    badMaterialExposure++;
                }
            }
        }

        List<MetricResultV2> metrics = new ArrayList<>();
        List<DiagnosticIssueV2> issues = new ArrayList<>();
        int[] sequence = {0};
        metric(metrics, issues, sequence, "environment-global",
                "environment.snow.wrong-snowline-cells", wrongSnowline,
                "environment.snow.cover", "environment.snow.wrong-snowline");
        metric(metrics, issues, sequence, "environment-mangrove",
                "environment.mangrove.bad-salinity-cells", badSalinity,
                "environment.water.salinity", "environment.mangrove.salinity");
        metric(metrics, issues, sequence, "environment-coral",
                "environment.coral.bad-reef-depth-cells", badReefDepth,
                "environment.coral.depth", "environment.coral.reef-depth");
        metric(metrics, issues, sequence, "environment-mangrove",
                "environment.mangrove.unsupported-root-cells", unsupportedRoot,
                "environment.ecology.root-support", "environment.mangrove.root-support");
        metric(metrics, issues, sequence, "environment-material",
                "environment.material.bad-exposure-cells", badMaterialExposure,
                "environment.material.profile", "environment.material.exposure");
        metric(metrics, issues, sequence, "environment-summary",
                "environment.mangrove.habitat-cells", mangroveCells,
                "environment.ecology.habitat", "environment.mangrove.habitat", false);
        metric(metrics, issues, sequence, "environment-summary",
                "environment.coral.habitat-cells", coralCells,
                "environment.ecology.habitat", "environment.coral.habitat", false);
        metric(metrics, issues, sequence, "environment-summary",
                "environment.volcanic.island-cells", volcanicCells,
                "environment.material.feature", "environment.volcanic.coverage", false);
        metric(metrics, issues, sequence, "environment-summary",
                "environment.canyon.mask-cells", canyonCells,
                "environment.material.feature", "environment.canyon.coverage", false);
        metric(metrics, issues, sequence, "environment-summary",
                "environment.canyon.strata-visible-cells", strataVisible,
                "environment.geology.strata", "environment.canyon.strata", false);
        return new EnvironmentValidationReportV2(metrics, issues);
    }

    public EnvironmentValidationArtifactV2 toArtifact(
            String sourcePlanChecksum,
            EnvironmentValidationReportV2 report
    ) {
        Objects.requireNonNull(report, "report");
        return new EnvironmentValidationArtifactV2(
                sourcePlanChecksum,
                new EnvironmentValidationArtifactV2.EnvironmentValidationReport(
                        report.metrics(), report.issues()));
    }

    public static boolean isWrongSnowline(EnvironmentCellSnapshotV2 cell) {
        return cell.snowCoverRaw() >= SNOW_COVER_THRESHOLD_RAW
                && cell.temperatureRaw() >= WARM_TEMPERATURE_RAW;
    }

    public static boolean isMangroveClaim(EnvironmentCellSnapshotV2 cell) {
        return cell.habitatCode() == HABITAT_MANGROVE || cell.wetlandMask() == 1;
    }

    public static boolean isBadMangroveSalinity(EnvironmentCellSnapshotV2 cell) {
        return isMangroveClaim(cell)
                && (cell.salinityRaw() < MANGROVE_MIN_SALINITY_RAW
                || cell.salinityRaw() > MANGROVE_MAX_SALINITY_RAW);
    }

    public static boolean isUnsupportedRoot(EnvironmentCellSnapshotV2 cell) {
        boolean rootClaim = cell.wetlandMask() == 1 && cell.openWaterGap() == 0;
        return rootClaim && (cell.substrateWet() == 0
                || cell.hydroperiodRaw() < MANGROVE_MIN_HYDROPERIOD_RAW
                || cell.wetnessRaw() < MANGROVE_MIN_WETNESS_RAW);
    }

    public static boolean isCoralClaim(EnvironmentCellSnapshotV2 cell) {
        return cell.habitatCode() == HABITAT_CORAL || cell.reefMask() == 1;
    }

    public static boolean isBadReefDepth(EnvironmentCellSnapshotV2 cell) {
        if (!isCoralClaim(cell)) {
            return false;
        }
        boolean depthBad = cell.reefDepthRaw() < CORAL_MIN_DEPTH_RAW
                || cell.reefDepthRaw() > CORAL_MAX_DEPTH_RAW;
        boolean climateBad = cell.temperatureRaw() < CORAL_MIN_TEMPERATURE_RAW
                || cell.salinityRaw() < CORAL_MIN_SALINITY_RAW;
        return depthBad || climateBad;
    }

    public static boolean isVolcanicCell(EnvironmentCellSnapshotV2 cell) {
        int feature = cell.featureMaterialClassCode();
        return cell.islandMask() == 1
                || (feature >= FEATURE_VOLCANIC_BASALT && feature <= FEATURE_VOLCANIC_ASH);
    }

    public static boolean isBadMaterialExposure(EnvironmentCellSnapshotV2 cell) {
        int feature = cell.featureMaterialClassCode();
        if (feature != 0 && (feature < FEATURE_VOLCANIC_BASALT || feature > FEATURE_CANYON_FLOOR)) {
            return true;
        }
        if (feature == FEATURE_CANYON_STRATA && cell.lithologyCode() < 1) {
            return true;
        }
        if (feature >= FEATURE_VOLCANIC_BASALT && feature <= FEATURE_VOLCANIC_ASH
                && cell.islandMask() == 0) {
            return true;
        }
        return feature >= FEATURE_CANYON_STRATA && feature <= FEATURE_CANYON_FLOOR
                && cell.canyonMask() == 0;
    }

    public static boolean hasConstraintError(EnvironmentCellSnapshotV2 cell) {
        return isWrongSnowline(cell)
                || isBadMangroveSalinity(cell)
                || isBadReefDepth(cell)
                || isUnsupportedRoot(cell)
                || isBadMaterialExposure(cell);
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
                metricId, 1, subject, actual, expected, 0, "cell-count", passed, checksum));
        if (hardZero && !passed) {
            issues.add(new DiagnosticIssueV2(
                    "environment-" + (++sequence[0]),
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
