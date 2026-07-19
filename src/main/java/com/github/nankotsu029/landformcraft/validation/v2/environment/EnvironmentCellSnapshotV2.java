package com.github.nankotsu029.landformcraft.validation.v2.environment;

import java.util.Objects;

/**
 * Public, generator-independent cell snapshot for V2-4-13 environment validation.
 * Callers supply values from sealed plans／resolvers／samplers; generators are never imported.
 */
public record EnvironmentCellSnapshotV2(
        int temperatureRaw,
        int moistureRaw,
        int wetnessRaw,
        int salinityRaw,
        int hydroperiodRaw,
        int snowCoverRaw,
        int habitatCode,
        int materialClassCode,
        int featureMaterialClassCode,
        int lithologyCode,
        int wetlandMask,
        int openWaterGap,
        int substrateWet,
        int reefMask,
        int reefDepthRaw,
        int islandMask,
        int canyonMask,
        int wallHeightMillionths
) {
    public EnvironmentCellSnapshotV2 {
        requireRaw(temperatureRaw, "temperatureRaw");
        requireRaw(moistureRaw, "moistureRaw");
        requireRaw(wetnessRaw, "wetnessRaw");
        requireRaw(salinityRaw, "salinityRaw");
        requireRaw(hydroperiodRaw, "hydroperiodRaw");
        requireRaw(snowCoverRaw, "snowCoverRaw");
        if (habitatCode < 0 || habitatCode > 255
                || materialClassCode < 0 || materialClassCode > 255
                || featureMaterialClassCode < 0 || featureMaterialClassCode > 255
                || lithologyCode < 0 || lithologyCode > 65_535
                || wetlandMask < 0 || wetlandMask > 1
                || openWaterGap < 0 || openWaterGap > 1
                || substrateWet < 0 || substrateWet > 1
                || reefMask < 0 || reefMask > 1
                || reefDepthRaw < 0 || reefDepthRaw > 1_000
                || islandMask < 0 || islandMask > 1
                || canyonMask < 0 || canyonMask > 1
                || wallHeightMillionths < 0) {
            throw new IllegalArgumentException("environment cell snapshot is out of range");
        }
    }

    private static void requireRaw(int value, String name) {
        if (value < 0 || value > 1_000) {
            throw new IllegalArgumentException(name + " must be in 0..1000");
        }
    }
}
