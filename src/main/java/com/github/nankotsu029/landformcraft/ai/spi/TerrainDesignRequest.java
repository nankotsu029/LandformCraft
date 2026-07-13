package com.github.nankotsu029.landformcraft.ai.spi;

import com.github.nankotsu029.landformcraft.model.GenerationRequest;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

public record TerrainDesignRequest(
        GenerationRequest generationRequest,
        List<PreparedReferenceImage> images,
        UUID operationId
) {
    public static final long MAX_TOTAL_IMAGE_BYTES = 16L * 1024L * 1024L;

    public TerrainDesignRequest(GenerationRequest generationRequest) {
        this(generationRequest, List.of(), UUID.randomUUID());
    }

    public TerrainDesignRequest(GenerationRequest generationRequest, List<PreparedReferenceImage> images) {
        this(generationRequest, images, UUID.randomUUID());
    }

    public TerrainDesignRequest {
        Objects.requireNonNull(generationRequest, "generationRequest");
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(operationId, "operationId");
        images = List.copyOf(images);
        if (images.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("images must not contain null elements");
        }
        if (generationRequest.images().size() != images.size()) {
            throw new IllegalArgumentException(
                    "every declared image must have exactly one verified image handle"
            );
        }
        long totalImageBytes = 0L;
        for (int index = 0; index < images.size(); index++) {
            var declared = generationRequest.images().get(index);
            var prepared = images.get(index);
            if (prepared.index() != index || !prepared.sourceFile().equals(declared.file())
                    || prepared.role() != declared.role()) {
                throw new IllegalArgumentException("verified image handles must match request order, path, and role");
            }
            totalImageBytes = Math.addExact(totalImageBytes, prepared.content().length);
            if (totalImageBytes > MAX_TOTAL_IMAGE_BYTES) {
                throw new IllegalArgumentException("verified image bytes exceed the provider submission limit");
            }
        }
    }
}
