package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.binding.BoundConstraintFieldV2;
import com.github.nankotsu029.landformcraft.core.v2.binding.ConstraintMapFieldBindingV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationFailureCodeV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoder;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-09 macro foundation stage: background-candidate resolution from the explicit foundation
 * input (ADR 0038 D1/D2/D5-1) and the fail-closed kernel invariants (D7-1). V2-19-06 added the second
 * explicit elevation source ADR 0038 D2-2 allows — the {@code HEIGHT_GUIDE} — together with the role
 * cardinality the surface path now requires instead of "exactly one constraint map".
 */
class MacroFoundationStageV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json");
    private static final Path GUIDED_REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-guided.request-v2.json");
    private static final Path GUIDED_INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-guided.terrain-intent-v2.json");
    private static final Path PLAIN_REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-plain.request-v2.json");
    private static final Path PLAIN_INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-plain.terrain-intent-v2.json");
    private static final CancellationToken TOKEN = () -> false;
    /** The 64x64 fixtures' own vertical extent (minY 32, maxY 72). */
    private static final MacroFoundationV2.VerticalExtentV2 EXTENT =
            new MacroFoundationV2.VerticalExtentV2(32_000_000L, 72_000_000L);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final MacroFoundationStageV2 stage = new MacroFoundationStageV2();

    @Test
    void resolvesTheBackgroundOwnerFromTheExplicitFoundationInput() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);

        MacroFoundationV2 foundation = stage.resolve(request, REQUEST, intent, TOKEN).orElseThrow();

        assertEquals(64, foundation.width());
        assertEquals(64, foundation.length());
        assertEquals(
                Sha256.file(REQUEST.resolveSibling("maps").resolve("harbor-cove-64-honored-land-water-u8.png")),
                foundation.maskSourceChecksum());
        // Exactly one effective owner per cell — the mask-derived background — and the medium /
        // provisional elevation come only from the declared inputs, never from inference.
        boolean sawLand = false;
        boolean sawWater = false;
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                assertEquals(MacroFoundationV2.BACKGROUND_OWNER_INDEX, foundation.effectiveOwnerIndexAt(x, z));
                int medium = foundation.mediumAt(x, z);
                assertTrue(medium == 0 || medium == 1);
                int expectedElevation = medium == 1 ? 54_000_000 : 46_000_000;
                assertEquals(expectedElevation, foundation.elevationMillionthsAt(x, z));
                HardLandWaterSourceV2.Classification hard =
                        foundation.hardLandWaterSource().classificationAt(x, z);
                assertEquals(medium, hard.rawValue());
                sawLand |= medium == 1;
                sawWater |= medium == 0;
            }
        }
        assertTrue(sawLand && sawWater, "the honored mask must register both mediums");
        // Halo reads outside the field never fabricate a constraint.
        assertEquals(HardLandWaterSourceV2.Classification.UNSPECIFIED,
                foundation.hardLandWaterSource().classificationAt(-1, 0));
        assertEquals(HardLandWaterSourceV2.Classification.UNSPECIFIED,
                foundation.hardLandWaterSource().classificationAt(64, 63));
    }

    @Test
    void aRequestWithoutDeclaredBaseLevelsKeepsTheLegacyPath() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        GenerationRequestV2 withoutLevels = new GenerationRequestV2(
                request.requestVersion(), request.requestId(), request.bounds(), request.prompt(),
                request.referenceImages(), request.constraintMaps(), request.generation(),
                request.constraintMapBudget(), Optional.empty(), Optional.empty(),
                java.util.Optional.empty());
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);

        assertTrue(stage.resolve(withoutLevels, REQUEST, intent, TOKEN).isEmpty(),
                "no declared base levels means no explicit foundation input (ADR 0038 D8-2)");
    }

    @Test
    void aSoftMaskBindingKeepsTheLegacyPath() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);
        TerrainIntentV2.ConstraintMapBinding hard = intent.mapReferences().getFirst();
        TerrainIntentV2.ConstraintMapBinding soft = new TerrainIntentV2.ConstraintMapBinding(
                hard.id(), hard.sourceId(), hard.role(), hard.artifactId(),
                TerrainIntentV2.Strength.SOFT, hard.sampling(), 1, 500_000);
        TerrainIntentV2 softIntent = new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                intent.features(), intent.relations(), intent.constraints(), intent.environment(),
                List.of(soft), intent.structures(), intent.provenance());

        assertTrue(stage.resolve(request, REQUEST, softIntent, TOKEN).isEmpty(),
                "only a HARD LAND_WATER_MASK is a normative foundation input (ADR 0038 D2-1)");
    }

    @Test
    void aMaskNoDataCellIsRejectedAsOwnerlessNotFilled(@TempDir Path root) throws IOException {
        // A HARD mask forbids no-data at bind time, so the ownerless kernel invariant is exercised
        // through a directly constructed foundation over a SOFT-bindable no-data mask: the guard is
        // defence in depth — no candidate may ever fall back to an implicit baseline (ADR 0038 D1-5).
        MacroFoundationV2 foundation = new MacroFoundationV2(
                noDataMaskField(root), new GenerationRequestV2.FoundationBaseLevels(54, 46),
                EXTENT, List.of());

        assertEquals(1, foundation.mediumAt(0, 0));
        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> foundation.mediumAt(1, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.OWNERLESS_CELL, rejection.failureCode());
    }

    @Test
    void overlappingProducersWithoutAContractAreRejected(@TempDir Path root) throws IOException {
        // The 2x1 mask is (land, water); both producers claim land so only the overlap itself is
        // under test here, and the second producer covers the land cell alone.
        MacroFoundationV2.ProducerLayer first = producer(2, "wide-plain", (x, z) -> true);
        MacroFoundationV2.ProducerLayer second = producer(3, "narrow-plain", (x, z) -> x == 0);
        MacroFoundationV2 foundation = new MacroFoundationV2(
                fullMaskField(root), new GenerationRequestV2.FoundationBaseLevels(54, 46),
                EXTENT, List.of(first, second));

        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> foundation.effectiveOwnerIndexAt(0, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.UNDECLARED_OVERLAP, rejection.failureCode());
        assertTrue(rejection.getMessage().contains("wide-plain")
                && rejection.getMessage().contains("narrow-plain"), rejection::getMessage);
    }

    @Test
    void aProducerReplacesTheBackgroundInsideItsOwnFootprintOnly(@TempDir Path root) throws IOException {
        // ADR 0038 D1-3: replacement is declarative. Inside the footprint the producer is the single
        // source of medium and elevation; one cell outside it the background is untouched.
        MacroFoundationV2 foundation = new MacroFoundationV2(
                fullMaskField(root), new GenerationRequestV2.FoundationBaseLevels(54, 46),
                EXTENT, List.of(producer(2, "inland-plain", (x, z) -> x == 0)));

        assertEquals(2, foundation.effectiveOwnerIndexAt(0, 0));
        assertEquals(1, foundation.mediumAt(0, 0));
        assertEquals(60_000_000, foundation.elevationMillionthsAt(0, 0));
        assertEquals(MacroFoundationV2.BACKGROUND_OWNER_INDEX, foundation.effectiveOwnerIndexAt(1, 0));
        assertEquals(0, foundation.mediumAt(1, 0));
        assertEquals(46_000_000, foundation.elevationMillionthsAt(1, 0));
    }

    @Test
    void aProducerMediumTheHardMaskContradictsIsRejected(@TempDir Path root) throws IOException {
        // The HARD mask is the land-water authority (ADR 0038 D2-3): a producer declaring land over a
        // cell the mask declares water is two HARD sources disagreeing, never a precedence question.
        MacroFoundationV2 foundation = new MacroFoundationV2(
                fullMaskField(root), new GenerationRequestV2.FoundationBaseLevels(54, 46),
                EXTENT, List.of(producer(2, "flooded-plain", (x, z) -> true)));

        assertEquals(1, foundation.mediumAt(0, 0));
        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> foundation.mediumAt(1, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION, rejection.failureCode());
        assertTrue(rejection.getMessage().contains(MacroFoundationV2.RULE_PRODUCER_MASK_MEDIUM_CONFLICT),
                rejection::getMessage);
    }

    @Test
    void aProducerElevationOutsideTheVerticalContractIsRejected(@TempDir Path root) throws IOException {
        // Rejected, never clamped — the same rule the elevation guide gets.
        MacroFoundationV2 foundation = new MacroFoundationV2(
                fullMaskField(root), new GenerationRequestV2.FoundationBaseLevels(54, 46),
                EXTENT,
                List.of(new MacroFoundationV2.ProducerLayer(2, "sky-plain",
                        TerrainIntentV2.FeatureKind.PLAIN, (x, z) -> x == 0,
                        (x, z) -> 1, (x, z) -> 73_000_000)));

        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> foundation.elevationMillionthsAt(0, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION, rejection.failureCode());
        assertTrue(rejection.getMessage()
                        .contains(MacroFoundationV2.RULE_PRODUCER_ELEVATION_OUT_OF_CONTRACT),
                rejection::getMessage);
    }

    @Test
    void aHardGuideDisagreeingWithAProducerIsRejectedAndASoftOneYields(@TempDir Path root)
            throws IOException {
        // Two declared sources for one height: HARD fails closed (AGENTS.md §7), SOFT yields to the
        // cell's effective owner and leaves the difference to the published residual.
        MacroFoundationV2.ProducerLayer producer = producer(2, "inland-plain", (x, z) -> x == 0);
        MacroFoundationV2 hard = new MacroFoundationV2(
                fullMaskField(root),
                Optional.of(new MacroFoundationV2.HeightGuideV2(
                        heightGuideField(root, new int[] {50, 51}),
                        TerrainIntentV2.Strength.HARD, 0, 32_000_000L, 72_000_000L)),
                new GenerationRequestV2.FoundationBaseLevels(54, 46), EXTENT, List.of(producer));

        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> hard.elevationMillionthsAt(0, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION, rejection.failureCode());
        assertTrue(rejection.getMessage()
                        .contains(MacroFoundationV2.RULE_HEIGHT_GUIDE_PRODUCER_CONFLICT),
                rejection::getMessage);

        MacroFoundationV2 soft = new MacroFoundationV2(
                fullMaskField(root),
                Optional.of(new MacroFoundationV2.HeightGuideV2(
                        heightGuideField(root, new int[] {50, 51}),
                        TerrainIntentV2.Strength.SOFT, 0, 32_000_000L, 72_000_000L)),
                new GenerationRequestV2.FoundationBaseLevels(54, 46), EXTENT, List.of(producer));
        assertEquals(60_000_000, soft.elevationMillionthsAt(0, 0));
        assertEquals(50_000_000, soft.desiredHeightMillionthsAt(0, 0));
    }

    @Test
    void compilesOneProducerLayerPerDeclaredPlainFeature() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(PLAIN_REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(PLAIN_INTENT);
        TerrainIntentV2.PolygonGeometry polygon = (TerrainIntentV2.PolygonGeometry) intent.features()
                .stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.PLAIN)
                .findFirst().orElseThrow().geometry();

        MacroFoundationV2 foundation =
                stage.resolve(request, PLAIN_REQUEST, intent, TOKEN).orElseThrow();

        int producerCells = 0;
        int backgroundCells = 0;
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                int owner = foundation.effectiveOwnerIndexAt(x, z);
                if (owner == MacroFoundationV2.BACKGROUND_OWNER_INDEX) {
                    backgroundCells++;
                    continue;
                }
                // The single declared producer takes the first index above the background.
                assertEquals(MacroFoundationV2.BACKGROUND_OWNER_INDEX + 1, owner);
                producerCells++;
                // Terrestrial by contract, and lifted above the declared land base level of 54.
                assertEquals(1, foundation.mediumAt(x, z));
                assertTrue(foundation.elevationMillionthsAt(x, z) > 54_000_000,
                        "producer cell " + x + ',' + z + " is not above the background base level");
            }
        }
        assertTrue(producerCells > 0, "the declared PLAIN produced no cell");
        assertEquals(64 * 64, producerCells + backgroundCells);
        // The footprint is the compiled polygon's own interior, not a bounding box.
        assertTrue(producerCells < polygonBoundingBoxCells(polygon),
                "the producer footprint is not bounded by its declared polygon");
    }

    @Test
    void theHeightGuideOutranksTheMediumBaseLevelAndNoDataFallsBackToIt() throws IOException {
        // ADR 0038 D2-2 allows exactly two explicit elevation sources. V2-19-06 fixes their priority:
        // the guide wins per cell, the declared per-medium base level fills what the guide does not
        // specify, and nothing else may contribute a height.
        GenerationRequestV2 request = codec.readGenerationRequest(GUIDED_REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(GUIDED_INTENT);
        int[] mask = HeightGuideExampleFixtureV2.maskSamples();

        MacroFoundationV2 foundation = stage.resolve(request, GUIDED_REQUEST, intent, TOKEN).orElseThrow();

        assertTrue(foundation.heightGuide().isPresent());
        int guided = 0;
        int fellBack = 0;
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                int maskSample = mask[z * 64 + x];
                if (HeightGuideExampleFixtureV2.isNoDataCell(x, z)) {
                    assertEquals(MacroFoundationV2.NO_HEIGHT, foundation.desiredHeightMillionthsAt(x, z));
                    assertEquals(maskSample == 1 ? 54_000_000 : 46_000_000,
                            foundation.elevationMillionthsAt(x, z), "no-data fallback at " + x + ',' + z);
                    fellBack++;
                    continue;
                }
                int expected = HeightGuideExampleFixtureV2.sampleAt(x, z, maskSample) * 1_000_000;
                assertEquals(expected, foundation.desiredHeightMillionthsAt(x, z));
                assertEquals(expected, foundation.elevationMillionthsAt(x, z), "guided at " + x + ',' + z);
                // The guide never touches the medium: that stays the mask's alone.
                assertEquals(maskSample, foundation.mediumAt(x, z));
                guided++;
            }
        }
        assertEquals(16, fellBack);
        assertEquals(64 * 64 - 16, guided);
    }

    @Test
    void aGuideValueOutsideTheDeclaredVerticalContractIsRejected(@TempDir Path root) throws IOException {
        // Defence in depth for the kernel: the request record already refuses a height encoding whose
        // declared range decodes outside the request's extent, so this guard exists for a foundation
        // assembled directly — a future producer tier included. Never clamped into range.
        MacroFoundationV2 foundation = new MacroFoundationV2(
                fullMaskField(root),
                Optional.of(new MacroFoundationV2.HeightGuideV2(
                        heightGuideField(root, new int[] {50, 70}),
                        TerrainIntentV2.Strength.SOFT, 0, 32_000_000L, 60_000_000L)),
                new GenerationRequestV2.FoundationBaseLevels(54, 46), EXTENT, List.of());

        assertEquals(50_000_000, foundation.elevationMillionthsAt(0, 0));
        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> foundation.elevationMillionthsAt(1, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION, rejection.failureCode());
        assertTrue(rejection.getMessage().contains(MacroFoundationV2.RULE_HEIGHT_GUIDE_OUT_OF_CONTRACT),
                rejection::getMessage);
    }

    @Test
    void aSecondGuideOrAZoneLabelBindingIsRejectedByRoleCardinality() throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(GUIDED_REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(GUIDED_INTENT);
        TerrainIntentV2.ConstraintMapBinding guide = bindingOf(
                intent, TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE);
        TerrainIntentV2.ConstraintMapBinding mask = bindingOf(
                intent, TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK);

        TerrainIntentV2 twoGuides = withMapReferences(intent, List.of(mask, guide,
                new TerrainIntentV2.ConstraintMapBinding("second-height-binding",
                        "constraint-source:other-height", guide.role(),
                        "constraint:height-guide:sha256-" + "1".repeat(64),
                        guide.strength(), guide.sampling(), guide.toleranceBlocks(),
                        guide.weightMillionths())));
        SurfaceFoundationExceptionV2 tooManyGuides = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> stage.resolve(request, GUIDED_REQUEST, twoGuides, TOKEN));
        assertEquals(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION, tooManyGuides.failureCode());
        assertTrue(tooManyGuides.getMessage().contains("at most one HEIGHT_GUIDE"),
                tooManyGuides::getMessage);

        // A role with no consumer on this path is rejected rather than accepted and ignored.
        TerrainIntentV2 withZone = withMapReferences(intent, List.of(mask,
                new TerrainIntentV2.ConstraintMapBinding("zone-binding", "constraint-source:zones",
                        TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP,
                        "constraint:zone-label-map:sha256-" + "0".repeat(64),
                        TerrainIntentV2.Strength.SOFT, TerrainIntentV2.Sampling.NEAREST, 0, 500_000)));
        SurfaceFoundationExceptionV2 zone = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> stage.resolve(request, GUIDED_REQUEST, withZone, TOKEN));
        assertTrue(zone.getMessage().contains("ZONE_LABEL_MAP"), zone::getMessage);
    }

    @Test
    void resolvesIdenticalGuidedElevationsAcrossThreadLocaleAndTimezone() throws Exception {
        GenerationRequestV2 request = codec.readGenerationRequest(GUIDED_REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(GUIDED_INTENT);
        Locale locale = Locale.getDefault();
        TimeZone timeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            long first = mediumDigest(stage.resolve(request, GUIDED_REQUEST, intent, TOKEN).orElseThrow());

            Locale.setDefault(Locale.forLanguageTag("en-US"));
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            AtomicReference<Long> other = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    other.set(mediumDigest(
                            stage.resolve(request, GUIDED_REQUEST, intent, TOKEN).orElseThrow()));
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            worker.start();
            worker.join();
            assertEquals(first, other.get());
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(timeZone);
        }
    }

    @Test
    void resolvesIdenticalMediumsAcrossThreadLocaleAndTimezone() throws Exception {
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);
        Locale locale = Locale.getDefault();
        TimeZone timeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            MacroFoundationV2 first = stage.resolve(request, REQUEST, intent, TOKEN).orElseThrow();
            long firstDigest = mediumDigest(first);

            Locale.setDefault(Locale.forLanguageTag("en-US"));
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            AtomicReference<Long> other = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    other.set(mediumDigest(stage.resolve(request, REQUEST, intent, TOKEN).orElseThrow()));
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            worker.start();
            worker.join();
            assertEquals(firstDigest, other.get());
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(timeZone);
        }
    }

    @Test
    void detailReplacesTheBaseLevelOfBackgroundCellsButNeverGuideCells(@TempDir Path root)
            throws IOException {
        // ADR 0041 D2: coherent detail replaces the flat per-medium base level on background cells the
        // guide does not specify, and nothing else. Cell 0 is guided (55) and must stay exactly 55; the
        // other two are background base-level cells and gain the kernel's offset over their base level.
        MacroFoundationV2.BackgroundDetailV2 detail = detail(4, 3, 8, 2);
        MacroFoundationV2 foundation = new MacroFoundationV2(
                boundField(root, new int[] {1, 1, 0}, 3, 1, false),
                Optional.of(new MacroFoundationV2.HeightGuideV2(
                        heightGuideField(root, new int[] {55, 255, 255}),
                        TerrainIntentV2.Strength.SOFT, 0, 32_000_000L, 72_000_000L)),
                new GenerationRequestV2.FoundationBaseLevels(54, 46), EXTENT,
                List.of(), Optional.of(detail), 50_000_000);

        assertEquals(55_000_000, foundation.elevationMillionthsAt(0, 0),
                "a guided cell must keep the guide's height, not a detailed one");
        assertEquals(54_000_000 + detail.land().valueMillionthsAt(1, 0),
                foundation.elevationMillionthsAt(1, 0));
        assertEquals(46_000_000 + detail.water().valueMillionthsAt(2, 0),
                foundation.elevationMillionthsAt(2, 0));
        // The medium is never touched: it stays the mask's alone (ADR 0041 凍結4).
        assertEquals(1, foundation.mediumAt(1, 0));
        assertEquals(0, foundation.mediumAt(2, 0));
    }

    @Test
    void detailLeavesProducerOwnedCellsAtTheProducerElevation(@TempDir Path root) throws IOException {
        // ADR 0041 D2: a producer owns the elevation of the cells in its footprint, so detail — which
        // only replaces the background base level — must not touch a producer cell.
        MacroFoundationV2.BackgroundDetailV2 detail = detail(4, 3, 8, 2);
        MacroFoundationV2 foundation = new MacroFoundationV2(
                fullMaskField(root), Optional.empty(),
                new GenerationRequestV2.FoundationBaseLevels(54, 46), EXTENT,
                List.of(producer(2, "inland-plain", (x, z) -> x == 0)),
                Optional.of(detail), 50_000_000);

        assertEquals(60_000_000, foundation.elevationMillionthsAt(0, 0),
                "a producer cell must keep the producer's elevation, not a detailed one");
        assertEquals(46_000_000 + detail.water().valueMillionthsAt(1, 0),
                foundation.elevationMillionthsAt(1, 0));
    }

    @Test
    void aDetailedElevationOutsideTheVerticalContractIsRejected(@TempDir Path root) throws IOException {
        // ADR 0041 D5 defence in depth: unreachable through the request gate (the amplitude is bounded
        // and validated), but a directly assembled foundation whose extent excludes any non-zero detail
        // fails closed rather than clamping. Pinned to the extent of exactly the base level.
        MacroFoundationV2.BackgroundDetailV2 detail = detail(8, 0, 8, 2);
        MacroFoundationV2 foundation = new MacroFoundationV2(
                boundField(root, new int[] {1, 1, 1, 1}, 4, 1, false), Optional.empty(),
                new GenerationRequestV2.FoundationBaseLevels(54, 46),
                new MacroFoundationV2.VerticalExtentV2(54_000_000L, 54_000_000L),
                List.of(), Optional.of(detail), 32_000_000);

        int cellWithDetail = -1;
        for (int x = 0; x < 4; x++) {
            if (detail.land().valueMillionthsAt(x, 0) != 0) {
                cellWithDetail = x;
                break;
            }
        }
        assertTrue(cellWithDetail >= 0, "the kernel produced no non-zero detail to exercise the guard");
        int cell = cellWithDetail;
        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> foundation.elevationMillionthsAt(cell, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION, rejection.failureCode());
        assertTrue(rejection.getMessage().contains(MacroFoundationV2.RULE_DETAIL_OUT_OF_CONTRACT),
                rejection::getMessage);
    }

    /** A resolved coherent detail for both mediums built from the fixtures' own request seed. */
    private static MacroFoundationV2.BackgroundDetailV2 detail(
            int landAmplitudeBlocks, int waterAmplitudeBlocks, int wavelengthBlocks, int octaves) {
        return new MacroFoundationV2.BackgroundDetailV2(
                com.github.nankotsu029.landformcraft.generator.v2.detail.CoherentDetailKernelV2.forMedium(
                        827_413L, "land", landAmplitudeBlocks, wavelengthBlocks, octaves),
                com.github.nankotsu029.landformcraft.generator.v2.detail.CoherentDetailKernelV2.forMedium(
                        827_413L, "water", waterAmplitudeBlocks, wavelengthBlocks, octaves));
    }

    /** A land producer at a fixed elevation of Y=60, so only the tested condition varies. */
    private static MacroFoundationV2.ProducerLayer producer(
            int ownerIndex,
            String ownerId,
            MacroFoundationV2.FootprintPredicate footprint
    ) {
        return new MacroFoundationV2.ProducerLayer(ownerIndex, ownerId,
                TerrainIntentV2.FeatureKind.PLAIN, footprint, (x, z) -> 1, (x, z) -> 60_000_000);
    }

    /** Cell count of the polygon's axis-aligned bounding box in release-local cells. */
    private static long polygonBoundingBoxCells(TerrainIntentV2.PolygonGeometry polygon) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (TerrainIntentV2.Point2 point : polygon.rings().getFirst()) {
            long x = point.xMillionths() * 63L / TerrainIntentV2.FIXED_SCALE;
            long z = point.zMillionths() * 63L / TerrainIntentV2.FIXED_SCALE;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        return (maxX - minX + 1L) * (maxZ - minZ + 1L);
    }

    private static long mediumDigest(MacroFoundationV2 foundation) {
        long digest = 1469598103934665603L;
        for (int z = 0; z < foundation.length(); z++) {
            for (int x = 0; x < foundation.width(); x++) {
                digest = (digest ^ foundation.mediumAt(x, z)) * 1099511628211L;
                digest = (digest ^ foundation.elevationMillionthsAt(x, z)) * 1099511628211L;
            }
        }
        return digest;
    }

    /** 2x1 SOFT-bound mask whose second cell is the declared no-data sentinel (9). */
    private BoundConstraintFieldV2 noDataMaskField(Path root) throws IOException {
        return boundField(root, new int[] {1, 9}, 2, 1, true);
    }

    /** 2x1 SOFT-bound elevation guide over the given absolute block-Y samples. */
    private BoundConstraintFieldV2 heightGuideField(Path root, int[] samples) throws IOException {
        byte[] bytes = new byte[samples.length];
        for (int index = 0; index < samples.length; index++) {
            bytes[index] = (byte) samples[index];
        }
        Path png = root.resolve("guide.png");
        new NumericPngEncoder().writeU8(png, samples.length, 1, bytes);
        GenerationRequestV2.ConstraintMapSource source = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:guide", "guide.png", Sha256.file(png), samples.length, 1,
                GenerationRequestV2.DecoderKind.HEIGHT_RASTER,
                new GenerationRequestV2.CoordinateMapping(
                        GenerationRequestV2.CoordinateOrigin.NORTH_WEST, GenerationRequestV2.XAxis.EAST,
                        GenerationRequestV2.ZAxis.SOUTH, GenerationRequestV2.PixelReference.PIXEL_CENTER,
                        GenerationRequestV2.AspectMismatchPolicy.REJECT,
                        GenerationRequestV2.QuarterTurn.DEGREES_0,
                        false, false, new GenerationRequestV2.PixelCrop(0, 0, samples.length, 1)),
                new GenerationRequestV2.HeightEncoding(
                        1, GenerationRequestV2.SampleType.U8, GenerationRequestV2.RasterChannel.GRAY,
                        GenerationRequestV2.HeightValueMeaning.ABSOLUTE_BLOCK_Y, 1_000_000L, 0L,
                        new GenerationRequestV2.IntRange(32, 72),
                        new GenerationRequestV2.NoDataSentinel(255)));
        TerrainIntentV2.ConstraintMapBinding binding = new TerrainIntentV2.ConstraintMapBinding(
                "guide-binding", "constraint-source:guide",
                TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE,
                "constraint:height-guide:sha256-" + "0".repeat(64),
                TerrainIntentV2.Strength.SOFT, TerrainIntentV2.Sampling.NEAREST, 0, 500_000);
        return new ConstraintMapFieldBindingV2().bind(
                root.resolve("request.json"),
                new GenerationRequestV2.Bounds(samples.length, 1, 32, 72, 50),
                source, binding, ConstraintMapDecodeLimits.defaults(), TOKEN);
    }

    private static TerrainIntentV2.ConstraintMapBinding bindingOf(
            TerrainIntentV2 intent,
            TerrainIntentV2.ConstraintMapRole role
    ) {
        return intent.mapReferences().stream()
                .filter(binding -> binding.role() == role)
                .findFirst()
                .orElseThrow();
    }

    private static TerrainIntentV2 withMapReferences(
            TerrainIntentV2 intent,
            List<TerrainIntentV2.ConstraintMapBinding> bindings
    ) {
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                intent.features(), intent.relations(), intent.constraints(), intent.environment(),
                bindings, intent.structures(), intent.provenance());
    }

    /** 2x1 fully specified mask (land, water). */
    private BoundConstraintFieldV2 fullMaskField(Path root) throws IOException {
        return boundField(root, new int[] {1, 0}, 2, 1, false);
    }

    private BoundConstraintFieldV2 boundField(
            Path root, int[] samples, int width, int length, boolean withNoData) throws IOException {
        byte[] bytes = new byte[samples.length];
        for (int index = 0; index < samples.length; index++) {
            bytes[index] = (byte) samples[index];
        }
        Path png = root.resolve("mask.png");
        new NumericPngEncoder().writeU8(png, width, length, bytes);
        GenerationRequestV2.CategoricalEncoding encoding = new GenerationRequestV2.CategoricalEncoding(
                1, GenerationRequestV2.SampleType.U8, GenerationRequestV2.RasterChannel.GRAY,
                List.of(new GenerationRequestV2.LabelMapping(0, "water"),
                        new GenerationRequestV2.LabelMapping(1, "land")),
                withNoData ? new GenerationRequestV2.NoDataSentinel(9)
                        : new GenerationRequestV2.NoDataForbidden());
        GenerationRequestV2.ConstraintMapSource source = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:mask", "mask.png", Sha256.file(png), width, length,
                GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER,
                new GenerationRequestV2.CoordinateMapping(
                        GenerationRequestV2.CoordinateOrigin.NORTH_WEST, GenerationRequestV2.XAxis.EAST,
                        GenerationRequestV2.ZAxis.SOUTH, GenerationRequestV2.PixelReference.PIXEL_CENTER,
                        GenerationRequestV2.AspectMismatchPolicy.REJECT,
                        GenerationRequestV2.QuarterTurn.DEGREES_0,
                        false, false, new GenerationRequestV2.PixelCrop(0, 0, width, length)),
                encoding);
        TerrainIntentV2.ConstraintMapBinding binding = new TerrainIntentV2.ConstraintMapBinding(
                "coast-mask-binding", "constraint-source:mask",
                TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                "constraint:land-water:sha256-" + "0".repeat(64),
                withNoData ? TerrainIntentV2.Strength.SOFT : TerrainIntentV2.Strength.HARD,
                TerrainIntentV2.Sampling.NEAREST, withNoData ? 1 : 0, withNoData ? 500_000 : 0);
        return new ConstraintMapFieldBindingV2().bind(
                root.resolve("request.json"),
                new GenerationRequestV2.Bounds(width, length, 32, 72, 50),
                source, binding, ConstraintMapDecodeLimits.defaults(), TOKEN);
    }
}
