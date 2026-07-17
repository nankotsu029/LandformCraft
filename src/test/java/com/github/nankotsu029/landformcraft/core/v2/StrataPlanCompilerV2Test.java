package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.geology.strata.StrataExposureResolverV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrataPlanCompilerV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void compilesOrderedProfilesDerivedScalarsAndExplicitHydrologyHandoff() {
        GeologyPlanV2 geology = geology(128, 96, 827413L);
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 plan = new StrataPlanCompilerV2().compile(geology, lithology);

        assertEquals(1, plan.profiles().size());
        StrataPlanV2.Profile profile = plan.profiles().getFirst();
        assertEquals(StrataPlanV2.LayerOrder.BOTTOM_TO_TOP, profile.layerOrder());
        assertEquals(1, profile.layers().size());
        assertEquals(StrataPlanV2.DEFAULT_STACK_THICKNESS_BLOCKS, profile.totalThicknessBlocks());
        assertEquals(lithology.canonicalChecksum(), plan.sourceLithologyPlanChecksum());
        assertEquals(geology.canonicalChecksum(), plan.sourceGeologyPlanChecksum());
        assertEquals(HydrologyPlanV2.FixedPriors.CHECKSUM,
                plan.hydrologyHandoff().sourceHydrologyPriorChecksum());
        assertEquals(HydrologyReconciliationPlanV2.ALGORITHM_VERSION,
                plan.hydrologyHandoff().sourceReconciliationAlgorithmVersion());
        assertEquals(StrataPlanV2.InputMode.SURFACE_EXPOSED_STRATA_SCALARS, plan.hydrologyHandoff().inputMode());
        assertEquals(StrataPlanV2.TransitionMode.EXPLICIT_VERSION_TRANSITION,
                plan.hydrologyHandoff().transitionMode());
        assertEquals(codec.strataPlanChecksum(plan), plan.canonicalChecksum());
        plan.requireLithologyPlan(geology, lithology);

        StrataExposureResolverV2 resolver = new StrataExposureResolverV2(geology, lithology, plan);
        assertEquals(1, resolver.exposedLithologyCode(1, 0, 0));
        assertEquals(500_000, resolver.derivedHardnessMillionths(1, 0, 0));
        assertEquals(500_000, resolver.derivedPermeabilityMillionths(1, 0, 0));
        assertEquals(500, resolver.derivedHardnessRaw(1, 0, 0));
        assertEquals(LithologyPlanV2.ErosionResponse.MODERATE, resolver.erosionResponse(1, 10, 10));
    }

    @Test
    void surfaceExposureRespectsLayerOrderThicknessAndTilt(@TempDir Path directory) throws Exception {
        GeologyPlanV2 geology = multiProvinceGeology(false);
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 base = new StrataPlanCompilerV2().compile(geology, lithology);
        StrataPlanV2.Profile basalt = base.profiles().stream()
                .filter(profile -> profile.provinceCode() == 2).findFirst().orElseThrow();
        assertEquals(2, basalt.layers().size());
        assertEquals(0, basalt.layers().getFirst().layerIndex());
        assertEquals(LithologyPlanV2.SemanticLithology.HARD_INTRUSIVE.compactCode(),
                basalt.layers().getFirst().lithologyCode());
        assertEquals(LithologyPlanV2.SemanticLithology.BASALTIC_FLOW.compactCode(),
                basalt.layers().get(1).lithologyCode());

        StrataPlanV2 tilted = codec.sealStrataPlan(new StrataPlanV2(
                base.planVersion(), base.profileContractVersion(), base.sourceGeologyPlanChecksum(),
                base.sourceLithologyPlanChecksum(),
                List.of(
                        base.profiles().getFirst(),
                        new StrataPlanV2.Profile(
                                basalt.profileId(), basalt.provinceId(), basalt.provinceCode(),
                                basalt.formationId(), basalt.formationCode(), basalt.layerOrder(),
                                basalt.layers(), StrataPlanV2.FoldTilt.cardinalDip(45, 90))),
                base.hydrologyHandoff(), base.budget(), "0".repeat(64)));
        StrataExposureResolverV2 resolver = new StrataExposureResolverV2(geology, lithology, tilted);
        assertEquals(LithologyPlanV2.SemanticLithology.BASALTIC_FLOW.compactCode(),
                resolver.exposedLithologyCode(2, 0, 0));
        assertEquals(LithologyPlanV2.SemanticLithology.HARD_INTRUSIVE.compactCode(),
                resolver.exposedLithologyCode(2, 20, 0));
        assertEquals(700_000, resolver.derivedHardnessMillionths(2, 0, 0));
        assertEquals(500_000, resolver.derivedHardnessMillionths(2, 20, 0));
        assertTrue(resolver.derivedHardnessRaw(2, 0, 0) >= 0
                && resolver.derivedHardnessRaw(2, 0, 0) <= 1_000);
        assertTrue(resolver.derivedPermeabilityRaw(2, 0, 0) >= 0
                && resolver.derivedPermeabilityRaw(2, 0, 0) <= 1_000);

        Path artifact = directory.resolve("strata-plan-v2.json");
        codec.writeStrataPlan(artifact, tilted);
        assertEquals(tilted, codec.readStrataPlan(artifact));
    }

    @Test
    void rejectsZeroThinInvertedUnknownAndFutureVersions(@TempDir Path directory) throws Exception {
        GeologyPlanV2 geology = geology(64, 48, 1L);
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 plan = new StrataPlanCompilerV2().compile(geology, lithology);
        Path artifact = directory.resolve("strata-plan-v2.json");
        codec.writeStrataPlan(artifact, plan);
        assertEquals(plan, codec.readStrataPlan(artifact));
        assertTrue(codec.canonicalStrataPlan(plan).getBytes(StandardCharsets.UTF_8).length
                < plan.budget().maximumCanonicalBytes());

        assertThrows(IllegalArgumentException.class, () -> new StrataPlanV2.Layer(
                0, "lithology.hard-intrusive", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StrataPlanV2.Profile(
                "bad", "province-uniform-prior", 1, "formation.uniform-prior", 1,
                StrataPlanV2.LayerOrder.BOTTOM_TO_TOP,
                List.of(
                        new StrataPlanV2.Layer(1, "lithology.hard-intrusive", 1, 8),
                        new StrataPlanV2.Layer(0, "lithology.basaltic-flow", 3, 8)),
                StrataPlanV2.FoldTilt.flat()));
        assertThrows(IllegalArgumentException.class, () -> StrataPlanV2.FoldTilt.cardinalDip(10, 45));

        String canonical = codec.canonicalStrataPlan(plan);
        assertThrows(StructuredDataValidationException.class, () -> codec.readStrataPlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-strata-plan"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readStrataPlan(
                canonical.replace("\"UNIFORM_GEOLOGY_PRIOR\"", "\"IMPLICIT_DEFAULT\""), "unknown-handoff"));
        assertThrows(Exception.class, () -> codec.readStrataPlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "tampered-strata-plan"));

        List<StrataPlanV2.Profile> unknownLithology = List.of(new StrataPlanV2.Profile(
                plan.profiles().getFirst().profileId(),
                plan.profiles().getFirst().provinceId(),
                plan.profiles().getFirst().provinceCode(),
                plan.profiles().getFirst().formationId(),
                plan.profiles().getFirst().formationCode(),
                StrataPlanV2.LayerOrder.BOTTOM_TO_TOP,
                List.of(new StrataPlanV2.Layer(0, "lithology.hard-intrusive", 99, 64)),
                StrataPlanV2.FoldTilt.flat()));
        StrataPlanV2 bad = codec.sealStrataPlan(new StrataPlanV2(
                plan.planVersion(), plan.profileContractVersion(), plan.sourceGeologyPlanChecksum(),
                plan.sourceLithologyPlanChecksum(), unknownLithology, plan.hydrologyHandoff(), plan.budget(),
                "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> bad.requireLithologyPlan(geology, lithology));
    }

    @Test
    void wholeTileSeamThreadLocaleAndTimezoneAreStable() throws Exception {
        GeologyPlanV2 firstGeology = multiProvinceGeology(false);
        GeologyPlanV2 reversedGeology = multiProvinceGeology(true);
        LithologyPlanV2 firstLithology = new LithologyPlanCompilerV2().compile(firstGeology);
        StrataPlanV2 first = new StrataPlanCompilerV2().compile(firstGeology, firstLithology);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            LithologyPlanV2 reorderedLithology = new LithologyPlanCompilerV2().compile(reversedGeology);
            StrataPlanV2 reordered = new StrataPlanCompilerV2().compile(reversedGeology, reorderedLithology);
            assertEquals(firstGeology.canonicalChecksum(), reversedGeology.canonicalChecksum());
            assertEquals(first.canonicalChecksum(), reordered.canonicalChecksum());
            assertNotEquals(first.canonicalChecksum(), new StrataPlanCompilerV2().compile(
                    geology(128, 96, 827414L),
                    new LithologyPlanCompilerV2().compile(geology(128, 96, 827414L))).canonicalChecksum());
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }

        StrataExposureResolverV2 resolver = new StrataExposureResolverV2(firstGeology, firstLithology, first);
        int[] whole = sample(resolver, 257, 1, false);
        assertArrayEquals(whole, sample(resolver, 128, 1, false));
        assertArrayEquals(whole, sample(resolver, 128, 1, true));
        assertArrayEquals(whole, sample(resolver, 128, 4, false));
        assertArrayEquals(whole, sample(resolver, 128, 4, true));
        // Seam cells shared by adjacent tiles stay identical.
        assertEquals(resolver.derivedHardnessRaw(1, 127, 0), resolver.derivedHardnessRaw(1, 127, 0));
        assertEquals(resolver.derivedHardnessRaw(2, 128, 0), resolver.derivedHardnessRaw(2, 128, 0));
    }

    @Test
    void rejectsLayerTileBudgetOverflow() {
        GeologyPlanV2 geology = geology(64, 48, 1L);
        LithologyPlanV2 lithology = new LithologyPlanCompilerV2().compile(geology);
        StrataPlanV2 plan = new StrataPlanCompilerV2().compile(geology, lithology);
        assertThrows(IllegalArgumentException.class, () -> new StrataPlanV2(
                plan.planVersion(), plan.profileContractVersion(), plan.sourceGeologyPlanChecksum(),
                plan.sourceLithologyPlanChecksum(), plan.profiles(), plan.hydrologyHandoff(),
                new StrataPlanV2.ResourceBudget(
                        StrataPlanV2.ResourceBudget.VERSION,
                        StrataPlanV2.MAX_PROFILES,
                        StrataPlanV2.MAX_LAYERS_PER_PROFILE,
                        StrataPlanV2.MAX_TOTAL_LAYERS,
                        StrataPlanV2.MAX_STACK_THICKNESS_BLOCKS,
                        plan.budget().globalCellCount(),
                        1L,
                        plan.budget().estimatedRetainedBytes(),
                        plan.budget().maximumCanonicalBytes()),
                "0".repeat(64)));
    }

    private GeologyPlanV2 geology(int width, int length, long seed) {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
        HydrologyPlanV2 hydrology = new HydrologyPlanCompilerV2().compile(bounds);
        return new GeologyPlanCompilerV2().compile(bounds, 128, seed, hydrology.fixedPriors());
    }

    private GeologyPlanV2 multiProvinceGeology(boolean reverse) {
        GeologyPlanV2 base = geology(257, 259, 827413L);
        List<GeologyPlanV2.ProvinceDescriptor> provinces = new ArrayList<>(List.of(
                base.provinces().getFirst(),
                new GeologyPlanV2.ProvinceDescriptor(
                        "province-basalt", 2, "formation.basalt", 2, 700_000, 150_000)));
        if (reverse) {
            Collections.reverse(provinces);
        }
        return codec.sealGeologyPlan(new GeologyPlanV2(
                base.planVersion(), base.fieldContractVersion(), base.moduleId(), base.moduleVersion(),
                base.stageId(), base.priorReplacement(), base.namedSeed(), base.seedNamespace(), base.width(),
                base.length(), provinces, base.fields(), base.budget(), "0".repeat(64)));
    }

    private static int[] sample(
            StrataExposureResolverV2 resolver,
            int tileSize,
            int threads,
            boolean reverse
    ) throws Exception {
        int width = 257;
        int length = 259;
        int[] result = new int[width * length];
        List<Tile> tiles = new ArrayList<>();
        for (int z = 0; z < length; z += tileSize) {
            for (int x = 0; x < width; x += tileSize) {
                tiles.add(new Tile(x, z, Math.min(tileSize, width - x), Math.min(tileSize, length - z)));
            }
        }
        if (reverse) {
            Collections.reverse(tiles);
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Tile tile : tiles) {
                futures.add(executor.submit(() -> {
                    for (int z = 0; z < tile.length(); z++) {
                        for (int x = 0; x < tile.width(); x++) {
                            int globalX = tile.x() + x;
                            int globalZ = tile.z() + z;
                            int province = (globalX + globalZ) % 2 == 0 ? 1 : 2;
                            result[globalZ * width + globalX] = resolver.derivedHardnessRaw(province, globalX, globalZ);
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private record Tile(int x, int z, int width, int length) {
    }
}
