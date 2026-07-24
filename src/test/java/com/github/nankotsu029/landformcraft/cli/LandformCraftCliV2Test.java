package com.github.nankotsu029.landformcraft.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-12-03 CLI surface for the official {@code lfc v2 <verb>} path. */
class LandformCraftCliV2Test {
    private static final String REQUEST = "examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json";
    private static final String INTENT = "examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json";

    @Test
    void requestVerbStrictlyReadsAV2Request() {
        Result result = run("v2", "request", "info", REQUEST);

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("requestId: harbor-cove-64-honored"), result.output());
        assertTrue(result.output().contains("v2CorrelationId: v2-"), result.output());
    }

    @Test
    void exportVerbPublishesAPlacementEligibleRelease(@TempDir Path root) {
        Result result = run("v2", "export", REQUEST, INTENT,
                root.resolve("exports").toString(), "cli-export", "water", "54", "46");

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("requiredCapabilities: [surface-2_5d]"), result.output());
        assertTrue(result.output().contains("placementEligible: true"), result.output());
        assertTrue(Files.isDirectory(root.resolve("exports").resolve("cli-export")));
        assertTrue(Files.isRegularFile(root.resolve("exports").resolve("cli-export.zip")));
    }

    @Test
    void exportHydrologyPlanVerbPublishesTheRiverOfflineProductionRoute(@TempDir Path root) {
        // V2-15-10 / ADR 0039 Candidate A: the hydrology-plan CLI form exports a RIVER-declaring
        // intent through the OFFLINE_PRODUCTION route without touching Paper capability.
        String riverRequest = "examples/v2/diagnostic/harbor-cove-64-honored-river.request-v2.json";
        String riverIntent = "examples/v2/diagnostic/harbor-cove-64-honored-river.terrain-intent-v2.json";
        Result result = run("v2", "export", "hydrology-plan", riverRequest, riverIntent,
                root.resolve("exports").toString(), "cli-export-hydrology", "water", "54", "46");

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("requiredCapabilities: [hydrology-plan, surface-2_5d]"),
                result.output());
        assertTrue(result.output().contains("placementEligible: true"), result.output());
        assertTrue(Files.isDirectory(root.resolve("exports").resolve("cli-export-hydrology")));
        assertTrue(Files.isRegularFile(root.resolve("exports").resolve("cli-export-hydrology.zip")));
    }

    @Test
    void exportVerbPublishesTheFoundationProducerOfflineProductionRoute(@TempDir Path root) {
        // V2-19-07: an intent declaring PLAIN reaches the public surface export path through the real
        // CLI — the macro foundation producer runs inside the same surface-2_5d pipeline, so the
        // capability set is unchanged and the Release stays placement-eligible.
        String plainRequest = "examples/v2/diagnostic/harbor-cove-64-honored-plain.request-v2.json";
        String plainIntent = "examples/v2/diagnostic/harbor-cove-64-honored-plain.terrain-intent-v2.json";
        Result result = run("v2", "export", plainRequest, plainIntent,
                root.resolve("exports").toString(), "cli-export-plain", "water", "54", "46");

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("requiredCapabilities: [surface-2_5d]"), result.output());
        assertTrue(result.output().contains("placementEligible: true"), result.output());
        assertTrue(Files.isDirectory(root.resolve("exports").resolve("cli-export-plain")));
        assertTrue(Files.isRegularFile(root.resolve("exports").resolve("cli-export-plain.zip")));
    }

    @Test
    void generateVerbPublishesTheDirectoryWithoutAZip(@TempDir Path root) {
        Result result = run("v2", "generate", REQUEST, INTENT,
                root.resolve("exports").toString(), "cli-generate", "water", "54", "46");

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(Files.isDirectory(root.resolve("exports").resolve("cli-generate")));
        assertFalse(Files.exists(root.resolve("exports").resolve("cli-generate.zip")));
    }

    @Test
    void previewVerbListsTheSealedDiagnosticLayers(@TempDir Path root) {
        assertEquals(0, run("v2", "generate", REQUEST, INTENT,
                root.resolve("exports").toString(), "cli-preview", "water", "54", "46").exitCode());

        Result result = run("v2", "preview", root.resolve("exports").resolve("cli-preview").toString());

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("previewLayers: ["), result.output());
        assertTrue(result.output().contains("requiredCapabilities: [surface-2_5d]"), result.output());
    }

    @Test
    void worldBoundVerbsAreRejectedWithTheStablePaperOnlyCode() {
        Result result = run("v2", "place", "execute", "11111111-1111-1111-1111-111111111111");

        assertEquals(2, result.exitCode());
        assertTrue(result.error().contains("V2_PAPER_ONLY"), result.error());
        assertTrue(result.error().contains("v2CorrelationId=v2-"), result.error());
    }

    @Test
    void removedR2AliasFailsClosed() {
        Result result = run("r2", "status", "11111111-1111-1111-1111-111111111111");

        assertEquals(2, result.exitCode());
        assertTrue(result.error().contains("V2_UNKNOWN_VERB"), result.error());
    }

    @Test
    void requestAuthoringCreatesEditsAndListsRequests(@TempDir Path root) {
        String data = root.resolve("data").toString();

        Result created = run("--data-dir", data, "v2", "request", "create", "cli-authored");
        assertEquals(0, created.exitCode(), created.error());
        assertTrue(created.output().contains("requestId: cli-authored"), created.output());

        Result bounds = run("--data-dir", data, "v2", "request", "bounds", "cli-authored",
                "64", "64", "32", "72", "50");
        assertEquals(0, bounds.exitCode(), bounds.error());
        assertTrue(bounds.output().contains("width: 64"), bounds.output());
        assertTrue(bounds.output().contains("waterLevel: 50"), bounds.output());

        Result prompt = run("--data-dir", data, "v2", "request", "prompt", "cli-authored",
                "A", "sheltered", "cove", "with", "a", "stone", "breakwater.");
        assertEquals(0, prompt.exitCode(), prompt.error());

        Result list = run("--data-dir", data, "v2", "request", "list");
        assertEquals(0, list.exitCode(), list.error());
        assertTrue(list.output().contains("cli-authored"), list.output());
    }

    @Test
    void anAuthoredRequestIsAcceptedByTheValidateAndExportVerbs(@TempDir Path root) throws Exception {
        String data = root.resolve("data").toString();
        // The export path pairs a request with the intent whose intentId matches its requestId, so
        // authoring reproduces the sealed honored fixture request and exports it with the sealed intent.
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "harbor-cove-64-honored").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "harbor-cove-64-honored",
                "64", "64", "32", "72", "50").exitCode());
        // V2-18-09/10: the mask is resolved into generation and must agree with the composed coastal
        // geometry, which the seed determines, so the authored request reproduces the fixture's seed.
        assertEquals(0, run("--data-dir", data, "v2", "request", "generation", "harbor-cove-64-honored",
                "827413", "64").exitCode());
        // V2-18-03: declare the real mask + digest so the HARD preflight gate can resolve it.
        assertEquals(0, run("--data-dir", data, "v2", "request", "constraint-map", "harbor-cove-64-honored",
                "coast-mask", "maps/harbor-cove-64-honored-land-water-u8.png",
                "b1d98bff54aa3fa8a6df022df9057f05ee0ad2ece1873c13982222614989a48e", "64", "64").exitCode());
        // V2-18-10: the surface owner gate is fail-closed, so the authored request must also declare
        // the explicit foundation input's per-medium base elevation.
        Result levels = run("--data-dir", data, "v2", "request", "foundation-base-levels",
                "harbor-cove-64-honored", "54", "46");
        assertEquals(0, levels.exitCode(), levels.error());
        assertTrue(levels.output().contains("landSurfaceY=54"), levels.output());
        assertTrue(levels.output().contains("waterBedY=46"), levels.output());
        assertEquals(0, run("--data-dir", data, "v2", "request", "prompt", "harbor-cove-64-honored",
                "A", "sheltered", "cove.").exitCode());
        Path requests = root.resolve("data").resolve("v2").resolve("requests");
        Path stored = requests.resolve("harbor-cove-64-honored.request-v2.json");
        assertTrue(Files.isRegularFile(stored));
        // The gate resolves the mask relative to the request's own directory.
        Files.createDirectories(requests.resolve("maps"));
        Files.copy(Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-land-water-u8.png"),
                requests.resolve("maps").resolve("harbor-cove-64-honored-land-water-u8.png"));

        Result validated = run("v2", "request", "validate", stored.toString());
        assertEquals(0, validated.exitCode(), validated.error());
        assertTrue(validated.output().contains("requestId: harbor-cove-64-honored"), validated.output());

        // F1 is only closed if an authored request reaches the production export path.
        Result exported = run("v2", "export", stored.toString(), INTENT,
                root.resolve("exports").toString(), "authored-export", "water", "54", "46");
        assertEquals(0, exported.exitCode(), exported.error());
        assertTrue(exported.output().contains("placementEligible: true"), exported.output());
    }

    @Test
    void theFoundationDetailVerbAuthorsCoherentBackgroundRelief(@TempDir Path root) throws Exception {
        // V2-19-12 (ADR 0041): coherent detail is export-relevant background state, so authoring must be
        // able to declare it — but only over an explicit foundation, and never in a way that crosses the
        // water level (fail closed, not clamped).
        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "detail-request").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "detail-request",
                "64", "64", "32", "72", "50").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "foundation-base-levels",
                "detail-request", "54", "46").exitCode());

        Result detail = run("--data-dir", data, "v2", "request", "foundation-detail",
                "detail-request", "3", "2", "16", "3");
        assertEquals(0, detail.exitCode(), detail.error());
        assertTrue(detail.output().contains("landAmplitudeBlocks=3"), detail.output());
        assertTrue(detail.output().contains("wavelengthBlocks=16"), detail.output());
        assertTrue(detail.output().contains("octaves=3"), detail.output());

        Path stored = root.resolve("data").resolve("v2").resolve("requests")
                .resolve("detail-request.request-v2.json");
        assertTrue(Files.readString(stored).contains("foundationDetail"));

        // Fail closed: an amplitude that would sink a land surface below the water level is rejected.
        Result rejected = run("--data-dir", data, "v2", "request", "foundation-detail",
                "detail-request", "8", "2", "16", "3");
        assertNotEquals(0, rejected.exitCode());
    }

    @Test
    void theMaskReconcileVerbAuthorsThePrePassDeclaration(@TempDir Path root) throws Exception {
        // V2-19-14 (ADR 0043 D3): the pre-pass changes which geometry is generated, so it has to be
        // declarable from authoring — over an explicit foundation only, and within its own tolerance
        // and evaluation budget (fail closed, not clamped).
        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "reconcile-request").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "reconcile-request",
                "64", "64", "32", "72", "50").exitCode());

        // No explicit foundation input yet: the declaration has nothing to align against.
        assertNotEquals(0, run("--data-dir", data, "v2", "request", "mask-reconcile",
                "reconcile-request", "4").exitCode());

        assertEquals(0, run("--data-dir", data, "v2", "request", "foundation-base-levels",
                "reconcile-request", "54", "46").exitCode());
        Result declared = run("--data-dir", data, "v2", "request", "mask-reconcile",
                "reconcile-request", "4");
        assertEquals(0, declared.exitCode(), declared.error());
        assertTrue(declared.output().contains("toleranceBlocks=4"), declared.output());

        Path stored = root.resolve("data").resolve("v2").resolve("requests")
                .resolve("reconcile-request.request-v2.json");
        assertTrue(Files.readString(stored).contains("maskFeatureReconcile"));

        // Fail closed: a tolerance outside 1..8 is rejected rather than clamped into range.
        assertNotEquals(0, run("--data-dir", data, "v2", "request", "mask-reconcile",
                "reconcile-request", "9").exitCode());
        assertNotEquals(0, run("--data-dir", data, "v2", "request", "mask-reconcile",
                "reconcile-request", "0").exitCode());
    }

    @Test
    void theConstraintMapVerbDeclaresTheCanonicalLandWaterSource(@TempDir Path root) {
        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "cli-authored").exitCode());

        Result declared = run("--data-dir", data, "v2", "request", "constraint-map", "cli-authored",
                "coast-mask", "maps/coast.png", "d".repeat(64), "128", "128");

        assertEquals(0, declared.exitCode(), declared.error());
        assertTrue(declared.output().contains("constraintMaps: 1"), declared.output());
    }

    @Test
    void aConstraintMapWhoseAspectDoesNotMatchTheBoundsIsRejected(@TempDir Path root) {
        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "cli-authored").exitCode());

        // Default bounds are 128x128, so a 64x128 map cannot map onto them.
        Result declared = run("--data-dir", data, "v2", "request", "constraint-map", "cli-authored",
                "coast-mask", "maps/coast.png", "d".repeat(64), "64", "128");

        assertEquals(1, declared.exitCode());
        assertTrue(declared.error().contains("aspect"), declared.error());
    }

    @Test
    void requestAuthoringRefusesDuplicatesUnsafeIdsAndSecretPrompts(@TempDir Path root) {
        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "cli-authored").exitCode());

        assertEquals(1, run("--data-dir", data, "v2", "request", "create", "cli-authored").exitCode());
        assertEquals(1, run("--data-dir", data, "v2", "request", "create", "../escape").exitCode());

        Result secret = run("--data-dir", data, "v2", "request", "prompt", "cli-authored",
                "api_key=0123456789abcdef");
        assertEquals(1, secret.exitCode());
        assertTrue(secret.error().contains("resembles a secret"), secret.error());
    }

    @Test
    void promptCaptureWithoutTextIsRejectedOnTheCliSurface() {
        Result result = run("v2", "request", "prompt", "cli-authored");

        assertEquals(2, result.exitCode());
        assertTrue(result.error().contains("V2_USAGE"), result.error());
        assertTrue(result.error().contains("<prompt...>"), result.error());
    }

    @Test
    void selectionAuthoringIsRejectedOnTheCliWithTheStablePaperOnlyCode() {
        Result result = run("v2", "request", "selection", "cli-authored");

        assertEquals(2, result.exitCode());
        assertTrue(result.error().contains("V2_PAPER_ONLY"), result.error());
    }

    @Test
    void jobAndCandidateVerbsReadTheDurableV2JobStore(@TempDir Path root) throws Exception {
        String data = root.resolve("data").toString();
        Path jobs = root.resolve("data").resolve("v2").resolve("jobs");
        Files.createDirectories(jobs);
        String jobId = "6f1d9e2a-3c4b-4a7d-8e51-2b9c7d0a4f63";
        Files.copy(Path.of("examples/v2/job/export-job-v2.json"),
                jobs.resolve(jobId + ".job-v2.json"));

        Result status = run("--data-dir", data, "v2", "job", "status", jobId);
        assertEquals(0, status.exitCode(), status.error());
        assertTrue(status.output().contains("state: PUBLISHED"), status.output());
        assertTrue(status.output().contains("requestId: harbor-cove-64"), status.output());

        Result list = run("--data-dir", data, "v2", "job", "list");
        assertEquals(0, list.exitCode(), list.error());
        assertTrue(list.output().contains(jobId), list.output());

        Result candidates = run("--data-dir", data, "v2", "candidate", "list", "harbor-cove-64");
        assertEquals(0, candidates.exitCode(), candidates.error());
        assertTrue(candidates.output().contains("harbor-cove-64-r1"), candidates.output());

        // A different request has no candidates, and the published job is not attributed to it.
        Result other = run("--data-dir", data, "v2", "candidate", "list", "other-request");
        assertEquals(0, other.exitCode(), other.error());
        assertFalse(other.output().contains(jobId), other.output());
    }

    @Test
    void unknownAndMalformedJobIdsAreRejected(@TempDir Path root) {
        String data = root.resolve("data").toString();

        assertEquals(1, run("--data-dir", data, "v2", "job", "status",
                "11111111-1111-1111-1111-111111111111").exitCode());
        Result traversal = run("--data-dir", data, "v2", "job", "status", "../escape");
        assertEquals(1, traversal.exitCode());
        assertTrue(traversal.error().contains("canonical UUID"), traversal.error());
    }

    @Test
    void theTwoStepExportIsRejectedOnTheCliWithTheStablePaperOnlyCode() {
        for (Result result : List.of(
                run("v2", "export", "plan", REQUEST, INTENT, "exports", "rel", "water", "54", "46"),
                run("v2", "export", "create", "plan-id", "token"))) {
            assertEquals(2, result.exitCode());
            assertTrue(result.error().contains("V2_PAPER_ONLY"), result.error());
        }
    }

    @Test
    void theSynchronousExportFormStillWorksAlongsideTheJobVerbs(@TempDir Path root) {
        Result result = run("v2", "export", REQUEST, INTENT,
                root.resolve("exports").toString(), "sync-still-works", "water", "54", "46");

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(Files.isDirectory(root.resolve("exports").resolve("sync-still-works")));
    }

    @Test
    void journalVerifyStrictlyReadsAV2PlacementJournal() {
        Result result = run("v2", "journal-verify",
                "examples/v2/placement/placement-journal-v2.json");

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("Verified v2 placement journal"), result.output());
        assertTrue(result.output().contains("placementId: 11111111-1111-1111-1111-111111111111"),
                result.output());
        assertTrue(result.output().contains("state: PLANNED"), result.output());
    }

    @Test
    void journalVerifyRejectsACorruptedJournalFailClosed(@TempDir Path root) throws Exception {
        Path corrupt = root.resolve("corrupt-journal.json");
        Files.writeString(corrupt, "{\"journalVersion\":1}");

        Result result = run("v2", "journal-verify", corrupt.toString());

        assertEquals(1, result.exitCode());
    }

    @Test
    void recoveryInspectReadsAJournalAndARecoveryPlanReadOnly() {
        Result journal = run("v2", "recovery", "inspect", "journal",
                "examples/v2/placement/placement-journal-v2.json");
        assertEquals(0, journal.exitCode(), journal.error());
        assertTrue(journal.output().contains("kind: journal"), journal.output());
        assertTrue(journal.output().contains("recoveryRequired: false"), journal.output());

        Result plan = run("v2", "recovery", "inspect", "plan",
                "examples/v2/placement/placement-recovery-plan-v2.json");
        assertEquals(0, plan.exitCode(), plan.error());
        assertTrue(plan.output().contains("kind: recovery-plan"), plan.output());
        assertTrue(plan.output().contains("classification: SAFE_TO_ACCEPT"), plan.output());
        assertTrue(plan.output().contains("sourceJournalState: RECOVERY_REQUIRED"), plan.output());
    }

    @Test
    void recoveryInspectRejectsAnUnknownKind() {
        Result result = run("v2", "recovery", "inspect", "everything",
                "examples/v2/placement/placement-journal-v2.json");

        assertEquals(1, result.exitCode());
        assertTrue(result.error().contains("journal") && result.error().contains("plan"),
                result.error());
    }

    @Test
    void retentionVerbsAreRejectedOnTheCliWithTheStablePaperOnlyCode() {
        for (Result result : List.of(
                run("v2", "retention", "plan", "11111111-1111-1111-1111-111111111111"),
                run("v2", "retention", "execute",
                        "11111111-1111-1111-1111-111111111111", "plan-id", "token"),
                run("v2", "retention", "status", "11111111-1111-1111-1111-111111111111"))) {
            assertEquals(2, result.exitCode());
            assertTrue(result.error().contains("V2_PAPER_ONLY"), result.error());
        }
    }

    @Test
    void unknownV2VerbsUseTheStableRoutingCode() {
        Result result = run("v2", "teleport");

        assertEquals(2, result.exitCode());
        assertTrue(result.error().contains("V2_UNKNOWN_VERB"), result.error());
    }

    @Test
    void migrateInspectRehearsesWithoutWritingAnything(@TempDir Path root) throws Exception {
        Result result = run("v2", "migrate", "inspect", "intent",
                "src/main/resources/legacy/v1/fixtures/mountain-stream/terrain-intent.json");

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("status: DRY_RUN"), result.output());
        assertTrue(result.output().contains("lossy: true"), result.output());
        assertTrue(result.output().contains("zone:mountain-source"), result.output());
        assertEquals(0, Files.list(root).count(), "a dry run writes nothing");
    }

    @Test
    void migrateApplyRefusesLossWithoutAnExplicitAcknowledgement(@TempDir Path root) {
        Result strict = run("v2", "migrate", "apply", "intent",
                "src/main/resources/legacy/v1/fixtures/mountain-stream/terrain-intent.json",
                root.resolve("out").toString(), "cli-migration", "strict");

        assertEquals(1, strict.exitCode());
        assertFalse(Files.exists(root.resolve("out").resolve("cli-migration")));

        Result accepted = run("v2", "migrate", "apply", "intent",
                "src/main/resources/legacy/v1/fixtures/mountain-stream/terrain-intent.json",
                root.resolve("out").toString(), "cli-migration", "accept-lossy");

        assertEquals(0, accepted.exitCode(), accepted.error());
        assertTrue(accepted.output().contains("status: PUBLISHED"), accepted.output());
        assertTrue(Files.isRegularFile(
                root.resolve("out").resolve("cli-migration").resolve("migration-report-v2.json")));
    }

    @Test
    void defaultSurfaceRoutesToV2WithoutAPrefix() {
        // `request validate` (no v2 prefix) resolves to the v2 request verb after the switch.
        Result result = run("request", "validate", REQUEST);
        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("requestId: harbor-cove-64-honored"), result.output());
        assertTrue(result.output().contains("v2CorrelationId: v2-"), result.output());
    }

    @Test
    void defaultSurfaceExportMatchesTheExplicitV2Form(@TempDir Path root) {
        Result bare = run("export", REQUEST, INTENT,
                root.resolve("a").toString(), "bare-export", "water", "54", "46");
        Result explicit = run("v2", "export", REQUEST, INTENT,
                root.resolve("b").toString(), "explicit-export", "water", "54", "46");

        assertEquals(0, bare.exitCode(), bare.error());
        assertEquals(0, explicit.exitCode(), explicit.error());
        assertTrue(bare.output().contains("placementEligible: true"), bare.output());
    }

    @Test
    void theRemovedV1OptInFailsClosed() {
        Result result = run("--v1", "validate");

        assertEquals(2, result.exitCode());
        assertTrue(result.error().contains("V2_UNKNOWN_VERB"), result.error());
    }

    @Test
    void aV1OnlyVerbIsNotOnTheDefaultSurface() {
        // `verify` has no v2 verb and the removed --v1 switch cannot resurrect it.
        assertEquals(2, run("verify", "build/nowhere").exitCode());
        assertTrue(run("verify", "build/nowhere").error().contains("V2_UNKNOWN_VERB"),
                run("verify", "build/nowhere").error());
        assertEquals(2, run("--v1", "verify", "build/nowhere").exitCode());
    }

    @Test
    void versionNeutralAndRemovedV1Dispatch() {
        // version stays version-neutral; an unknown default verb is a v2 routing error.
        assertEquals(0, run("version").exitCode());
        assertEquals(2, run("definitely-not-a-command").exitCode());
        assertTrue(run("definitely-not-a-command").error().contains("V2_UNKNOWN_VERB"),
                run("definitely-not-a-command").error());
        assertTrue(run("--v1", "definitely-not-a-command").error().contains("V2_UNKNOWN_VERB"),
                run("--v1", "definitely-not-a-command").error());
        assertFalse(run("--help").output().contains("--v1"));
    }

    // --- V2-14-01: image extraction path wiring ------------------------------------------------

    @Test
    void extractPromoteDeclareAndExportRunsTheWholeImageFidelityChain(@TempDir Path root) throws Exception {
        // 抽出: an untrusted colour PNG becomes a sealed V2-7 land/water draft bundle.
        Path image = root.resolve("coast.png");
        writeLandWaterPngFromMask(image,
                Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-land-water-u8.png"));
        Path draftDir = root.resolve("draft");
        Result extracted = run("v2", "extract", "land-water", image.toString(), draftDir.toString());
        assertEquals(0, extracted.exitCode(), extracted.error());
        assertTrue(extracted.output().contains("kind: land-water"), extracted.output());
        assertTrue(extracted.output().contains("width: 64"), extracted.output());
        assertTrue(Files.isRegularFile(draftDir.resolve("extracted-mask-draft-v2.json")));

        // 昇格: an explicit confidence threshold + UNKNOWN handling promotes the draft to a V2-1 map.
        Path promoteDir = root.resolve("promoted");
        Result promoted = run("v2", "promote", "land-water", draftDir.toString(),
                promoteDir.toString(), "1", "reject");
        assertEquals(0, promoted.exitCode(), promoted.error());
        assertTrue(promoted.output().contains("mapPath: land-water.png"), promoted.output());
        assertTrue(Files.isRegularFile(promoteDir.resolve("land-water.png")));
        String sha256 = valueOf(promoted.output(), "sha256");

        // request宣言: the extracted map is declared as the request's single constraint source.
        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "harbor-cove-64-honored").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "harbor-cove-64-honored",
                "64", "64", "32", "72", "50").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "generation", "harbor-cove-64-honored",
                "827413", "64").exitCode());
        // The sealed intent binds to constraint-source:coast-mask, so the declared slug reuses it. Since
        // V2-18-03 the HARD preflight gate resolves the declared mask (existence + digest + dimensions),
        // so the extracted PNG is placed beside the stored request rather than merely declared.
        Result declared = run("--data-dir", data, "v2", "request", "constraint-map", "harbor-cove-64-honored",
                "coast-mask", "maps/extracted-coast-land-water.png", sha256, "64", "64");
        assertEquals(0, declared.exitCode(), declared.error());
        assertTrue(declared.output().contains("constraintMaps: 1"), declared.output());
        // V2-18-10: the extracted map is the export's macro foundation input, which also needs the
        // declared per-medium base elevation before the owner coverage gate can pass.
        assertEquals(0, run("--data-dir", data, "v2", "request", "foundation-base-levels",
                "harbor-cove-64-honored", "54", "46").exitCode());

        Path requests = root.resolve("data").resolve("v2").resolve("requests");
        Path stored = requests.resolve("harbor-cove-64-honored.request-v2.json");
        Files.createDirectories(requests.resolve("maps"));
        Files.copy(promoteDir.resolve("land-water.png"),
                requests.resolve("maps").resolve("extracted-coast-land-water.png"));
        Result validated = run("v2", "request", "validate", stored.toString());
        assertEquals(0, validated.exitCode(), validated.error());

        // v2 export到達: the authored request carrying the extracted map reaches the production export.
        Result exported = run("v2", "export", stored.toString(), INTENT,
                root.resolve("exports").toString(), "extracted-export", "water", "54", "46");
        assertEquals(0, exported.exitCode(), exported.error());
        assertTrue(exported.output().contains("placementEligible: true"), exported.output());
    }

    @Test
    void heightGuideExtractionAndPromotionQuantiseAgainstTheStoredRequestBounds(@TempDir Path root)
            throws Exception {
        Path image = root.resolve("relief.png");
        writeHeightGuidePng(image, 16, 16);
        Path draftDir = root.resolve("draft");
        assertEquals(0, run("v2", "extract", "height-guide", image.toString(),
                draftDir.toString()).exitCode());

        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "relief").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "relief",
                "16", "16", "0", "255", "62").exitCode());
        Path request = root.resolve("data").resolve("v2").resolve("requests")
                .resolve("relief.request-v2.json");

        Result promoted = run("v2", "promote", "height-guide", draftDir.toString(),
                root.resolve("promoted").toString(), request.toString(), "1",
                "blocks-above-min-y", "1000000", "0");
        assertEquals(0, promoted.exitCode(), promoted.error());
        assertTrue(promoted.output().contains("mapPath: height-guide.png"), promoted.output());
        assertTrue(promoted.output().contains("valueMeaning: BLOCKS_ABOVE_REQUEST_MIN_Y"), promoted.output());
        assertTrue(Files.isRegularFile(root.resolve("promoted").resolve("height-guide.png")));
    }

    @Test
    void zoneLabelExtractionAndPromotionPublishACategoricalMap(@TempDir Path root) throws Exception {
        Path image = root.resolve("zones.png");
        writeZonePng(image, 16, 16);
        Path draftDir = root.resolve("draft");
        assertEquals(0, run("v2", "extract", "zone-label", image.toString(),
                draftDir.toString()).exitCode());

        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "zones").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "zones",
                "16", "16", "0", "128", "62").exitCode());
        Path request = root.resolve("data").resolve("v2").resolve("requests")
                .resolve("zones.request-v2.json");

        Result promoted = run("v2", "promote", "zone-label", draftDir.toString(),
                root.resolve("promoted").toString(), request.toString(), "1");
        assertEquals(0, promoted.exitCode(), promoted.error());
        assertTrue(promoted.output().contains("mapPath: zone-labels.png"), promoted.output());
        assertTrue(Files.isRegularFile(root.resolve("promoted").resolve("zone-labels.png")));
    }

    @Test
    void extractAndPromoteAreCliOnlyGrammarWithStableUsage() {
        // Missing arguments surface the stable V2_USAGE code with the canonical usage text.
        Result usage = run("v2", "extract", "land-water", "only-one-arg");
        assertEquals(2, usage.exitCode());
        assertTrue(usage.error().contains("V2_USAGE"), usage.error());
        assertTrue(usage.error().contains("<draft-output-dir>"), usage.error());

        // An unknown extract kind is an unknown-operation routing error, not a guess.
        Result unknown = run("v2", "extract", "coastline", "a.png", "b");
        assertEquals(2, unknown.exitCode());
        assertTrue(unknown.error().contains("V2_UNKNOWN_OPERATION"), unknown.error());
    }

    @Test
    void promoteRejectsAnUnknownHandlingWord(@TempDir Path root) throws Exception {
        Path image = root.resolve("coast.png");
        writeLandWaterPng(image, 8, 8);
        Path draftDir = root.resolve("draft");
        assertEquals(0, run("v2", "extract", "land-water", image.toString(),
                draftDir.toString()).exitCode());

        Result promoted = run("v2", "promote", "land-water", draftDir.toString(),
                root.resolve("promoted").toString(), "1", "maybe");
        assertEquals(1, promoted.exitCode());
        assertTrue(promoted.error().contains("unknown-handling"), promoted.error());
    }

    /**
     * Colour source image whose classification reproduces the honored fixture's macro land-water
     * composition (V2-18-10 fixture migration): since the surface owner gate is fail-closed, the
     * extracted map is now the export's foundation input, so it must carry the same composition the
     * sealed intent's coastal modifiers were authored against — an arbitrary half-and-half pattern
     * would contradict them and be rejected by the modifier compositor rather than by anything the
     * image-fidelity chain is proving.
     */
    private static void writeLandWaterPngFromMask(Path path, Path maskPng) throws IOException {
        BufferedImage mask = ImageIO.read(maskPng.toFile());
        BufferedImage image = new BufferedImage(
                mask.getWidth(), mask.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < mask.getHeight(); z++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                boolean land = mask.getRaster().getSample(x, z, 0) != 0;
                image.setRGB(x, z, land ? argb(255, 70, 140, 70) : argb(255, 10, 40, 220));
            }
        }
        writePng(image, path);
    }

    private static void writeLandWaterPng(Path path, int width, int length) throws IOException {
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                // Saturated, full-alpha colours the deterministic extractor classifies with confidence.
                int argb = x < width / 2 ? argb(255, 10, 40, 220) : argb(255, 70, 140, 70);
                image.setRGB(x, z, argb);
            }
        }
        writePng(image, path);
    }

    private static void writeHeightGuidePng(Path path, int width, int length) throws IOException {
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int grey = (x * 200) / Math.max(1, width - 1) + 10;
                image.setRGB(x, z, argb(255, grey, grey, grey));
            }
        }
        writePng(image, path);
    }

    private static void writeZonePng(Path path, int width, int length) throws IOException {
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                // Two clearly separated fixed-palette zones (shore sand vs upland rock-ish green).
                int argb = z < length / 2 ? argb(255, 194, 178, 128) : argb(255, 70, 140, 70);
                image.setRGB(x, z, argb);
            }
        }
        writePng(image, path);
    }

    /**
     * V2-19-04: source → binding → compile → consumer, entirely from the public command surface.
     *
     * <p>Before this task the chain broke twice: only one land/water source could be declared (each
     * declaration replacing the last), and the intent binding — whose canonical {@code artifactId}
     * embeds the declared input digest — had to be hand-written, because no verb produced one.</p>
     */
    @Test
    void constraintSourceAndIntentBindingCarryAnAuthoredRequestIntoTheExport(@TempDir Path root)
            throws Exception {
        Path image = root.resolve("coast.png");
        writeLandWaterPngFromMask(image,
                Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-land-water-u8.png"));
        Path draftDir = root.resolve("draft");
        assertEquals(0, run("v2", "extract", "land-water", image.toString(), draftDir.toString()).exitCode());
        Path promoteDir = root.resolve("promoted");
        Result promoted = run("v2", "promote", "land-water", draftDir.toString(),
                promoteDir.toString(), "1", "reject");
        assertEquals(0, promoted.exitCode(), promoted.error());

        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "harbor-cove-64-honored").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "harbor-cove-64-honored",
                "64", "64", "32", "72", "50").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "generation", "harbor-cove-64-honored",
                "827413", "64").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "foundation-base-levels",
                "harbor-cove-64-honored", "54", "46").exitCode());

        // 宣言: the promoted map is declared with its role and encoding read from the promotion record.
        Result declared = run("--data-dir", data, "v2", "request", "constraint-source",
                "harbor-cove-64-honored", "coast-mask", "land-water", promoteDir.toString(),
                "maps/extracted-coast-land-water.png");
        assertEquals(0, declared.exitCode(), declared.error());
        assertTrue(declared.output().contains("constraintMaps: 1"), declared.output());

        Path requests = root.resolve("data").resolve("v2").resolve("requests");
        Path stored = requests.resolve("harbor-cove-64-honored.request-v2.json");
        Files.createDirectories(requests.resolve("maps"));
        Files.copy(promoteDir.resolve("land-water.png"),
                requests.resolve("maps").resolve("extracted-coast-land-water.png"));

        // binding: the artifactId digest is computed from the declaration, never typed by the author.
        Path boundIntent = root.resolve("bound.terrain-intent-v2.json");
        Result bound = run("v2", "intent", "bind", stored.toString(), INTENT, boundIntent.toString(),
                "coast-mask", "land-water", "hard", "nearest", "0");
        assertEquals(0, bound.exitCode(), bound.error());
        String sha256 = valueOf(promoted.output(), "sha256");
        assertTrue(bound.output().contains("artifactId: constraint:land-water:sha256-" + sha256),
                bound.output());
        assertTrue(bound.output().contains("generatorConsumer: MACRO_FOUNDATION"), bound.output());

        // 確認: the read-only verb re-derives the same value and re-resolves the map bytes.
        Result verified = run("v2", "intent", "bindings", stored.toString(), boundIntent.toString());
        assertEquals(0, verified.exitCode(), verified.error());
        assertTrue(verified.output().contains("consistent: true"), verified.output());

        // consumer: the authored request and its bound intent reach the production export.
        Result exported = run("v2", "export", stored.toString(), boundIntent.toString(),
                root.resolve("exports").toString(), "bound-export", "water", "54", "46");
        assertEquals(0, exported.exitCode(), exported.error());
        assertTrue(exported.output().contains("placementEligible: true"), exported.output());
    }

    /**
     * V2-19-06: the same chain carrying a second map role through to a consumer. Before this task the
     * export required exactly one constraint map, so a request that declared an elevation guide could
     * be authored and verified but never exported.
     */
    @Test
    void aDeclaredHeightGuideReachesTheProductionExportFromTheCli(@TempDir Path root) throws Exception {
        String data = root.resolve("data").toString();
        Path requests = root.resolve("data").resolve("v2").resolve("requests");
        Path stored = requests.resolve("harbor-cove-64-honored.request-v2.json");
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "harbor-cove-64-honored").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "harbor-cove-64-honored",
                "64", "64", "32", "72", "50").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "generation", "harbor-cove-64-honored",
                "827413", "64").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "foundation-base-levels",
                "harbor-cove-64-honored", "54", "46").exitCode());

        Path maskImage = root.resolve("coast.png");
        writeLandWaterPngFromMask(maskImage,
                Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-land-water-u8.png"));
        assertEquals(0, run("v2", "extract", "land-water", maskImage.toString(),
                root.resolve("mask-draft").toString()).exitCode());
        assertEquals(0, run("v2", "promote", "land-water", root.resolve("mask-draft").toString(),
                root.resolve("mask-promoted").toString(), "1", "reject").exitCode());

        Path reliefImage = root.resolve("relief.png");
        writeHeightGuidePng(reliefImage, 64, 64);
        assertEquals(0, run("v2", "extract", "height-guide", reliefImage.toString(),
                root.resolve("relief-draft").toString()).exitCode());
        // 1/8-block per sample keeps the promoted guide's whole declared range inside the request's
        // 32..72 extent; the request record refuses a height encoding that could leave it.
        Result promotedGuide = run("v2", "promote", "height-guide", root.resolve("relief-draft").toString(),
                root.resolve("relief-promoted").toString(), stored.toString(), "1",
                "blocks-above-min-y", "125000", "0");
        assertEquals(0, promotedGuide.exitCode(), promotedGuide.error());

        assertEquals(0, run("--data-dir", data, "v2", "request", "constraint-source",
                "harbor-cove-64-honored", "coast-mask", "land-water",
                root.resolve("mask-promoted").toString(), "maps/coast-land-water.png").exitCode());
        Result declared = run("--data-dir", data, "v2", "request", "constraint-source",
                "harbor-cove-64-honored", "coast-height", "height-guide",
                root.resolve("relief-promoted").toString(), "maps/coast-height-guide.png");
        assertEquals(0, declared.exitCode(), declared.error());
        assertTrue(declared.output().contains("constraintMaps: 2"), declared.output());

        Files.createDirectories(requests.resolve("maps"));
        Files.copy(root.resolve("mask-promoted").resolve("land-water.png"),
                requests.resolve("maps").resolve("coast-land-water.png"));
        Files.copy(root.resolve("relief-promoted").resolve("height-guide.png"),
                requests.resolve("maps").resolve("coast-height-guide.png"));

        Path boundIntent = root.resolve("bound.terrain-intent-v2.json");
        assertEquals(0, run("v2", "intent", "bind", stored.toString(), INTENT, boundIntent.toString(),
                "coast-mask", "land-water", "hard", "nearest", "0").exitCode());
        Result boundGuide = run("v2", "intent", "bind", stored.toString(), boundIntent.toString(),
                boundIntent.toString(), "coast-height", "height-guide", "soft", "nearest", "2", "500000");
        assertEquals(0, boundGuide.exitCode(), boundGuide.error());
        // The guide now has a real generation-side consumer, which authoring reports honestly.
        assertTrue(boundGuide.output().contains("generatorConsumer: MACRO_FOUNDATION"), boundGuide.output());

        Result exported = run("v2", "export", stored.toString(), boundIntent.toString(),
                root.resolve("exports").toString(), "guided-export", "water", "54", "46");
        assertEquals(0, exported.exitCode(), exported.error());
        assertTrue(exported.output().contains("placementEligible: true"), exported.output());
    }

    @Test
    void intentBindingsFailsClosedWhenTheMapBytesNoLongerMatchTheDeclaration(@TempDir Path root)
            throws Exception {
        Path promoteDir = root.resolve("promoted");
        Path draftDir = root.resolve("draft");
        Path image = root.resolve("coast.png");
        writeLandWaterPng(image, 64, 64);
        assertEquals(0, run("v2", "extract", "land-water", image.toString(), draftDir.toString()).exitCode());
        assertEquals(0, run("v2", "promote", "land-water", draftDir.toString(),
                promoteDir.toString(), "1", "reject").exitCode());

        String data = root.resolve("data").toString();
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "harbor-cove-64-honored").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "harbor-cove-64-honored",
                "64", "64", "32", "72", "50").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "constraint-source",
                "harbor-cove-64-honored", "coast-mask", "land-water", promoteDir.toString(),
                "maps/coast.png").exitCode());
        Path requests = root.resolve("data").resolve("v2").resolve("requests");
        Path stored = requests.resolve("harbor-cove-64-honored.request-v2.json");
        Files.createDirectories(requests.resolve("maps"));
        Files.copy(promoteDir.resolve("land-water.png"), requests.resolve("maps").resolve("coast.png"));
        Path boundIntent = root.resolve("bound.terrain-intent-v2.json");
        assertEquals(0, run("v2", "intent", "bind", stored.toString(), INTENT, boundIntent.toString(),
                "coast-mask", "land-water", "hard", "nearest", "0").exitCode());

        // Swapping the declared map after binding must be caught before any export runs.
        writeLandWaterPng(requests.resolve("maps").resolve("coast.png"), 64, 64);
        Files.write(requests.resolve("maps").resolve("coast.png"),
                Files.readAllBytes(Path.of("examples/v2/diagnostic/maps/shore-2to1-400-land-water-u8.png")));

        Result verified = run("v2", "intent", "bindings", stored.toString(), boundIntent.toString());
        assertEquals(1, verified.exitCode(), verified.output());
        assertTrue(verified.error().contains("LFC-REQUEST-INVALID"), verified.error());
    }

    /**
     * V2-19-03: reference-image designs are reachable from the real CLI. Before this task the
     * orchestrator dropped the declared images and the provider request rejected the mismatch, so
     * this exact command failed for every request carrying an image.
     */
    @Test
    void designWithReferenceImagesPublishesAPackageFromTheCli(@TempDir Path root) throws Exception {
        Path workspace = copyObliqueMultiView(root);

        Result result = run("v2", "design", "fixture",
                workspace.resolve("request-v2.json").toString(),
                "terrain-intent-v2.json",
                root.resolve("designs").toString());

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("Published verified v2 design package"), result.output());
        assertTrue(result.output().contains("requestId: oblique-multi-view-64"), result.output());
        Path published = Path.of(valueOf(result.output(), "directory"));
        assertTrue(Files.isRegularFile(published.resolve("terrain-intent-v2.json")), result.output());
        assertTrue(Files.isRegularFile(published.resolve("audit-v2.json")), result.output());
        // The design package seals the intent only; images are AI cues, not published artifacts.
        assertFalse(Files.exists(published.resolve("references")), result.output());
    }

    /**
     * V2-19-08: the CLI design summary states what the production dispatch registry would do with the
     * designed intent, and the audit records it. This fixture declares {@code PLAIN} alone.
     *
     * <p>V2-19-09 (ADR 0040) changed the answer, not the reporting: the surface runtime no longer
     * requires the coastal four, so a {@code PLAIN}-only design is now {@code SELECTABLE} and carries
     * neither a missing-companion nor an unselectable finding. The lint contract, rule ids and
     * NON_GATING classification are unchanged.</p>
     */
    @Test
    void designSummaryAndAuditCarryTheReportOnlySupportLint(@TempDir Path root) throws Exception {
        Path workspace = copyObliqueMultiView(root);

        Result result = run("v2", "design", "fixture",
                workspace.resolve("request-v2.json").toString(),
                "terrain-intent-v2.json",
                root.resolve("designs").toString());

        assertEquals(0, result.exitCode(), result.error());
        assertEquals("design-support-lint-v1", valueOf(result.output(), "supportLintContract"));
        assertEquals("SELECTABLE", valueOf(result.output(), "dispatchDryRun"));
        assertEquals("[PLAIN]", valueOf(result.output(), "declaredKinds"));
        assertTrue(valueOf(result.output(), "selectablePipelines")
                .contains("v2.production.surface-2_5d.coastal"), result.output());
        assertTrue(valueOf(result.output(), "reachableKinds").contains("SANDY_BEACH"), result.output());
        assertFalse(result.output().contains("v2.design.route-companion-missing"), result.output());
        assertFalse(result.output().contains("v2.design.dispatch-unselectable"), result.output());

        String audit = Files.readString(
                Path.of(valueOf(result.output(), "directory")).resolve("audit-v2.json"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(audit.contains("\"supportLint\""), audit);
        assertTrue(audit.contains("\"design-support-lint-v1\""), audit);
        assertTrue(audit.contains("\"SELECTABLE\""), audit);
        assertFalse(audit.contains("\"GATING\""), "the lint records advisory findings only");
    }

    @Test
    void designFailsClosedWhenADeclaredReferenceImageIsMissing(@TempDir Path root) throws Exception {
        Path workspace = copyObliqueMultiView(root);
        Files.delete(workspace.resolve("references/cove-north.png"));

        Result result = run("v2", "design", "fixture",
                workspace.resolve("request-v2.json").toString(),
                "terrain-intent-v2.json",
                root.resolve("designs").toString());

        assertEquals(1, result.exitCode(), result.output());
        assertTrue(result.error().contains("LFC-REQUEST-INVALID"), result.error());
        assertTrue(result.error().contains("Design failed safely (INVALID_REQUEST)."), result.error());
        assertFalse(result.error().contains(root.toString()),
                "the safe message must not echo an absolute filesystem path");
        assertFalse(Files.exists(root.resolve("designs").resolve("oblique-multi-view-64")),
                "a rejected design must not leave a package behind");
    }

    private static Path copyObliqueMultiView(Path root) throws IOException {
        Path workspace = root.resolve("design-images");
        Files.createDirectories(workspace.resolve("references"));
        Files.copy(Path.of("examples/v2/diagnostic/oblique-multi-view.request-v2.json"),
                workspace.resolve("request-v2.json"));
        Files.copy(Path.of("examples/v2/diagnostic/oblique-multi-view.terrain-intent-v2.json"),
                workspace.resolve("terrain-intent-v2.json"));
        try (var images = Files.list(Path.of("examples/v2/diagnostic/references"))) {
            for (Path image : images.sorted().toList()) {
                Files.copy(image, workspace.resolve("references").resolve(image.getFileName().toString()));
            }
        }
        return workspace;
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("no PNG writer available for the extraction fixture");
        }
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** Reads one {@code key: value} line from the CLI's human-readable output. */
    private static String valueOf(String output, String key) {
        for (String line : output.split("\n")) {
            if (line.startsWith(key + ": ")) {
                return line.substring(key.length() + 2).trim();
            }
        }
        throw new AssertionError("no '" + key + "' line in CLI output:\n" + output);
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream output = new PrintStream(out, true, StandardCharsets.UTF_8);
             PrintStream error = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            exitCode = LandformCraftCli.run(args, output, error);
        }
        return new Result(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String output, String error) {
    }
}
