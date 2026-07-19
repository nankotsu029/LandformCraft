package com.github.nankotsu029.landformcraft.generator.v2.volume.seacave;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.SeaCavePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaCaveGeneratorV2Test {
    private static final long M = 1_000_000L;

    @Test
    void marineOpeningFluidContinuityAndRoofPass() throws Exception {
        var compiled = positive();
        SeaCaveGeneratorV2 generator = new SeaCaveGeneratorV2(
                compiled.plan(), compiled.sdfPlan(), compiled.csgPlan());
        SeaCaveGeneratorV2.SeaCaveMetricsV2 metrics = generator.validate();
        assertTrue(metrics.carvedSamples() > 0);
        assertTrue(metrics.openingSamples() >= 2);
        assertTrue(metrics.fluidSamples() > 0);
        assertTrue(metrics.fluidAtOpening() > 0);
        assertEquals(0, metrics.leakInlandSamples());
        assertEquals(0, metrics.thinRoofSamples());
        assertEquals(0, metrics.breakthroughSamples());
        assertEquals(SeaCavePlanCompilerV2.LIFECYCLE, "SUPPORTED");

        Path example = Path.of("examples/v2/volume/sea-cave-plan-v2.json");
        assertEquals(compiled.plan().canonicalChecksum(),
                new LandformV2DataCodec().readSeaCavePlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsLandlockedUnsupportedLeakAndOpeningConflict() {
        VolumeSdfAabbV2 host = hostAabb();

        SeaCaveExceptionV2 landlocked = assertThrows(SeaCaveExceptionV2.class,
                () -> SeaCavePlanCompilerV2.compile(
                        "sea.landlocked",
                        "cliff.host",
                        SeaCavePlanV2.CardinalFace.WEST,
                        host,
                        vec(20, 10, 0),
                        vec(28, 10, 0),
                        4 * M,
                        10,
                        "fluid.sea",
                        28,
                        SeaCavePlanV2.Kernel.standard()));
        assertEquals(SeaCaveFailureCodeV2.LANDLOCKED, landlocked.failureCode());

        SeaCaveExceptionV2 unsupported = assertThrows(SeaCaveExceptionV2.class,
                () -> SeaCavePlanCompilerV2.compile(
                        "sea.unsupported",
                        "cliff.host",
                        SeaCavePlanV2.CardinalFace.WEST,
                        host,
                        vec(0, 10, 0),
                        vec(50, 10, 0),
                        4 * M,
                        10,
                        "fluid.sea",
                        28,
                        SeaCavePlanV2.Kernel.standard()));
        assertEquals(SeaCaveFailureCodeV2.UNSUPPORTED_HOST, unsupported.failureCode());

        var leak = SeaCavePlanCompilerV2.compile(
                "sea.leak",
                "cliff.host",
                SeaCavePlanV2.CardinalFace.WEST,
                host,
                vec(0, 10, 0),
                vec(36, 10, 0),
                8 * M,
                10,
                "fluid.sea",
                28,
                SeaCavePlanV2.Kernel.standard());
        SeaCaveExceptionV2 leaking = assertThrows(SeaCaveExceptionV2.class,
                () -> new SeaCaveGeneratorV2(leak.plan(), leak.sdfPlan(), leak.csgPlan()).validate());
        assertEquals(SeaCaveFailureCodeV2.LEAKING_INLAND, leaking.failureCode());
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        var expected = positive();
        String checksum = new SeaCaveGeneratorV2(
                expected.plan(), expected.sdfPlan(), expected.csgPlan()).metricChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.KOREA);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var again = positive();
            assertEquals(expected.plan().canonicalChecksum(), again.plan().canonicalChecksum());
            assertEquals(checksum, new SeaCaveGeneratorV2(
                    again.plan(), again.sdfPlan(), again.csgPlan()).metricChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() -> {
                    var c = positive();
                    return new SeaCaveGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
                assertEquals(checksum, four.submit(() -> {
                    var c = positive();
                    return new SeaCaveGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static SeaCavePlanCompilerV2.CompiledSeaCaveV2 positive() {
        return SeaCavePlanCompilerV2.compile(
                "sea.fixture-cave",
                "cliff.host",
                SeaCavePlanV2.CardinalFace.WEST,
                hostAabb(),
                vec(0, 10, 0),
                vec(20, 10, 0),
                4 * M,
                10,
                "fluid.sea-cave",
                28,
                SeaCavePlanV2.Kernel.standard());
    }

    private static VolumeSdfAabbV2 hostAabb() {
        return new VolumeSdfAabbV2(0, 0, -10 * M, 40 * M, 30 * M, 10 * M);
    }

    private static VolumeSdfVec3V2 vec(int x, int y, int z) {
        return new VolumeSdfVec3V2(x * M, y * M, z * M);
    }
}
