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
 * input (ADR 0038 D1/D2/D5-1) and the fail-closed kernel invariants (D7-1).
 */
class MacroFoundationStageV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json");
    private static final CancellationToken TOKEN = () -> false;

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
                request.constraintMapBudget(), Optional.empty());
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
                noDataMaskField(root), new GenerationRequestV2.FoundationBaseLevels(54, 46), List.of());

        assertEquals(1, foundation.mediumAt(0, 0));
        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> foundation.mediumAt(1, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.OWNERLESS_CELL, rejection.failureCode());
    }

    @Test
    void overlappingProducersWithoutAContractAreRejected(@TempDir Path root) throws IOException {
        MacroFoundationV2.ProducerLayer first = new MacroFoundationV2.ProducerLayer(
                2, (x, z) -> true, (x, z) -> 1, (x, z) -> 60_000_000);
        MacroFoundationV2.ProducerLayer second = new MacroFoundationV2.ProducerLayer(
                3, (x, z) -> x == 0, (x, z) -> 0, (x, z) -> 40_000_000);
        MacroFoundationV2 foundation = new MacroFoundationV2(
                fullMaskField(root), new GenerationRequestV2.FoundationBaseLevels(54, 46),
                List.of(first, second));

        SurfaceFoundationExceptionV2 rejection = assertThrows(SurfaceFoundationExceptionV2.class,
                () -> foundation.effectiveOwnerIndexAt(0, 0));
        assertEquals(SurfaceFoundationFailureCodeV2.UNDECLARED_OVERLAP, rejection.failureCode());
        // A cell only one producer claims replaces the background declaratively (ADR 0038 D1-3).
        assertEquals(2, foundation.effectiveOwnerIndexAt(1, 0));
        assertEquals(60_000_000, foundation.elevationMillionthsAt(1, 0));
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
