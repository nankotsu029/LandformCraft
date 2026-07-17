package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.geology.ProvinceLithologyResolverV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

class LithologyPlanCompilerV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void compilesFixedCatalogAndExplicitProvinceAssignment() {
        GeologyPlanV2 geology = geology(128, 96, 827413L);
        LithologyPlanV2 plan = new LithologyPlanCompilerV2().compile(geology);

        assertEquals(9, plan.catalog().entries().size());
        assertEquals(LithologyPlanV2.SemanticLithology.values().length, plan.catalog().budget().maximumEntries());
        assertEquals(LithologyPlanV2.COMPACT_CODE_BITS, plan.catalog().budget().compactCodeBits());
        assertEquals(1, plan.provinceAssignments().size());
        assertEquals("lithology.hard-intrusive", plan.provinceAssignments().getFirst().lithologyId());
        assertEquals(1, plan.provinceAssignments().getFirst().lithologyCode());
        assertEquals(geology.canonicalChecksum(), plan.sourceGeologyPlanChecksum());
        assertEquals(codec.lithologyCatalogChecksum(plan.catalog()), plan.catalog().canonicalChecksum());
        assertEquals(codec.lithologyPlanChecksum(plan), plan.canonicalChecksum());
        plan.requireGeologyPlan(geology);
    }

    @Test
    void catalogAndPlanRoundTripAndRejectFutureUnknownAndExternalPreset(@TempDir Path directory) throws Exception {
        LithologyPlanV2 plan = new LithologyPlanCompilerV2().compile(geology(64, 48, 1L));
        Path artifact = directory.resolve("lithology-plan-v2.json");
        codec.writeLithologyPlan(artifact, plan);
        assertEquals(plan, codec.readLithologyPlan(artifact));
        assertTrue(codec.canonicalLithologyCatalog(plan.catalog()).getBytes(StandardCharsets.UTF_8).length
                < plan.catalog().budget().maximumCanonicalBytes());
        assertTrue(codec.canonicalLithologyPlan(plan).getBytes(StandardCharsets.UTF_8).length
                < plan.budget().maximumCanonicalBytes());

        String canonical = codec.canonicalLithologyPlan(plan);
        assertThrows(StructuredDataValidationException.class, () -> codec.readLithologyPlan(
                canonical.replace("\"planVersion\":1", "\"planVersion\":2"), "future-lithology-plan"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readLithologyPlan(
                canonical.replace("\"HARD_INTRUSIVE\"", "\"UNTRUSTED_PLUGIN\""), "unknown-lithology"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readLithologyPlan(
                canonical.replace("\"kind\":\"HARD_INTRUSIVE\"",
                        "\"kind\":\"HARD_INTRUSIVE\",\"className\":\"example.ExternalPreset\""),
                "external-lithology-preset"));
        assertThrows(IOException.class, () -> codec.readLithologyPlan(
                canonical.replace(plan.canonicalChecksum(), "0".repeat(64)), "tampered-lithology-plan"));
    }

    @Test
    void assignmentOrderThreadLocaleAndTimezoneAreStable() throws Exception {
        GeologyPlanV2 firstGeology = multiProvinceGeology(false);
        GeologyPlanV2 reversedGeology = multiProvinceGeology(true);
        LithologyPlanV2 first = new LithologyPlanCompilerV2().compile(firstGeology);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            LithologyPlanV2 reordered = new LithologyPlanCompilerV2().compile(reversedGeology);
            assertEquals(firstGeology.canonicalChecksum(), reversedGeology.canonicalChecksum());
            assertEquals(first.canonicalChecksum(), reordered.canonicalChecksum());
            assertNotEquals(first.canonicalChecksum(), new LithologyPlanCompilerV2().compile(
                    geology(128, 96, 827414L)).canonicalChecksum());
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }

        ProvinceLithologyResolverV2 resolver = new ProvinceLithologyResolverV2(firstGeology, first);
        int[] whole = resolve(resolver, 257, 1, false);
        assertArrayEquals(whole, resolve(resolver, 128, 1, false));
        assertArrayEquals(whole, resolve(resolver, 128, 1, true));
        assertArrayEquals(whole, resolve(resolver, 128, 4, false));
        assertArrayEquals(whole, resolve(resolver, 128, 4, true));
    }

    @Test
    void rejectsMismatchedProvinceAndUnknownCompactCode() {
        GeologyPlanV2 geology = multiProvinceGeology(false);
        LithologyPlanV2 plan = new LithologyPlanCompilerV2().compile(geology);
        List<LithologyPlanV2.ProvinceAssignment> bad = new ArrayList<>(plan.provinceAssignments());
        LithologyPlanV2.ProvinceAssignment first = bad.getFirst();
        bad.set(0, new LithologyPlanV2.ProvinceAssignment(
                first.provinceId(), first.provinceCode(), first.formationId(), first.formationCode(),
                "lithology.reef-carbonate", 9));
        LithologyPlanV2 mismatch = codec.sealLithologyPlan(new LithologyPlanV2(
                plan.planVersion(), plan.assignmentContractVersion(), plan.sourceGeologyPlanChecksum(), plan.catalog(),
                bad, plan.budget(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> mismatch.requireGeologyPlan(geology));

        ProvinceLithologyResolverV2 resolver = new ProvinceLithologyResolverV2(geology, plan);
        assertThrows(IllegalArgumentException.class, () -> resolver.lithologyCodeForProvinceRaw(999));
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
        if (reverse) Collections.reverse(provinces);
        return codec.sealGeologyPlan(new GeologyPlanV2(
                base.planVersion(), base.fieldContractVersion(), base.moduleId(), base.moduleVersion(),
                base.stageId(), base.priorReplacement(), base.namedSeed(), base.seedNamespace(), base.width(),
                base.length(), provinces, base.fields(), base.budget(), "0".repeat(64)));
    }

    private static int[] resolve(
            ProvinceLithologyResolverV2 resolver,
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
        if (reverse) Collections.reverse(tiles);
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
                            result[globalZ * width + globalX] = resolver.lithologyCodeForProvinceRaw(province);
                        }
                    }
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private record Tile(int x, int z, int width, int length) {
    }
}
