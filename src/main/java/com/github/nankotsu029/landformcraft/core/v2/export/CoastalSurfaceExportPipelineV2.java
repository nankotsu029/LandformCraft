package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticGateContractV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.material.SurfaceMaterializationV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldValueSource;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.CoastalValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionException;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.CoastalPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticFieldFactoryV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationInputV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationReportV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidatorV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetDrivenValidatorV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetValidationReportV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Production generation stage of the Release 2 export path (V2-12-02).
 *
 * <p>Turns a sealed {@code GenerationRequestV2} plus a design-stage {@code TerrainIntentV2} into
 * the complete sealed input set of one {@code surface-2_5d} Release: constraint field sidecars,
 * frozen Blueprint, field-only coastal validation, the fixed diagnostic preview set, and the
 * offline Sponge v3 tiles. It publishes nothing; {@link Release2ExportApplicationServiceV2} owns
 * staging, strict read-back, and atomic publish.</p>
 *
 * <p>Tile geometry comes from {@link TilePlanV2}, so a published Release opens directly through the
 * V2-6-20 verified canonical block source without any re-tiling.</p>
 */
final class CoastalSurfaceExportPipelineV2 implements ProductionExportPipelineV2 {
    static final String PIPELINE_ID = "v2.production.surface-2_5d.coastal";
    static final String GENERATOR_HANDLER_ID = "v2.coast.surface-generator";
    static final String VALIDATOR_HANDLER_ID = "v2.coast.field-validator";
    static final String PREVIEW_HANDLER_ID = "v2.coast.diagnostic-preview";
    static final String EXPORT_HANDLER_ID = "v2.release.surface-2_5d-export";
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
                    // V2-19-07: PLAIN executes on this pipeline as a macro foundation producer, not as
                    // a fifth coastal modifier — the macro foundation stage resolves it before the
                    // modifiers compose (ADR 0038 D1/D5-1). Its dispatch route is OFFLINE_PRODUCTION
                    // (ADR 0039 Candidate A): the coastal four stay the exact PRODUCTION_CONNECTED set.
                    TerrainIntentV2.FeatureKind.PLAIN,
                    TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                    TerrainIntentV2.FeatureKind.SANDY_BEACH),
            List.of(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS),
            List.of("surface-2_5d"));

    private final LandformV2DataCodec data = new LandformV2DataCodec();
    private final ConstraintFieldIndexCodecV2 indexCodec = new ConstraintFieldIndexCodecV2();
    private final DiagnosticGateContractV2 gateContract = DiagnosticGateContractV2.builtIn();
    private final TargetDrivenValidatorV2 targetValidator = TargetDrivenValidatorV2.builtIn();
    private final MacroFoundationStageV2 foundationStage = new MacroFoundationStageV2();
    private final MaskFeatureReconcileStageV2 reconcileStage = new MaskFeatureReconcileStageV2();
    private final SurfaceFoundationOwnerGateV2 ownerGate = new SurfaceFoundationOwnerGateV2();

    @Override
    public PipelineDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public GeneratedSurface generate(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 draftIntent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        return generateWithFields(request, requestSource, draftIntent, baseline, workRoot, budget, token)
                .surface();
    }

    /**
     * Same coastal generation as {@link #generate}, also retaining the release-local fields so a
     * hydrology overlay can sample provisional surface height without regenerating the coast.
     */
    GeneratedCoastalSurface generateWithFields(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 draftIntent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        return completeWithTiles(
                prepare(request, requestSource, draftIntent, baseline, workRoot, budget, token),
                Optional.empty(), SurfaceMaterializationV2.builtIn(), token);
    }

    /**
     * Everything of a coastal surface Release except the offline tiles: sealed request and intent,
     * constraint field sidecars, frozen Blueprint, the fail-closed gates, field-only validation and
     * the diagnostic preview set.
     *
     * <p>V2-19-05 split this out of {@link #generateWithFields} so a downstream stage can freeze its
     * own global route — the reconciled river route — from the frozen Blueprint and the finished
     * surface fields <em>before</em> the first tile is written, and then contribute one
     * {@link SurfaceBlockOverlayV2} to {@link #completeWithTiles}. Tile writing stays the single
     * place the final canonical block stream is produced.</p>
     */
    PreparedCoastalSurfaceV2 prepare(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 draftIntent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(requestSource, "requestSource");
        Objects.requireNonNull(draftIntent, "draftIntent");
        Objects.requireNonNull(baseline, "baseline");
        token.throwIfCancellationRequested();
        if (!request.requestId().equals(draftIntent.intentId())) {
            throw new IOException("terrain intent does not belong to the generation request");
        }
        GenerationRequestV2.Bounds bounds = request.bounds();
        int width = bounds.width();
        int length = bounds.length();
        ScaleClassV2 scale = ScaleClassV2.forDimensions(width, length);
        if (scale == ScaleClassV2.LARGE) {
            throw new IOException("LARGE Release 2 export is not supported until the V2-8 streaming gates close");
        }
        ScaleProfileV2 profile = ScaleProfileV2.defaults(scale);
        TilePlanV2 tilePlan = TilePlanV2.of(width, length, profile);
        budget.requireAdmitted(width, length, tilePlan.tileCount(), profile);

        Files.createDirectories(workRoot);
        Path requestPath = workRoot.resolve("generation-request.json");
        data.writeGenerationRequest(requestPath, request);
        String requestChecksum = data.generationRequestChecksum(request);

        // V2-18-09 macro foundation stage (ADR 0038 D5-1): with a complete explicit foundation input
        // (HARD LAND_WATER_MASK + declared per-medium base levels) the coastal four compose as
        // modifiers over the mask-derived background owner and the baseline argument is ignored
        // (D8-1). Without it the legacy baseline path stays byte-identical (D8-2). The maps are bound
        // once here (V2-19-14) because the reconcile pre-pass needs the HARD mask before the producer
        // layers — which the pre-pass may move — are built.
        Optional<MacroFoundationStageV2.FoundationInputsV2> foundationInputs;
        try {
            foundationInputs = foundationStage.bindInputs(request, requestSource, draftIntent, token);
        } catch (SurfaceFoundationExceptionV2 exception) {
            throw new IOException("macro foundation resolution failed [" + exception.failureCode() + "]: "
                    + exception.getMessage(), exception);
        }
        WorldBlueprintV2 draftBlueprint = compile(request, draftIntent, requestChecksum, bounds);
        CoastalGeneratorRuntimeV2 runtime = CoastalGeneratorRuntimeV2.create(draftBlueprint);
        // V2-19-14 (ADR 0043): align the declared geometry with the HARD mask by one bounded rigid
        // translation, before anything is compiled for keeps. Declared-only and opt-in: without the
        // request field, and without an explicit foundation input to align against, nothing runs and
        // the whole path is byte-identical. The pre-pass never moves the mask and never relaxes a
        // gate — geometry that already agrees is left exactly where the author put it.
        Optional<MaskFeatureReconcileV2> reconcile = Optional.empty();
        TerrainIntentV2 alignedIntent = draftIntent;
        if (foundationInputs.isPresent() && request.maskFeatureReconcile().isPresent()) {
            MaskFeatureReconcileStageV2.ReconciledIntentV2 reconciled;
            try {
                reconciled = reconcileStage.reconcile(
                        request.maskFeatureReconcile().get(), draftIntent, runtime,
                        foundationInputs.get().mask(), width, length, token);
            } catch (SurfaceFoundationExceptionV2 exception) {
                throw new IOException("mask feature reconcile failed [" + exception.failureCode()
                        + "]: " + exception.getMessage(), exception);
            } catch (CoastalTransitionException exception) {
                throw new IOException("coastal modifier composition rejected the declared geometry ["
                        + exception.ruleId() + "]: " + exception.getMessage(), exception);
            }
            reconcile = Optional.of(reconciled.report());
            if (reconciled.report().applied()) {
                alignedIntent = reconciled.intent();
                draftBlueprint = compile(request, alignedIntent, requestChecksum, bounds);
                runtime = CoastalGeneratorRuntimeV2.create(draftBlueprint);
            }
        }
        Optional<MacroFoundationV2> foundation;
        try {
            TerrainIntentV2 foundationIntent = alignedIntent;
            foundation = foundationInputs.map(
                    inputs -> foundationStage.resolve(inputs, request, foundationIntent));
        } catch (SurfaceFoundationExceptionV2 exception) {
            throw new IOException("macro foundation resolution failed [" + exception.failureCode() + "]: "
                    + exception.getMessage(), exception);
        }
        // ADR 0038 D8-1: on the foundation path the baseline argument is fully ignored — not even
        // validated — because it contributes nothing; the legacy path still validates and uses it.
        if (foundation.isEmpty()) {
            baseline.requireWithin(bounds.minY(), bounds.maxY());
        }
        CoastalSurfaceFieldsV2 fields;
        try {
            fields = foundation.isPresent()
                    ? CoastalSurfaceFieldsV2.generate(runtime, width, length, foundation.get(), token)
                    : CoastalSurfaceFieldsV2.generate(runtime, width, length, baseline, token);
        } catch (SurfaceFoundationExceptionV2 exception) {
            throw new IOException("macro foundation kernel invariant violated [" + exception.failureCode()
                    + "]: " + exception.getMessage(), exception);
        } catch (CoastalTransitionException exception) {
            // A modifier claiming a cell against the HARD mask is an intent/geometry contradiction;
            // the export spine surfaces it as the usual fail-closed IOException instead of leaking a
            // generator runtime exception (rule id preserved for the operator).
            throw new IOException("coastal modifier composition rejected the HARD land-water input ["
                    + exception.ruleId() + "]: " + exception.getMessage(), exception);
        }
        if (fields.hardProtectedCells() < 1) {
            throw new IOException("coastal export produced no HARD-protected land-water cells");
        }

        Path constraintRoot = workRoot.resolve("constraints");
        List<FieldArtifactDescriptorV2> descriptors = writeConstraintFields(
                constraintRoot, request, fields, foundation, width, length, token);

        // V2-18-07: bind each desired reference to the declared INPUT digest of its source, not to the
        // field the pipeline just generated. The old self-rebinding to desired.semanticChecksum() made
        // "desired" indistinguishable from "actual"; binding to the request's declared constraint-map
        // digest records the honest provenance (the input map the desired field is meant to reproduce).
        // V2-19-06 does this per declared source instead of for the single map the path used to allow.
        // V2-19-14 (ADR 0043 D5): the sealed intent carries the reconciled geometry, so the published
        // Release describes the terrain it actually contains; the applied offset is reported instead.
        TerrainIntentV2 intent = withDeclaredInputDigests(alignedIntent, request, foundation.isPresent());
        Path intentPath = workRoot.resolve("terrain-intent.json");
        data.writeTerrainIntent(intentPath, intent);
        String intentChecksum = data.terrainIntentChecksum(intent);

        WorldBlueprintV2 blueprint = compile(request, intent, requestChecksum, bounds);
        requireSameCoastalGeometry(draftBlueprint, blueprint);
        Path blueprintPath = workRoot.resolve("world-blueprint.json");
        data.writeWorldBlueprint(blueprintPath, blueprint);

        ConstraintFieldIndexV2 index = new ConstraintFieldIndexV2(
                1, request.requestId(), requestChecksum, intentChecksum,
                appliedBindings(intent, descriptors), descriptors);
        Path indexPath = constraintRoot.resolve("index.json");
        indexCodec.write(indexPath, index);
        indexCodec.readAndVerify(indexPath, constraintRoot, requestChecksum, intentChecksum, token);

        // V2-19-06: the guide is consumed by the same macro foundation stage as the mask, so an
        // honored HEIGHT_GUIDE stops being reported as an unconsumed HARD map reference.
        Set<TerrainIntentV2.ConstraintMapRole> consumedMapRoles = consumedMapRoles(foundation);
        IntentContributionCoverageV2 coverage = IntentContributionCoverageV2.compute(
                intent, fields, DESCRIPTOR.contractOnlyKinds(), gateContract, consumedMapRoles);
        // V2-18-10 (ADR 0038 D7-2/D8-2): the foundation owner metric V2-18-02 reported and V2-18-09
        // raised to 100% is now an export requirement. A legacy request without an explicit
        // foundation input reaches 0% here — a baseline fill is not an owner — and is rejected, and
        // the deprecated baseline argument cannot override it. Nothing is published; the gate runs
        // before the diagnostic previews and offline tiles so a doomed run stops early.
        ownerGate.requireFullCoverage(PIPELINE_ID, coverage, foundation.isPresent()
                ? "the resolved macro foundation does not cover the whole surface domain"
                : "the request declares no explicit macro foundation input (a HARD LAND_WATER_MASK "
                        + "map reference plus foundationBaseLevels), so no cell has a foundation owner");

        // V2-18-09: on the foundation path the desired sampler reads the resolved INPUT mask, so the
        // residual metrics measure conformance against the declared intent instead of the former
        // desired == actual self-reference (audit item 3). V2-19-06 adds the declared elevation guide,
        // which turns coastal.height.residual-max from a structurally-zero metric into a measurement.
        // The legacy path keeps the self-sampled input unchanged until V2-18-10 retires it.
        CoastalValidationInputV2 validationInput = foundation.isPresent()
                ? new CoastalValidationInputV2(blueprint, inputDesiredSampler(foundation.get(), fields), fields)
                : new CoastalValidationInputV2(blueprint, fields, fields);
        CoastalValidationReportV2 report = new CoastalValidatorV2().validate(validationInput, token);
        if (!report.passesHardValidation()) {
            throw new IOException("coastal HARD validation failed: " + report.issues());
        }
        // V2-18-04: the common target-driven path evaluates the blueprint's ValidationTargetV2 list.
        // The built-in registry consumes v2.edge-classification targets; a HARD EDGE violation gates
        // export here, after generation produced the land-water field, before anything is published.
        TargetValidationReportV2 targetReport = targetValidator.validate(blueprint.validationTargets(), fields);
        if (targetReport.hasHardViolation()) {
            throw new IOException("target-driven HARD validation failed: " + targetReport.hardViolations());
        }
        Path validationPath = workRoot.resolve("coastal-validation.json");
        new CoastalValidationArtifactCodecV2().write(validationPath, new CoastalValidationArtifactV2(
                blueprint.canonicalChecksum(), CoastalValidationArtifactV2.VALIDATOR_VERSION,
                new CoastalValidationArtifactV2.CoastalValidationReport(report.metrics(), report.issues())));

        Path previewRoot = workRoot.resolve("previews");
        CoastalPreviewIndexV2 previews = new CoastalDiagnosticPreviewRendererV2().render(
                previewRoot, blueprint.canonicalChecksum(),
                CoastalDiagnosticFieldFactoryV2.create(validationInput, report), token);

        List<ExportWarningV2> warnings = foundation.isPresent()
                ? List.of(ExportWarningV2.surfaceBaselineDeprecated())
                : List.of();
        return new PreparedCoastalSurfaceV2(
                bounds, tilePlan, blueprint, fields, coverage, reconcile, warnings,
                workRoot.resolve("tiles"), requestPath, intentPath, blueprintPath, indexPath,
                constraintRoot, validationPath, previewRoot);
    }

    /**
     * Writes the final canonical block stream and assembles the Release source. The optional overlay
     * and the surface materialization are applied once, to the single resolver every tile is written
     * from, so a route or a material decision frozen before this call cannot drift between tiles or
     * across a seam.
     */
    GeneratedCoastalSurface completeWithTiles(
            PreparedCoastalSurfaceV2 prepared,
            Optional<SurfaceBlockOverlayV2> overlay,
            SurfaceMaterializationV2 materialization,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(overlay, "overlay");
        Objects.requireNonNull(materialization, "materialization");
        GenerationRequestV2.Bounds bounds = prepared.bounds();
        TerrainBlockResolver resolver = prepared.resolver(overlay, materialization);
        List<SurfaceReleaseSourceV2.TileSource> tiles = writeTiles(
                prepared.tilesRoot(), prepared.tilePlan(), prepared.blueprint(), resolver,
                bounds.minY(), bounds.maxY(), token);

        SurfaceReleaseSourceV2 source = new SurfaceReleaseSourceV2(
                prepared.requestPath(), prepared.intentPath(), prepared.blueprintPath(),
                prepared.indexPath(), prepared.constraintRoot(), prepared.validationPath(),
                prepared.previewRoot().resolve("index.json"), prepared.previewRoot(), tiles);
        return new GeneratedCoastalSurface(
                new GeneratedSurface(source, prepared.blueprint(),
                        Optional.of(prepared.coverage()), prepared.maskFeatureReconcile(),
                        prepared.warnings()),
                prepared.fields(), resolver);
    }

    /**
     * Decorates the canonical surface block resolver before any tile is written (V2-19-05). The
     * decorated resolver is the only block source of the published Release, so an overlay is by
     * construction visible in the final canonical block stream the materialization gate measures.
     */
    @FunctionalInterface
    interface SurfaceBlockOverlayV2 {
        TerrainBlockResolver decorate(TerrainBlockResolver base);
    }

    /** Complete coastal surface state, tiles excluded. */
    record PreparedCoastalSurfaceV2(
            GenerationRequestV2.Bounds bounds,
            TilePlanV2 tilePlan,
            WorldBlueprintV2 blueprint,
            CoastalSurfaceFieldsV2 fields,
            IntentContributionCoverageV2 coverage,
            Optional<MaskFeatureReconcileV2> maskFeatureReconcile,
            List<ExportWarningV2> warnings,
            Path tilesRoot,
            Path requestPath,
            Path intentPath,
            Path blueprintPath,
            Path indexPath,
            Path constraintRoot,
            Path validationPath,
            Path previewRoot
    ) {
        PreparedCoastalSurfaceV2 {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(tilePlan, "tilePlan");
            Objects.requireNonNull(blueprint, "blueprint");
            Objects.requireNonNull(fields, "fields");
            Objects.requireNonNull(coverage, "coverage");
            Objects.requireNonNull(maskFeatureReconcile, "maskFeatureReconcile");
            warnings = List.copyOf(warnings);
        }

        TerrainBlockResolver resolver(
                Optional<SurfaceBlockOverlayV2> overlay,
                SurfaceMaterializationV2 materialization
        ) {
            TerrainBlockResolver base =
                    fields.resolver(bounds.minY(), bounds.waterLevel(), materialization);
            return overlay.map(value -> value.decorate(base)).orElse(base);
        }
    }

    /**
     * Desired sampler over the resolved foundation input: land-water comes straight from the mask and
     * surface height from the declared elevation guide, each only where its map specifies a value
     * (role no-data cells are excluded from comparison). A field no declared input constrains — and,
     * without a guide, that still includes surface height — has no desired value at all rather than a
     * self-sampled one.
     */
    private static CoastalFieldSamplerV2 inputDesiredSampler(
            MacroFoundationV2 foundation,
            CoastalSurfaceFieldsV2 fields
    ) {
        return new CoastalFieldSamplerV2() {
            @Override
            public int width() {
                return fields.width();
            }

            @Override
            public int length() {
                return fields.length();
            }

            @Override
            public int valueAt(String fieldId, int globalX, int globalZ) {
                if (CoastalTransitionModuleV2.LAND_WATER_FIELD_ID.equals(fieldId)) {
                    int desired = foundation.desiredLandWaterAt(globalX, globalZ);
                    return desired == 0 || desired == 1 ? desired : CoastalValidationInputV2.NO_DATA;
                }
                if (CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID.equals(fieldId)) {
                    // MacroFoundationV2.NO_HEIGHT and CoastalValidationInputV2.NO_DATA are the same
                    // sentinel, so an unguided cell simply drops out of the comparison.
                    return foundation.desiredHeightMillionthsAt(globalX, globalZ);
                }
                return CoastalValidationInputV2.NO_DATA;
            }
        };
    }

    /**
     * {@code blockResolver} is the exact resolver the published tiles were written from (V2-19-05):
     * downstream stages answer semantic queries from the final canonical block stream instead of
     * re-deriving a second, possibly divergent one.
     */
    record GeneratedCoastalSurface(
            GeneratedSurface surface,
            CoastalSurfaceFieldsV2 fields,
            TerrainBlockResolver blockResolver
    ) {
        GeneratedCoastalSurface {
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(fields, "fields");
            Objects.requireNonNull(blockResolver, "blockResolver");
        }
    }

    private WorldBlueprintV2 compile(
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            String requestChecksum,
            GenerationRequestV2.Bounds bounds
    ) {
        return new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                request.requestId(),
                new GenerationBounds(bounds.width(), bounds.length(), bounds.minY(), bounds.maxY(),
                        bounds.waterLevel()),
                request.generation().tileSize(),
                request.generation().globalSeed(),
                requestChecksum,
                DiagnosticCompileRequestV2.defaultBudget()), intent);
    }

    /**
     * The land-water binding is rebound to the input mask digest (V2-18-07), so the sealed Blueprint is
     * recompiled. Any change to the coastal geometry would silently invalidate the already sampled
     * fields, so it fails closed instead.
     */
    private static void requireSameCoastalGeometry(WorldBlueprintV2 draft, WorldBlueprintV2 sealed) {
        if (!draft.coastalTransitionPlans().equals(sealed.coastalTransitionPlans())
                || !draft.coastalFeaturePlans().equals(sealed.coastalFeaturePlans())
                || !draft.sandyBeachPlans().equals(sealed.sandyBeachPlans())
                || !draft.harborBasinPlans().equals(sealed.harborBasinPlans())
                || !draft.breakwaterHarborPlans().equals(sealed.breakwaterHarborPlans())
                || !draft.rockyCapePlans().equals(sealed.rockyCapePlans())) {
            throw new IllegalStateException("constraint rebinding changed the sealed coastal geometry");
        }
    }

    /**
     * Rebinds every declared map reference to the declared input digest of its own source (V2-19-06).
     *
     * <p>The single-binding form this replaces could only ever describe the land-water mask. Roles and
     * cardinality are validated by {@link MacroFoundationStageV2.FoundationInputRolesV2} on the
     * foundation path, so an intent that reaches here declares exactly what the foundation consumed;
     * on the legacy baseline path the pre-V2-19-06 single-mask requirement is kept verbatim.</p>
     */
    private static TerrainIntentV2 withDeclaredInputDigests(
            TerrainIntentV2 intent,
            GenerationRequestV2 request,
            boolean foundationPath
    ) {
        List<TerrainIntentV2.ConstraintMapBinding> declared = intent.mapReferences();
        if (!foundationPath && (declared.size() != 1
                || declared.getFirst().role() != TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK)) {
            throw new IllegalArgumentException(
                    "surface-2_5d export requires exactly one LAND_WATER_MASK map binding");
        }
        List<TerrainIntentV2.ConstraintMapBinding> bound = new ArrayList<>(declared.size());
        for (TerrainIntentV2.ConstraintMapBinding binding : declared) {
            GenerationRequestV2.ConstraintMapSource source = declaredSource(request, binding.sourceId());
            bound.add(new TerrainIntentV2.ConstraintMapBinding(
                    binding.id(),
                    binding.sourceId(),
                    binding.role(),
                    artifactPrefix(binding.role()) + source.expectedSha256(),
                    binding.strength(),
                    binding.sampling(),
                    binding.toleranceBlocks(),
                    binding.weightMillionths()));
        }
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                intent.features(), intent.relations(), intent.constraints(), intent.environment(),
                List.copyOf(bound), intent.structures(), intent.provenance());
    }

    /** Canonical artifact-id prefix per role, matching {@code TerrainIntentV2.ConstraintMapBinding}. */
    private static String artifactPrefix(TerrainIntentV2.ConstraintMapRole role) {
        return switch (role) {
            case LAND_WATER_MASK -> "constraint:land-water:sha256-";
            case HEIGHT_GUIDE -> "constraint:height-guide:sha256-";
            case ZONE_LABEL_MAP -> "constraint:zone-label-map:sha256-";
        };
    }

    private static GenerationRequestV2.ConstraintMapSource declaredSource(
            GenerationRequestV2 request,
            String sourceId
    ) {
        return request.constraintMaps().stream()
                .filter(source -> source.sourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no declared constraint-map source matches the binding sourceId " + sourceId));
    }

    /** One applied binding per sealed map reference, each owning exactly its own three sidecars. */
    private static List<ConstraintFieldIndexV2.AppliedBinding> appliedBindings(
            TerrainIntentV2 intent,
            List<FieldArtifactDescriptorV2> descriptors
    ) {
        List<ConstraintFieldIndexV2.AppliedBinding> bindings = new ArrayList<>(intent.mapReferences().size());
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            bindings.add(switch (binding.role()) {
                case LAND_WATER_MASK -> appliedBinding(binding, descriptors,
                        FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                        List.of(FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER),
                        List.of(new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"),
                                new ConstraintFieldIndexV2.LabelEntry(1, 1, "land")));
                case HEIGHT_GUIDE -> appliedBinding(binding, descriptors,
                        FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT,
                        List.of(FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT,
                                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT,
                                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT),
                        List.of());
                case ZONE_LABEL_MAP -> throw new IllegalStateException(
                        "surface-2_5d export has no zone label consumer");
            });
        }
        return List.copyOf(bindings);
    }

    private static ConstraintFieldIndexV2.AppliedBinding appliedBinding(
            TerrainIntentV2.ConstraintMapBinding binding,
            List<FieldArtifactDescriptorV2> descriptors,
            FieldArtifactDescriptorV2.FieldSemantic canonicalSemantic,
            List<FieldArtifactDescriptorV2.FieldSemantic> ownedSemantics,
            List<ConstraintFieldIndexV2.LabelEntry> labels
    ) {
        // V2-18-07 decouples the two artifact references that used to be conflated. The constraint field
        // index's canonical artifact id keeps identifying the DESIRED field artifact by its own semantic
        // checksum (an integrity invariant of the index), while the intent binding's artifactId now records
        // the INPUT map provenance. They are different concerns and are no longer forced to be equal.
        FieldArtifactDescriptorV2 canonical = descriptorFor(descriptors, canonicalSemantic);
        String canonicalArtifactId = artifactPrefix(binding.role()) + canonical.semanticChecksum();
        return new ConstraintFieldIndexV2.AppliedBinding(
                binding.id(), binding.sourceId(), binding.role(), binding.strength(), binding.sampling(),
                binding.toleranceBlocks(), binding.weightMillionths(), canonicalArtifactId,
                canonical.definition().fieldId(),
                ownedSemantics.stream()
                        .map(semantic -> descriptorFor(descriptors, semantic).definition().fieldId())
                        .toList(),
                labels);
    }

    private static Set<TerrainIntentV2.ConstraintMapRole> consumedMapRoles(
            Optional<MacroFoundationV2> foundation
    ) {
        if (foundation.isEmpty()) {
            return Set.of();
        }
        return foundation.get().heightGuide().isPresent()
                ? Set.of(TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                        TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE)
                : Set.of(TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK);
    }

    private static List<FieldArtifactDescriptorV2> writeConstraintFields(
            Path root,
            GenerationRequestV2 request,
            CoastalSurfaceFieldsV2 fields,
            Optional<MacroFoundationV2> foundation,
            int width,
            int length,
            CancellationToken token
    ) throws IOException {
        requireEveryDeclaredSourceIsConsumed(request, foundation);
        GenerationRequestV2.ConstraintMapSource map = foundation
                .map(value -> declaredSource(request, value.maskField().sourceId()))
                .orElseGet(() -> request.constraintMaps().getFirst());
        FieldArtifactDescriptorV2.Provenance provenance = provenanceOf(map);
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        List<FieldArtifactDescriptorV2> result = new ArrayList<>(6);
        // V2-18-09: on the foundation path DESIRED is the decoded input mask (with the established
        // role no-data sentinel), ACTUAL is the composed output, and RESIDUAL is their measured
        // difference — no longer the definitionally zero self-copy. The legacy path keeps the
        // self-sampled sidecars byte-identical.
        result.add(writer.write(root, "fields/coast-land-desired.lfgrid",
                definition("constraint.coast.land.desired",
                        FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.U8, 1_000_000L, width, length),
                provenance,
                foundation.<FieldValueSource>map(value ->
                        (x, z) -> value.desiredLandWaterAt(x, z))
                        .orElse((x, z) -> fields.landWaterAt(z * width + x)),
                token));
        result.add(writer.write(root, "fields/coast-land-actual.lfgrid",
                definition("constraint.coast.land.actual",
                        FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.U8, 1_000_000L, width, length),
                provenance, (x, z) -> fields.landWaterAt(z * width + x), token));
        result.add(writer.write(root, "fields/coast-land-residual.lfgrid",
                definition("constraint.coast.land.residual",
                        FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldValueType.I32, 1L, width, length),
                provenance,
                foundation.<FieldValueSource>map(value -> (x, z) -> {
                    int desired = value.desiredLandWaterAt(x, z);
                    if (desired != 0 && desired != 1) {
                        return 0;
                    }
                    return fields.landWaterAt(z * width + x) - desired;
                }).orElse((x, z) -> 0),
                token));
        Optional<MacroFoundationV2.HeightGuideV2> guide = foundation.flatMap(MacroFoundationV2::heightGuide);
        if (guide.isPresent()) {
            result.addAll(writeHeightGuideFields(root, request, fields, foundation.orElseThrow(),
                    guide.orElseThrow(), width, length, writer, token));
        }
        return List.copyOf(result);
    }

    /**
     * The elevation guide's desired / actual / residual sidecars (V2-19-06).
     *
     * <p>DESIRED is the decoded guide itself, ACTUAL is the composed surface height the Release was
     * written from, and RESIDUAL is {@code actual - desired} — the same orientation as the land-water
     * residual beside it. Where the guide declares no-data there is nothing to compare, so DESIRED and
     * RESIDUAL carry the canonical no-data sentinel while ACTUAL still records the height that was
     * actually generated there from the per-medium base level.</p>
     */
    private static List<FieldArtifactDescriptorV2> writeHeightGuideFields(
            Path root,
            GenerationRequestV2 request,
            CoastalSurfaceFieldsV2 fields,
            MacroFoundationV2 foundation,
            MacroFoundationV2.HeightGuideV2 guide,
            int width,
            int length,
            LfcGridWriterV1 writer,
            CancellationToken token
    ) throws IOException {
        GenerationRequestV2.ConstraintMapSource source = declaredSource(request, guide.field().sourceId());
        FieldArtifactDescriptorV2.Provenance provenance = provenanceOf(source);
        boolean hasNoData = source.encoding().noData() instanceof GenerationRequestV2.NoDataSentinel;
        FieldArtifactDescriptorV2.Sampling sampling =
                guide.field().binding().sampling() == TerrainIntentV2.Sampling.NEAREST
                        ? FieldArtifactDescriptorV2.Sampling.NEAREST
                        : FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED;
        FieldValueSource actual = (x, z) ->
                fields.valueAt(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, x, z);
        return List.of(
                writer.write(root, "fields/coast-height-desired.lfgrid",
                        heightDefinition("constraint.coast.height.desired",
                                FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT,
                                sampling, hasNoData, width, length),
                        provenance, foundation::desiredHeightMillionthsAt, token),
                writer.write(root, "fields/coast-height-actual.lfgrid",
                        heightDefinition("constraint.coast.height.actual",
                                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT,
                                sampling, hasNoData, width, length),
                        provenance, actual, token),
                writer.write(root, "fields/coast-height-residual.lfgrid",
                        heightDefinition("constraint.coast.height.residual",
                                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT,
                                sampling, hasNoData, width, length),
                        provenance, (x, z) -> {
                            int desired = foundation.desiredHeightMillionthsAt(x, z);
                            return desired == MacroFoundationV2.NO_HEIGHT
                                    ? MacroFoundationV2.NO_HEIGHT
                                    : Math.subtractExact(actual.rawValueAt(x, z), desired);
                        }, token));
    }

    /**
     * Every declared constraint-map source must be consumed by a binding this path honors, because a
     * published Release binds the two sets to each other. A source nothing reads would otherwise ship
     * as unverifiable provenance.
     */
    private static void requireEveryDeclaredSourceIsConsumed(
            GenerationRequestV2 request,
            Optional<MacroFoundationV2> foundation
    ) throws IOException {
        if (foundation.isEmpty()) {
            if (request.constraintMaps().size() != 1) {
                throw new IOException(
                        "surface-2_5d export requires exactly one declared constraint map source");
            }
            return;
        }
        MacroFoundationV2 resolved = foundation.orElseThrow();
        Set<String> consumed = new LinkedHashSet<>();
        consumed.add(resolved.maskField().sourceId());
        resolved.heightGuide().ifPresent(guide -> consumed.add(guide.field().sourceId()));
        Set<String> declared = request.constraintMaps().stream()
                .map(GenerationRequestV2.ConstraintMapSource::sourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!declared.equals(consumed)) {
            throw new IOException("surface-2_5d export requires every declared constraint map source to "
                    + "be bound to a consumed role; declared " + declared + ", consumed " + consumed);
        }
    }

    private static FieldArtifactDescriptorV2.Provenance provenanceOf(
            GenerationRequestV2.ConstraintMapSource source
    ) {
        return new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP,
                source.sourceId(), source.expectedSha256(), "numeric-png", "1", "pixel-center-v1");
    }

    private static FieldArtifactDescriptorV2.Definition definition(
            String id,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.FieldValueType type,
            long scale,
            int width,
            int length
    ) {
        return new FieldArtifactDescriptorV2.Definition(
                id, semantic, type, width, length,
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                FieldArtifactDescriptorV2.Sampling.NEAREST, scale, 0L, false, 0);
    }

    /** The canonical HEIGHT_GUIDE sidecar shape the constraint field index requires for the role. */
    private static FieldArtifactDescriptorV2.Definition heightDefinition(
            String id,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.Sampling sampling,
            boolean hasNoData,
            int width,
            int length
    ) {
        return new FieldArtifactDescriptorV2.Definition(
                id, semantic, FieldArtifactDescriptorV2.FieldValueType.I32, width, length,
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                sampling, 1L, 0L, hasNoData, hasNoData ? MacroFoundationV2.NO_HEIGHT : 0);
    }

    private static FieldArtifactDescriptorV2 descriptorFor(
            List<FieldArtifactDescriptorV2> descriptors,
            FieldArtifactDescriptorV2.FieldSemantic semantic
    ) {
        return descriptors.stream()
                .filter(descriptor -> descriptor.definition().semantic() == semantic)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing constraint field " + semantic));
    }

    private static List<SurfaceReleaseSourceV2.TileSource> writeTiles(
            Path root,
            TilePlanV2 tilePlan,
            WorldBlueprintV2 blueprint,
            TerrainBlockResolver resolver,
            int minY,
            int maxY,
            CancellationToken token
    ) throws IOException {
        Files.createDirectories(root);
        OfflineTileArtifactCodecV2 codec = new OfflineTileArtifactCodecV2();
        OfflineTileSchematicWriterV2 schematicWriter = new OfflineTileSchematicWriterV2();
        List<SurfaceReleaseSourceV2.TileSource> tiles = new ArrayList<>(tilePlan.tileCount());
        for (int index = 0; index < tilePlan.tileCount(); index++) {
            token.throwIfCancellationRequested();
            TilePlanV2.TileV2 tile = tilePlan.tileByIndex(index);
            OfflineTilePlanV2 plan = new OfflineTilePlanV2(
                    OfflineTilePlanV2.VERSION, tile.tileId(), tile.tileX(), tile.tileZ(),
                    tile.coreMinX(), tile.coreMinZ(), tile.coreWidth(), tile.coreLength(), minY, maxY);
            Path schematic = root.resolve(plan.defaultSchematicFileName());
            OfflineTileArtifactV2 artifact = schematicWriter.write(
                    schematic, plan, blueprint.canonicalChecksum(), resolver, token);
            Path metadata = root.resolve(tile.tileId() + ".json");
            codec.write(metadata, artifact);
            tiles.add(new SurfaceReleaseSourceV2.TileSource(artifact.tileId(), metadata, schematic));
        }
        return List.copyOf(tiles);
    }
}
