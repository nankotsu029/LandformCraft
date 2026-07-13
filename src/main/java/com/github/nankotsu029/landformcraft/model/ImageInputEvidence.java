package com.github.nankotsu029.landformcraft.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ImageInputEvidence(
        int schemaVersion,
        String requestId,
        String normalizationVersion,
        String providerId,
        String providerResponseId,
        String promptVersion,
        List<ImageEvidenceEntry> images,
        List<ImageConsistencyCheck> consistencyChecks,
        Instant createdAt
) {
    public static final String NORMALIZATION_VERSION = "image-normalization-v1";

    public ImageInputEvidence {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        requestId = ModelValidation.requireSlug(requestId, "requestId");
        normalizationVersion = ModelValidation.requireNonBlank(normalizationVersion, "normalizationVersion", 128);
        if (!NORMALIZATION_VERSION.equals(normalizationVersion)) {
            throw new IllegalArgumentException("unsupported image normalization version");
        }
        providerId = ModelValidation.requireNonBlank(providerId, "providerId", 128);
        providerResponseId = ModelValidation.requireNonBlank(providerResponseId, "providerResponseId", 256);
        promptVersion = ModelValidation.requireNonBlank(promptVersion, "promptVersion", 128);
        images = ModelValidation.immutableList(images, "images", 16);
        consistencyChecks = ModelValidation.immutableList(consistencyChecks, "consistencyChecks", 64);
        Objects.requireNonNull(createdAt, "createdAt");
        var indexes = new HashSet<Integer>();
        var imageIds = new HashSet<String>();
        var files = new HashSet<String>();
        var topDownFiles = new HashSet<String>();
        for (int index = 0; index < images.size(); index++) {
            ImageEvidenceEntry image = images.get(index);
            if (!indexes.add(image.index()) || !imageIds.add(image.imageId()) || !files.add(image.sourceFile())) {
                throw new IllegalArgumentException("image evidence indexes, IDs, and source files must be unique");
            }
            if (image.index() != index) {
                throw new IllegalArgumentException("image evidence indexes must be contiguous and ordered");
            }
            if (image.role() == ReferenceImageRole.TOP_DOWN_SKETCH) {
                topDownFiles.add(image.sourceFile());
            }
        }
        for (ImageConsistencyCheck check : consistencyChecks) {
            if (!topDownFiles.contains(check.sourceFile())) {
                throw new IllegalArgumentException("consistency checks must reference a TOP_DOWN_SKETCH image");
            }
        }
    }

    public ImageInputEvidence withProviderResult(
            String completedProviderId,
            String completedProviderResponseId,
            String completedPromptVersion,
            boolean providerSubmitted
    ) {
        return new ImageInputEvidence(
                schemaVersion, requestId, normalizationVersion, completedProviderId,
                completedProviderResponseId, completedPromptVersion,
                images.stream().map(image -> image.withProviderSubmitted(providerSubmitted)).toList(),
                consistencyChecks, createdAt
        );
    }

    public static ImageInputEvidence empty(String requestId, Instant createdAt) {
        return new ImageInputEvidence(
                1, requestId, NORMALIZATION_VERSION, "unresolved", "unresolved", "unresolved",
                List.of(), List.of(), createdAt
        );
    }
}
