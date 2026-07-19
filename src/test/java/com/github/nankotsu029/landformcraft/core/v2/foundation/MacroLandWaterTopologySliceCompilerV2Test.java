package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2.MacroRegionKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroLandWaterTopologySliceCompilerV2Test {
    private static final int WIDTH = 32;
    private static final int LENGTH = 32;
    private static final int LABEL_ISTHMUS = 1;
    private static final int LABEL_STRAIT = 2;
    private static final int LABEL_BAY = 3;
    private static final int LABEL_BASIN = 4;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final MacroLandWaterTopologySliceCompilerV2 sliceCompiler =
            new MacroLandWaterTopologySliceCompilerV2();
    private final MacroLandWaterTopologyPlanCompilerV2 planCompiler =
            new MacroLandWaterTopologyPlanCompilerV2();

    @Test
    void positiveManualMaskCompilesDeterministicallyAndFreezes() throws Exception {
        var input = positiveInput(2, 2);
        var slice = sliceCompiler.compile(input);

        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().orderIndependent());
        assertTrue(slice.validation().metrics().threadIndependent());
        assertTrue(slice.validation().metrics().freezeReady());
        assertTrue(slice.validation().metrics().graphBudgetOk());
        assertTrue(slice.validation().metrics().rasterBudgetOk());
        assertEquals(slice.topology().canonicalChecksum(), slice.freezeChecksum());

        Set<MacroRegionKind> kinds = EnumSet.noneOf(MacroRegionKind.class);
        slice.topology().regions().forEach(region -> kinds.add(region.kind()));
        assertTrue(kinds.contains(MacroRegionKind.ISTHMUS));
        assertTrue(kinds.contains(MacroRegionKind.STRAIT));
        assertTrue(kinds.contains(MacroRegionKind.BAY));
        assertTrue(kinds.contains(MacroRegionKind.ENCLOSED_BASIN));
        assertFalse(slice.topology().containments().isEmpty());
        assertFalse(slice.topology().zoneBindings().isEmpty());

        Path planFile = Path.of("examples/v2/foundation/macro-land-water-topology-plan-v2.json");
        codec.writeMacroLandWaterTopologyPlan(planFile, slice.topology());
        assertEquals(slice.topology(), codec.readMacroLandWaterTopologyPlan(planFile));
    }

    @Test
    void sealedPlanStrictRoundTrip(@TempDir Path temp) throws Exception {
        var slice = sliceCompiler.compile(positiveInput(2, 2));
        Path path = temp.resolve("topology.json");
        codec.writeMacroLandWaterTopologyPlan(path, slice.topology());
        assertEquals(slice.topology(), codec.readMacroLandWaterTopologyPlan(path));

        Path validationPath = temp.resolve("validation.json");
        codec.writeFoundationMacroLandWaterTopologyValidationArtifact(validationPath, slice.validation());
        assertEquals(slice.validation(),
                codec.readFoundationMacroLandWaterTopologyValidationArtifact(validationPath));
    }

    @Test
    void rejectsCollapsedIsthmus() {
        var input = positiveInput(4, 2);
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> planCompiler.compile(input));
        assertEquals(MacroLandWaterTopologyPlanCompilerV2.RULE_COLLAPSED_ISTHMUS, ex.ruleId());
    }

    @Test
    void rejectsDisconnectedStrait() {
        byte[] mask = landFill();
        int[] zones = new int[WIDTH * LENGTH];
        // Dead-end water pocket labeled STRAIT surrounded by land — one water body only.
        fillRect(mask, zones, 10, 10, 14, 14, (byte) 1, LABEL_STRAIT);
        Map<Integer, MacroRegionKind> kinds = Map.of(LABEL_STRAIT, MacroRegionKind.STRAIT);
        var input = new MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2(
                "disconnected-strait", WIDTH, LENGTH, mask, zones, kinds, 2, 2, 0);
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> planCompiler.compile(input));
        assertEquals(MacroLandWaterTopologyPlanCompilerV2.RULE_DISCONNECTED_STRAIT, ex.ruleId());
    }

    @Test
    void rejectsNestedBasinWithoutContainment() {
        byte[] mask = waterFill();
        int[] zones = new int[WIDTH * LENGTH];
        // Open water labeled ENCLOSED_BASIN touches border → not contained.
        fillRect(mask, zones, 0, 0, 5, 5, (byte) 1, LABEL_BASIN);
        Map<Integer, MacroRegionKind> kinds = Map.of(LABEL_BASIN, MacroRegionKind.ENCLOSED_BASIN);
        var input = new MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2(
                "open-basin", WIDTH, LENGTH, mask, zones, kinds, 2, 2, 0);
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> planCompiler.compile(input));
        assertEquals(MacroLandWaterTopologyPlanCompilerV2.RULE_NESTED_BASIN, ex.ruleId());
    }

    @Test
    void rejectsAmbiguousBoundaryMediumConflict() {
        byte[] mask = landFill();
        int[] zones = new int[WIDTH * LENGTH];
        fillRect(mask, zones, 4, 4, 8, 8, (byte) 0, LABEL_STRAIT); // land cells with water kind
        Map<Integer, MacroRegionKind> kinds = Map.of(LABEL_STRAIT, MacroRegionKind.STRAIT);
        var input = new MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2(
                "ambiguous", WIDTH, LENGTH, mask, zones, kinds, 2, 2, 0);
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> planCompiler.compile(input));
        assertEquals(MacroLandWaterTopologyPlanCompilerV2.RULE_AMBIGUOUS_BOUNDARY, ex.ruleId());
    }

    @Test
    void rejectsRasterBudget() {
        byte[] mask = new byte[] {0};
        var input = new MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2(
                "tiny", 1, 1, mask, null, Map.of(), 2, 2, 0);
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> planCompiler.compile(input));
        assertEquals(MacroLandWaterTopologyPlanCompilerV2.RULE_RASTER_BUDGET, ex.ruleId());
    }

    @Test
    void rejectsUnknownFutureContractOnRecord() {
        assertThrows(IllegalArgumentException.class, () -> new MacroLandWaterTopologyPlanV2(
                1,
                "topo",
                "macro-land-water-topology-contract-v9",
                32,
                32,
                MacroLandWaterTopologyPlanV2.LAND_WATER_MASK_FIELD_ID,
                MacroLandWaterTopologyPlanV2.ZONE_LABEL_FIELD_ID,
                MacroLandWaterTopologyPlanV2.REGION_INDEX_FIELD_ID,
                java.util.List.of(new MacroLandWaterTopologyPlanV2.Region(
                        "region.0",
                        MacroRegionKind.UNLABELED_LAND,
                        MacroLandWaterTopologyPlanV2.Medium.LAND,
                        1, 0, 0, 0, 0, 0, 0, 1)),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                2,
                2,
                0,
                1,
                1024,
                "0".repeat(64),
                "0".repeat(64)));
    }

    private static MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2 positiveInput(
            int minIsthmus,
            int minStrait
    ) {
        byte[] mask = new byte[WIDTH * LENGTH];
        int[] zones = new int[WIDTH * LENGTH];
        // North and south oceans.
        fillRect(mask, zones, 0, 0, WIDTH - 1, 9, (byte) 1, 0);
        fillRect(mask, zones, 0, 22, WIDTH - 1, LENGTH - 1, (byte) 1, 0);
        // Land band.
        fillRect(mask, zones, 0, 10, WIDTH - 1, 21, (byte) 0, 0);
        // Bay indent from the north ocean into west land.
        fillRect(mask, zones, 3, 10, 7, 12, (byte) 1, LABEL_BAY);
        // Isthmus bridging west/east land (width 2).
        fillRect(mask, zones, 11, 10, 12, 21, (byte) 0, LABEL_ISTHMUS);
        // Strait bridging north/south water (width 2).
        fillRect(mask, zones, 21, 10, 22, 21, (byte) 1, LABEL_STRAIT);
        // Enclosed basin lake inside west land.
        fillRect(mask, zones, 3, 15, 5, 17, (byte) 1, LABEL_BASIN);

        Map<Integer, MacroRegionKind> kinds = new HashMap<>();
        kinds.put(LABEL_ISTHMUS, MacroRegionKind.ISTHMUS);
        kinds.put(LABEL_STRAIT, MacroRegionKind.STRAIT);
        kinds.put(LABEL_BAY, MacroRegionKind.BAY);
        kinds.put(LABEL_BASIN, MacroRegionKind.ENCLOSED_BASIN);
        return new MacroLandWaterTopologyPlanCompilerV2.ManualTopologyInputV2(
                "macro-coast-topology",
                WIDTH,
                LENGTH,
                mask,
                zones,
                kinds,
                minIsthmus,
                minStrait,
                0);
    }

    private static byte[] landFill() {
        byte[] mask = new byte[WIDTH * LENGTH];
        java.util.Arrays.fill(mask, (byte) 0);
        return mask;
    }

    private static byte[] waterFill() {
        byte[] mask = new byte[WIDTH * LENGTH];
        java.util.Arrays.fill(mask, (byte) 1);
        return mask;
    }

    private static void fillRect(
            byte[] mask,
            int[] zones,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            byte medium,
            int label
    ) {
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int index = z * WIDTH + x;
                mask[index] = medium;
                zones[index] = label;
            }
        }
    }
}
