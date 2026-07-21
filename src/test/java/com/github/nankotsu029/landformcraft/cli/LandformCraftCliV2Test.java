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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-12-03 CLI surface for the official {@code lfc v2 <verb>} path. */
class LandformCraftCliV2Test {
    private static final String REQUEST = "examples/v2/diagnostic/harbor-cove-64.request-v2.json";
    private static final String INTENT = "examples/v2/diagnostic/harbor-cove-64.terrain-intent-v2.json";

    @Test
    void requestVerbStrictlyReadsAV2Request() {
        Result result = run("v2", "request", "info", REQUEST);

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("requestId: harbor-cove-64"), result.output());
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
    void anAuthoredRequestIsAcceptedByTheValidateAndExportVerbs(@TempDir Path root) {
        String data = root.resolve("data").toString();
        // The export path pairs a request with the intent whose intentId matches its requestId, so
        // authoring reproduces the sealed fixture request and exports it with the sealed intent.
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "harbor-cove-64").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "harbor-cove-64",
                "64", "64", "32", "72", "50").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "constraint-map", "harbor-cove-64",
                "coast-mask", "maps/harbor-cove-64-land-water-u8.png",
                "d".repeat(64), "64", "64").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "prompt", "harbor-cove-64",
                "A", "sheltered", "cove.").exitCode());
        Path stored = root.resolve("data").resolve("v2").resolve("requests")
                .resolve("harbor-cove-64.request-v2.json");
        assertTrue(Files.isRegularFile(stored));

        Result validated = run("v2", "request", "validate", stored.toString());
        assertEquals(0, validated.exitCode(), validated.error());
        assertTrue(validated.output().contains("requestId: harbor-cove-64"), validated.output());

        // F1 is only closed if an authored request reaches the production export path.
        Result exported = run("v2", "export", stored.toString(), INTENT,
                root.resolve("exports").toString(), "authored-export", "water", "54", "46");
        assertEquals(0, exported.exitCode(), exported.error());
        assertTrue(exported.output().contains("placementEligible: true"), exported.output());
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
        assertTrue(result.output().contains("requestId: harbor-cove-64"), result.output());
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
        writeLandWaterPng(image, 64, 64);
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
        assertEquals(0, run("--data-dir", data, "v2", "request", "create", "harbor-cove-64").exitCode());
        assertEquals(0, run("--data-dir", data, "v2", "request", "bounds", "harbor-cove-64",
                "64", "64", "32", "72", "50").exitCode());
        // The sealed intent binds to constraint-source:coast-mask, so the declared slug reuses it; the
        // export reads the source id and extracted digest for provenance and never rereads the file.
        Result declared = run("--data-dir", data, "v2", "request", "constraint-map", "harbor-cove-64",
                "coast-mask", "maps/extracted-coast-land-water.png", sha256, "64", "64");
        assertEquals(0, declared.exitCode(), declared.error());
        assertTrue(declared.output().contains("constraintMaps: 1"), declared.output());

        Path stored = root.resolve("data").resolve("v2").resolve("requests")
                .resolve("harbor-cove-64.request-v2.json");
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
