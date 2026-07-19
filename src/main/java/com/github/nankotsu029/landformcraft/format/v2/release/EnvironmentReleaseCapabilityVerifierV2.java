package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.validation.EnvironmentValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.EnvironmentPreviewIndexCodecV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Semantic verifier for Release 2 {@code environment-fields}, which always depends on
 * {@code hydrology-plan} and {@code surface-2_5d}.
 */
final class EnvironmentReleaseCapabilityVerifierV2 {
    static final String GEOLOGY_TYPE = "geology-plan-v2";
    static final String LITHOLOGY_TYPE = "lithology-plan-v2";
    static final String STRATA_TYPE = "strata-plan-v2";
    static final String CLIMATE_TYPE = "climate-plan-v2";
    static final String WATER_CONDITION_TYPE = "water-condition-plan-v2";
    static final String SNOW_TYPE = "snow-plan-v2";
    static final String MATERIAL_TYPE = "material-profile-plan-v2";
    static final String PALETTE_TYPE = "minecraft-palette-plan-v2";
    static final String ECOLOGY_TYPE = "ecology-plan-v2";
    static final String FEATURE_MATERIAL_TYPE = "feature-material-profile-plan-v2";
    static final String VALIDATION_TYPE = "environment-validation-artifact-v2";
    static final String PREVIEW_INDEX_TYPE = "environment-preview-index-v2";
    static final String PREVIEW_PNG_TYPE = "environment-preview-png-v1";

    static final String GEOLOGY_PATH = "environment/geology-plan.json";
    static final String LITHOLOGY_PATH = "environment/lithology-plan.json";
    static final String STRATA_PATH = "environment/strata-plan.json";
    static final String CLIMATE_PATH = "environment/climate-plan.json";
    static final String WATER_CONDITION_PATH = "environment/water-condition-plan.json";
    static final String SNOW_PATH = "environment/snow-plan.json";
    static final String MATERIAL_PATH = "environment/material-profile-plan.json";
    static final String PALETTE_PATH = "environment/minecraft-palette-plan.json";
    static final String ECOLOGY_PATH = "environment/ecology-plan.json";
    static final String FEATURE_MATERIAL_PATH = "environment/feature-material-profile-plan.json";
    static final String VALIDATION_PATH = "environment/validation.json";
    static final String PREVIEW_INDEX_PATH = "environment/previews/index.json";

    private final HydrologyReleaseCapabilityVerifierV2 hydrologyVerifier =
            new HydrologyReleaseCapabilityVerifierV2();
    private final SurfaceReleaseCapabilityVerifierV2 surfaceVerifier = new SurfaceReleaseCapabilityVerifierV2();
    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();
    private final EnvironmentValidationArtifactCodecV2 validationCodec = new EnvironmentValidationArtifactCodecV2();
    private final EnvironmentPreviewIndexCodecV2 previewCodec = new EnvironmentPreviewIndexCodecV2();

    void verify(Path root, ReleaseManifestV2 manifest, CancellationToken cancellationToken) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (!manifest.requiredCapabilities().equals(ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE)) {
            throw new IOException(
                    "environment-fields Release must require environment-fields, hydrology-plan, and surface-2_5d");
        }
        ReleaseCapabilityArtifactIndexV2 index = new ReleaseCapabilityArtifactIndexV2(manifest.artifacts());
        verifyEnvironmentStack(root, index, cancellationToken);
        index.requireNoUnexpectedArtifacts();
    }

    /**
     * Verifies the surface, hydrology, and environment artifact set without requiring the index to be
     * fully consumed. Used when {@code sparse-volume} adds further required artifacts beside environment.
     */
    WorldBlueprintV2 verifyEnvironmentStack(
            Path root,
            ReleaseCapabilityArtifactIndexV2 index,
            CancellationToken cancellationToken
    ) throws IOException {
        WorldBlueprintV2 blueprint = surfaceVerifier.verifySurfacePayload(root, index, cancellationToken);
        hydrologyVerifier.verifyHydrologyPayload(root, index, blueprint, cancellationToken);
        verifyEnvironmentPayload(root, index, blueprint, cancellationToken);
        return blueprint;
    }

    private void verifyEnvironmentPayload(
            Path root,
            ReleaseCapabilityArtifactIndexV2 index,
            WorldBlueprintV2 blueprint,
            CancellationToken cancellationToken
    ) throws IOException {
        ReleaseArtifactDescriptorV2 geologyDescriptor = index.singleton(GEOLOGY_TYPE, GEOLOGY_PATH);
        ReleaseArtifactDescriptorV2 lithologyDescriptor = index.singleton(LITHOLOGY_TYPE, LITHOLOGY_PATH);
        ReleaseArtifactDescriptorV2 strataDescriptor = index.singleton(STRATA_TYPE, STRATA_PATH);
        ReleaseArtifactDescriptorV2 climateDescriptor = index.singleton(CLIMATE_TYPE, CLIMATE_PATH);
        ReleaseArtifactDescriptorV2 waterDescriptor = index.singleton(WATER_CONDITION_TYPE, WATER_CONDITION_PATH);
        ReleaseArtifactDescriptorV2 snowDescriptor = index.singleton(SNOW_TYPE, SNOW_PATH);
        ReleaseArtifactDescriptorV2 materialDescriptor = index.singleton(MATERIAL_TYPE, MATERIAL_PATH);
        ReleaseArtifactDescriptorV2 paletteDescriptor = index.singleton(PALETTE_TYPE, PALETTE_PATH);
        ReleaseArtifactDescriptorV2 ecologyDescriptor = index.singleton(ECOLOGY_TYPE, ECOLOGY_PATH);
        ReleaseArtifactDescriptorV2 featureDescriptor = index.singleton(FEATURE_MATERIAL_TYPE, FEATURE_MATERIAL_PATH);
        ReleaseArtifactDescriptorV2 validationDescriptor = index.singleton(VALIDATION_TYPE, VALIDATION_PATH);
        ReleaseArtifactDescriptorV2 previewIndexDescriptor = index.singleton(PREVIEW_INDEX_TYPE, PREVIEW_INDEX_PATH);

        cancellationToken.throwIfCancellationRequested();
        GeologyPlanV2 geology = dataCodec.readGeologyPlan(root.resolve(GEOLOGY_PATH));
        requireSemantic(geologyDescriptor, geology.canonicalChecksum(), "geology plan");
        if (!geology.equals(blueprint.geologyPlan())) {
            throw new IOException("environment geology plan does not match the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        LithologyPlanV2 lithology = dataCodec.readLithologyPlan(root.resolve(LITHOLOGY_PATH));
        requireSemantic(lithologyDescriptor, lithology.canonicalChecksum(), "lithology plan");
        if (!lithology.equals(blueprint.lithologyPlan())) {
            throw new IOException("environment lithology plan does not match the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        StrataPlanV2 strata = dataCodec.readStrataPlan(root.resolve(STRATA_PATH));
        requireSemantic(strataDescriptor, strata.canonicalChecksum(), "strata plan");
        if (!strata.equals(blueprint.strataPlan())) {
            throw new IOException("environment strata plan does not match the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        ClimatePlanV2 climate = dataCodec.readClimatePlan(root.resolve(CLIMATE_PATH));
        requireSemantic(climateDescriptor, climate.canonicalChecksum(), "climate plan");
        if (!climate.equals(blueprint.climatePlan())) {
            throw new IOException("environment climate plan does not match the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        WaterConditionPlanV2 water = dataCodec.readWaterConditionPlan(root.resolve(WATER_CONDITION_PATH));
        requireSemantic(waterDescriptor, water.canonicalChecksum(), "water-condition plan");
        if (!water.equals(blueprint.waterConditionPlan())) {
            throw new IOException("environment water-condition plan does not match the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        SnowPlanV2 snow = dataCodec.readSnowPlan(root.resolve(SNOW_PATH));
        requireSemantic(snowDescriptor, snow.canonicalChecksum(), "snow plan");
        snow.requireClimatePlan(climate);
        if (snow.width() != blueprint.space().bounds().width()
                || snow.length() != blueprint.space().bounds().length()) {
            throw new IOException("environment snow plan dimensions do not match the Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        MaterialProfilePlanV2 material = dataCodec.readMaterialProfilePlan(root.resolve(MATERIAL_PATH));
        requireSemantic(materialDescriptor, material.canonicalChecksum(), "material profile plan");
        material.requireGeologyPlan(geology, lithology, strata);
        material.requireWaterConditionPlan(water);
        material.requireSnowPlan(snow);

        cancellationToken.throwIfCancellationRequested();
        MinecraftPalettePlanV2 palette = dataCodec.readMinecraftPalettePlan(root.resolve(PALETTE_PATH));
        requireSemantic(paletteDescriptor, palette.canonicalChecksum(), "minecraft palette plan");
        palette.requireMaterialProfilePlan(material);

        cancellationToken.throwIfCancellationRequested();
        EcologyPlanV2 ecology = dataCodec.readEcologyPlan(root.resolve(ECOLOGY_PATH));
        requireSemantic(ecologyDescriptor, ecology.canonicalChecksum(), "ecology plan");
        ecology.requireClimatePlan(climate);
        ecology.requireWaterConditionPlan(water);
        ecology.requireSnowPlan(snow);

        cancellationToken.throwIfCancellationRequested();
        FeatureMaterialProfilePlanV2 feature =
                dataCodec.readFeatureMaterialProfilePlan(root.resolve(FEATURE_MATERIAL_PATH));
        requireSemantic(featureDescriptor, feature.canonicalChecksum(), "feature material profile plan");
        feature.requireMaterialProfilePlan(material);
        feature.requireGeologyPlan(geology, lithology, strata);

        cancellationToken.throwIfCancellationRequested();
        EnvironmentValidationArtifactV2 validation = validationCodec.read(root.resolve(VALIDATION_PATH));
        requireSemantic(validationDescriptor, validation.canonicalChecksum(), "environment validation artifact");
        if (!validation.sourcePlanChecksum().equals(blueprint.canonicalChecksum())
                || !validation.report().passesHardValidation()) {
            throw new IOException("environment validation artifact does not validate the released Blueprint");
        }

        cancellationToken.throwIfCancellationRequested();
        EnvironmentPreviewIndexV2 previews = previewCodec.readAndVerify(
                root.resolve(PREVIEW_INDEX_PATH), root.resolve("environment/previews"), cancellationToken);
        requireSemantic(previewIndexDescriptor, previews.canonicalChecksum(), "environment preview index");
        if (!previews.sourcePlanChecksum().equals(blueprint.canonicalChecksum())
                || previews.width() != blueprint.space().bounds().width()
                || previews.length() != blueprint.space().bounds().length()) {
            throw new IOException("environment previews do not bind to the released Blueprint dimensions");
        }
        for (EnvironmentPreviewIndexV2.Layer layer : previews.layers()) {
            cancellationToken.throwIfCancellationRequested();
            ReleaseArtifactDescriptorV2 descriptor = index.requirePath(
                    "environment/previews/" + layer.path(), PREVIEW_PNG_TYPE);
            requireSemantic(descriptor, layer.sha256(), "environment preview layer " + layer.layerId());
            if (descriptor.byteLength() != layer.byteLength()
                    || !descriptor.artifactChecksum().equals(layer.sha256())) {
                throw new IOException("environment preview layer manifest binding differs: " + layer.path());
            }
        }
        if (index.ofType(PREVIEW_PNG_TYPE).size() != previews.layers().size()) {
            throw new IOException("environment Release preview layer set is incomplete or has an extra entry");
        }
    }

    private static void requireSemantic(
            ReleaseArtifactDescriptorV2 descriptor, String expected, String description
    ) throws IOException {
        if (!descriptor.semanticChecksum().equals(expected)) {
            throw new IOException("environment Release semantic checksum differs for " + description);
        }
    }
}
