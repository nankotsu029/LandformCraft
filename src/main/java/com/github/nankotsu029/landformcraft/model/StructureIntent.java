package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

public record StructureIntent(StructureType type, int count, String preferredZone) {
    public StructureIntent {
        Objects.requireNonNull(type, "type");
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("count must be between 1 and 64");
        }
        preferredZone = ModelValidation.requireSlug(preferredZone, "preferredZone");
    }
}
