package com.github.nankotsu029.landformcraft.core.v2.design;

import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignCapabilityNegotiatorV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignExceptionV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignFailureCodeV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.FixtureTerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.ImportedJsonTerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.ProviderCapabilityDescriptorV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignProviderV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignRequestV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignResultV2;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.ManualConstraintMapGenerationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.ManualConstraintMapResultV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageMaskExtractionLimitsV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignAuditV2;
import com.github.nankotsu029.landformcraft.model.v2.design.ImageDraftEvidenceV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

/** Release 2 design orchestrator. Never invokes terrain generation or implicit HARD image promotion. */
public final class TerrainDesignApplicationServiceV2 {
    @FunctionalInterface
    public interface ProviderFactory extends BiFunction<DesignPathKindV2, String, TerrainDesignProviderV2> {
    }

    private final GenerationExecutors executors;
    private final ProviderFactory providerFactory;
    private final Clock clock;
    private final LandformV2DataCodec codec;
    private final DesignArtifactPublisherV2 publisher;
    private final ManualConstraintMapGenerationServiceV2 manualService;
    private final ReferenceImageSoftDraftServiceV2 softDraftService;

    public TerrainDesignApplicationServiceV2(GenerationExecutors executors, ProviderFactory providerFactory) {
        this(executors, providerFactory, Clock.systemUTC());
    }

    public TerrainDesignApplicationServiceV2(
            GenerationExecutors executors,
            ProviderFactory providerFactory,
            Clock clock
    ) {
        this(
                executors,
                providerFactory,
                clock,
                new LandformV2DataCodec(),
                new DesignArtifactPublisherV2(),
                new ManualConstraintMapGenerationServiceV2(),
                new ReferenceImageSoftDraftServiceV2()
        );
    }

    TerrainDesignApplicationServiceV2(
            GenerationExecutors executors,
            ProviderFactory providerFactory,
            Clock clock,
            LandformV2DataCodec codec,
            DesignArtifactPublisherV2 publisher,
            ManualConstraintMapGenerationServiceV2 manualService,
            ReferenceImageSoftDraftServiceV2 softDraftService
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.providerFactory = providerFactory;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.manualService = Objects.requireNonNull(manualService, "manualService");
        this.softDraftService = Objects.requireNonNull(softDraftService, "softDraftService");
    }

    public boolean isRelease2Path() {
        return true;
    }

    public CompletableFuture<DesignArtifactsV2> design(DesignDispatchRequestV2 request) {
        Objects.requireNonNull(request, "request");
        CancellationToken cancellationToken = request.cancellationToken().orElse(() -> false);
        UUID jobId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        return executors.supplyIo(() -> runDesign(request, jobId, startedAt, cancellationToken))
                .whenComplete((ignored, failure) -> {
                    if (failure != null && !(unwrap(failure) instanceof CancellationException)) {
                        // no job repository in v2 design service; failures propagate to caller
                    }
                });
    }

    private DesignArtifactsV2 runDesign(
            DesignDispatchRequestV2 request,
            UUID jobId,
            Instant startedAt,
            CancellationToken cancellationToken
    ) {
        cancellationToken.throwIfCancellationRequested();
        ProviderCapabilityDescriptorV2 descriptor = DesignCapabilityNegotiatorV2.negotiate(
                request.intentContractVersion(),
                request.path(),
                request.modelOrIntentPath(),
                request.capabilities());
        GenerationRequestV2 generationRequest = readRequest(request.requestPath());
        String requestChecksum = requestChecksum(request.requestPath());
        return switch (request.path()) {
            case OPENAI, ANTHROPIC -> publishProviderResult(
                    request, jobId, startedAt, generationRequest, requestChecksum, descriptor, cancellationToken);
            case IMPORT, FIXTURE -> publishBuiltInProviderResult(
                    request, jobId, startedAt, generationRequest, requestChecksum, descriptor, cancellationToken);
            case MANUAL_CONSTRAINT -> publishManualResult(
                    request, jobId, startedAt, generationRequest, requestChecksum, descriptor, cancellationToken);
            case REFERENCE_IMAGE_DRAFT -> publishSoftDraftResult(
                    request, jobId, startedAt, generationRequest, requestChecksum, descriptor, cancellationToken);
        };
    }

    private DesignArtifactsV2 publishProviderResult(
            DesignDispatchRequestV2 request,
            UUID jobId,
            Instant startedAt,
            GenerationRequestV2 generationRequest,
            String requestChecksum,
            ProviderCapabilityDescriptorV2 descriptor,
            CancellationToken cancellationToken
    ) {
        if (providerFactory == null) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.INVALID_REQUEST,
                    "provider factory is required for HTTP design paths");
        }
        TerrainDesignProviderV2 provider = providerFactory.apply(request.path(), request.modelOrIntentPath());
        if (provider == null) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.UNSUPPORTED_PROVIDER,
                    "provider factory returned null");
        }
        TerrainDesignResultV2 result = awaitProvider(
                provider.design(buildProviderRequest(request, generationRequest, jobId)),
                cancellationToken);
        return publishStructured(
                request, jobId, startedAt, generationRequest, requestChecksum, result, Optional.empty());
    }

    private DesignArtifactsV2 publishBuiltInProviderResult(
            DesignDispatchRequestV2 request,
            UUID jobId,
            Instant startedAt,
            GenerationRequestV2 generationRequest,
            String requestChecksum,
            ProviderCapabilityDescriptorV2 descriptor,
            CancellationToken cancellationToken
    ) {
        Path intentPath = resolveModelPath(request.requestPath(), request.modelOrIntentPath());
        TerrainDesignProviderV2 provider = switch (request.path()) {
            case IMPORT -> new ImportedJsonTerrainDesignProviderV2(executors, intentPath, codec, clock);
            case FIXTURE -> new FixtureTerrainDesignProviderV2(readIntent(intentPath), clock);
            default -> throw new IllegalStateException("unexpected built-in provider path");
        };
        TerrainDesignResultV2 result = awaitProvider(
                provider.design(buildProviderRequest(request, generationRequest, jobId)),
                cancellationToken);
        return publishStructured(
                request, jobId, startedAt, generationRequest, requestChecksum, result, Optional.empty());
    }

    private DesignArtifactsV2 publishManualResult(
            DesignDispatchRequestV2 request,
            UUID jobId,
            Instant startedAt,
            GenerationRequestV2 generationRequest,
            String requestChecksum,
            ProviderCapabilityDescriptorV2 descriptor,
            CancellationToken cancellationToken
    ) {
        Path draftIntentPath = request.draftIntentPath()
                .orElseThrow(() -> new DesignExceptionV2(
                        DesignFailureCodeV2.INVALID_REQUEST,
                        "manual constraint design requires draftIntentPath"));
        TerrainIntentV2 draftIntent = readIntent(draftIntentPath);
        Path bundleTarget = request.designsRoot()
                .resolve(generationRequest.requestId())
                .resolve(".constraint-" + jobId);
        try {
            ManualConstraintMapResultV2 prepared = manualService.prepareManual(
                    request.requestPath(),
                    generationRequest,
                    draftIntent,
                    bundleTarget,
                    cancellationToken);
            TerrainIntentV2 intent = withBindings(draftIntent, prepared.canonicalBindings());
            String intentChecksum = codec.terrainIntentChecksum(intent);
            Instant completedAt = clock.instant();
            TerrainDesignResultV2 result = new TerrainDesignResultV2(
                    intent,
                    "manual-constraint-v2",
                    descriptor.modelId(),
                    "manual-constraint-bundle-v1",
                    "manual-" + intentChecksum.substring(0, 24),
                    ProviderUsage.ZERO,
                    1,
                    completedAt,
                    sortedCapabilities(request.capabilities()),
                    descriptor.catalogVersion()
            );
            DesignAuditV2 audit = buildAudit(
                    jobId,
                    generationRequest.requestId(),
                    request.path(),
                    startedAt,
                    result,
                    requestChecksum,
                    intentChecksum);
            return publisher.publish(
                    request.requestPath(),
                    request.designsRoot(),
                    jobId,
                    generationRequest.requestId(),
                    startedAt,
                    intent,
                    audit,
                    Optional.empty());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private DesignArtifactsV2 publishSoftDraftResult(
            DesignDispatchRequestV2 request,
            UUID jobId,
            Instant startedAt,
            GenerationRequestV2 generationRequest,
            String requestChecksum,
            ProviderCapabilityDescriptorV2 descriptor,
            CancellationToken cancellationToken
    ) {
        SoftDraftPixelInputV2 draftInput = request.draftInput()
                .orElseThrow(() -> new DesignExceptionV2(
                        DesignFailureCodeV2.INVALID_REQUEST,
                        "reference image draft design requires draftInput"));
        Path intentPath = request.draftIntentPath()
                .orElseGet(() -> resolveModelPath(request.requestPath(), request.modelOrIntentPath()));
        TerrainIntentV2 intent = readIntent(intentPath);
        if (!intent.mapReferences().isEmpty()) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.HARD_PROMOTION_FORBIDDEN,
                    "reference image draft path must not carry HARD mapReferences");
        }
        ReferenceImageSoftDraftServiceV2.ExtractionResult extraction = softDraftService.extract(
                draftInput.width(),
                draftInput.length(),
                draftInput.argbPixels(),
                draftInput.sourceChecksum(),
                ImageMaskExtractionLimitsV2.defaults(),
                cancellationToken,
                null);
        softDraftService.assertSoftOnly(extraction.evidence());
        String intentChecksum = codec.terrainIntentChecksum(intent);
        Instant completedAt = clock.instant();
        TerrainDesignResultV2 result = new TerrainDesignResultV2(
                intent,
                "reference-image-soft-draft-v2",
                descriptor.modelId(),
                extraction.evidence().algorithmVersion(),
                "draft-" + extraction.evidence().semanticChecksum().substring(0, 24),
                ProviderUsage.ZERO,
                1,
                completedAt,
                sortedCapabilities(request.capabilities()),
                descriptor.catalogVersion()
        );
        DesignAuditV2 audit = buildAudit(
                jobId,
                generationRequest.requestId(),
                request.path(),
                startedAt,
                result,
                requestChecksum,
                intentChecksum);
        try {
            return publisher.publish(
                    request.requestPath(),
                    request.designsRoot(),
                    jobId,
                    generationRequest.requestId(),
                    startedAt,
                    intent,
                    audit,
                    Optional.of(extraction.evidence()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private DesignArtifactsV2 publishStructured(
            DesignDispatchRequestV2 request,
            UUID jobId,
            Instant startedAt,
            GenerationRequestV2 generationRequest,
            String requestChecksum,
            TerrainDesignResultV2 result,
            Optional<ImageDraftEvidenceV2> draftEvidence
    ) {
        if (!generationRequest.requestId().equals(result.intent().intentId())) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.INVALID_RESPONSE,
                    "provider intentId must match generation requestId");
        }
        String intentChecksum = codec.terrainIntentChecksum(result.intent());
        DesignAuditV2 audit = buildAudit(
                jobId,
                generationRequest.requestId(),
                request.path(),
                startedAt,
                result,
                requestChecksum,
                intentChecksum);
        try {
            return publisher.publish(
                    request.requestPath(),
                    request.designsRoot(),
                    jobId,
                    generationRequest.requestId(),
                    startedAt,
                    result.intent(),
                    audit,
                    draftEvidence);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private TerrainDesignRequestV2 buildProviderRequest(
            DesignDispatchRequestV2 request,
            GenerationRequestV2 generationRequest,
            UUID jobId
    ) {
        return new TerrainDesignRequestV2(
                request.intentContractVersion(),
                request.path(),
                request.capabilities(),
                generationRequest,
                List.of(),
                jobId
        );
    }

    private static DesignAuditV2 buildAudit(
            UUID jobId,
            String requestId,
            DesignPathKindV2 pathKind,
            Instant startedAt,
            TerrainDesignResultV2 result,
            String requestChecksum,
            String intentChecksum
    ) {
        return new DesignAuditV2(
                DesignAuditV2.VERSION,
                jobId,
                requestId,
                pathKind,
                result.providerId(),
                result.modelId(),
                result.promptVersion(),
                result.responseId(),
                result.usage(),
                result.attempts(),
                requestChecksum,
                intentChecksum,
                sortedCapabilities(result.negotiatedCapabilities()),
                result.capabilityCatalogVersion(),
                startedAt,
                result.createdAt()
        );
    }

    private static Set<DesignCapabilityV2> sortedCapabilities(Set<DesignCapabilityV2> capabilities) {
        return Set.copyOf(new TreeSet<>(capabilities));
    }

    private GenerationRequestV2 readRequest(Path requestPath) {
        try {
            return codec.readGenerationRequest(requestPath);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String requestChecksum(Path requestPath) {
        try {
            return Sha256.file(requestPath);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private TerrainIntentV2 readIntent(Path intentPath) {
        try {
            return codec.readTerrainIntent(intentPath);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path resolveModelPath(Path requestPath, String modelOrIntentPath) {
        Path candidate = Path.of(modelOrIntentPath);
        if (candidate.isAbsolute() || modelOrIntentPath.contains("\\") || modelOrIntentPath.contains("..")) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.PATH_SECURITY,
                    "modelOrIntentPath must be a safe relative import path");
        }
        Path parent = requestPath.toAbsolutePath().normalize().getParent();
        Path resolved = parent.resolve(candidate).normalize();
        if (!resolved.startsWith(parent)) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.PATH_SECURITY,
                    "modelOrIntentPath escapes the request directory");
        }
        return resolved;
    }

    private static TerrainIntentV2 withBindings(
            TerrainIntentV2 source,
            List<TerrainIntentV2.ConstraintMapBinding> bindings
    ) {
        return new TerrainIntentV2(
                source.intentVersion(),
                source.intentId(),
                source.theme(),
                source.coordinateSystem(),
                source.features(),
                source.relations(),
                source.constraints(),
                source.environment(),
                bindings,
                source.structures(),
                source.provenance());
    }

    private static TerrainDesignResultV2 awaitProvider(
            CompletableFuture<TerrainDesignResultV2> future,
            CancellationToken cancellationToken
    ) {
        cancellationToken.throwIfCancellationRequested();
        try {
            TerrainDesignResultV2 result = future.join();
            cancellationToken.throwIfCancellationRequested();
            return result;
        } catch (CompletionException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof DesignExceptionV2 designException) {
                throw designException;
            }
            if (cause instanceof CancellationException cancellation) {
                throw cancellation;
            }
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.INVALID_RESPONSE,
                    "provider design failed",
                    cause);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof UncheckedIOException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
