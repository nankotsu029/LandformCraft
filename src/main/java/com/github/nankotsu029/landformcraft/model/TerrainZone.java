package com.github.nankotsu029.landformcraft.model;

import java.util.Objects;

public record TerrainZone(String id, TerrainZoneType type, PreferredArea preferredArea, double areaShare) {
    public TerrainZone {
        id = ModelValidation.requireSlug(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(preferredArea, "preferredArea");
        areaShare = ModelValidation.requireUnitInterval(areaShare, "areaShare");
        if (areaShare == 0.0) {
            throw new IllegalArgumentException("areaShare must be greater than zero");
        }
    }
}
