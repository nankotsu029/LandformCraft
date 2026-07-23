package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.command.V2RequestStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2WorkflowServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.design.TerrainDesignApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.ExportBudgetV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.core.v2.job.ExportJobServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.job.ExportJobStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.job.ExportJobSubmissionV2;
import com.github.nankotsu029.landformcraft.core.v2.job.ExportPlanStoreV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobKindV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobSnapshotV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Paper adapter for the offline v2 verbs (V2-12-03): {@code request}, {@code design},
 * {@code generate}, {@code export}, {@code preview}.
 *
 * <p>Operator-supplied path arguments arrive over chat and are never trusted. Every path is
 * resolved inside the plugin-owned v2 workspace and must be a regular, non-symbolic file or
 * directory; absolute paths, {@code ..}, and backslashes are rejected. All work runs on the bounded
 * executors, never on the Paper main thread.</p>
 */
public final class PaperV2WorkflowServiceV2 {
    private final GenerationExecutors executors;
    private final V2WorkflowServiceV2 workflow;
    private final Path workspaceRoot;
    private final ExportJobServiceV2 jobs;
    private final ExportPlanStoreV2 plans;

    public PaperV2WorkflowServiceV2(
            GenerationExecutors executors,
            TerrainDesignApplicationServiceV2.ProviderFactory providerFactory,
            Path workspaceRoot
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.workflow = new V2WorkflowServiceV2(executors, providerFactory);
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot")
                .toAbsolutePath().normalize();
        this.jobs = new ExportJobServiceV2(
                executors, new ExportJobStoreV2(this.workspaceRoot.resolve("jobs")), Clock.systemUTC());
        this.plans = new ExportPlanStoreV2(Clock.systemUTC());
    }

    /**
     * v2 request authoring (V2-12-08). The store is rooted at {@code <workspace>/requests}, so an
     * authored request is addressable from the other verbs as {@code requests/<id>.request-v2.json}
     * without the operator ever supplying a path.
     */
    public CompletionStage<GenerationRequestV2> createRequest(String requestId) {
        return executors.supplyIo(() -> io(() -> requests().create(requestId)));
    }

    public CompletionStage<GenerationRequestV2> setBounds(
            String requestId,
            int width,
            int length,
            int minY,
            int maxY,
            int waterLevel
    ) {
        return executors.supplyIo(() -> io(() ->
                requests().bounds(requestId, width, length, minY, maxY, waterLevel)));
    }

    public CompletionStage<GenerationRequestV2> setBoundsFromSelection(
            String requestId,
            int width,
            int length,
            int minY,
            int maxY
    ) {
        return executors.supplyIo(() -> io(() ->
                requests().boundsKeepingWaterLevel(requestId, width, length, minY, maxY)));
    }

    public CompletionStage<GenerationRequestV2> setConstraintMap(
            String requestId,
            String sourceSlug,
            String file,
            String expectedSha256,
            int expectedWidth,
            int expectedLength
    ) {
        return executors.supplyIo(() -> io(() -> requests().constraintMap(
                requestId, sourceSlug, file, expectedSha256, expectedWidth, expectedLength)));
    }

    /**
     * Declares one constraint map source of any role from a sealed {@code promote} record (V2-19-04).
     *
     * <p>The promotion directory is operator-supplied and therefore resolved inside the plugin
     * workspace like every other path this adapter accepts. Unlike {@link #setConstraintMap} the
     * declaration is added rather than replacing the set.</p>
     */
    public CompletionStage<GenerationRequestV2> setConstraintSource(
            String requestId,
            String sourceSlug,
            TerrainIntentV2.ConstraintMapRole role,
            String relativePromotionDirectory,
            String file
    ) {
        Path promotion = requireDirectory(relativePromotionDirectory, "promotion directory");
        return executors.supplyIo(() -> io(() ->
                requests().constraintSource(requestId, sourceSlug, role, promotion, file)));
    }

    /** Replaces the generation settings (seed／tile size); export-relevant since V2-18-09/10. */
    public CompletionStage<GenerationRequestV2> setGeneration(
            String requestId,
            long globalSeed,
            int tileSize
    ) {
        return executors.supplyIo(() -> io(() -> requests().generation(requestId, globalSeed, tileSize)));
    }

    /**
     * Declares the macro foundation's per-medium base elevation (V2-18-10, ADR 0038 D2-2). Required
     * together with the constraint map source before a {@code surface-2_5d} export can pass the
     * foundation owner gate.
     */
    public CompletionStage<GenerationRequestV2> setFoundationBaseLevels(
            String requestId,
            int landSurfaceY,
            int waterBedY
    ) {
        return executors.supplyIo(() -> io(() ->
                requests().foundationBaseLevels(requestId, landSurfaceY, waterBedY)));
    }

    public CompletionStage<GenerationRequestV2> setPrompt(String requestId, String prompt) {
        return executors.supplyIo(() -> io(() -> requests().prompt(requestId, prompt)));
    }

    public CompletionStage<List<String>> listRequests() {
        return executors.supplyIo(() -> io(() -> requests().list()));
    }

    /** Workspace-relative path of an authored request, for operator-facing messages. */
    public String requestArgument(String requestId) {
        return "requests/" + requests().relativePathOf(requestId);
    }

    private V2RequestStoreV2 requests() {
        return new V2RequestStoreV2(resolve("requests", "requests root"));
    }

    private static <T> T io(IoSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }

    public CompletionStage<Map<String, Object>> inspectRequest(String relativeRequest) {
        Path request = requireFile(relativeRequest, "request");
        return executors.supplyIo(() -> {
            try {
                return workflow.inspectRequest(request);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    public CompletionStage<DesignArtifactsV2> design(
            String pathKind,
            String relativeRequest,
            String modelOrIntentPath
    ) {
        Path request = requireFile(relativeRequest, "request");
        Path designs = resolve("designs", "designs root");
        // The v2 design service already owns its executor task; wrapping it in another one would
        // hold an I/O permit while waiting for a second.
        return workflow.design(pathKind, request, modelOrIntentPath, designs);
    }

    public CompletionStage<Release2ExportResultV2> export(
            String relativeRequest,
            String relativeIntent,
            String releaseId,
            SurfaceBaselineV2 baseline,
            boolean createZip
    ) {
        Path request = requireFile(relativeRequest, "request");
        Path intent = requireFile(relativeIntent, "terrain intent");
        Path exports = resolve("exports", "exports root");
        Path work = resolve("work", "work root").resolve(requireReleaseId(releaseId));
        return executors.supplyIo(() -> {
            try {
                return workflow.export(request, intent, work, exports, releaseId, baseline, createZip);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /**
     * Reserves one asynchronous export and returns its single-use confirmation token (V2-12-09).
     * The inputs are digested now and re-digested at {@code create}, so editing the request or the
     * intent between the two steps invalidates the reservation instead of exporting something else.
     */
    public CompletionStage<ExportPlanStoreV2.PreparedExportV2> planExport(
            String relativeRequest,
            String relativeIntent,
            String releaseId,
            SurfaceBaselineV2 baseline,
            boolean createZip,
            String actor
    ) {
        Path request = requireFile(relativeRequest, "request");
        Path intent = requireFile(relativeIntent, "terrain intent");
        Path exports = resolve("exports", "exports root");
        Path work = resolve("work", "work root").resolve(requireReleaseId(releaseId));
        return executors.supplyIo(() -> io(() -> {
            ExportJobSubmissionV2 submission = new ExportJobSubmissionV2(
                    new LandformV2DataCodec().readGenerationRequest(request).requestId(),
                    releaseId,
                    createZip ? ExportJobKindV2.EXPORT : ExportJobKindV2.GENERATE,
                    request, intent, work, exports, baseline, ExportBudgetV2.defaults());
            // Admit disk before issuing a token: a reservation the runtime cannot honour must fail
            // at plan time, not after the operator has confirmed it.
            submission.budget().requireFreeDisk(work);
            return plans.prepare(submission, actor, inputsDigest(submission));
        }));
    }

    /** Consumes the confirmation token and queues the job. Returns the {@code QUEUED} snapshot. */
    public CompletionStage<ExportJobSnapshotV2> createExport(String planId, String token, String actor) {
        UUID parsed = requirePlanId(planId);
        return executors.supplyIo(() -> io(() -> jobs.submit(
                plans.consume(parsed, token, actor, PaperV2WorkflowServiceV2::inputsDigest))));
    }

    public CompletionStage<ExportJobSnapshotV2> jobStatus(String jobId) {
        return executors.supplyIo(() -> io(() -> jobs.status(jobId)));
    }

    public CompletionStage<ExportJobSnapshotV2> cancelJob(String jobId) {
        return executors.supplyIo(() -> io(() -> jobs.cancel(jobId)));
    }

    public CompletionStage<List<ExportJobSnapshotV2>> listJobs() {
        return executors.supplyIo(() -> io(jobs::list));
    }

    public CompletionStage<List<ExportJobSnapshotV2>> candidates(String requestId) {
        return executors.supplyIo(() -> io(() -> jobs.candidates(requestId)));
    }

    /**
     * Digest of everything the reservation covers. Recomputed at confirmation time from the stored
     * plan, so a request or intent edited in between no longer matches.
     */
    private static String inputsDigest(ExportJobSubmissionV2 submission) throws IOException {
        String value = Sha256.file(submission.generationRequest()) + "\n"
                + Sha256.file(submission.terrainIntent()) + "\n"
                + submission.releaseId() + "\n"
                + submission.kind().name();
        return Sha256.bytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID requirePlanId(String planId) {
        Objects.requireNonNull(planId, "planId");
        UUID parsed;
        try {
            parsed = UUID.fromString(planId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("v2 export plan id must be a canonical UUID: " + planId);
        }
        if (!parsed.toString().equals(planId)) {
            throw new IllegalArgumentException("v2 export plan id must be a canonical UUID: " + planId);
        }
        return parsed;
    }

    public CompletionStage<Map<String, Object>> inspectPreviews(String relativeRelease) {
        Path release = requirePath(relativeRelease, "release");
        return executors.supplyIo(() -> {
            try {
                return workflow.inspectPreviews(release);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /** Plugin-owned root every v2 command argument is resolved inside. */
    public Path workspaceRoot() {
        return workspaceRoot;
    }

    private Path resolve(String name, String description) {
        Path target = workspaceRoot.resolve(name).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("v2 " + description + " escapes the plugin workspace");
        }
        try {
            Files.createDirectories(target);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return target;
    }

    private Path requireFile(String relative, String description) {
        Path resolved = requirePath(relative, description);
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("v2 " + description + " must be a regular file inside the workspace");
        }
        return resolved;
    }

    private Path requirePath(String relative, String description) {
        Objects.requireNonNull(relative, description);
        Path candidate = Path.of(relative);
        if (candidate.isAbsolute() || relative.contains("\\") || relative.contains("..")
                || !candidate.normalize().equals(candidate)) {
            throw new IllegalArgumentException("v2 " + description + " must be a safe relative path");
        }
        Path resolved = workspaceRoot.resolve(candidate).normalize();
        if (!resolved.startsWith(workspaceRoot) || Files.isSymbolicLink(resolved)
                || !Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("v2 " + description + " must exist inside the plugin workspace");
        }
        return resolved;
    }

    private Path requireDirectory(String relative, String description) {
        Path resolved = requirePath(relative, description);
        if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(
                    "v2 " + description + " must be a directory inside the workspace");
        }
        return resolved;
    }

    private static String requireReleaseId(String releaseId) {
        if (releaseId == null || !releaseId.matches("^[a-z0-9][a-z0-9._-]{0,63}$")) {
            throw new IllegalArgumentException("v2 release id must be a lowercase portable slug");
        }
        return releaseId;
    }
}
