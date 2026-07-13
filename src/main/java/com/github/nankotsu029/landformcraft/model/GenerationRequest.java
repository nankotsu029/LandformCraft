package com.github.nankotsu029.landformcraft.model;

import java.util.List;
import java.util.Objects;

public record GenerationRequest(
        int schemaVersion,
        String requestId,
        GenerationBounds bounds,
        String prompt,
        List<ReferenceImage> images,
        GenerationOptions generation,
        OutputOptions output
) {
    public static final int MAX_PROMPT_LENGTH = 20_000;
    public static final int MAX_REFERENCE_IMAGES = 16;

    public GenerationRequest {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        requestId = ModelValidation.requireSlug(requestId, "requestId");
        Objects.requireNonNull(bounds, "bounds");
        prompt = ModelValidation.requireNonBlank(prompt, "prompt", MAX_PROMPT_LENGTH);
        images = ModelValidation.immutableList(images, "images", MAX_REFERENCE_IMAGES);
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(output, "output");
    }
}
