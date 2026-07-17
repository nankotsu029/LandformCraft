package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.HydrologyValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import com.github.nankotsu029.landformcraft.preview.v2.HydrologyPreviewIndexCodecV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Semantic verifier for Release 2 {@code hydrology-plan}, which always depends on {@code surface-2_5d}.
 */
final class HydrologyReleaseCapabilityVerifierV2 {
    static final String PLAN_TYPE = "hydrology-plan-v2";
    static final String ROUTING_TYPE = "hydrology-routing-artifact-v2";
    static final String FIELD_GRID_TYPE = "hydrology-field-grid-v1";
    static final String RECONCILIATION_PLAN_TYPE = "hydrology-reconciliation-plan-v2";
    static final String RECONCILIATION_ARTIFACT_TYPE = "hydrology-reconciliation-artifact-v2";
    static final String VALIDATION_TYPE = "hydrology-validation-artifact-v2";
    static final String PREVIEW_INDEX_TYPE = "hydrology-preview-index-v2";
    static final String PREVIEW_PNG_TYPE = "hydrology-preview-png-v1";

    static final String PLAN_PATH = "hydrology/plan.json";
    static final String ROUTING_INDEX_PATH = "hydrology/routing/index.json";
    static final String RECONCILIATION_PLAN_PATH = "hydrology/reconciliation-plan.json";
    static final String RECONCILIATION_ARTIFACT_PATH = "hydrology/reconciliation-artifact.json";
    static final String VALIDATION_PATH = "hydrology/validation.json";
    static final String PREVIEW_INDEX_PATH = "hydrology/previews/index.json";

    private final SurfaceReleaseCapabilityVerifierV2 surfaceVerifier = new SurfaceReleaseCapabilityVerifierV2();
    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final HydrologyRoutingArtifactCodecV2 routingCodec = new HydrologyRoutingArtifactCodecV2();
    private final HydrologyReconciliationArtifactCodecV2 reconciliationCodec =
            new HydrologyReconciliationArtifactCodecV2();
    private final HydrologyValidationArtifactCodecV2 validationCodec = new HydrologyValidationArtifactCodecV2();
    private final HydrologyPreviewIndexCodecV2 previewCodec = new HydrologyPreviewIndexCodecV2();

    void verify(Path root, ReleaseManifestV2 manifest, CancellationToken cancellationToken) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (!manifest.requiredCapabilities().equals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE)) {
            throw new IOException("hydrology-plan Release must require hydrology-plan and surface-2_5d");
        }
        ReleaseCapabilityArtifactIndexV2 index = new ReleaseCapabilityArtifactIndexV2(manifest.artifacts());
        WorldBlueprintV2 blueprint = surfaceVerifier.verifySurfacePayload(root, index, cancellationToken);
        verifyHydrologyPayload(root, index, blueprint, cancellationToken);
        index.requireNoUnexpectedArtifacts();
    }

    private void verifyHydrologyPayload(
            Path root,
            ReleaseCapabilityArtifactIndexV2 index,
            WorldBlueprintV2 blueprint,
            CancellationToken cancellationToken
    ) throws IOException {
        ReleaseArtifactDescriptorV2 planDescriptor = index.singleton(PLAN_TYPE, PLAN_PATH);
        ReleaseArtifactDescriptorV2 routingDescriptor = index.singleton(ROUTING_TYPE, ROUTING_INDEX_PATH);
        ReleaseArtifactDescriptorV2 reconciliationPlanDescriptor =
                index.singleton(RECONCILIATION_PLAN_TYPE, RECONCILIATION_PLAN_PATH);
        ReleaseArtifactDescriptorV2 reconciliationArtifactDescriptor =
                index.singleton(RECONCILIATION_ARTIFACT_TYPE, RECONCILIATION_ARTIFACT_PATH);
        ReleaseArtifactDescriptorV2 validationDescriptor = index.singleton(VALIDATION_TYPE, VALIDATION_PATH);
        ReleaseArtifactDescriptorV2 previewIndexDescriptor = index.singleton(PREVIEW_INDEX_TYPE, PREVIEW_INDEX_PATH);

        cancellationToken.throwIfCancellationRequested();
        HydrologyPlanV2 plan = dataCodec.readHydrologyPlan(root.resolve(PLAN_PATH));
        requireSemantic(planDescriptor, plan.canonicalChecksum(), "hydrology plan");
        if (!plan.canonicalChecksum().equals(blueprint.hydrologyPlan().canonicalChecksum())
                || !plan.equals(blueprint.hydrologyPlan())) {
            throw new IOException("hydrology plan does not match the released Blueprint hydrology plan");
        }

        cancellationToken.throwIfCancellationRequested();
        HydrologyRoutingArtifactV2 routing = routingCodec.readAndVerify(
                root.resolve(ROUTING_INDEX_PATH), root.resolve("hydrology/routing"), cancellationToken);
        requireSemantic(routingDescriptor, routing.canonicalChecksum(), "hydrology routing artifact");
        if (!routing.sourceHydrologyPlanChecksum().equals(plan.canonicalChecksum())
                || routing.width() != blueprint.space().bounds().width()
                || routing.length() != blueprint.space().bounds().length()
                || !routing.fixedPriorChecksum().equals(plan.fixedPriors().priorChecksum())) {
            throw new IOException("hydrology routing artifact does not bind to the released plan and Blueprint");
        }
        for (var field : routing.fields()) {
            cancellationToken.throwIfCancellationRequested();
            ReleaseArtifactDescriptorV2 descriptor = index.requirePath(
                    "hydrology/routing/" + field.relativePath(), FIELD_GRID_TYPE);
            requireSemantic(descriptor, field.semanticChecksum(), "hydrology field " + field.definition().fieldId());
            if (!descriptor.artifactChecksum().equals(field.artifactChecksum())) {
                throw new IOException("hydrology field artifact checksum differs from its routing descriptor");
            }
        }
        if (index.ofType(FIELD_GRID_TYPE).size() != routing.fields().size()) {
            throw new IOException("hydrology Release field sidecar set is incomplete or has an extra entry");
        }

        cancellationToken.throwIfCancellationRequested();
        HydrologyReconciliationPlanV2 reconciliationPlan =
                dataCodec.readHydrologyReconciliationPlan(root.resolve(RECONCILIATION_PLAN_PATH));
        requireSemantic(reconciliationPlanDescriptor, reconciliationPlan.canonicalChecksum(),
                "hydrology reconciliation plan");
        if (!reconciliationPlan.sourceHydrologyPlanChecksum().equals(plan.canonicalChecksum())
                || !reconciliationPlan.equals(blueprint.hydrologyReconciliationPlan())) {
            throw new IOException("hydrology reconciliation plan does not match the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        HydrologyReconciliationArtifactV2 reconciliationArtifact =
                reconciliationCodec.read(root.resolve(RECONCILIATION_ARTIFACT_PATH));
        requireSemantic(reconciliationArtifactDescriptor, reconciliationArtifact.canonicalChecksum(),
                "hydrology reconciliation artifact");
        if (!reconciliationArtifact.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())
                || !reconciliationArtifact.sourcePlanChecksum().equals(reconciliationPlan.canonicalChecksum())) {
            throw new IOException("hydrology reconciliation artifact does not bind to Blueprint and plan");
        }

        cancellationToken.throwIfCancellationRequested();
        HydrologyValidationArtifactV2 validation = validationCodec.read(root.resolve(VALIDATION_PATH));
        requireSemantic(validationDescriptor, validation.canonicalChecksum(), "hydrology validation artifact");
        if (!validation.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())
                || !validation.report().passesHardValidation()) {
            throw new IOException("hydrology validation artifact does not validate the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        HydrologyPreviewIndexV2 previews = previewCodec.readAndVerify(
                root.resolve(PREVIEW_INDEX_PATH), root.resolve("hydrology/previews"), cancellationToken);
        requireSemantic(previewIndexDescriptor, previews.canonicalChecksum(), "hydrology preview index");
        if (!previews.sourceBlueprintChecksum().equals(blueprint.canonicalChecksum())
                || previews.width() != blueprint.space().bounds().width()
                || previews.length() != blueprint.space().bounds().length()) {
            throw new IOException("hydrology previews do not bind to the released Blueprint dimensions");
        }
        for (HydrologyPreviewIndexV2.Layer layer : previews.layers()) {
            cancellationToken.throwIfCancellationRequested();
            ReleaseArtifactDescriptorV2 descriptor = index.requirePath(
                    "hydrology/previews/" + layer.path(), PREVIEW_PNG_TYPE);
            requireSemantic(descriptor, layer.sha256(), "hydrology preview layer " + layer.layerId());
            if (descriptor.byteLength() != layer.byteLength() || !descriptor.artifactChecksum().equals(layer.sha256())) {
                throw new IOException("hydrology preview layer manifest binding differs: " + layer.path());
            }
        }
        if (index.ofType(PREVIEW_PNG_TYPE).size() != previews.layers().size()) {
            throw new IOException("hydrology Release preview layer set is incomplete or has an extra entry");
        }
    }

    private static void requireSemantic(
            ReleaseArtifactDescriptorV2 descriptor, String expected, String description
    ) throws IOException {
        if (!descriptor.semanticChecksum().equals(expected)) {
            throw new IOException("hydrology Release semantic checksum differs for " + description);
        }
    }
}
