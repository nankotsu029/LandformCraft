package com.github.nankotsu029.landformcraft.generator.v2.volume.water;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cave.CaveNetworkPlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.UndergroundLakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
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

class UndergroundLakeGeneratorV2Test {
    private static final long M = 1_000_000L;

    @Test
    void containedLakePassesFluidAirCavityRimAndReadBack() throws Exception {
        var host = hostNetwork();
        var compiled = UndergroundLakePlanCompilerV2.compile(
                "cave.underground-lake",
                host.plan(),
                "n.chamber",
                "n.entrance",
                4 * M,
                8,
                "fluid.underground-lake",
                2,
                UndergroundLakePlanV2.Kernel.standard());
        UndergroundLakeGeneratorV2 generator = new UndergroundLakeGeneratorV2(
                compiled.plan(),
                host.plan(),
                compiled.sdfPlan(),
                host.sdfPlan(),
                compiled.csgPlan());
        UndergroundLakeGeneratorV2.UndergroundLakeMetricsV2 metrics = generator.validate();
        assertTrue(metrics.cavitySamples() > 0);
        assertTrue(metrics.fluidSamples() > 0);
        assertTrue(metrics.airCavitySamples() >= 2);
        assertTrue(metrics.rimSamples() > 0);
        assertEquals(0, metrics.leakSamples());
        assertEquals(0, metrics.thinRoofSamples());
        assertEquals(0, metrics.breakthroughSamples());
        assertEquals(UndergroundLakePlanCompilerV2.LIFECYCLE, "SUPPORTED");
        assertEquals(1, compiled.csgPlan().operators().stream()
                .filter(op -> op.kind() == VolumeCsgPlanV2.OperationKind.ADD_FLUID).count());

        Path example = Path.of("examples/v2/volume/underground-lake-plan-v2.json");
        LandformV2DataCodec codec = new LandformV2DataCodec();
        assertEquals(compiled.plan().canonicalChecksum(),
                codec.readUndergroundLakePlan(example).canonicalChecksum());
        assertTrue(Files.size(example) > 0);
    }

    @Test
    void rejectsOrphanUnreachableLeakDoubleFluidAndCarveAsFluid() {
        var host = hostNetwork();

        UndergroundLakeExceptionV2 orphan = assertThrows(UndergroundLakeExceptionV2.class,
                () -> UndergroundLakePlanCompilerV2.compile(
                        "lake.orphan",
                        host.plan(),
                        "n.missing",
                        "n.entrance",
                        4 * M,
                        8,
                        "fluid.a",
                        2,
                        UndergroundLakePlanV2.Kernel.standard()));
        assertEquals(UndergroundLakeFailureCodeV2.ORPHAN_BASIN, orphan.failureCode());

        UndergroundLakeExceptionV2 unreachable = assertThrows(UndergroundLakeExceptionV2.class,
                () -> UndergroundLakePlanCompilerV2.compile(
                        "lake.unreachable",
                        host.plan(),
                        "n.chamber",
                        "n.not-an-entrance",
                        4 * M,
                        8,
                        "fluid.a",
                        2,
                        UndergroundLakePlanV2.Kernel.standard()));
        assertEquals(UndergroundLakeFailureCodeV2.NOT_REACHABLE_FROM_ENTRANCE, unreachable.failureCode());

        var leak = UndergroundLakePlanCompilerV2.compile(
                "lake.leak",
                host.plan(),
                "n.chamber",
                "n.entrance",
                9 * M,
                8,
                "fluid.a",
                2,
                UndergroundLakePlanV2.Kernel.standard());
        UndergroundLakeExceptionV2 leaking = assertThrows(UndergroundLakeExceptionV2.class,
                () -> new UndergroundLakeGeneratorV2(
                        leak.plan(), host.plan(), leak.sdfPlan(), host.sdfPlan(), leak.csgPlan())
                        .validate());
        assertEquals(UndergroundLakeFailureCodeV2.LEAKING_FLUID, leaking.failureCode());

        UndergroundLakeExceptionV2 doubleFluid = assertThrows(UndergroundLakeExceptionV2.class,
                () -> UndergroundLakePlanCompilerV2.requireSingleFluidOwner(List.of(
                        new VolumeCsgPlanV2.Operator(
                                "op.carve", 0, VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.a",
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), ""),
                        new VolumeCsgPlanV2.Operator(
                                "op.f1", 1, VolumeCsgPlanV2.OperationKind.ADD_FLUID, "prim.a",
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of("op.carve"), "fluid.a"),
                        new VolumeCsgPlanV2.Operator(
                                "op.f2", 2, VolumeCsgPlanV2.OperationKind.ADD_FLUID, "prim.a",
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of("op.carve"), "fluid.b"))));
        assertEquals(UndergroundLakeFailureCodeV2.DOUBLE_FLUID_OWNER, doubleFluid.failureCode());

        UndergroundLakeExceptionV2 carveAsFluid = assertThrows(UndergroundLakeExceptionV2.class,
                () -> UndergroundLakePlanCompilerV2.requireCarveThenFluid(List.of(
                        new VolumeCsgPlanV2.Operator(
                                "op.fluid-first", 0, VolumeCsgPlanV2.OperationKind.ADD_FLUID, "prim.a",
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), "fluid.a"),
                        new VolumeCsgPlanV2.Operator(
                                "op.carve-late", 1, VolumeCsgPlanV2.OperationKind.CARVE_SOLID, "prim.a",
                                VolumeCsgPlanV2.MaskMode.NONE, "", List.of(), ""))));
        assertEquals(UndergroundLakeFailureCodeV2.CARVE_AS_FLUID_CORRUPTION, carveAsFluid.failureCode());
    }

    @Test
    void compileAndMetricsAreOrderThreadLocaleStable() throws Exception {
        var host = hostNetwork();
        var expected = UndergroundLakePlanCompilerV2.compile(
                "lake.stable",
                host.plan(),
                "n.chamber",
                "n.entrance",
                4 * M,
                8,
                "fluid.underground-lake",
                2,
                UndergroundLakePlanV2.Kernel.standard());
        String checksum = new UndergroundLakeGeneratorV2(
                expected.plan(), host.plan(), expected.sdfPlan(), host.sdfPlan(), expected.csgPlan())
                .metricChecksum();

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.ITALY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var again = UndergroundLakePlanCompilerV2.compile(
                    "lake.stable",
                    host.plan(),
                    "n.chamber",
                    "n.entrance",
                    4 * M,
                    8,
                    "fluid.underground-lake",
                    2,
                    UndergroundLakePlanV2.Kernel.standard());
            assertEquals(expected.plan().canonicalChecksum(), again.plan().canonicalChecksum());
            assertEquals(checksum, new UndergroundLakeGeneratorV2(
                    again.plan(), host.plan(), again.sdfPlan(), host.sdfPlan(), again.csgPlan())
                    .metricChecksum());
            try (var one = Executors.newFixedThreadPool(1); var four = Executors.newFixedThreadPool(4)) {
                assertEquals(checksum, one.submit(() -> {
                    var c = UndergroundLakePlanCompilerV2.compile(
                            "lake.stable",
                            host.plan(),
                            "n.chamber",
                            "n.entrance",
                            4 * M,
                            8,
                            "fluid.underground-lake",
                            2,
                            UndergroundLakePlanV2.Kernel.standard());
                    return new UndergroundLakeGeneratorV2(
                            c.plan(), host.plan(), c.sdfPlan(), host.sdfPlan(), c.csgPlan())
                            .metricChecksum();
                }).get());
                assertEquals(checksum, four.submit(() -> {
                    var c = UndergroundLakePlanCompilerV2.compile(
                            "lake.stable",
                            host.plan(),
                            "n.chamber",
                            "n.entrance",
                            4 * M,
                            8,
                            "fluid.underground-lake",
                            2,
                            UndergroundLakePlanV2.Kernel.standard());
                    return new UndergroundLakeGeneratorV2(
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
