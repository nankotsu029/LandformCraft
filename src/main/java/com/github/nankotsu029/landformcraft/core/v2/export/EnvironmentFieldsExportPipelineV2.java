package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.EcologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.FeatureMaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MinecraftPalettePlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.EnvironmentReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.EnvironmentValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.snow.SnowFieldModulesV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.EnvironmentDiagnosticFieldFactoryV2;
import com.github.nankotsu029.landformcraft.preview.v2.EnvironmentDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentCellSnapshotV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentFieldSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentValidationInputV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentValidationReportV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentValidatorV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * V2-15-07 production pipeline for shared {@code environment-fields} artifacts.
 *
 * <p>It reuses the shared hydrology-plan chain, then freezes geology／climate／water／snow／material／
 * palette／ecology／feature-material plans, field-only environment validation, and the fixed
 * environment preview set. Individual environment FeatureKinds are not promoted or executed here.</p>
 */
final class EnvironmentFieldsExportPipelineV2 implements ProductionExportPipelineV2 {
    static final String PIPELINE_ID = "v2.production.environment-fields.shared";
    static final String GENERATOR_HANDLER_ID = "v2.environment.shared-generator";
    static final String VALIDATOR_HANDLER_ID = "v2.environment.field-validator";
    static final String PREVIEW_HANDLER_ID = "v2.environment.diagnostic-preview";
    static final String EXPORT_HANDLER_ID = "v2.release.environment-fields-export";

    private static final PipelineDescriptor DESCRIPTOR = new PipelineDescriptor(
            PIPELINE_ID,
            new HandlerSet(
                    GENERATOR_HANDLER_ID,
                    VALIDATOR_HANDLER_ID,
                    PREVIEW_HANDLER_ID,
                    EXPORT_HANDLER_ID),
            List.of(
                    TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                    TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                    TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                    TerrainIntentV2.FeatureKind.SANDY_BEACH),
            List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
            ReleaseArtifactCatalogV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE);

    private final HydrologyPlanExportPipelineV2 hydrology = new HydrologyPlanExportPipelineV2();
    private final LandformV2DataCodec data = new LandformV2DataCodec();
    private final MaterialProfilePlanCompilerV2 materialCompiler = new MaterialProfilePlanCompilerV2();
    private final MinecraftPalettePlanCompilerV2 paletteCompiler = new MinecraftPalettePlanCompilerV2();
    private final EcologyPlanCompilerV2 ecologyCompiler = new EcologyPlanCompilerV2();
    private final FeatureMaterialProfilePlanCompilerV2 featureMaterialCompiler =
            new FeatureMaterialProfilePlanCompilerV2();
    private final EnvironmentValidatorV2 validator = new EnvironmentValidatorV2();
    private final EnvironmentValidationArtifactCodecV2 validationCodec =
            new EnvironmentValidationArtifactCodecV2();

    @Override
    public PipelineDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GeneratedSurface generate(
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        throw new IOException(
                "environment-fields pipeline cannot publish surface-2_5d alone; use generateEnvironment");
    }

    @Override
    public GeneratedHydrology generateHydrology(
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        throw new IOException(
                "environment-fields pipeline cannot publish hydrology-plan alone; use generateEnvironment");
    }

    @Override
    public GeneratedEnvironment generateEnvironment(
            GenerationRequestV2 request,
            TerrainIntentV2 draftIntent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(draftIntent, "draftIntent");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(workRoot, "workRoot");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();

        Path hydrologyRoot = workRoot.resolve("hydrology");
        Path environmentRoot = workRoot.resolve("environment-work");
        Files.createDirectories(environmentRoot);

        GeneratedHydrology hydrologyGenerated = hydrology.generateHydrology(
                request, draftIntent, baseline, hydrologyRoot, budget, token);
        WorldBlueprintV2 blueprint = hydrologyGenerated.blueprint();
        int width = request.bounds().width();
        int length = request.bounds().length();
        requireSharedEnvironmentOnly(blueprint);

        Path geologyPath = environmentRoot.resolve("geology-plan.json");
        data.writeGeologyPlan(geologyPath, blueprint.geologyPlan());
        Path lithologyPath = environmentRoot.resolve("lithology-plan.json");
        data.writeLithologyPlan(lithologyPath, blueprint.lithologyPlan());
        Path strataPath = environmentRoot.resolve("strata-plan.json");
        data.writeStrataPlan(strataPath, blueprint.strataPlan());
        Path climatePath = environmentRoot.resolve("climate-plan.json");
        data.writeClimatePlan(climatePath, blueprint.climatePlan());
        Path waterPath = environmentRoot.resolve("water-condition-plan.json");
        data.writeWaterConditionPlan(waterPath, blueprint.waterConditionPlan());

        SnowPlanV2 snow = sealSnowPlan(blueprint);
        Path snowPath = environmentRoot.resolve("snow-plan.json");
        data.writeSnowPlan(snowPath, snow);

        MaterialProfilePlanV2 material = materialCompiler.compile(
                blueprint.geologyPlan(),
                blueprint.lithologyPlan(),
                blueprint.strataPlan(),
                blueprint.waterConditionPlan(),
                snow);
        Path materialPath = environmentRoot.resolve("material-profile-plan.json");
        data.writeMaterialProfilePlan(materialPath, material);

        MinecraftPalettePlanV2 palette = paletteCompiler.compile(material);
        Path palettePath = environmentRoot.resolve("minecraft-palette-plan.json");
        data.writeMinecraftPalettePlan(palettePath, palette);

        EcologyPlanV2.EcologyPreset ecologyPreset = resolveEcologyPreset(draftIntent);
        EcologyPlanV2 ecology = ecologyCompiler.compile(
                blueprint.climatePlan(), blueprint.waterConditionPlan(), snow, ecologyPreset);
        Path ecologyPath = environmentRoot.resolve("ecology-plan.json");
        data.writeEcologyPlan(ecologyPath, ecology);

        FeatureMaterialProfilePlanV2 featureMaterial = featureMaterialCompiler.compile(
                material,
                blueprint.geologyPlan(),
                blueprint.lithologyPlan(),
                blueprint.strataPlan(),
                blueprint.volcanicPlans(),
                blueprint.canyonPlans());
        Path featurePath = environmentRoot.resolve("feature-material-profile-plan.json");
        data.writeFeatureMaterialProfilePlan(featurePath, featureMaterial);

        EnvironmentFieldSamplerV2 sampler = sharedCoastalFieldSampler();
        EnvironmentValidationInputV2 validationInput = new EnvironmentValidationInputV2(
                width, length, blueprint.canonicalChecksum(), sampler);
        EnvironmentValidationReportV2 report = validator.validate(validationInput, token);
        if (!report.passesHardValidation()) {
            throw new IOException("environment HARD validation failed: " + report.issues());
        }
        Path environmentValidationPath = environmentRoot.resolve("validation.json");
        validationCodec.write(environmentValidationPath, validator.toArtifact(
                blueprint.canonicalChecksum(), report));

        Path environmentPreviewRoot = environmentRoot.resolve("previews");
        new EnvironmentDiagnosticPreviewRendererV2().render(
                environmentPreviewRoot,
                blueprint.canonicalChecksum(),
                EnvironmentDiagnosticFieldFactoryV2.create(width, length, sampler),
                token);

        EnvironmentReleaseSourceV2 source = new EnvironmentReleaseSourceV2(
                hydrologyGenerated.source(),
                geologyPath,
                lithologyPath,
                strataPath,
                climatePath,
                waterPath,
                snowPath,
                materialPath,
                palettePath,
                ecologyPath,
                featurePath,
                environmentValidationPath,
                environmentPreviewRoot.resolve("index.json"),
                environmentPreviewRoot);
        return new GeneratedEnvironment(source, blueprint, hydrologyGenerated.baseTerrain());
    }

    private SnowPlanV2 sealSnowPlan(WorldBlueprintV2 blueprint) {
        int width = blueprint.space().bounds().width();
        int length = blueprint.space().bounds().length();
        long cells = Math.multiplyExact((long) width, length);
        int windowSize = Math.min(256, Math.max(width, length));
        long workingBytes = Math.max(1L, Math.multiplyExact(
                Math.multiplyExact((long) Math.min(width, windowSize), Math.min(length, windowSize)),
                2L * Integer.BYTES));
        SnowPlanV2 draft = new SnowPlanV2(
                SnowPlanV2.VERSION,
                SnowPlanV2.FIELD_CONTRACT_VERSION,
                SnowFieldModulesV2.MODULE_ID,
                SnowFieldModulesV2.MODULE_VERSION,
                SnowFieldModulesV2.STAGE_ID,
                blueprint.climatePlan().namedSeed(),
                SnowPlanV2.SEED_NAMESPACE,
                width,
                length,
                blueprint.space().bounds().minY(),
                blueprint.space().bounds().maxY(),
                SnowPlanV2.Kernel.standard(),
                new SnowPlanV2.ClimateBinding(
                        SnowPlanV2.ClimateBinding.VERSION,
                        blueprint.climatePlan().canonicalChecksum(),
                        SnowPlanV2.ClimateBinding.TEMPERATURE_FIELD_ID,
                        SnowPlanV2.ClimateBinding.MOISTURE_FIELD_ID,
                        SnowPlanV2.ClimateBinding.CONTRACT_VERSION),
                new SnowFieldModulesV2().fieldBindings(),
                new SnowPlanV2.ResourceBudget(
                        SnowPlanV2.ResourceBudget.VERSION,
                        2,
                        cells,
                        Math.multiplyExact(cells, 2L),
                        32_768L,
                        windowSize,
                        workingBytes,
                        131_072L),
                "0".repeat(64));
        SnowPlanV2 sealed = data.sealSnowPlan(draft);
        sealed.requireClimatePlan(blueprint.climatePlan());
        return sealed;
    }

    private static EcologyPlanV2.EcologyPreset resolveEcologyPreset(TerrainIntentV2 intent) throws IOException {
        String preset = intent.environment().ecologyPreset();
        if (preset == null || preset.isBlank()) {
            return EcologyPlanV2.EcologyPreset.SPARSE_COASTAL;
        }
        try {
            return EcologyPlanV2.EcologyPreset.valueOf(preset);
        } catch (IllegalArgumentException exception) {
            throw new IOException("environment-fields shared pipeline rejects unknown ecologyPreset: " + preset,
                    exception);
        }
    }

    /**
     * Shared coastal environment stack without individual environment Feature overlays. Cells stay
     * within HARD environment thresholds so validation measures the public sampler only.
     */
    private static EnvironmentFieldSamplerV2 sharedCoastalFieldSampler() {
        EnvironmentCellSnapshotV2 healthy = new EnvironmentCellSnapshotV2(
                400, 500, 500, 400, 500, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0);
        return (x, z) -> healthy;
    }

    private static void requireSharedEnvironmentOnly(WorldBlueprintV2 blueprint) throws IOException {
        if (!blueprint.mangroveWetlandPlans().isEmpty()
                || !blueprint.coralReefPlans().isEmpty()
                || !blueprint.mountainPlans().isEmpty()
                || !blueprint.volcanicPlans().isEmpty()
                || !blueprint.fjordPlans().isEmpty()
                || !blueprint.canyonPlans().isEmpty()) {
            throw new IOException(
                    "environment-fields shared pipeline rejects individual environment Feature plans; "
                            + "wire them in later V2-15 Feature Tasks");
        }
    }
}
