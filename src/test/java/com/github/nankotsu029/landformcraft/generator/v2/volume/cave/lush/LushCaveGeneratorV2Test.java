package com.github.nankotsu029.landformcraft.generator.v2.volume.cave.lush;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cave.CaveNetworkPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
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

class LushCaveGeneratorV2Test {
    private static final long M = 1_000_000L;

    @Test
    void lushWithinReachableWetSurfacesAndEcologyHooksPass() throws Exception {
        var host = hostNetwork();
        var compiled = LushCavePlanCompilerV2.compile(
                "cave.lush-chamber",
                host.plan(),
                "n.chamber",
                "n.entrance",
                4 * M,
                8,
                800_000,
                120_000,
                LushCavePlanV2Kernel());
        LushCaveGeneratorV2 generator = new LushCaveGeneratorV2(
                compiled.plan(),
                host.plan(),
                compiled.sdfPlan(),
                host.sdfPlan(),
                compiled.csgPlan());
        LushCaveGeneratorV2.LushCaveMetricsV2 metrics = generator.validate();
        assertTrue(metrics.carvedSamples() > 0);
        assertTrue(metrics.containedSamples() > 0);
        assertTrue(metrics.floorSamples() > 0);
        assertTrue(metrics.wallSamples() > 0);
        assertTrue(metrics.ceilingSamples() > 0);
        assertEquals(0, metrics.thinRoofSamples());
        assertEquals(0, metrics.breakthroughSamples());
        assertEquals(LushCavePlanCompilerV2.LIFECYCLE, "SUPPORTED");
        assertEquals("LUSH_SUBTERRANEAN", compiled.plan().ecologyHook().ecologyPreset());
        assertEquals(3, compiled.plan().ecologyHook().reservedAssemblageIds().size());

        Path example = Path.of("examples/v2/volume/lush-cave-plan-v2.json");
        LandformV2DataCodec codec = new LandformV2DataCodec();
        assertEquals(compiled.plan().canonicalChecksum(),
                codec.readLushCavePlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsOrphanTooDryUnreachableOversizeAndThinRoof() {
        var host = hostNetwork();

        LushCaveExceptionV2 orphan = assertThrows(LushCaveExceptionV2.class,
                () -> LushCavePlanCompilerV2.compile(
                        "cave.lush-orphan",
                        host.plan(),
                        "n.missing",
                        "n.entrance",
                        4 * M,
                        8,
                        800_000,
                        100_000,
                        LushCavePlanV2Kernel()));
        assertEquals(LushCaveFailureCodeV2.ORPHAN_CHAMBER, orphan.failureCode());

        LushCaveExceptionV2 tooDry = assertThrows(LushCaveExceptionV2.class,
                () -> LushCavePlanCompilerV2.compile(
                        "cave.lush-dry",
                        host.plan(),
                        "n.chamber",
                        "n.entrance",
                        4 * M,
                        8,
                        100_000,
                        100_000,
                        LushCavePlanV2Kernel()));
        assertEquals(LushCaveFailureCodeV2.TOO_DRY, tooDry.failureCode());

        LushCaveExceptionV2 unreachable = assertThrows(LushCaveExceptionV2.class,
                () -> LushCavePlanCompilerV2.compile(
                        "cave.lush-unreachable",
                        host.plan(),
                        "n.chamber",
                        "n.not-an-entrance",
                        4 * M,
                        8,
                        800_000,
                        100_000,
                        LushCavePlanV2Kernel()));
        assertEquals(LushCaveFailureCodeV2.NOT_REACHABLE_FROM_ENTRANCE, unreachable.failureCode());

        var oversized = LushCavePlanCompilerV2.compile(
                "cave.lush-oversize",
                host.plan(),
                "n.chamber",
                "n.entrance",
                9 * M,
                8,
                800_000,
                100_000,
                LushCavePlanV2Kernel());
        LushCaveExceptionV2 containment = assertThrows(LushCaveExceptionV2.class,
                () -> new LushCaveGeneratorV2(
                        oversized.plan(),
                        host.plan(),
                        oversized.sdfPlan(),
                        host.sdfPlan(),
                        oversized.csgPlan()).validate());
        assertEquals(LushCaveFailureCodeV2.NETWORK_CONTAINMENT_FAILED, containment.failureCode());

        var shallowHost = CaveNetworkPlanCompilerV2.compile(
                "cave.shallow-host",
                List.of(
                        node("n.entrance", CaveNetworkPlanV2.NodeKind.ENTRANCE, 0, 18, 0, 2),
                        node("n.chamber", CaveNetworkPlanV2.NodeKind.CHAMBER, 10, 19, 0, 3)),
                List.of(edge("e.1", "n.entrance", "n.chamber", 2)),
                List.of("n.entrance"),
                20,
                new CaveNetworkPlanV2.Kernel("cave-network-v1", 5, 32, 64, 16_000_000L));
        var thinLush = LushCavePlanCompilerV2.compile(
                "cave.lush-thin",
                shallowHost.plan(),
                "n.chamber",
                "n.entrance",
                3 * M,
                6,
                800_000,
                100_000,
                new com.github.nankotsu029.landformcraft.model.v2.volume.LushCavePlanV2.Kernel(
                        "lush-cave-v1", 5, 750_000, 6, 30_000_000L, 64_000));
        LushCaveExceptionV2 thin = assertThrows(LushCaveExceptionV2.class,
                () -> new LushCaveGeneratorV2(
                        thinLush.plan(),
                        shallowHost.plan(),
                        thinLush.sdfPlan(),
                        shallowHost.sdfPlan(),
                        thinLush.csgPlan()).validate());
        assertTrue(thin.failureCode() == LushCaveFailureCodeV2.THIN_ROOF
                || thin.failureCode() == LushCaveFailureCodeV2.SURFACE_BREAKTHROUGH);
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        var host = hostNetwork();
        var expected = LushCavePlanCompilerV2.compile(
                "cave.lush-stable",
                host.plan(),
                "n.chamber",
                "n.entrance",
                4 * M,
                8,
                800_000,
                120_000,
                LushCavePlanV2Kernel());
        String checksum = new LushCaveGeneratorV2(
                expected.plan(), host.plan(), expected.sdfPlan(), host.sdfPlan(), expected.csgPlan())
                .metricChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var again = LushCavePlanCompilerV2.compile(
                    "cave.lush-stable",
                    host.plan(),
                    "n.chamber",
                    "n.entrance",
                    4 * M,
                    8,
                    800_000,
                    120_000,
                    LushCavePlanV2Kernel());
            assertEquals(expected.plan().canonicalChecksum(), again.plan().canonicalChecksum());
            assertEquals(checksum, new LushCaveGeneratorV2(
                    again.plan(), host.plan(), again.sdfPlan(), host.sdfPlan(), again.csgPlan())
                    .metricChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() -> {
                    var c = LushCavePlanCompilerV2.compile(
                            "cave.lush-stable",
                            host.plan(),
                            "n.chamber",
                            "n.entrance",
                            4 * M,
                            8,
                            800_000,
                            120_000,
                            LushCavePlanV2Kernel());
                    return new LushCaveGeneratorV2(
                            c.plan(), host.plan(), c.sdfPlan(), host.sdfPlan(), c.csgPlan())
                            .metricChecksum();
                }).get());
                assertEquals(checksum, four.submit(() -> {
                    var c = LushCavePlanCompilerV2.compile(
                            "cave.lush-stable",
                            host.plan(),
                            "n.chamber",
                            "n.entrance",
                            4 * M,
                            8,
                            800_000,
                            120_000,
                            LushCavePlanV2Kernel());
                    return new LushCaveGeneratorV2(
                            c.plan(), host.plan(), c.sdfPlan(), host.sdfPlan(), c.csgPlan())
                            .metricChecksum();
                }).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static CaveNetworkPlanCompilerV2.CompiledCaveNetworkV2 hostNetwork() {
        return CaveNetworkPlanCompilerV2.compile(
                "cave.fixture-network",
                List.of(
                        node("n.entrance", CaveNetworkPlanV2.NodeKind.ENTRANCE, 0, 18, 0, 3),
                        node("n.junction", CaveNetworkPlanV2.NodeKind.JUNCTION, 12, 10, 0, 3),
                        node("n.chamber", CaveNetworkPlanV2.NodeKind.CHAMBER, 24, 8, 0, 4)),
                List.of(
                        edge("e.1", "n.entrance", "n.junction", 2),
                        edge("e.2", "n.junction", "n.chamber", 2)),
                List.of("n.entrance"),
                20,
                CaveNetworkPlanV2.Kernel.standard());
    }

    private static com.github.nankotsu029.landformcraft.model.v2.volume.LushCavePlanV2.Kernel
            LushCavePlanV2Kernel() {
        return com.github.nankotsu029.landformcraft.model.v2.volume.LushCavePlanV2.Kernel.standard();
    }

    private static CaveNetworkPlanV2.Node node(
            String id,
            CaveNetworkPlanV2.NodeKind kind,
            int x,
            int y,
            int z,
            int radius
    ) {
        return new CaveNetworkPlanV2.Node(
                id, kind, new VolumeSdfVec3V2(x * M, y * M, z * M), radius * M);
    }

    private static CaveNetworkPlanV2.Edge edge(String id, String from, String to, int radius) {
        return new CaveNetworkPlanV2.Edge(id, from, to, radius * M);
    }
}
