package com.github.nankotsu029.landformcraft.validation.v2.environment;

import com.github.nankotsu029.landformcraft.format.v2.validation.EnvironmentValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentValidationArtifactV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentValidatorV2Test {
    private static final String CHECKSUM = "a".repeat(64);

    @TempDir
    Path directory;

    @Test
    void acceptsHealthyFixtureAndIsDeterministicAcrossTileLocaleAndTimezone() {
        EnvironmentFieldSamplerV2 fields = healthy(8, 6);
        EnvironmentValidationReportV2 report = validate(8, 6, fields);
        assertTrue(report.passesHardValidation(), () -> report.issues().toString());
        assertEquals(10, report.metrics().size());

        assertEquals(report, validate(8, 6, tiled(fields, 3)));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(report, validate(8, 6, healthy(8, 6)));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void detectsWrongSnowlineSalinityReefDepthRootSupportAndMaterialExposureIndependently() {
        assertIssue(mutate(healthy(4, 4), cell -> snapshot(
                600, cell.moistureRaw(), cell.wetnessRaw(), cell.salinityRaw(),
                cell.hydroperiodRaw(), 800, cell.habitatCode(), cell.materialClassCode(),
                cell.featureMaterialClassCode(), cell.lithologyCode(), cell.wetlandMask(),
                cell.openWaterGap(), cell.substrateWet(), cell.reefMask(), cell.reefDepthRaw(),
                cell.islandMask(), cell.canyonMask(), cell.wallHeightMillionths()
        )), "environment.snow.wrong-snowline");

        assertIssue(mutate(healthy(4, 4), cell -> snapshot(
                cell.temperatureRaw(), cell.moistureRaw(), cell.wetnessRaw(), 50,
                cell.hydroperiodRaw(), cell.snowCoverRaw(), 1, cell.materialClassCode(),
                cell.featureMaterialClassCode(), cell.lithologyCode(), 1, 0, 1, 0, 0, 0, 0, 0
        )), "environment.mangrove.salinity");

        assertIssue(mutate(healthy(4, 4), cell -> snapshot(
                700, cell.moistureRaw(), cell.wetnessRaw(), 800, cell.hydroperiodRaw(),
                cell.snowCoverRaw(), 2, cell.materialClassCode(), cell.featureMaterialClassCode(),
                cell.lithologyCode(), 0, 0, 0, 1, 20, 0, 0, 0
        )), "environment.coral.reef-depth");

        assertIssue(mutate(healthy(4, 4), cell -> snapshot(
                cell.temperatureRaw(), cell.moistureRaw(), 100, 400, 100, cell.snowCoverRaw(),
                1, cell.materialClassCode(), cell.featureMaterialClassCode(), cell.lithologyCode(),
                1, 0, 0, 0, 0, 0, 0, 0
        )), "environment.mangrove.root-support");

        assertIssue(mutate(healthy(4, 4), cell -> snapshot(
                cell.temperatureRaw(), cell.moistureRaw(), cell.wetnessRaw(), cell.salinityRaw(),
                cell.hydroperiodRaw(), cell.snowCoverRaw(), cell.habitatCode(), cell.materialClassCode(),
                10, 0, 0, 0, 0, 0, 0, 0, 1, 2_000_000
        )), "environment.material.exposure");
    }

    @Test
    void sealsValidationArtifactWithStrictRoundTripAndRejectsTampering() throws Exception {
        EnvironmentValidatorV2 validator = new EnvironmentValidatorV2();
        EnvironmentValidationReportV2 report = validate(5, 5, healthy(5, 5));
        EnvironmentValidationArtifactV2 sealed = new EnvironmentValidationArtifactCodecV2().seal(
                validator.toArtifact(CHECKSUM, report));
        Path path = directory.resolve("environment-validation.json");
        new EnvironmentValidationArtifactCodecV2().write(path, sealed);
        assertEquals(sealed, new EnvironmentValidationArtifactCodecV2().read(path));

        String json = Files.readString(path);
        Files.writeString(path, json.replaceFirst("a{64}", "b".repeat(64)));
        assertThrows(Exception.class, () -> new EnvironmentValidationArtifactCodecV2().read(path));
    }

    @Test
    void cancelStopsScanWithoutPublishingPartialResults() {
        AtomicInteger checks = new AtomicInteger();
        assertThrows(java.util.concurrent.CancellationException.class, () ->
                new EnvironmentValidatorV2().validate(
                        new EnvironmentValidationInputV2(64, 64, CHECKSUM, healthy(64, 64)),
                        () -> checks.incrementAndGet() > 1));
    }

    @Test
    void rejectsOversizeScanBudget() {
        assertThrows(IllegalArgumentException.class, () ->
                new EnvironmentValidationInputV2(1_025, 1, CHECKSUM, (x, z) -> healthyCell()));
    }

    private static void assertIssue(EnvironmentFieldSamplerV2 sampler, String ruleId) {
        EnvironmentValidationReportV2 report = validate(4, 4, sampler);
        assertFalse(report.passesHardValidation());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.ruleId().equals(ruleId)),
                () -> report.issues().toString());
    }

    private static EnvironmentValidationReportV2 validate(
            int width,
            int length,
            EnvironmentFieldSamplerV2 sampler
    ) {
        return new EnvironmentValidatorV2().validate(
                new EnvironmentValidationInputV2(width, length, CHECKSUM, sampler), () -> false);
    }

    private static EnvironmentFieldSamplerV2 healthy(int width, int length) {
        return (x, z) -> {
            if (x < 0 || z < 0 || x >= width || z >= length) {
                throw new IllegalArgumentException("out of bounds");
            }
            return healthyCell();
        };
    }

    private static EnvironmentCellSnapshotV2 healthyCell() {
        return snapshot(400, 500, 500, 400, 500, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static EnvironmentFieldSamplerV2 mutate(
            EnvironmentFieldSamplerV2 base,
            UnaryOperator<EnvironmentCellSnapshotV2> mutation
    ) {
        return (x, z) -> mutation.apply(base.at(x, z));
    }

    private static EnvironmentFieldSamplerV2 tiled(EnvironmentFieldSamplerV2 base, int tileSize) {
        return (x, z) -> {
            int tileX = x / tileSize;
            int tileZ = z / tileSize;
            int localX = x - tileX * tileSize;
            int localZ = z - tileZ * tileSize;
            return base.at(tileX * tileSize + localX, tileZ * tileSize + localZ);
        };
    }

    private static EnvironmentCellSnapshotV2 snapshot(
            int temperatureRaw,
            int moistureRaw,
            int wetnessRaw,
            int salinityRaw,
            int hydroperiodRaw,
            int snowCoverRaw,
            int habitatCode,
            int materialClassCode,
            int featureMaterialClassCode,
            int lithologyCode,
            int wetlandMask,
            int openWaterGap,
            int substrateWet,
            int reefMask,
            int reefDepthRaw,
            int islandMask,
            int canyonMask,
            int wallHeightMillionths
    ) {
        return new EnvironmentCellSnapshotV2(
                temperatureRaw, moistureRaw, wetnessRaw, salinityRaw, hydroperiodRaw, snowCoverRaw,
                habitatCode, materialClassCode, featureMaterialClassCode, lithologyCode, wetlandMask,
                openWaterGap, substrateWet, reefMask, reefDepthRaw, islandMask, canyonMask,
                wallHeightMillionths);
    }
}
