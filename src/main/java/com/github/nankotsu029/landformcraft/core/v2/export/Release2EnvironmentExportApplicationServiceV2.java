package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.EnvironmentReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerificationV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseEnvironmentArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseEnvironmentPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseEnvironmentVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleasePlacementEligibilityVerifierV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Production Release 2 {@code environment-fields} export path (V2-15-07).
 *
 * <p>Wires the shared environment artifact set onto the existing coastal production feature routes
 * without promoting individual environment FeatureKinds. Publish uses
 * {@link ReleaseEnvironmentPublisherV2} (staging → strict read-back → atomic publish) and requires
 * {@code ["environment-fields","hydrology-plan","surface-2_5d"]}.</p>
 */
public final class Release2EnvironmentExportApplicationServiceV2 {
    private final GenerationExecutors executors;
    private final LandformV2DataCodec codec;
    private final ProductionDispatchRegistryV2 dispatchRegistry;
    private final ReleaseEnvironmentPublisherV2 publisher;
    private final ReleaseEnvironmentVerifierV2 verifier;
    private final ReleasePlacementEligibilityVerifierV2 eligibilityVerifier;

    public Release2EnvironmentExportApplicationServiceV2(GenerationExecutors executors) {
        this(executors, new ReleaseEnvironmentPublisherV2(), new ReleaseEnvironmentVerifierV2(),
                new ReleasePlacementEligibilityVerifierV2(), ProductionDispatchRegistryV2.builtIn());
    }

    public Release2EnvironmentExportApplicationServiceV2(
            GenerationExecutors executors,
            ReleaseEnvironmentPublisherV2 publisher,
            ReleaseEnvironmentVerifierV2 verifier,
            ReleasePlacementEligibilityVerifierV2 eligibilityVerifier
    ) {
        this(executors, publisher, verifier, eligibilityVerifier, ProductionDispatchRegistryV2.builtIn());
    }

    Release2EnvironmentExportApplicationServiceV2(
            GenerationExecutors executors,
            ReleaseEnvironmentPublisherV2 publisher,
            ReleaseEnvironmentVerifierV2 verifier,
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
                    intent, ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE);
        } catch (IllegalArgumentException exception) {
            throw new IOException("production environment dispatch rejected terrain intent: "
                    + exception.getMessage(), exception);
        }
        ProductionExportPipelineV2.GeneratedEnvironment generated = dispatch.pipeline().generateEnvironment(
                generationRequest, intent, request.baseline(), request.workRoot(), request.budget(), token);

        EnvironmentReleaseSourceV2 source = generated.source();
        ReleaseEnvironmentArtifactsV2 published = publisher.publish(
                request.exportsRoot(), request.releaseId(), source, request.createZip(), token);

        ReleaseCoreVerificationV2 directory = verifier.verify(published.releaseDirectory(), token);
        if (!directory.manifest().requiredCapabilities().equals(dispatch.plan().requiredCapabilities())) {
            throw new IOException("published environment Release capability set differs from dispatch plan");
        }
        if (!directory.manifest().requiredCapabilities()
                .equals(ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE)) {
            throw new IOException(
                    "published environment Release must require environment-fields, hydrology-plan, and surface-2_5d");
        }
        if (published.zip().isPresent()) {
            ReleaseCoreVerificationV2 zip = verifier.verify(published.zip().get(), token);
            if (!directory.manifest().equals(zip.manifest())) {
                throw new IOException("published environment Release directory and ZIP manifests differ");
            }
        }
        var eligibility = eligibilityVerifier.verifyEligible(published.releaseDirectory(), token);

        List<String> tileIds = source.hydrology().surface().tiles().stream()
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
                eligibility);
    }
}
