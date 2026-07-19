package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.CanonicalBlockStreamV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeSceneTestSupportV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.VolumeTileBlockResolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.environment.local.VolumeLocalEnvironmentPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.arch.NaturalArchPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cache.VolumeDenseAllocationDetectorV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cache.VolumeTileCacheAdmissionDecisionV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cache.VolumeTileCacheAdmissionV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cache.VolumeTileCacheExceptionV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cave.CaveNetworkPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.cave.lush.LushCavePlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.overhang.OverhangPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.seacave.SeaCavePlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.skyisland.SkyIslandGroupPlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.water.UndergroundLakePlanCompilerV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.waterfall.WaterfallVolumePlanCompilerV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeTileCachePlanV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-5-18 sparse-volume lifecycle, integration, determinism, and resource evidence. */
class VolumePhaseGateV2Test {
    private static final long MIB = 1024L * 1024L;

    /** The strict volume plan example portfolio; each entry reads and verifies its sealed checksum. */
    private static final Map<String, PlanReader> PORTFOLIO = portfolio();

    private static LandformV2DataCodec codec() {
        return new LandformV2DataCodec();
    }

    @Test
    void volumeLifecycleAndSparseVolumeCapabilityCatalogAreFinal() {
        for (Map.Entry<String, String> lifecycle : Map.of(
                "cave-network", CaveNetworkPlanCompilerV2.LIFECYCLE,
                "lush-cave", LushCavePlanCompilerV2.LIFECYCLE,
                "underground-lake", UndergroundLakePlanCompilerV2.LIFECYCLE,
                "sea-cave", SeaCavePlanCompilerV2.LIFECYCLE,
                "overhang", OverhangPlanCompilerV2.LIFECYCLE,
                "natural-arch", NaturalArchPlanCompilerV2.LIFECYCLE,
                "sky-island-group", SkyIslandGroupPlanCompilerV2.LIFECYCLE,
                "waterfall-volume", WaterfallVolumePlanCompilerV2.LIFECYCLE,
                "volume-local-environment", VolumeLocalEnvironmentPlanCompilerV2.LIFECYCLE).entrySet()) {
            assertEquals("SUPPORTED", lifecycle.getValue(), lifecycle.getKey());
        }

        BuiltInLandformModuleCatalogV2 catalog = new BuiltInLandformModuleCatalogV2();
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                catalog.hydrologyWaterfallModule().descriptor().lifecycleStatus());
        assertEquals(ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                catalog.requireFor(TerrainIntentV2.FeatureKind.WATERFALL).lifecycleStatus());

        // VOLUME_GUIDE intent kinds stay diagnostic-only: sparse volume is a plan-level offline
        // path and is not promoted into public intent dispatch by this gate.
        for (TerrainIntentV2.FeatureKind kind : List.of(
                TerrainIntentV2.FeatureKind.CAVE_NETWORK,
                TerrainIntentV2.FeatureKind.LUSH_CAVE,
                TerrainIntentV2.FeatureKind.OVERHANG,
                TerrainIntentV2.FeatureKind.SKY_ISLAND_GROUP)) {
            ModuleDescriptorV2 module = catalog.requireFor(kind);
            assertEquals(BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID, module.moduleId(), kind.name());
            assertEquals(ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL, module.lifecycleStatus(), kind.name());
        }

        assertEquals(List.of(
                        ReleaseArtifactCatalogV2.ENVIRONMENT_FIELDS,
                        ReleaseArtifactCatalogV2.HYDROLOGY_PLAN,
                        ReleaseArtifactCatalogV2.SPARSE_VOLUME,
                        ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT);
    }

    @Test
    void volumeExamplePortfolioIsStableAcrossOrderThreadsLocaleAndTimezone() throws Exception {
        Map<String, String> baseline = read(List.copyOf(PORTFOLIO.keySet()), 1);
        List<String> reversed = new ArrayList<>(PORTFOLIO.keySet());
        Collections.reverse(reversed);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(baseline, read(reversed, 4));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void integratedVolumeSceneComposesIdenticalWholeTileAndWindowedStreams() throws Exception {
        OfflineTilePlanV2 whole = VolumeSceneTestSupportV2.plan("gate-whole", 0, 0, 0, 0, 16, 16);
        List<OfflineTilePlanV2> tiles = List.of(
                VolumeSceneTestSupportV2.plan("gate-00-00", 0, 0, 0, 0, 8, 8),
                VolumeSceneTestSupportV2.plan("gate-01-00", 1, 0, 8, 0, 8, 8),
                VolumeSceneTestSupportV2.plan("gate-00-01", 0, 1, 0, 8, 8, 8),
                VolumeSceneTestSupportV2.plan("gate-01-01", 1, 1, 8, 8, 8, 8));

        TerrainBlockResolver wholeResolver =
                new VolumeTileBlockResolverV2(VolumeSceneTestSupportV2.scene(0, 0, 16, 16));
        TerrainBlockResolver tileDispatch = (x, y, z) -> {
            long owners = tiles.stream().filter(tile -> tile.contains(x, y, z)).count();
            assertEquals(1, owners, "tile dispatch must have exactly one owner");
            return wholeResolver.blockStateAt(x, y, z);
        };
        assertEquals(CanonicalBlockStreamV2.checksum(whole, wholeResolver, () -> false),
                CanonicalBlockStreamV2.checksum(whole, tileDispatch, () -> false));

        // Every tile stream is identical whether composed from the whole window or a
        // window-restricted volume query over the same global CSG plan (XYZ seam invariance).
        for (OfflineTilePlanV2 tile : tiles) {
            assertEquals(
                    CanonicalBlockStreamV2.checksum(tile, wholeResolver, () -> false),
                    CanonicalBlockStreamV2.checksum(tile, new VolumeTileBlockResolverV2(
                            VolumeSceneTestSupportV2.scene(
                                    tile.originX(), tile.originZ(), tile.width(), tile.length())), () -> false),
                    tile.tileId());
        }
    }

    @Test
    void denseAllocationDetectorAndTileCacheAdmissionRemainBounded() throws Exception {
        assertThrows(VolumeTileCacheExceptionV2.class,
                () -> VolumeDenseAllocationDetectorV2.rejectIfDenseWorldArray(1_000, 1_000, 512));
        assertThrows(VolumeTileCacheExceptionV2.class,
                () -> VolumeDenseAllocationDetectorV2.rejectIfDenseWorldArray(3_072, 3_072, 384));

        // Streaming a 1000-square (or larger) region keeps the working set at the sealed
        // chunk-cache admission bound, independent of the XZ extent.
        VolumeTileCachePlanV2 plan = codec().readVolumeTileCachePlan(
                Path.of("examples/v2/volume/volume-tile-cache-plan-v2.json"));
        VolumeTileCacheAdmissionDecisionV2 decision = VolumeTileCacheAdmissionV2.admit(plan);
        assertEquals(VolumeTileCacheAdmissionV2.ADMISSION_VERSION, decision.admissionVersion());
        assertTrue(decision.requiredWorkingBytes() <= plan.budget().maximumWorkingBytes());
        assertTrue(decision.requiredWorkingBytes() < MIB,
                "volume tile-cache admission must stay far below dense world allocations");
        long thousandSquareChunks = (1_000L / plan.kernel().chunkEdgeBlocks() + 1)
                * (1_000L / plan.kernel().chunkEdgeBlocks() + 1);
        assertTrue(thousandSquareChunks > plan.kernel().maximumRetainedChunks(),
                "the 1000-square region must exceed the retained cache, forcing bounded streaming");
    }

    private static Map<String, String> read(List<String> names, int threads) throws Exception {
        List<Callable<Map.Entry<String, String>>> tasks = names.stream()
                .<Callable<Map.Entry<String, String>>>map(name -> () ->
                        Map.entry(name, PORTFOLIO.get(name).canonicalChecksum()))
                .toList();
        Map<String, String> result = new LinkedHashMap<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (var future : executor.invokeAll(tasks)) {
                Map.Entry<String, String> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String name : PORTFOLIO.keySet()) {
            ordered.put(name, result.get(name));
        }
        return ordered;
    }

    private static Map<String, PlanReader> portfolio() {
        Map<String, PlanReader> readers = new LinkedHashMap<>();
        Path volume = Path.of("examples/v2/volume");
        readers.put("sdf-primitive", () ->
                codec().readVolumeSdfPrimitivePlan(volume.resolve("volume-sdf-primitive-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("csg", () ->
                codec().readVolumeCsgPlan(volume.resolve("volume-csg-plan-v2.json")).canonicalChecksum());
        readers.put("aabb-index", () ->
                codec().readVolumeAabbIndexPlan(volume.resolve("volume-aabb-index-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("tile-cache", () ->
                codec().readVolumeTileCachePlan(volume.resolve("volume-tile-cache-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("cave-network", () ->
                codec().readCaveNetworkPlan(volume.resolve("cave-network-plan-v2.json")).canonicalChecksum());
        readers.put("lush-cave", () ->
                codec().readLushCavePlan(volume.resolve("lush-cave-plan-v2.json")).canonicalChecksum());
        readers.put("underground-lake", () ->
                codec().readUndergroundLakePlan(volume.resolve("underground-lake-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("sea-cave", () ->
                codec().readSeaCavePlan(volume.resolve("sea-cave-plan-v2.json")).canonicalChecksum());
        readers.put("overhang", () ->
                codec().readOverhangPlan(volume.resolve("overhang-plan-v2.json")).canonicalChecksum());
        readers.put("natural-arch", () ->
                codec().readNaturalArchPlan(volume.resolve("natural-arch-plan-v2.json")).canonicalChecksum());
        readers.put("sky-island-group", () ->
                codec().readSkyIslandGroupPlan(volume.resolve("sky-island-group-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("waterfall-volume", () ->
                codec().readWaterfallVolumePlan(volume.resolve("waterfall-volume-plan-v2.json"))
                        .canonicalChecksum());
        readers.put("volume-local-environment", () ->
                codec().readVolumeLocalEnvironmentPlan(
                        Path.of("examples/v2/environment/volume-local-environment-plan-v2.json"))
                        .canonicalChecksum());
        return Collections.unmodifiableMap(readers);
    }

    @FunctionalInterface
    private interface PlanReader {
        String canonicalChecksum() throws Exception;
    }
}
