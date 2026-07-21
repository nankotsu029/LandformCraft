package com.github.nankotsu029.landformcraft.cli;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.CustomAssetService;
import com.github.nankotsu029.landformcraft.core.DoctorService;
import com.github.nankotsu029.landformcraft.core.LandformErrorCode;
import com.github.nankotsu029.landformcraft.core.LandformException;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.ExtractedHeightGuidePromotionOptionsV2;
import com.github.nankotsu029.landformcraft.core.v2.ExtractedMaskPromotionOptionsV2;
import com.github.nankotsu029.landformcraft.core.v2.ExtractedZoneLabelPromotionOptionsV2;
import com.github.nankotsu029.landformcraft.core.v2.ImageExtractionWorkflowServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandRouteV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandRouterV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandVerbV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2RequestStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.job.ExportJobServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.job.ExportJobStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2WorkflowServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.migration.LegacyRetirementPreflightV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationSourceKindV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobSnapshotV2;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/** Offline entry point for schema validation, deterministic generation, release export, and verification. */
public final class LandformCraftCli {
    public static final String VERSION = "0.9.0-beta.1";
    public static final int SCHEMA_VERSION = 2;
    private static final ThreadLocal<CliOptions> OPTIONS = new ThreadLocal<>();
    /** Cooperative cancellation tied to the CLI thread's interrupt flag (Ctrl-C / shutdown). */
    private static final CancellationToken INTERRUPTIBLE = () -> Thread.currentThread().isInterrupted();

    private LandformCraftCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(standardOutput, "standardOutput");
        Objects.requireNonNull(errorOutput, "errorOutput");
        final ParsedInvocation parsed;
        try {
            parsed = ParsedInvocation.parse(args);
        } catch (IllegalArgumentException exception) {
            reportFailure(errorOutput, "parse-options", exception, 2);
            return 2;
        }
        args = parsed.arguments();
        OPTIONS.set(parsed.options());
        try {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printHelp(standardOutput);
            return 0;
        }

        // Maintained, version-neutral verbs.
        if ("version".equals(args[0])) {
            return version(args, standardOutput, errorOutput);
        }
        if ("doctor".equals(args[0])) {
            return doctor(args, standardOutput, errorOutput);
        }
        if ("asset".equals(args[0])) {
            return asset(args, standardOutput, errorOutput);
        }
        if ("retirement-preflight".equals(args[0])) {
            return retirementPreflight(args, standardOutput, errorOutput);
        }
        // The explicit v2 root remains the stable version-qualified alias.
        if ("v2".equals(args[0])) {
            return v2(args, standardOutput, errorOutput);
        }
        // Default surface is v2 (ADR 0035 D5): `lfc <verb>` is an alias for `lfc v2 <verb>`.
        return v2(prependV2Root(args), standardOutput, errorOutput);
        } finally {
            OPTIONS.remove();
        }
    }

    /** Rewrites {@code <verb> …} to {@code v2 <verb> …} so the default surface routes through v2. */
    private static String[] prependV2Root(String[] args) {
        String[] rooted = new String[args.length + 1];
        rooted[0] = "v2";
        System.arraycopy(args, 0, rooted, 1, args.length);
        return rooted;
    }

    /** Official v2 command path; world-bound verbs are rejected with {@code V2_PAPER_ONLY}. */
    private static int v2(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        V2CommandRouteV2 route = V2CommandRouterV2.route(args, V2CommandVerbV2.Surface.CLI);
        if (!route.accepted()) {
            reportFailure(errorOutput, "v2",
                    new IllegalArgumentException(route.errorCode().orElseThrow().name()
                            + ": " + route.message() + " [v2CorrelationId=" + route.correlationId() + "]"),
                    2);
            return 2;
        }
        List<String> tokens = route.arguments();
        V2CommandVerbV2 verb = route.requireVerb();
        int parallelism = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        try (GenerationExecutors executors = GenerationExecutors.createDefault(parallelism)) {
            V2WorkflowServiceV2 workflow = new V2WorkflowServiceV2(executors, v2ProviderFactory(executors));
            switch (verb) {
                case REQUEST_VALIDATE, REQUEST_INFO -> {
                    Map<String, Object> inspected = new LinkedHashMap<>(
                            workflow.inspectRequest(Path.of(tokens.get(3))));
                    inspected.put("v2CorrelationId", route.correlationId());
                    emit(standardOutput, inspected);
                }
                case REQUEST_CREATE, REQUEST_BOUNDS, REQUEST_CONSTRAINT_MAP, REQUEST_PROMPT_INLINE, REQUEST_LIST ->
                        emit(standardOutput, authorRequest(verb, tokens, route));
                case JOB_STATUS, JOB_CANCEL, JOB_LIST, CANDIDATE_LIST, CANDIDATE_INFO ->
                        emit(standardOutput, inspectJobs(verb, tokens, route, executors));
                case JOURNAL_VERIFY, RECOVERY_INSPECT ->
                        emit(standardOutput, inspectPlacementState(verb, tokens, route));
                case DESIGN -> {
                    Path designsRoot = tokens.size() >= 6
                            ? Path.of(tokens.get(5)) : Path.of("build", "landformcraft-designs-v2");
                    var artifacts = joinWithInterruptCancellation(workflow.design(
                            tokens.get(2), Path.of(tokens.get(3)), tokens.get(4), designsRoot));
                    emit(standardOutput, Map.of(
                            "message", "Published verified v2 design package",
                            "v2CorrelationId", route.correlationId(),
                            "requestId", artifacts.audit().requestId(),
                            "provider", artifacts.audit().providerId(),
                            "intentChecksum", artifacts.audit().intentChecksum(),
                            "directory", artifacts.directory().toString()));
                }
                case GENERATE, EXPORT -> {
                    Path exportsRoot = Path.of(tokens.get(4));
                    Map<String, Object> summary = new LinkedHashMap<>(V2WorkflowServiceV2.summarize(
                            workflow.export(
                                    Path.of(tokens.get(2)),
                                    Path.of(tokens.get(3)),
                                    exportsRoot.resolve(".work-" + tokens.get(5)),
                                    exportsRoot,
                                    tokens.get(5),
                                    V2WorkflowServiceV2.baseline(
                                            tokens.get(6), tokens.get(7), tokens.get(8)),
                                    verb == V2CommandVerbV2.EXPORT)));
                    summary.put("v2CorrelationId", route.correlationId());
                    emit(standardOutput, summary);
                }
                case PREVIEW -> {
                    Map<String, Object> previews = new LinkedHashMap<>(
                            workflow.inspectPreviews(Path.of(tokens.get(2))));
                    previews.put("v2CorrelationId", route.correlationId());
                    emit(standardOutput, previews);
                }
                case EXTRACT_LAND_WATER, EXTRACT_HEIGHT_GUIDE, EXTRACT_ZONE_LABEL ->
                        emit(standardOutput, extract(verb, tokens, route));
                case PROMOTE_LAND_WATER, PROMOTE_HEIGHT_GUIDE, PROMOTE_ZONE_LABEL ->
                        emit(standardOutput, promote(verb, tokens, route));
                case MIGRATE_INSPECT, MIGRATE_APPLY -> {
                    Map<String, Object> summary = new LinkedHashMap<>(
                            LegacyMigrationApplicationServiceV2.summarize(
                                    new LegacyMigrationApplicationServiceV2(executors)
                                            .migrateNow(migrationRequest(verb, tokens))));
                    summary.put("v2CorrelationId", route.correlationId());
                    emit(standardOutput, summary);
                }
                default -> {
                    // Paper-only verbs are rejected by the router before reaching this switch.
                    throw new IllegalStateException("unroutable v2 verb: " + verb);
                }
            }
            return 0;
        } catch (IOException | CompletionException | IllegalArgumentException
                 | IllegalStateException | StructuredDataValidationException
                 | java.io.UncheckedIOException exception) {
            reportFailure(errorOutput, "v2", unwrap(exception), 1);
            return 1;
        }
    }

    /**
     * Read-only v2 placement-state verbs (V2-12-10): strict journal verification and recovery
     * inspection of an explicit artifact. These give the CLI the read-only visibility the v1
     * {@code journal-verify} and {@code recovery status|diagnose} verbs offered, without touching a
     * world. Nothing is written and no operation store is traversed.
     */
    private static Map<String, Object> inspectPlacementState(
            V2CommandVerbV2 verb,
            List<String> tokens,
            V2CommandRouteV2 route
    ) throws IOException {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("v2CorrelationId", route.correlationId());
        if (verb == V2CommandVerbV2.JOURNAL_VERIFY) {
            var journal = codec.readPlacementJournal(Path.of(tokens.get(2)));
            result.put("message", "Verified v2 placement journal");
            result.put("placementId", journal.plan().placementId().toString());
            result.put("state", journal.state().name());
            result.put("tiles", journal.tiles().size());
            result.put("snapshotBytesUsed", journal.snapshotBytesUsed());
            result.put("journalChecksum", journal.journalChecksum());
            return result;
        }
        String kind = tokens.get(3);
        Path path = Path.of(tokens.get(4));
        switch (kind) {
            case "journal" -> {
                var journal = codec.readPlacementJournal(path);
                result.put("kind", "journal");
                result.put("placementId", journal.plan().placementId().toString());
                result.put("state", journal.state().name());
                // RECOVERY_REQUIRED is the state the v1 `recovery status` surfaced.
                result.put("recoveryRequired",
                        journal.state().name().equals("RECOVERY_REQUIRED"));
            }
            case "plan" -> {
                var plan = codec.readPlacementRecoveryPlan(path);
                result.put("kind", "recovery-plan");
                result.put("placementId", plan.placementId().toString());
                result.put("classification", plan.classification().name());
                result.put("sourceJournalState", plan.sourceJournalState().name());
                result.put("confirmationAction", plan.confirmationAction().name());
            }
            default -> throw new IllegalArgumentException(
                    "recovery inspect kind must be 'journal' or 'plan', not '" + kind + "'");
        }
        return result;
    }

    /**
     * Read-mostly v2 job and candidate verbs (V2-12-09). The job store is durable, so the CLI can
     * report on jobs a Paper server queued. {@code cancel} records the terminal state; a worker
     * running in another process is reached only when the cancel is issued from that process, which
     * matches how the v1 CLI behaved.
     */
    private static Map<String, Object> inspectJobs(
            V2CommandVerbV2 verb,
            List<String> tokens,
            V2CommandRouteV2 route,
            GenerationExecutors executors
    ) throws IOException {
        ExportJobServiceV2 jobs = new ExportJobServiceV2(
                executors,
                new ExportJobStoreV2(options().dataDirectory().resolve("v2").resolve("jobs")),
                Clock.systemUTC());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("v2CorrelationId", route.correlationId());
        switch (verb) {
            case JOB_STATUS -> result.putAll(describeJob(jobs.status(tokens.get(3))));
            case JOB_CANCEL -> result.putAll(describeJob(jobs.cancel(tokens.get(3))));
            case CANDIDATE_INFO -> result.putAll(describeJob(jobs.status(tokens.get(3))));
            case JOB_LIST -> result.put("jobs", jobs.list().stream()
                    .map(snapshot -> snapshot.jobId() + " " + snapshot.state().name())
                    .toList());
            case CANDIDATE_LIST -> result.put("candidates", jobs.candidates(tokens.get(3)).stream()
                    .map(snapshot -> snapshot.jobId() + " " + snapshot.releaseId())
                    .toList());
            default -> throw new IllegalStateException("not a job verb: " + verb);
        }
        return result;
    }

    private static Map<String, Object> describeJob(ExportJobSnapshotV2 snapshot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("jobId", snapshot.jobId());
        values.put("requestId", snapshot.requestId());
        values.put("releaseId", snapshot.releaseId());
        values.put("kind", snapshot.kind().name());
        values.put("state", snapshot.state().name());
        values.put("progressMillionths", snapshot.progressMillionths());
        values.put("updatedAt", snapshot.updatedAt());
        values.put("message", snapshot.message());
        return values;
    }

    /**
     * Runs one v2 request authoring verb (V2-12-08). The store owns slug validation, strict schema
     * validation, and atomic publish; this method only maps routed tokens onto it and reports the
     * result, including the stored path so the operator can feed it straight to {@code v2 export}.
     */
    private static Map<String, Object> authorRequest(
            V2CommandVerbV2 verb,
            List<String> tokens,
            V2CommandRouteV2 route
    ) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(
                options().dataDirectory().resolve("v2").resolve("requests"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("v2CorrelationId", route.correlationId());
        if (verb == V2CommandVerbV2.REQUEST_LIST) {
            result.put("requests", store.list());
            return result;
        }
        String requestId = tokens.get(3);
        GenerationRequestV2 request = switch (verb) {
            case REQUEST_CREATE -> store.create(requestId);
            case REQUEST_BOUNDS -> store.bounds(requestId, integer(tokens.get(4)), integer(tokens.get(5)),
                    integer(tokens.get(6)), integer(tokens.get(7)), integer(tokens.get(8)));
            case REQUEST_CONSTRAINT_MAP -> store.constraintMap(requestId, tokens.get(4), tokens.get(5),
                    tokens.get(6), integer(tokens.get(7)), integer(tokens.get(8)));
            case REQUEST_PROMPT_INLINE -> store.prompt(requestId,
                    String.join(" ", tokens.subList(4, tokens.size())));
            default -> throw new IllegalStateException("not a request authoring verb: " + verb);
        };
        result.put("requestId", request.requestId());
        result.put("width", request.bounds().width());
        result.put("length", request.bounds().length());
        result.put("minY", request.bounds().minY());
        result.put("maxY", request.bounds().maxY());
        result.put("waterLevel", request.bounds().waterLevel());
        result.put("constraintMaps", request.constraintMaps().size());
        result.put("path", store.pathOf(request.requestId()).toString());
        return result;
    }

    /**
     * Runs one deterministic image-extraction verb (V2-14-01): the untrusted image is turned into a
     * sealed V2-7 draft bundle. The result reports the draft directory and provenance so the operator
     * can feed it straight to {@code v2 promote}. The extraction never calls AI and never guesses.
     */
    private static Map<String, Object> extract(
            V2CommandVerbV2 verb,
            List<String> tokens,
            V2CommandRouteV2 route
    ) throws IOException {
        ImageExtractionWorkflowServiceV2 workflow = new ImageExtractionWorkflowServiceV2();
        CancellationToken token = INTERRUPTIBLE;
        Path imageFile = Path.of(tokens.get(3));
        Path draftDirectory = Path.of(tokens.get(4));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("v2CorrelationId", route.correlationId());
        switch (verb) {
            case EXTRACT_LAND_WATER -> {
                var artifact = workflow.extractLandWater(imageFile, draftDirectory, token);
                result.put("kind", "land-water");
                result.put("role", "LAND_WATER_MASK");
                result.put("algorithmVersion", artifact.algorithmVersion());
                result.put("sourceChecksum", artifact.sourceChecksum());
                result.put("semanticChecksum", artifact.semanticChecksum());
                result.put("width", artifact.width());
                result.put("length", artifact.length());
                result.put("waterCells", artifact.waterCells());
                result.put("landCells", artifact.landCells());
                result.put("unknownCells", artifact.unknownCells());
            }
            case EXTRACT_HEIGHT_GUIDE -> {
                var artifact = workflow.extractHeightGuide(imageFile, draftDirectory, token);
                result.put("kind", "height-guide");
                result.put("role", "HEIGHT_GUIDE");
                result.put("algorithmVersion", artifact.algorithmVersion());
                result.put("sampleSpaceDeclaration", artifact.sampleSpaceDeclaration());
                result.put("sourceChecksum", artifact.sourceChecksum());
                result.put("semanticChecksum", artifact.semanticChecksum());
                result.put("width", artifact.width());
                result.put("length", artifact.length());
                result.put("validCells", artifact.validCells());
                result.put("noDataCells", artifact.noDataCells());
            }
            case EXTRACT_ZONE_LABEL -> {
                var artifact = workflow.extractZoneLabel(imageFile, draftDirectory, token);
                result.put("kind", "zone-label");
                result.put("role", "ZONE_LABEL_MAP");
                result.put("algorithmVersion", artifact.algorithmVersion());
                result.put("sampleSpaceDeclaration", artifact.sampleSpaceDeclaration());
                result.put("sourceChecksum", artifact.sourceChecksum());
                result.put("semanticChecksum", artifact.semanticChecksum());
                result.put("width", artifact.width());
                result.put("length", artifact.length());
                result.put("labeledCells", artifact.labeledCells());
                result.put("unknownCells", artifact.unknownCells());
            }
            default -> throw new IllegalStateException("not an extract verb: " + verb);
        }
        result.put("draftDirectory", draftDirectory.toAbsolutePath().normalize().toString());
        return result;
    }

    /**
     * Runs one explicit promotion verb (V2-14-01): a sealed draft becomes a V2-1 constraint map,
     * re-verified through the strict decoder before atomic publish. The result reports the published
     * map file, digest, and dimensions plus the exact {@code v2 request constraint-map} arguments, so
     * the extracted map can be declared as a request's constraint source and carried into
     * {@code v2 export}. Promotion always requires an explicit confidence threshold and handling; the
     * {@code EXPERIMENTAL} extraction path is never promoted implicitly.
     */
    private static Map<String, Object> promote(
            V2CommandVerbV2 verb,
            List<String> tokens,
            V2CommandRouteV2 route
    ) throws IOException {
        ImageExtractionWorkflowServiceV2 workflow = new ImageExtractionWorkflowServiceV2();
        CancellationToken token = INTERRUPTIBLE;
        Path draftDirectory = Path.of(tokens.get(3));
        Path outputDirectory = Path.of(tokens.get(4));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("v2CorrelationId", route.correlationId());
        switch (verb) {
            case PROMOTE_LAND_WATER -> {
                int threshold = integer(tokens.get(5));
                var options = landWaterPromotionOptions(threshold, tokens.get(6),
                        tokens.size() >= 8 ? tokens.get(7) : null);
                var record = workflow.promoteLandWater(draftDirectory, outputDirectory, options, token);
                result.put("kind", "land-water");
                result.put("role", record.role());
                result.put("mapPath", record.mapPath());
                result.put("mapFile", outputDirectory.resolve(record.mapPath())
                        .toAbsolutePath().normalize().toString());
                result.put("sha256", record.mapSha256());
                result.put("width", record.width());
                result.put("length", record.length());
                result.put("waterCells", record.waterCells());
                result.put("landCells", record.landCells());
                result.put("noDataCells", record.noDataCells());
                result.put("thresholdSuppressedCells", record.thresholdSuppressedCells());
                result.put("requestConstraintMapArgs", constraintMapArgs(
                        record.mapSha256(), record.width(), record.length()));
            }
            case PROMOTE_HEIGHT_GUIDE -> {
                GenerationRequestV2.Bounds bounds = boundsOf(Path.of(tokens.get(5)));
                int threshold = integer(tokens.get(6));
                var options = ExtractedHeightGuidePromotionOptionsV2.of(
                        threshold, heightValueMeaning(tokens.get(7)),
                        longValue(tokens.get(8)), longValue(tokens.get(9)));
                var record = workflow.promoteHeightGuide(
                        draftDirectory, outputDirectory, options, bounds, token);
                result.put("kind", "height-guide");
                result.put("role", record.role());
                result.put("mapPath", record.mapPath());
                result.put("mapFile", outputDirectory.resolve(record.mapPath())
                        .toAbsolutePath().normalize().toString());
                result.put("sha256", record.mapSha256());
                result.put("width", record.width());
                result.put("length", record.length());
                result.put("valueMeaning", record.valueMeaning());
                result.put("validCells", record.validCells());
                result.put("noDataCells", record.noDataCells());
                result.put("thresholdSuppressedCells", record.thresholdSuppressedCells());
            }
            case PROMOTE_ZONE_LABEL -> {
                GenerationRequestV2.Bounds bounds = boundsOf(Path.of(tokens.get(5)));
                int threshold = integer(tokens.get(6));
                int noData = tokens.size() >= 8 ? integer(tokens.get(7))
                        : ExtractedZoneLabelPromotionOptionsV2.DEFAULT_NODATA_SAMPLE;
                var options = new ExtractedZoneLabelPromotionOptionsV2(threshold, noData);
                var record = workflow.promoteZoneLabel(
                        draftDirectory, outputDirectory, options, bounds, token);
                result.put("kind", "zone-label");
                result.put("role", record.role());
                result.put("mapPath", record.mapPath());
                result.put("mapFile", outputDirectory.resolve(record.mapPath())
                        .toAbsolutePath().normalize().toString());
                result.put("sha256", record.mapSha256());
                result.put("width", record.width());
                result.put("length", record.length());
                result.put("labeledCells", record.labeledCells());
                result.put("noDataCells", record.noDataCells());
                result.put("thresholdSuppressedCells", record.thresholdSuppressedCells());
            }
            default -> throw new IllegalStateException("not a promote verb: " + verb);
        }
        return result;
    }

    private static ExtractedMaskPromotionOptionsV2 landWaterPromotionOptions(
            int confidenceThreshold, String handling, String noDataSample) {
        return switch (handling) {
            case "reject" -> ExtractedMaskPromotionOptionsV2.rejectBelow(confidenceThreshold);
            case "water" -> ExtractedMaskPromotionOptionsV2.mapUnknownToWater(confidenceThreshold);
            case "land" -> ExtractedMaskPromotionOptionsV2.mapUnknownToLand(confidenceThreshold);
            case "nodata" -> ExtractedMaskPromotionOptionsV2.mapUnknownToNoData(confidenceThreshold,
                    noDataSample == null
                            ? ExtractedMaskPromotionOptionsV2.DEFAULT_NODATA_SAMPLE : integer(noDataSample));
            default -> throw new IllegalArgumentException(
                    "unknown-handling must be one of reject, water, land, nodata; not '" + handling + "'");
        };
    }

    private static GenerationRequestV2.HeightValueMeaning heightValueMeaning(String value) {
        return switch (value) {
            case "absolute-block-y" -> GenerationRequestV2.HeightValueMeaning.ABSOLUTE_BLOCK_Y;
            case "blocks-above-min-y" -> GenerationRequestV2.HeightValueMeaning.BLOCKS_ABOVE_REQUEST_MIN_Y;
            case "blocks-relative-to-water" ->
                    GenerationRequestV2.HeightValueMeaning.BLOCKS_RELATIVE_TO_WATER_LEVEL;
            default -> throw new IllegalArgumentException(
                    "height value meaning must be one of absolute-block-y, blocks-above-min-y, "
                            + "blocks-relative-to-water; not '" + value + "'");
        };
    }

    /** Reads the bounds a height/zone promotion quantises against from a strict generation request. */
    private static GenerationRequestV2.Bounds boundsOf(Path request) throws IOException {
        return new LandformV2DataCodec().readGenerationRequest(request).bounds();
    }

    /** Suggested {@code v2 request constraint-map} arguments for the promoted land/water map. */
    private static List<String> constraintMapArgs(String sha256, int width, int length) {
        return List.of("<request-id>", "<source-slug>", "land-water.png",
                sha256, Integer.toString(width), Integer.toString(length));
    }

    private static long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected an integer: " + value, exception);
        }
    }

    /**
     * Builds one migration request from the routed tokens (V2-12-04). {@code inspect} is the dry run;
     * {@code apply} publishes and must state {@code strict} or {@code accept-lossy} explicitly, so a
     * lossy migration is never the default.
     */
    private static LegacyMigrationRequestV2 migrationRequest(V2CommandVerbV2 verb, List<String> tokens) {
        LegacyMigrationSourceKindV2 kind = migrationSourceKind(tokens.get(3));
        Path source = Path.of(tokens.get(4));
        if (verb == V2CommandVerbV2.MIGRATE_INSPECT) {
            return LegacyMigrationRequestV2.dryRun(kind, source);
        }
        boolean acceptLossy = switch (tokens.get(7)) {
            case "strict" -> false;
            case "accept-lossy" -> true;
            default -> throw new IllegalArgumentException(
                    "migration loss policy must be 'strict' or 'accept-lossy', not '" + tokens.get(7) + "'");
        };
        return new LegacyMigrationRequestV2(kind, source, java.util.Optional.of(Path.of(tokens.get(5))),
                tokens.get(6), false, acceptLossy);
    }

    private static LegacyMigrationSourceKindV2 migrationSourceKind(String value) {
        return switch (value) {
            case "intent" -> LegacyMigrationSourceKindV2.V1_TERRAIN_INTENT;
            case "design" -> LegacyMigrationSourceKindV2.V1_DESIGN_PACKAGE;
            case "release" -> LegacyMigrationSourceKindV2.V1_RELEASE;
            default -> throw new IllegalArgumentException(
                    "migration source kind must be one of intent, design, release; not '" + value + "'");
        };
    }

    private static com.github.nankotsu029.landformcraft.core.v2.design.TerrainDesignApplicationServiceV2
            .ProviderFactory v2ProviderFactory(GenerationExecutors executors) {
        var policy = com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy.defaults();
        var client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        return (path, modelOrIntentPath) -> switch (path) {
            case OPENAI -> new com.github.nankotsu029.landformcraft.ai.openai.OpenAiTerrainDesignProviderV2(
                    executors, requireEnvironment("OPENAI_API_KEY"), modelOrIntentPath,
                    com.github.nankotsu029.landformcraft.ai.openai.OpenAiTerrainDesignProviderV2.DEFAULT_ENDPOINT,
                    policy, Clock.systemUTC(), client);
            case ANTHROPIC ->
                    new com.github.nankotsu029.landformcraft.ai.anthropic.AnthropicTerrainDesignProviderV2(
                            executors, requireEnvironment("ANTHROPIC_API_KEY"), modelOrIntentPath,
                            com.github.nankotsu029.landformcraft.ai.anthropic
                                    .AnthropicTerrainDesignProviderV2.DEFAULT_ENDPOINT,
                            policy, Clock.systemUTC(), client);
            default -> throw new IllegalArgumentException(
                    "provider factory is only used by the HTTP design paths");
        };
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("required environment variable is not set: " + name);
        }
        return value;
    }

    private static int version(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 1) {
            return usage(err, "version", "Usage: version");
        }
        emit(out, Map.of(
                "product", "LandformCraft",
                "version", VERSION,
                "generatorVersion", DiagnosticBlueprintCompilerV2.GENERATOR_VERSION,
                "schemaVersion", SCHEMA_VERSION,
                "minecraftCompatibility", "1.21.11"
        ));
        return 0;
    }

    /** ADR 0035 D8 fail-closed inventory and byte-exact neutral archive command. */
    private static int retirementPreflight(String[] args, PrintStream out, PrintStream err) {
        try {
            LegacyRetirementPreflightV2 preflight = new LegacyRetirementPreflightV2();
            LegacyRetirementPreflightV2.Result result;
            Path report;
            if (args.length == 3 && "inspect".equals(args[1])) {
                report = Path.of(args[2]);
                result = preflight.inspect(options().dataDirectory());
            } else if (args.length == 5 && "archive".equals(args[1])) {
                report = Path.of(args[4]);
                result = preflight.archive(options().dataDirectory(), Path.of(args[2]), args[3]);
            } else {
                return usage(err, "retirement-preflight",
                        "Usage: retirement-preflight inspect <report.json> | "
                                + "retirement-preflight archive <archive-root> <archive-id> <report.json>");
            }
            preflight.writeReport(report, result);
            emit(out, Map.of(
                    "status", result.status(),
                    "resolution", result.resolution(),
                    "entryCount", result.entryCount(),
                    "totalBytes", result.totalBytes(),
                    "unresolvedCount", result.unresolvedCount(),
                    "report", report.toAbsolutePath().normalize().toString()));
            return result.unresolvedCount() == 0 ? 0 : 3;
        } catch (IOException | IllegalArgumentException exception) {
            reportFailure(err, "retirement-preflight", exception, 1);
            return 1;
        }
    }

    private static int doctor(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 1) {
            return usage(err, "doctor", "Usage: doctor");
        }
        try {
            var report = new DoctorService().inspect(options().dataDirectory(), "CLI");
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("javaVersion", report.javaVersion());
            value.put("runtime", report.runtime());
            value.put("dataDirectory", report.dataDirectory());
            value.put("writable", report.writable());
            value.put("atomicMove", report.atomicMove());
            value.put("usableBytes", report.usableBytes());
            value.put("openAiKeyPresent", report.openAiKeyPresent());
            value.put("anthropicKeyPresent", report.anthropicKeyPresent());
            value.put("runningJobs", report.runningJobs());
            value.put("recoveryRequiredPlacements", report.recoveryRequiredPlacements());
            value.put("warnings", report.warnings());
            emit(out, value);
            return report.writable() && report.atomicMove() && report.usableBytes() > 0L ? 0 : 1;
        } catch (RuntimeException | IOException exception) {
            reportFailure(err, "doctor", exception, 1);
            return 1;
        }
    }

    private static int asset(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            return usage(err, "asset", "Usage: asset <validate|import|list|info|remove> ...");
        }
        try {
            Path data = options().dataDirectory();
            CustomAssetService service = new CustomAssetService(
                    data.resolve("imports"), data.resolve("assets"), data.resolve("exports"), Clock.systemUTC());
            Object result;
            switch (args[1]) {
                case "validate", "import" -> {
                    if (args.length != 4) {
                        return usage(err, "asset-" + args[1],
                                "Usage: asset " + args[1] + " <relative-schematic> <relative-metadata>");
                    }
                    result = args[1].equals("validate")
                            ? service.validate(args[2], args[3]) : service.importAsset(args[2], args[3]);
                }
                case "list" -> {
                    if (args.length != 2) {
                        return usage(err, "asset-list", "Usage: asset list");
                    }
                    result = service.list();
                }
                case "info" -> {
                    if (args.length != 3) {
                        return usage(err, "asset-info", "Usage: asset info <asset-id>");
                    }
                    result = service.info(args[2]);
                }
                case "remove" -> {
                    if (args.length != 3) {
                        return usage(err, "asset-remove", "Usage: asset remove <asset-id>");
                    }
                    service.remove(args[2]);
                    result = Map.of("removed", args[2]);
                }
                default -> {
                    return usage(err, "asset", "Unknown asset subcommand: " + args[1]);
                }
            }
            emit(out, result);
            return 0;
        } catch (RuntimeException | IOException exception) {
            reportFailure(err, "asset-" + args[1], unwrap(exception), 1);
            return 1;
        }
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected an integer: " + value, exception);
        }
    }

    private static int usage(PrintStream error, String operation, String message) {
        reportFailure(error, operation, new IllegalArgumentException(message), 2);
        return 2;
    }

    private static void emit(PrintStream output, Object value) {
        CliOptions options = options();
        if (options.quiet()) {
            return;
        }
        try {
            if (options.json()) {
                output.println(new LandformDataCodec().writeJsonString(value));
            } else if (value instanceof Map<?, ?> map) {
                map.forEach((key, item) -> output.println(key + ": " + item));
            } else {
                output.println(value);
            }
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static void reportFailure(PrintStream error, String operation, Throwable throwable, int exitCode) {
        Throwable actual = unwrap(throwable);
        LandformException failure;
        if (actual instanceof LandformException domain) {
            failure = domain;
        } else {
            LandformErrorCode code;
            if (actual instanceof com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignException provider) {
                code = provider.code() == com.github.nankotsu029.landformcraft.ai.spi.ProviderFailureCode.TIMEOUT
                        ? LandformErrorCode.PROVIDER_TIMEOUT : LandformErrorCode.PROVIDER_FAILED;
            } else if (actual instanceof com.github.nankotsu029.landformcraft.validation.ImageInputException) {
                code = LandformErrorCode.IMAGE_UNSAFE;
            } else if (operation.equals("verify")) {
                code = LandformErrorCode.RELEASE_TAMPERED;
            } else if (actual instanceof IllegalArgumentException
                    || actual instanceof StructuredDataValidationException
                    || actual instanceof IOException) {
                code = LandformErrorCode.REQUEST_INVALID;
            } else {
                code = LandformErrorCode.INTERNAL;
            }
            String safe = actual instanceof IllegalArgumentException && actual.getMessage() != null
                    ? actual.getMessage() : switch (operation) {
                        case "journal-verify" -> "Journal verification failed safely.";
                        case "design-verify" -> "Design verification failed safely.";
                        case "verify" -> "Release verification failed safely.";
                        case "design" -> "Design failed safely.";
                        case "generate" -> "Generation failed safely.";
                        case "export" -> "Export failed safely.";
                        default -> "Operation failed safely.";
                    };
            failure = new LandformException(code, safe, operation, "", "cli",
                    "Run with --verbose and inspect the administrator log.", actual);
        }
        CliOptions options = OPTIONS.get();
        if (options != null && options.json()) {
            try {
                error.println(new LandformDataCodec().writeJsonString(Map.of(
                        "errorCode", failure.code().code(),
                        "safeMessage", failure.getMessage(),
                        "correlationId", failure.correlationId().toString(),
                        "operation", failure.operation(),
                        "resourceId", failure.resourceId(),
                        "stage", failure.stage(),
                        "suggestedAction", failure.suggestedAction(),
                        "exitCode", exitCode)));
            } catch (IOException exception) {
                error.println(failure.code().code() + " correlationId=" + failure.correlationId());
            }
        } else {
            error.println(failure.code().code() + ": " + failure.getMessage());
            error.println("correlationId: " + failure.correlationId());
            error.println("operation: " + failure.operation() + "; stage: " + failure.stage());
            error.println("suggestedAction: " + failure.suggestedAction());
        }
        if (options != null && options.verbose()) {
            actual.printStackTrace(error);
        }
    }

    private static CliOptions options() {
        CliOptions value = OPTIONS.get();
        return value == null
                ? new CliOptions(Path.of(".landformcraft"), false, false, false) : value;
    }

    private record CliOptions(
            Path dataDirectory, boolean json, boolean quiet, boolean verbose) {
        private CliOptions {
            dataDirectory = dataDirectory.toAbsolutePath().normalize();
        }
    }

    private record ParsedInvocation(String[] arguments, CliOptions options) {
        static ParsedInvocation parse(String[] raw) {
            List<String> positional = new ArrayList<>();
            Path data = Path.of(".landformcraft");
            boolean json = false;
            boolean quiet = false;
            boolean verbose = false;
            for (int index = 0; index < raw.length; index++) {
                switch (raw[index]) {
                    case "--data-dir" -> {
                        if (++index >= raw.length) {
                            throw new IllegalArgumentException("--data-dir requires a directory");
                        }
                        data = Path.of(raw[index]);
                    }
                    case "--json" -> json = true;
                    case "--quiet" -> quiet = true;
                    case "--verbose" -> verbose = true;
                    default -> positional.add(raw[index]);
                }
            }
            if (json && quiet) {
                throw new IllegalArgumentException("--json and --quiet cannot be combined");
            }
            return new ParsedInvocation(positional.toArray(String[]::new),
                    new CliOptions(data, json, quiet, verbose));
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.io.UncheckedIOException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> T joinWithInterruptCancellation(java.util.concurrent.CompletableFuture<T> future) {
        Thread hook = Thread.ofPlatform().unstarted(() -> future.cancel(true));
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(hook);
        try {
            return future.join();
        } finally {
            try {
                runtime.removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown already started; the hook owns cancellation.
            }
        }
    }

    private static void printHelp(PrintStream output) {
        output.println("LandformCraft CLI " + VERSION);
        output.println("Usage:");
        output.println("  landformcraft [共通オプション] <コマンド>");
        output.println();
        output.println("共通オプション:");
        output.println("  --data-dir <directory>  管理データの保存先");
        output.println("  --json                  機械可読なJSONで出力");
        output.println("  --quiet                 正常出力を抑制");
        output.println("  --verbose               失敗時にstack traceを表示");
        output.println();
        output.println("既定はv2です（ADR 0035 D5）。<verb> は 'v2 <verb>' と同じで、'v2 <verb>' も明示形として"
                + "使えます。");
        output.println();
        output.println("設計・生成:");
        helpLine(output, "request validate|info <generation-request-v2.json>", "v2 requestを厳密に検証");
        helpLine(output, "request create <request-id>", "既定boundsのv2 requestを作成（--data-dir配下）");
        helpLine(output, "request bounds <request-id> <width> <length> <min-y> <max-y> <water-level>",
                "v2 requestのboundsを更新");
        helpLine(output, "request constraint-map <request-id> <source-slug> <file> <sha256> "
                + "<width> <length>", "land/water constraint map sourceを宣言（surface-2_5d exportに必須）");
        helpLine(output, "request prompt <request-id> <prompt...>", "v2 requestのpromptを更新");
        helpLine(output, "request list", "保存済みv2 requestを列挙");
        helpLine(output, "design <import|fixture|openai|anthropic> <request-v2.json> <intent-or-model> "
                + "[designs-root]", "v2 Design Packageを作成");
        helpLine(output, "generate <request-v2.json> <terrain-intent-v2.json> <exports-root> <release-id> "
                + "<land|water> <land-surface-y> <water-bed-y>", "Release 2 directoryをZIPなしで公開");
        helpLine(output, "export <同上>", "Release 2 directory＋ZIPを公開しplacement適格性まで検証");
        output.println();
        output.println("Release・確認:");
        helpLine(output, "preview <release-directory-or-zip>", "検証済みReleaseのpreview indexを表示");
        helpLine(output, "job status|cancel <job-id> | job list", "非同期v2 export jobを確認・取消");
        helpLine(output, "candidate list <request-id> | candidate info <job-id>",
                "request単位の公開済みexport（v2 candidate）を確認");
        helpLine(output, "journal-verify <placement-journal-v2.json>", "v2 placement journalを厳密に検証");
        helpLine(output, "recovery inspect <journal|plan> <artifact.json>",
                "v2 placement journal／recovery planをread-onlyで確認");
        output.println();
        output.println("画像抽出（V2-7経路。決定論的・AI非依存・EXPERIMENTAL）:");
        helpLine(output, "extract <land-water|height-guide|zone-label> <image-file> <draft-output-dir>",
                "通常画像／スケッチからdraft bundleを抽出（secure envelope経由）");
        helpLine(output, "promote land-water <draft-dir> <output-dir> <confidence-threshold> "
                + "<reject|water|land|nodata> [nodata-sample]",
                "draftを明示昇格しland-water constraint mapを公開");
        helpLine(output, "promote height-guide <draft-dir> <output-dir> <request-v2.json> "
                + "<confidence-threshold> <absolute-block-y|blocks-above-min-y|blocks-relative-to-water> "
                + "<scale-millionths> <offset-millionths>",
                "draftを明示昇格しheight-guide constraint mapを公開");
        helpLine(output, "promote zone-label <draft-dir> <output-dir> <request-v2.json> "
                + "<confidence-threshold> [nodata-sample]",
                "draftを明示昇格しzone-label constraint mapを公開");
        output.println("      昇格後は 'request constraint-map' でsourceを宣言し 'v2 export' へ渡します。"
                + "暗黙昇格は行いません。");
        output.println();
        output.println("移行:");
        helpLine(output, "migrate inspect <intent|design|release> <v1-source>",
                "v1資産のv2変換を試算（dry-run。何も書き込まない）");
        helpLine(output, "migrate apply <intent|design|release> <v1-source> <output-root> <migration-id> "
                + "<strict|accept-lossy>", "v1資産をv2 design package＋変換reportへ変換");
        output.println("      place／undo／status／recover は world を伴うため Paper 専用です"
                + "（/lfc <verb>）。migrate／extract／promote は operator workstation の path を読むため "
                + "CLI 専用です。");
        output.println();
        output.println("管理（version中立）:");
        helpLine(output, "asset <validate|import|list|info|remove> ...", "custom assetを管理");
        helpLine(output, "version", "version情報を表示");
        helpLine(output, "doctor", "実行環境を診断");
        helpLine(output, "retirement-preflight inspect <report.json>",
                "legacy operational stateをfail-closedで棚卸し");
        helpLine(output, "retirement-preflight archive <archive-root> <archive-id> <report.json>",
                "legacy stateをbyte-exactなneutral archiveへ非破壊保存");
        output.println();
        output.println("例:");
        output.println("  ./gradlew run --args=\"request validate "
                + "examples/v2/diagnostic/harbor-cove-64.request-v2.json\"");
        output.println("  ./gradlew run --args=\"v2 migrate inspect release build/release\"");
        output.println("  ./gradlew run --args=\"doctor --data-dir build/data --json\"");
    }

    private static void helpLine(PrintStream output, String command, String description) {
        output.println("  " + command);
        output.println("      " + description);
    }
}
