package com.github.nankotsu029.landformcraft.validation;

import java.util.List;
import java.util.Objects;

public record LoadedImageInputs(List<LoadedReferenceImage> images) {
    public LoadedImageInputs {
        Objects.requireNonNull(images, "images");
        images = List.copyOf(images);
    }
}
