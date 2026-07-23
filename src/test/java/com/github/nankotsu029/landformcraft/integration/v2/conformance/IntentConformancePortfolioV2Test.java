package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetEvaluationV2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-18-11 permanent intent-conformance regression.
 *
 * <p>Closes audit item 5 ("no layer asserts terrain shape"). Every case is exported once through the
 * public production export service and then measured <em>from the published Release</em> — the sealed
 * intent, the frozen blueprint and the ACTUAL land-water sidecar — so the assertions describe the
 * artifact an operator ships, not in-process generator state.</p>
 *
 * <p>The portfolio is deliberately additive: later leaves that connect new kinds add a
 * {@link IntentConformancePortfolioV2.CaseV2} entry (mountain+river+lake, island+reef, …). Promoting
 * the portfolio into the Phase gate is {@code V2-18-12}'s Task, not this one's.</p>
 */
class IntentConformancePortfolioV2Test {
    @TempDir
    static Path root;

    private static GenerationExecutors executors;
    private static final Map<String, Path> RELEASES = new LinkedHashMap<>();
    private static final Map<String, IntentConformancePortfolioV2.MeasurementsV2> MEASUREMENTS =
            new LinkedHashMap<>();

    static Stream<IntentConformancePortfolioV2.CaseV2> cases() {
        return IntentConformancePortfolioV2.cases().stream();
    }

    @BeforeAll
    static void exportPortfolio() throws Exception {
        executors = GenerationExecutors.createDefault(2);
        for (IntentConformancePortfolioV2.CaseV2 portfolioCase : IntentConformancePortfolioV2.cases()) {
            Path run = root.resolve(portfolioCase.id());
            Release2ExportResultV2 result = new Release2ExportApplicationServiceV2(executors).exportNow(
                    new Release2ExportRequestV2(
                            portfolioCase.request(), portfolioCase.intent(),
                            run.resolve("work"), run.resolve("exports"),
                            portfolioCase.id(), portfolioCase.baseline()));
            RELEASES.put(portfolioCase.id(), result.releaseDirectory());
            MEASUREMENTS.put(portfolioCase.id(),
                    IntentConformancePortfolioV2.measure(result.releaseDirectory()));
        }
    }

    @AfterAll
    static void stopExecutors() {
        executors.shutdown(Duration.ofSeconds(30));
        executors.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void publishedEdgesMatchTheDeclaredEdgeClassification(IntentConformancePortfolioV2.CaseV2 portfolioCase) {
        List<TargetEvaluationV2> edges = MEASUREMENTS.get(portfolioCase.id()).edgeEvaluations();

        // Both honored fixtures declare the audit's original macro composition: north is mainland,
        // south is open sea. V2-18-11 restored them to HARD (V2-18-04 evaluates EDGE, V2-18-09 gives
        // the mask a foundation), so production now rejects an export that loses either edge.
        assertEquals(List.of("north-is-land", "south-is-sea"),
                edges.stream().map(TargetEvaluationV2::targetId).sorted().toList());
        for (TargetEvaluationV2 edge : edges) {
            assertEquals(TerrainIntentV2.Strength.HARD, edge.hardness(), edge.targetId());
            assertTrue(edge.satisfied(), edge::detail);
            // Pinned, not merely "above the minimum": the macro foundation resolves both edge bands
            // entirely from the declared mask, so any drift is a shape regression to look at.
            assertEquals(1_000_000L, edge.measuredMillionths(), edge::detail);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theBeachLandBandsStayConnectedToTheDeclaredBackshore(
            IntentConformancePortfolioV2.CaseV2 portfolioCase) {
        IntentConformancePortfolioV2.MeasurementsV2 measurements = MEASUREMENTS.get(portfolioCase.id());
        IntentConformancePortfolioV2.BeachContinuityV2 beach = measurements.beach();
        IntentConformancePortfolioV2.HinterlandV2 hinterland = measurements.backshorePlains();

        assertTrue(beach.landBandCells() > 0, "the beach owns no foreshore/backshore cell");
        // A foreshore or backshore cell that is water is a beach the terrain never actually built.
        assertEquals(beach.landBandCells(), beach.landBandOnLand());
        // …and the nearshore band is its mirror image: sand below the waterline is equally wrong.
        assertTrue(beach.nearshoreCells() > 0, "the beach owns no nearshore cell");
        assertEquals(beach.nearshoreCells(), beach.nearshoreOnWater());
        // Continuity: one land component for the whole beach, and it is the mainland …
        assertEquals(1, beach.landBandComponentCount());
        assertEquals(beach.landBandCells(), beach.landBandInMainland());
        // … which also carries the declared backshore hinterland, so beach ↔ backshore is one land
        // mass rather than a beach stranded on an island (the audit's baseline-fallback symptom).
        assertTrue(hinterland.polygonCells() > 0, "the case declares no backshore hinterland area");
        assertEquals(hinterland.polygonCells(), hinterland.onLand());
        assertEquals(hinterland.polygonCells(), hinterland.inMainland());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyDeclaredBreakwaterArmIsMeasuredAgainstItsLandfall(
            IntentConformancePortfolioV2.CaseV2 portfolioCase) {
        IntentConformancePortfolioV2.MeasurementsV2 measurements = MEASUREMENTS.get(portfolioCase.id());

        assertEquals(portfolioCase.declaredArmIds(), ids(measurements.arms()));
        for (IntentConformancePortfolioV2.ArmLandfallV2 arm : measurements.arms()) {
            assertTrue(arm.ownedCells() > 0, arm.armId());
            assertEquals(portfolioCase.shoreConnectedArmIds().contains(arm.armId()), arm.connectedToShore(),
                    () -> "shore connection of " + arm.armId() + " changed: " + arm);
        }
    }

    @Test
    void theHonored400EastArmIsAnIsolatedIslandUntilV21813CorrectsTheFixture() {
        // Registered non-conformance, kept visible rather than asserted away. The declared
        // "east-landfall" endpoint (0.61, 0.47) stops short of the rocky cape's western edge (0.62),
        // so the whole east arm composes as its own land component: every cell of it is land, none of
        // it touches off-structure mainland, and the landfall cell itself is not on the mainland.
        // Correcting the geometry means regenerating the V2-18-09 land-water mask, which is V2-18-13.
        IntentConformancePortfolioV2.MeasurementsV2 measurements = MEASUREMENTS.get("coastal-honored-400");
        IntentConformancePortfolioV2.ArmLandfallV2 east = measurements.arm("east-arm");
        IntentConformancePortfolioV2.ArmLandfallV2 west = measurements.arm("west-arm");

        assertEquals(east.ownedCells(), east.ownedLandCells());
        assertEquals(0, east.offStructureMainlandContacts());
        assertFalse(east.landfallCellInMainland());
        assertFalse(east.connectedToShore());
        // The same measurement reports the west arm as connected, so the assertion is discriminating
        // rather than universally failing.
        assertTrue(west.offStructureMainlandContacts() > 0);
        assertTrue(west.landfallCellInMainland());
        assertTrue(west.connectedToShore());
        // Both arms are still one land component each, so the isolated arm is a landfall defect and
        // not a fragmented structure.
        assertTrue(measurements.landComponentCount() >= 2);
    }

    @Test
    void aFloodedNorthEdgeIsRejectedByTheSameEdgeMeasurement() throws Exception {
        // The audit's exact symptom (north edge land share 0.000) fed back through the published
        // artifact: the portfolio must fail, not merely report a lower share.
        Path release = RELEASES.get("harbor-cove-64-honored");
        WorldBlueprintV2 blueprint = IntentConformancePortfolioV2.blueprintOf(release);
        TerrainIntentV2 intent = IntentConformancePortfolioV2.intentOf(release);
        int[] land = IntentConformancePortfolioV2.readActualLandWater(release);

        IntentConformancePortfolioV2.MeasurementsV2 damaged = IntentConformancePortfolioV2.measure(
                blueprint, intent,
                IntentConformancePortfolioV2.withFloodedNorthEdgeBand(land, 64, 64));

        TargetEvaluationV2 north = damaged.edgeEvaluations().stream()
                .filter(edge -> edge.targetId().equals("north-is-land")).findFirst().orElseThrow();
        assertFalse(north.satisfied(), north::detail);
        assertEquals(0L, north.measuredMillionths());
    }

    @Test
    void aFloodedBackshoreBreaksTheBeachContinuityMeasurement() throws Exception {
        Path release = RELEASES.get("harbor-cove-64-honored");
        WorldBlueprintV2 blueprint = IntentConformancePortfolioV2.blueprintOf(release);
        TerrainIntentV2 intent = IntentConformancePortfolioV2.intentOf(release);
        int[] land = IntentConformancePortfolioV2.readActualLandWater(release);

        IntentConformancePortfolioV2.MeasurementsV2 damaged = IntentConformancePortfolioV2.measure(
                blueprint, intent,
                IntentConformancePortfolioV2.withFloodedHinterland(intent, land, 64, 64));

        IntentConformancePortfolioV2.HinterlandV2 hinterland = damaged.backshorePlains();
        assertTrue(hinterland.polygonCells() > 0);
        assertEquals(0, hinterland.onLand());
        assertEquals(0, hinterland.inMainland());
        // The surviving beach is no longer continuous with the declared backshore, which is precisely
        // what the positive assertion pins.
        assertNotEquals(hinterland.polygonCells(), hinterland.inMainland());
    }

    @Test
    void anIsolatedLandBlobIsNeverCountedAsMainland() {
        // Unit-level guard for the component kernel the shape assertions depend on: 8×8 raster with a
        // 3×3 mass and a 2-cell island. The island must not be mistaken for the mainland.
        int[] land = new int[64];
        for (int z = 1; z <= 3; z++) {
            for (int x = 1; x <= 3; x++) {
                land[z * 8 + x] = IntentConformancePortfolioV2.LAND;
            }
        }
        land[6 * 8 + 6] = IntentConformancePortfolioV2.LAND;
        land[6 * 8 + 7] = IntentConformancePortfolioV2.LAND;

        IntentConformancePortfolioV2.LandComponentsV2 components =
                IntentConformancePortfolioV2.LandComponentsV2.of(land, 8, 8);

        assertEquals(11, components.landCells());
        assertEquals(2, components.componentCount());
        assertEquals(9, components.mainlandCells());
        assertTrue(components.isMainland(1 * 8 + 1));
        assertFalse(components.isMainland(6 * 8 + 6));
        assertFalse(components.isMainland(0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theMeasurementIsIndependentOfLocaleAndTimezone(IntentConformancePortfolioV2.CaseV2 portfolioCase)
            throws Exception {
        Path release = RELEASES.get(portfolioCase.id());
        IntentConformancePortfolioV2.MeasurementsV2 baseline = MEASUREMENTS.get(portfolioCase.id());
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(baseline, IntentConformancePortfolioV2.measure(release));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        assertEquals(baseline, IntentConformancePortfolioV2.measure(release));
        assertEquals(portfolioCase.width(), baseline.width());
        assertEquals(portfolioCase.length(), baseline.length());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void thePublishedReleaseIsTheOnlyMeasurementInput(IntentConformancePortfolioV2.CaseV2 portfolioCase)
            throws Exception {
        // Measuring a Release copied away from its work directory must give the identical record: the
        // portfolio may not depend on staging leftovers or on the exporting process.
        Path release = RELEASES.get(portfolioCase.id());
        Path copy = root.resolve("copies").resolve(portfolioCase.id());
        copyTree(release, copy);

        assertEquals(MEASUREMENTS.get(portfolioCase.id()), IntentConformancePortfolioV2.measure(copy));
    }

    private static void copyTree(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }

    private static Set<String> ids(List<IntentConformancePortfolioV2.ArmLandfallV2> arms) {
        return arms.stream().map(IntentConformancePortfolioV2.ArmLandfallV2::armId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
