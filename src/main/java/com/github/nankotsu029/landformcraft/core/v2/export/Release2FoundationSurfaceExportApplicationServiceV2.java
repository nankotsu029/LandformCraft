package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
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
 * Offline foundation → {@code surface-2_5d} export path (V2-15-09, ADR 0037).
 *
 * <p>Uses {@link FoundationSurfaceExportAdapterV2} to project plain／hill foundation merge output
 * into the existing surface Release exact set, then publishes through
 * {@link ReleaseSurfacePublisherV2}. It is deliberately outside
 * {@link ProductionDispatchRegistryV2} so it does not collide with the coastal
 * {@code ["surface-2_5d"]} pipeline. FeatureKinds stay non-production-connected.</p>
 */
public final class Release2FoundationSurfaceExportApplicationServiceV2 {
    private final GenerationExecutors executors;
    private final LandformV2DataCodec codec;
    private final FoundationSurfaceExportAdapterV2 adapter;
    private final ReleaseSurfacePublisherV2 publisher;
    private final ReleaseSurfaceVerifierV2 verifier;
    private final ReleasePlacementEligibilityVerifierV2 eligibilityVerifier;

    public Release2FoundationSurfaceExportApplicationServiceV2(GenerationExecutors executors) {
        this(executors, new FoundationSurfaceExportAdapterV2(), new ReleaseSurfacePublisherV2(),
                new ReleaseSurfaceVerifierV2(), new ReleasePlacementEligibilityVerifierV2());
    }

    public Release2FoundationSurfaceExportApplicationServiceV2(
            GenerationExecutors executors,
            FoundationSurfaceExportAdapterV2 adapter,
            ReleaseSurfacePublisherV2 publisher,
            ReleaseSurfaceVerifierV2 verifier,
            ReleasePlacementEligibilityVerifierV2 eligibilityVerifier
    ) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.codec = new LandformV2DataCodec();
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.eligibilityVerifier = Objects.requireNonNull(eligibilityVerifier, "eligibilityVerifier");
    }

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

    public Release2ExportResultV2 exportNow(Release2ExportRequestV2 request) throws IOException {
        Objects.requireNonNull(request, "request");
        CancellationToken token = request.cancellationToken().orElse(() -> false);
        token.throwIfCancellationRequested();
        request.budget().requireFreeDisk(request.workRoot());

        GenerationRequestV2 generationRequest = codec.readGenerationRequest(request.generationRequest());
        TerrainIntentV2 intent = codec.readTerrainIntent(request.terrainIntent());
        ProductionExportPipelineV2.GeneratedSurface generated = adapter.generatePlainHill(
                generationRequest, intent, request.workRoot(), request.budget(), token);

        SurfaceReleaseSourceV2 source = generated.source();
        ReleaseSurfaceArtifactsV2 published = publisher.publish(
                request.exportsRoot(), request.releaseId(), source, request.createZip(), token);

        ReleaseCoreVerificationV2 directory = verifier.verify(published.releaseDirectory(), token);
        if (!directory.manifest().requiredCapabilities().equals(
                List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D))) {
            throw new IOException("foundation surface Release must require exactly surface-2_5d");
        }
        if (published.zip().isPresent()) {
            ReleaseCoreVerificationV2 zip = verifier.verify(published.zip().get(), token);
            if (!directory.manifest().equals(zip.manifest())) {
                throw new IOException("published foundation surface directory and ZIP manifests differ");
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
                generated.maskFeatureReconcile(),
                generated.warnings());
    }
}
