package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionCompositorV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev-only helper that regenerates the {@code harbor-cove-64-honored-beach} HARD land-water mask from
 * its intent geometry (V2-19-09). It applies the rule V2-18-13 established for
 * {@code coastal-honored-400}, to the beach-only composition: a cell the coastal compositor owns takes
 * the composed land-water value (the mask is the export's HARD source, so it must equal what the
 * modifiers build), and every other cell keeps the macro background of
 * {@code harbor-cove-64-honored-land-water-u8.png}.
 *
 * <p>The background therefore still carries the shoreline, harbor water and rocky headland of the
 * four-contributor fixture as declared HARD input; what the beach-only intent drops is the
 * <em>modifier</em> that shaped those cells, not the mask that classifies them. Under ADR 0038 D1 the
 * macro foundation owns every such cell, which is exactly what this fixture demonstrates.</p>
 *
 * <p>Run manually after editing the intent geometry, then paste the printed SHA-256 into
 * {@code harbor-cove-64-honored-beach.request-v2.json}'s {@code expectedSha256}. Regenerating the
 * current committed geometry reproduces the current committed mask byte-for-byte, which is the
 * reconstruction invariant this helper relies on.</p>
 */
class RegenerateBeachOnlyCoastalMaskExampleTest {
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-beach.terrain-intent-v2.json");
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored-beach.request-v2.json");
    private static final Path BACKGROUND =
            Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-land-water-u8.png");
    private static final Path MASK =
            Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-beach-land-water-u8.png");

    @Test
    @Disabled("manual fixture regeneration helper (V2-19-09)")
    void rewriteBeachOnlyMask() throws Exception {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        GenerationRequestV2 request = codec.readGenerationRequest(REQUEST);
        TerrainIntentV2 intent = codec.readTerrainIntent(INTENT);

        int width = request.bounds().width();
        int length = request.bounds().length();
        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(
                new DiagnosticCompileRequestV2(
                        request.requestId(),
                        new GenerationBounds(width, length, request.bounds().minY(),
                                request.bounds().maxY(), request.bounds().waterLevel()),
                        request.generation().tileSize(),
                        request.generation().globalSeed(),
                        codec.generationRequestChecksum(request),
                        DiagnosticCompileRequestV2.defaultBudget()),
                intent);
        CoastalGeneratorRuntimeV2 runtime = CoastalGeneratorRuntimeV2.create(blueprint);

        BufferedImage background = ImageIO.read(BACKGROUND.toFile());
        BufferedImage image = new BufferedImage(width, length, BufferedImage.TYPE_BYTE_GRAY);
        int changed = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                CoastalTransitionCompositorV2.CompositionSample sample =
                        runtime.composeAt(x, z, HardLandWaterSourceV2.NONE);
                int backgroundValue = background.getRaster().getSample(x, z, 0);
                int value = sample.active() ? sample.landWater() : backgroundValue;
                image.getRaster().setSample(x, z, 0, value);
                if (value != backgroundValue) {
                    changed++;
                }
            }
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        byte[] png = buffer.toByteArray();
        Files.write(MASK, png);
        System.out.println("[V2-19-09] harbor-cove-64-honored-beach mask cells changed vs background="
                + changed + " sha256=" + Sha256.bytes(png));
    }
}
