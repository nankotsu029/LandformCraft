package com.github.nankotsu029.landformcraft.format.v2.release;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Trusted application-layer inputs used to assemble one portable {@code environment-fields} release.
 * Raw paths stop at this publisher boundary and are never stored in the Release manifest.
 */
public record EnvironmentReleaseSourceV2(
        HydrologyReleaseSourceV2 hydrology,
        Path geologyPlan,
        Path lithologyPlan,
        Path strataPlan,
        Path climatePlan,
        Path waterConditionPlan,
        Path snowPlan,
        Path materialProfilePlan,
        Path minecraftPalettePlan,
        Path ecologyPlan,
        Path featureMaterialProfilePlan,
        Path environmentValidationArtifact,
        Path environmentPreviewIndex,
        Path environmentPreviewRoot
) {
    public EnvironmentReleaseSourceV2 {
        Objects.requireNonNull(hydrology, "hydrology");
        Objects.requireNonNull(geologyPlan, "geologyPlan");
        Objects.requireNonNull(lithologyPlan, "lithologyPlan");
        Objects.requireNonNull(strataPlan, "strataPlan");
        Objects.requireNonNull(climatePlan, "climatePlan");
        Objects.requireNonNull(waterConditionPlan, "waterConditionPlan");
        Objects.requireNonNull(snowPlan, "snowPlan");
        Objects.requireNonNull(materialProfilePlan, "materialProfilePlan");
        Objects.requireNonNull(minecraftPalettePlan, "minecraftPalettePlan");
        Objects.requireNonNull(ecologyPlan, "ecologyPlan");
        Objects.requireNonNull(featureMaterialProfilePlan, "featureMaterialProfilePlan");
        Objects.requireNonNull(environmentValidationArtifact, "environmentValidationArtifact");
        Objects.requireNonNull(environmentPreviewIndex, "environmentPreviewIndex");
        Objects.requireNonNull(environmentPreviewRoot, "environmentPreviewRoot");
    }
}
