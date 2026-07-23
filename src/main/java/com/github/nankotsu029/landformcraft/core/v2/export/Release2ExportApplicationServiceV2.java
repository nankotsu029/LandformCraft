package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerificationV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfacePublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSurfaceVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Production Release 2 export path (V2-12-02).
 *
 * <p>This is the officially supported entry point from CLI and Paper adapters: it takes a sealed
 * generation request plus a design-stage terrain intent, runs generation, field-only validation and
 * diagnostic previews, publishes one {@code surface-2_5d} Release through
 * {@link ReleaseSurfacePublisherV2} (staging → strict read-back → atomic publish), and only returns
 * after the published container passes the strict capability verifier and the placement eligibility
 * gate. It never mutates a world and never enables a capability the Release does not carry.</p>
 *
 * <p>Command routing for CLI and Paper is out of scope here and lands in {@code V2-12-03}.</p>
 */
public final class Release2ExportApplicationServiceV2 {
    private final GenerationExecutors executors;
    private final LandformV2DataCodec codec;
    private final ProductionDispatchRegistryV2 dispatchRegistry;
    private final HardPreflightGateV2 preflightGate = new HardPreflightGateV2();
    private final ReleaseSurfacePublisherV2 publisher;
    private final ReleaseSurfaceVerifierV2 verifier;
    private final ReleasePlacementEligibilityVerifierV2 eligibilityVerifier;

    public Release2ExportApplicationServiceV2(GenerationExecutors executors) {
        this(executors, new ReleaseSurfacePublisherV2(), new ReleaseSurfaceVerifierV2(),
                new ReleasePlacementEligibilityVerifierV2(), ProductionDispatchRegistryV2.builtIn());
    }

    public Release2ExportApplicationServiceV2(
            GenerationExecutors executors,
            ReleaseSurfacePublisherV2 publisher,
            ReleaseSurfaceVerifierV2 verifier,
            ReleasePlacementEligibilityVerifierV2 eligibilityVerifier
    ) {
        this(executors, publisher, verifier, eligibilityVerifier, ProductionDispatchRegistryV2.builtIn());
    }

    Release2ExportApplicationServiceV2(
            GenerationExecutors executors,
            ReleaseSurfacePublisherV2 publisher,
            ReleaseSurfaceVerifierV2 verifier,
            ReleasePlacementEligibilityVerifierV2 eligibilityVerifier,
            ProductionDispatchRegistryV2 dispatchRegistry
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.codec = new LandformV2DataCodec();
        this.dispatchRegistry = Objects.requireNonNull(dispatchRegistry, "dispatchRegistry");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.eligibilityVerifier = Objects.requireNonNull(eligibilityVerifier, "eligibilityVerifier");
    }

    public boolean isRelease2Path() {
        return true;
    }

    /** Asynchronous entry point. Heavy CPU and artifact I/O never run on the caller's thread. */
    public CompletableFuture<Release2ExportResultV2> export(Release2ExportRequestV2 request) {
        Objects.requireNonNull(request, "request");
        return executors.supplyIo(() -> {
            try {
                return exportNow(request);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    /** Synchronous entry point for callers that already own a worker thread. */
    public Release2ExportResultV2 exportNow(Release2ExportRequestV2 request) throws IOException {
        Objects.requireNonNull(request, "request");
        CancellationToken token = request.cancellationToken().orElse(() -> false);
        token.throwIfCancellationRequested();
        request.budget().requireFreeDisk(request.workRoot());

        GenerationRequestV2 generationRequest = codec.readGenerationRequest(request.generationRequest());
        TerrainIntentV2 intent = codec.readTerrainIntent(request.terrainIntent());
        ProductionDispatchRegistryV2.DispatchSelection dispatch;
        try {
            dispatch = dispatchRegistry.select(intent);
        } catch (IllegalArgumentException exception) {
            throw new IOException("production dispatch rejected terrain intent: " + exception.getMessage(), exception);
        }
        preflightGate.requireHonorable(generationRequest, request.generationRequest(), intent, token);
        ProductionExportPipelineV2.GeneratedSurface generated = dispatch.pipeline().generate(
                generationRequest, request.generationRequest(), intent, request.baseline(),
                request.workRoot(), request.budget(), token);

        SurfaceReleaseSourceV2 source = generated.source();
        ReleaseSurfaceArtifactsV2 published = publisher.publish(
                request.exportsRoot(), request.releaseId(), source, request.createZip(), token);

        ReleaseCoreVerificationV2 directory = verifier.verify(published.releaseDirectory(), token);
        if (!directory.manifest().requiredCapabilities().equals(dispatch.plan().requiredCapabilities())) {
            throw new IOException("published Release 2 capability set differs from production dispatch plan");
        }
        if (published.zip().isPresent()) {
            ReleaseCoreVerificationV2 zip = verifier.verify(published.zip().get(), token);
            if (!directory.manifest().equals(zip.manifest())) {
                throw new IOException("published Release 2 directory and ZIP manifests differ");
            }
        }
        var eligibility = eligibilityVerifier.verifyEligible(published.releaseDirectory(), token);

        List<String> tileIds = source.tiles().stream()
                .map(SurfaceReleaseSourceV2.TileSource::tileId)
                .toList();
        return new Release2ExportResultV2(
                published.releaseId(),
                published.releaseDirectory(),
                published.zip(),
                generated.blueprint().canonicalChecksum(),
                directory.manifest().canonicalChecksum(),
                directory.manifest().requiredCapabilities(),
                tileIds,
                eligibility,
                generated.intentContributionCoverage(),
                generated.warnings());
    }
}
