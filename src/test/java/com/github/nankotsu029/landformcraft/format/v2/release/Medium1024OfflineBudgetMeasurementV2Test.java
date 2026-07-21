package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.PreviewDimensionBudgetV2;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-13-03: 1024×1024 offline generation budget measurement, plus its cell-budget follow-up fix.
 *
 * <p>V2-13-03 measured that the frozen 1_000_000-cell subsystem budgets (hydrology GraphWorkBudget,
 * preview, macro raster) rejected 1024×1024 = 1_048_576 cells end to end. A follow-up raised those
 * budgets to {@link com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2#MEDIUM_MAXIMUM_CELLS}
 * (the MEDIUM ceiling squared), so 1024² is now admitted through the offline pipeline.</p>
 *
 * <p>Heavy end-to-end probe runs only when {@code LANDFORMCRAFT_V21303_EVIDENCE_DIR} or
 * {@code landformcraft.v21303.evidenceDir} is set, so the default suite stays fast while the
 * committed evidence is produced by the measurement runner.</p>
 */
class Medium1024OfflineBudgetMeasurementV2Test {
    private static final Path SQUARE_REQUEST =
            Path.of("examples/v2/diagnostic/medium-1024-square.request-v2.json");

    @Test
    void squareRequestFixtureIsMediumCeilingAndPreviewAdmitted() throws Exception {
        GenerationRequestV2 request = new LandformV2DataCodec().readGenerationRequest(
                Files.readString(SQUARE_REQUEST), "v2-13-03-square-request");
        assertEquals(1_024, request.bounds().width());
        assertEquals(1_024, request.bounds().length());
        assertEquals(1_048_576L, (long) request.bounds().width() * request.bounds().length());
        assertTrue(PreviewDimensionBudgetV2.admits(request.bounds().width(), request.bounds().length()));
        assertTrue(PreviewDimensionBudgetV2.admits(1_000, 1_000));
        assertTrue(PreviewDimensionBudgetV2.admits(1_024, 1_024));
        assertFalse(PreviewDimensionBudgetV2.admits(1_025, 1_024)); // dimension ceiling, not cell budget
    }

    @Test
    void mediumScaleAndExportBudgetsAdmitOneThousandTwentyFourSquare() {
        Map<String, Object> snapshot = Medium1024OfflineBudgetProbeV2.admissionSnapshot();
        assertEquals(1_048_576L, snapshot.get("cells"));
        assertEquals(true, snapshot.get("previewAdmits"));
        assertEquals(true, snapshot.get("macroRasterAdmits"));
        assertEquals(true, snapshot.get("tileBudgetOk"));
        assertEquals(true, snapshot.get("residentBudgetOk"));
        assertEquals(64, snapshot.get("tileCount"));
        assertEquals(
                ScaleProfileV2.defaults(ScaleClassV2.MEDIUM).maximumTileCount(),
                TilePlanV2.of(1_024, 1_024, ScaleProfileV2.defaults(ScaleClassV2.MEDIUM)).tileCount());
        long estimated = (Long) snapshot.get("estimatedResidentBytes");
        long retained = (Long) snapshot.get("mediumRetainedBytes");
        assertTrue(estimated < retained, "descriptor resident " + estimated + " must fit MEDIUM retained "
                + retained);
        assertEquals(1_048_576L, PreviewDimensionBudgetV2.MAXIMUM_CELLS);
        assertEquals(1_048_576L, MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS);
    }

    @Test
    void previewDimensionBudgetAdmitsOneThousandTwentyFourSquareAtBoundary() {
        assertTrue(PreviewDimensionBudgetV2.admits(1_000, 1_000));
        assertTrue(PreviewDimensionBudgetV2.admits(1_024, 1_024));
        assertDoesNotThrow(() -> PreviewDimensionBudgetV2.requireAdmitted(1_024, 1_024, "coastal preview"));
        try {
            PreviewDimensionBudgetV2.requireAdmitted(1_025, 1_024, "coastal preview");
            throw new AssertionError("expected preview budget rejection above the MEDIUM ceiling");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("preview cell budget"), exception.getMessage());
            assertTrue(exception.getMessage().contains("1025x1024"), exception.getMessage());
        }
    }

    @Test
    void hydrologyGraphWorkBudgetAdmitsOneThousandTwentyFourSquareCells() {
        int fields = HydrologyPlanV2.FieldSemantic.values().length;
        assertDoesNotThrow(() ->
                new HydrologyPlanV2.GraphWorkBudget(
                        HydrologyPlanV2.GraphWorkBudget.VERSION,
                        1, 2, 2, 1, 1, fields,
                        1_048_576L, 1L, 1L));
        assertThrows(IllegalArgumentException.class, () ->
                new HydrologyPlanV2.GraphWorkBudget(
                        HydrologyPlanV2.GraphWorkBudget.VERSION,
                        1, 2, 2, 1, 1, fields,
                        1_048_577L, 1L, 1L));
    }

    @Test
    void heavyProbeWritesCommittedEvidenceWhenConfigured(@TempDir Path temp) throws Exception {
        String configured = firstNonBlank(
                System.getProperty("landformcraft.v21303.evidenceDir"),
                System.getenv("LANDFORMCRAFT_V21303_EVIDENCE_DIR"));
        Assumptions.assumeTrue(configured != null && !configured.isBlank(),
                "V2-13-03 evidence directory is not configured");

        Path evidenceDir = Path.of(configured).toAbsolutePath().normalize();
        Path work = temp.resolve("work");
        Medium1024OfflineBudgetProbeV2.ProbeReport report = Medium1024OfflineBudgetProbeV2.run(work);
        Medium1024OfflineBudgetProbeV2.writeEvidence(evidenceDir, report);

        assertTrue(report.scaleAdmissionOk());
        assertTrue(report.exportBudgetOk());
        assertTrue(report.previewBudgetAdmits());
        assertTrue(report.macroRasterBudgetAdmits());
        assertTrue(report.endToEndCompleted());
        assertEquals("NONE", report.blockingStage());
        assertTrue(Files.isRegularFile(evidenceDir.resolve("offline-budget-measurement.json")));
        assertTrue(Files.isRegularFile(evidenceDir.resolve("offline-budget-measurement.txt")));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
