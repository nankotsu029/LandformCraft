package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignProvider;
import com.github.nankotsu029.landformcraft.format.DesignArtifactVerifier;
import com.github.nankotsu029.landformcraft.format.DesignVerification;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.ReleaseArtifacts;
import com.github.nankotsu029.landformcraft.format.ReleaseVerification;
import com.github.nankotsu029.landformcraft.format.ReleaseVerifier;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GenerationJobSnapshot;
import com.github.nankotsu029.landformcraft.model.GenerationOptions;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.GenerationStage;
import com.github.nankotsu029.landformcraft.model.OutputOptions;
import com.github.nankotsu029.landformcraft.model.ActorIdentity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Filesystem-backed request/design/generation/export workflow used by Paper commands. */
public final class PaperWorkflowService implements AutoCloseable {
    private static final Duration EXPORT_CONFIRMATION_TTL = Duration.ofMinutes(10);

    private final Path root;
    private final GenerationExecutors executors;
    private final TerrainDesignProviderFactory providerFactory;
    private final Clock clock;
    private final LandformDataCodec codec = new LandformDataCodec();
    private final FileGenerationJobRepository jobs;
    private final ConcurrentHashMap<UUID, CompletableFuture<?>> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ExportPlan> exportPlans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, GenerationJobSnapshot> jobStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> jobWrites = new ConcurrentHashMap<>();

    public PaperWorkflowService(
            Path root, GenerationExecutors executors, TerrainDesignProviderFactory providerFactory, Clock clock
    ) {
        this.root = root.toAbsolutePath().normalize();
        this.executors = Objects.requireNonNull(executors, "executors");
        this.providerFactory = Objects.requireNonNull(providerFactory, "providerFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.jobs = new FileGenerationJobRepository(this.root.resolve("jobs"), executors);
    }

    public <T> CompletableFuture<T> io(Supplier<T> operation) {
        return executors.supplyIo(operation);
    }

    public CompletableFuture<List<String>> releases() {
        return executors.supplyIo(() -> {
            Path exports = root.resolve("exports");
            if (!Files.isDirectory(exports)) {
                return List.of();
            }
            try (var paths = Files.walk(exports, 2)) {
                return paths.filter(Files::isDirectory)
                        .filter(value -> Files.isRegularFile(value.resolve("manifest.json")))
                        .filter(value -> !Files.isSymbolicLink(value))
                        .map(value -> exports.relativize(value).toString().replace('\\', '/'))
                        .sorted().toList();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public CompletableFuture<GenerationRequest> createRequest(String requestId) {
        return executors.supplyIo(() -> {
            try {
                Path path = requestPath(requestId);
                if (Files.exists(path)) {
                    throw invalid(requestId, "Request already exists.");
                }
                GenerationRequest request = new GenerationRequest(
                        1, requestId, new GenerationBounds(128, 128, -32, 160, 62),
                        "Describe the terrain before validation.", List.of(),
                        new GenerationOptions(1, 0L), new OutputOptions(128, true, true));
                codec.writeGenerationRequest(path, request);
                return request;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public CompletableFuture<GenerationRequest> setBounds(
            String requestId, int width, int length, int minY, int maxY, int waterLevel
    ) {
        return mutateRequest(requestId, current -> new GenerationRequest(
                current.schemaVersion(), current.requestId(),
                new GenerationBounds(width, length, minY, maxY, waterLevel), current.prompt(),
                current.images(), current.generation(), current.output()));
    }

    public CompletableFuture<GenerationRequest> setPrompt(String requestId, String prompt) {
        if (secretLike(prompt)) {
            return CompletableFuture.failedFuture(invalid(requestId,
                    "Prompt resembles a secret; provider keys are environment-only."));
        }
        return mutateRequest(requestId, current -> new GenerationRequest(
                current.schemaVersion(), current.requestId(), current.bounds(), prompt,
                current.images(), current.generation(), current.output()));
    }

    public CompletableFuture<GenerationRequest> requestInfo(String requestId) {
        return executors.supplyIo(() -> {
            try {
                return codec.readGenerationRequest(requestPath(requestId));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public CompletableFuture<List<GenerationRequest>> requestList() {
        return executors.supplyIo(() -> {
            try {
                Path requests = root.resolve("requests");
                if (!Files.isDirectory(requests)) {
                    return List.of();
                }
                List<GenerationRequest> result = new ArrayList<>();
                try (var paths = Files.list(requests)) {
                    for (Path directory : paths.filter(Files::isDirectory).sorted().toList()) {
                        if (!Files.isSymbolicLink(directory) && Files.isRegularFile(directory.resolve("request.yml"))) {
                            result.add(codec.readGenerationRequest(directory.resolve("request.yml")));
                        }
                    }
                }
                return List.copyOf(result);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public UUID startDesign(String requestId, String provider, String modelOrPath) {
        Path request = requestPathUnchecked(requestId);
        TerrainDesignProvider adapter = providerFactory.create(provider, modelOrPath);
        DesignJobHandle handle = new TerrainDesignApplicationService(executors, adapter, jobs)
                .start(request, root.resolve("designs"));
        jobStates.put(handle.jobId(), new GenerationJobSnapshot(
                1, handle.jobId(), requestId, GenerationStage.QUEUED, 0.0, clock.instant(), "design job queued"));
        active.put(handle.jobId(), handle.completion());
        handle.completion().whenComplete((value, failure) -> {
            active.remove(handle.jobId());
            adapter.close();
            jobStates.compute(handle.jobId(), (ignored, current) -> {
                if (current != null && current.stage() == GenerationStage.CANCELLED) {
                    return current;
                }
                return new GenerationJobSnapshot(
                        1, handle.jobId(), failure == null ? value.audit().requestId() : requestId,
                        failure == null ? GenerationStage.READY : GenerationStage.FAILED,
                        1.0, clock.instant(), failure == null ? "design artifact ready" : "design failed safely");
            });
        });
        return handle.jobId();
    }

    public CompletableFuture<DesignVerification> designInfo(UUID designId) {
        return executors.supplyIo(() -> {
            try {
                return new DesignArtifactVerifier().verify(findDesign(designId));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public UUID startGenerate(UUID designId) {
        UUID jobId = UUID.randomUUID();
        saveJob(jobId, "unresolved", GenerationStage.QUEUED, 0.0, "generation queued");
        CompletableFuture<GenerationOutcome> future = designInfo(designId).thenCompose(design -> {
            Path request = requestPathUnchecked(design.audit().requestId());
            try {
                if (!Sha256.file(request).equals(design.audit().requestChecksum())) {
                    throw new LandformException(LandformErrorCode.REQUEST_INVALID,
                            "Request changed after Design Package creation.", "generate", designId.toString(),
                            "request-checksum", "Create a new Design Package from the current request.");
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            saveJob(jobId, design.audit().requestId(), GenerationStage.GENERATING_TERRAIN, 0.2,
                    "generation started");
            Path candidate = root.resolve("candidates").resolve(jobId.toString());
            return new GenerationApplicationService(executors).generatePreview(
                    request, findDesignUnchecked(designId).resolve("terrain-intent.json"), candidate, 0);
        });
        active.put(jobId, future);
        future.whenComplete((outcome, failure) -> {
            active.remove(jobId);
            if (failure == null) {
                try {
                    codec.writeJson(root.resolve("candidates").resolve(jobId.toString())
                            .resolve("candidate-source.json"), Map.of(
                            "candidateId", jobId.toString(), "designId", designId.toString(),
                            "requestId", outcome.terrainPlan().blueprint().requestId()));
                    saveJob(jobId, outcome.terrainPlan().blueprint().requestId(), GenerationStage.READY, 1.0,
                            "candidate ready");
                } catch (IOException exception) {
                    saveJob(jobId, outcome.terrainPlan().blueprint().requestId(), GenerationStage.FAILED, 1.0,
                            "candidate metadata publication failed");
                }
            } else {
                saveJob(jobId, "unresolved", GenerationStage.FAILED, 1.0, "generation failed safely");
            }
        });
        return jobId;
    }

    public CompletableFuture<GenerationJobSnapshot> jobStatus(UUID jobId) {
        GenerationJobSnapshot current = jobStates.get(jobId);
        if (current != null) {
            return CompletableFuture.completedFuture(current);
        }
        return jobs.find(jobId.toString()).thenApply(value -> value.orElseThrow(() ->
                new LandformException(LandformErrorCode.NOT_FOUND, "Job was not found.", "job-status",
                        jobId.toString(), "job-repository", "Check the job ID.")));
    }

    public CompletableFuture<GenerationJobSnapshot> cancel(UUID jobId) {
        CompletableFuture<?> future = active.remove(jobId);
        if (future != null) {
            future.cancel(true);
        }
        GenerationJobSnapshot current = jobStates.get(jobId);
        if (current != null) {
            if (terminal(current.stage())) {
                return CompletableFuture.completedFuture(current);
            }
            return CompletableFuture.completedFuture(saveJob(jobId, current.requestId(),
                    GenerationStage.CANCELLED, current.progress(), "job cancellation requested"));
        }
        return jobs.cancel(jobId.toString(), clock);
    }

    public CompletableFuture<List<UUID>> candidates(String requestId) {
        requireSlug(requestId);
        return executors.supplyIo(() -> listUuidDirectories(root.resolve("candidates")).stream()
                .filter(id -> candidateSource(id).requestId().equals(requestId))
                .toList());
    }

    public CompletableFuture<List<Path>> candidateInfo(UUID candidateId) {
        return inspectCandidate(candidateId, false, false);
    }

    public CompletableFuture<List<Path>> candidatePreview(UUID candidateId) {
        return inspectCandidate(candidateId, true, false);
    }

    public CompletableFuture<List<Path>> candidateValidate(UUID candidateId) {
        return inspectCandidate(candidateId, false, true);
    }

    private CompletableFuture<List<Path>> inspectCandidate(UUID candidateId, boolean previewsOnly, boolean validate) {
        return executors.supplyIo(() -> {
            Path candidate = candidate(candidateId);
            try (var paths = Files.list(candidate)) {
                List<Path> files = paths.filter(Files::isRegularFile)
                        .filter(value -> !Files.isSymbolicLink(value)).sorted().toList();
                if (validate) {
                    Set<String> names = files.stream().map(value -> value.getFileName().toString())
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    Set<String> required = Set.of(
                            "world-blueprint.json", "generation-summary.json", "validation.json",
                            "structures.json", "overview.png", "height.png", "water.png", "slope.png",
                            "materials.png", "features.png", "structures.png", "validation.png",
                            "candidate-source.json");
                    if (!names.containsAll(required)) {
                        throw new LandformException(LandformErrorCode.REQUEST_INVALID,
                                "Candidate is incomplete.", "candidate-validate", candidateId.toString(),
                                "candidate-contract", "Generate the candidate again.");
                    }
                    codec.readWorldBlueprint(candidate.resolve("world-blueprint.json"));
                    codec.readStructurePlacements(candidate.resolve("structures.json"));
                    candidateSource(candidateId);
                }
                return previewsOnly ? files.stream()
                        .filter(value -> value.getFileName().toString().endsWith(".png")).toList() : files;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public PreparedExport planExport(UUID candidateId, ActorIdentity actor) {
        Path candidate = candidate(candidateId);
        String checksum;
        try {
            checksum = Sha256.file(candidate.resolve("world-blueprint.json"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        UUID planId = UUID.randomUUID();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(EXPORT_CONFIRMATION_TTL);
        String token = UUID.randomUUID().toString();
        ExportPlan plan = new ExportPlan(planId, candidateId, actor, checksum, createdAt, expiresAt,
                hash(planId, candidateId, actor, checksum, createdAt, expiresAt, token), false);
        exportPlans.put(planId, plan);
        return new PreparedExport(planId, candidateId, token, expiresAt);
    }

    public UUID startExport(UUID planId, String token, ActorIdentity actor) {
        ExportPlan plan = Optional.ofNullable(exportPlans.get(planId)).orElseThrow(() ->
                new LandformException(LandformErrorCode.NOT_FOUND, "Export plan was not found.",
                        "export-create", planId.toString(), "export-plan", "Create a new export plan."));
        if (plan.used || !plan.actor.equals(actor) || !clock.instant().isBefore(plan.expiresAt)
                || !MessageDigest.isEqual(plan.hash.getBytes(StandardCharsets.US_ASCII),
                hash(plan.planId, plan.candidateId, actor, plan.candidateChecksum, plan.createdAt,
                        plan.expiresAt, token).getBytes(StandardCharsets.US_ASCII))) {
            throw new LandformException(LandformErrorCode.CONFIRM_ACTOR_MISMATCH,
                    "Export confirmation is invalid, expired, reused, or belongs to another operator.",
                    "export-create", planId.toString(), "confirmation", "Create a new export plan.");
        }
        exportPlans.put(planId, plan.markUsed());
        UUID jobId = UUID.randomUUID();
        DesignSource source = candidateSource(plan.candidateId);
        saveJob(jobId, source.requestId, GenerationStage.PACKAGING, 0.5, "export started");
        CompletableFuture<ReleaseArtifacts> future = new ReleaseApplicationService(executors).export(
                requestPathUnchecked(source.requestId), findDesignUnchecked(source.designId)
                        .resolve("terrain-intent.json"), root.resolve("exports"), 0);
        active.put(jobId, future);
        future.whenComplete((release, failure) -> {
            active.remove(jobId);
            saveJob(jobId, source.requestId, failure == null ? GenerationStage.READY : GenerationStage.FAILED,
                    1.0, failure == null ? "release ready: " + release.releaseId() : "export failed safely");
        });
        return jobId;
    }

    public CompletableFuture<ReleaseVerification> verifyRelease(String relative) {
        return executors.supplyIo(() -> {
            Path release = portable(root.resolve("exports"), relative);
            try {
                return new ReleaseVerifier().verify(release);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Override
    public void close() {
        active.values().forEach(value -> value.cancel(true));
        active.clear();
    }

    private CompletableFuture<GenerationRequest> mutateRequest(
            String requestId, java.util.function.Function<GenerationRequest, GenerationRequest> mutation
    ) {
        return executors.supplyIo(() -> {
            try {
                Path path = requestPath(requestId);
                GenerationRequest updated = mutation.apply(codec.readGenerationRequest(path));
                codec.writeGenerationRequest(path, updated);
                return updated;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private GenerationJobSnapshot saveJob(
            UUID id, String requestId, GenerationStage stage, double progress, String message
    ) {
        String safeRequest = requestId.matches("[a-z0-9][a-z0-9._-]{0,63}") ? requestId : "unresolved";
        GenerationJobSnapshot proposed = new GenerationJobSnapshot(
                1, id, safeRequest, stage, progress, clock.instant(), message);
        GenerationJobSnapshot selected = jobStates.compute(id, (ignored, current) -> {
            if (current == null) {
                return proposed;
            }
            if (terminal(current.stage()) || proposed.progress() < current.progress()
                    || proposed.stage() == GenerationStage.QUEUED) {
                return current;
            }
            return proposed;
        });
        if (selected != proposed) {
            return selected;
        }
        jobWrites.compute(id, (ignored, previous) -> {
            CompletableFuture<Void> ordered = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((value, failure) -> null);
            return ordered.thenCompose(value -> jobs.save(proposed));
        });
        return proposed;
    }

    private static boolean terminal(GenerationStage stage) {
        return stage == GenerationStage.READY || stage == GenerationStage.FAILED
                || stage == GenerationStage.CANCELLED;
    }

    private Path requestPath(String id) throws IOException {
        Path path = requestPathUnchecked(id);
        Files.createDirectories(path.getParent());
        if (Files.isSymbolicLink(path.getParent())) {
            throw new IOException("request directory must not be a symbolic link");
        }
        return path;
    }

    private Path requestPathUnchecked(String id) {
        requireSlug(id);
        return root.resolve("requests").resolve(id).resolve("request.yml").normalize();
    }

    private Path importPath(String relative) {
        return portable(root.resolve("imports"), relative);
    }

    private Path findDesign(UUID id) throws IOException {
        Path designs = root.resolve("designs");
        if (!Files.isDirectory(designs)) {
            throw new IOException("design root does not exist");
        }
        try (var paths = Files.walk(designs, 2)) {
            return paths.filter(Files::isDirectory).filter(value -> value.getFileName().toString().equals(id.toString()))
                    .filter(value -> !Files.isSymbolicLink(value)).findFirst()
                    .orElseThrow(() -> new IOException("design was not found"));
        }
    }

    private Path findDesignUnchecked(UUID id) {
        try {
            return findDesign(id);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Path candidate(UUID id) {
        Path value = root.resolve("candidates").resolve(id.toString()).normalize();
        if (!value.startsWith(root.resolve("candidates")) || !Files.isDirectory(value)
                || Files.isSymbolicLink(value)) {
            throw new LandformException(LandformErrorCode.NOT_FOUND, "Candidate was not found.",
                    "candidate", id.toString(), "candidate-store", "Check candidate list.");
        }
        return value;
    }

    private DesignSource candidateSource(UUID id) {
        try {
            String json = Files.readString(candidate(id).resolve("candidate-source.json"));
            String design = json.replaceAll("(?s).*\\\"designId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*", "$1");
            String request = json.replaceAll("(?s).*\\\"requestId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*", "$1");
            return new DesignSource(UUID.fromString(design), request);
        } catch (IOException | IllegalArgumentException exception) {
            throw new LandformException(LandformErrorCode.REQUEST_INVALID,
                    "Candidate source metadata is invalid.", "export-plan", id.toString(),
                    "candidate-source", "Generate the candidate again.", exception);
        }
    }

    private static List<UUID> listUuidDirectories(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory).filter(value -> !Files.isSymbolicLink(value))
                    .map(value -> UUID.fromString(value.getFileName().toString()))
                    .sorted(Comparator.comparing(UUID::toString)).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path portable(Path root, String relative) {
        Path value = Path.of(relative);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(value).normalize();
        if (value.isAbsolute() || relative.contains("\\") || !value.normalize().equals(value)
                || !resolved.startsWith(normalizedRoot) || Files.isSymbolicLink(resolved)) {
            throw new LandformException(LandformErrorCode.PATH_UNSAFE, "Unsafe relative path.",
                    "path", relative, "path-validation", "Use a canonical path inside the configured root.");
        }
        return resolved;
    }

    private static String hash(
            UUID planId, UUID candidateId, ActorIdentity actor, String checksum,
            Instant createdAt, Instant expiresAt, String token
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = planId + "\n" + candidateId + "\n" + actor.canonical() + "\n"
                    + checksum + "\n" + createdAt + "\n" + expiresAt + "\n" + token;
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void requireSlug(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw invalid(value, "Invalid portable ID.");
        }
    }

    private static boolean secretLike(String value) {
        String lower = Objects.requireNonNull(value, "value").toLowerCase(java.util.Locale.ROOT);
        return lower.contains("authorization:") || lower.contains("api_key=")
                || value.matches(".*sk-[A-Za-z0-9_-]{16,}.*");
    }

    private static LandformException invalid(String id, String message) {
        return new LandformException(LandformErrorCode.REQUEST_INVALID, message, "request", id,
                "request-validation", "Inspect request info and correct the input.");
    }

    public record PreparedExport(UUID planId, UUID candidateId, String confirmationToken, Instant expiresAt) { }

    private record DesignSource(UUID designId, String requestId) { }

    private record ExportPlan(
            UUID planId, UUID candidateId, ActorIdentity actor, String candidateChecksum,
            Instant createdAt, Instant expiresAt, String hash, boolean used
    ) {
        ExportPlan markUsed() {
            return new ExportPlan(planId, candidateId, actor, candidateChecksum, createdAt, expiresAt, hash, true);
        }
    }
}
