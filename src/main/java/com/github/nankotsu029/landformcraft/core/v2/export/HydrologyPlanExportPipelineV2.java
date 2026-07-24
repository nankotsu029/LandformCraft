package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.material.SurfaceMaterializationV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingBundlePublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.release.HydrologyReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.HydrologyValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.HydrologyIrModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.DeterministicHydrologyRoutingSolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingRequestV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.ProvisionalSurfaceV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.HydrologyLakeModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.lake.LakeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.BoundedHydrologyReconcilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.HydrologyReconciliationPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.HydrologyRiverModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.river.MeanderingRiverGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.HydrologyWaterfallModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.waterfall.WaterfallGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.CanyonGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.LandformCanyonModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.LakePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.MeanderingRiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WaterfallPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * V2-15-06 production pipeline for shared {@code hydrology-plan} artifacts, extended by V2-15-10
 * (ADR 0039 Candidate A) to also execute the {@code RIVER} / {@code MEANDERING_RIVER} offline
 * production route, by V2-15-11 to also execute the {@code LAKE} offline production route, by
 * V2-15-12 to also execute the {@code CANYON} offline production route, and by V2-15-13 to also
 * execute the {@code WATERFALL} offline production route.
 *
 * <p>It reuses the coastal surface chain, then freezes empty-graph routing, reconciliation,
 * field-only validation, and the fixed hydrology preview set. When the compiled Blueprint carries a
 * {@code RIVER} or {@code MEANDERING_RIVER} plan, this pipeline also executes
 * {@link MeanderingRiverGeneratorV2} and samples its fields for validation/preview; when it carries a
 * {@code LAKE} plan, it likewise executes {@link LakeGeneratorV2}; when it carries a {@code CANYON}
 * plan (always alongside the {@code MEANDERING_RIVER} it shares a bed with, per the HARD
 * {@code WITHIN} binding), it likewise executes {@link CanyonGeneratorV2}; when it carries a
 * {@code WATERFALL} plan (always alongside the {@code MEANDERING_RIVER} it falls on, per the HARD
 * {@code ON_PATH_OF} binding), it likewise executes {@link WaterfallGeneratorV2}. Other individual
 * hydrology FeatureKinds are still not promoted or executed here.</p>
 */
final class HydrologyPlanExportPipelineV2 implements ProductionExportPipelineV2 {
    static final String PIPELINE_ID = "v2.production.hydrology-plan.shared";
    static final String GENERATOR_HANDLER_ID = "v2.hydrology.shared-generator";
    static final String VALIDATOR_HANDLER_ID = "v2.hydrology.field-validator";
    static final String PREVIEW_HANDLER_ID = "v2.hydrology.diagnostic-preview";
    static final String EXPORT_HANDLER_ID = "v2.release.hydrology-plan-export";

    private static final IntPredicate NO_HARD_ROUTE_CONFLICT = index -> false;

    private static final Set<String> RIVER_ONLY_FIELD_IDS = Set.of(
            HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID,
            HydrologyRiverModuleV2.BANK_MASK_FIELD_ID,
            HydrologyRiverModuleV2.FLOODPLAIN_MASK_FIELD_ID,
            HydrologyRiverModuleV2.MEANDER_CORRIDOR_FIELD_ID,
            HydrologyRiverModuleV2.LOCAL_WIDTH_FIELD_ID,
            HydrologyRiverModuleV2.DISCHARGE_INDEX_FIELD_ID);

    private static final Set<String> LAKE_ONLY_FIELD_IDS = Set.of(
            HydrologyLakeModuleV2.BASIN_MASK_FIELD_ID,
            HydrologyLakeModuleV2.RIM_MASK_FIELD_ID,
            HydrologyLakeModuleV2.SPILLWAY_MASK_FIELD_ID,
            HydrologyLakeModuleV2.DEPTH_FIELD_ID,
            HydrologyLakeModuleV2.FLOOR_HEIGHT_FIELD_ID,
            HydrologyLakeModuleV2.SURFACE_FIELD_ID);

    private static final Set<String> WATERFALL_ONLY_FIELD_IDS = Set.of(
            HydrologyWaterfallModuleV2.LIP_MASK_FIELD_ID,
            HydrologyWaterfallModuleV2.BASE_MASK_FIELD_ID,
            HydrologyWaterfallModuleV2.PLUNGE_POOL_MASK_FIELD_ID,
            HydrologyWaterfallModuleV2.LIP_ELEVATION_FIELD_ID,
            HydrologyWaterfallModuleV2.BASE_ELEVATION_FIELD_ID,
            HydrologyWaterfallModuleV2.PLUNGE_POOL_FLOOR_FIELD_ID);

    private static final Set<String> CANYON_ONLY_FIELD_IDS = Set.of(
            LandformCanyonModuleV2.CANYON_MASK_FIELD_ID,
            LandformCanyonModuleV2.FLOOR_MASK_FIELD_ID,
            LandformCanyonModuleV2.RIM_MASK_FIELD_ID,
            LandformCanyonModuleV2.TERRACE_MASK_FIELD_ID,
            LandformCanyonModuleV2.SURFACE_HEIGHT_FIELD_ID,
            LandformCanyonModuleV2.WALL_HEIGHT_FIELD_ID);

    /**
     * V2-15-11: {@code RIVER}/{@code LAKE} share the {@link HydrologyIrModuleV2} bed／water fields, so
     * both generator kinds may contribute a value at the same field id. {@link #mergedFieldValueAt}
     * scans every generator regardless of kind for these ids.
     */
    private static final Set<String> SHARED_IR_FIELD_IDS = Set.of(
            HydrologyIrModuleV2.BED_ELEVATION_FIELD,
            HydrologyIrModuleV2.WATER_SURFACE_FIELD,
            HydrologyIrModuleV2.WATER_DEPTH_FIELD,
            HydrologyIrModuleV2.WATER_BODY_ID_FIELD);

    /**
     * V2-15-12: {@code CANYON} shares only {@code BED_ELEVATION_FIELD} with {@code RIVER} (it consumes
     * the shared river bed, never a water surface/depth/body — a canyon owns no fluid).
     */
    private static final Set<String> CANYON_SHARED_IR_FIELD_IDS = Set.of(
            HydrologyIrModuleV2.BED_ELEVATION_FIELD);

    /**
     * V2-15-13: {@code WATERFALL} shares only {@code BED_ELEVATION_FIELD} with its host river (its
     * lip crest and plunge floor are bed elevations; the falling column's own fluid belongs to the
     * bounded sparse volume, not to a 2.5D water field).
     */
    private static final Set<String> WATERFALL_SHARED_IR_FIELD_IDS = Set.of(
            HydrologyIrModuleV2.BED_ELEVATION_FIELD);

    private static final Set<String> RIVER_FIELD_IDS;
    private static final Set<String> LAKE_FIELD_IDS;
    private static final Set<String> CANYON_FIELD_IDS;
    private static final Set<String> WATERFALL_FIELD_IDS;

    static {
        Set<String> river = new HashSet<>(RIVER_ONLY_FIELD_IDS);
        river.addAll(SHARED_IR_FIELD_IDS);
        RIVER_FIELD_IDS = Set.copyOf(river);
        Set<String> lake = new HashSet<>(LAKE_ONLY_FIELD_IDS);
        lake.addAll(SHARED_IR_FIELD_IDS);
        LAKE_FIELD_IDS = Set.copyOf(lake);
        Set<String> canyon = new HashSet<>(CANYON_ONLY_FIELD_IDS);
        canyon.addAll(CANYON_SHARED_IR_FIELD_IDS);
        CANYON_FIELD_IDS = Set.copyOf(canyon);
        Set<String> waterfall = new HashSet<>(WATERFALL_ONLY_FIELD_IDS);
        waterfall.addAll(WATERFALL_SHARED_IR_FIELD_IDS);
        WATERFALL_FIELD_IDS = Set.copyOf(waterfall);
    }

    private static final Set<MeanderingRiverGeneratorV2.RiverField> MAXIMUM_MERGED_RIVER_FIELDS = Set.of(
            MeanderingRiverGeneratorV2.RiverField.CHANNEL_MASK,
            MeanderingRiverGeneratorV2.RiverField.BANK_MASK,
            MeanderingRiverGeneratorV2.RiverField.FLOODPLAIN_MASK,
            MeanderingRiverGeneratorV2.RiverField.MEANDER_CORRIDOR,
            MeanderingRiverGeneratorV2.RiverField.DISCHARGE_INDEX,
            MeanderingRiverGeneratorV2.RiverField.WATER_BODY_ID);

    private static final Set<LakeGeneratorV2.LakeField> MAXIMUM_MERGED_LAKE_FIELDS = Set.of(
            LakeGeneratorV2.LakeField.BASIN_MASK,
            LakeGeneratorV2.LakeField.RIM_MASK,
            LakeGeneratorV2.LakeField.SPILLWAY_MASK,
            LakeGeneratorV2.LakeField.WATER_BODY_ID);

    private static final Set<CanyonGeneratorV2.CanyonField> MAXIMUM_MERGED_CANYON_FIELDS = Set.of(
            CanyonGeneratorV2.CanyonField.CANYON_MASK,
            CanyonGeneratorV2.CanyonField.FLOOR_MASK,
            CanyonGeneratorV2.CanyonField.RIM_MASK,
            CanyonGeneratorV2.CanyonField.TERRACE_MASK);

    private static final Set<WaterfallGeneratorV2.WaterfallField> MAXIMUM_MERGED_WATERFALL_FIELDS = Set.of(
            WaterfallGeneratorV2.WaterfallField.LIP_MASK,
            WaterfallGeneratorV2.WaterfallField.BASE_MASK,
            WaterfallGeneratorV2.WaterfallField.PLUNGE_POOL_MASK);

    private static final PipelineDescriptor DESCRIPTOR = new PipelineDescriptor(
            PIPELINE_ID,
            new HandlerSet(
                    GENERATOR_HANDLER_ID,
                    VALIDATOR_HANDLER_ID,
                    PREVIEW_HANDLER_ID,
                    EXPORT_HANDLER_ID),
            List.of(
                    TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                    TerrainIntentV2.FeatureKind.CANYON,
                    TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                    TerrainIntentV2.FeatureKind.LAKE,
                    TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                    TerrainIntentV2.FeatureKind.RIVER,
                    TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                    TerrainIntentV2.FeatureKind.SANDY_BEACH,
                    TerrainIntentV2.FeatureKind.WATERFALL),
            List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
            ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE);

    private final CoastalSurfaceExportPipelineV2 coastal = new CoastalSurfaceExportPipelineV2();
    private final LandformV2DataCodec data = new LandformV2DataCodec();
    private final DeterministicHydrologyRoutingSolverV2 routingSolver =
            new DeterministicHydrologyRoutingSolverV2();
    private final HydrologyRoutingBundlePublisherV2 routingPublisher =
            new HydrologyRoutingBundlePublisherV2();
    private final BoundedHydrologyReconcilerV2 reconciler = new BoundedHydrologyReconcilerV2();
    private final HydrologyReconciliationPlanCompilerV2 reconciliationPlanCompiler =
            new HydrologyReconciliationPlanCompilerV2();
    private final HydrologyValidatorV2 validator = new HydrologyValidatorV2();

    @Override
    public PipelineDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GeneratedSurface generate(
            GenerationRequestV2 request,
            Path requestSource,
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
            Path requestSource,
            TerrainIntentV2 draftIntent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        return completeHydrology(
                prepareHydrology(request, requestSource, draftIntent, baseline, workRoot, budget, token),
                SurfaceMaterializationV2.builtIn(), token);
    }

    /**
     * Everything of a hydrology Release except the final canonical block stream: the prepared coastal
     * surface, the frozen routing／reconciliation artifacts, the global river bed freeze, field-only
     * validation and the diagnostic preview set.
     *
     * <p>V2-19-10 split this out of {@link #generateHydrology} for the same reason V2-19-05 split
     * the coastal stage: the environment pipeline seals its material and palette plans from this
     * Blueprint and must bind them to the resolver <em>before</em> the first tile is written. Tile
     * writing stays the single place the final canonical block stream is produced.</p>
     */
    PreparedHydrologyV2 prepareHydrology(
            GenerationRequestV2 request,
            Path requestSource,
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

        // V2-19-05: the coastal surface is prepared without its tiles first, so routing,
        // reconciliation and the river bed freeze all complete before the final canonical block
        // stream is written. Nothing here re-derives geometry per tile.
        CoastalSurfaceExportPipelineV2.PreparedCoastalSurfaceV2 prepared =
                coastal.prepare(request, requestSource, draftIntent, baseline, surfaceRoot, budget, token);
        WorldBlueprintV2 blueprint = prepared.blueprint();
        CoastalSurfaceFieldsV2 fields = prepared.fields();
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
        // V2-15-10: a RIVER / MEANDERING_RIVER plan freezes non-empty REACH_BED reconciliation
        // variables (HydrologyReconciliationPlanCompilerV2.compileRivers); the coastal-only empty-graph
        // path still freezes zero variables, so the shared baseline state is correct for both.
        HydrologyReconciliationArtifactV2 reconciliationArtifact = reconciler.reconcile(
                blueprint.canonicalChecksum(),
                blueprint.hydrologyReconciliationPlan(),
                reconciliationPlanCompiler.baselineState(blueprint.hydrologyReconciliationPlan()),
                token);
        Path reconciliationArtifactPath = hydrologyRoot.resolve("reconciliation-artifact.json");
        new HydrologyReconciliationArtifactCodecV2().write(
                reconciliationArtifactPath, reconciliationArtifact);

        // V2-19-05 global route freeze: the reconciled route becomes one bounded, immutable river bed
        // field here — after routing and reconciliation, before the first tile — and is applied to the
        // single resolver every tile is written from (CARVE_SOLID then ADD_FLUID, fluid owned by the
        // fill). A run without a river plan freezes nothing and stays byte-identical.
        Optional<RiverBedMaterializationV2.FrozenRiverBedV2> riverBed = RiverBedMaterializationV2.freeze(
                blueprint.meanderingRiverPlans(), routingResult, reconciliationArtifact, fields,
                request.bounds(), token);
        // V2-15-11: the same global freeze doctrine for LAKE. A closed basin's geometry does not
        // depend on routing or reconciliation, so the freeze only needs the sampled coastal fields
        // and the request bounds. A run without a lake plan freezes nothing and stays byte-identical.
        Optional<LakeBedMaterializationV2.FrozenLakeBedV2> lakeBed = LakeBedMaterializationV2.freeze(
                blueprint.lakePlans(), fields, request.bounds(), token);
        // V2-15-12: the same global freeze doctrine for CANYON. A canyon's geometry is fully derived
        // from its shared river's already-frozen centerline/bed (validated by the river freeze above),
        // so this freeze also only needs the sampled coastal fields and the request bounds. A run
        // without a canyon plan freezes nothing and stays byte-identical.
        Optional<CanyonBedMaterializationV2.FrozenCanyonBedV2> canyonBed = CanyonBedMaterializationV2.freeze(
                blueprint.canyonPlans(), fields, request.bounds(), token);
        // V2-15-13: the same global freeze doctrine for WATERFALL. The plunge basin is cut below the
        // host river's already-frozen channel, so the freeze consumes that channel to keep the two
        // materializations disjoint. A run without a waterfall plan freezes nothing and stays
        // byte-identical.
        Optional<WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2> waterfallBasin =
                WaterfallBasinMaterializationV2.freeze(
                        blueprint.waterfallPlans(), riverBed, fields, request.bounds(), token);

        HydrologyFieldSamplerV2 sampler = sharedFieldSampler(
                routingResult, blueprint.meanderingRiverPlans(), blueprint.lakePlans(),
                blueprint.canyonPlans(), blueprint.waterfallPlans());
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

        return new PreparedHydrologyV2(
                prepared, request.bounds(), blueprint, fields, routingResult, riverBed, lakeBed, canyonBed,
                waterfallBasin,
                hydrologyPlanPath, routingRoot, reconciliationPlanPath, reconciliationArtifactPath,
                hydrologyValidationPath, hydrologyPreviewRoot);
    }

    /**
     * Writes the final canonical block stream through the given surface materialization and
     * assembles the hydrology Release source.
     */
    GeneratedHydrology completeHydrology(
            PreparedHydrologyV2 prepared,
            SurfaceMaterializationV2 materialization,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(materialization, "materialization");
        CoastalSurfaceExportPipelineV2.GeneratedCoastalSurface coastalGenerated =
                coastal.completeWithTiles(
                        prepared.surface(),
                        combinedOverlay(prepared.riverBed(), prepared.lakeBed(), prepared.canyonBed(),
                                prepared.waterfallBasin()),
                        materialization,
                        token);
        GeneratedSurface surface = coastalGenerated.surface();

        HydrologyReleaseSourceV2 source = new HydrologyReleaseSourceV2(
                surface.source(),
                prepared.hydrologyPlanPath(),
                prepared.routingRoot().resolve("index.json"),
                prepared.routingRoot(),
                prepared.reconciliationPlanPath(),
                prepared.reconciliationArtifactPath(),
                prepared.validationPath(),
                prepared.previewRoot().resolve("index.json"),
                prepared.previewRoot());
        return new GeneratedHydrology(
                source,
                prepared.blueprint(),
                new CoastalSurfaceTerrainQueryV2(
                        prepared.fields(),
                        coastalGenerated.blockResolver(),
                        prepared.bounds().minY(),
                        prepared.bounds().maxY()));
    }

    /** Complete shared hydrology state, final tiles excluded (V2-19-10). */
    record PreparedHydrologyV2(
            CoastalSurfaceExportPipelineV2.PreparedCoastalSurfaceV2 surface,
            GenerationRequestV2.Bounds bounds,
            WorldBlueprintV2 blueprint,
            CoastalSurfaceFieldsV2 fields,
            HydrologyRoutingResultV2 routing,
            Optional<RiverBedMaterializationV2.FrozenRiverBedV2> riverBed,
            Optional<LakeBedMaterializationV2.FrozenLakeBedV2> lakeBed,
            Optional<CanyonBedMaterializationV2.FrozenCanyonBedV2> canyonBed,
            Optional<WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2> waterfallBasin,
            Path hydrologyPlanPath,
            Path routingRoot,
            Path reconciliationPlanPath,
            Path reconciliationArtifactPath,
            Path validationPath,
            Path previewRoot
    ) {
        PreparedHydrologyV2 {
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(blueprint, "blueprint");
            Objects.requireNonNull(fields, "fields");
            Objects.requireNonNull(routing, "routing");
            Objects.requireNonNull(riverBed, "riverBed");
            Objects.requireNonNull(lakeBed, "lakeBed");
            Objects.requireNonNull(canyonBed, "canyonBed");
            Objects.requireNonNull(waterfallBasin, "waterfallBasin");
        }
    }

    /**
     * V2-15-11: composes the river-bed and lake-bed overlays declared before the first tile; V2-15-12
     * adds canyon and V2-15-13 the waterfall plunge basin. The fixed order is canyon, then river, then
     * lake, then waterfall, so a run carrying more than one never depends on evaluation order and the
     * one intentional overlap — a canyon's floor and its shared river's channel — always resolves to
     * the river's own channel/fluid decision (canyon is composed innermost, river wraps it, so river's
     * decorate answers first for the cells it claims and falls through to the canyon-decorated base
     * everywhere else). The waterfall basin is disjoint from the host channel by construction (its
     * freeze excludes every frozen channel cell), so its outermost position claims only cells no other
     * overlay decides. Any subset of the four may be absent; all absent stays byte-identical to the
     * coastal-only path.
     */
    private static Optional<CoastalSurfaceExportPipelineV2.SurfaceBlockOverlayV2> combinedOverlay(
            Optional<RiverBedMaterializationV2.FrozenRiverBedV2> riverBed,
            Optional<LakeBedMaterializationV2.FrozenLakeBedV2> lakeBed,
            Optional<CanyonBedMaterializationV2.FrozenCanyonBedV2> canyonBed,
            Optional<WaterfallBasinMaterializationV2.FrozenWaterfallBasinV2> waterfallBasin
    ) {
        if (riverBed.isEmpty() && lakeBed.isEmpty() && canyonBed.isEmpty() && waterfallBasin.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(base -> {
            TerrainBlockResolver resolver = base;
            if (canyonBed.isPresent()) {
                resolver = canyonBed.get().decorate(resolver);
            }
            if (riverBed.isPresent()) {
                resolver = riverBed.get().decorate(resolver);
            }
            if (lakeBed.isPresent()) {
                resolver = lakeBed.get().decorate(resolver);
            }
            if (waterfallBasin.isPresent()) {
                resolver = waterfallBasin.get().decorate(resolver);
            }
            return resolver;
        });
    }

    private static void requireSharedHydrologyOnly(WorldBlueprintV2 blueprint) throws IOException {
        // V2-15-10 / ADR 0039 Candidate A wired RIVER and MEANDERING_RIVER; V2-15-11 adds LAKE;
        // V2-15-12 adds CANYON; V2-15-13 adds WATERFALL. meanderingRiverPlans()/lakePlans()/
        // canyonPlans()/waterfallPlans() may be non-empty. Every other individual hydrology Feature
        // plan (delta/tidal/fjord/mountain/volcanic/mangrove/coral) and any non-empty global basin
        // graph remain rejected — including the plan-level fall nodes of a declared basin graph,
        // which are a different (graph-level) construct from the WATERFALL Feature plan.
        if (!blueprint.deltaPlans().isEmpty()
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

    /**
     * V2-15-10: adds a merged {@link MeanderingRiverGeneratorV2} sampler for any {@code RIVER} /
     * {@code MEANDERING_RIVER} plans on the Blueprint, without touching {@link MeanderingRiverGeneratorV2}
     * field math; V2-15-11 adds the same treatment for {@link LakeGeneratorV2} / {@code LAKE} plans;
     * V2-15-12 adds it for {@link CanyonGeneratorV2} / {@code CANYON} plans; V2-15-13 adds it for
     * {@link WaterfallGeneratorV2} / {@code WATERFALL} plans. Mask-shaped fields merge
     * with {@code MAX} (they are always {@code 0} or a positive code from every generator, never
     * {@code NO_DATA}); the remaining fields merge by taking the first non-{@code NO_DATA} value across
     * every contributing generator, in plan order, matching {@link HydrologyValidationInputV2#NO_DATA}'s
     * shared sentinel. River and lake share all four {@link HydrologyIrModuleV2} bed／water field ids;
     * canyon and waterfall share only {@code BED_ELEVATION_FIELD} (neither owns a 2.5D fluid field),
     * so a cell any contributing generator claims can add to that one shared id.
     */
    private static HydrologyFieldSamplerV2 sharedFieldSampler(
            HydrologyRoutingResultV2 routing,
            List<MeanderingRiverPlanV2> riverPlans,
            List<LakePlanV2> lakePlans,
            List<CanyonPlanV2> canyonPlans,
            List<WaterfallPlanV2> waterfallPlans
    ) {
        List<MeanderingRiverGeneratorV2> riverGenerators = riverPlans.stream()
                .map(MeanderingRiverGeneratorV2::new)
                .toList();
        List<LakeGeneratorV2> lakeGenerators = lakePlans.stream()
                .map(LakeGeneratorV2::new)
                .toList();
        List<CanyonGeneratorV2> canyonGenerators = canyonPlans.stream()
                .map(CanyonGeneratorV2::new)
                .toList();
        List<WaterfallGeneratorV2> waterfallGenerators = waterfallPlans.stream()
                .map(WaterfallGeneratorV2::new)
                .toList();
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
                boolean riverField = !riverGenerators.isEmpty() && RIVER_FIELD_IDS.contains(fieldId);
                boolean lakeField = !lakeGenerators.isEmpty() && LAKE_FIELD_IDS.contains(fieldId);
                boolean canyonField = !canyonGenerators.isEmpty() && CANYON_FIELD_IDS.contains(fieldId);
                boolean waterfallField =
                        !waterfallGenerators.isEmpty() && WATERFALL_FIELD_IDS.contains(fieldId);
                if (riverField || lakeField || canyonField || waterfallField) {
                    return mergedFieldValueAt(
                            riverField ? riverGenerators : List.of(),
                            lakeField ? lakeGenerators : List.of(),
                            canyonField ? canyonGenerators : List.of(),
                            waterfallField ? waterfallGenerators : List.of(),
                            fieldId, globalX, globalZ);
                }
                return HydrologyValidationInputV2.NO_DATA;
            }
        };
    }

    private static int mergedFieldValueAt(
            List<MeanderingRiverGeneratorV2> riverGenerators,
            List<LakeGeneratorV2> lakeGenerators,
            List<CanyonGeneratorV2> canyonGenerators,
            List<WaterfallGeneratorV2> waterfallGenerators,
            String fieldId,
            int globalX,
            int globalZ
    ) {
        boolean maximumMerge = isMaximumMergedField(fieldId);
        int accumulated = maximumMerge ? 0 : HydrologyValidationInputV2.NO_DATA;
        if (!riverGenerators.isEmpty()) {
            MeanderingRiverGeneratorV2.RiverField field = riverFieldFor(fieldId);
            for (MeanderingRiverGeneratorV2 generator : riverGenerators) {
                if (globalX < 0 || globalX >= generator.width() || globalZ < 0 || globalZ >= generator.length()) {
                    continue;
                }
                int raw = generator.sampleAt(globalX, globalZ, NO_HARD_ROUTE_CONFLICT).rawValue(field);
                if (raw == MeanderingRiverGeneratorV2.NO_DATA) {
                    continue;
                }
                accumulated = maximumMerge
                        ? Math.max(accumulated, raw)
                        : (accumulated == HydrologyValidationInputV2.NO_DATA ? raw : accumulated);
            }
        }
        if (!lakeGenerators.isEmpty()) {
            LakeGeneratorV2.LakeField field = lakeFieldFor(fieldId);
            for (LakeGeneratorV2 generator : lakeGenerators) {
                if (globalX < 0 || globalX >= generator.width() || globalZ < 0 || globalZ >= generator.length()) {
                    continue;
                }
                int raw = generator.sampleAt(globalX, globalZ, NO_HARD_ROUTE_CONFLICT).rawValue(field);
                if (raw == LakeGeneratorV2.NO_DATA) {
                    continue;
                }
                accumulated = maximumMerge
                        ? Math.max(accumulated, raw)
                        : (accumulated == HydrologyValidationInputV2.NO_DATA ? raw : accumulated);
            }
        }
        if (!canyonGenerators.isEmpty()) {
            CanyonGeneratorV2.CanyonField field = canyonFieldFor(fieldId);
            for (CanyonGeneratorV2 generator : canyonGenerators) {
                if (globalX < 0 || globalX >= generator.width() || globalZ < 0 || globalZ >= generator.length()) {
                    continue;
                }
                int raw = generator.sampleAt(globalX, globalZ, NO_HARD_ROUTE_CONFLICT).rawValue(field);
                if (raw == CanyonGeneratorV2.NO_DATA) {
                    continue;
                }
                accumulated = maximumMerge
                        ? Math.max(accumulated, raw)
                        : (accumulated == HydrologyValidationInputV2.NO_DATA ? raw : accumulated);
            }
        }
        if (!waterfallGenerators.isEmpty()) {
            WaterfallGeneratorV2.WaterfallField field = waterfallFieldFor(fieldId);
            for (WaterfallGeneratorV2 generator : waterfallGenerators) {
                if (globalX < 0 || globalX >= generator.width() || globalZ < 0 || globalZ >= generator.length()) {
                    continue;
                }
                int raw = generator.sampleAt(globalX, globalZ, NO_HARD_ROUTE_CONFLICT).rawValue(field);
                if (raw == WaterfallGeneratorV2.NO_DATA) {
                    continue;
                }
                accumulated = maximumMerge
                        ? Math.max(accumulated, raw)
                        : (accumulated == HydrologyValidationInputV2.NO_DATA ? raw : accumulated);
            }
        }
        return accumulated;
    }

    private static boolean isMaximumMergedField(String fieldId) {
        if (RIVER_ONLY_FIELD_IDS.contains(fieldId) || SHARED_IR_FIELD_IDS.contains(fieldId)) {
            if (MAXIMUM_MERGED_RIVER_FIELDS.contains(riverFieldFor(fieldId))) {
                return true;
            }
        }
        if (LAKE_ONLY_FIELD_IDS.contains(fieldId) || SHARED_IR_FIELD_IDS.contains(fieldId)) {
            if (MAXIMUM_MERGED_LAKE_FIELDS.contains(lakeFieldFor(fieldId))) {
                return true;
            }
        }
        if (CANYON_ONLY_FIELD_IDS.contains(fieldId) || CANYON_SHARED_IR_FIELD_IDS.contains(fieldId)) {
            if (MAXIMUM_MERGED_CANYON_FIELDS.contains(canyonFieldFor(fieldId))) {
                return true;
            }
        }
        if (WATERFALL_ONLY_FIELD_IDS.contains(fieldId) || WATERFALL_SHARED_IR_FIELD_IDS.contains(fieldId)) {
            if (MAXIMUM_MERGED_WATERFALL_FIELDS.contains(waterfallFieldFor(fieldId))) {
                return true;
            }
        }
        return false;
    }

    private static MeanderingRiverGeneratorV2.RiverField riverFieldFor(String fieldId) {
        if (HydrologyRiverModuleV2.CHANNEL_MASK_FIELD_ID.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.CHANNEL_MASK;
        }
        if (HydrologyRiverModuleV2.BANK_MASK_FIELD_ID.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.BANK_MASK;
        }
        if (HydrologyRiverModuleV2.FLOODPLAIN_MASK_FIELD_ID.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.FLOODPLAIN_MASK;
        }
        if (HydrologyRiverModuleV2.MEANDER_CORRIDOR_FIELD_ID.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.MEANDER_CORRIDOR;
        }
        if (HydrologyRiverModuleV2.LOCAL_WIDTH_FIELD_ID.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.LOCAL_WIDTH;
        }
        if (HydrologyRiverModuleV2.DISCHARGE_INDEX_FIELD_ID.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.DISCHARGE_INDEX;
        }
        if (HydrologyIrModuleV2.BED_ELEVATION_FIELD.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.BED_ELEVATION;
        }
        if (HydrologyIrModuleV2.WATER_SURFACE_FIELD.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.WATER_SURFACE;
        }
        if (HydrologyIrModuleV2.WATER_DEPTH_FIELD.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.WATER_DEPTH;
        }
        if (HydrologyIrModuleV2.WATER_BODY_ID_FIELD.equals(fieldId)) {
            return MeanderingRiverGeneratorV2.RiverField.WATER_BODY_ID;
        }
        throw new IllegalArgumentException("unknown river field id: " + fieldId);
    }

    private static LakeGeneratorV2.LakeField lakeFieldFor(String fieldId) {
        if (HydrologyLakeModuleV2.BASIN_MASK_FIELD_ID.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.BASIN_MASK;
        }
        if (HydrologyLakeModuleV2.RIM_MASK_FIELD_ID.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.RIM_MASK;
        }
        if (HydrologyLakeModuleV2.SPILLWAY_MASK_FIELD_ID.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.SPILLWAY_MASK;
        }
        if (HydrologyLakeModuleV2.DEPTH_FIELD_ID.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.DEPTH;
        }
        if (HydrologyLakeModuleV2.FLOOR_HEIGHT_FIELD_ID.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.FLOOR_HEIGHT;
        }
        if (HydrologyLakeModuleV2.SURFACE_FIELD_ID.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.SURFACE;
        }
        if (HydrologyIrModuleV2.BED_ELEVATION_FIELD.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.BED_ELEVATION;
        }
        if (HydrologyIrModuleV2.WATER_SURFACE_FIELD.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.WATER_SURFACE;
        }
        if (HydrologyIrModuleV2.WATER_DEPTH_FIELD.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.WATER_DEPTH;
        }
        if (HydrologyIrModuleV2.WATER_BODY_ID_FIELD.equals(fieldId)) {
            return LakeGeneratorV2.LakeField.WATER_BODY_ID;
        }
        throw new IllegalArgumentException("unknown lake field id: " + fieldId);
    }

    private static CanyonGeneratorV2.CanyonField canyonFieldFor(String fieldId) {
        if (LandformCanyonModuleV2.CANYON_MASK_FIELD_ID.equals(fieldId)) {
            return CanyonGeneratorV2.CanyonField.CANYON_MASK;
        }
        if (LandformCanyonModuleV2.FLOOR_MASK_FIELD_ID.equals(fieldId)) {
            return CanyonGeneratorV2.CanyonField.FLOOR_MASK;
        }
        if (LandformCanyonModuleV2.RIM_MASK_FIELD_ID.equals(fieldId)) {
            return CanyonGeneratorV2.CanyonField.RIM_MASK;
        }
        if (LandformCanyonModuleV2.TERRACE_MASK_FIELD_ID.equals(fieldId)) {
            return CanyonGeneratorV2.CanyonField.TERRACE_MASK;
        }
        if (LandformCanyonModuleV2.SURFACE_HEIGHT_FIELD_ID.equals(fieldId)) {
            return CanyonGeneratorV2.CanyonField.SURFACE_HEIGHT;
        }
        if (LandformCanyonModuleV2.WALL_HEIGHT_FIELD_ID.equals(fieldId)) {
            return CanyonGeneratorV2.CanyonField.WALL_HEIGHT;
        }
        if (HydrologyIrModuleV2.BED_ELEVATION_FIELD.equals(fieldId)) {
            return CanyonGeneratorV2.CanyonField.BED_ELEVATION;
        }
        throw new IllegalArgumentException("unknown canyon field id: " + fieldId);
    }

    private static WaterfallGeneratorV2.WaterfallField waterfallFieldFor(String fieldId) {
        if (HydrologyWaterfallModuleV2.LIP_MASK_FIELD_ID.equals(fieldId)) {
            return WaterfallGeneratorV2.WaterfallField.LIP_MASK;
        }
        if (HydrologyWaterfallModuleV2.BASE_MASK_FIELD_ID.equals(fieldId)) {
            return WaterfallGeneratorV2.WaterfallField.BASE_MASK;
        }
        if (HydrologyWaterfallModuleV2.PLUNGE_POOL_MASK_FIELD_ID.equals(fieldId)) {
            return WaterfallGeneratorV2.WaterfallField.PLUNGE_POOL_MASK;
        }
        if (HydrologyWaterfallModuleV2.LIP_ELEVATION_FIELD_ID.equals(fieldId)) {
            return WaterfallGeneratorV2.WaterfallField.LIP_ELEVATION;
        }
        if (HydrologyWaterfallModuleV2.BASE_ELEVATION_FIELD_ID.equals(fieldId)) {
            return WaterfallGeneratorV2.WaterfallField.BASE_ELEVATION;
        }
        if (HydrologyWaterfallModuleV2.PLUNGE_POOL_FLOOR_FIELD_ID.equals(fieldId)) {
            return WaterfallGeneratorV2.WaterfallField.PLUNGE_POOL_FLOOR;
        }
        if (HydrologyIrModuleV2.BED_ELEVATION_FIELD.equals(fieldId)) {
            return WaterfallGeneratorV2.WaterfallField.BED_ELEVATION;
        }
        throw new IllegalArgumentException("unknown waterfall field id: " + fieldId);
    }
}
