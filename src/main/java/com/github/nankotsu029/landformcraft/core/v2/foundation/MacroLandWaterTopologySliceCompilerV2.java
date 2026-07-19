package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationMacroLandWaterTopologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates V2-9-12 macro land-water topology compile, validation, preview, and freeze handoff.
 */
public final class MacroLandWaterTopologySliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final MacroLandWaterTopologyPlanCompilerV2 planCompiler = new MacroLandWaterTopologyPlanCompilerV2();

    public MacroLandWaterTopologySliceV2 compile(MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2 input) {
        Objects.requireNonNull(input, "input");
        MacroLandWaterTopologyPlanV2 plan = codec.sealMacroLandWaterTopologyPlan(planCompiler.compile(input));

        String forwardChecksum = plan.canonicalChecksum();
        String reverseTileChecksum = compileAssembledFromTiles(input, true).canonicalChecksum();
        String forwardTileChecksum = compileAssembledFromTiles(input, false).canonicalChecksum();
        boolean wholeTileOk = forwardChecksum.equals(reverseTileChecksum)
                && forwardChecksum.equals(forwardTileChecksum);
        boolean orderIndependent = wholeTileOk;
        boolean threadIndependent = compileAcrossThreads(input, forwardChecksum);

        FoundationMacroLandWaterTopologyValidationArtifactV2 validation =
                codec.sealFoundationMacroLandWaterTopologyValidationArtifact(
                        new FoundationMacroLandWaterTopologyValidationArtifactV2(
                                FoundationMacroLandWaterTopologyValidationArtifactV2.VERSION,
                                FoundationMacroLandWaterTopologyValidationArtifactV2.CONTRACT_VERSION,
                                plan.topologyId(),
                                new FoundationMacroLandWaterTopologyValidationArtifactV2.Metrics(
                                        !plan.adjacencies().isEmpty() || plan.regions().size() == 1,
                                        true,
                                        true,
                                        true,
                                        plan.estimatedGraphWorkUnits()
                                                <= MacroLandWaterTopologyPlanV2.MAXIMUM_GRAPH_WORK_UNITS,
                                        plan.estimatedRasterCells()
                                                <= MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS,
                                        wholeTileOk,
                                        orderIndependent,
                                        threadIndependent,
                                        wholeTileOk && orderIndependent && threadIndependent),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> layers = new ArrayList<>();
        layers.add(new FoundationPreviewIndexV2.Layer(
                "land-water-mask",
                MacroLandWaterTopologyPlanV2.LAND_WATER_MASK_FIELD_ID,
                plan.geometryChecksum()));
        layers.add(new FoundationPreviewIndexV2.Layer(
                "region-index",
                MacroLandWaterTopologyPlanV2.REGION_INDEX_FIELD_ID,
                plan.canonicalChecksum()));
        if (!plan.zoneLabelFieldId().isEmpty()) {
            layers.add(new FoundationPreviewIndexV2.Layer(
                    "zone-label",
                    MacroLandWaterTopologyPlanV2.ZONE_LABEL_FIELD_ID,
                    plan.geometryChecksum()));
        }
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        layers,
                        plan.width(),
                        plan.length(),
                        "0".repeat(64)));

        return new MacroLandWaterTopologySliceV2(plan, validation, preview, plan.canonicalChecksum());
    }

    private MacroLandWaterTopologyPlanV2 compileAssembledFromTiles(
            MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2 input,
            boolean reverse
    ) {
        int width = input.width();
        int length = input.length();
        int tileSize = 32;
        TilePlanV2 tilePlan = new TilePlanV2(width, length, tileSize, 0);
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < tilePlan.tileCount(); index++) {
            indices.add(index);
        }
        if (reverse) {
            indices = indices.reversed();
        }
        byte[] assembledMask = new byte[width * length];
        int[] assembledZones = input.zoneLabels() == null ? null : new int[width * length];
        for (int index : indices) {
            TilePlanV2.TileV2 tile = tilePlan.tileByIndex(index);
            for (int z = tile.coreMinZ(); z < tile.coreMinZ() + tile.coreLength(); z++) {
                for (int x = tile.coreMinX(); x < tile.coreMinX() + tile.coreWidth(); x++) {
                    int cell = z * width + x;
                    assembledMask[cell] = input.landWaterMask()[cell];
                    if (assembledZones != null) {
                        assembledZones[cell] = input.zoneLabels()[cell];
                    }
                }
            }
        }
        MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2 assembled =
                new MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2(
                        input.topologyId(),
                        width,
                        length,
                        assembledMask,
                        assembledZones,
                        input.labelKinds(),
                        input.minimumIsthmusWidthBlocks(),
                        input.minimumStraitWidthBlocks(),
                        input.supportRadiusXZ());
        return codec.sealMacroLandWaterTopologyPlan(planCompiler.compile(assembled));
    }

    private boolean compileAcrossThreads(
            MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2 input,
            String expectedChecksum
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() ->
                        codec.sealMacroLandWaterTopologyPlan(planCompiler.compile(input)).canonicalChecksum()));
            }
            for (Future<String> future : futures) {
                if (!expectedChecksum.equals(future.get())) {
                    return false;
                }
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException ex) {
            throw new FoundationSliceException("v2.topology-thread-compile",
                    "threaded topology compile failed", ex);
        } finally {
            executor.shutdownNow();
        }
    }

    public record MacroLandWaterTopologySliceV2(
            MacroLandWaterTopologyPlanV2 topology,
            FoundationMacroLandWaterTopologyValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String freezeChecksum
    ) {
    }
}
