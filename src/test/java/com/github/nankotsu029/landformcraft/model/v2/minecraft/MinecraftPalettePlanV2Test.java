package com.github.nankotsu029.landformcraft.model.v2.minecraft;

import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftPalettePlanV2Test {
    @Test
    void rejectsUnknownCompatibilityFutureResolverAndNonCanonicalBlockStates() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftPalettePlanV2.Compatibility(
                "1.21.11", 4671, "minecraft-palette-resolver-v2",
                MinecraftPalettePlanV2.Compatibility.CONTRACT_VERSION));
        assertThrows(IllegalArgumentException.class, () -> new MinecraftPalettePlanV2.Compatibility(
                "1.20.0", 4671, MinecraftPalettePlanV2.RESOLVER_VERSION,
                MinecraftPalettePlanV2.Compatibility.CONTRACT_VERSION));
        assertThrows(IllegalArgumentException.class, () -> new MinecraftPalettePlanV2.Mapping(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_EXPOSED,
                "material.host-rock-exposed", 1,
                MaterialProfilePlanV2.SurfaceAspect.SURFACE,
                "minecraft:stone{nbt:true}"));
        assertThrows(IllegalArgumentException.class, () -> new MinecraftPalettePlanV2.Mapping(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_EXPOSED,
                "material.host-rock-exposed", 1,
                MaterialProfilePlanV2.SurfaceAspect.SURFACE,
                "minecraft:stone[facing=north,axis=y]"));
    }

    @Test
    void rejectsIncompleteCatalogAndUnknownMaterialCatalogBinding() {
        List<MinecraftPalettePlanV2.Mapping> mappings = new ArrayList<>();
        mappings.add(new MinecraftPalettePlanV2.Mapping(
                MaterialProfilePlanV2.SemanticMaterialClass.HOST_ROCK_EXPOSED,
                "material.host-rock-exposed", 1,
                MaterialProfilePlanV2.SurfaceAspect.SURFACE,
                "minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> new MinecraftPalettePlanV2.Catalog(
                MinecraftPalettePlanV2.Catalog.VERSION,
                MinecraftPalettePlanV2.Catalog.ID,
                MinecraftPalettePlanV2.Catalog.CONTRACT_VERSION,
                mappings,
                new MinecraftPalettePlanV2.CatalogBudget(
                        MinecraftPalettePlanV2.CatalogBudget.VERSION,
                        MinecraftPalettePlanV2.MAPPING_COUNT,
                        32L * 1024L,
                        16)));
        assertThrows(IllegalArgumentException.class, () -> new MinecraftPalettePlanV2.MaterialProfileBinding(
                1, "a".repeat(64), "landformcraft.other-catalog", 1,
                MinecraftPalettePlanV2.MaterialProfileBinding.CONTRACT_VERSION));
    }

    @Test
    void mappingCountMatchesSixClassesTimesThreeAspects() {
        assertEquals(18, MinecraftPalettePlanV2.MAPPING_COUNT);
    }
}
