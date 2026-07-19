package com.github.nankotsu029.landformcraft.generator.v2.volume.skyisland;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.SkyIslandGroupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyIslandGroupGeneratorV2Test {
    private static final long M = 1_000_000L;

    @Test
    void componentClearanceGapAndSurfaceClassesPass() throws Exception {
        var compiled = positive();
        SkyIslandGroupGeneratorV2 generator = new SkyIslandGroupGeneratorV2(
                compiled.plan(), compiled.sdfPlan(), compiled.csgPlan());
        SkyIslandGroupGeneratorV2.SkyIslandGroupMetricsV2 metrics = generator.validate();
        assertEquals(2, metrics.componentCount());
        assertTrue(metrics.solidSamples() > 0);
        assertTrue(metrics.topSamples() > 0);
        assertTrue(metrics.undersideSamples() > 0);
        assertTrue(metrics.minGroundClearanceBlocks() >= 8);
        assertTrue(metrics.minInterIslandGapBlocks() >= 4);
        assertEquals(SkyIslandGroupPlanCompilerV2.LIFECYCLE, "SUPPORTED");

        Path example = Path.of("examples/v2/volume/sky-island-group-plan-v2.json");
        assertEquals(compiled.plan().canonicalChecksum(),
                new LandformV2DataCodec().readSkyIslandGroupPlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsMergedTouchingGroundOutOfYAndDenseFill() {
        SkyIslandGroupExceptionV2 merged = assertThrows(SkyIslandGroupExceptionV2.class,
                () -> SkyIslandGroupPlanCompilerV2.compile(
                        "sky.merged",
                        20,
                        0,
                        100,
                        List.of(
                                component("island.a", vec(-4, 40, 0), half(4, 4, 4),
                                        vec(-4, 38, 0), half(3, 2, 3)),
                                component("island.b", vec(2, 40, 0), half(4, 4, 4),
                                        vec(2, 38, 0), half(3, 2, 3))),
                        SkyIslandGroupPlanV2.Kernel.standard()));
        assertTrue(merged.failureCode() == SkyIslandGroupFailureCodeV2.MERGED_COMPONENTS
                || merged.failureCode() == SkyIslandGroupFailureCodeV2.INSUFFICIENT_GAP);

        SkyIslandGroupExceptionV2 ground = assertThrows(SkyIslandGroupExceptionV2.class,
                () -> SkyIslandGroupPlanCompilerV2.compile(
                        "sky.ground",
                        20,
                        0,
                        100,
                        List.of(component("island.low", vec(0, 24, 0), half(4, 4, 4),
                                vec(0, 22, 0), half(3, 2, 3))),
                        SkyIslandGroupPlanV2.Kernel.standard()));
        assertEquals(SkyIslandGroupFailureCodeV2.TOUCHING_GROUND, ground.failureCode());

        SkyIslandGroupExceptionV2 outOfY = assertThrows(SkyIslandGroupExceptionV2.class,
                () -> SkyIslandGroupPlanCompilerV2.compile(
                        "sky.oob",
                        20,
                        0,
                        40,
                        List.of(component("island.high", vec(0, 40, 0), half(4, 4, 4),
                                vec(0, 38, 0), half(3, 2, 3))),
                        SkyIslandGroupPlanV2.Kernel.standard()));
        assertEquals(SkyIslandGroupFailureCodeV2.OUT_OF_Y, outOfY.failureCode());

        SkyIslandGroupExceptionV2 dense = assertThrows(SkyIslandGroupExceptionV2.class,
                () -> SkyIslandGroupPlanCompilerV2.compile(
                        "sky.dense",
                        20,
                        0,
                        100,
                        List.of(component("island.fill", vec(0, 30, 0), half(4, 12, 4),
                                vec(0, 24, 0), half(3, 4, 3))),
                        SkyIslandGroupPlanV2.Kernel.standard()));
        assertTrue(dense.failureCode() == SkyIslandGroupFailureCodeV2.DENSE_AIR_FILL
                || dense.failureCode() == SkyIslandGroupFailureCodeV2.TOUCHING_GROUND);
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        var expected = positive();
        String checksum = new SkyIslandGroupGeneratorV2(
                expected.plan(), expected.sdfPlan(), expected.csgPlan()).metricChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.KOREA);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var again = positive();
            assertEquals(expected.plan().canonicalChecksum(), again.plan().canonicalChecksum());
            assertEquals(checksum, new SkyIslandGroupGeneratorV2(
                    again.plan(), again.sdfPlan(), again.csgPlan()).metricChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() -> {
                    var c = positive();
                    return new SkyIslandGroupGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan())
                            .metricChecksum();
                }).get());
                assertEquals(checksum, four.submit(() -> {
                    var c = positive();
                    return new SkyIslandGroupGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan())
                            .metricChecksum();
                }).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static SkyIslandGroupPlanCompilerV2.CompiledSkyIslandGroupV2 positive() {
        return SkyIslandGroupPlanCompilerV2.compile(
                "sky.fixture-group",
                20,
                0,
                100,
                List.of(
                        component("island.a", vec(-12, 40, 0), half(4, 4, 4),
                                vec(-12, 38, 0), half(3, 2, 3)),
                        component("island.b", vec(12, 42, 0), half(4, 4, 4),
                                vec(12, 40, 0), half(3, 2, 3))),
                SkyIslandGroupPlanV2.Kernel.standard());
    }

    private static SkyIslandGroupPlanV2.IslandComponent component(
            String id,
            VolumeSdfVec3V2 lobeCenter,
            VolumeSdfVec3V2 lobeHalf,
            VolumeSdfVec3V2 underCenter,
            VolumeSdfVec3V2 underHalf
    ) {
        return new SkyIslandGroupPlanV2.IslandComponent(
                id,
                new SkyIslandGroupPlanV2.BoxSpec(lobeCenter, lobeHalf, 0L),
                new SkyIslandGroupPlanV2.BoxSpec(underCenter, underHalf, 0L));
    }

    private static VolumeSdfVec3V2 vec(int x, int y, int z) {
        return new VolumeSdfVec3V2(x * M, y * M, z * M);
    }

    private static VolumeSdfVec3V2 half(int x, int y, int z) {
        return new VolumeSdfVec3V2(x * M, y * M, z * M);
    }
}
