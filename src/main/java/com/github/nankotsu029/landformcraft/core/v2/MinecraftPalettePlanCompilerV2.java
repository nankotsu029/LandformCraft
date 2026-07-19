package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.EnvironmentBlockStateCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.minecraft.MinecraftPalettePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compiles the frozen V2-4-08 Minecraft palette: closed semantic→block mapping, 1.21.11 compatibility
 * tuple, and checksum binding to a sealed material-profile plan.
 */
public final class MinecraftPalettePlanCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public MinecraftPalettePlanV2 compile(MaterialProfilePlanV2 materialProfilePlan) {
        Objects.requireNonNull(materialProfilePlan, "materialProfilePlan");
        MinecraftPalettePlanV2.Catalog catalog = builtInCatalog();
        for (MinecraftPalettePlanV2.Mapping mapping : catalog.mappings()) {
            EnvironmentBlockStateCatalogV2.requireKnown(mapping.blockState());
        }
        long distinct = catalog.mappings().stream()
                .map(MinecraftPalettePlanV2.Mapping::blockState)
                .distinct()
                .count();
        MinecraftPalettePlanV2 draft = new MinecraftPalettePlanV2(
                MinecraftPalettePlanV2.VERSION,
                MinecraftPalettePlanV2.PALETTE_CONTRACT_VERSION,
                new MinecraftPalettePlanV2.MaterialProfileBinding(
                        MinecraftPalettePlanV2.MaterialProfileBinding.VERSION,
                        materialProfilePlan.canonicalChecksum(),
                        materialProfilePlan.catalog().catalogId(),
                        materialProfilePlan.catalog().catalogVersion(),
                        MinecraftPalettePlanV2.MaterialProfileBinding.CONTRACT_VERSION),
                MinecraftPalettePlanV2.Compatibility.standard(),
                catalog,
                new MinecraftPalettePlanV2.ResourceBudget(
                        MinecraftPalettePlanV2.ResourceBudget.VERSION,
                        MinecraftPalettePlanV2.MAPPING_COUNT,
                        MinecraftPalettePlanV2.ResourceBudget.MAXIMUM_PALETTE_SIZE,
                        MinecraftPalettePlanV2.ResourceBudget.MAXIMUM_PALETTE_RETAINED_BYTES,
                        MinecraftPalettePlanV2.MAX_CANONICAL_BYTES,
                        Math.addExact(8_192L, Math.multiplyExact(distinct, 64L))),
                "0".repeat(64));
        draft.requireMaterialProfilePlan(materialProfilePlan);
        return codec.sealMinecraftPalettePlan(draft);
    }

    private static MinecraftPalettePlanV2.Catalog builtInCatalog() {
        List<MinecraftPalettePlanV2.Mapping> mappings = new ArrayList<>(MinecraftPalettePlanV2.MAPPING_COUNT);
        for (MaterialProfilePlanV2.SemanticMaterialClass kind :
                MaterialProfilePlanV2.SemanticMaterialClass.values()) {
            for (MaterialProfilePlanV2.SurfaceAspect aspect :
                    MaterialProfilePlanV2.SurfaceAspect.values()) {
                mappings.add(new MinecraftPalettePlanV2.Mapping(
                        kind, kind.classId(), kind.compactCode(), aspect, blockStateFor(kind, aspect)));
            }
        }
        return new MinecraftPalettePlanV2.Catalog(
                MinecraftPalettePlanV2.Catalog.VERSION,
                MinecraftPalettePlanV2.Catalog.ID,
                MinecraftPalettePlanV2.Catalog.CONTRACT_VERSION,
                mappings,
                new MinecraftPalettePlanV2.CatalogBudget(
                        MinecraftPalettePlanV2.CatalogBudget.VERSION,
                        MinecraftPalettePlanV2.MAPPING_COUNT,
                        32L * 1024L,
                        16));
    }

    /**
     * Built-in Minecraft 1.21.11 mapping table. Targets stay inside the environment export allowlist
     * and intentionally reuse the V2-2 coastal vocabulary so offline coastal tiles remain valid.
     */
    private static String blockStateFor(
            MaterialProfilePlanV2.SemanticMaterialClass kind,
            MaterialProfilePlanV2.SurfaceAspect aspect
    ) {
        return switch (kind) {
            case HOST_ROCK_EXPOSED -> "minecraft:stone";
            case HOST_ROCK_WET -> "minecraft:cobblestone";
            case SEDIMENT_EXPOSED -> switch (aspect) {
                case SURFACE -> "minecraft:sand";
                case CEILING -> "minecraft:dirt";
                case FLOOR -> "minecraft:gravel";
            };
            case SEDIMENT_WET -> "minecraft:mud";
            case SNOW_COVERED_ROCK -> switch (aspect) {
                case SURFACE -> "minecraft:snow_block";
                case CEILING, FLOOR -> "minecraft:stone";
            };
            case SNOW_COVERED_SEDIMENT -> switch (aspect) {
                case SURFACE -> "minecraft:snow_block";
                case CEILING -> "minecraft:dirt";
                case FLOOR -> "minecraft:gravel";
            };
        };
    }
}
