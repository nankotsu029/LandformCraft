package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingBundlePublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.release.HydrologyReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.HydrologyValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.DeterministicHydrologyRoutingSolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingRequestV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.ProvisionalSurfaceV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.BoundedHydrologyReconcilerV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationStateV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import com.github.nankotsu029.landformcraft.preview.v2.HydrologyDiagnosticFieldFactoryV2;
import com.github.nankotsu029.landformcraft.preview.v2.HydrologyDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyFieldSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidationInputV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidationReportV2;
import com.github.nankotsu029.landformcraft.validation.v2.hydrology.HydrologyValidatorV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * V2-15-06 production pipeline for shared {@code hydrology-plan} artifacts.
 *
 * <p>It reuses the coastal surface chain, then freezes empty-graph routing, reconciliation,
 * field-only validation, and the fixed hydrology preview set. Individual hydrology FeatureKinds are
 * not promoted or executed here.</p>
 */
final class HydrologyPlanExportPipelineV2 implements ProductionExportPipelineV2 {
    static final String PIPELINE_ID = "v2.production.hydrology-plan.shared";
    static final String GENERATOR_HANDLER_ID = "v2.hydrology.shared-generator";
    static final String VALIDATOR_HANDLER_ID = "v2.hydrology.field-validator";
    static final String PREVIEW_HANDLER_ID = "v2.hydrology.diagnostic-preview";
    static final String EXPORT_HANDLER_ID = "v2.release.hydrology-plan-export";

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
            ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE);

    private final CoastalSurfaceExportPipelineV2 coastal = new CoastalSurfaceExportPipelineV2();
    private final LandformV2DataCodec data = new LandformV2DataCodec();
    private final DeterministicHydrologyRoutingSolverV2 routingSolver =
            new DeterministicHydrologyRoutingSolverV2();
    private final HydrologyRoutingBundlePublisherV2 routingPublisher =
            new HydrologyRoutingBundlePublisherV2();
    private final BoundedHydrologyReconcilerV2 reconciler = new BoundedHydrologyReconcilerV2();
    private final HydrologyValidatorV2 validator = new HydrologyValidatorV2();

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
        throw new IOException("hydrology-plan pipeline cannot publish surface-2_5d alone; use generateHydrology");
    }

    @Override
    public GeneratedHydrology generateHydrology(
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

        Path surfaceRoot = workRoot.resolve("surface");
        Path hydrologyRoot = workRoot.resolve("hydrology-work");
        Files.createDirectories(hydrologyRoot);

        CoastalSurfaceExportPipelineV2.GeneratedCoastalSurface coastalGenerated =
                coastal.generateWithFields(request, draftIntent, baseline, surfaceRoot, budget, token);
        GeneratedSurface surface = coastalGenerated.surface();
        WorldBlueprintV2 blueprint = surface.blueprint();
        CoastalSurfaceFieldsV2 fields = coastalGenerated.fields();
        int width = request.bounds().width();
        int length = request.bounds().length();
        requireSharedHydrologyOnly(blueprint);

        Path hydrologyPlanPath = hydrologyRoot.resolve("plan.json");
        data.writeHydrologyPlan(hydrologyPlanPath, blueprint.hydrologyPlan());

        List<HydrologyRoutingArtifactV2.Outlet> outlets = selectBoundaryOutlets(
                width, length, fields, blueprint.hydrologyPlan().budget().maximumBasins());
        ProvisionalSurfaceV2 provisional = provisionalSurface(fields);
        HydrologyRoutingResultV2 routingResult = routingSolver.solve(
                HydrologyRoutingRequestV2.create(
                        width, length, blueprint.hydrologyPlan(), provisional, outlets),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(),
                token);
        Path routingRoot = hydrologyRoot.resolve("routing");
        routingPublisher.publish(routingRoot, routingResult, token);

        Path reconciliationPlanPath = hydrologyRoot.resolve("reconciliation-plan.json");
        data.writeHydrologyReconciliationPlan(
                reconciliationPlanPath, blueprint.hydrologyReconciliationPlan());
        HydrologyReconciliationArtifactV2 reconciliationArtifact = reconciler.reconcile(
                blueprint.canonicalChecksum(),
                blueprint.hydrologyReconciliationPlan(),
                new HydrologyReconciliationStateV2(
                        blueprint.hydrologyReconciliationPlan().canonicalChecksum(), List.of()),
                token);
        Path reconciliationArtifactPath = hydrologyRoot.resolve("reconciliation-artifact.json");
        new HydrologyReconciliationArtifactCodecV2().write(
                reconciliationArtifactPath, reconciliationArtifact);

        HydrologyFieldSamplerV2 sampler = sharedFieldSampler(routingResult);
        HydrologyValidationInputV2 validationInput = new HydrologyValidationInputV2(
                blueprint, sampler, reconciliationArtifact);
        HydrologyValidationReportV2 report = validator.validate(validationInput, token);
        if (!report.passesHardValidation()) {
            throw new IOException("hydrology HARD validation failed: " + report.issues());
        }
        Path hydrologyValidationPath = hydrologyRoot.resolve("validation.json");
        new HydrologyValidationArtifactCodecV2().write(hydrologyValidationPath,
                new HydrologyValidationArtifactV2(
                        blueprint.canonicalChecksum(),
                        HydrologyValidationArtifactV2.VALIDATOR_VERSION,
                        new HydrologyValidationArtifactV2.HydrologyValidationReport(
                                report.metrics(), report.issues())));

        Path hydrologyPreviewRoot = hydrologyRoot.resolve("previews");
        new HydrologyDiagnosticPreviewRendererV2().render(
                hydrologyPreviewRoot,
                blueprint.canonicalChecksum(),
                HydrologyDiagnosticFieldFactoryV2.create(validationInput, report),
                token);

        HydrologyReleaseSourceV2 source = new HydrologyReleaseSourceV2(
                surface.source(),
                hydrologyPlanPath,
                routingRoot.resolve("index.json"),
                routingRoot,
                reconciliationPlanPath,
                reconciliationArtifactPath,
                hydrologyValidationPath,
                hydrologyPreviewRoot.resolve("index.json"),
                hydrologyPreviewRoot);
        return new GeneratedHydrology(
                source,
                blueprint,
                new CoastalSurfaceTerrainQueryV2(
                        fields,
                        request.bounds().minY(),
                        request.bounds().maxY(),
                        request.bounds().waterLevel()));
    }

    private static void requireSharedHydrologyOnly(WorldBlueprintV2 blueprint) throws IOException {
        if (!blueprint.meanderingRiverPlans().isEmpty()
                || !blueprint.lakePlans().isEmpty()
                || !blueprint.canyonPlans().isEmpty()
                || !blueprint.waterfallPlans().isEmpty()
                || !blueprint.deltaPlans().isEmpty()
                || !blueprint.tidalChannelPlans().isEmpty()
                || !blueprint.mangroveWetlandPlans().isEmpty()
                || !blueprint.coralReefPlans().isEmpty()
                || !blueprint.fjordPlans().isEmpty()
                || !blueprint.mountainPlans().isEmpty()
                || !blueprint.volcanicPlans().isEmpty()
                || !blueprint.hydrologyPlan().basins().isEmpty()
                || !blueprint.hydrologyPlan().nodes().isEmpty()
                || !blueprint.hydrologyPlan().reaches().isEmpty()
                || !blueprint.hydrologyPlan().waterBodies().isEmpty()
                || !blueprint.hydrologyPlan().fallPlans().isEmpty()) {
            throw new IOException(
                    "hydrology-plan shared pipeline rejects individual hydrology Feature plans; "
                            + "wire them in later V2-15 Feature Tasks");
        }
    }

    private static ProvisionalSurfaceV2 provisionalSurface(CoastalSurfaceFieldsV2 fields) {
        return new ProvisionalSurfaceV2() {
            @Override
            public long elevationMillionthsAt(int globalX, int globalZ) {
                return fields.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, globalX, globalZ);
            }

            @Override
            public boolean routableAt(int globalX, int globalZ) {
                return true;
            }
        };
    }

    private static List<HydrologyRoutingArtifactV2.Outlet> selectBoundaryOutlets(
            int width,
            int length,
            CoastalSurfaceFieldsV2 fields,
            int maximumBasins
    ) throws IOException {
        List<int[]> waterBoundary = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            maybeAddWaterBoundary(waterBoundary, fields, x, 0, width);
            if (length > 1) {
                maybeAddWaterBoundary(waterBoundary, fields, x, length - 1, width);
            }
        }
        for (int z = 1; z < length - 1; z++) {
            maybeAddWaterBoundary(waterBoundary, fields, 0, z, width);
            if (width > 1) {
                maybeAddWaterBoundary(waterBoundary, fields, width - 1, z, width);
            }
        }
        if (waterBoundary.isEmpty()) {
            throw new IOException("hydrology-plan shared pipeline requires at least one WATER boundary cell");
        }
        int limit = Math.min(maximumBasins, waterBoundary.size());
        List<HydrologyRoutingArtifactV2.Outlet> outlets = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            int[] cell = waterBoundary.get(index);
            outlets.add(outlet(cell[0], cell[1]));
        }
        return List.copyOf(outlets);
    }

    private static void maybeAddWaterBoundary(
            List<int[]> waterBoundary,
            CoastalSurfaceFieldsV2 fields,
            int x,
            int z,
            int width
    ) {
        if (fields.landWaterAt(z * width + x) == 0) {
            waterBoundary.add(new int[] {x, z});
        }
    }

    private static HydrologyRoutingArtifactV2.Outlet outlet(int x, int z) {
        return new HydrologyRoutingArtifactV2.Outlet(
                String.format(Locale.ROOT, "boundary-%d-%d", z, x),
                x,
                z,
                HydrologyRoutingArtifactV2.OutletKind.BOUNDARY);
    }

    private static HydrologyFieldSamplerV2 sharedFieldSampler(HydrologyRoutingResultV2 routing) {
        return new HydrologyFieldSamplerV2() {
            @Override
            public int width() {
                return routing.width();
            }

            @Override
            public int length() {
                return routing.length();
            }

            @Override
            public int valueAt(String fieldId, int globalX, int globalZ) {
                if (HydrologyIrModuleV2.FLOW_DIRECTION_FIELD.equals(fieldId)) {
                    return routing.flowDirectionCodeAt(globalX, globalZ);
                }
                if (HydrologyIrModuleV2.FLOW_ACCUMULATION_FIELD.equals(fieldId)) {
                    return routing.flowAccumulationAt(globalX, globalZ);
                }
                return HydrologyValidationInputV2.NO_DATA;
            }
        };
    }
}
