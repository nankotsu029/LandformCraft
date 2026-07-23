package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.SurfaceFoundationMergeCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationInputV2;

import java.util.Objects;

/**
 * Release-local 2.5D fields projected from a frozen foundation merge (V2-15-09).
 *
 * <p>Maps {@link SurfaceFoundationMergeCompilerV2} samples onto the coastal field IDs required by
 * the existing {@code surface-2_5d} validation／preview containers (ADR 0037). Coastal feature
 * overlays stay zero because foundation Blueprints carry no coastal plans. Elevation values that
 * fall outside request Y bounds are treated as datum-relative offsets above {@code waterLevel}
 * (PLAIN-style), while in-range values are absolute block Y (hill／bathymetry-style).</p>
 */
final class FoundationSurfaceFieldsV2 implements CoastalFieldSamplerV2 {
    static final int SCALE = SurfaceFoundationMergeCompilerV2.FIXED_SCALE;

    private final int[] landWater;
    private final int[] surfaceHeight;
    private final int[] ownerIndex;
    private final int width;
    private final int length;
    private final int coveredCells;

    private FoundationSurfaceFieldsV2(
            int[] landWater,
            int[] surfaceHeight,
            int[] ownerIndex,
            int width,
            int length,
            int coveredCells
    ) {
        this.landWater = landWater;
        this.surfaceHeight = surfaceHeight;
        this.ownerIndex = ownerIndex;
        this.width = width;
        this.length = length;
        this.coveredCells = coveredCells;
    }

    static FoundationSurfaceFieldsV2 fromMerge(
            SurfaceFoundationMergeCompilerV2 merge,
            int width,
            int length,
            int minY,
            int maxY,
            int waterLevel
    ) {
        Objects.requireNonNull(merge, "merge");
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("foundation surface dimensions must be positive");
        }
        int cells = Math.multiplyExact(width, length);
        int[] landWater = new int[cells];
        int[] surfaceHeight = new int[cells];
        int[] ownerIndex = new int[cells];
        int covered = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                SurfaceFoundationMergeCompilerV2.MergeSample sample = merge.sampleAt(x, z);
                int index = z * width + x;
                landWater[index] = isWaterClass(sample.surfaceClassCode()) ? 0 : 1;
                int elevationBlocks = Math.floorDiv(sample.elevationMillionths(), SCALE);
                int absoluteY = absoluteSurfaceY(elevationBlocks, minY, maxY, waterLevel);
                surfaceHeight[index] = Math.multiplyExact(absoluteY, SCALE);
                ownerIndex[index] = sample.ownerIndex();
                // V2-18-10: count cells that actually carry an effective foundation owner. The old
                // unconditional increment could never differ from the cell total, so the adapter's
                // "full owner coverage" check was true by construction; the count is now measured.
                if (sample.ownerIndex() > 0) {
                    covered++;
                }
            }
        }
        return new FoundationSurfaceFieldsV2(landWater, surfaceHeight, ownerIndex, width, length, covered);
    }

    private static int absoluteSurfaceY(int elevationBlocks, int minY, int maxY, int waterLevel) {
        int y = elevationBlocks;
        if (y < minY || y > maxY) {
            y = Math.addExact(waterLevel, elevationBlocks);
        }
        if (y <= minY) {
            y = minY + 1;
        }
        if (y > maxY) {
            y = maxY;
        }
        return y;
    }

    private static boolean isWaterClass(int surfaceClassCode) {
        return surfaceClassCode == SurfaceFoundationPlanV2.SurfaceClassCode.OCEAN.code()
                || surfaceClassCode == SurfaceFoundationPlanV2.SurfaceClassCode.SHELF.code()
                || surfaceClassCode == SurfaceFoundationPlanV2.SurfaceClassCode.SLOPE.code()
                || surfaceClassCode == SurfaceFoundationPlanV2.SurfaceClassCode.SUBMARINE_CANYON.code();
    }

    /** Cells of the request domain carrying an effective foundation owner (V2-18-10 gate subject). */
    int foundationOwnerCells() {
        return coveredCells;
    }

    int landWaterAt(int index) {
        return landWater[index];
    }

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
        if (globalX < 0 || globalX >= width || globalZ < 0 || globalZ >= length) {
            throw new IndexOutOfBoundsException("coordinate outside foundation surface fields");
        }
        int index = globalZ * width + globalX;
        if (CoastalTransitionModuleV2.LAND_WATER_FIELD_ID.equals(fieldId)) {
            return landWater[index];
        }
        if (CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID.equals(fieldId)) {
            return surfaceHeight[index];
        }
        if (CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID.equals(fieldId)) {
            return ownerIndex[index];
        }
        if (CoastalTransitionModuleV2.CONFLICT_FIELD_ID.equals(fieldId)
                || CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID.equals(fieldId)
                || CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID.equals(fieldId)
                || CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID.equals(fieldId)
                || CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID.equals(fieldId)) {
            return 0;
        }
        return CoastalValidationInputV2.NO_DATA;
    }

    /**
     * Canonical surface block mapping shared with coastal offline tiles: bedrock floor, grass／stone
     * land columns, gravel／water seabed, air above. No fluid simulation and no volume overlay.
     */
    TerrainBlockResolver resolver(int minY, int waterLevel) {
        return (x, y, z) -> {
            int index = z * width + x;
            if (y == minY) {
                return "minecraft:bedrock";
            }
            int surface = Math.floorDiv(surfaceHeight[index], SCALE);
            if (landWater[index] == 0) {
                if (y <= surface) {
                    return y == surface ? "minecraft:gravel" : "minecraft:stone";
                }
                if (y <= waterLevel) {
                    return "minecraft:water";
                }
                return "minecraft:air";
            }
            if (y > surface) {
                return "minecraft:air";
            }
            return y == surface ? "minecraft:grass_block"
                    : y >= surface - 2 ? "minecraft:dirt" : "minecraft:stone";
        };
    }
}
