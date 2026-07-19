package com.github.nankotsu029.landformcraft.generator.v2.volume.cave;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
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

class CaveNetworkGeneratorV2Test {
    private static final long M = 1_000_000L;

    @Test
    void connectedCavePassesRoofAndEntranceMetrics() throws Exception {
        var compiled = CaveNetworkPlanCompilerV2.compile(
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
        CaveNetworkGeneratorV2 generator = new CaveNetworkGeneratorV2(
                compiled.plan(), compiled.sdfPlan(), compiled.csgPlan());
        CaveNetworkGeneratorV2.CaveNetworkMetricsV2 metrics = generator.validate();
        assertTrue(metrics.carvedSamples() > 0);
        assertEquals(0, metrics.thinRoofSamples());
        assertEquals(0, metrics.breakthroughSamples());
        assertEquals(CaveNetworkPlanCompilerV2.LIFECYCLE, "SUPPORTED");

        Path example = Path.of("examples/v2/volume/cave-network-plan-v2.json");
        LandformV2DataCodec codec = new LandformV2DataCodec();
        assertEquals(compiled.plan().canonicalChecksum(),
                codec.readCaveNetworkPlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsDisconnectedIsolatedThinRoofAndBreakthrough() {
        CaveNetworkExceptionV2 disconnected = assertThrows(CaveNetworkExceptionV2.class,
                () -> CaveNetworkPlanCompilerV2.compile(
                        "cave.bad",
                        List.of(
                                node("n.entrance", CaveNetworkPlanV2.NodeKind.ENTRANCE, 0, 18, 0, 2),
                                node("n.isolated", CaveNetworkPlanV2.NodeKind.CHAMBER, 40, 8, 0, 3)),
                        List.of(),
                        List.of("n.entrance"),
                        20,
                        CaveNetworkPlanV2.Kernel.standard()));
        assertEquals(CaveNetworkFailureCodeV2.ISOLATED_CHAMBER, disconnected.failureCode());

        var thin = CaveNetworkPlanCompilerV2.compile(
                "cave.thin",
                List.of(
                        node("n.entrance", CaveNetworkPlanV2.NodeKind.ENTRANCE, 0, 18, 0, 2),
                        node("n.shallow", CaveNetworkPlanV2.NodeKind.CHAMBER, 20, 19, 0, 3)),
                List.of(edge("e.1", "n.entrance", "n.shallow", 2)),
                List.of("n.entrance"),
                20,
                new CaveNetworkPlanV2.Kernel("cave-network-v1", 5, 32, 64, 16_000_000L));
        CaveNetworkExceptionV2 thinRoof = assertThrows(CaveNetworkExceptionV2.class,
                () -> new CaveNetworkGeneratorV2(thin.plan(), thin.sdfPlan(), thin.csgPlan()).validate());
        assertEquals(CaveNetworkFailureCodeV2.THIN_ROOF, thinRoof.failureCode());

        var breakThrough = CaveNetworkPlanCompilerV2.compile(
                "cave.break",
                List.of(
                        node("n.entrance", CaveNetworkPlanV2.NodeKind.ENTRANCE, 0, 8, 0, 2),
                        node("n.up", CaveNetworkPlanV2.NodeKind.CHAMBER, 16, 12, 0, 4)),
                List.of(edge("e.1", "n.entrance", "n.up", 2)),
                List.of("n.entrance"),
                10,
                CaveNetworkPlanV2.Kernel.standard());
        CaveNetworkExceptionV2 surface = assertThrows(CaveNetworkExceptionV2.class,
                () -> new CaveNetworkGeneratorV2(
                        breakThrough.plan(), breakThrough.sdfPlan(), breakThrough.csgPlan()).validate());
        assertTrue(surface.failureCode() == CaveNetworkFailureCodeV2.SURFACE_BREAKTHROUGH
                || surface.failureCode() == CaveNetworkFailureCodeV2.THIN_ROOF
                || surface.failureCode() == CaveNetworkFailureCodeV2.HARD_CLEARANCE_CONFLICT);
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        List<CaveNetworkPlanV2.Node> nodes = List.of(
                node("n.entrance", CaveNetworkPlanV2.NodeKind.ENTRANCE, 0, 18, 0, 3),
                node("n.chamber", CaveNetworkPlanV2.NodeKind.CHAMBER, 16, 8, 0, 4));
        List<CaveNetworkPlanV2.Edge> edges = List.of(edge("e.1", "n.entrance", "n.chamber", 2));
        var expected = CaveNetworkPlanCompilerV2.compile(
                "cave.stable", nodes, edges, List.of("n.entrance"), 20,
                CaveNetworkPlanV2.Kernel.standard());
        String checksum = new CaveNetworkGeneratorV2(
                expected.plan(), expected.sdfPlan(), expected.csgPlan()).metricChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var again = CaveNetworkPlanCompilerV2.compile(
                    "cave.stable", nodes, edges, List.of("n.entrance"), 20,
                    CaveNetworkPlanV2.Kernel.standard());
            assertEquals(expected.plan().canonicalChecksum(), again.plan().canonicalChecksum());
            assertEquals(checksum, new CaveNetworkGeneratorV2(
                    again.plan(), again.sdfPlan(), again.csgPlan()).metricChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() -> {
                    var c = CaveNetworkPlanCompilerV2.compile(
                            "cave.stable", nodes, edges, List.of("n.entrance"), 20,
                            CaveNetworkPlanV2.Kernel.standard());
                    return new CaveNetworkGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
                assertEquals(checksum, four.submit(() -> {
                    var c = CaveNetworkPlanCompilerV2.compile(
                            "cave.stable", nodes, edges, List.of("n.entrance"), 20,
                            CaveNetworkPlanV2.Kernel.standard());
                    return new CaveNetworkGeneratorV2(c.plan(), c.sdfPlan(), c.csgPlan()).metricChecksum();
                }).get());
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
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
