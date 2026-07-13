package com.github.nankotsu029.landformcraft.validation;

import com.github.nankotsu029.landformcraft.ai.spi.PreparedReferenceImage;
import com.github.nankotsu029.landformcraft.model.ImageInputEvidence;

import java.util.List;
import java.util.Objects;

public record PreparedImageInputs(
        List<PreparedReferenceImage> images,
        ImageInputEvidence evidence
) {
    public PreparedImageInputs {
        Objects.requireNonNull(images, "images");
        images = List.copyOf(images);
        Objects.requireNonNull(evidence, "evidence");
    }
}
