package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.catalog.FeatureSupportCatalogConsistencyVerifierV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2PlacementApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplySliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.rollback.PlacementRestoreSliceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementSettleTickV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceReceiptV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.verify.PlacementVerifyReadSliceV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.release.EnvironmentReleaseFixtureV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfacePublisherV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.BuiltInFeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-6-19 Release candidate audit and Phase gate evidence.
 *
 * <p>Pins the V2-6 parent-gate invariants that the RC audit relies on: the sealed Feature
 * Support Catalog stays free of Paper-capability false promotion and keeps the smoke-measured
 * 64x64 hard limit; the Release 2 capability list and valid dependency prefixes are unchanged;
 * the sealed placement example portfolio reads identically across read order, worker count,
 * locale, and timezone; and the full Release 2 lifecycle (plan, confirm, snapshot-all,
 * containment, apply, settle, full verify, Undo) produces identical world block states across
 * repeat runs, executor sizes, locale, and timezone with zero mutation before snapshot-all.</p>
 */
class PlacementPhaseGateV2Test {

    private static final UUID WORLD = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Map<String, ExampleReader> PORTFOLIO = portfolio();

    private static LandformV2DataCodec codec() {
        return new LandformV2DataCodec();
    }

    @Test
    void paperCapabilitiesMatchSmokeEvidenceAndHardLimitStaysSmokeMeasured() throws Exception {
        FeatureSupportCatalogV2 catalog =
                new FeatureSupportCatalogConsistencyVerifierV2().requireConsistentBuiltIn();

        // V2-11-01 binds the five Paper columns to real-machine evidence: SUPPORTED only for the
        // smoke-evidenced surface-2_5d prefix (V2-6-14/15, 64x64), EXPERIMENTAL for the other
        // Release capability prefixes (mechanism reachable via /lfc r2, per-prefix smoke
        // pending), UNSUPPORTED/NOT_APPLICABLE everywhere else (no Release container path).
        List<FeatureSupportCapabilityV2> paperColumns = List.of(
                FeatureSupportCapabilityV2.PAPER_APPLY,
                FeatureSupportCapabilityV2.POST_APPLY_VALIDATION,
                FeatureSupportCapabilityV2.SNAPSHOT,
                FeatureSupportCapabilityV2.ROLLBACK,
                FeatureSupportCapabilityV2.RESTART_RECOVERY);
        for (FeatureSupportEntryV2 entry : catalog.entries()) {
            FeatureSupportLevelV2 expected;
            if (BuiltInFeatureSupportCatalogV2.PAPER_SMOKE_EVIDENCED_CAPABILITIES
                    .contains(entry.requiredReleaseCapability())) {
                expected = FeatureSupportLevelV2.SUPPORTED;
            } else if (!entry.requiredReleaseCapability().isEmpty()) {
                expected = FeatureSupportLevelV2.EXPERIMENTAL;
            } else {
                expected = FeatureSupportLevelV2.UNSUPPORTED;
            }
            for (FeatureSupportCapabilityV2 capability : paperColumns) {
                FeatureSupportLevelV2 level = entry.support().level(capability);
                assertTrue(level == expected || level == FeatureSupportLevelV2.NOT_APPLICABLE,
                        entry.entryId() + " " + capability + " must be " + expected + ": " + level);
            }
            if (expected == FeatureSupportLevelV2.SUPPORTED) {
                assertEquals(BuiltInFeatureSupportCatalogV2.REQUIRED_RUNTIME,
                        entry.requiredRuntime(), entry.entryId());
                assertTrue(entry.evidenceRef().contains("smoke"), entry.entryId());
            }
        }
        assertEquals(4, catalog.entries().stream()
                .filter(entry -> entry.support().level(FeatureSupportCapabilityV2.PAPER_APPLY)
                        == FeatureSupportLevelV2.SUPPORTED)
                .count());

        // The unmeasured 500/1000 dimensions stay rejected; only the smoke-measured 64x64 limit
        // is admitted (V2-6-16/17 CANCELLED).
        assertEquals(PlacementDimensionLimitV2.smokeMeasured(), catalog.placementDimensionLimit());
        assertTrue(catalog.placementDimensionLimit().admits(64, 64));
        assertTrue(catalog.rejectsUnmeasuredPaperPromotion(65, 64));
        assertTrue(catalog.rejectsUnmeasuredPaperPromotion(500, 500));
        assertTrue(catalog.rejectsUnmeasuredPaperPromotion(1_000, 1_000));

        // The sealed example is byte-stable against the built-in catalog data.
        FeatureSupportCatalogCodecV2 catalogCodec = new FeatureSupportCatalogCodecV2();
        FeatureSupportCatalogV2 sealedExample =
                catalogCodec.read(Path.of("examples/v2/catalog/feature-support-catalog-v2.json"));
        catalogCodec.verifyChecksum(sealedExample);
        assertEquals(catalogCodec.builtInSealed().canonicalChecksum(),
                sealedExample.canonicalChecksum());

        // V2-6 adds no Release capability, and the V2-6-12 dependency matrix keeps its five
        // valid prefixes as the only readable capability sets.
        assertEquals(List.of(
                        ReleaseArtifactCatalogV2.ENVIRONMENT_FIELDS,
                        ReleaseArtifactCatalogV2.HYDROLOGY_PLAN,
                        ReleaseArtifactCatalogV2.SPARSE_VOLUME,
                        ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT);
        assertEquals(5, ReleaseCapabilityDependencyMatrixV2.validPrefixes().size());
    }

    @Test
    void placementExamplePortfolioIsStableAcrossOrderThreadsLocaleAndTimezone() throws Exception {
        Map<String, String> baseline = read(List.copyOf(PORTFOLIO.keySet()), 1);
        List<String> reversed = new ArrayList<>(PORTFOLIO.keySet());
        Collections.reverse(reversed);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(baseline, read(reversed, 4));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void release2LifecycleAppliesVerifiesAndUndoesDeterministically(@TempDir Path root)
            throws Exception {
        LifecycleOutcome first = runLifecycle(root.resolve("run-a"), 4, 2, 16);

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        LifecycleOutcome second;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            second = runLifecycle(root.resolve("run-b"), 1, 1, 8);
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        // Repeat run with different worker counts, locale, and timezone produces the identical
        // applied world block-state map: the RC determinism/runtime-profile requirement.
        assertEquals(first.appliedStates(), second.appliedStates());
        assertFalse(first.appliedStates().isEmpty());

        // Both runs end in terminal UNDONE with the world restored to the snapshot baseline.
        for (LifecycleOutcome outcome : List.of(first, second)) {
            assertEquals(PlacementJournalStateV2.UNDONE, outcome.undoneState());
            outcome.undoneStates().values()
                    .forEach(state -> assertEquals("minecraft:stone", state));
        }
    }

    private LifecycleOutcome runLifecycle(Path root, int cpu, int io, int queue) throws Exception {
        Path releases = root.resolve("releases");
        EnvironmentReleaseFixtureV2.Fixture fixture =
                EnvironmentReleaseFixtureV2.build(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 release = new ReleaseSurfacePublisherV2().publish(
                releases, "phase-gate", fixture.source().hydrology().surface(), false, () -> false);
        FakeWorld gateway = new FakeWorld("minecraft:stone");
        GenerationExecutors executors = GenerationExecutors.create(cpu, io, queue);
        Release2PlacementApplicationServiceV2 service = new Release2PlacementApplicationServiceV2(
                releases, root.resolve("state"), executors, gateway, Clock.systemUTC(),
                64L * 1024L * 1024L);
        try {
            var prepared = service.plan(new Release2PlacementApplicationServiceV2.PlanRequestV2(
                    release.releaseDirectory().getFileName().toString(), WORLD, "world",
                    10, 64, 20, new WorldAabbV2(-100, -64, -100, 100, 320, 100),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false))
                    .toCompletableFuture().join();
            assertEquals(PlacementJournalStateV2.CONFIRMATION_ISSUED, prepared.journal().state());
            assertEquals(0, gateway.mutationCalls);

            var confirmed = service.confirm(new Release2PlacementApplicationServiceV2.ConfirmRequestV2(
                    prepared.plan().placementId(), prepared.confirmationToken(),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false))
                    .toCompletableFuture().join();
            assertEquals(PlacementJournalStateV2.SNAPSHOT_COMPLETE, confirmed.journal().state());
            assertEquals(0, gateway.mutationCalls);
            assertTrue(gateway.snapshotReads > 0);

            var executed = service.execute(new Release2PlacementApplicationServiceV2.ExecuteRequestV2(
                    prepared.plan().placementId(), PlacementPlanV2.PlacementActorV2.console(),
                    () -> false)).toCompletableFuture().join();
            assertEquals(PlacementJournalStateV2.APPLIED, executed.journal().state());
            assertFalse(executed.rolledBack());
            assertTrue(gateway.firstMutationAfterSnapshot);
            Map<Coord, String> appliedStates = new TreeMap<>(gateway.states);

            var preparedUndo = service.prepareUndo(
                    prepared.plan().placementId(), PlacementPlanV2.PlacementActorV2.console())
                    .toCompletableFuture().join();
            var undone = service.executeUndo(
                    prepared.plan().placementId(), preparedUndo.plaintextToken(),
                    PlacementPlanV2.PlacementActorV2.console(), () -> false)
                    .toCompletableFuture().join();
            return new LifecycleOutcome(
                    appliedStates,
                    new TreeMap<>(gateway.states),
                    undone.undoneJournal().state());
        } finally {
            service.close();
            assertTrue(executors.shutdown(Duration.ofSeconds(10)));
        }
    }

    private static Map<String, String> read(List<String> names, int threads) throws Exception {
        List<Callable<Map.Entry<String, String>>> tasks = names.stream()
                .<Callable<Map.Entry<String, String>>>map(name -> () ->
                        Map.entry(name, PORTFOLIO.get(name).canonicalForm()))
                .toList();
        Map<String, String> result = new LinkedHashMap<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (var future : executor.invokeAll(tasks)) {
                Map.Entry<String, String> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String name : PORTFOLIO.keySet()) {
            ordered.put(name, result.get(name));
        }
        return ordered;
    }

    private static Map<String, ExampleReader> portfolio() {
        Map<String, ExampleReader> readers = new LinkedHashMap<>();
        Path placement = Path.of("examples/v2/placement");
        readers.put("placement-plan", () ->
                codec().readPlacementPlan(placement.resolve("placement-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("placement-journal", () ->
                codec().readPlacementJournal(placement.resolve("placement-journal-v2.json"))
                        .toString());
        readers.put("placement-envelope-plan", () ->
                codec().readPlacementEnvelopePlan(placement.resolve("placement-envelope-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("placement-reservation-plan", () ->
                codec().readPlacementReservationPlan(
                        placement.resolve("placement-reservation-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("placement-safety-state", () ->
                codec().readPlacementSafetyStateV2(placement.resolve("placement-safety-state-v2.json"))
                        .canonicalChecksum());
        readers.put("placement-snapshot-plan", () ->
                codec().readPlacementSnapshotPlan(placement.resolve("placement-snapshot-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("placement-containment-policy", () ->
                codec().readPlacementContainmentPolicy(
                        placement.resolve("placement-containment-policy-v2.json"))
                        .toString());
        readers.put("placement-containment-evidence", () ->
                codec().readPlacementContainmentEvidence(
                        placement.resolve("placement-containment-evidence-v2.json"))
                        .canonicalChecksum());
        readers.put("placement-settle-verify-policy", () ->
                codec().readPlacementSettleVerifyPolicy(
                        placement.resolve("placement-settle-verify-policy-v2.json"))
                        .toString());
        readers.put("placement-verify-evidence", () ->
                codec().readPlacementVerifyEvidence(
                        placement.resolve("placement-verify-evidence-v2.json"))
                        .canonicalChecksum());
        readers.put("placement-undo-plan", () ->
                codec().readPlacementUndoPlan(placement.resolve("placement-undo-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("placement-recovery-plan", () ->
                codec().readPlacementRecoveryPlan(placement.resolve("placement-recovery-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("feature-support-catalog", () ->
                new FeatureSupportCatalogCodecV2()
                        .read(Path.of("examples/v2/catalog/feature-support-catalog-v2.json"))
                        .canonicalChecksum());
        return Collections.unmodifiableMap(readers);
    }

    @FunctionalInterface
    private interface ExampleReader {
        String canonicalForm() throws Exception;
    }

    private record LifecycleOutcome(
            Map<Coord, String> appliedStates,
            Map<Coord, String> undoneStates,
            PlacementJournalStateV2 undoneState
    ) { }

    private static final class FakeWorld implements PlacementWorldGatewayV2 {
        private final String defaultState;
        private final Map<Coord, String> states = new ConcurrentHashMap<>();
        private int snapshotReads;
        private int mutationCalls;
        private boolean firstMutationAfterSnapshot;

        private FakeWorld(String defaultState) {
            this.defaultState = defaultState;
        }

        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            snapshotReads++;
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        consumer.accept(x, y, z, state(x, y, z));
                    }
                }
            }
        }

        @Override
        public CompletionStage<PlacementApplySliceReceiptV2> applyBlockSlice(PlacementApplySliceV2 slice) {
            if (mutationCalls++ == 0) firstMutationAfterSnapshot = snapshotReads >= 2;
            slice.mutations().forEach(block -> states.put(
                    new Coord(block.x(), block.y(), block.z()), block.blockState()));
            return CompletableFuture.completedFuture(new PlacementApplySliceReceiptV2(
                    slice.operationId(), slice.tileId(), slice.sliceSequence(), slice.mutations().size(),
                    true, true, true));
        }

        @Override
        public CompletionStage<PlacementRestoreSliceReceiptV2> restoreBlockSlice(PlacementRestoreSliceV2 slice) {
            mutationCalls++;
            slice.blocks().forEach(block -> states.put(
                    new Coord(block.x(), block.y(), block.z()), block.blockState()));
            return CompletableFuture.completedFuture(new PlacementRestoreSliceReceiptV2(
                    slice.operationId(), slice.tileId(), slice.sliceSequence(), slice.blocks().size(),
                    true, true, true));
        }

        @Override
        public CompletionStage<PlacementSettleTickReceiptV2> advanceSettleTick(PlacementSettleTickV2 tick) {
            return CompletableFuture.completedFuture(new PlacementSettleTickReceiptV2(
                    tick.operationId(), tick.tickIndex(), true, true, 0, 0, List.of()));
        }

        @Override
        public CompletionStage<PlacementVerifyReadSliceReceiptV2> readVerifySlice(
                PlacementVerifyReadSliceV2 slice
        ) {
            List<String> blocks = new ArrayList<>();
            WorldAabbV2 region = slice.effectEnvelope();
            for (int offset = 0; offset < slice.blockCount(); offset++) {
                long index = slice.startIndex() + offset;
                int width = region.maxX() - region.minX() + 1;
                int length = region.maxZ() - region.minZ() + 1;
                long layer = (long) width * length;
                int x = region.minX() + (int) (index % width);
                int z = region.minZ() + (int) ((index / width) % length);
                int y = region.minY() + (int) (index / layer);
                blocks.add(state(x, y, z));
            }
            return CompletableFuture.completedFuture(new PlacementVerifyReadSliceReceiptV2(
                    slice.operationId(), slice.sliceSequence(), true, true, blocks));
        }

        private String state(int x, int y, int z) {
            return states.getOrDefault(new Coord(x, y, z), defaultState);
        }
    }

    private record Coord(int x, int y, int z) implements Comparable<Coord> {
        @Override
        public int compareTo(Coord other) {
            int byX = Integer.compare(x, other.x);
            if (byX != 0) return byX;
            int byY = Integer.compare(y, other.y);
            if (byY != 0) return byY;
            return Integer.compare(z, other.z);
        }
    }
}
