package com.github.nankotsu029.landformcraft.core.v2.placement;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementPlanCompilerV2Test {
    private static final UUID PLACEMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPERATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORLD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String MANIFEST_CHECKSUM =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlacementPlanCompilerV2 compiler = new PlacementPlanCompilerV2();

    @Test
    void bundledExamplesMatchCompilerContract() throws IOException {
        PlacementPlanCompilerV2.CompiledPlacementV2 expected = compileFixture();
        assertEquals(expected.plan(), codec.readPlacementPlan(
                Path.of("examples/v2/placement/placement-plan-v2.json")));
        assertEquals(expected.journal(), codec.readPlacementJournal(
                Path.of("examples/v2/placement/placement-journal-v2.json")));
    }

    @Test
    void compilesSealedPlanAndPlannedJournal(@TempDir Path directory) throws IOException {
        PlacementPlanCompilerV2.CompiledPlacementV2 compiled = compileFixture();
        PlacementPlanV2 plan = compiled.plan();
        PlacementJournalV2 journal = compiled.journal();

        assertEquals(PlacementPlanV2.VERSION, plan.planVersion());
        assertEquals(PlacementPlanV2.PLACEMENT_CONTRACT_VERSION, plan.placementContractVersion());
        assertEquals(List.of(PlacementPlanV2.CAPABILITY_SURFACE_TWO_POINT_FIVE_D),
                plan.requiredCapabilities());
        assertFalse(plan.envelopeReferences().bound());
        assertFalse(plan.reservationConfirmationBinding().reservationBound());
        assertFalse(plan.reservationConfirmationBinding().confirmationIssued());
        assertEquals(codec.placementPlanChecksum(plan), plan.canonicalChecksum());
        assertEquals(PlacementJournalStateV2.PLANNED, journal.state());
        assertEquals(plan.canonicalChecksum(), journal.planChecksum());
        assertTrue(journal.tiles().stream().allMatch(tile -> tile.state() == PlacementTileStateV2.PENDING));
        assertEquals(codec.placementJournalChecksum(journal), journal.journalChecksum());

        Path planPath = directory.resolve("placement-plan-v2.json");
        Path journalPath = directory.resolve("placement-journal-v2.json");
        codec.writePlacementPlan(planPath, plan);
        codec.writePlacementJournal(journalPath, journal);
        assertEquals(plan, codec.readPlacementPlan(planPath));
        assertEquals(journal, codec.readPlacementJournal(journalPath));
        assertEquals(codec.canonicalPlacementPlan(plan), Files.readString(planPath));
        assertEquals(codec.canonicalPlacementJournal(journal), Files.readString(journalPath));
    }

    @Test
    void rejectsUnknownCapabilityFutureVersionTamperedChecksumAndUnsafePath() throws IOException {
        PlacementPlanV2 plan = compileFixture().plan();
        String canonical = codec.canonicalPlacementPlan(plan);

        assertThrows(IllegalArgumentException.class, () -> compile(
                List.of("future-capability"),
                TilePlanV2.of(64, 64, ScaleProfileV2.defaults(ScaleClassV2.MEDIUM))));
        assertThrows(StructuredDataValidationException.class, () -> codec.readPlacementPlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-plan"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readPlacementPlan(
                canonical.replace(PlacementPlanV2.PLACEMENT_CONTRACT_VERSION, "release-2-placement-contract-v2"),
                "future-contract"));
        assertThrows(IOException.class, () -> codec.readPlacementPlan(
                canonical.replace(plan.canonicalChecksum(), PlacementPlanV2.UNBOUND_CHECKSUM),
                "tampered-plan"));
        assertThrows(IllegalArgumentException.class, () -> new PlacementPlanV2.ReleaseBindingV2(
                1, 2, "../escape", MANIFEST_CHECKSUM,
                PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION));
        assertThrows(IllegalArgumentException.class, () -> new PlacementPlanV2.ReleaseBindingV2(
                1, 1, "releases/demo", MANIFEST_CHECKSUM,
                PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION));
    }

    @Test
    void rejectsTargetMismatchUnknownJournalStateAndTileBudgetOverflow() throws IOException {
        PlacementPlanCompilerV2.CompiledPlacementV2 compiled = compileFixture();
        PlacementPlanV2 plan = compiled.plan();
        PlacementPlanV2.PlacementTargetV2 other = new PlacementPlanV2.PlacementTargetV2(
                WORLD_ID, "other-world", PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                0, 64, 0, 0, 64, 0, 63, 80, 63);
        assertThrows(IllegalArgumentException.class, () -> plan.requireTarget(other));

        assertThrows(StructuredDataValidationException.class, () -> codec.readPlacementJournal(
                codec.canonicalPlacementJournal(compiled.journal())
                        .replace("\"PLANNED\"", "\"FUTURE_STATE\""),
                "future-state"));

        assertThrows(IllegalArgumentException.class, () -> compile(
                List.of(PlacementPlanV2.CAPABILITY_SURFACE_TWO_POINT_FIVE_D),
                new TilePlanV2(3_072, 3_072, 32, 0)));
    }

    @Test
    void tileOrderCanonicalAndDeterministicAcrossLocaleTimezoneAndThreads() throws Exception {
        PlacementPlanCompilerV2.CompiledPlacementV2 first = compileFixture();
        PlacementPlanCompilerV2.CompiledPlacementV2 repeated = compileFixture();
        assertEquals(first.plan().canonicalChecksum(), repeated.plan().canonicalChecksum());
        assertEquals(first.journal().journalChecksum(), repeated.journal().journalChecksum());
        assertEquals("tile-x0-z0", first.plan().tileOrder().tiles().get(0).tileId());
        assertEquals(0, first.plan().tileOrder().tiles().get(0).tileIndex());

        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertEquals(first.plan().canonicalChecksum(), compileFixture().plan().canonicalChecksum());
            assertEquals(first.journal().journalChecksum(), compileFixture().journal().journalChecksum());
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> tasks = List.of(
                    () -> compileFixture().plan().canonicalChecksum(),
                    () -> compileFixture().plan().canonicalChecksum(),
                    () -> compileFixture().plan().canonicalChecksum(),
                    () -> compileFixture().plan().canonicalChecksum());
            List<Future<String>> futures = executor.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertEquals(first.plan().canonicalChecksum(), future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        PlacementPlanCompilerV2.CompiledPlacementV2 different = compile(
                PlacementPlanV2.CAPABILITIES_SPARSE_VOLUME_WITH_ENVIRONMENT,
                TilePlanV2.of(64, 64, ScaleProfileV2.defaults(ScaleClassV2.MEDIUM)));
        assertNotEquals(first.plan().canonicalChecksum(), different.plan().canonicalChecksum());
        assertTrue(codec.canonicalPlacementPlan(first.plan()).getBytes(StandardCharsets.UTF_8).length
                < first.plan().budget().maximumCanonicalBytes());
    }

    @Test
    void leavesV1PlacementJournalCodecUnchanged() throws IOException {
        var v1 = new com.github.nankotsu029.landformcraft.format.LandformDataCodec();
        var journal = v1.readPlacementJournal(Path.of("examples/placement-journal.json"));
        assertEquals(1, journal.schemaVersion());
        assertEquals(com.github.nankotsu029.landformcraft.model.PlacementState.PLANNED, journal.state());
    }

    private PlacementPlanCompilerV2.CompiledPlacementV2 compileFixture() {
        return compile(
                List.of(PlacementPlanV2.CAPABILITY_SURFACE_TWO_POINT_FIVE_D),
                TilePlanV2.of(64, 64, ScaleProfileV2.defaults(ScaleClassV2.MEDIUM)));
    }

    private PlacementPlanCompilerV2.CompiledPlacementV2 compile(
            List<String> capabilities,
            TilePlanV2 tilePlan
    ) {
        return compiler.compile(new PlacementPlanCompilerV2.PlacementCompileRequestV2(
                PLACEMENT_ID,
                OPERATION_ID,
                "azure-coast-demo",
                PlacementPlanV2.PlacementActorV2.console(),
                new PlacementPlanV2.PlacementTargetV2(
                        WORLD_ID,
                        "world",
                        PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                        0, 64, 0,
                        0, 64, 0,
                        63, 80, 63),
                new PlacementPlanV2.ReleaseBindingV2(
                        PlacementPlanV2.ReleaseBindingV2.VERSION,
                        2,
                        "releases/azure-coast-r2",
                        MANIFEST_CHECKSUM,
                        PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                capabilities,
                tilePlan));
    }
}
