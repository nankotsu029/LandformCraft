package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-01 semantic materialization gate contract.
 *
 * <p>Exercises the block-metric column with a pair of published Releases that genuinely differ in
 * their final canonical block stream: the {@code harbor-cove-64-honored} coastal contract exported
 * as-is (baseline) and re-exported with the declared foundation land surface raised by one block
 * (feature analogue). The negative direction — a route with no block effect failing the gate — is
 * pinned in {@link IntentConformancePortfolioV2Test} against the real V2-15-10 RIVER route; this
 * class pins the instrument itself: a real block effect is measured, classified and gated, an
 * identity stream (the shape of a constant healthy sampler or an {@code ADD_FLUID}-into-solid
 * identity slice) always fails, and the declaration must match the observed effect classes.</p>
 */
class FeatureMaterializationGateV2Test {
    private static final Path REQUEST =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json");
    private static final Path INTENT =
            Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json");
    private static final Path MASK =
            Path.of("examples/v2/diagnostic/maps/harbor-cove-64-honored-land-water-u8.png");
    private static final SurfaceBaselineV2 BASELINE_ARGUMENT =
            new SurfaceBaselineV2(HardLandWaterSourceV2.Classification.WATER, 54, 46);

    @TempDir
    static Path root;

    private static GenerationExecutors executors;
    private static Path baselineRelease;
    private static Path raisedRelease;

    @BeforeAll
    static void exportBaselineAndRaisedReleases() throws Exception {
        executors = GenerationExecutors.createDefault(2);
        Release2ExportApplicationServiceV2 service = new Release2ExportApplicationServiceV2(executors);

        Path baselineRun = root.resolve("baseline");
        baselineRelease = service.exportNow(new Release2ExportRequestV2(
                REQUEST, INTENT, baselineRun.resolve("work"), baselineRun.resolve("exports"),
                "harbor-cove-64-honored", BASELINE_ARGUMENT)).releaseDirectory();

        // The same declared foundation input with landSurfaceY 54 → 55: the mask, the seed and every
        // coastal feature stay identical, so the only difference in the final canonical block stream
        // is the background land surface — a real, bounded block effect to point the instrument at.
        Path fixture = root.resolve("raised-fixture");
        Files.createDirectories(fixture.resolve("maps"));
        String request = Files.readString(REQUEST, StandardCharsets.UTF_8);
        String raised = request.replace("\"landSurfaceY\": 54", "\"landSurfaceY\": 55");
        assertTrue(!raised.equals(request), "fixture no longer declares landSurfaceY 54");
        Path raisedRequest = fixture.resolve("harbor-cove-64-honored.request-v2.json");
        Files.writeString(raisedRequest, raised, StandardCharsets.UTF_8);
        Files.copy(MASK, fixture.resolve("maps").resolve(MASK.getFileName().toString()));

        Path raisedRun = root.resolve("raised");
        raisedRelease = new Release2ExportApplicationServiceV2(executors)
                .exportNow(new Release2ExportRequestV2(
                        raisedRequest, INTENT, raisedRun.resolve("work"), raisedRun.resolve("exports"),
                        "harbor-cove-64-honored-raised", BASELINE_ARGUMENT)).releaseDirectory();
    }

    @AfterAll
    static void stopExecutors() {
        executors.shutdown(Duration.ofSeconds(30));
        executors.close();
    }

    @Test
    void aRealBlockEffectIsMeasuredClassifiedAndPassesTheGate() throws Exception {
        FeatureMaterializationV2.BlockEffectV2 effect =
                FeatureMaterializationV2.measureBlockEffect(baselineRelease, raisedRelease);

        // 64×64 cells × (72−32+1) layers compared; the raised background must change blocks, and the
        // change must include solid mass appearing where air was (the raised land surface).
        assertEquals(64L * 64L * 41L, effect.comparedCells());
        assertTrue(effect.changedCells() > 0, "raising the declared land surface changed no block");
        assertTrue(effect.solidShapeChanges() > 0, () -> "no solid shape change measured: " + effect);

        FeatureMaterializationV2.requireMaterialized(
                new FeatureMaterializationV2.MaterializationClaimV2(
                        "background-foundation", TerrainIntentV2.FeatureKind.PLAIN,
                        Set.of("surface.height"), effect.observedClasses()),
                effect);
    }

    @Test
    void theDeclarationMustMatchTheObservedEffectClasses() throws Exception {
        FeatureMaterializationV2.BlockEffectV2 effect =
                FeatureMaterializationV2.measureBlockEffect(baselineRelease, raisedRelease);
        assertTrue(!effect.observedClasses().equals(Set.of(FeatureMaterializationV2.EffectClassV2.FLUID)));

        // Declaring an effect class the stream does not show (or omitting one it shows) fails: the
        // declaration is a shape-conformance contract, not a label.
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> FeatureMaterializationV2.requireMaterialized(
                        new FeatureMaterializationV2.MaterializationClaimV2(
                                "background-foundation", TerrainIntentV2.FeatureKind.PLAIN,
                                Set.of("surface.height"),
                                Set.of(FeatureMaterializationV2.EffectClassV2.FLUID)),
                        effect));
        assertTrue(rejected.getMessage().contains("shape conformance"), rejected::getMessage);
    }

    @Test
    void anEmptyDeclarationIsRejectedEvenWithANonEmptyEffect() throws Exception {
        FeatureMaterializationV2.BlockEffectV2 effect =
                FeatureMaterializationV2.measureBlockEffect(baselineRelease, raisedRelease);

        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> FeatureMaterializationV2.requireMaterialized(
                        new FeatureMaterializationV2.MaterializationClaimV2(
                                "undeclared", TerrainIntentV2.FeatureKind.PLAIN, Set.of(), Set.of()),
                        effect));
        assertTrue(rejected.getMessage().contains("declares no changed canonical field"),
                rejected::getMessage);
    }

    @Test
    void anIdentityStreamAlwaysFailsTheGate() throws Exception {
        // A Release diffed against itself is the exact block-stream shape of every intentional
        // no-op: a constant healthy validation sampler, or the sparse shared path's
        // ADD_FLUID-into-solid identity slice, leaves the final stream byte-identical to its
        // baseline. Such evidence must never promote a Feature.
        FeatureMaterializationV2.BlockEffectV2 identity =
                FeatureMaterializationV2.measureBlockEffect(baselineRelease, baselineRelease);

        assertEquals(64L * 64L * 41L, identity.comparedCells());
        assertEquals(0L, identity.changedCells());
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> FeatureMaterializationV2.requireMaterialized(
                        new FeatureMaterializationV2.MaterializationClaimV2(
                                "identity-slice", TerrainIntentV2.FeatureKind.FLOODED_CAVE,
                                Set.of("volume.fluid"),
                                Set.of(FeatureMaterializationV2.EffectClassV2.FLUID)),
                        identity));
        assertTrue(rejected.getMessage().contains("no effect on the final canonical block stream"),
                rejected::getMessage);
    }

    @Test
    void theMeasurementIsPureAndLocaleAndTimezoneIndependent() throws Exception {
        FeatureMaterializationV2.BlockEffectV2 first =
                FeatureMaterializationV2.measureBlockEffect(baselineRelease, raisedRelease);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(first, FeatureMaterializationV2.measureBlockEffect(baselineRelease, raisedRelease));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        assertEquals(first, FeatureMaterializationV2.measureBlockEffect(baselineRelease, raisedRelease));
    }
}
