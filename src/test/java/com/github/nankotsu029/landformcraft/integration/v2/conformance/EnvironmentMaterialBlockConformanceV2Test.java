package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2EnvironmentExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-10 block-metric evidence for the material／palette connection.
 *
 * <p>The 2026-07-23 cross-cutting audit recorded two facts about the environment stack: the surface
 * resolver picked from eleven inlined block-state literals and the sealed palette／material plans
 * reached no block (§2.4), and the shared environment validation sampled a constant {@code healthy}
 * snapshot (§2.8). This test measures the fix the same way {@code V2-19-01} requires a Feature to be
 * measured — from the <em>final canonical block stream</em> of a published Release against a
 * published baseline Release — using the very same {@link FeatureMaterializationV2} measurement
 * code, and deliberately not from a plan artifact or a validation report.</p>
 *
 * <p>It is not a Feature promotion: no FeatureKind changes support column or dispatch reachability
 * here. What is measured is that binding the environment material changes blocks, that it changes
 * only their material (never solid mass and never a fluid), and that the change varies across the
 * domain — a constant sampler would paint every natural surface cell the same way.</p>
 */
class EnvironmentMaterialBlockConformanceV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json");
    private static final SurfaceBaselineV2 BASELINE =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46);

    @TempDir
    static Path root;

    private static GenerationExecutors executors;
    private static Path surfaceRelease;
    private static Path environmentRelease;

    @BeforeAll
    static void exportBoth() throws Exception {
        executors = GenerationExecutors.createDefault(2);
        surfaceRelease = new Release2ExportApplicationServiceV2(executors)
                .exportNow(request("surface-baseline")).releaseDirectory();
        environmentRelease = new Release2EnvironmentExportApplicationServiceV2(executors)
                .exportNow(request("environment-material")).releaseDirectory();
    }

    @AfterAll
    static void stopExecutors() {
        executors.shutdown(Duration.ofSeconds(30));
        executors.close();
    }

    @Test
    void theSealedEnvironmentMaterialChangesTheFinalCanonicalBlockStream() throws Exception {
        FeatureMaterializationV2.BlockEffectV2 effect =
                FeatureMaterializationV2.measureBlockEffect(surfaceRelease, environmentRelease);

        assertFalse(effect.empty(), "binding the sealed environment material changed no block");
        // Only the material of already-solid cells may change: the override replaces one solid block
        // state with another, so it can neither add nor remove solid mass nor create/destroy a fluid.
        assertEquals(Set.of(FeatureMaterializationV2.EffectClassV2.MATERIAL), effect.observedClasses());
        assertEquals(0L, effect.solidShapeChanges());
        assertEquals(0L, effect.fluidChanges());
        assertEquals(effect.changedCells(), effect.materialChanges());
        // Pinned, not merely "non-zero": the exposed surface of every natural cell the sealed
        // material profile calls wet or snow-covered, and nothing else.
        assertEquals(167_936L, effect.comparedCells());
        assertEquals(3_191L, effect.changedCells());
    }

    @Test
    void theOverriddenSurfaceVariesAcrossTheDomainInsteadOfBeingConstant() throws Exception {
        FeatureMaterializationV2.FinalBlockStreamV2 baseline =
                FeatureMaterializationV2.readFinalBlockStream(surfaceRelease);
        FeatureMaterializationV2.FinalBlockStreamV2 environment =
                FeatureMaterializationV2.readFinalBlockStream(environmentRelease);

        TreeMap<String, Integer> baselineTop = new TreeMap<>();
        TreeMap<String, Integer> environmentTop = new TreeMap<>();
        for (int z = 0; z < baseline.length(); z++) {
            for (int x = 0; x < baseline.width(); x++) {
                int y = topSolidY(baseline, x, z);
                baselineTop.merge(baseline.at(x, y, z), 1, Integer::sum);
                environmentTop.merge(environment.at(x, y, z), 1, Integer::sum);
            }
        }
        // The baseline is the frozen V2-2 role table: beach sand, cape rock, seabed gravel,
        // vegetated mainland, breakwater crest. V2-19-10 must not have moved it.
        assertEquals(
                Map.of("minecraft:cobblestone", 228, "minecraft:grass_block", 1062,
                        "minecraft:gravel", 2251, "minecraft:sand", 221, "minecraft:stone_bricks", 334),
                baselineTop);
        // With the environment material bound, the sealed profile's WETNESS_OVERRIDE claims every
        // cell the water-condition field calls wet — here the coast and the seabed — while the drier
        // inland keeps the role's base assignment and the built breakwater crest is never claimed.
        // A constant sampler would resolve one class for the whole domain and leave one state.
        assertEquals(
                Map.of("minecraft:cobblestone", 3419, "minecraft:grass_block", 343,
                        "minecraft:stone_bricks", 334),
                environmentTop);
        assertTrue(environmentTop.size() >= 2,
                "the environment exposed surface is uniform: " + environmentTop);
    }

    @Test
    void theEnvironmentMaterialIsIndependentOfLocaleAndTimeZone() throws Exception {
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        Path repeated;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            repeated = new Release2EnvironmentExportApplicationServiceV2(executors)
                    .exportNow(request("environment-material-repeat")).releaseDirectory();
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }

        FeatureMaterializationV2.BlockEffectV2 first =
                FeatureMaterializationV2.measureBlockEffect(surfaceRelease, environmentRelease);
        FeatureMaterializationV2.BlockEffectV2 second =
                FeatureMaterializationV2.measureBlockEffect(surfaceRelease, repeated);
        assertEquals(first, second);
        assertEquals(FeatureMaterializationV2.measureBlockEffect(environmentRelease, repeated).changedCells(), 0L);
    }

    private static int topSolidY(FeatureMaterializationV2.FinalBlockStreamV2 stream, int x, int z) {
        for (int y = stream.maxY(); y >= stream.minY(); y--) {
            if (stream.isSolid(x, y, z)) {
                return y;
            }
        }
        throw new IllegalStateException("column " + x + "," + z + " carries no solid block");
    }

    private static Release2ExportRequestV2 request(String releaseId) {
        Path run = root.resolve(releaseId);
        return new Release2ExportRequestV2(
                REQUEST, INTENT, run.resolve("work"), run.resolve("exports"), releaseId, BASELINE);
    }
}
