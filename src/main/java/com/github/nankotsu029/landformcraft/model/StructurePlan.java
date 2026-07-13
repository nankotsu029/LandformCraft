package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

public record StructurePlan(
        String assetId,
        StructureType type,
        int anchorX,
        int anchorY,
        int anchorZ,
        QuarterTurn rotation,
        int sizeX,
        int sizeY,
        int sizeZ
) {
    public StructurePlan {
        assetId = ModelValidation.requireSlug(assetId, "assetId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rotation, "rotation");
        if (anchorX < 0 || anchorZ < 0 || sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("invalid structure anchor or size");
        }
    }
}
