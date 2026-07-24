package com.github.nankotsu029.landformcraft.core.v2.material;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-19-10: the closed, allowlisted role catalog that replaced the inlined block-state literals. */
class SurfaceMaterialProfileV2Test {
    @Test
    void theBuiltInTableIsTheElevenStateV22Vocabulary() {
        SurfaceMaterialProfileV2 profile = SurfaceMaterialProfileV2.builtIn();

        assertEquals(SurfaceMaterialProfileV2.BUILT_IN_PROFILE_ID, profile.profileId());
        // The audit measured paletteSize 11 on a published coastal tile; the role table is the same
        // vocabulary, now declared instead of inlined.
        assertEquals(List.of(
                        "minecraft:bedrock",
                        "minecraft:air",
                        "minecraft:water",
                        "minecraft:grass_block",
                        "minecraft:dirt",
                        "minecraft:stone",
                        "minecraft:sand",
                        "minecraft:sandstone",
                        "minecraft:cobblestone",
                        "minecraft:gravel",
                        "minecraft:stone_bricks"),
                profile.declaredBlockStates());
        assertEquals(11, profile.declaredBlockStates().size());
    }

    @Test
    void everyRoleIsMappedAndCarriesItsColumnTier() {
        SurfaceMaterialProfileV2 profile = SurfaceMaterialProfileV2.builtIn();
        for (SurfaceMaterialProfileV2.RoleV2 role : SurfaceMaterialProfileV2.RoleV2.values()) {
            assertTrue(profile.blockState(role).startsWith("minecraft:"), role.name());
        }
        // Only the exposed natural surface is open to an environment material override.
        assertEquals(SurfaceMaterialProfileV2.LayerV2.NATURAL_SURFACE,
                SurfaceMaterialProfileV2.RoleV2.VEGETATED_SURFACE.layer());
        assertEquals(SurfaceMaterialProfileV2.LayerV2.NATURAL_SUBSURFACE,
                SurfaceMaterialProfileV2.RoleV2.SUBSOIL.layer());
        assertEquals(SurfaceMaterialProfileV2.LayerV2.BUILT_STRUCTURE,
                SurfaceMaterialProfileV2.RoleV2.STRUCTURE_CREST.layer());
        assertEquals(SurfaceMaterialProfileV2.LayerV2.STRUCTURAL,
                SurfaceMaterialProfileV2.RoleV2.OPEN_WATER.layer());
    }

    @Test
    void anArbitraryOrUnknownBlockStateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceMaterialProfileV2.of("test.unknown-state", table("minecraft:diamond_block")));
        // Not a Minecraft identifier at all, and an NBT payload: both fail before the allowlist.
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceMaterialProfileV2.of("test.not-canonical", table("stone")));
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceMaterialProfileV2.of("test.nbt", table("minecraft:chest{Items:[]}")));
    }

    @Test
    void anIncompleteRoleTableIsRejected() {
        Map<SurfaceMaterialProfileV2.RoleV2, String> partial =
                new EnumMap<>(SurfaceMaterialProfileV2.RoleV2.class);
        partial.put(SurfaceMaterialProfileV2.RoleV2.BEDROCK_FLOOR, "minecraft:bedrock");
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceMaterialProfileV2.of("test.partial", partial));
    }

    @Test
    void theChecksumIdentifiesTheOrderedRoleTable() {
        String builtIn = SurfaceMaterialProfileV2.builtIn().canonicalChecksum();
        assertEquals(builtIn, SurfaceMaterialProfileV2.builtIn().canonicalChecksum());
        assertEquals(64, builtIn.length());

        Map<SurfaceMaterialProfileV2.RoleV2, String> changed =
                new EnumMap<>(SurfaceMaterialProfileV2.RoleV2.class);
        for (SurfaceMaterialProfileV2.RoleV2 role : SurfaceMaterialProfileV2.RoleV2.values()) {
            changed.put(role, SurfaceMaterialProfileV2.builtIn().blockState(role));
        }
        changed.put(SurfaceMaterialProfileV2.RoleV2.VEGETATED_SURFACE, "minecraft:mud");
        assertNotEquals(builtIn,
                SurfaceMaterialProfileV2.of("test.changed", changed).canonicalChecksum());
    }

    private static Map<SurfaceMaterialProfileV2.RoleV2, String> table(String vegetatedSurface) {
        Map<SurfaceMaterialProfileV2.RoleV2, String> states =
                new EnumMap<>(SurfaceMaterialProfileV2.RoleV2.class);
        for (SurfaceMaterialProfileV2.RoleV2 role : SurfaceMaterialProfileV2.RoleV2.values()) {
            states.put(role, SurfaceMaterialProfileV2.builtIn().blockState(role));
        }
        states.put(SurfaceMaterialProfileV2.RoleV2.VEGETATED_SURFACE, vegetatedSurface);
        return states;
    }
}
