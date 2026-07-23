package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionCompositorV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalValidationInputV2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Release-local coastal descriptor fields sampled once from a frozen Blueprint (V2-12-02).
 *
 * <p>Row-major, integer-only, and independent of module registration order: every value comes from
 * {@link CoastalTransitionCompositorV2} and the four coastal generators, never from generator
 * private state. HARD land-water cells are re-composed through {@link HardLandWaterSourceV2} and
 * the pipeline fails closed if a HARD cell is not preserved.</p>
 */
final class CoastalSurfaceFieldsV2 implements CoastalFieldSamplerV2 {
    static final int SCALE = 1_000_000;

    private static final List<String> FIELD_IDS = List.of(
            CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID,
            CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID,
            CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID,
            CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID,
            CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID,
            CoastalTransitionModuleV2.LAND_WATER_FIELD_ID,
            CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID,
            CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID,
            CoastalTransitionModuleV2.CONFLICT_FIELD_ID);

    private final Map<String, int[]> values;
    private final int[] landWater;
    private final int[] surfaceHeight;
    private final int width;
    private final int length;
    private final int hardProtectedCells;
    /**
     * Effective foundation owner index per cell in the {@code foundation.owner-index} namespace, or
     * {@code null} on the legacy baseline path where no cell has a foundation owner (V2-18-09).
     * V2-19-07 made it per-cell: with the producer tier wired, a cell is owned either by the
     * mask-derived background or by the producer whose footprint replaced it (ADR 0038 D1-3/D1-4).
     */
    private final int[] foundationOwnerIndex;

    private CoastalSurfaceFieldsV2(
            Map<String, int[]> values,
            int width,
            int length,
            int hardProtectedCells,
            int[] foundationOwnerIndex
    ) {
        this.values = Map.copyOf(values);
        this.landWater = values.get(CoastalTransitionModuleV2.LAND_WATER_FIELD_ID);
        this.surfaceHeight = values.get(CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID);
        this.width = width;
        this.length = length;
        this.hardProtectedCells = hardProtectedCells;
        this.foundationOwnerIndex = foundationOwnerIndex;
    }

    /**
     * Estimated resident bytes for the dense descriptor working set: the declared fields plus the
     * per-cell scratch the two generation paths hold (desired land-water / desired height on the
     * legacy path, the foundation owner index since V2-19-07).
     */
    static long estimatedResidentBytes(int width, int length) {
        return Math.multiplyExact(
                Math.multiplyExact((long) width, (long) length),
                (long) (FIELD_IDS.size() + 3) * Integer.BYTES);
    }

    static CoastalSurfaceFieldsV2 generate(
            CoastalGeneratorRuntimeV2 runtime,
            int width,
            int length,
            SurfaceBaselineV2 baseline,
            CancellationToken cancellationToken
    ) {
        int cells = Math.multiplyExact(width, length);
        Map<String, int[]> fields = new LinkedHashMap<>();
        for (String id : FIELD_IDS) {
            fields.put(id, new int[cells]);
        }
        boolean[] active = new boolean[cells];
        int[] desiredLandWater = new int[cells];
        int[] desiredHeight = new int[cells];
        int baselineLandWater = baseline.classification() == HardLandWaterSourceV2.Classification.LAND ? 1 : 0;
        for (int z = 0; z < length; z++) {
            cancellationToken.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                CoastalTransitionCompositorV2.CompositionSample sample =
                        runtime.compositor().sampleAt(x, z, HardLandWaterSourceV2.NONE);
                active[index] = sample.active();
                desiredLandWater[index] = sample.active() ? sample.landWater() : baselineLandWater;
                desiredHeight[index] = sample.active()
                        ? sample.surfaceHeightMillionths()
                        : Math.multiplyExact(baseline.surfaceYFor(baselineLandWater), SCALE);
            }
        }

        HardLandWaterSourceV2 hard = (x, z) -> desiredLandWater[z * width + x] == 1
                ? HardLandWaterSourceV2.Classification.LAND
                : HardLandWaterSourceV2.Classification.WATER;
        int protectedCells = 0;
        for (int z = 0; z < length; z++) {
            cancellationToken.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                SandyBeachGeneratorV2.BeachSample beach =
                        runtime.beach().sampleAt(x, z, HardLandWaterSourceV2.NONE);
                HarborBasinGeneratorV2.HarborSample harbor =
                        runtime.harbor().sampleAt(x, z, HardLandWaterSourceV2.NONE);
                BreakwaterHarborGeneratorV2.BreakwaterSample breakwater = runtime.breakwater().sampleAt(x, z);
                RockyCapeGeneratorV2.CapeSample cape =
                        runtime.cape().sampleAt(x, z, HardLandWaterSourceV2.NONE);
                put(fields, CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID, index,
                        beach.localWidthMillionths());
                put(fields, CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID, index, beach.band().rawValue());
                put(fields, CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID, index, harbor.region().rawValue());
                put(fields, CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID, index, harbor.water());
                put(fields, CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID, index, harbor.depthMillionths());
                put(fields, CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID, index,
                        breakwater.region().rawValue());
                put(fields, CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID, index, cape.region().rawValue());
                put(fields, CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID, index, cape.rockExposure());
                put(fields, CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID, index,
                        cape.descriptorIndex());

                if (active[index]) {
                    CoastalTransitionCompositorV2.CompositionSample composed =
                            runtime.compositor().sampleAt(x, z, hard);
                    if (!composed.hardProtected() || composed.landWater() != desiredLandWater[index]) {
                        throw new IllegalStateException(
                                "coastal export did not preserve the HARD land-water cell at " + x + ',' + z);
                    }
                    protectedCells++;
                    put(fields, CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, index, composed.landWater());
                    put(fields, CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, index,
                            composed.surfaceHeightMillionths());
                    put(fields, CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, index, composed.ownerIndex());
                    put(fields, CoastalTransitionModuleV2.CONFLICT_FIELD_ID, index, composed.conflict());
                } else {
                    put(fields, CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, index, desiredLandWater[index]);
                    put(fields, CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, index, desiredHeight[index]);
                    put(fields, CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, index, 0);
                    put(fields, CoastalTransitionModuleV2.CONFLICT_FIELD_ID, index, 0);
                }
            }
        }
        return new CoastalSurfaceFieldsV2(fields, width, length, protectedCells, null);
    }

    /**
     * Foundation-path generation (V2-18-09): the coastal four compose as surface modifiers over the
     * resolved macro foundation. Feature-unowned cells take the background owner's medium and
     * provisional elevation from the explicit foundation input — the mask plus, since V2-19-06, the
     * optional {@code HEIGHT_GUIDE} — never a {@code SurfaceBaselineV2} fallback, and the mask is
     * injected into the transition compositor as the HARD land-water source, so every mask-specified
     * cell is either honored or rejected fail-closed.
     */
    static CoastalSurfaceFieldsV2 generate(
            CoastalGeneratorRuntimeV2 runtime,
            int width,
            int length,
            MacroFoundationV2 foundation,
            CancellationToken cancellationToken
    ) {
        if (foundation.width() != width || foundation.length() != length) {
            throw new IllegalStateException("macro foundation dimensions do not match the export bounds");
        }
        int cells = Math.multiplyExact(width, length);
        Map<String, int[]> fields = new LinkedHashMap<>();
        for (String id : FIELD_IDS) {
            fields.put(id, new int[cells]);
        }
        boolean[] active = new boolean[cells];
        // V2-19-07: resolving the effective foundation owner of every cell up front is also where the
        // ADR 0038 D7-1 kernel invariants run — undeclared producer overlap, a producer medium the
        // HARD mask contradicts, an out-of-extent producer elevation — so a contradictory foundation
        // input fails closed before a single field value is written.
        int[] foundationOwner = new int[cells];
        for (int z = 0; z < length; z++) {
            cancellationToken.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                active[index] = runtime.compositor()
                        .sampleAt(x, z, HardLandWaterSourceV2.NONE).active();
                foundationOwner[index] = foundation.effectiveOwnerIndexAt(x, z);
            }
        }

        HardLandWaterSourceV2 hard = foundation.hardLandWaterSource();
        int protectedCells = 0;
        for (int z = 0; z < length; z++) {
            cancellationToken.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                SandyBeachGeneratorV2.BeachSample beach =
                        runtime.beach().sampleAt(x, z, HardLandWaterSourceV2.NONE);
                HarborBasinGeneratorV2.HarborSample harbor =
                        runtime.harbor().sampleAt(x, z, HardLandWaterSourceV2.NONE);
                BreakwaterHarborGeneratorV2.BreakwaterSample breakwater = runtime.breakwater().sampleAt(x, z);
                RockyCapeGeneratorV2.CapeSample cape =
                        runtime.cape().sampleAt(x, z, HardLandWaterSourceV2.NONE);
                put(fields, CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID, index,
                        beach.localWidthMillionths());
                put(fields, CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID, index, beach.band().rawValue());
                put(fields, CoastalFoundationModuleV2.HARBOR_REGION_FIELD_ID, index, harbor.region().rawValue());
                put(fields, CoastalFoundationModuleV2.HARBOR_WATER_FIELD_ID, index, harbor.water());
                put(fields, CoastalFoundationModuleV2.HARBOR_DEPTH_FIELD_ID, index, harbor.depthMillionths());
                put(fields, CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID, index,
                        breakwater.region().rawValue());
                put(fields, CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID, index, cape.region().rawValue());
                put(fields, CoastalFoundationModuleV2.CAPE_ROCK_EXPOSURE_FIELD_ID, index, cape.rockExposure());
                put(fields, CoastalFoundationModuleV2.CAPE_DESCRIPTOR_INDEX_FIELD_ID, index,
                        cape.descriptorIndex());

                HardLandWaterSourceV2.Classification maskClass = hard.classificationAt(x, z);
                if (active[index]) {
                    CoastalTransitionCompositorV2.CompositionSample composed =
                            runtime.compositor().sampleAt(x, z, hard);
                    if (maskClass != HardLandWaterSourceV2.Classification.UNSPECIFIED) {
                        if (!composed.hardProtected() || composed.landWater() != maskClass.rawValue()) {
                            throw new IllegalStateException(
                                    "coastal export did not preserve the HARD land-water cell at " + x + ',' + z);
                        }
                        protectedCells++;
                    }
                    // V2-19-06: a modifier owns the height of the cell it claims (ADR 0038 D5-3), so a
                    // HARD guide specifying that same cell is a conflict between two declared sources
                    // and fails closed here rather than being silently overwritten.
                    foundation.requireModifierHonorsHeightGuide(x, z, composed.surfaceHeightMillionths());
                    put(fields, CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, index, composed.landWater());
                    put(fields, CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, index,
                            composed.surfaceHeightMillionths());
                    put(fields, CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, index, composed.ownerIndex());
                    put(fields, CoastalTransitionModuleV2.CONFLICT_FIELD_ID, index, composed.conflict());
                } else {
                    if (maskClass != HardLandWaterSourceV2.Classification.UNSPECIFIED) {
                        protectedCells++;
                    }
                    put(fields, CoastalTransitionModuleV2.LAND_WATER_FIELD_ID, index,
                            foundation.mediumAt(x, z));
                    put(fields, CoastalTransitionModuleV2.SURFACE_HEIGHT_FIELD_ID, index,
                            foundation.elevationMillionthsAt(x, z));
                    put(fields, CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, index, 0);
                    put(fields, CoastalTransitionModuleV2.CONFLICT_FIELD_ID, index, 0);
                }
            }
        }
        return new CoastalSurfaceFieldsV2(fields, width, length, protectedCells, foundationOwner);
    }

    private static void put(Map<String, int[]> fields, String id, int index, int value) {
        fields.get(id)[index] = value;
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
        int[] field = values.get(fieldId);
        if (field == null && BuiltInLandformModuleCatalogV2.CONTRACT_FIELD_ID.equals(fieldId)) {
            // The generic land-water contract field (V2-18-04 EDGE evaluator reads this id) resolves to
            // the composed land-water field: at export time they carry the same land(1)/water(0) values.
            field = landWater;
        }
        if (field == null && SurfaceFoundationPlanV2.OWNER_INDEX_FIELD_ID.equals(fieldId)) {
            // V2-18-09: on the foundation path every cell carries an effective foundation owner —
            // the background candidate, or since V2-19-07 the producer that replaced it inside its
            // footprint; 0 on the legacy baseline path, where no cell has a foundation owner.
            // Surface modifiers never own foundation cells (ADR 0038 D5-3).
            if (globalX < 0 || globalX >= width || globalZ < 0 || globalZ >= length) {
                throw new IndexOutOfBoundsException("coordinate outside release-local coastal fields");
            }
            return foundationOwnerIndex == null ? 0 : foundationOwnerIndex[globalZ * width + globalX];
        }
        if (field == null) return CoastalValidationInputV2.NO_DATA;
        if (globalX < 0 || globalX >= width || globalZ < 0 || globalZ >= length) {
            throw new IndexOutOfBoundsException("coordinate outside release-local coastal fields");
        }
        return field[globalZ * width + globalX];
    }

    int landWaterAt(int index) {
        return landWater[index];
    }

    int hardProtectedCells() {
        return hardProtectedCells;
    }

    /**
     * Canonical V2-2 surface block mapping: bedrock floor, semantic coastal surfaces, water up to
     * the request water level, air above. It owns no fluid simulation and no volume overlay.
     */
    TerrainBlockResolver resolver(int minY, int waterLevel) {
        int[] beach = values.get(CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID);
        int[] breakwater = values.get(CoastalFoundationModuleV2.BREAKWATER_REGION_FIELD_ID);
        int[] cape = values.get(CoastalFoundationModuleV2.CAPE_REGION_FIELD_ID);
        return (x, y, z) -> {
            int index = z * width + x;
            if (y == minY) return "minecraft:bedrock";
            int surface = Math.floorDiv(surfaceHeight[index], SCALE);
            int breakwaterRegion = breakwater[index];
            boolean submergedBreakwater = breakwaterRegion == 2 || breakwaterRegion == 3;
            if (landWater[index] == 0 || submergedBreakwater) {
                if (y <= surface) {
                    if (breakwaterRegion != 0 && y >= surface - 1) return "minecraft:stone_bricks";
                    return y == surface ? "minecraft:gravel" : "minecraft:stone";
                }
                if (y <= waterLevel) return "minecraft:water";
                return "minecraft:air";
            }
            if (y > surface) return "minecraft:air";
            if (breakwaterRegion != 0) {
                return y >= surface - 1 ? "minecraft:stone_bricks" : "minecraft:cobblestone";
            }
            if (beach[index] == 2 || beach[index] == 3) {
                return y == surface ? "minecraft:sand"
                        : y >= surface - 2 ? "minecraft:sandstone" : "minecraft:stone";
            }
            if (cape[index] != 0) {
                return y == surface ? "minecraft:cobblestone" : "minecraft:stone";
            }
            return y == surface ? "minecraft:grass_block"
                    : y >= surface - 2 ? "minecraft:dirt" : "minecraft:stone";
        };
    }
}
