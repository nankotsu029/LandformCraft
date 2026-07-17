package com.github.nankotsu029.landformcraft.compatibility;

import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.generator.TerrainGenerator;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.GenerationOptions;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.OutputOptions;
import com.github.nankotsu029.landformcraft.worldedit.BlockColumnMaterializer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks the existing v1 contract and output while the isolated v2 diagnostic path evolves. */
class V1CompatibilityGoldenTest {
    private static final String INTENT_JSON_SHA256 =
            "9d482b33bcf721c9f8b065ef5050bb926cfe3b251ee857c5bb42dba2c72f1708";
    private static final String BLUEPRINT_JSON_SHA256 =
            "e19a9585c34ae6ade4b6e84f466664645ec07c2326faea7921b6c8d698a9046f";
    private static final String TERRAIN_PLAN_SHA256 =
            "8c1990af04be2f3de9c8bcd7ca6e522780f620c8bc052e04302dbfc8e0d45cfb";
    private static final String BLOCK_STREAM_SHA256 =
            "f7d37be8e2c8b5758bd62513cc134373f775d5e2e60a08e5db74ec2961962220";

    @Test
    void v1RockyCoastFixtureAndGeneratorRemainBitStable() throws Exception {
        LandformDataCodec codec = new LandformDataCodec();
        var intent = codec.readTerrainIntent(Path.of("examples/rocky-coast/terrain-intent.json"));
        var request = new GenerationRequest(
                1,
                "v1-compatibility-golden",
                new GenerationBounds(32, 32, 0, 31, 15),
                "v1 compatibility golden",
                List.of(),
                new GenerationOptions(1, 827_413L),
                new OutputOptions(32, true, false)
        );
        var blueprint = new BlueprintCompiler().compile(request, intent, 0);
        var plan = new TerrainGenerator().generate(blueprint, () -> false);

        String intentDigest = Sha256.bytes(
                codec.writeJsonString(intent).getBytes(StandardCharsets.UTF_8));
        String blueprintDigest = Sha256.bytes(
                codec.writeJsonString(blueprint).getBytes(StandardCharsets.UTF_8));

        assertAll(
                () -> assertEquals(INTENT_JSON_SHA256, intentDigest),
                () -> assertEquals(BLUEPRINT_JSON_SHA256, blueprintDigest),
                () -> assertEquals(TERRAIN_PLAN_SHA256, plan.checksum()),
                () -> assertEquals(BLOCK_STREAM_SHA256, blockStreamDigest(plan))
        );
    }

    private static String blockStreamDigest(com.github.nankotsu029.landformcraft.model.TerrainPlan plan) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        ByteBuffer value = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        BlockColumnMaterializer materializer = new BlockColumnMaterializer();
        var bounds = plan.blueprint().bounds();
        for (int z = 0; z < bounds.length(); z++) {
            for (int x = 0; x < bounds.width(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    value.clear();
                    value.putInt(materializer.paletteIdAt(plan, x, y, z));
                    digest.update(value.array());
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
