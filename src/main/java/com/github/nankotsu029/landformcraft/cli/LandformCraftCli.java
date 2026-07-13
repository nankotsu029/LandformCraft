package com.github.nankotsu029.landformcraft.cli;

import com.github.nankotsu029.landformcraft.ai.anthropic.AnthropicTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.openai.OpenAiTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.FixtureTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.ImportedJsonTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignProvider;
import com.github.nankotsu029.landformcraft.core.GenerationApplicationService;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.GenerationOutcome;
import com.github.nankotsu029.landformcraft.core.FileGenerationJobRepository;
import com.github.nankotsu029.landformcraft.core.FilePlacementJournalRepository;
import com.github.nankotsu029.landformcraft.core.ReleaseApplicationService;
import com.github.nankotsu029.landformcraft.core.TerrainDesignApplicationService;
import com.github.nankotsu029.landformcraft.core.CustomAssetService;
import com.github.nankotsu029.landformcraft.core.DoctorService;
import com.github.nankotsu029.landformcraft.core.LandformErrorCode;
import com.github.nankotsu029.landformcraft.core.LandformException;
import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GenerationOptions;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.OutputOptions;
import com.github.nankotsu029.landformcraft.model.PlacementState;
import com.github.nankotsu029.landformcraft.format.DesignArtifactVerifier;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.ReleaseVerifier;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/** Offline entry point for schema validation, deterministic generation, release export, and verification. */
public final class LandformCraftCli {
    public static final String VERSION = "0.9.0-beta.1";
    public static final int SCHEMA_VERSION = 1;
    private static final ThreadLocal<CliOptions> OPTIONS = new ThreadLocal<>();

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

        if ("version".equals(args[0])) {
            return version(args, standardOutput, errorOutput);
        }
        if ("doctor".equals(args[0])) {
            return doctor(args, standardOutput, errorOutput);
        }
        if ("job".equals(args[0])) {
            return job(args, standardOutput, errorOutput);
        }
        if ("request".equals(args[0])) {
            return request(args, standardOutput, errorOutput);
        }
        if ("candidate".equals(args[0])) {
            return candidate(args, standardOutput, errorOutput);
        }
        if ("asset".equals(args[0])) {
            return asset(args, standardOutput, errorOutput);
        }
        if ("recovery".equals(args[0])) {
            return recovery(args, standardOutput, errorOutput);
        }

        if ("validate".equals(args[0])) {
            return validate(args, standardOutput, errorOutput);
        }
        if ("generate".equals(args[0]) || "preview".equals(args[0])) {
            return generate(args, standardOutput, errorOutput);
        }
        if ("export".equals(args[0])) {
            return exportRelease(args, standardOutput, errorOutput);
        }
        if ("verify".equals(args[0])) {
            return verify(args, standardOutput, errorOutput);
        }
        if ("journal-verify".equals(args[0])) {
            return verifyJournal(args, standardOutput, errorOutput);
        }
        if ("design".equals(args[0])) {
            return design(args, standardOutput, errorOutput);
        }
        if ("design-verify".equals(args[0])) {
            return verifyDesign(args, standardOutput, errorOutput);
        }

        reportFailure(errorOutput, "dispatch", new IllegalArgumentException(
                "Unknown command: " + args[0] + ". Run with --help for usage."), 2);
        return 2;
        } finally {
            OPTIONS.remove();
        }
    }

    private static int generate(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        if (args.length < 3 || args.length > 5) {
            return usage(errorOutput, "generate",
                    "Usage: generate <request.yml> <terrain-intent.json> [output-directory] [candidate-index]");
        }
        Path request = Path.of(args[1]);
        Path intent = Path.of(args[2]);
        Path output = args.length >= 4 ? Path.of(args[3]) : Path.of("build", "landformcraft-preview");
        int candidateIndex;
        try {
            candidateIndex = args.length == 5 ? Integer.parseInt(args[4]) : 0;
        } catch (NumberFormatException exception) {
            return usage(errorOutput, "generate", "candidate-index must be an integer");
        }

        int parallelism = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        try (GenerationExecutors executors = GenerationExecutors.createDefault(parallelism)) {
            GenerationApplicationService service = new GenerationApplicationService(executors);
            GenerationOutcome outcome = joinWithInterruptCancellation(
                    service.generatePreview(request, intent, output, candidateIndex));
            emit(standardOutput, Map.of(
                    "message", "Generated deterministic terrain preview",
                    "checksum", outcome.terrainPlan().checksum(),
                    "tiles", outcome.terrainPlan().tiles().size(),
                    "structures", outcome.terrainPlan().structures().size(),
                    "generationMillis", outcome.metrics().generationMillis(),
                    "estimatedPeakBytes", outcome.metrics().estimatedPeakWorkingBytes(),
                    "valid", outcome.validation().isValid(),
                    "output", outcome.outputDirectory().toString()));
            return outcome.validation().isValid() ? 0 : 1;
        } catch (CompletionException | IllegalArgumentException | java.io.UncheckedIOException exception) {
            Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause() : exception;
            reportFailure(errorOutput, "generate", cause, 1);
            return 1;
        }
    }

    private static int validate(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        if (args.length != 3) {
            return usage(errorOutput, "validate", "Usage: validate <request.yml> <terrain-intent.json>");
        }
        try {
            LandformDataCodec codec = new LandformDataCodec();
            var request = codec.readGenerationRequest(Path.of(args[1]));
            codec.readTerrainIntent(Path.of(args[2]));
            emit(standardOutput, Map.of("message", "Valid input contracts", "requestId", request.requestId()));
            return 0;
        } catch (IOException | IllegalArgumentException | StructuredDataValidationException exception) {
            reportFailure(errorOutput, "validate", exception, 1);
            return 1;
        }
    }

    private static int exportRelease(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        if (args.length < 3 || args.length > 5) {
            return usage(errorOutput, "export",
                    "Usage: export <request.yml> <terrain-intent.json> [exports-root] [candidate-index]");
        }
        Path exportsRoot = args.length >= 4 ? Path.of(args[3]) : Path.of("build", "landformcraft-exports");
        int candidateIndex;
        try {
            candidateIndex = args.length == 5 ? Integer.parseInt(args[4]) : 0;
        } catch (NumberFormatException exception) {
            return usage(errorOutput, "export", "candidate-index must be an integer");
        }
        int parallelism = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        try (GenerationExecutors executors = GenerationExecutors.createDefault(parallelism)) {
            var artifacts = joinWithInterruptCancellation(new ReleaseApplicationService(executors)
                    .export(Path.of(args[1]), Path.of(args[2]), exportsRoot, candidateIndex));
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("message", "Published verified release");
            result.put("releaseId", artifacts.releaseId());
            result.put("directory", artifacts.releaseDirectory().toString());
            artifacts.zip().ifPresent(path -> result.put("zip", path.toString()));
            artifacts.zipChecksum().ifPresent(path -> result.put("zipChecksum", path.toString()));
            emit(standardOutput, result);
            return 0;
        } catch (CompletionException | IllegalArgumentException exception) {
            Throwable cause = unwrap(exception);
            reportFailure(errorOutput, "export", cause, 1);
            return 1;
        }
    }

    private static int verify(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        if (args.length != 2) {
            return usage(errorOutput, "verify", "Usage: verify <release-directory-or-zip>");
        }
        try {
            var verification = new ReleaseVerifier().verify(Path.of(args[1]));
            emit(standardOutput, Map.of("message", "Verified release",
                    "requestId", verification.manifest().requestId(),
                    "files", verification.verifiedFiles(), "tiles", verification.verifiedTiles()));
            return 0;
        } catch (IOException | IllegalArgumentException | StructuredDataValidationException exception) {
            reportFailure(errorOutput, "verify", exception, 1);
            return 1;
        }
    }

    private static int verifyJournal(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        if (args.length != 2) {
            return usage(errorOutput, "journal-verify", "Usage: journal-verify <placement-journal.json>");
        }
        try {
            var journal = new LandformDataCodec().readPlacementJournal(Path.of(args[1]));
            emit(standardOutput, Map.of("message", "Verified placement journal",
                    "placementId", journal.plan().placementId().toString(),
                    "state", journal.state().name(), "tiles", journal.tiles().size()));
            return 0;
        } catch (IOException | IllegalArgumentException | StructuredDataValidationException exception) {
            reportFailure(errorOutput, "journal-verify", exception, 1);
            return 1;
        }
    }

    private static int design(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        if (args.length < 4 || args.length > 6) {
            return usage(errorOutput, "design", "Usage: design <import|fixture|openai|anthropic> <request.yml> "
                    + "<intent.json|model> [designs-root] [jobs-root]");
        }
        String providerName = args[1];
        Path requestPath = Path.of(args[2]);
        Path designsRoot = args.length >= 5 ? Path.of(args[4]) : Path.of("build", "landformcraft-designs");
        Path jobsRoot = args.length >= 6 ? Path.of(args[5]) : Path.of("build", "landformcraft-jobs");
        int parallelism = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        try (GenerationExecutors executors = GenerationExecutors.createDefault(parallelism)) {
            TerrainDesignProvider provider = createProvider(providerName, args[3], executors);
            try (provider) {
                var repository = new FileGenerationJobRepository(jobsRoot, executors);
                var handle = new TerrainDesignApplicationService(executors, provider, repository)
                        .start(requestPath, designsRoot);
                if (!options().quiet() && !options().json()) {
                    standardOutput.println("Design job: " + handle.jobId());
                }
                var artifacts = joinWithInterruptCancellation(handle.completion());
                emit(standardOutput, Map.of(
                        "message", "Published verified design",
                        "jobId", handle.jobId().toString(), "requestId", artifacts.audit().requestId(),
                        "provider", artifacts.audit().providerId(), "model", artifacts.audit().modelId(),
                        "usageTokens", artifacts.audit().usage().totalTokens(),
                        "images", artifacts.imageEvidence().images().size(),
                        "imageChecks", artifacts.imageEvidence().consistencyChecks().size(),
                        "directory", artifacts.directory().toString()));
                return 0;
            }
        } catch (CompletionException | IllegalArgumentException | java.io.UncheckedIOException exception) {
            Throwable cause = unwrap(exception);
            reportFailure(errorOutput, "design", cause, 1);
            return 1;
        }
    }

    private static TerrainDesignProvider createProvider(
            String providerName,
            String sourceOrModel,
            GenerationExecutors executors
    ) {
        return switch (providerName) {
            case "import" -> new ImportedJsonTerrainDesignProvider(executors, Path.of(sourceOrModel));
            case "fixture" -> {
                try {
                    yield new FixtureTerrainDesignProvider(
                            new LandformDataCodec().readTerrainIntent(Path.of(sourceOrModel))
                    );
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            }
            case "openai" -> new OpenAiTerrainDesignProvider(
                    executors, requireEnvironment("OPENAI_API_KEY"), sourceOrModel
            );
            case "anthropic" -> new AnthropicTerrainDesignProvider(
                    executors, requireEnvironment("ANTHROPIC_API_KEY"), sourceOrModel
            );
            default -> throw new IllegalArgumentException("unknown design provider: " + providerName);
        };
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("required environment variable is not set: " + name);
        }
        return value;
    }

    private static int verifyDesign(String[] args, PrintStream standardOutput, PrintStream errorOutput) {
        if (args.length != 2) {
            return usage(errorOutput, "design-verify", "Usage: design-verify <design-directory>");
        }
        try {
            var verification = new DesignArtifactVerifier().verify(Path.of(args[1]));
            emit(standardOutput, Map.of("message", "Verified design",
                    "requestId", verification.audit().requestId(),
                    "provider", verification.audit().providerId(), "files", verification.verifiedFiles(),
                    "images", verification.imageEvidence().images().size(),
                    "imageChecks", verification.imageEvidence().consistencyChecks().size()));
            return 0;
        } catch (IOException | IllegalArgumentException | StructuredDataValidationException exception) {
            reportFailure(errorOutput, "design-verify", exception, 1);
            return 1;
        }
    }

    private static int version(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 1) {
            return usage(err, "version", "Usage: version");
        }
        emit(out, Map.of(
                "product", "LandformCraft",
                "version", VERSION,
                "generatorVersion", BlueprintCompiler.GENERATOR_VERSION,
                "schemaVersion", SCHEMA_VERSION,
                "minecraftCompatibility", "1.21.11"
        ));
        return 0;
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

    private static int job(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 3 || !(args[1].equals("status") || args[1].equals("cancel"))) {
            return usage(err, "job", "Usage: job <status|cancel> <job-id>");
        }
        try (GenerationExecutors executors = GenerationExecutors.createDefault(1)) {
            FileGenerationJobRepository jobs = new FileGenerationJobRepository(
                    options().dataDirectory().resolve("jobs"), executors);
            var snapshot = args[1].equals("cancel")
                    ? jobs.cancel(args[2], Clock.systemUTC()).join()
                    : jobs.find(args[2]).join().orElseThrow(() -> new LandformException(
                            LandformErrorCode.NOT_FOUND, "Job was not found.", "job-status", args[2],
                            "load-job", "Check the job ID."));
            emit(out, Map.of(
                    "jobId", snapshot.jobId().toString(), "requestId", snapshot.requestId(),
                    "stage", snapshot.stage().name(), "progress", snapshot.progress(),
                    "updatedAt", snapshot.updatedAt().toString(), "message", snapshot.message()));
            return 0;
        } catch (RuntimeException exception) {
            reportFailure(err, "job-" + args[1], unwrap(exception), 1);
            return 1;
        }
    }

    private static int request(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            return usage(err, "request", "Usage: request <create|bounds|prompt|validate|info|list> ...");
        }
        try {
            Path root = options().dataDirectory().resolve("requests");
            LandformDataCodec codec = new LandformDataCodec();
            switch (args[1]) {
                case "create" -> {
                    if (args.length != 3) {
                        return usage(err, "request-create", "Usage: request create <request-id>");
                    }
                    Path target = requestPath(root, args[2]);
                    if (Files.exists(target)) {
                        throw new LandformException(LandformErrorCode.REQUEST_INVALID,
                                "Request already exists.", "request-create", args[2], "publish",
                                "Choose another request ID or edit the existing request.");
                    }
                    GenerationRequest value = new GenerationRequest(
                            1, args[2], new GenerationBounds(128, 128, -32, 160, 62),
                            "Describe the terrain before validation.", List.of(),
                            new GenerationOptions(1, 0L), new OutputOptions(128, true, true));
                    codec.writeGenerationRequest(target, value);
                    emit(out, requestMap(value, target));
                }
                case "bounds" -> {
                    if (args.length != 8) {
                        return usage(err, "request-bounds",
                                "Usage: request bounds <request-id> <width> <length> <min-y> <max-y> <water-level>");
                    }
                    Path target = requestPath(root, args[2]);
                    GenerationRequest current = codec.readGenerationRequest(target);
                    GenerationRequest updated = new GenerationRequest(
                            current.schemaVersion(), current.requestId(),
                            new GenerationBounds(integer(args[3]), integer(args[4]), integer(args[5]),
                                    integer(args[6]), integer(args[7])),
                            current.prompt(), current.images(), current.generation(), current.output());
                    codec.writeGenerationRequest(target, updated);
                    emit(out, requestMap(updated, target));
                }
                case "prompt" -> {
                    if (args.length < 4) {
                        return usage(err, "request-prompt", "Usage: request prompt <request-id> <prompt...>");
                    }
                    String prompt = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    if (looksSecret(prompt)) {
                        throw new LandformException(LandformErrorCode.REQUEST_INVALID,
                                "Prompt resembles a secret and was refused.", "request-prompt", args[2],
                                "secret-filter", "Set provider keys only through environment variables.");
                    }
                    Path target = requestPath(root, args[2]);
                    GenerationRequest current = codec.readGenerationRequest(target);
                    GenerationRequest updated = new GenerationRequest(
                            current.schemaVersion(), current.requestId(), current.bounds(), prompt,
                            current.images(), current.generation(), current.output());
                    codec.writeGenerationRequest(target, updated);
                    emit(out, requestMap(updated, target));
                }
                case "validate", "info" -> {
                    if (args.length != 3) {
                        return usage(err, "request-" + args[1], "Usage: request " + args[1] + " <request-id>");
                    }
                    Path target = requestPath(root, args[2]);
                    emit(out, requestMap(codec.readGenerationRequest(target), target));
                }
                case "list" -> {
                    if (args.length != 2) {
                        return usage(err, "request-list", "Usage: request list");
                    }
                    List<String> ids = new ArrayList<>();
                    if (Files.isDirectory(root)) {
                        try (var paths = Files.list(root)) {
                            for (Path path : paths.filter(Files::isDirectory).sorted().toList()) {
                                if (!Files.isSymbolicLink(path) && Files.isRegularFile(path.resolve("request.yml"))) {
                                    ids.add(codec.readGenerationRequest(path.resolve("request.yml")).requestId());
                                }
                            }
                        }
                    }
                    emit(out, Map.of("requests", ids));
                }
                default -> {
                    return usage(err, "request", "Unknown request subcommand: " + args[1]);
                }
            }
            return 0;
        } catch (RuntimeException | IOException exception) {
            reportFailure(err, "request-" + args[1], unwrap(exception), 1);
            return 1;
        }
    }

    private static int candidate(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 3) {
            return usage(err, "candidate", "Usage: candidate list <request-id> | candidate <info|preview|validate> <candidate-id>");
        }
        try {
            Path root = options().dataDirectory().resolve("candidates").toAbsolutePath().normalize();
            if (args[1].equals("list")) {
                List<String> ids = new ArrayList<>();
                if (Files.isDirectory(root)) {
                    try (var paths = Files.list(root)) {
                        paths.filter(Files::isDirectory).filter(value -> !Files.isSymbolicLink(value))
                                .filter(value -> {
                                    try {
                                        return new LandformDataCodec().readWorldBlueprint(
                                                value.resolve("world-blueprint.json")).requestId().equals(args[2]);
                                    } catch (IOException | RuntimeException exception) {
                                        return false;
                                    }
                                }).map(value -> value.getFileName().toString()).sorted().forEach(ids::add);
                    }
                }
                emit(out, Map.of("requestId", args[2], "candidates", ids));
                return 0;
            }
            Path directory = safeDirectory(root, args[2]);
            List<Map<String, Object>> files = new ArrayList<>();
            try (var paths = Files.list(directory)) {
                for (Path file : paths.filter(Files::isRegularFile).filter(value -> !Files.isSymbolicLink(value))
                        .sorted().toList()) {
                    boolean include = !args[1].equals("preview") || file.getFileName().toString().endsWith(".png");
                    if (include) {
                        files.add(Map.of("file", file.getFileName().toString(), "bytes", Files.size(file),
                                "checksum", Sha256.file(file)));
                    }
                }
            }
            if (args[1].equals("validate")) {
                Set<String> names = files.stream().map(value -> value.get("file").toString())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                Set<String> required = Set.of(
                        "world-blueprint.json", "generation-summary.json", "validation.json",
                        "structures.json", "overview.png", "height.png", "water.png", "slope.png",
                        "materials.png", "features.png", "structures.png", "validation.png");
                if (!names.containsAll(required)) {
                    throw new LandformException(LandformErrorCode.REQUEST_INVALID,
                            "Candidate is incomplete.", "candidate-validate", args[2],
                            "artifact-validation", "Generate the candidate again.");
                }
                LandformDataCodec codec = new LandformDataCodec();
                codec.readWorldBlueprint(directory.resolve("world-blueprint.json"));
                codec.readStructurePlacements(directory.resolve("structures.json"));
            }
            emit(out, Map.of("candidateId", args[2], "directory", directory.toString(), "files", files));
            return 0;
        } catch (RuntimeException | IOException exception) {
            reportFailure(err, "candidate-" + args[1], unwrap(exception), 1);
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

    private static int recovery(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2 || args.length > 3
                || !(args[1].equals("list") || args[1].equals("status") || args[1].equals("diagnose"))) {
            return usage(err, "recovery", "Usage: recovery <list|status|diagnose> [placement-id]");
        }
        try (GenerationExecutors executors = GenerationExecutors.createDefault(1)) {
            FilePlacementJournalRepository repository = new FilePlacementJournalRepository(
                    options().dataDirectory().resolve("placements"), executors);
            if (args[1].equals("list")) {
                if (args.length != 2) {
                    return usage(err, "recovery-list", "Usage: recovery list");
                }
                var values = repository.findAll().join().stream()
                        .filter(value -> value.state() == PlacementState.RECOVERY_REQUIRED)
                        .map(value -> Map.of("placementId", value.plan().placementId().toString(),
                                "state", value.state().name(), "message", value.message())).toList();
                emit(out, Map.of("placements", values));
                return 0;
            }
            if (args.length != 3) {
                return usage(err, "recovery-" + args[1], "Placement ID is required.");
            }
            var journal = repository.find(UUID.fromString(args[2])).join().orElseThrow(() ->
                    new LandformException(LandformErrorCode.NOT_FOUND, "Placement was not found.",
                            "recovery-" + args[1], args[2], "journal", "Check the placement ID."));
            String classification = journal.state() == PlacementState.RECOVERY_REQUIRED
                    ? "MANUAL_INTERVENTION_REQUIRED" : "NOT_REQUIRED";
            emit(out, Map.of("placementId", args[2], "state", journal.state().name(),
                    "classification", classification, "message", journal.message(),
                    "suggestedAction", "World mutation is Paper-only; use /landformcraft apply recover diagnose."));
            return 0;
        } catch (RuntimeException exception) {
            reportFailure(err, "recovery-" + args[1], unwrap(exception), 1);
            return 1;
        }
    }

    private static Path requestPath(Path root, String id) throws IOException {
        if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new LandformException(LandformErrorCode.REQUEST_INVALID, "Invalid request ID.",
                    "request", id, "path-validation", "Use a lowercase portable slug.");
        }
        Path directory = root.resolve(id).normalize();
        if (!directory.startsWith(root.toAbsolutePath().normalize())) {
            throw new LandformException(LandformErrorCode.PATH_UNSAFE, "Request path escapes its root.",
                    "request", id, "path-validation", "Use a portable request ID.");
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("request directory must not be a symbolic link");
        }
        return directory.resolve("request.yml");
    }

    private static Map<String, Object> requestMap(GenerationRequest value, Path path) {
        return Map.of(
                "requestId", value.requestId(), "path", path.toAbsolutePath().normalize().toString(),
                "width", value.bounds().width(), "length", value.bounds().length(),
                "minY", value.bounds().minY(), "maxY", value.bounds().maxY(),
                "waterLevel", value.bounds().waterLevel(), "images", value.images().size());
    }

    private static Path safeDirectory(Path root, String id) throws IOException {
        if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IOException("invalid portable ID");
        }
        Path value = root.resolve(id).normalize();
        if (!value.startsWith(root) || !Files.isDirectory(value) || Files.isSymbolicLink(value)) {
            throw new IOException("directory was not found or is unsafe");
        }
        return value;
    }

    private static boolean looksSecret(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("authorization:") || lower.contains("api_key=")
                || lower.contains("apikey=") || value.matches(".*sk-[A-Za-z0-9_-]{16,}.*");
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
        return value == null ? new CliOptions(Path.of(".landformcraft"), false, false, false) : value;
    }

    private record CliOptions(Path dataDirectory, boolean json, boolean quiet, boolean verbose) {
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
        output.println("  Common: --data-dir <directory> --json --quiet --verbose");
        output.println("  version");
        output.println("  doctor");
        output.println("  request <create|bounds|prompt|validate|info|list> ...");
        output.println("  job <status|cancel> <job-id>");
        output.println("  candidate list <request-id> | candidate <info|preview|validate> <candidate-id>");
        output.println("  asset <validate|import|list|info|remove> ...");
        output.println("  recovery <list|status|diagnose> [placement-id]");
        output.println("  validate <request.yml> <terrain-intent.json>");
        output.println("  generate <request.yml> <terrain-intent.json> [output-directory] [candidate-index]");
        output.println("  preview <request.yml> <terrain-intent.json> [output-directory] [candidate-index]");
        output.println("  export <request.yml> <terrain-intent.json> [exports-root] [candidate-index]");
        output.println("  verify <release-directory-or-zip>");
        output.println("  journal-verify <placement-journal.json>");
        output.println("  design <import|fixture|openai|anthropic> <request.yml> <intent.json|model> "
                + "[designs-root] [jobs-root]");
        output.println("  design-verify <design-directory>");
    }
}
