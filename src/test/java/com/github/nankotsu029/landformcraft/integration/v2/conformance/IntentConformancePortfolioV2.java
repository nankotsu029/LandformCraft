package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.format.v2.validation.HydrologyValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalRasterKernelV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionCompositorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionLayerSourcesV2;
import com.github.nankotsu029.landformcraft.model.v2.BreakwaterHarborPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetDrivenValidatorV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetEvaluationV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.ValidationFieldSamplerV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * V2-18-11 intent-conformance E2E portfolio.
 *
 * <p>The V2-18 audit (item 5) found that no layer asserted terrain <em>shape</em>: the existing tests
 * pinned determinism, checksums, strict verify and budget rejection, so a release whose north edge had
 * lost the mainland and whose features floated in isolation still passed every gate. This portfolio
 * closes that gap permanently. It measures a <em>published</em> Release — never in-process generator
 * state — and reports:</p>
 *
 * <ol>
 *   <li><b>EDGE conformance:</b> the published blueprint's {@code v2.edge-classification} targets are
 *       re-evaluated by the production {@link com.github.nankotsu029.landformcraft.validation.v2.target
 *       .EdgeClassificationEvaluatorV2} over the published ACTUAL land-water sidecar, so the measured
 *       edge shares are read back out of the artifact an operator would ship.</li>
 *   <li><b>beach ↔ backshore land continuity:</b> every beach-owned land band cell is land, lies in one
 *       land component, and that component also carries the declared backshore hinterland.</li>
 *   <li><b>breakwater arm land connection:</b> for each declared arm, whether its footprint reaches
 *       off-structure mainland — a breakwater whose "landfall" endpoint never lands is an isolated
 *       island, which the shape assertions must expose rather than tolerate.</li>
 * </ol>
 *
 * <p>Cases accumulate as later V2-18/V2-15 leaves connect more kinds; adding a case is adding one
 * {@link CaseV2} entry. The portfolio's promotion into the Phase gate is {@code V2-18-12}'s Task.</p>
 */
final class IntentConformancePortfolioV2 {
    /** Land value in the published land-water sidecar and in the EDGE evaluator's contract field. */
    static final int LAND = 1;
    static final int WATER = 0;

    private IntentConformancePortfolioV2() {
    }

    /**
     * Which production dispatch capability set the case exports through. {@code SURFACE} is the
     * plain {@code surface-2_5d} coastal path; {@code HYDROLOGY} is the V2-15-10 {@code hydrology-plan}
     * path (ADR 0039 Candidate A {@code OFFLINE_PRODUCTION} route), required for a case that declares
     * a {@code RIVER} / {@code MEANDERING_RIVER} feature alongside the coastal contributors.
     */
    enum ExportRouteV2 { SURFACE, HYDROLOGY }

    /**
     * One portfolio fixture. {@code shoreConnectedArmIds} is the arm set whose footprint currently
     * reaches off-structure mainland; it is an explicit expectation rather than a derived value so a
     * regression cannot silently disconnect an arm, and so a known non-conformance stays visible.
     */
    record CaseV2(
            String id,
            Path request,
            Path intent,
            SurfaceBaselineV2 baseline,
            int width,
            int length,
            Set<String> shoreConnectedArmIds,
            Set<String> declaredArmIds,
            ExportRouteV2 exportRoute,
            Set<TerrainIntentV2.FeatureKind> declaredCoastalKinds
    ) {
        CaseV2 {
            Objects.requireNonNull(id, "id");
            shoreConnectedArmIds = Set.copyOf(shoreConnectedArmIds);
            declaredArmIds = Set.copyOf(declaredArmIds);
            Objects.requireNonNull(exportRoute, "exportRoute");
            declaredCoastalKinds = Set.copyOf(declaredCoastalKinds);
        }

        /** The four-contributor set every pre-V2-19-09 case declares, plus the contract-only companion. */
        static Set<TerrainIntentV2.FeatureKind> coastalFourAndBackshore() {
            return Set.of(
                    TerrainIntentV2.FeatureKind.SANDY_BEACH,
                    TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                    TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                    TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                    TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS);
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** The permanent portfolio. Both original cases are the V2-18-03 gate-passing coastal contracts. */
    static List<CaseV2> cases() {
        return List.of(
                new CaseV2(
                        "harbor-cove-64-honored",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        // Both declared landfalls reach the mainland at this scale.
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.SURFACE,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        "coastal-honored-400",
                        Path.of("examples/v2/diagnostic/coastal-honored-400.request-v2.json"),
                        Path.of("examples/v2/diagnostic/coastal-honored-400.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 42),
                        400, 400,
                        // V2-18-13 corrected the fixture: the rocky cape's western edge was extended
                        // west (NW 0.62→0.60, SW 0.72→0.685) so the east breakwater arm's foundation
                        // toe reaches cape land and its declared landfall now sits on the mainland. The
                        // land-water mask was regenerated (composed output ∪ macro composition) and both
                        // arms now connect to shore.
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.SURFACE,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-15-10 / ADR 0039 Candidate A: the harbor-cove-64-honored coastal contract
                        // plus one RIVER feature, published through the hydrology-plan OFFLINE_PRODUCTION
                        // route so this leaf's per-leaf intent-conformance obligation is measured from a
                        // published Release rather than in-process generator state. The coastal shape
                        // fixture and land-water mask are otherwise identical to harbor-cove-64-honored.
                        // V2-19-05 shortened the reach so it stays on the macro foundation background:
                        // a channel crossing a coastal modifier's cells is now rejected fail-closed.
                        "harbor-cove-64-honored-river",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-river.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-river.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.HYDROLOGY,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-19-06: the same coastal contract plus an explicit HEIGHT_GUIDE, which the
                        // macro foundation reads as its background elevation source. The land-water
                        // mask is byte-identical to harbor-cove-64-honored, so every shape assertion
                        // of the portfolio must give the same answer while the heights change.
                        "harbor-cove-64-honored-guided",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-guided.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-guided.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.SURFACE,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-19-07: the same coastal contract plus one inland PLAIN feature — the
                        // first macro foundation producer. The land-water mask is byte-identical to
                        // harbor-cove-64-honored, so every shape assertion of the portfolio must give
                        // the same answer while the terrain the producer owns rises.
                        "harbor-cove-64-honored-plain",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-plain.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-plain.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.SURFACE,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-19-05: the same reach declared as the MEANDERING_RIVER kind. Both kinds
                        // share one materialization (V2-15-10 bridges RIVER onto the MEANDERING_RIVER
                        // compile path), so promoting both to MATERIALIZED needs the block effect of
                        // both measured here rather than inferred from one another.
                        "harbor-cove-64-honored-meander",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-meander.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-meander.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.HYDROLOGY,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-19-09 (ADR 0040 D1, size 1): the same contract reduced to the sandy beach
                        // alone — the subset the 2026-07-23 audit measured as impossible to export.
                        // Its HARD mask is regenerated by the same "active ? composed : background"
                        // rule V2-18-13 fixed for coastal-honored-400, so the cells the removed
                        // modifiers used to shape stay declared input owned by the macro foundation.
                        "harbor-cove-64-honored-beach",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-beach.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-beach.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of(), Set.of(),
                        ExportRouteV2.SURFACE,
                        Set.of(TerrainIntentV2.FeatureKind.SANDY_BEACH,
                                TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS)),
                new CaseV2(
                        // V2-19-09 (ADR 0040 D1, size 0): one inland PLAIN producer and the HARD mask,
                        // with no coastal modifier at all. The mask is byte-identical to
                        // harbor-cove-64-honored, so the declared EDGE contract must still be satisfied
                        // by the macro foundation on its own.
                        "harbor-cove-64-honored-coastless",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-coastless.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-coastless.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of(), Set.of(),
                        ExportRouteV2.SURFACE,
                        Set.of()),
                new CaseV2(
                        // V2-19-12 (ADR 0041): the same coastal contract plus coherent detail on the
                        // macro foundation background. The intent and the land-water mask are
                        // byte-identical to harbor-cove-64-honored — detail only moves the background
                        // elevation, never the medium — so every shape assertion of the portfolio must
                        // give the same answer while the background heights gain a bounded relief.
                        "harbor-cove-64-honored-detail",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-detail.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-detail.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.SURFACE,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-19-14 (ADR 0043): the same coastal contract with every declared feature
                        // two blocks south of the land-water mask it must honor, reconciled back by the
                        // opt-in pre-pass. The mask is byte-identical to harbor-cove-64-honored and the
                        // pre-pass restores the authored geometry exactly, so every shape assertion of
                        // the portfolio must give the four-contributor fixture's own answers.
                        "harbor-cove-64-honored-drift",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-drift.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-drift.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.SURFACE,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-15-11: the same coastal contract plus one closed LAKE feature, published
                        // through the hydrology-plan OFFLINE_PRODUCTION route so this leaf's per-leaf
                        // intent-conformance obligation is measured from a published Release rather than
                        // in-process generator state. The coastal shape fixture and land-water mask are
                        // otherwise identical to harbor-cove-64-honored; the basin stays on the macro
                        // foundation background (v2.lake.coastal-owner-conflict rejects otherwise).
                        "harbor-cove-64-honored-lake",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-lake.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-lake.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.HYDROLOGY,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-15-12: the harbor-cove-64-honored coastal contract plus one MEANDERING_RIVER
                        // reach and one CANYON feature bound HARD WITHIN it (a CANYON cannot be declared
                        // without its shared river). Published through the hydrology-plan
                        // OFFLINE_PRODUCTION route. The canyon owns no fluid — the shared river's
                        // channel/water stays the river's own — so the block-effect claim declares both
                        // kinds' classes, and CanyonBlockConformanceV2 separately confirms the canyon's
                        // own carve is dry.
                        "harbor-cove-64-honored-canyon",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-canyon.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-canyon.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.HYDROLOGY,
                        CaseV2.coastalFourAndBackshore()),
                new CaseV2(
                        // V2-15-13: the harbor-cove-64-honored coastal contract plus one
                        // MEANDERING_RIVER reach and one WATERFALL bound HARD ON_PATH_OF it (a
                        // WATERFALL cannot be declared without its host river). Published through the
                        // hydrology-plan OFFLINE_PRODUCTION route. The fall owns the plunge basin it
                        // cuts below the host bed and the water that basin holds; the host keeps its
                        // own channel columns, so the block-effect claim declares both kinds' classes
                        // and WaterfallBlockConformanceV2 separately confirms the basin's depth, the
                        // measured fall head and the containment envelope.
                        "harbor-cove-64-honored-waterfall",
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-waterfall.request-v2.json"),
                        Path.of("examples/v2/diagnostic/harbor-cove-64-honored-waterfall.terrain-intent-v2.json"),
                        new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46),
                        64, 64,
                        Set.of("west-arm", "east-arm"),
                        Set.of("west-arm", "east-arm"),
                        ExportRouteV2.HYDROLOGY,
                        CaseV2.coastalFourAndBackshore()));
    }

    /** The declared {@code RIVER} / {@code MEANDERING_RIVER} feature id measured by the river cases. */
    static final String RIVER_FEATURE_ID = "inland-stem";

    /** The declared {@code PLAIN} feature id measured by the V2-19-07 foundation producer case. */
    static final String PLAIN_FEATURE_ID = "inland-plain";

    /** The declared {@code LAKE} feature id measured by the V2-15-11 case. */
    static final String LAKE_FEATURE_ID = "north-basin";

    /** The declared {@code CANYON} feature id measured by the V2-15-12 case. */
    static final String CANYON_FEATURE_ID = "stem-canyon";

    /** The declared {@code WATERFALL} feature id measured by the V2-15-13 case. */
    static final String WATERFALL_FEATURE_ID = "stem-fall";

    /**
     * The two {@link ExportRouteV2#HYDROLOGY} cases, by declared FeatureKind: the block-materialization
     * evidence {@code public-dispatch-reachability-v1} requires before an {@code OFFLINE_PRODUCTION}
     * route may be displayed as MATERIALIZED.
     */
    static Map<TerrainIntentV2.FeatureKind, String> riverCaseIdsByKind() {
        return Map.of(
                TerrainIntentV2.FeatureKind.RIVER, "harbor-cove-64-honored-river",
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER, "harbor-cove-64-honored-meander");
    }

    /** Everything the portfolio measures, all of it read back from the published Release. */
    record MeasurementsV2(
            int width,
            int length,
            List<TargetEvaluationV2> edgeEvaluations,
            int landCells,
            int landComponentCount,
            int mainlandCells,
            Optional<BeachContinuityV2> beach,
            Optional<HinterlandV2> backshorePlains,
            List<ArmLandfallV2> arms
    ) {
        MeasurementsV2 {
            edgeEvaluations = List.copyOf(edgeEvaluations);
            Objects.requireNonNull(beach, "beach");
            Objects.requireNonNull(backshorePlains, "backshorePlains");
            arms = List.copyOf(arms);
        }

        ArmLandfallV2 arm(String armId) {
            return arms.stream().filter(arm -> arm.armId().equals(armId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no such declared arm: " + armId));
        }
    }

    /**
     * Beach-owned cells only: a cell the compositor awards to another contributor says nothing about
     * the beach's own conformance.
     */
    record BeachContinuityV2(
            int landBandCells,
            int landBandOnLand,
            int landBandInMainland,
            int landBandComponentCount,
            int nearshoreCells,
            int nearshoreOnWater
    ) {
    }

    /** The declared {@code BACKSHORE_PLAINS} polygon, rasterized from the published intent. */
    record HinterlandV2(int polygonCells, int onLand, int inMainland) {
    }

    record ArmLandfallV2(
            String armId,
            int ownedCells,
            int ownedLandCells,
            int offStructureMainlandContacts,
            boolean landfallCellInMainland
    ) {
        boolean connectedToShore() {
            return offStructureMainlandContacts > 0 && landfallCellInMainland;
        }
    }

    /** Measures one published surface Release. Pure: the same directory always yields the same record. */
    static MeasurementsV2 measure(Path releaseDirectory) throws IOException {
        return measure(blueprintOf(releaseDirectory), intentOf(releaseDirectory),
                readActualLandWater(releaseDirectory));
    }

    static WorldBlueprintV2 blueprintOf(Path releaseDirectory) throws IOException {
        return new LandformV2DataCodec().readWorldBlueprint(
                releaseDirectory.resolve("blueprint/world-blueprint.json"));
    }

    static TerrainIntentV2 intentOf(Path releaseDirectory) throws IOException {
        return new LandformV2DataCodec().readTerrainIntent(
                releaseDirectory.resolve("source/terrain-intent.json"));
    }

    /**
     * Measurement over an explicit raster. The negative tests feed a deliberately damaged copy of a
     * published field through exactly this entry point, so a broken shape is proved to be detected by
     * the same code the positive cases run.
     */
    static MeasurementsV2 measure(WorldBlueprintV2 blueprint, TerrainIntentV2 intent, int[] land)
            throws IOException {
        int width = blueprint.space().bounds().width();
        int length = blueprint.space().bounds().length();
        if (land.length != Math.multiplyExact(width, length)) {
            throw new IOException("published land-water sidecar does not match the blueprint bounds");
        }

        List<TargetEvaluationV2> edges = TargetDrivenValidatorV2.builtIn()
                .validate(blueprint.validationTargets(), sampler(land, width, length))
                .evaluations();

        LandComponentsV2 components = LandComponentsV2.of(land, width, length);
        CoastalRuntimeV2 runtime = CoastalRuntimeV2.of(blueprint, width, length);

        return new MeasurementsV2(
                width, length, edges,
                components.landCells(), components.componentCount(), components.mainlandCells(),
                measureBeach(runtime, components, land, width, length),
                measureHinterland(intent, components, land, width, length),
                measureArms(runtime, blueprint, intent, components, land, width, length));
    }

    private static Optional<BeachContinuityV2> measureBeach(
            CoastalRuntimeV2 runtime,
            LandComponentsV2 components,
            int[] land,
            int width,
            int length
    ) {
        // V2-19-09 (ADR 0040 D1): a case that declares no beach has no beach conformance to measure.
        // Absence is reported, never substituted with zeroes that would read as a passing measurement.
        if (runtime.beach() == null) {
            return Optional.empty();
        }
        int landBandCells = 0;
        int landBandOnLand = 0;
        int landBandInMainland = 0;
        int nearshoreCells = 0;
        int nearshoreOnWater = 0;
        Set<Integer> bandComponents = new TreeSet<>();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                if (runtime.ownerAt(x, z) != runtime.beachOwnerIndex()) {
                    continue;
                }
                int index = z * width + x;
                SandyBeachGeneratorV2.BeachBand band =
                        runtime.beach().sampleAt(x, z, HardLandWaterSourceV2.NONE).band();
                switch (band) {
                    case FORESHORE, BACKSHORE -> {
                        landBandCells++;
                        if (land[index] == LAND) {
                            landBandOnLand++;
                            bandComponents.add(components.componentAt(index));
                        }
                        if (components.isMainland(index)) {
                            landBandInMainland++;
                        }
                    }
                    case NEARSHORE -> {
                        nearshoreCells++;
                        if (land[index] == WATER) {
                            nearshoreOnWater++;
                        }
                    }
                    case OUTSIDE -> {
                    }
                }
            }
        }
        return Optional.of(new BeachContinuityV2(landBandCells, landBandOnLand, landBandInMainland,
                bandComponents.size(), nearshoreCells, nearshoreOnWater));
    }

    private static Optional<HinterlandV2> measureHinterland(
            TerrainIntentV2 intent,
            LandComponentsV2 components,
            int[] land,
            int width,
            int length
    ) {
        Optional<TerrainIntentV2.Feature> plains = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS)
                .findFirst();
        if (plains.isEmpty()) {
            return Optional.empty();
        }
        List<TerrainIntentV2.Point2> ring =
                ((TerrainIntentV2.PolygonGeometry) plains.orElseThrow().geometry()).rings().getFirst();
        int cells = 0;
        int onLand = 0;
        int inMainland = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                if (!inside(ring, x, z, width, length)) {
                    continue;
                }
                cells++;
                int index = z * width + x;
                if (land[index] == LAND) {
                    onLand++;
                }
                if (components.isMainland(index)) {
                    inMainland++;
                }
            }
        }
        return Optional.of(new HinterlandV2(cells, onLand, inMainland));
    }

    private static List<ArmLandfallV2> measureArms(
            CoastalRuntimeV2 runtime,
            WorldBlueprintV2 blueprint,
            TerrainIntentV2 intent,
            LandComponentsV2 components,
            int[] land,
            int width,
            int length
    ) {
        // V2-19-09: no breakwater declared means no arm to measure, not a measurement of zero arms.
        if (runtime.breakwater() == null || blueprint.breakwaterHarborPlans().isEmpty()) {
            return List.of();
        }
        Map<Integer, String> armIdByOrder = new LinkedHashMap<>();
        for (BreakwaterHarborPlanV2.ArmPlan arm : blueprint.breakwaterHarborPlans().getFirst().arms()) {
            armIdByOrder.put(arm.armOrder(), arm.armId());
        }
        Map<String, int[]> counters = new LinkedHashMap<>();
        armIdByOrder.values().forEach(armId -> counters.put(armId, new int[3]));
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                if (runtime.ownerAt(x, z) != runtime.breakwaterOwnerIndex()) {
                    continue;
                }
                BreakwaterHarborGeneratorV2.BreakwaterSample sample = runtime.breakwater().sampleAt(x, z);
                if (sample.region() == BreakwaterHarborGeneratorV2.BreakwaterRegion.OUTSIDE) {
                    continue;
                }
                String armId = armIdByOrder.get(sample.armIndex());
                if (armId == null) {
                    throw new IllegalStateException("breakwater sample references an undeclared arm order");
                }
                int[] counter = counters.get(armId);
                counter[0]++;
                if (land[z * width + x] == LAND) {
                    counter[1]++;
                }
                if (touchesOffStructureMainland(runtime, components, land, width, length, x, z)) {
                    counter[2]++;
                }
            }
        }

        List<ArmLandfallV2> arms = new ArrayList<>(counters.size());
        Map<String, TerrainIntentV2.Point2> landfalls = declaredLandfalls(intent);
        for (Map.Entry<String, int[]> entry : counters.entrySet()) {
            TerrainIntentV2.Point2 landfall = landfalls.get(entry.getKey());
            boolean onMainland = landfall != null
                    && components.isMainland(cellIndex(landfall, width, length));
            arms.add(new ArmLandfallV2(entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                    entry.getValue()[2], onMainland));
        }
        return List.copyOf(arms);
    }

    /**
     * A breakwater cell counts as connected only through land it does not own itself: the structure
     * being land is not evidence that it reaches the shore.
     */
    private static boolean touchesOffStructureMainland(
            CoastalRuntimeV2 runtime,
            LandComponentsV2 components,
            int[] land,
            int width,
            int length,
            int x,
            int z
    ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx < 0 || nz < 0 || nx >= width || nz >= length) {
                    continue;
                }
                int neighbour = nz * width + nx;
                if (land[neighbour] != LAND || !components.isMainland(neighbour)) {
                    continue;
                }
                if (runtime.breakwater().sampleAt(nx, nz).region()
                        == BreakwaterHarborGeneratorV2.BreakwaterRegion.OUTSIDE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, TerrainIntentV2.Point2> declaredLandfalls(TerrainIntentV2 intent) {
        TerrainIntentV2.Feature breakwater = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("portfolio case declares no breakwater"));
        Map<String, TerrainIntentV2.Point2> landfalls = new LinkedHashMap<>();
        for (TerrainIntentV2.NamedPath path
                : ((TerrainIntentV2.MultiSplineGeometry) breakwater.geometry()).paths()) {
            landfalls.put(path.id(), path.points().getFirst());
        }
        return landfalls;
    }

    /** Normalized XZ → release-local cell, matching the plan compilers' {@code millionths × (n-1)}. */
    private static int cellIndex(TerrainIntentV2.Point2 point, int width, int length) {
        int x = clamp(Math.round(point.xMillionths() / (double) TerrainIntentV2.FIXED_SCALE * (width - 1)), width);
        int z = clamp(Math.round(point.zMillionths() / (double) TerrainIntentV2.FIXED_SCALE * (length - 1)), length);
        return z * width + x;
    }

    private static int clamp(long value, int extent) {
        return (int) Math.max(0, Math.min(extent - 1, value));
    }

    /**
     * Negative fixture: floods the declared backshore hinterland. The declared land mass disappearing
     * must show up as a conformance failure, never as a silently smaller "mainland".
     */
    static int[] withFloodedHinterland(TerrainIntentV2 intent, int[] land, int width, int length) {
        TerrainIntentV2.Feature plains = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS)
                .findFirst()
                .orElseThrow();
        List<TerrainIntentV2.Point2> ring =
                ((TerrainIntentV2.PolygonGeometry) plains.geometry()).rings().getFirst();
        int[] damaged = land.clone();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                if (inside(ring, x, z, width, length)) {
                    damaged[z * width + x] = WATER;
                }
            }
        }
        return damaged;
    }

    /**
     * Negative fixture: floods the NORTH edge band the version-1 EDGE contract measures — exactly the
     * shape the audit observed (north land share 0.000) — leaving the rest of the map untouched.
     */
    static int[] withFloodedNorthEdgeBand(int[] land, int width, int length) {
        int[] damaged = land.clone();
        int depth = Math.max(1, length / 32);
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                damaged[z * width + x] = WATER;
            }
        }
        return damaged;
    }

    /** Even-odd containment of the cell centre, evaluated in release-local cell coordinates. */
    private static boolean inside(List<TerrainIntentV2.Point2> ring, int x, int z, int width, int length) {
        boolean in = false;
        int size = ring.size();
        for (int i = 0, j = size - 1; i < size; j = i++) {
            double xi = ring.get(i).xMillionths() / (double) TerrainIntentV2.FIXED_SCALE * (width - 1);
            double zi = ring.get(i).zMillionths() / (double) TerrainIntentV2.FIXED_SCALE * (length - 1);
            double xj = ring.get(j).xMillionths() / (double) TerrainIntentV2.FIXED_SCALE * (width - 1);
            double zj = ring.get(j).zMillionths() / (double) TerrainIntentV2.FIXED_SCALE * (length - 1);
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /**
     * Reads the published {@code hydrology/validation.json} evidence through the strict,
     * checksum-verifying reader. Only defined for {@link ExportRouteV2#HYDROLOGY} cases, whose Release
     * carries this artifact in addition to the shared {@code surface-2_5d} tree.
     */
    static HydrologyValidationArtifactV2.HydrologyValidationReport readHydrologyValidationReport(
            Path releaseDirectory
    ) throws IOException {
        return new HydrologyValidationArtifactCodecV2()
                .read(releaseDirectory.resolve("hydrology/validation.json"))
                .report();
    }

    /** Reads the published ACTUAL land-water sidecar through the strict, checksum-verifying reader. */
    static int[] readActualLandWater(Path releaseDirectory) throws IOException {
        return readPublishedField(
                releaseDirectory, FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER);
    }

    /**
     * Reads one published constraint-field sidecar by semantic, through the strict, checksum-verifying
     * reader (V2-19-06 generalised this from the land-water-only form). Raw values are returned as
     * stored: land/water cells are 0/1, height cells are block-millionths with
     * {@link Integer#MIN_VALUE} for the no-data sentinel.
     */
    static int[] readPublishedField(
            Path releaseDirectory,
            FieldArtifactDescriptorV2.FieldSemantic semantic
    ) throws IOException {
        Path constraintRoot = releaseDirectory.resolve("constraints");
        ConstraintFieldIndexV2 index =
                new ConstraintFieldIndexCodecV2().readAndVerify(constraintRoot.resolve("index.json"), constraintRoot);
        FieldArtifactDescriptorV2 field = index.fields().stream()
                .filter(candidate -> candidate.definition().semantic() == semantic)
                .findFirst()
                .orElseThrow(() -> new IOException("published Release carries no " + semantic + " field"));
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(constraintRoot, field)) {
            return reader.readWindow(0, 0, field.definition().width(), field.definition().length())
                    .toRawArray();
        }
    }

    /**
     * Cells no coastal modifier claims — the macro foundation background (V2-19-06). Rebuilt from the
     * published blueprint through the same compositor the export composed with, so "background" means
     * exactly what it meant at generation time.
     */
    static boolean[] backgroundCells(WorldBlueprintV2 blueprint, int width, int length) {
        CoastalRuntimeV2 runtime = CoastalRuntimeV2.of(blueprint, width, length);
        boolean[] background = new boolean[Math.multiplyExact(width, length)];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                background[z * width + x] = !runtime.activeAt(x, z);
            }
        }
        return background;
    }

    /** Exposes the published raster to the production EDGE evaluator under its contract field id. */
    static ValidationFieldSamplerV2 sampler(int[] land, int width, int length) {
        return new ValidationFieldSamplerV2() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public int length() {
                return length;
            }

            @Override
            public int valueAt(String fieldId, int globalX, int globalZ) {
                if (!BuiltInLandformModuleCatalogV2.CONTRACT_FIELD_ID.equals(fieldId)) {
                    throw new IllegalArgumentException("portfolio sampler only publishes the contract field");
                }
                if (globalX < 0 || globalX >= width || globalZ < 0 || globalZ >= length) {
                    throw new IndexOutOfBoundsException("coordinate outside the published field");
                }
                return land[globalZ * width + globalX];
            }
        };
    }

    /** 4-connected land components of the published raster; the mainland is the largest one. */
    static final class LandComponentsV2 {
        private final int[] component;
        private final int componentCount;
        private final int mainland;
        private final int mainlandCells;
        private final int landCells;

        private LandComponentsV2(int[] component, int componentCount, int mainland, int mainlandCells,
                                 int landCells) {
            this.component = component;
            this.componentCount = componentCount;
            this.mainland = mainland;
            this.mainlandCells = mainlandCells;
            this.landCells = landCells;
        }

        static LandComponentsV2 of(int[] land, int width, int length) {
            int[] component = new int[land.length];
            int count = 0;
            int mainland = 0;
            int mainlandCells = 0;
            int landCells = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            for (int start = 0; start < land.length; start++) {
                if (land[start] != LAND || component[start] != 0) {
                    continue;
                }
                count++;
                component[start] = count;
                queue.add(start);
                int size = 0;
                while (!queue.isEmpty()) {
                    int cell = queue.poll();
                    size++;
                    int x = cell % width;
                    int z = cell / width;
                    enqueue(land, component, queue, width, length, x + 1, z, count);
                    enqueue(land, component, queue, width, length, x - 1, z, count);
                    enqueue(land, component, queue, width, length, x, z + 1, count);
                    enqueue(land, component, queue, width, length, x, z - 1, count);
                }
                landCells += size;
                // Ties resolve to the lower component id, which scan order fixes deterministically.
                if (size > mainlandCells) {
                    mainland = count;
                    mainlandCells = size;
                }
            }
            return new LandComponentsV2(component, count, mainland, mainlandCells, landCells);
        }

        private static void enqueue(int[] land, int[] component, ArrayDeque<Integer> queue,
                                    int width, int length, int x, int z, int label) {
            if (x < 0 || z < 0 || x >= width || z >= length) {
                return;
            }
            int index = z * width + x;
            if (land[index] == LAND && component[index] == 0) {
                component[index] = label;
                queue.add(index);
            }
        }

        int componentAt(int index) {
            return component[index];
        }

        boolean isMainland(int index) {
            return mainland != 0 && component[index] == mainland;
        }

        int componentCount() {
            return componentCount;
        }

        int mainlandCells() {
            return mainlandCells;
        }

        int landCells() {
            return landCells;
        }
    }

    /**
     * The four coastal generators and their compositor, rebuilt from the <em>published</em> blueprint.
     * This mirrors the export spine's own runtime assembly, so the portfolio reads the same effective
     * owner the release was composed with without reaching into pipeline-private state.
     */
    private record CoastalRuntimeV2(
            SandyBeachGeneratorV2 beach,
            BreakwaterHarborGeneratorV2 breakwater,
            CoastalTransitionCompositorV2 compositor,
            int beachOwnerIndex,
            int breakwaterOwnerIndex
    ) {
        static CoastalRuntimeV2 of(WorldBlueprintV2 blueprint, int width, int length) {
            // ADR 0040 D1 size 0: no coastal transition plan is sealed at all, so there is no
            // compositor and every cell belongs to the macro foundation background.
            if (blueprint.coastalTransitionPlans().isEmpty()) {
                return new CoastalRuntimeV2(null, null, null, 0, 0);
            }
            CoastalTransitionPlanV2 plan = blueprint.coastalTransitionPlans().getFirst();
            SandyBeachGeneratorV2 beach = null;
            BreakwaterHarborGeneratorV2 breakwater = null;
            int beachOwner = 0;
            int breakwaterOwner = 0;
            List<CoastalTransitionCompositorV2.LayerBinding> bindings = new ArrayList<>();
            for (CoastalTransitionPlanV2.Contributor contributor : plan.contributors()) {
                CoastalFeaturePlanV2 coastal = blueprint.coastalFeaturePlans().stream()
                        .filter(candidate -> candidate.featureId().equals(contributor.featureId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "published blueprint has no plan for contributor " + contributor.featureId()));
                switch (contributor.kind()) {
                    case SANDY_BEACH -> {
                        beach = new SandyBeachGeneratorV2(blueprint.sandyBeachPlans().getFirst(),
                                new CoastalRasterKernelV2(coastal, width, length));
                        beachOwner = contributor.ownerIndex();
                        bindings.add(CoastalTransitionLayerSourcesV2.beach(
                                contributor, beach, HardLandWaterSourceV2.NONE));
                    }
                    case HARBOR_BASIN -> bindings.add(CoastalTransitionLayerSourcesV2.harbor(contributor,
                            new HarborBasinGeneratorV2(blueprint.harborBasinPlans().getFirst(), coastal,
                                    width, length),
                            HardLandWaterSourceV2.NONE));
                    case BREAKWATER_HARBOR -> {
                        breakwater = new BreakwaterHarborGeneratorV2(
                                blueprint.breakwaterHarborPlans().getFirst(), coastal, width, length);
                        breakwaterOwner = contributor.ownerIndex();
                        bindings.add(CoastalTransitionLayerSourcesV2.breakwater(contributor, breakwater));
                    }
                    case ROCKY_CAPE -> bindings.add(CoastalTransitionLayerSourcesV2.cape(contributor,
                            new RockyCapeGeneratorV2(blueprint.rockyCapePlans().getFirst(), coastal,
                                    width, length),
                            HardLandWaterSourceV2.NONE));
                    default -> throw new IllegalStateException(
                            "portfolio does not model coastal contributor kind " + contributor.kind());
                }
            }
            return new CoastalRuntimeV2(beach, breakwater,
                    new CoastalTransitionCompositorV2(plan, width, length, bindings),
                    beachOwner, breakwaterOwner);
        }

        int ownerAt(int x, int z) {
            return compositor == null ? 0 : compositor.sampleAt(x, z, HardLandWaterSourceV2.NONE).ownerIndex();
        }

        /** Whether any coastal modifier contributes at the cell; false means the foundation owns it. */
        boolean activeAt(int x, int z) {
            return compositor != null && compositor.sampleAt(x, z, HardLandWaterSourceV2.NONE).active();
        }
    }
}
