package com.github.nankotsu029.landformcraft.generator.v2.hydrology.core;

import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingBundlePublisherV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyFlowDirectionV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicHydrologyRoutingSolverV2Test {
    private final DeterministicHydrologyRoutingSolverV2 solver = new DeterministicHydrologyRoutingSolverV2();

    @Test
    void bowlFlatMultipleOutletAndBoundaryFixturesReachDeclaredOutlets() {
        HydrologyRoutingResultV2 bowl = solve(
                33, 33,
                ProvisionalSurfaceV2.routable((x, z) ->
                        40_000_000 + (Math.abs(x - 16) + Math.abs(z - 16)) * 100_000),
                List.of(outlet("north-mouth", 16, 0, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY)),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical());
        assertEquals(1_089L, bowl.basins().getFirst().areaCells());
        assertEquals(1_089, bowl.flowAccumulationAt(16, 0));
        assertEveryRouteTerminates(bowl);

        HydrologyRoutingResultV2 flat = solve(
                33, 33, ProvisionalSurfaceV2.routable((x, z) -> 50_000_000),
                List.of(outlet("flat-mouth", 0, 0, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY)),
                new HydrologyRoutingRequestV2.ExecutionProfile(
                        32, HydrologyRoutingRequestV2.TileOrder.REVERSE, 4));
        assertEquals(1_089, flat.flowAccumulationAt(0, 0));
        assertEveryRouteTerminates(flat);

        List<HydrologyRoutingArtifactV2.Outlet> multiple = List.of(
                outlet("east-mouth", 95, 31, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY),
                outlet("west-mouth", 0, 31, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY));
        HydrologyRoutingResultV2 split = solve(
                96, 64, ProvisionalSurfaceV2.routable((x, z) -> 60_000_000), multiple,
                HydrologyRoutingRequestV2.ExecutionProfile.canonical());
        assertEquals(2, split.basins().size());
        assertEquals(6_144L, split.basins().stream().mapToLong(
                HydrologyRoutingArtifactV2.BasinSummary::areaCells).sum());
        assertTrue(split.basins().stream().allMatch(basin -> basin.areaCells() > 0));
        assertEveryRouteTerminates(split);

        HydrologyPlanV2 plan = plan(96, 64);
        assertThrows(IllegalArgumentException.class, () -> HydrologyRoutingRequestV2.create(
                96, 64, plan, ProvisionalSurfaceV2.routable((x, z) -> 0),
                List.of(outlet("not-boundary", 10, 10, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))));
    }

    @Test
    void candidateTileThreadLocaleAndTimezoneOrderProduceIdenticalGraphAndFields() {
        int width = 96;
        int length = 64;
        ProvisionalSurfaceV2 surface = ProvisionalSurfaceV2.routable((x, z) ->
                40_000_000 + Math.floorMod(x * 31 + z * 17 + (x / 7) * 13, 2_000) * 1_000);
        List<HydrologyRoutingArtifactV2.Outlet> candidates = new ArrayList<>(List.of(
                outlet("west", 0, 20, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY),
                outlet("east", 95, 44, HydrologyRoutingArtifactV2.OutletKind.HARD)));
        HydrologyRoutingResultV2 canonical = solve(
                width, length, surface, candidates,
                new HydrologyRoutingRequestV2.ExecutionProfile(
                        32, HydrologyRoutingRequestV2.TileOrder.FORWARD, 1));

        Collections.reverse(candidates);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            HydrologyRoutingResultV2 reordered = solve(
                    width, length, surface, candidates,
                    new HydrologyRoutingRequestV2.ExecutionProfile(
                            32, HydrologyRoutingRequestV2.TileOrder.REVERSE, 4));
            HydrologyRoutingResultV2 differentTiles = solve(
                    width, length, surface, candidates,
                    new HydrologyRoutingRequestV2.ExecutionProfile(
                            64, HydrologyRoutingRequestV2.TileOrder.FORWARD, 3));
            assertSameChecksums(canonical, reordered);
            assertSameChecksums(canonical, differentTiles);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void atomicallyPublishesStrictBundleAndBoundedReaderRejectsTampering(@TempDir Path root)
            throws Exception {
        HydrologyRoutingResultV2 result = solve(
                96, 64,
                ProvisionalSurfaceV2.routable((x, z) -> 45_000_000 + (x - 48) * (x - 48) * 1_000),
                List.of(
                        outlet("west", 0, 20, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY),
                        outlet("east", 95, 44, HydrologyRoutingArtifactV2.OutletKind.HARD)),
                new HydrologyRoutingRequestV2.ExecutionProfile(
                        32, HydrologyRoutingRequestV2.TileOrder.REVERSE, 4));
        Path bundle = root.resolve("routing");
        HydrologyRoutingArtifactV2 artifact = new HydrologyRoutingBundlePublisherV2().publish(
                bundle, result, () -> false);
        HydrologyRoutingArtifactCodecV2 codec = new HydrologyRoutingArtifactCodecV2();
        assertEquals(artifact, codec.readAndVerify(bundle.resolve("index.json"), bundle, () -> false));
        Path detachedIndex = root.resolve("detached-index.json");
        Files.copy(bundle.resolve("index.json"), detachedIndex);
        assertThrows(IOException.class, () -> codec.readAndVerify(detachedIndex, bundle, () -> false));
        assertEquals(result.routingChecksum(), artifact.routingChecksum());
        assertEquals(2, artifact.fields().size());

        HydrologyRoutingArtifactV2.BasinSummary first = artifact.basins().get(0);
        HydrologyRoutingArtifactV2.BasinSummary second = artifact.basins().get(1);
        List<HydrologyRoutingArtifactV2.BasinSummary> swappedOutletAssignments = List.of(
                new HydrologyRoutingArtifactV2.BasinSummary(
                        first.basinId(), first.numericId(), second.outletId(), second.outletCellId(),
                        second.outletElevationMillionths(), second.areaCells(), second.outletAccumulation()),
                new HydrologyRoutingArtifactV2.BasinSummary(
                        second.basinId(), second.numericId(), first.outletId(), first.outletCellId(),
                        first.outletElevationMillionths(), first.areaCells(), first.outletAccumulation()));
        String swappedGraph = HydrologyRoutingArtifactV2.computeGraphChecksum(
                artifact.width(), artifact.length(), artifact.sourceHydrologyPlanChecksum(),
                artifact.sourceSurfaceChecksum(), artifact.fixedPriorChecksum(), artifact.outlets(),
                swappedOutletAssignments);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyRoutingArtifactV2(
                artifact.artifactVersion(), artifact.solverVersion(), artifact.directionEncodingVersion(),
                artifact.width(), artifact.length(), artifact.sourceHydrologyPlanChecksum(),
                artifact.sourceSurfaceChecksum(), artifact.fixedPriorChecksum(), artifact.outlets(),
                swappedOutletAssignments, artifact.fields(), artifact.resources(), swappedGraph,
                HydrologyRoutingArtifactV2.computeRoutingChecksum(swappedGraph, artifact.fields()),
                "0".repeat(64)));

        Path extra = bundle.resolve("extra.bin");
        Files.write(extra, new byte[]{1});
        assertThrows(IOException.class,
                () -> codec.readAndVerify(bundle.resolve("index.json"), bundle, () -> false));
        Files.delete(extra);
        Path extraDirectory = bundle.resolve("unindexed");
        Files.createDirectory(extraDirectory);
        assertThrows(IOException.class,
                () -> codec.readAndVerify(bundle.resolve("index.json"), bundle, () -> false));
        Files.delete(extraDirectory);
        assertThrows(IOException.class,
                () -> codec.read(" ".repeat(512 * 1024 + 1), "oversized-routing"));

        FieldArtifactDescriptorV2 direction = artifact.fields().stream()
                .filter(field -> field.definition().semantic()
                        == FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION)
                .findFirst().orElseThrow();
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(bundle, direction,
                new LfcGridReaderV1.ReadLimits(8L * 1024L * 1024L, 32L * 1024L))) {
            assertTrue(LfcGridReaderV1.estimateWindowWorkingBytes(
                    32, 32, FieldArtifactDescriptorV2.FieldValueType.U8) < 8_000L);
            assertEquals(result.flowDirectionCodeAt(32, 16),
                    reader.readWindow(32, 16, 32, 32).rawValueAt(0, 0));
        }

        Files.write(bundle.resolve(direction.relativePath()), new byte[]{1, 2, 3});
        assertThrows(IOException.class,
                () -> codec.readAndVerify(bundle.resolve("index.json"), bundle, () -> false));

        String canonical = codec.canonical(artifact);
        assertThrows(StructuredDataValidationException.class, () -> codec.read(
                canonical.replace("\"artifactVersion\":1", "\"artifactVersion\":2"), "future-routing"));
        assertThrows(IOException.class, () -> codec.read(
                canonical.replace(artifact.canonicalChecksum(), "0".repeat(64)), "tampered-routing"));
    }

    @Test
    void rejectsBlockedHardOutletDisconnectedComponentOverflowBudgetAndCancellation(@TempDir Path root) {
        HydrologyPlanV2 smallPlan = plan(33, 33);
        assertThrows(IllegalArgumentException.class, () -> HydrologyRoutingRequestV2.create(
                33, 33, smallPlan, ProvisionalSurfaceV2.routable((x, z) -> 50_000_000),
                List.of(
                        outlet("west", 0, 16, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY),
                        outlet("east", 32, 16, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))));
        ProvisionalSurfaceV2 blockedOutlet = surface(
                (x, z) -> 50_000_000L, (x, z) -> x != 16 || z != 16);
        HydrologyRoutingException blocked = assertThrows(HydrologyRoutingException.class, () -> solver.solve(
                HydrologyRoutingRequestV2.create(
                        33, 33, smallPlan, blockedOutlet,
                        List.of(outlet("hard", 16, 16, HydrologyRoutingArtifactV2.OutletKind.HARD))),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(), () -> false));
        assertEquals("v2.hydrology-routing-hard-outlet", blocked.ruleId());

        ProvisionalSurfaceV2 disconnected = surface(
                (x, z) -> 50_000_000L, (x, z) -> x != 16);
        HydrologyRoutingException unroutable = assertThrows(HydrologyRoutingException.class, () -> solver.solve(
                HydrologyRoutingRequestV2.create(
                        33, 33, smallPlan, disconnected,
                        List.of(outlet("west", 0, 16, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(), () -> false));
        assertEquals("v2.hydrology-routing-unroutable", unroutable.ruleId());

        ProvisionalSurfaceV2 overflow = surface(
                (x, z) -> Long.MAX_VALUE, (x, z) -> true);
        HydrologyRoutingException overflowFailure = assertThrows(HydrologyRoutingException.class, () -> solver.solve(
                HydrologyRoutingRequestV2.create(
                        33, 33, smallPlan, overflow,
                        List.of(outlet("west", 0, 16, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(), () -> false));
        assertEquals("v2.hydrology-routing-overflow", overflowFailure.ruleId());

        var defaults = HydrologyRoutingRequestV2.ResourceBudget.fromPlan(smallPlan);
        var tooSmall = new HydrologyRoutingRequestV2.ResourceBudget(
                defaults.budgetVersion(), defaults.maximumOutlets(), 1,
                defaults.maximumWorkingBytes(), defaults.maximumRetainedResultBytes(),
                defaults.maximumFieldArtifactBytes());
        HydrologyRoutingRequestV2 budgetRequest = new HydrologyRoutingRequestV2(
                1, 33, 33, smallPlan, ProvisionalSurfaceV2.routable((x, z) -> 0),
                List.of(outlet("west", 0, 16, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY)), tooSmall);
        HydrologyRoutingException budget = assertThrows(HydrologyRoutingException.class, () -> solver.solve(
                budgetRequest, HydrologyRoutingRequestV2.ExecutionProfile.canonical(), () -> false));
        assertEquals("v2.hydrology-routing-budget", budget.ruleId());

        assertThrows(CancellationException.class, () -> solver.solve(
                HydrologyRoutingRequestV2.create(
                        33, 33, smallPlan, ProvisionalSurfaceV2.routable((x, z) -> 0),
                        List.of(outlet("west", 0, 16, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(), () -> true));
        AtomicInteger solveChecks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> solver.solve(
                HydrologyRoutingRequestV2.create(
                        33, 33, smallPlan, ProvisionalSurfaceV2.routable((x, z) -> 0),
                        List.of(outlet("west", 0, 16, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(),
                () -> solveChecks.incrementAndGet() > 6));

        HydrologyRoutingResultV2 result = solve(
                33, 33, ProvisionalSurfaceV2.routable((x, z) -> 0),
                List.of(outlet("west", 0, 16, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY)),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical());
        AtomicInteger checks = new AtomicInteger();
        Path target = root.resolve("cancelled");
        assertThrows(CancellationException.class, () -> new HydrologyRoutingBundlePublisherV2().publish(
                target, result, () -> checks.incrementAndGet() > 4));
        assertFalse(Files.exists(target));
        try (var files = Files.list(root)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".hydrology-routing-")));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void thousandSquareUsesCompactRetainedFieldsAndAdmittedPeak() {
        HydrologyRoutingResultV2 result = solve(
                1_000, 1_000,
                ProvisionalSurfaceV2.routable((x, z) -> 30_000_000 + Math.floorMod(x * 13 + z * 7, 10_000)),
                List.of(outlet("north-west", 0, 0, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY)),
                new HydrologyRoutingRequestV2.ExecutionProfile(
                        128, HydrologyRoutingRequestV2.TileOrder.REVERSE, 4));
        assertEquals(1_000_000L, result.metrics().routableCellCount());
        assertTrue(result.metrics().peakWorkingBytes() < 40L * 1024L * 1024L);
        assertTrue(result.metrics().retainedResultBytes() < 6L * 1024L * 1024L);
        assertTrue(result.metrics().cpuWorkUnits() <= result.metrics().budget().maximumCpuWorkUnits());
        assertEquals(1_000_000, result.flowAccumulationAt(0, 0));
    }

    private HydrologyRoutingResultV2 solve(
            int width,
            int length,
            ProvisionalSurfaceV2 surface,
            List<HydrologyRoutingArtifactV2.Outlet> outlets,
            HydrologyRoutingRequestV2.ExecutionProfile profile
    ) {
        return solver.solve(
                HydrologyRoutingRequestV2.create(width, length, plan(width, length), surface, outlets),
                profile, () -> false);
    }

    private static HydrologyPlanV2 plan(int width, int length) {
        return new HydrologyPlanCompilerV2().compile(new WorldBlueprintV2.Bounds(width, length, -64, 255, 50));
    }

    private static HydrologyRoutingArtifactV2.Outlet outlet(
            String id,
            int x,
            int z,
            HydrologyRoutingArtifactV2.OutletKind kind
    ) {
        return new HydrologyRoutingArtifactV2.Outlet(id, x, z, kind);
    }

    private static ProvisionalSurfaceV2 surface(Elevation elevation, Routable routable) {
        return new ProvisionalSurfaceV2() {
            @Override
            public long elevationMillionthsAt(int globalX, int globalZ) {
                return elevation.at(globalX, globalZ);
            }

            @Override
            public boolean routableAt(int globalX, int globalZ) {
                return routable.at(globalX, globalZ);
            }
        };
    }

    private static void assertEveryRouteTerminates(HydrologyRoutingResultV2 result) {
        int maximumSteps = Math.multiplyExact(result.width(), result.length());
        for (int z = 0; z < result.length(); z++) {
            for (int x = 0; x < result.width(); x++) {
                int currentX = x;
                int currentZ = z;
                int previousAccumulation = 0;
                for (int step = 0; step <= maximumSteps; step++) {
                    int accumulation = result.flowAccumulationAt(currentX, currentZ);
                    assertTrue(accumulation > previousAccumulation);
                    HydrologyFlowDirectionV2 flow = HydrologyFlowDirectionV2.fromCode(
                            result.flowDirectionCodeAt(currentX, currentZ));
                    if (flow == HydrologyFlowDirectionV2.TERMINAL) break;
                    previousAccumulation = accumulation;
                    currentX += flow.deltaX();
                    currentZ += flow.deltaZ();
                    if (step == maximumSteps) throw new AssertionError("route did not reach an outlet");
                }
            }
        }
    }

    private static void assertSameChecksums(
            HydrologyRoutingResultV2 expected,
            HydrologyRoutingResultV2 actual
    ) {
        assertEquals(expected.sourceSurfaceChecksum(), actual.sourceSurfaceChecksum());
        assertEquals(expected.graphChecksum(), actual.graphChecksum());
        assertEquals(expected.flowDirectionSemanticChecksum(), actual.flowDirectionSemanticChecksum());
        assertEquals(expected.flowAccumulationSemanticChecksum(), actual.flowAccumulationSemanticChecksum());
        assertEquals(expected.routingChecksum(), actual.routingChecksum());
        assertEquals(expected.basins(), actual.basins());
    }

    @FunctionalInterface
    private interface Elevation {
        long at(int x, int z);
    }

    @FunctionalInterface
    private interface Routable {
        boolean at(int x, int z);
    }
}
