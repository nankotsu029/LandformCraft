package com.github.nankotsu029.landformcraft.generator.v2.volume.sdf;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitiveV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfVec3V2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeSdfKernelV2Test {
    private static final long M = 1_000_000L;
    private static final String ZERO = "0".repeat(64);

    @Test
    void sphereBoundarySymmetryAndTranslationAreGolden() {
        VolumeSdfPrimitivePlanV2 plan = sealedPlan(List.of(
                new VolumeSdfPrimitiveV2.Sphere("sdf.sphere", new VolumeSdfVec3V2(10 * M, 20 * M, 30 * M), 5 * M)));
        VolumeSdfKernelV2 kernel = new VolumeSdfKernelV2(plan);

        long boundary = kernel.sampleDistanceMillionths("sdf.sphere", 15 * M, 20 * M, 30 * M);
        assertTrue(Math.abs(boundary) <= 2_000L, "boundary distance=" + boundary);
        assertTrue(kernel.sampleDistanceMillionths("sdf.sphere", 10 * M, 20 * M, 30 * M) < 0L);
        assertTrue(kernel.sampleDistanceMillionths("sdf.sphere", 20 * M, 20 * M, 30 * M) > 0L);

        long east = kernel.sampleDistanceMillionths("sdf.sphere", 15 * M, 20 * M, 30 * M);
        long west = kernel.sampleDistanceMillionths("sdf.sphere", 5 * M, 20 * M, 30 * M);
        assertEquals(east, west);

        VolumeSdfPrimitivePlanV2 translated = sealedPlan(List.of(
                new VolumeSdfPrimitiveV2.Sphere("sdf.sphere", new VolumeSdfVec3V2(110 * M, 20 * M, 30 * M), 5 * M)));
        VolumeSdfKernelV2 translatedKernel = new VolumeSdfKernelV2(translated);
        assertEquals(
                kernel.sampleDistanceMillionths("sdf.sphere", 12 * M, 21 * M, 28 * M),
                translatedKernel.sampleDistanceMillionths("sdf.sphere", 112 * M, 21 * M, 28 * M));

        VolumeSdfAabbV2 bounds = kernel.conservativeBounds("sdf.sphere");
        assertTrue(bounds.minXMillionths() <= 5 * M);
        assertTrue(bounds.maxXMillionths() >= 15 * M);
        String checksum = kernel.goldenChecksum("sdf.sphere", M, 2);
        assertEquals(64, checksum.length());
        assertEquals(checksum, kernel.goldenChecksum("sdf.sphere", M, 2));
    }

    @Test
    void ellipsoidCapsulePlaneRoundedBoxAndSweptSplineSignsMatchExpectedRegions() {
        VolumeSdfPrimitivePlanV2 plan = sealedPlan(List.of(
                new VolumeSdfPrimitiveV2.Ellipsoid(
                        "sdf.ellipsoid",
                        new VolumeSdfVec3V2(0, 0, 0),
                        new VolumeSdfVec3V2(8 * M, 4 * M, 2 * M)),
                new VolumeSdfPrimitiveV2.Capsule(
                        "sdf.capsule",
                        new VolumeSdfVec3V2(0, 0, 0),
                        new VolumeSdfVec3V2(0, 10 * M, 0),
                        2 * M),
                new VolumeSdfPrimitiveV2.Plane(
                        "sdf.plane",
                        new VolumeSdfVec3V2(0, 0, 0),
                        new VolumeSdfVec3V2(0, M, 0),
                        new VolumeSdfAabbV2(-10 * M, -10 * M, -10 * M, 10 * M, 10 * M, 10 * M)),
                new VolumeSdfPrimitiveV2.RoundedBox(
                        "sdf.box",
                        new VolumeSdfVec3V2(0, 0, 0),
                        new VolumeSdfVec3V2(4 * M, 3 * M, 2 * M),
                        M),
                new VolumeSdfPrimitiveV2.SweptSpline(
                        "sdf.sweep",
                        List.of(
                                new VolumeSdfVec3V2(0, 0, 0),
                                new VolumeSdfVec3V2(10 * M, 0, 0),
                                new VolumeSdfVec3V2(10 * M, 10 * M, 0)),
                        2 * M)));
        VolumeSdfKernelV2 kernel = new VolumeSdfKernelV2(plan);

        assertTrue(kernel.sampleSign("sdf.ellipsoid", 0, 0, 0) < 0);
        assertTrue(kernel.sampleSign("sdf.ellipsoid", 9 * M, 0, 0) > 0);
        assertTrue(kernel.sampleSign("sdf.capsule", 0, 5 * M, 0) < 0);
        assertTrue(kernel.sampleSign("sdf.capsule", 5 * M, 5 * M, 0) > 0);
        assertEquals(-1, kernel.sampleSign("sdf.plane", 0, -M, 0));
        assertEquals(1, kernel.sampleSign("sdf.plane", 0, M, 0));
        assertTrue(kernel.sampleSign("sdf.box", 0, 0, 0) < 0);
        assertTrue(kernel.sampleSign("sdf.box", 6 * M, 0, 0) > 0);
        assertTrue(kernel.sampleSign("sdf.sweep", 5 * M, 0, 0) < 0);
        assertTrue(kernel.sampleSign("sdf.sweep", 5 * M, 5 * M, 0) > 0);

        for (VolumeSdfPrimitiveV2 primitive : plan.primitives()) {
            VolumeSdfAabbV2 bounds = kernel.conservativeBounds(primitive.primitiveId());
            assertTrue(bounds.extentXBlocks() <= VolumeSdfPrimitivePlanV2.MAXIMUM_AABB_EXTENT_BLOCKS);
            assertTrue(bounds.extentYBlocks() <= VolumeSdfPrimitivePlanV2.MAXIMUM_AABB_EXTENT_BLOCKS);
            assertTrue(bounds.extentZBlocks() <= VolumeSdfPrimitivePlanV2.MAXIMUM_AABB_EXTENT_BLOCKS);
        }
    }

    @Test
    void samplingIsDeterministicAcrossLocaleTimezoneThreadAndPrimitiveOrder() throws Exception {
        List<VolumeSdfPrimitiveV2> firstOrder = List.of(
                new VolumeSdfPrimitiveV2.Sphere("a.sphere", new VolumeSdfVec3V2(0, 0, 0), 3 * M),
                new VolumeSdfPrimitiveV2.Capsule(
                        "b.capsule",
                        new VolumeSdfVec3V2(0, 0, 0),
                        new VolumeSdfVec3V2(0, 6 * M, 0),
                        M));
        List<VolumeSdfPrimitiveV2> reversed = List.of(firstOrder.get(1), firstOrder.get(0));
        VolumeSdfKernelV2 first = new VolumeSdfKernelV2(sealedPlan(firstOrder));
        VolumeSdfKernelV2 second = new VolumeSdfKernelV2(sealedPlan(reversed));
        assertEquals(
                first.sampleDistanceMillionths("a.sphere", M, 0, 0),
                second.sampleDistanceMillionths("a.sphere", M, 0, 0));
        assertEquals(
                first.goldenChecksum("b.capsule", M, 2),
                second.goldenChecksum("b.capsule", M, 2));

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            VolumeSdfKernelV2 localeKernel = new VolumeSdfKernelV2(sealedPlan(firstOrder));
            assertEquals(
                    first.goldenChecksum("a.sphere", M, 2),
                    localeKernel.goldenChecksum("a.sphere", M, 2));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
            long expected = first.sampleDistanceMillionths("a.sphere", 2 * M, M, -M);
            assertEquals(expected, one.submit(() ->
                    new VolumeSdfKernelV2(sealedPlan(firstOrder))
                            .sampleDistanceMillionths("a.sphere", 2 * M, M, -M)).get());
            assertEquals(expected, four.submit(() ->
                    new VolumeSdfKernelV2(sealedPlan(firstOrder))
                            .sampleDistanceMillionths("a.sphere", 2 * M, M, -M)).get());
        }
    }

    @Test
    void rejectsZeroRadiusFutureKernelAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeSdfPrimitiveV2.Sphere(
                "bad.sphere", new VolumeSdfVec3V2(0, 0, 0), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new VolumeSdfPrimitivePlanV2.Kernel("volume-sdf-fixed-v2", 64, 256));

        VolumeSdfPrimitivePlanV2 valid = sealedPlan(List.of(
                new VolumeSdfPrimitiveV2.Sphere("sdf.sphere", new VolumeSdfVec3V2(0, 0, 0), 2 * M)));
        VolumeSdfKernelV2 kernel = new VolumeSdfKernelV2(valid);
        VolumeSdfExceptionV2 overflow = assertThrows(VolumeSdfExceptionV2.class,
                () -> kernel.sampleDistanceMillionths(
                        "sdf.sphere",
                        Long.MAX_VALUE / 4,
                        Long.MAX_VALUE / 4,
                        Long.MAX_VALUE / 4));
        assertEquals(VolumeSdfFailureCodeV2.ARITHMETIC_OVERFLOW, overflow.failureCode());
    }

    @Test
    void exampleRoundTripsThroughSchemaAndCodec() throws Exception {
        Path example = Path.of("examples/v2/volume/volume-sdf-primitive-plan-v2.json");
        LandformV2DataCodec codec = new LandformV2DataCodec();
        VolumeSdfPrimitivePlanV2 plan = codec.readVolumeSdfPrimitivePlan(example);
        assertEquals(6, plan.primitives().size());
        VolumeSdfKernelV2 kernel = new VolumeSdfKernelV2(plan);
        assertEquals(plan.canonicalChecksum(), codec.volumeSdfPrimitivePlanChecksum(plan));
        assertTrue(kernel.sampleSign("fixture.sphere", 0, 0, 0) < 0);
        String rewritten = codec.canonicalVolumeSdfPrimitivePlan(plan);
        VolumeSdfPrimitivePlanV2 again = codec.readVolumeSdfPrimitivePlan(rewritten, "round-trip");
        assertEquals(plan, again);
        assertTrue(Files.size(example) > 0);
    }

    private static VolumeSdfPrimitivePlanV2 sealedPlan(List<VolumeSdfPrimitiveV2> primitives) {
        VolumeSdfPrimitivePlanV2 draft = new VolumeSdfPrimitivePlanV2(
                VolumeSdfPrimitivePlanV2.VERSION,
                VolumeSdfPrimitivePlanV2.PRIMITIVE_CONTRACT_VERSION,
                VolumeSdfPrimitivePlanV2.Quantization.standard(),
                VolumeSdfPrimitivePlanV2.Kernel.standard(),
                new ArrayList<>(primitives),
                new VolumeSdfPrimitivePlanV2.ResourceBudget(
                        VolumeSdfPrimitivePlanV2.ResourceBudget.VERSION,
                        VolumeSdfPrimitivePlanV2.MAXIMUM_PRIMITIVES,
                        VolumeSdfPrimitivePlanV2.MAXIMUM_SWEPT_CONTROL_POINTS,
                        4_096L,
                        VolumeSdfPrimitivePlanV2.MAX_CANONICAL_BYTES,
                        64L * 1024L),
                ZERO);
        return new LandformV2DataCodec().sealVolumeSdfPrimitivePlan(draft);
    }
}
