package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

public record ReferenceImage(String file, ReferenceImageRole role) {
    public ReferenceImage {
        file = ModelValidation.requireSafeRelativePath(file, "file");
        Objects.requireNonNull(role, "role");
    }
}
