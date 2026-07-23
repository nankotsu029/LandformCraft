package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.export.HeightGuideExampleFixtureV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2HydrologyExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.MetricResultV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.validation.v2.conformance.ConformanceMeasurementsV2;
import com.github.nankotsu029.landformcraft.validation.v2.conformance.ConformanceResidualEvaluatorV2;
import com.github.nankotsu029.landformcraft.validation.v2.conformance.ConformanceResidualV2;
import com.github.nankotsu029.landformcraft.validation.v2.conformance.ConformanceTargetSetV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetEvaluationV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.ValidationFieldSamplerV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-11 permanent intent-conformance regression.
 *
 * <p>Closes audit item 5 ("no layer asserts terrain shape"). Every case is exported once through the
 * public production export service and then measured <em>from the published Release</em> — the sealed
 * intent, the frozen blueprint and the ACTUAL land-water sidecar — so the assertions describe the
 * artifact an operator ships, not in-process generator state.</p>
 *
 * <p>The portfolio is deliberately additive: later leaves that connect new kinds add a
 * {@link IntentConformancePortfolioV2.CaseV2} entry (mountain+river+lake, island+reef, …). Promoting
 * the portfolio into the Phase gate is {@code V2-18-12}'s Task, not this one's.</p>
 */
class IntentConformancePortfolioV2Test {
    @TempDir
    static Path root;

    private static GenerationExecutors executors;
    private static final Map<String, Path> RELEASES = new LinkedHashMap<>();
    private static final Map<String, IntentConformancePortfolioV2.MeasurementsV2> MEASUREMENTS =
            new LinkedHashMap<>();

    static Stream<IntentConformancePortfolioV2.CaseV2> cases() {
        return IntentConformancePortfolioV2.cases().stream();
    }

    @BeforeAll
    static void exportPortfolio() throws Exception {
        executors = GenerationExecutors.createDefault(2);
        for (IntentConformancePortfolioV2.CaseV2 portfolioCase : IntentConformancePortfolioV2.cases()) {
            Path run = root.resolve(portfolioCase.id());
            Release2ExportRequestV2 request = new Release2ExportRequestV2(
                    portfolioCase.request(), portfolioCase.intent(),
                    run.resolve("work"), run.resolve("exports"),
                    portfolioCase.id(), portfolioCase.baseline());
            // V2-15-10: a HYDROLOGY-route case (RIVER / MEANDERING_RIVER alongside the coastal
            // contributors) must publish through the hydrology-plan OFFLINE_PRODUCTION dispatch path,
            // not the plain surface-2_5d path, per ADR 0039 Candidate A.
            Release2ExportResultV2 result = portfolioCase.exportRoute()
                    == IntentConformancePortfolioV2.ExportRouteV2.HYDROLOGY
                    ? new Release2HydrologyExportApplicationServiceV2(executors).exportNow(request)
                    : new Release2ExportApplicationServiceV2(executors).exportNow(request);
            RELEASES.put(portfolioCase.id(), result.releaseDirectory());
            MEASUREMENTS.put(portfolioCase.id(),
                    IntentConformancePortfolioV2.measure(result.releaseDirectory()));
        }
    }

    @AfterAll
    static void stopExecutors() {
        executors.shutdown(Duration.ofSeconds(30));
        executors.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void publishedEdgesMatchTheDeclaredEdgeClassification(IntentConformancePortfolioV2.CaseV2 portfolioCase) {
        List<TargetEvaluationV2> edges = MEASUREMENTS.get(portfolioCase.id()).edgeEvaluations();

        // Both honored fixtures declare the audit's original macro composition: north is mainland,
        // south is open sea. V2-18-11 restored them to HARD (V2-18-04 evaluates EDGE, V2-18-09 gives
        // the mask a foundation), so production now rejects an export that loses either edge.
        assertEquals(List.of("north-is-land", "south-is-sea"),
                edges.stream().map(TargetEvaluationV2::targetId).sorted().toList());
        for (TargetEvaluationV2 edge : edges) {
            assertEquals(TerrainIntentV2.Strength.HARD, edge.hardness(), edge.targetId());
            assertTrue(edge.satisfied(), edge::detail);
            // Pinned, not merely "above the minimum": the macro foundation resolves both edge bands
            // entirely from the declared mask, so any drift is a shape regression to look at.
            assertEquals(1_000_000L, edge.measuredMillionths(), edge::detail);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theBeachLandBandsStayConnectedToTheDeclaredBackshore(
            IntentConformancePortfolioV2.CaseV2 portfolioCase) {
        IntentConformancePortfolioV2.MeasurementsV2 measurements = MEASUREMENTS.get(portfolioCase.id());
        IntentConformancePortfolioV2.BeachContinuityV2 beach = measurements.beach();
        IntentConformancePortfolioV2.HinterlandV2 hinterland = measurements.backshorePlains();

        assertTrue(beach.landBandCells() > 0, "the beach owns no foreshore/backshore cell");
        // A foreshore or backshore cell that is water is a beach the terrain never actually built.
        assertEquals(beach.landBandCells(), beach.landBandOnLand());
        // …and the nearshore band is its mirror image: sand below the waterline is equally wrong.
        assertTrue(beach.nearshoreCells() > 0, "the beach owns no nearshore cell");
        assertEquals(beach.nearshoreCells(), beach.nearshoreOnWater());
        // Continuity: one land component for the whole beach, and it is the mainland …
        assertEquals(1, beach.landBandComponentCount());
        assertEquals(beach.landBandCells(), beach.landBandInMainland());
        // … which also carries the declared backshore hinterland, so beach ↔ backshore is one land
        // mass rather than a beach stranded on an island (the audit's baseline-fallback symptom).
        assertTrue(hinterland.polygonCells() > 0, "the case declares no backshore hinterland area");
        assertEquals(hinterland.polygonCells(), hinterland.onLand());
        assertEquals(hinterland.polygonCells(), hinterland.inMainland());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyDeclaredBreakwaterArmIsMeasuredAgainstItsLandfall(
            IntentConformancePortfolioV2.CaseV2 portfolioCase) {
        IntentConformancePortfolioV2.MeasurementsV2 measurements = MEASUREMENTS.get(portfolioCase.id());

        assertEquals(portfolioCase.declaredArmIds(), ids(measurements.arms()));
        for (IntentConformancePortfolioV2.ArmLandfallV2 arm : measurements.arms()) {
            assertTrue(arm.ownedCells() > 0, arm.armId());
            assertEquals(portfolioCase.shoreConnectedArmIds().contains(arm.armId()), arm.connectedToShore(),
                    () -> "shore connection of " + arm.armId() + " changed: " + arm);
        }
    }

    @Test
    void theHonored400EastArmLandsOnTheMainlandAfterV21813() {
        // V2-18-13 corrected the registered non-conformance: the rocky cape's western edge was
        // extended west so the east breakwater arm's foundation toe reaches cape land, and the mask
        // was regenerated. Both arms now land on the mainland; the previous isolated 2163-cell land
        // island is gone.
        IntentConformancePortfolioV2.MeasurementsV2 measurements = MEASUREMENTS.get("coastal-honored-400");
        IntentConformancePortfolioV2.ArmLandfallV2 east = measurements.arm("east-arm");
        IntentConformancePortfolioV2.ArmLandfallV2 west = measurements.arm("west-arm");

        // Every arm cell is land, both reach off-structure mainland, and both declared landfall cells
        // now sit on the mainland — so both compose as connected causeways, not stranded islands.
        assertEquals(east.ownedCells(), east.ownedLandCells());
        assertTrue(east.offStructureMainlandContacts() > 0, () -> "east arm: " + east);
        assertTrue(east.landfallCellInMainland(), () -> "east arm: " + east);
        assertTrue(east.connectedToShore(), () -> "east arm: " + east);
        assertEquals(west.ownedCells(), west.ownedLandCells());
        assertTrue(west.offStructureMainlandContacts() > 0, () -> "west arm: " + west);
        assertTrue(west.landfallCellInMainland(), () -> "west arm: " + west);
        assertTrue(west.connectedToShore(), () -> "west arm: " + west);
        // The only remaining non-mainland land components are the rocky cape's declared offshore sea
        // stacks, never a stranded breakwater arm.
        assertEquals(72852, measurements.mainlandCells());
        assertEquals(4, measurements.landComponentCount());
    }

    @Test
    void theDeclaredHeightGuideIsTheBackgroundElevationAndItsResidualIsMeasured() throws Exception {
        // V2-19-06: the elevation guide is an input to generation, not decoration. Read back from the
        // published Release: every background cell the guide specifies carries exactly the guide's
        // height, the cells it marks no-data fall back to the declared per-medium base level, and the
        // only cells that differ are the ones a coastal modifier owns (ADR 0038 D5-3).
        Path release = RELEASES.get("harbor-cove-64-honored-guided");
        WorldBlueprintV2 blueprint = IntentConformancePortfolioV2.blueprintOf(release);
        int[] desired = IntentConformancePortfolioV2.readPublishedField(
                release, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT);
        int[] actual = IntentConformancePortfolioV2.readPublishedField(
                release, FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT);
        int[] residual = IntentConformancePortfolioV2.readPublishedField(
                release, FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT);
        boolean[] background = IntentConformancePortfolioV2.backgroundCells(blueprint, 64, 64);
        int[] mask = HeightGuideExampleFixtureV2.maskSamples();

        int guidedBackgroundCells = 0;
        int noDataBackgroundCells = 0;
        int modifierMismatches = 0;
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                int index = z * 64 + x;
                int expectedDesired = HeightGuideExampleFixtureV2.isNoDataCell(x, z)
                        ? Integer.MIN_VALUE
                        : HeightGuideExampleFixtureV2.sampleAt(x, z, mask[index]) * 1_000_000;
                assertEquals(expectedDesired, desired[index], "desired height at " + x + ',' + z);
                if (!background[index]) {
                    if (desired[index] != Integer.MIN_VALUE && actual[index] != desired[index]) {
                        modifierMismatches++;
                    }
                    continue;
                }
                if (desired[index] == Integer.MIN_VALUE) {
                    // The documented fallback: the per-medium base level, never a guessed height.
                    noDataBackgroundCells++;
                    assertEquals(mask[index] == 1 ? 54_000_000 : 46_000_000, actual[index],
                            "no-data fallback at " + x + ',' + z);
                    assertEquals(Integer.MIN_VALUE, residual[index], "residual at " + x + ',' + z);
                    continue;
                }
                guidedBackgroundCells++;
                assertEquals(desired[index], actual[index], "background height at " + x + ',' + z);
                assertEquals(0, residual[index], "residual at " + x + ',' + z);
            }
        }
        // Pinned: 3122 background cells take the guide verbatim, the 16 no-data cells fall back, and
        // the 955 remaining specified cells are the ones a coastal modifier owns. A drift in any of
        // the three is a change in who owns the surface, not a rounding detail.
        assertEquals(3122, guidedBackgroundCells);
        assertEquals(16, noDataBackgroundCells, "the no-data patch must stay on the background");
        assertEquals(955, modifierMismatches,
                "a SOFT guide must record where the modifiers own the height instead");

        // The RasterResidual the guide enables: one target per bound map, both resolved against the
        // declared input digest rather than a self-derived reference. Before V2-19-06 the height
        // binding produced no target at all, so its conformance was silently absent.
        int[] desiredLand = IntentConformancePortfolioV2.readPublishedField(
                release, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);
        int[] actualLand = IntentConformancePortfolioV2.readPublishedField(
                release, FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER);
        List<ConformanceResidualV2> residuals = new ConformanceResidualEvaluatorV2().evaluate(
                ConformanceTargetSetV2.from(
                        List.of(),
                        IntentConformancePortfolioV2.intentOf(release).mapReferences(),
                        declaredDigests(release)),
                new ConformanceMeasurementsV2(
                        Optional.of(rasterSampler(desiredLand, desired)),
                        Optional.of(rasterSampler(actualLand, actual)),
                        Map.of(), Map.of()));
        Map<String, ConformanceResidualV2.RasterResidual> byTarget = residuals.stream()
                .filter(ConformanceResidualV2.RasterResidual.class::isInstance)
                .map(ConformanceResidualV2.RasterResidual.class::cast)
                .collect(Collectors.toMap(ConformanceResidualV2.RasterResidual::targetId, value -> value));
        assertEquals(2, residuals.size(), residuals::toString);
        assertEquals(residuals.size(), byTarget.size(),
                "every declared map must yield a measured raster residual, never an unconsumed target");
        ConformanceResidualV2.RasterResidual land =
                byTarget.get("conformance-desired-raster-coast-mask-binding");
        assertEquals(64L * 64L, land.comparedCells());
        assertEquals(0L, land.mismatchCells(), "the HARD mask must still be reproduced exactly");
        ConformanceResidualV2.RasterResidual height =
                byTarget.get("conformance-desired-raster-coast-height-binding");
        assertEquals(64L * 64L - 16L, height.comparedCells(),
                "the no-data patch is not compared, everything else is");
        assertEquals(modifierMismatches, height.mismatchCells());
    }

    @Test
    void theHeightGuideReachesTheFinalCanonicalBlockStream() throws Exception {
        // The guide changes the published blocks, measured the V2-19-01 way: the guided Release
        // against the otherwise identical unguided one. A guide that only reached a report would show
        // up here as an empty diff.
        FeatureMaterializationV2.BlockEffectV2 block = FeatureMaterializationV2.measureBlockEffect(
                RELEASES.get("harbor-cove-64-honored"), RELEASES.get("harbor-cove-64-honored-guided"));

        assertEquals(64L * 64L * 41L, block.comparedCells());
        assertTrue(block.changedCells() > 0, () -> "the height guide changed no block: " + block);
        // The terraces move the surface itself (SOLID_SHAPE), which drags the water column above a
        // deepened sea bed (FLUID) and the surface material with it (MATERIAL).
        assertEquals(13289L, block.changedCells(), block::toString);
        assertEquals(3103L, block.solidShapeChanges(), block::toString);
        assertEquals(4940L, block.fluidChanges(), block::toString);
        assertEquals(5246L, block.materialChanges(), block::toString);
    }

    @Test
    void thePlainFoundationProducerMaterializesTheDeclaredBlockEffectClasses() throws Exception {
        // V2-19-07: the first macro foundation producer, measured the V2-19-01 way against the
        // otherwise identical Release without the PLAIN feature. Raising the land it owns lifts solid
        // mass into what used to be air (SOLID_SHAPE) and moves the surface/subsurface materials with
        // it (MATERIAL); the footprint is inland, so no fluid column is touched at all.
        FeatureMaterializationV2.BlockEffectV2 block = FeatureMaterializationV2.measureBlockEffect(
                RELEASES.get("harbor-cove-64-honored"), RELEASES.get("harbor-cove-64-honored-plain"));

        assertEquals(64L * 64L * 41L, block.comparedCells());
        assertEquals(1238L, block.changedCells(), block::toString);
        assertEquals(713L, block.solidShapeChanges(), block::toString);
        assertEquals(525L, block.materialChanges(), block::toString);
        assertEquals(0L, block.fluidChanges(),
                () -> "an inland PLAIN must not own any fluid: " + block);

        FeatureMaterializationV2.requireMaterialized(
                new FeatureMaterializationV2.MaterializationClaimV2(
                        IntentConformancePortfolioV2.PLAIN_FEATURE_ID,
                        TerrainIntentV2.FeatureKind.PLAIN,
                        Set.of("foundation.plain.mask", "foundation.plain.base-elevation",
                                "foundation.plain.micro-relief"),
                        Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                FeatureMaterializationV2.EffectClassV2.MATERIAL)),
                block);
    }

    @Test
    void theFinalTileStreamCarriesTheDeclaredPlainElevationAndBoundedReplacement() throws Exception {
        // V2-19-07 block column: the producer owns the elevation of every cell in its footprint, the
        // declared micro-relief band is really exercised, and the replacement stops at the footprint —
        // every other background cell still stands at the request's own per-medium base level.
        PlainBlockConformanceV2.MeasurementsV2 plain =
                PlainBlockConformanceV2.measure(RELEASES.get("harbor-cove-64-honored-plain"));

        assertEquals(IntentConformancePortfolioV2.PLAIN_FEATURE_ID, plain.featureId());
        assertEquals(50, plain.waterLevel());
        // Datum: water level + declared base elevation (midpoint 6) + the declared micro relief 1..3.
        assertEquals(57, plain.declaredMinimumSurfaceY());
        assertEquals(59, plain.declaredMaximumSurfaceY());
        assertTrue(plain.raisedCells() > 0, "the declared PLAIN raised no published column");
        assertEquals(175, plain.raisedCells(), plain::toString);
        assertEquals(plain.raisedCells(), plain.raisedCellsInsideDeclaredExtent(),
                "the producer changed a column outside its declared polygon extent");
        assertEquals(plain.raisedCells(), plain.raisedCellsWithinDeclaredBand(),
                "a producer column stands outside the declared elevation band");
        assertEquals(plain.raisedCells(), plain.raisedCellsSolidToSurface());
        assertEquals(plain.raisedCells(), plain.raisedCellsDryColumn());
        // The micro relief is a band, not a constant: all three declared steps occur.
        assertEquals(Map.of(57, 54, 58, 54, 59, 67), plain.raisedCellsBySurfaceY(), plain::toString);
        // Bounded replacement: every background cell the producer does not own keeps the declared
        // per-medium base level, on both mediums.
        assertEquals(plain.backgroundLandCells() - plain.raisedCells(),
                plain.backgroundLandCellsAtBaseLevel(), plain::toString);
        assertEquals(plain.backgroundWaterCells(), plain.backgroundWaterCellsAtBedLevel(),
                plain::toString);
    }

    @Test
    void thePlainProducerLeavesEveryModifierOwnedColumnUntouched() throws Exception {
        // Tier separation (ADR 0038 D5-3): the foundation producer resolves under the modifiers, so a
        // cell a coastal modifier owns must be block-identical to the baseline Release.
        Path baseline = RELEASES.get("harbor-cove-64-honored");
        Path plain = RELEASES.get("harbor-cove-64-honored-plain");
        FeatureMaterializationV2.FinalBlockStreamV2 baselineStream =
                FeatureMaterializationV2.readFinalBlockStream(baseline);
        FeatureMaterializationV2.FinalBlockStreamV2 plainStream =
                FeatureMaterializationV2.readFinalBlockStream(plain);
        boolean[] background = IntentConformancePortfolioV2.backgroundCells(
                IntentConformancePortfolioV2.blueprintOf(plain), 64, 64);

        int modifierColumns = 0;
        int changedModifierColumns = 0;
        int changedBackgroundColumns = 0;
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                boolean changed = PlainBlockConformanceV2.surfaceY(plainStream, x, z)
                        != PlainBlockConformanceV2.surfaceY(baselineStream, x, z);
                if (background[z * 64 + x]) {
                    changedBackgroundColumns += changed ? 1 : 0;
                    continue;
                }
                modifierColumns++;
                changedModifierColumns += changed ? 1 : 0;
            }
        }
        assertTrue(modifierColumns > 0, "the case has no coastal modifier column to protect");
        assertEquals(0, changedModifierColumns,
                "the foundation producer overwrote a surface modifier's column");
        assertEquals(175, changedBackgroundColumns);
    }

    private static Map<String, String> declaredDigests(Path release) throws Exception {
        return new com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec()
                .readGenerationRequest(release.resolve("source/generation-request.json"))
                .constraintMaps().stream()
                .collect(Collectors.toMap(
                        source -> source.sourceId(),
                        source -> source.expectedSha256()));
    }

    /** Publishes the two already-read rasters under the contract field id of their own role. */
    private static ValidationFieldSamplerV2 rasterSampler(int[] land, int[] height) {
        return new ValidationFieldSamplerV2() {
            @Override
            public int width() {
                return 64;
            }

            @Override
            public int length() {
                return 64;
            }

            @Override
            public int valueAt(String fieldId, int globalX, int globalZ) {
                int index = globalZ * 64 + globalX;
                if (ConformanceTargetSetV2.LAND_WATER_FIELD_ID.equals(fieldId)) {
                    return land[index];
                }
                if (ConformanceTargetSetV2.HEIGHT_GUIDE_FIELD_ID.equals(fieldId)) {
                    return height[index];
                }
                throw new IllegalArgumentException("no published raster for field " + fieldId);
            }
        };
    }

    static Stream<Map.Entry<TerrainIntentV2.FeatureKind, String>> riverCases() {
        return IntentConformancePortfolioV2.riverCaseIdsByKind().entrySet().stream()
                .sorted(Map.Entry.comparingByKey());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("riverCases")
    void theRiverOfflineWiringPublishesPassingHydrologyMetrics(
            Map.Entry<TerrainIntentV2.FeatureKind, String> riverCase) throws Exception {
        // V2-15-10 / ADR 0039 Candidate A per-leaf intent-conformance obligation: read the reach-shape
        // evidence back from the published hydrology-plan Release (never in-process generator state)
        // and confirm the shared HydrologyValidatorV2 metrics are computed and pass for both kinds.
        // This is the portfolio's PLAN-ONLY column; the block column is measured separately below and
        // neither may stand in for the other (V2-19-01).
        Path release = RELEASES.get(riverCase.getValue());
        HydrologyValidationArtifactV2.HydrologyValidationReport report =
                IntentConformancePortfolioV2.readHydrologyValidationReport(release);
        assertTrue(report.passesHardValidation(), () -> String.valueOf(report.issues()));

        Map<String, MetricResultV2> riverMetrics = report.metrics().stream()
                .filter(metric -> IntentConformancePortfolioV2.RIVER_FEATURE_ID.equals(metric.subject()))
                .collect(Collectors.toMap(MetricResultV2::metricId, metric -> metric));
        assertEquals(Set.of(
                "hydrology.river.channel-gaps",
                "hydrology.river.reverse-gradient-cells",
                "hydrology.river.source-mouth-reachable"), riverMetrics.keySet());
        for (MetricResultV2 metric : riverMetrics.values()) {
            assertTrue(metric.passed(), metric::toString);
        }
        assertEquals(0L, riverMetrics.get("hydrology.river.channel-gaps").actualMillionths());
        assertEquals(0L, riverMetrics.get("hydrology.river.reverse-gradient-cells").actualMillionths());
        assertEquals(1L, riverMetrics.get("hydrology.river.source-mouth-reachable").actualMillionths());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("riverCases")
    void theRiverRouteMaterializesTheDeclaredBlockEffectClasses(
            Map.Entry<TerrainIntentV2.FeatureKind, String> riverCase) throws Exception {
        // V2-19-05, the first application of the V2-19-01 gate's positive direction: the offline
        // production route must change the final canonical block stream, and exactly in the classes
        // the leaf declares. CARVE_SOLID voids the trench above the bed (SOLID_SHAPE) and ADD_FLUID
        // owns the water it leaves behind (FLUID); nothing re-lines the bed, so MATERIAL stays absent.
        Path baseline = RELEASES.get("harbor-cove-64-honored");
        Path river = RELEASES.get(riverCase.getValue());

        FeatureMaterializationV2.BlockEffectV2 block =
                FeatureMaterializationV2.measureBlockEffect(baseline, river);
        assertEquals(64L * 64L * 41L, block.comparedCells());
        assertTrue(block.changedCells() > 0, () -> "the river route changed no block: " + block);
        assertEquals(0L, block.materialChanges(), () -> "the river re-lined the bed: " + block);

        FeatureMaterializationV2.requireMaterialized(
                new FeatureMaterializationV2.MaterializationClaimV2(
                        IntentConformancePortfolioV2.RIVER_FEATURE_ID,
                        riverCase.getKey(),
                        Set.of("hydrology.river.channel-mask", "hydrology.bed.elevation",
                                "hydrology.water.surface"),
                        Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                FeatureMaterializationV2.EffectClassV2.FLUID)),
                block);
    }

    @Test
    void bothRiverKindsShareOneMaterializationAndPinItsSize() throws Exception {
        // V2-15-10 bridges RIVER onto the MEANDERING_RIVER compile path, so the same declared reach
        // must produce the identical block effect under either kind. Pinned exactly: a drift in the
        // carve or fill footprint is a shape regression to look at, not a rounding detail.
        Path baseline = RELEASES.get("harbor-cove-64-honored");
        FeatureMaterializationV2.BlockEffectV2 river = FeatureMaterializationV2.measureBlockEffect(
                baseline, RELEASES.get("harbor-cove-64-honored-river"));
        FeatureMaterializationV2.BlockEffectV2 meander = FeatureMaterializationV2.measureBlockEffect(
                baseline, RELEASES.get("harbor-cove-64-honored-meander"));

        assertEquals(river, meander);
        assertEquals(575L, river.changedCells(), river::toString);
        assertEquals(458L, river.solidShapeChanges(), river::toString);
        assertEquals(117L, river.fluidChanges(), river::toString);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("riverCases")
    void theFinalTileStreamCarriesTheDeclaredBedDepthContinuityAndContainment(
            Map.Entry<TerrainIntentV2.FeatureKind, String> riverCase) throws Exception {
        // V2-19-05 block column, measured from the published tiles alone: bed depth, water
        // continuity, source→mouth reachability and the leak envelope.
        Path release = RELEASES.get(riverCase.getValue());
        RiverBlockConformanceV2.MeasurementsV2 river = RiverBlockConformanceV2.measure(
                release, IntentConformancePortfolioV2.blueprintOf(release));

        assertEquals(IntentConformancePortfolioV2.RIVER_FEATURE_ID, river.featureId());
        assertTrue(river.channelCells() > 0, "the published reach rasterizes to no channel cell");
        // Bed depth: every channel column is cut to its declared bed, carries exactly the declared
        // water depth, and is open to the sky above it.
        assertEquals(1, river.declaredWaterDepthBlocks());
        assertEquals(river.channelCells(), river.channelCellsAtDeclaredBedDepth());
        assertEquals(river.channelCells(), river.channelCellsOpenAbove());
        assertEquals(river.channelCells(), river.waterCells());
        // Water continuity and reachability: one water body, and the declared source reaches the
        // declared mouth inside it.
        assertEquals(1, river.waterComponentCount());
        assertTrue(river.sourceToMouthReachable(), river::toString);
        // Leak envelope: no channel water block touches non-channel air, and the surrounding terrain
        // stands solid at the water surface all the way around the reach.
        assertEquals(0, river.leakCells(), river::toString);
        assertTrue(river.envelopeCells() > 0, "the reach has no containment envelope");
        assertEquals(river.envelopeCells(), river.envelopeCellsSolidAtWaterSurface(), river::toString);
    }

    @Test
    void aPassingPlanColumnStillCannotSubstituteForAnEmptyBlockColumn() throws Exception {
        // V2-19-01's mandated separation, kept executable after V2-19-05 filled the block column:
        // the gate accepts nothing but a measured BlockEffectV2, and an empty one fails however
        // complete the plan-only evidence is. The identity diff is the exact block-stream shape of
        // every intentional no-op (constant healthy sampler, ADD_FLUID-into-solid identity slice).
        Path river = RELEASES.get("harbor-cove-64-honored-river");
        HydrologyValidationArtifactV2.HydrologyValidationReport plan =
                IntentConformancePortfolioV2.readHydrologyValidationReport(river);
        assertTrue(plan.passesHardValidation(), () -> String.valueOf(plan.issues()));

        FeatureMaterializationV2.BlockEffectV2 identity =
                FeatureMaterializationV2.measureBlockEffect(river, river);
        assertTrue(identity.comparedCells() > 0);
        assertEquals(0L, identity.changedCells());

        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> FeatureMaterializationV2.requireMaterialized(
                        new FeatureMaterializationV2.MaterializationClaimV2(
                                IntentConformancePortfolioV2.RIVER_FEATURE_ID,
                                TerrainIntentV2.FeatureKind.RIVER,
                                Set.of("hydrology.river.channel-mask"),
                                Set.of(FeatureMaterializationV2.EffectClassV2.SOLID_SHAPE,
                                        FeatureMaterializationV2.EffectClassV2.FLUID)),
                        identity));
        assertTrue(rejected.getMessage().contains("no effect on the final canonical block stream"),
                rejected::getMessage);
    }

    @Test
    void aFloodedNorthEdgeIsRejectedByTheSameEdgeMeasurement() throws Exception {
        // The audit's exact symptom (north edge land share 0.000) fed back through the published
        // artifact: the portfolio must fail, not merely report a lower share.
        Path release = RELEASES.get("harbor-cove-64-honored");
        WorldBlueprintV2 blueprint = IntentConformancePortfolioV2.blueprintOf(release);
        TerrainIntentV2 intent = IntentConformancePortfolioV2.intentOf(release);
        int[] land = IntentConformancePortfolioV2.readActualLandWater(release);

        IntentConformancePortfolioV2.MeasurementsV2 damaged = IntentConformancePortfolioV2.measure(
                blueprint, intent,
                IntentConformancePortfolioV2.withFloodedNorthEdgeBand(land, 64, 64));

        TargetEvaluationV2 north = damaged.edgeEvaluations().stream()
                .filter(edge -> edge.targetId().equals("north-is-land")).findFirst().orElseThrow();
        assertFalse(north.satisfied(), north::detail);
        assertEquals(0L, north.measuredMillionths());
    }

    @Test
    void aFloodedBackshoreBreaksTheBeachContinuityMeasurement() throws Exception {
        Path release = RELEASES.get("harbor-cove-64-honored");
        WorldBlueprintV2 blueprint = IntentConformancePortfolioV2.blueprintOf(release);
        TerrainIntentV2 intent = IntentConformancePortfolioV2.intentOf(release);
        int[] land = IntentConformancePortfolioV2.readActualLandWater(release);

        IntentConformancePortfolioV2.MeasurementsV2 damaged = IntentConformancePortfolioV2.measure(
                blueprint, intent,
                IntentConformancePortfolioV2.withFloodedHinterland(intent, land, 64, 64));

        IntentConformancePortfolioV2.HinterlandV2 hinterland = damaged.backshorePlains();
        assertTrue(hinterland.polygonCells() > 0);
        assertEquals(0, hinterland.onLand());
        assertEquals(0, hinterland.inMainland());
        // The surviving beach is no longer continuous with the declared backshore, which is precisely
        // what the positive assertion pins.
        assertNotEquals(hinterland.polygonCells(), hinterland.inMainland());
    }

    @Test
    void anIsolatedLandBlobIsNeverCountedAsMainland() {
        // Unit-level guard for the component kernel the shape assertions depend on: 8×8 raster with a
        // 3×3 mass and a 2-cell island. The island must not be mistaken for the mainland.
        int[] land = new int[64];
        for (int z = 1; z <= 3; z++) {
            for (int x = 1; x <= 3; x++) {
                land[z * 8 + x] = IntentConformancePortfolioV2.LAND;
            }
        }
        land[6 * 8 + 6] = IntentConformancePortfolioV2.LAND;
        land[6 * 8 + 7] = IntentConformancePortfolioV2.LAND;

        IntentConformancePortfolioV2.LandComponentsV2 components =
                IntentConformancePortfolioV2.LandComponentsV2.of(land, 8, 8);

        assertEquals(11, components.landCells());
        assertEquals(2, components.componentCount());
        assertEquals(9, components.mainlandCells());
        assertTrue(components.isMainland(1 * 8 + 1));
        assertFalse(components.isMainland(6 * 8 + 6));
        assertFalse(components.isMainland(0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theMeasurementIsIndependentOfLocaleAndTimezone(IntentConformancePortfolioV2.CaseV2 portfolioCase)
            throws Exception {
        Path release = RELEASES.get(portfolioCase.id());
        IntentConformancePortfolioV2.MeasurementsV2 baseline = MEASUREMENTS.get(portfolioCase.id());
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(baseline, IntentConformancePortfolioV2.measure(release));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        assertEquals(baseline, IntentConformancePortfolioV2.measure(release));
        assertEquals(portfolioCase.width(), baseline.width());
        assertEquals(portfolioCase.length(), baseline.length());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void thePublishedReleaseIsTheOnlyMeasurementInput(IntentConformancePortfolioV2.CaseV2 portfolioCase)
            throws Exception {
        // Measuring a Release copied away from its work directory must give the identical record: the
        // portfolio may not depend on staging leftovers or on the exporting process.
        Path release = RELEASES.get(portfolioCase.id());
        Path copy = root.resolve("copies").resolve(portfolioCase.id());
        copyTree(release, copy);

        assertEquals(MEASUREMENTS.get(portfolioCase.id()), IntentConformancePortfolioV2.measure(copy));
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }

    private static Set<String> ids(List<IntentConformancePortfolioV2.ArmLandfallV2> arms) {
        return arms.stream().map(IntentConformancePortfolioV2.ArmLandfallV2::armId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
