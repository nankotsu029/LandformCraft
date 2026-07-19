package com.github.nankotsu029.landformcraft.generator.v2.volume.waterfall;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import com.github.nankotsu029.landformcraft.model.v2.volume.WaterfallVolumePlanV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterfallVolumeGeneratorV2Test {
    private static final long M = 1_000_000L;
    private static final String GEOMETRY =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void lipColumnPoolContinuityAndBehindClearancePass() throws Exception {
        var compiled = positive();
        WaterfallVolumeGeneratorV2 generator = new WaterfallVolumeGeneratorV2(
                compiled.plan(), compiled.sdfPlan(), compiled.csgPlan());
        WaterfallVolumeGeneratorV2.WaterfallVolumeMetricsV2 metrics = generator.validate();
        assertTrue(metrics.columnFluidSamples() >= 4);
        assertTrue(metrics.poolFluidSamples() >= 4);
        assertTrue(metrics.behindClearanceSamples() >= 4);
        assertEquals(0, metrics.leakSamples());
        assertEquals(WaterfallVolumePlanCompilerV2.LIFECYCLE, "SUPPORTED");

        Path example = Path.of("examples/v2/volume/waterfall-volume-plan-v2.json");
        assertEquals(compiled.plan().canonicalChecksum(),
                new LandformV2DataCodec().readWaterfallVolumePlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsOffsetLeakMissingPoolAndChecksumMismatch() {
        VolumeSdfVec3V2 lip = vec(0, 40, 0);
        VolumeSdfVec3V2 base = vec(0, 10, 6);

        WaterfallVolumeExceptionV2 offset = assertThrows(WaterfallVolumeExceptionV2.class,
                () -> WaterfallVolumePlanCompilerV2.compile(
                        "fall.offset",
                        "node.fall",
                        "waterfall.fixture",
                        GEOMETRY,
                        lip,
                        base,
                        vec(4, 40, 0),
                        base,
                        2 * M,
                        behindSpec(),
                        poolSpec(base),
                        "fluid.waterfall",
                        WaterfallVolumePlanV2.Kernel.standard()));
        assertEquals(WaterfallVolumeFailureCodeV2.OFFSET_COLUMN, offset.failureCode());

        WaterfallVolumeExceptionV2 missingBehind = assertThrows(WaterfallVolumeExceptionV2.class,
                () -> WaterfallVolumePlanCompilerV2.compile(
                        "fall.no-behind",
                        "node.fall",
                        "waterfall.fixture",
                        GEOMETRY,
                        lip,
                        base,
                        lip,
                        base,
                        2 * M,
                        new WaterfallVolumePlanV2.BehindFallSpec(
                                vec(0, 25, 8),
                                vec(3, 12, 2),
                                M),
                        poolSpec(base),
                        "fluid.waterfall",
                        WaterfallVolumePlanV2.Kernel.standard()));
        assertEquals(WaterfallVolumeFailureCodeV2.MISSING_BEHIND_CLEARANCE, missingBehind.failureCode());

        WaterfallVolumeExceptionV2 missingPool = assertThrows(WaterfallVolumeExceptionV2.class,
                () -> WaterfallVolumePlanCompilerV2.compile(
                        "fall.no-pool",
                        "node.fall",
                        "waterfall.fixture",
                        GEOMETRY,
                        lip,
                        base,
                        lip,
                        base,
                        2 * M,
                        behindSpec(),
                        new WaterfallVolumePlanV2.PlungePoolSpec(vec(20, 10, 20), 4 * M, 10),
                        "fluid.waterfall",
                        WaterfallVolumePlanV2.Kernel.standard()));
        assertEquals(WaterfallVolumeFailureCodeV2.MISSING_POOL, missingPool.failureCode());

        var compiled = positive();
        WaterfallVolumeExceptionV2 mismatch = assertThrows(WaterfallVolumeExceptionV2.class,
                () -> new WaterfallVolumeGeneratorV2(
                        compiled.plan(),
                        compiled.sdfPlan(),
                        compiled.csgPlan(),
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        assertEquals(WaterfallVolumeFailureCodeV2.GRAPH_CHECKSUM_MISMATCH, mismatch.failureCode());
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        var expected = positive();
        String checksum = new WaterfallVolumeGeneratorV2(
                expected.plan(), expected.sdfPlan(), expected.csgPlan()).metricChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.KOREA);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var again = positive();
            assertEquals(expected.plan().canonicalChecksum(), again.plan().canonicalChecksum());
            assertEquals(checksum, new WaterfallVolumeGeneratorV2(
                    again.plan(), again.sdfPlan(), again.csgPlan()).metricChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() -> {
                    var c = positive();
                    return new WaterfallVolumeGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
                assertEquals(checksum, four.submit(() -> {
                    var c = positive();
                    return new WaterfallVolumeGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static WaterfallVolumePlanCompilerV2.CompiledWaterfallVolumeV2 positive() {
        VolumeSdfVec3V2 lip = vec(0, 40, 0);
        VolumeSdfVec3V2 base = vec(0, 10, 6);
        return WaterfallVolumePlanCompilerV2.compile(
                "fall.fixture-volume",
                "node.fall",
                "waterfall.fixture",
                GEOMETRY,
                lip,
                base,
                lip,
                base,
                2 * M,
                behindSpec(),
                poolSpec(base),
                "fluid.waterfall",
                WaterfallVolumePlanV2.Kernel.standard());
    }

    private static WaterfallVolumePlanV2.BehindFallSpec behindSpec() {
        return new WaterfallVolumePlanV2.BehindFallSpec(
                vec(0, 25, -3),
                vec(4, 16, 3),
                M);
    }

    private static WaterfallVolumePlanV2.PlungePoolSpec poolSpec(VolumeSdfVec3V2 base) {
        return new WaterfallVolumePlanV2.PlungePoolSpec(base, 4 * M, 10);
    }

    private static VolumeSdfVec3V2 vec(int x, int y, int z) {
        return new VolumeSdfVec3V2(x * M, y * M, z * M);
    }
}
