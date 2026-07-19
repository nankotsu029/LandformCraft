package com.github.nankotsu029.landformcraft.generator.v2.volume.arch;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.NaturalArchPlanV2;
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

class NaturalArchGeneratorV2Test {
    private static final long M = 1_000_000L;

    @Test
    void pierCrownClearanceAndThroughOpeningPass() throws Exception {
        var compiled = positive();
        NaturalArchGeneratorV2 generator = new NaturalArchGeneratorV2(
                compiled.plan(), compiled.sdfPlan(), compiled.csgPlan());
        NaturalArchGeneratorV2.NaturalArchMetricsV2 metrics = generator.validate();
        assertTrue(metrics.solidSamples() > 0);
        assertTrue(metrics.leftPierSamples() > 0);
        assertTrue(metrics.rightPierSamples() > 0);
        assertTrue(metrics.crownSamples() > 0);
        assertTrue(metrics.clearanceSamples() >= 8);
        assertTrue(metrics.spanBlocks() >= 4);
        assertTrue(metrics.crownThicknessBlocks() >= 3);
        assertEquals(NaturalArchPlanCompilerV2.LIFECYCLE, "SUPPORTED");

        Path example = Path.of("examples/v2/volume/natural-arch-plan-v2.json");
        assertEquals(compiled.plan().canonicalChecksum(),
                new LandformV2DataCodec().readNaturalArchPlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsOnePierThinCrownClosedOpeningAndShortSpan() {
        NaturalArchExceptionV2 onePier = assertThrows(NaturalArchExceptionV2.class,
                () -> NaturalArchPlanCompilerV2.compile(
                        "arch.one-pier",
                        NaturalArchPlanV2.PassageAxis.Z,
                        vec(0, 12, 0),
                        half(12, 8, 4),
                        0L,
                        vec(-4, 10, 0),
                        half(8, 6, 6),
                        0L,
                        NaturalArchPlanV2.Kernel.standard()));
        assertEquals(NaturalArchFailureCodeV2.ONE_PIER, onePier.failureCode());

        NaturalArchExceptionV2 thinCrown = assertThrows(NaturalArchExceptionV2.class,
                () -> NaturalArchPlanCompilerV2.compile(
                        "arch.thin-crown",
                        NaturalArchPlanV2.PassageAxis.Z,
                        vec(0, 12, 0),
                        half(12, 8, 4),
                        0L,
                        vec(0, 11, 0),
                        half(6, 7, 6),
                        0L,
                        NaturalArchPlanV2.Kernel.standard()));
        assertEquals(NaturalArchFailureCodeV2.THIN_CROWN, thinCrown.failureCode());

        NaturalArchExceptionV2 closed = assertThrows(NaturalArchExceptionV2.class,
                () -> NaturalArchPlanCompilerV2.compile(
                        "arch.closed",
                        NaturalArchPlanV2.PassageAxis.Z,
                        vec(0, 12, 0),
                        half(12, 8, 4),
                        0L,
                        vec(0, 10, 0),
                        half(6, 6, 3),
                        0L,
                        NaturalArchPlanV2.Kernel.standard()));
        assertEquals(NaturalArchFailureCodeV2.CLOSED_OPENING, closed.failureCode());

        NaturalArchExceptionV2 shortSpan = assertThrows(NaturalArchExceptionV2.class,
                () -> NaturalArchPlanCompilerV2.compile(
                        "arch.short",
                        NaturalArchPlanV2.PassageAxis.Z,
                        vec(0, 12, 0),
                        half(12, 8, 4),
                        0L,
                        vec(0, 10, 0),
                        half(1, 6, 6),
                        0L,
                        NaturalArchPlanV2.Kernel.standard()));
        assertEquals(NaturalArchFailureCodeV2.SHORT_SPAN, shortSpan.failureCode());
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        var expected = positive();
        String checksum = new NaturalArchGeneratorV2(
                expected.plan(), expected.sdfPlan(), expected.csgPlan()).metricChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.KOREA);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var again = positive();
            assertEquals(expected.plan().canonicalChecksum(), again.plan().canonicalChecksum());
            assertEquals(checksum, new NaturalArchGeneratorV2(
                    again.plan(), again.sdfPlan(), again.csgPlan()).metricChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() -> {
                    var c = positive();
                    return new NaturalArchGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
                assertEquals(checksum, four.submit(() -> {
                    var c = positive();
                    return new NaturalArchGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static NaturalArchPlanCompilerV2.CompiledNaturalArchV2 positive() {
        return NaturalArchPlanCompilerV2.compile(
                "arch.fixture",
                NaturalArchPlanV2.PassageAxis.Z,
                vec(0, 12, 0),
                half(12, 8, 4),
                0L,
                vec(0, 10, 0),
                half(6, 6, 6),
                0L,
                NaturalArchPlanV2.Kernel.standard());
    }

    private static VolumeSdfVec3V2 vec(int x, int y, int z) {
        return new VolumeSdfVec3V2(x * M, y * M, z * M);
    }

    private static VolumeSdfVec3V2 half(int x, int y, int z) {
        return new VolumeSdfVec3V2(x * M, y * M, z * M);
    }
}
