package com.github.nankotsu029.landformcraft.validation.v2.volume;

import com.github.nankotsu029.landformcraft.format.v2.validation.VolumeValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.VolumeValidationArtifactV2;
import com.github.nankotsu029.landformcraft.validation.v2.volume.VolumeFeatureSnapshotV2.VolumeFeatureKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeValidatorV2Test {
    private static final String CHECKSUM = "a".repeat(64);

    @TempDir
    Path directory;

    @Test
    void acceptsHealthyFixtureAndIsDeterministicAcrossOrderLocaleAndTimezone() {
        VolumeFeatureSamplerV2 features = healthyPortfolio();
        VolumeValidationReportV2 report = validate(8, 6, features);
        assertTrue(report.passesHardValidation(), () -> report.issues().toString());
        assertEquals(11, report.metrics().size());

        VolumeFeatureSamplerV2 reversed = () -> {
            List<VolumeFeatureSnapshotV2> list = new ArrayList<>(features.features());
            java.util.Collections.reverse(list);
            return list;
        };
        assertEquals(report, validate(8, 6, reversed));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(report, validate(8, 6, healthyPortfolio()));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void detectsIsolatedCaveThinRoofLeakFloatingOverhangBrokenArchMergedSkyAndFallDiscontinuity() {
        assertIssue(mutate("cave-network-a", feature -> new VolumeFeatureSnapshotV2(
                feature.kind(), feature.featureId(), feature.planChecksum(), 2, false,
                feature.minRoofBlocks(), feature.supportPresent(), feature.clearanceBlocks(),
                feature.componentCount(), feature.fluidLeakSamples(), feature.solidFluidConflictSamples(),
                feature.fallContinuous(), feature.islandsMerged(), feature.materialClassCode()
        )), "volume.cave.isolated-component");

        assertIssue(mutate("cave-network-a", feature -> new VolumeFeatureSnapshotV2(
                feature.kind(), feature.featureId(), feature.planChecksum(), feature.connectedComponents(),
                feature.entranceReachable(), 1, feature.supportPresent(), feature.clearanceBlocks(),
                feature.componentCount(), feature.fluidLeakSamples(), feature.solidFluidConflictSamples(),
                feature.fallContinuous(), feature.islandsMerged(), feature.materialClassCode()
        )), "volume.cave.thin-roof");

        assertIssue(mutate("underground-lake-a", feature -> new VolumeFeatureSnapshotV2(
                feature.kind(), feature.featureId(), feature.planChecksum(), feature.connectedComponents(),
                feature.entranceReachable(), feature.minRoofBlocks(), feature.supportPresent(),
                feature.clearanceBlocks(), feature.componentCount(), 3, feature.solidFluidConflictSamples(),
                feature.fallContinuous(), feature.islandsMerged(), feature.materialClassCode()
        )), "volume.fluid.leak");

        assertIssue(mutate("overhang-a", feature -> new VolumeFeatureSnapshotV2(
                feature.kind(), feature.featureId(), feature.planChecksum(), feature.connectedComponents(),
                feature.entranceReachable(), feature.minRoofBlocks(), false, feature.clearanceBlocks(),
                feature.componentCount(), feature.fluidLeakSamples(), feature.solidFluidConflictSamples(),
                feature.fallContinuous(), feature.islandsMerged(), feature.materialClassCode()
        )), "volume.overhang.floating");

        assertIssue(mutate("natural-arch-a", feature -> new VolumeFeatureSnapshotV2(
                feature.kind(), feature.featureId(), feature.planChecksum(), feature.connectedComponents(),
                feature.entranceReachable(), feature.minRoofBlocks(), feature.supportPresent(), 0,
                feature.componentCount(), feature.fluidLeakSamples(), feature.solidFluidConflictSamples(),
                feature.fallContinuous(), feature.islandsMerged(), feature.materialClassCode()
        )), "volume.arch.broken-clearance");

        assertIssue(mutate("sky-island-a", feature -> new VolumeFeatureSnapshotV2(
                feature.kind(), feature.featureId(), feature.planChecksum(), feature.connectedComponents(),
                feature.entranceReachable(), feature.minRoofBlocks(), feature.supportPresent(),
                feature.clearanceBlocks(), feature.componentCount(), feature.fluidLeakSamples(),
                feature.solidFluidConflictSamples(), feature.fallContinuous(), true,
                feature.materialClassCode()
        )), "volume.sky.merged-islands");

        assertIssue(mutate("waterfall-a", feature -> new VolumeFeatureSnapshotV2(
                feature.kind(), feature.featureId(), feature.planChecksum(), feature.connectedComponents(),
                feature.entranceReachable(), feature.minRoofBlocks(), feature.supportPresent(),
                feature.clearanceBlocks(), feature.componentCount(), feature.fluidLeakSamples(),
                feature.solidFluidConflictSamples(), false, feature.islandsMerged(),
                feature.materialClassCode()
        )), "volume.waterfall.fall-discontinuity");
    }

    @Test
    void sealsValidationArtifactWithStrictRoundTripAndRejectsTampering() throws Exception {
        VolumeValidatorV2 validator = new VolumeValidatorV2();
        VolumeValidationReportV2 report = validate(5, 5, healthyPortfolio());
        VolumeValidationArtifactV2 sealed = new VolumeValidationArtifactCodecV2().seal(
                validator.toArtifact(CHECKSUM, report));
        Path path = directory.resolve("volume-validation.json");
        new VolumeValidationArtifactCodecV2().write(path, sealed);
        assertEquals(sealed, new VolumeValidationArtifactCodecV2().read(path));

        String json = Files.readString(path);
        Files.writeString(path, json.replaceFirst("a{64}", "b".repeat(64)));
        assertThrows(Exception.class, () -> new VolumeValidationArtifactCodecV2().read(path));
    }

    @Test
    void cancelStopsScanWithoutPublishingPartialResults() {
        AtomicInteger checks = new AtomicInteger();
        List<VolumeFeatureSnapshotV2> many = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            many.add(healthy(VolumeFeatureKind.CAVE_NETWORK, "cave-" + index));
        }
        assertThrows(java.util.concurrent.CancellationException.class, () ->
                new VolumeValidatorV2().validate(
                        new VolumeValidationInputV2(8, 8, CHECKSUM, () -> many),
                        () -> checks.incrementAndGet() > 1));
    }

    @Test
    void rejectsOversizeFeatureBudget() {
        List<VolumeFeatureSnapshotV2> many = new ArrayList<>();
        for (int index = 0; index < VolumeValidationInputV2.MAXIMUM_FEATURES + 1; index++) {
            many.add(healthy(VolumeFeatureKind.CAVE_NETWORK, "cave-" + index));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new VolumeValidatorV2().validate(
                        new VolumeValidationInputV2(8, 8, CHECKSUM, () -> many), () -> false));
    }

    @Test
    void exampleArtifactRoundTrips() throws Exception {
        Path example = Path.of("examples/v2/volume/volume-validation-artifact-v2.json");
        VolumeValidationArtifactV2 artifact = new VolumeValidationArtifactCodecV2().read(example);
        assertTrue(artifact.report().passesHardValidation());
        assertEquals(VolumeValidationArtifactV2.VALIDATOR_ID, artifact.validatorId());
    }

    private static void assertIssue(VolumeFeatureSamplerV2 sampler, String ruleId) {
        VolumeValidationReportV2 report = validate(4, 4, sampler);
        assertFalse(report.passesHardValidation());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.ruleId().equals(ruleId)),
                () -> report.issues().toString());
    }

    private static VolumeValidationReportV2 validate(
            int width,
            int length,
            VolumeFeatureSamplerV2 sampler
    ) {
        return new VolumeValidatorV2().validate(
                new VolumeValidationInputV2(width, length, CHECKSUM, sampler), () -> false);
    }

    private static VolumeFeatureSamplerV2 healthyPortfolio() {
        return () -> List.of(
                healthy(VolumeFeatureKind.CAVE_NETWORK, "cave-network-a"),
                healthy(VolumeFeatureKind.LUSH_CAVE, "lush-cave-a"),
                healthy(VolumeFeatureKind.UNDERGROUND_LAKE, "underground-lake-a"),
                healthy(VolumeFeatureKind.SEA_CAVE, "sea-cave-a"),
                healthy(VolumeFeatureKind.OVERHANG, "overhang-a"),
                healthy(VolumeFeatureKind.NATURAL_ARCH, "natural-arch-a"),
                healthy(VolumeFeatureKind.SKY_ISLAND_GROUP, "sky-island-a"),
                healthy(VolumeFeatureKind.WATERFALL_VOLUME, "waterfall-a"));
    }

    private static VolumeFeatureSamplerV2 mutate(String featureId, UnaryOperator<VolumeFeatureSnapshotV2> mutation) {
        return () -> healthyPortfolio().features().stream()
                .map(feature -> feature.featureId().equals(featureId) ? mutation.apply(feature) : feature)
                .toList();
    }

    private static VolumeFeatureSnapshotV2 healthy(VolumeFeatureKind kind, String featureId) {
        return new VolumeFeatureSnapshotV2(
                kind, featureId, CHECKSUM, 1, true, 4, true, 4, 1, 0, 0, true, false, 1);
    }
}
