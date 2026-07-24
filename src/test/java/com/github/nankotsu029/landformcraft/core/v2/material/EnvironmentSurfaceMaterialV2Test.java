package com.github.nankotsu029.landformcraft.core.v2.material;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V2-19-10: the sealed material profile and palette plan decide the exposed surface material.
 *
 * <p>The two bundled example plans are the fixture, so the mapping asserted here is exactly the one
 * a published {@code environment-fields} Release carries.</p>
 */
class EnvironmentSurfaceMaterialV2Test {
    private static final CancellationToken NEVER = () -> false;

    private static MaterialProfilePlanV2 material;
    private static MinecraftPalettePlanV2 palette;

    @BeforeAll
    static void readBundledPlans() throws IOException {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        material = codec.readMaterialProfilePlan(
                Path.of("examples/v2/material/material-profile-plan-v2.json"));
        palette = codec.readMinecraftPalettePlan(
                Path.of("examples/v2/minecraft/minecraft-palette-plan-v2.json"));
    }

    @Test
    void onlyTheConditionalOverrideClassesClaimACell() {
        EnvironmentSurfaceMaterialV2 field = EnvironmentSurfaceMaterialV2.resolve(
                palette, material, 3, 2, (x, z) -> classCodeGrid(x, z), NEVER);

        // Exposed rock and exposed sediment declare no override: the surface role's base assignment
        // stands, which is what keeps a beach sand and a cape rock.
        assertNull(field.blockStateAt(0, 0));
        assertNull(field.blockStateAt(1, 0));
        // Wet rock, wet sediment and the two snow classes resolve through the palette at SURFACE.
        assertEquals("minecraft:cobblestone", field.blockStateAt(2, 0));
        assertEquals("minecraft:mud", field.blockStateAt(0, 1));
        assertEquals("minecraft:snow_block", field.blockStateAt(1, 1));
        assertEquals("minecraft:snow_block", field.blockStateAt(2, 1));
        assertEquals(4L, field.overriddenCells());
        assertEquals(List.of("minecraft:cobblestone", "minecraft:mud",
                "minecraft:snow_block", "minecraft:snow_block"), field.overrideStates());
    }

    @Test
    void everyOverrideStateComesFromTheSealedPaletteMapping() {
        EnvironmentSurfaceMaterialV2 field = EnvironmentSurfaceMaterialV2.resolve(
                palette, material, 2, 1,
                (x, z) -> MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_WET.compactCode(),
                NEVER);
        assertEquals(palette.catalog()
                        .require(MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_WET,
                                MaterialProfilePlanV2.SurfaceAspect.SURFACE)
                        .blockState(),
                field.blockStateAt(1, 0));
    }

    @Test
    void aClassCodeOutsideTheSealedCatalogIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EnvironmentSurfaceMaterialV2.resolve(
                palette, material, 2, 2, (x, z) -> 250, NEVER));
        // The geology no-data sentinel is not a material class and must not become a block either.
        assertThrows(IllegalArgumentException.class, () -> EnvironmentSurfaceMaterialV2.resolve(
                palette, material, 2, 2, (x, z) -> 65_535, NEVER));
    }

    @Test
    void aMismatchedMaterialProfileOrCancellationFailsClosed() {
        assertThrows(CancellationException.class, () -> EnvironmentSurfaceMaterialV2.resolve(
                palette, material, 2, 2,
                (x, z) -> MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_WET.compactCode(),
                () -> true));
        assertThrows(IllegalArgumentException.class, () -> EnvironmentSurfaceMaterialV2.resolve(
                palette, material.withCanonicalChecksum("0".repeat(64)), 2, 2,
                (x, z) -> MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_WET.compactCode(),
                NEVER));
    }

    @Test
    void theBindingOverridesOnlyTheExposedNaturalSurface() {
        EnvironmentSurfaceMaterialV2 field = EnvironmentSurfaceMaterialV2.resolve(
                palette, material, 2, 2,
                (x, z) -> MaterialProfilePlanV2.SemanticMaterialClass.SEDIMENT_WET.compactCode(),
                NEVER);
        SurfaceMaterializationV2 binding = SurfaceMaterializationV2.withNaturalSurface(
                SurfaceMaterialProfileV2.builtIn(), field);

        assertEquals("minecraft:mud",
                binding.blockStateAt(SurfaceMaterialProfileV2.RoleV2.VEGETATED_SURFACE, 0, 0));
        assertEquals("minecraft:mud",
                binding.blockStateAt(SurfaceMaterialProfileV2.RoleV2.SEABED_SURFACE, 1, 1));
        // Subsurface, built structure and structural roles keep the profile's state.
        assertEquals("minecraft:dirt",
                binding.blockStateAt(SurfaceMaterialProfileV2.RoleV2.SUBSOIL, 0, 0));
        assertEquals("minecraft:stone_bricks",
                binding.blockStateAt(SurfaceMaterialProfileV2.RoleV2.STRUCTURE_CREST, 0, 0));
        assertEquals("minecraft:water",
                binding.blockStateAt(SurfaceMaterialProfileV2.RoleV2.OPEN_WATER, 0, 0));

        // Unbound, every role resolves to the frozen V2-2 table.
        SurfaceMaterializationV2 builtIn = SurfaceMaterializationV2.builtIn();
        assertEquals("minecraft:grass_block",
                builtIn.blockStateAt(SurfaceMaterialProfileV2.RoleV2.VEGETATED_SURFACE, 0, 0));
    }

    private static int classCodeGrid(int x, int z) {
        MaterialProfilePlanV2.SemanticMaterialClass kind = z == 0
                ? switch (x) {
                    case 0 -> MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_EXPOSED;
                    case 1 -> MaterialProfilePlanV2.SemanticMaterialClass.SEDIMENT_EXPOSED;
                    default -> MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_WET;
                }
                : switch (x) {
                    case 0 -> MaterialProfilePlanV2.SemanticMaterialClass.SEDIMENT_WET;
                    case 1 -> MaterialProfilePlanV2.SemanticMaterialClass.SNOW_COVERED_ROCK;
                    default -> MaterialProfilePlanV2.SemanticMaterialClass.SNOW_COVERED_SEDIMENT;
                };
        return kind.compactCode();
    }
}
