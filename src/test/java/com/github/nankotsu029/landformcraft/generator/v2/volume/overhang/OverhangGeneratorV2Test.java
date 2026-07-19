package com.github.nankotsu029.landformcraft.generator.v2.volume.overhang;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.OverhangPlanV2;
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

class OverhangGeneratorV2Test {
    private static final long M = 1_000_000L;

    @Test
    void supportRoofClearanceAndSeawardOpeningPass() throws Exception {
        var compiled = positive();
        OverhangGeneratorV2 generator = new OverhangGeneratorV2(
                compiled.plan(), compiled.sdfPlan(), compiled.csgPlan());
        OverhangGeneratorV2.OverhangMetricsV2 metrics = generator.validate();
        assertTrue(metrics.solidSamples() > 0);
        assertTrue(metrics.supportSamples() >= 4);
        assertTrue(metrics.roofSamples() > 0);
        assertTrue(metrics.clearanceSamples() >= 8);
        assertTrue(metrics.seawardOpeningSamples() > 0);
        assertTrue(metrics.projectionBlocks() >= 4);
        assertTrue(metrics.roofThicknessBlocks() >= 3);
        assertEquals(OverhangPlanCompilerV2.LIFECYCLE, "SUPPORTED");

        Path example = Path.of("examples/v2/volume/overhang-plan-v2.json");
        assertEquals(compiled.plan().canonicalChecksum(),
                new LandformV2DataCodec().readOverhangPlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsFloatingThinBlockedAndMissingOpening() {
        VolumeSdfAabbV2 host = hostAabb();

        OverhangExceptionV2 floating = assertThrows(OverhangExceptionV2.class,
                () -> OverhangPlanCompilerV2.compile(
                        "overhang.floating",
                        "cliff.host",
                        OverhangPlanV2.CardinalFace.WEST,
                        host,
                        vec(-20, 14, 0),
                        half(6, 8, 6),
                        0L,
                        vec(-20, 10, 0),
                        half(6, 4, 6),
                        0L,
                        OverhangPlanV2.Kernel.standard()));
        assertEquals(OverhangFailureCodeV2.FLOATING_SLAB, floating.failureCode());

        OverhangExceptionV2 thin = assertThrows(OverhangExceptionV2.class,
                () -> OverhangPlanCompilerV2.compile(
                        "overhang.thin",
                        "cliff.host",
                        OverhangPlanV2.CardinalFace.WEST,
                        host,
                        vec(-6, 14, 0),
                        half(6, 8, 6),
                        0L,
                        vec(-6, 13, 0),
                        half(6, 7, 6),
                        0L,
                        OverhangPlanV2.Kernel.standard()));
        assertEquals(OverhangFailureCodeV2.THIN_ROOF, thin.failureCode());

        OverhangExceptionV2 blocked = assertThrows(OverhangExceptionV2.class,
                () -> OverhangPlanCompilerV2.compile(
                        "overhang.blocked",
                        "cliff.host",
                        OverhangPlanV2.CardinalFace.WEST,
                        host,
                        vec(-6, 14, 0),
                        half(6, 8, 6),
                        0L,
                        vec(8, 10, 0),
                        half(4, 4, 4),
                        0L,
                        OverhangPlanV2.Kernel.standard()));
        assertTrue(blocked.failureCode() == OverhangFailureCodeV2.BLOCKED_CORRIDOR
                || blocked.failureCode() == OverhangFailureCodeV2.MISSING_SEAWARD_OPENING);

        OverhangExceptionV2 shortProjection = assertThrows(OverhangExceptionV2.class,
                () -> OverhangPlanCompilerV2.compile(
                        "overhang.short",
                        "cliff.host",
                        OverhangPlanV2.CardinalFace.WEST,
                        host,
                        vec(-1, 14, 0),
                        half(1, 8, 6),
                        0L,
                        vec(-1, 10, 0),
                        half(1, 4, 6),
                        0L,
                        OverhangPlanV2.Kernel.standard()));
        assertEquals(OverhangFailureCodeV2.SHORT_PROJECTION, shortProjection.failureCode());
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        var expected = positive();
        String checksum = new OverhangGeneratorV2(
                expected.plan(), expected.sdfPlan(), expected.csgPlan()).metricChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.KOREA);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var again = positive();
            assertEquals(expected.plan().canonicalChecksum(), again.plan().canonicalChecksum());
            assertEquals(checksum, new OverhangGeneratorV2(
                    again.plan(), again.sdfPlan(), again.csgPlan()).metricChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() -> {
                    var c = positive();
                    return new OverhangGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
                assertEquals(checksum, four.submit(() -> {
                    var c = positive();
                    return new OverhangGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static OverhangPlanCompilerV2.CompiledOverhangV2 positive() {
        return OverhangPlanCompilerV2.compile(
                "overhang.fixture",
                "cliff.host",
                OverhangPlanV2.CardinalFace.WEST,
                hostAabb(),
                vec(-6, 14, 0),
                half(6, 8, 6),
                0L,
                vec(-6, 10, 0),
                half(6, 4, 6),
                0L,
                OverhangPlanV2.Kernel.standard());
    }

    private static VolumeSdfAabbV2 hostAabb() {
        return new VolumeSdfAabbV2(0, 0, -10 * M, 40 * M, 30 * M, 10 * M);
    }

    private static VolumeSdfVec3V2 vec(int x, int y, int z) {
        return new VolumeSdfVec3V2(x * M, y * M, z * M);
    }

    private static VolumeSdfVec3V2 half(int x, int y, int z) {
        return new VolumeSdfVec3V2(x * M, y * M, z * M);
    }
}
