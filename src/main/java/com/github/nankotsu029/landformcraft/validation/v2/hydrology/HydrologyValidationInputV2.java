package com.github.nankotsu029.landformcraft.validation.v2.hydrology;

import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;

import java.util.Objects;
import java.util.Optional;

/** Frozen Blueprint plus independently sampled hydrology fields and optional reconciliation evidence. */
public record HydrologyValidationInputV2(
        WorldBlueprintV2 blueprint,
        HydrologyFieldSamplerV2 fields,
        HydrologyReconciliationArtifactV2 reconciliationArtifact
) {
    public static final int NO_DATA = Integer.MIN_VALUE;

    public HydrologyValidationInputV2 {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(fields, "fields");
        int width = blueprint.space().bounds().width();
        int length = blueprint.space().bounds().length();
        if (fields.width() != width || fields.length() != length) {
            throw new IllegalArgumentException("hydrology validation field dimensions do not match Blueprint bounds");
        }
    }

    public HydrologyValidationInputV2(WorldBlueprintV2 blueprint, HydrologyFieldSamplerV2 fields) {
        this(blueprint, fields, null);
    }

    public Optional<HydrologyReconciliationArtifactV2> reconciliation() {
        return Optional.ofNullable(reconciliationArtifact);
    }
}
