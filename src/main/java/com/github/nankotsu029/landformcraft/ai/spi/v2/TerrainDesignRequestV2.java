package com.github.nankotsu029.landformcraft.ai.spi.v2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;

import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportSurfaceV2;

import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Versioned provider request. Intent contract version is explicit; there is no auto-upgrade.
 *
 * <p>{@code supportSurface} (V2-19-08) states which FeatureKinds the current production dispatch
 * registry can actually route, so a provider is told the reachable set before it designs rather than
 * discovering it at export time. It is advisory: nothing here narrows the historic 60-kind Schema a
 * provider may return.</p>
 */
public record TerrainDesignRequestV2(
        int intentContractVersion,
        DesignPathKindV2 path,
        Set<DesignCapabilityV2> requestedCapabilities,
        GenerationRequestV2 generationRequest,
        List<PreparedReferenceImageV2> images,
        UUID operationId,
        DesignSupportSurfaceV2 supportSurface
) {
    public static final long MAX_TOTAL_IMAGE_BYTES = 16L * 1024L * 1024L;

    public TerrainDesignRequestV2 {
        if (intentContractVersion != ProviderCapabilityCatalogV2.INTENT_CONTRACT_VERSION) {
            throw new IllegalArgumentException("intentContractVersion must be exactly "
                    + ProviderCapabilityCatalogV2.INTENT_CONTRACT_VERSION);
        }
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(requestedCapabilities, "requestedCapabilities");
        requestedCapabilities = Set.copyOf(requestedCapabilities);
        Objects.requireNonNull(generationRequest, "generationRequest");
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(supportSurface, "supportSurface");
        images = List.copyOf(images);
        if (images.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("images must not contain null elements");
        }
        if (generationRequest.referenceImages().size() != images.size()) {
            throw new IllegalArgumentException(
                    "every declared reference image must have exactly one verified image handle");
        }
        long totalImageBytes = 0L;
        for (int index = 0; index < images.size(); index++) {
            var declared = generationRequest.referenceImages().get(index);
            var prepared = images.get(index);
            if (prepared.index() != index
                    || !prepared.sourceFile().equals(declared.file())
                    || prepared.role() != declared.role()) {
                throw new IllegalArgumentException(
                        "verified image handles must match request order, path, and role");
            }
            totalImageBytes = Math.addExact(totalImageBytes, prepared.content().length);
            if (totalImageBytes > MAX_TOTAL_IMAGE_BYTES) {
                throw new IllegalArgumentException("verified image bytes exceed the provider submission limit");
            }
        }
    }
}
