package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerificationV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSparseVolumeArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSparseVolumePublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseSparseVolumeVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.SparseVolumeReleaseSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Production Release 2 {@code sparse-volume} export path (V2-15-08).
 *
 * <p>Wires the shared ordered-CSG volume overlay onto the environment／hydrology／surface production
 * chain without promoting individual volume FeatureKinds. The existing publisher owns staging,
 * strict directory／ZIP read-back, and atomic publication.</p>
 */
public final class Release2SparseVolumeExportApplicationServiceV2 {
    private final GenerationExecutors executors;
    private final LandformV2DataCodec codec;
    private final ProductionDispatchRegistryV2 dispatchRegistry;
    private final HardPreflightGateV2 preflightGate = new HardPreflightGateV2();
    private final ReleaseSparseVolumePublisherV2 publisher;
    private final ReleaseSparseVolumeVerifierV2 verifier;
    private final ReleasePlacementEligibilityVerifierV2 eligibilityVerifier;

    public Release2SparseVolumeExportApplicationServiceV2(GenerationExecutors executors) {
        this(executors, new ReleaseSparseVolumePublisherV2(), new ReleaseSparseVolumeVerifierV2(),
                new ReleasePlacementEligibilityVerifierV2(), ProductionDispatchRegistryV2.builtIn());
    }

    public Release2SparseVolumeExportApplicationServiceV2(
            GenerationExecutors executors,
            ReleaseSparseVolumePublisherV2 publisher,
            ReleaseSparseVolumeVerifierV2 verifier,
            ReleasePlacementEligibilityVerifierV2 eligibilityVerifier
    ) {
        this(executors, publisher, verifier, eligibilityVerifier, ProductionDispatchRegistryV2.builtIn());
    }

    Release2SparseVolumeExportApplicationServiceV2(
            GenerationExecutors executors,
            ReleaseSparseVolumePublisherV2 publisher,
            ReleaseSparseVolumeVerifierV2 verifier,
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
        ProductionDispatchRegistryV2.DispatchSelection dispatch;
        try {
            dispatch = dispatchRegistry.select(
                    intent, ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT);
        } catch (IllegalArgumentException exception) {
            throw new IOException("production sparse-volume dispatch rejected terrain intent: "
                    + exception.getMessage(), exception);
        }
        preflightGate.requireHonorable(generationRequest, request.generationRequest(), intent, token);
        ProductionExportPipelineV2.GeneratedSparseVolume generated =
                dispatch.pipeline().generateSparseVolume(
                        generationRequest,
                        request.generationRequest(),
                        intent,
                        request.baseline(),
                        request.workRoot(),
                        request.budget(),
                        token);

        SparseVolumeReleaseSourceV2 source = generated.source();
        ReleaseSparseVolumeArtifactsV2 published = publisher.publish(
                request.exportsRoot(), request.releaseId(), source, request.createZip(), token);

        ReleaseCoreVerificationV2 directory = verifier.verify(published.releaseDirectory(), token);
        if (!directory.manifest().requiredCapabilities().equals(dispatch.plan().requiredCapabilities())) {
            throw new IOException("published sparse-volume Release capability set differs from dispatch plan");
        }
        if (!directory.manifest().requiredCapabilities()
                .equals(ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT)) {
            throw new IOException("published sparse-volume Release must require the complete capability prefix");
        }
        if (published.zip().isPresent()) {
            ReleaseCoreVerificationV2 zip = verifier.verify(published.zip().get(), token);
            if (!directory.manifest().equals(zip.manifest())) {
                throw new IOException("published sparse-volume Release directory and ZIP manifests differ");
            }
        }
        var eligibility = eligibilityVerifier.verifyEligible(published.releaseDirectory(), token);

        List<String> tileIds = source.tiles().stream()
                .map(SparseVolumeReleaseSourceV2.TileSource::tileId)
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
                Optional.empty(),
                List.of());
    }
}
