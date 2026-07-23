package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.HydrologyReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerificationV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseHydrologyArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseHydrologyPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseHydrologyVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Production Release 2 {@code hydrology-plan} export path (V2-15-06).
 *
 * <p>Wires the shared hydrology artifact set onto the existing coastal production feature routes
 * without promoting individual hydrology FeatureKinds. Publish uses
 * {@link ReleaseHydrologyPublisherV2} (staging → strict read-back → atomic publish) and requires
 * {@code ["hydrology-plan","surface-2_5d"]}.</p>
 */
public final class Release2HydrologyExportApplicationServiceV2 {
    private final GenerationExecutors executors;
    private final LandformV2DataCodec codec;
    private final ProductionDispatchRegistryV2 dispatchRegistry;
    private final HardPreflightGateV2 preflightGate = new HardPreflightGateV2();
    private final ReleaseHydrologyPublisherV2 publisher;
    private final ReleaseHydrologyVerifierV2 verifier;
    private final ReleasePlacementEligibilityVerifierV2 eligibilityVerifier;

    public Release2HydrologyExportApplicationServiceV2(GenerationExecutors executors) {
        this(executors, new ReleaseHydrologyPublisherV2(), new ReleaseHydrologyVerifierV2(),
                new ReleasePlacementEligibilityVerifierV2(), ProductionDispatchRegistryV2.builtIn());
    }

    public Release2HydrologyExportApplicationServiceV2(
            GenerationExecutors executors,
            ReleaseHydrologyPublisherV2 publisher,
            ReleaseHydrologyVerifierV2 verifier,
            ReleasePlacementEligibilityVerifierV2 eligibilityVerifier
    ) {
        this(executors, publisher, verifier, eligibilityVerifier, ProductionDispatchRegistryV2.builtIn());
    }

    Release2HydrologyExportApplicationServiceV2(
            GenerationExecutors executors,
            ReleaseHydrologyPublisherV2 publisher,
            ReleaseHydrologyVerifierV2 verifier,
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
            dispatch = dispatchRegistry.select(intent, ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE);
        } catch (IllegalArgumentException exception) {
            throw new IOException("production hydrology dispatch rejected terrain intent: "
                    + exception.getMessage(), exception);
        }
        preflightGate.requireHonorable(generationRequest, request.generationRequest(), intent, token);
        ProductionExportPipelineV2.GeneratedHydrology generated = dispatch.pipeline().generateHydrology(
                generationRequest, request.generationRequest(), intent, request.baseline(),
                request.workRoot(), request.budget(), token);

        HydrologyReleaseSourceV2 source = generated.source();
        ReleaseHydrologyArtifactsV2 published = publisher.publish(
                request.exportsRoot(), request.releaseId(), source, request.createZip(), token);

        ReleaseCoreVerificationV2 directory = verifier.verify(published.releaseDirectory(), token);
        if (!directory.manifest().requiredCapabilities().equals(dispatch.plan().requiredCapabilities())) {
            throw new IOException("published hydrology Release capability set differs from dispatch plan");
        }
        if (!directory.manifest().requiredCapabilities().equals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE)) {
            throw new IOException("published hydrology Release must require hydrology-plan and surface-2_5d");
        }
        if (published.zip().isPresent()) {
            ReleaseCoreVerificationV2 zip = verifier.verify(published.zip().get(), token);
            if (!directory.manifest().equals(zip.manifest())) {
                throw new IOException("published hydrology Release directory and ZIP manifests differ");
            }
        }
        var eligibility = eligibilityVerifier.verifyEligible(published.releaseDirectory(), token);

        List<String> tileIds = source.surface().tiles().stream()
                .map(tile -> tile.tileId())
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
