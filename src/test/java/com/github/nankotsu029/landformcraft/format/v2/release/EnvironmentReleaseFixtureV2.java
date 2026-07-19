package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.EcologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.FeatureMaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MaterialProfilePlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.MinecraftPalettePlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldValueSource;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingBundlePublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.CoastalValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.EnvironmentValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.HydrologyValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.DeterministicHydrologyRoutingSolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingRequestV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.ProvisionalSurfaceV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.BoundedHydrologyReconcilerV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.EnvironmentValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationStateV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticFieldsV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.preview.v2.EnvironmentDiagnosticFieldFactoryV2;
import com.github.nankotsu029.landformcraft.preview.v2.EnvironmentDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.preview.v2.HydrologyDiagnosticFieldsV2;
import com.github.nankotsu029.landformcraft.preview.v2.HydrologyDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentCellSnapshotV2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared builder for a complete {@code environment-fields} Release source (surface + hydrology +
 * environment). Used by the environment and sparse-volume Release capability tests.
 */
public final class EnvironmentReleaseFixtureV2 {
    private static final LandformV2DataCodec DATA = new LandformV2DataCodec();

    private EnvironmentReleaseFixtureV2() {
    }

    public record Fixture(EnvironmentReleaseSourceV2 source, WorldBlueprintV2 blueprint) {
    }

    public static Fixture build(Path root) throws Exception {
        Files.createDirectories(root);
        var request = DATA.readGenerationRequest(Path.of("examples/v2/manual-constraint-island/request-v2.json"));
        Path requestPath = root.resolve("request.json");
        DATA.writeGenerationRequest(requestPath, request);
        String requestChecksum = DATA.generationRequestChecksum(request);

        Path fieldRoot = root.resolve("constraints");
        List<FieldArtifactDescriptorV2> fields = writeFields(fieldRoot);
        String sourceIntent = Files.readString(Path.of("examples/v2/manual-constraint-island/terrain-intent-v2.json"));
        String landChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER).semanticChecksum();
        String heightChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT).semanticChecksum();
        String zoneChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP).semanticChecksum();
        TerrainIntentV2 intent = DATA.readTerrainIntent(sourceIntent
                .replace("a".repeat(64), landChecksum)
                .replace("b".repeat(64), heightChecksum)
                .replace("c".repeat(64), zoneChecksum), "environment-fixture-intent");
        Path intentPath = root.resolve("intent.json");
        DATA.writeTerrainIntent(intentPath, intent);
        String intentChecksum = DATA.terrainIntentChecksum(intent);

        ConstraintFieldIndexV2 index = new ConstraintFieldIndexV2(1, request.requestId(), requestChecksum, intentChecksum,
                bindings(fields), fields);
        Path indexPath = fieldRoot.resolve("index.json");
        new ConstraintFieldIndexCodecV2().write(indexPath, index);

        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                request.requestId(), new GenerationBounds(4, 4, 0, 100, 50), 32, request.generation().globalSeed(),
                requestChecksum, DiagnosticCompileRequestV2.defaultBudget()), intent);
        Path blueprintPath = root.resolve("blueprint.json");
        DATA.writeWorldBlueprint(blueprintPath, blueprint);

        Path validationPath = root.resolve("coastal-validation.json");
        new CoastalValidationArtifactCodecV2().write(validationPath, new CoastalValidationArtifactV2(
                blueprint.canonicalChecksum(), "coastal-validator-v1",
                new CoastalValidationArtifactV2.CoastalValidationReport(List.of(), List.of())));

        Path previewRoot = root.resolve("previews");
        new CoastalDiagnosticPreviewRendererV2().render(
                previewRoot, blueprint.canonicalChecksum(), coastalFields(), () -> false);

        OfflineTilePlanV2 plan = new OfflineTilePlanV2(1, "tile-00-00", 0, 0, 0, 0, 4, 4, 0, 100);
        Path tileRoot = root.resolve("tiles");
        Path schematic = tileRoot.resolve(plan.defaultSchematicFileName());
        OfflineTileArtifactV2 tile = new OfflineTileSchematicWriterV2().write(
                schematic, plan, blueprint.canonicalChecksum(), resolver(), () -> false);
        Path metadata = tileRoot.resolve("tile-00-00.json");
        new OfflineTileArtifactCodecV2().write(metadata, tile);

        SurfaceReleaseSourceV2 surface = new SurfaceReleaseSourceV2(
                requestPath, intentPath, blueprintPath, indexPath, fieldRoot,
                validationPath, previewRoot.resolve("index.json"), previewRoot,
                List.of(new SurfaceReleaseSourceV2.TileSource(tile.tileId(), metadata, schematic)));

        Path hydrologyPlanPath = root.resolve("hydrology-plan.json");
        DATA.writeHydrologyPlan(hydrologyPlanPath, blueprint.hydrologyPlan());
        HydrologyRoutingResultV2 routingResult = new DeterministicHydrologyRoutingSolverV2().solve(
                HydrologyRoutingRequestV2.create(
                        4, 4, blueprint.hydrologyPlan(),
                        ProvisionalSurfaceV2.routable((x, z) -> 50_000_000 + x + z),
                        List.of(new HydrologyRoutingArtifactV2.Outlet(
                                "west", 0, 0, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(), () -> false);
        Path routingRoot = root.resolve("routing");
        new HydrologyRoutingBundlePublisherV2().publish(routingRoot, routingResult, () -> false);
        Path reconciliationPlanPath = root.resolve("reconciliation-plan.json");
        DATA.writeHydrologyReconciliationPlan(reconciliationPlanPath, blueprint.hydrologyReconciliationPlan());
        HydrologyReconciliationArtifactV2 reconciliationArtifact = new BoundedHydrologyReconcilerV2().reconcile(
                blueprint.canonicalChecksum(),
                blueprint.hydrologyReconciliationPlan(),
                new HydrologyReconciliationStateV2(blueprint.hydrologyReconciliationPlan().canonicalChecksum(), List.of()),
                () -> false);
        Path reconciliationArtifactPath = root.resolve("reconciliation-artifact.json");
        new HydrologyReconciliationArtifactCodecV2().write(reconciliationArtifactPath, reconciliationArtifact);
        Path hydrologyValidationPath = root.resolve("hydrology-validation.json");
        new HydrologyValidationArtifactCodecV2().write(hydrologyValidationPath, new HydrologyValidationArtifactV2(
                blueprint.canonicalChecksum(), HydrologyValidationArtifactV2.VALIDATOR_VERSION,
                new HydrologyValidationArtifactV2.HydrologyValidationReport(List.of(), List.of())));
        Path hydrologyPreviewRoot = root.resolve("hydrology-previews");
        new HydrologyDiagnosticPreviewRendererV2().render(
                hydrologyPreviewRoot, blueprint.canonicalChecksum(), hydrologyFields(), () -> false);

        HydrologyReleaseSourceV2 hydrology = new HydrologyReleaseSourceV2(
                surface, hydrologyPlanPath, routingRoot.resolve("index.json"), routingRoot,
                reconciliationPlanPath, reconciliationArtifactPath, hydrologyValidationPath,
                hydrologyPreviewRoot.resolve("index.json"), hydrologyPreviewRoot);

        Path geologyPath = root.resolve("geology-plan.json");
        DATA.writeGeologyPlan(geologyPath, blueprint.geologyPlan());
        Path lithologyPath = root.resolve("lithology-plan.json");
        DATA.writeLithologyPlan(lithologyPath, blueprint.lithologyPlan());
        Path strataPath = root.resolve("strata-plan.json");
        DATA.writeStrataPlan(strataPath, blueprint.strataPlan());
        Path climatePath = root.resolve("climate-plan.json");
        DATA.writeClimatePlan(climatePath, blueprint.climatePlan());
        Path waterPath = root.resolve("water-condition-plan.json");
        DATA.writeWaterConditionPlan(waterPath, blueprint.waterConditionPlan());

        SnowPlanV2 snow = snowPlan(blueprint);
        Path snowPath = root.resolve("snow-plan.json");
        DATA.writeSnowPlan(snowPath, snow);

        MaterialProfilePlanV2 material = new MaterialProfilePlanCompilerV2().compile(
                blueprint.geologyPlan(), blueprint.lithologyPlan(), blueprint.strataPlan(),
                blueprint.waterConditionPlan(), snow);
        Path materialPath = root.resolve("material-profile-plan.json");
        DATA.writeMaterialProfilePlan(materialPath, material);

        MinecraftPalettePlanV2 palette = new MinecraftPalettePlanCompilerV2().compile(material);
        Path palettePath = root.resolve("minecraft-palette-plan.json");
        DATA.writeMinecraftPalettePlan(palettePath, palette);

        EcologyPlanV2 ecology = new EcologyPlanCompilerV2().compile(
                blueprint.climatePlan(), blueprint.waterConditionPlan(), snow,
                EcologyPlanV2.EcologyPreset.SPARSE_COASTAL);
        Path ecologyPath = root.resolve("ecology-plan.json");
        DATA.writeEcologyPlan(ecologyPath, ecology);

        FeatureMaterialProfilePlanV2 feature = new FeatureMaterialProfilePlanCompilerV2().compile(
                material, blueprint.geologyPlan(), blueprint.lithologyPlan(), blueprint.strataPlan(),
                blueprint.volcanicPlans(), blueprint.canyonPlans());
        Path featurePath = root.resolve("feature-material-profile-plan.json");
        DATA.writeFeatureMaterialProfilePlan(featurePath, feature);

        Path environmentValidationPath = root.resolve("environment-validation.json");
        new EnvironmentValidationArtifactCodecV2().write(environmentValidationPath,
                new EnvironmentValidationArtifactV2(
                        blueprint.canonicalChecksum(),
                        new EnvironmentValidationArtifactV2.EnvironmentValidationReport(List.of(), List.of())));

        Path environmentPreviewRoot = root.resolve("environment-previews");
        new EnvironmentDiagnosticPreviewRendererV2().render(
                environmentPreviewRoot, blueprint.canonicalChecksum(),
                EnvironmentDiagnosticFieldFactoryV2.create(4, 4, (x, z) -> new EnvironmentCellSnapshotV2(
                        400, 500, 500, 400, 500, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0)),
                () -> false);

        return new Fixture(new EnvironmentReleaseSourceV2(
                hydrology, geologyPath, lithologyPath, strataPath, climatePath, waterPath, snowPath,
                materialPath, palettePath, ecologyPath, featurePath, environmentValidationPath,
                environmentPreviewRoot.resolve("index.json"), environmentPreviewRoot), blueprint);
    }

    private static SnowPlanV2 snowPlan(WorldBlueprintV2 blueprint) {
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
                "generate.snow",
                "0.1.0-v2-4-06",
                "stage.snow",
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
                List.of(
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.potential", SnowPlanV2.FieldSemantic.SNOW_POTENTIAL,
                                SnowPlanV2.FieldValueType.U16, "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER, SnowPlanV2.Sampling.NEAREST, 1_000),
                        new SnowPlanV2.FieldBinding(
                                "environment.snow.cover", SnowPlanV2.FieldSemantic.SNOW_COVER,
                                SnowPlanV2.FieldValueType.U16, "generate.snow",
                                SnowPlanV2.Ownership.SINGLE_OWNER, SnowPlanV2.Sampling.NEAREST, 1_000)),
                new SnowPlanV2.ResourceBudget(
                        SnowPlanV2.ResourceBudget.VERSION, 2, cells, Math.multiplyExact(cells, 2L),
                        32_768L, windowSize, workingBytes, 131_072L),
                "0".repeat(64));
        SnowPlanV2 sealed = DATA.sealSnowPlan(draft);
        sealed.requireClimatePlan(blueprint.climatePlan());
        return sealed;
    }

    private static List<FieldArtifactDescriptorV2> writeFields(Path root) throws Exception {
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        List<FieldArtifactDescriptorV2> fields = new ArrayList<>();
        fields.add(write(writer, root, "fields/land-desired.lfgrid", "constraint.land.desired",
                FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER, FieldArtifactDescriptorV2.FieldValueType.U8,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", false, (x, z) -> (x + z) & 1));
        fields.add(write(writer, root, "fields/land-actual.lfgrid", "constraint.land.actual",
                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER, FieldArtifactDescriptorV2.FieldValueType.U8,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", false, (x, z) -> (x + z) & 1));
        fields.add(write(writer, root, "fields/land-residual.lfgrid", "constraint.land.residual",
                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER, FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", false, (x, z) -> 0));
        fields.add(write(writer, root, "fields/height-desired.lfgrid", "constraint.height.desired",
                FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT, FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", false, (x, z) -> 50_000_000));
        fields.add(write(writer, root, "fields/height-actual.lfgrid", "constraint.height.actual",
                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT, FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", false, (x, z) -> 50_000_000));
        fields.add(write(writer, root, "fields/height-residual.lfgrid", "constraint.height.residual",
                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT, FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", false, (x, z) -> 0));
        fields.add(write(writer, root, "fields/zone-desired.lfgrid", "constraint.zone.desired",
                FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP, FieldArtifactDescriptorV2.FieldValueType.U16,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "zones", false, (x, z) -> x < 2 ? 1 : 2));
        return List.copyOf(fields);
    }

    private static FieldArtifactDescriptorV2 write(
            LfcGridWriterV1 writer, Path root, String path, String id,
            FieldArtifactDescriptorV2.FieldSemantic semantic, FieldArtifactDescriptorV2.FieldValueType type,
            FieldArtifactDescriptorV2.Sampling sampling, String source, boolean noData, FieldValueSource values
    ) throws Exception {
        int sentinel = noData ? type.maximumRaw() : 0;
        long scale = type == FieldArtifactDescriptorV2.FieldValueType.I32 ? 1L : 1_000_000L;
        FieldArtifactDescriptorV2.Definition definition = new FieldArtifactDescriptorV2.Definition(
                id, semantic, type, 4, 4, FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                sampling, scale, 0L, noData, sentinel);
        String checksum = switch (source) {
            case "land-water" -> "a";
            case "height" -> "b";
            default -> "c";
        };
        return writer.write(root, path, definition, new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP, "constraint-source:" + source,
                checksum.repeat(64), "numeric-png", "1", "pixel-center-v1"), values, () -> false);
    }

    private static List<ConstraintFieldIndexV2.AppliedBinding> bindings(List<FieldArtifactDescriptorV2> fields) {
        FieldArtifactDescriptorV2 land = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);
        FieldArtifactDescriptorV2 height = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT);
        FieldArtifactDescriptorV2 zone = field(fields, FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP);
        return List.of(
                new ConstraintFieldIndexV2.AppliedBinding("land-water-binding", "constraint-source:land-water",
                        TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, TerrainIntentV2.Strength.HARD,
                        TerrainIntentV2.Sampling.NEAREST, 0, 0, "constraint:land-water:sha256-" + land.semanticChecksum(),
                        land.definition().fieldId(), ids(fields, "constraint.land"), List.of(
                        new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"),
                        new ConstraintFieldIndexV2.LabelEntry(1, 1, "land"))),
                new ConstraintFieldIndexV2.AppliedBinding("height-binding", "constraint-source:height",
                        TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE, TerrainIntentV2.Strength.SOFT,
                        TerrainIntentV2.Sampling.BILINEAR_FIXED, 2, 800_000,
                        "constraint:height-guide:sha256-" + height.semanticChecksum(), height.definition().fieldId(),
                        ids(fields, "constraint.height"), List.of()),
                new ConstraintFieldIndexV2.AppliedBinding("zone-binding", "constraint-source:zones",
                        TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP, TerrainIntentV2.Strength.HARD,
                        TerrainIntentV2.Sampling.NEAREST, 0, 0, "constraint:zone-label-map:sha256-" + zone.semanticChecksum(),
                        zone.definition().fieldId(), ids(fields, "constraint.zone"), List.of(
                        new ConstraintFieldIndexV2.LabelEntry(10, 1, "shore"),
                        new ConstraintFieldIndexV2.LabelEntry(20, 2, "upland"))));
    }

    private static List<String> ids(List<FieldArtifactDescriptorV2> fields, String prefix) {
        return fields.stream().map(value -> value.definition().fieldId()).filter(value -> value.startsWith(prefix)).toList();
    }

    private static FieldArtifactDescriptorV2 field(
            List<FieldArtifactDescriptorV2> fields, FieldArtifactDescriptorV2.FieldSemantic semantic
    ) {
        return fields.stream().filter(value -> value.definition().semantic() == semantic).findFirst().orElseThrow();
    }

    private static CoastalDiagnosticFieldsV2 coastalFields() {
        return new CoastalDiagnosticFieldsV2(4, 4, 0, 100_000_000,
                (x, z) -> 0, (x, z) -> 0, (x, z) -> 0, (x, z) -> 0,
                (x, z) -> (x + z) & 1, (x, z) -> (x + z) & 1, (x, z) -> 0,
                (x, z) -> 50_000_000, (x, z) -> 50_000_000, (x, z) -> 0, (x, z) -> 0);
    }

    private static HydrologyDiagnosticFieldsV2 hydrologyFields() {
        return new HydrologyDiagnosticFieldsV2(
                4, 4, 0, 100_000_000,
                (x, z) -> x % 2, (x, z) -> z % 3, (x, z) -> (x + z) % 4, (x, z) -> x == z ? 1 : 0,
                (x, z) -> (x + z) * 1_000_000, (x, z) -> (x + z + 1) * 1_000_000, (x, z) -> x % 3,
                (x, z) -> x < 2 ? 1 : 0, (x, z) -> (x + z) % 5, (x, z) -> (x + z) * 500_000,
                (x, z) -> z % 2, (x, z) -> 0);
    }

    private static TerrainBlockResolver resolver() {
        return (x, y, z) -> y == 0 ? "minecraft:bedrock" : y < 50 ? "minecraft:stone"
                : y == 50 ? "minecraft:water" : "minecraft:air";
    }
}
