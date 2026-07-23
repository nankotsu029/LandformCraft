package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.v2.export.ExportBudgetV2;
import com.github.nankotsu029.landformcraft.core.v2.scale.ScaleAdmissionDecisionV2;
import com.github.nankotsu029.landformcraft.core.v2.scale.ScaleAdmissionV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;
import com.github.nankotsu029.landformcraft.preview.v2.PreviewDimensionBudgetV2;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * V2-13-03 offline budget probe for 1024×1024 MEDIUM generation.
 *
 * <p>Walks generate → validation → preview under the MEDIUM／Export／preview budgets and records
 * wall time, heap delta, and disk usage per stage. {@link PreviewDimensionBudgetV2#MAXIMUM_CELLS}
 * and the other per-plan {@code globalCellCount} budgets were raised (cell-budget follow-up to
 * V2-13-03) to the MEDIUM ceiling squared, so 1024² now completes end to end.</p>
 */
final class Medium1024OfflineBudgetProbeV2 {
    static final int WIDTH = 1_024;
    static final int LENGTH = 1_024;
    static final long CELLS = (long) WIDTH * (long) LENGTH;
    /** Matches {@code CoastalSurfaceFieldsV2.estimatedResidentBytes} (13 descriptor fields + 3). */
    private static final long DESCRIPTOR_INT_ARRAYS = 16L;

    private Medium1024OfflineBudgetProbeV2() {
    }

    record StageResult(
            String stageId,
            String outcome,
            long wallNanos,
            long heapUsedBytesBefore,
            long heapUsedBytesAfter,
            long heapDeltaBytes,
            long diskBytesAfterStage,
            String detail
    ) {
    }

    record ProbeReport(
            int width,
            int length,
            long cells,
            boolean previewBudgetAdmits,
            boolean macroRasterBudgetAdmits,
            boolean scaleAdmissionOk,
            boolean exportBudgetOk,
            long estimatedDescriptorResidentBytes,
            long mediumRetainedBytes,
            int tileCount,
            long mediumMaximumTileCount,
            List<StageResult> stages,
            String blockingStage,
            String blockingDetail,
            boolean endToEndCompleted
    ) {
    }

    static ProbeReport run(Path workRoot) throws Exception {
        Files.createDirectories(workRoot);
        List<StageResult> stages = new ArrayList<>();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        ScaleProfileV2 medium = ScaleProfileV2.defaults(ScaleClassV2.MEDIUM);
        TilePlanV2 tilePlan = TilePlanV2.of(WIDTH, LENGTH, medium);
        long estimatedResident = Math.multiplyExact(CELLS, DESCRIPTOR_INT_ARRAYS * Integer.BYTES);
        boolean previewAdmits = PreviewDimensionBudgetV2.admits(WIDTH, LENGTH);
        boolean macroAdmits = CELLS <= MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS;

        String blockingStage = null;
        String blockingDetail = null;

        StageResult admission = timed(memory, workRoot, "SCALE_EXPORT_ADMISSION", () -> {
            ScaleAdmissionDecisionV2 decision = ScaleAdmissionV2.admit(WIDTH, LENGTH, medium);
            if (decision.tilePlan().tileCount() != tilePlan.tileCount()) {
                throw new IllegalStateException("tile plan mismatch");
            }
            ExportBudgetV2 budget = ExportBudgetV2.defaults();
            if (tilePlan.tileCount() > Math.min(budget.maximumTileCount(), medium.maximumTileCount())) {
                throw new IOException("Release 2 export tile count exceeds its budget: " + tilePlan.tileCount());
            }
            if (estimatedResident > Math.min(budget.maximumResidentBytes(), medium.maximumRetainedBytes())) {
                throw new IOException("Release 2 export descriptor working set exceeds its budget: "
                        + estimatedResident);
            }
            return StageBodyResult.pass("scale+export admitted; tiles=" + tilePlan.tileCount()
                    + " estimatedResidentBytes=" + estimatedResident);
        });
        stages.add(admission);
        boolean scaleOk = "PASS".equals(admission.outcome());
        boolean exportOk = scaleOk;
        if (!scaleOk) {
            blockingStage = admission.stageId();
            blockingDetail = admission.detail();
        }

        if (scaleOk) {
            StageResult fields = timed(memory, workRoot, "FIELD_SIDECARS_BLUEPRINT_VALIDATION_PREVIEW", () -> {
                Path source = workRoot.resolve("source-pre-preview");
                // Reuse the measurement fixture builder; catch the first fail-closed cell budget gate.
                try {
                    MeasurementSurfaceFixtureV2.build(
                            source, "v2-13-03-measure-1024", WIDTH, LENGTH, 0, 1);
                    return StageBodyResult.pass("full fixture completed: field sidecars, Blueprint compile, "
                            + "coastal validation artifact, preview render, and all tiles");
                } catch (Exception exception) {
                    String message = exception.getMessage() == null ? exception.toString() : exception.getMessage();
                    String classified = classifyCellBudgetMessage(message);
                    if (classified != null) {
                        return StageBodyResult.reject(classified + ": " + message);
                    }
                    Throwable cursor = exception;
                    while (cursor != null) {
                        String nested = cursor.getMessage() == null ? "" : cursor.getMessage();
                        classified = classifyCellBudgetMessage(nested);
                        if (classified != null) {
                            return StageBodyResult.reject(classified + ": " + nested);
                        }
                        cursor = cursor.getCause();
                    }
                    throw exception;
                }
            });
            stages.add(fields);
            if ("REJECT".equals(fields.outcome())) {
                blockingStage = blockingStageId(fields.detail());
                blockingDetail = fields.detail();
            } else if (!"PASS".equals(fields.outcome())) {
                blockingStage = fields.stageId();
                blockingDetail = fields.detail();
            } else {
                blockingStage = "NONE";
                blockingDetail = "full fixture completed end to end; no cell budget gate rejected 1024²";
            }
        }

        boolean completed = stages.stream().allMatch(stage -> "PASS".equals(stage.outcome()))
                && previewAdmits;
        return new ProbeReport(
                WIDTH,
                LENGTH,
                CELLS,
                previewAdmits,
                macroAdmits,
                scaleOk,
                exportOk,
                estimatedResident,
                medium.maximumRetainedBytes(),
                tilePlan.tileCount(),
                medium.maximumTileCount(),
                List.copyOf(stages),
                blockingStage == null ? "UNKNOWN" : blockingStage,
                blockingDetail == null ? "" : blockingDetail,
                completed);
    }

    private static boolean isPreviewBudgetMessage(String message) {
        return (message.contains("preview") && message.contains("budget"))
                || message.contains("exceed its index budget")
                || message.contains("preview cell budget")
                || message.contains("preview PNG dimensions exceed");
    }

    /**
     * Returns a stable gate id when {@code message} is a frozen 1_000_000-cell (or equivalent)
     * MEDIUM outer budget rejection; otherwise null.
     */
    private static String classifyCellBudgetMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        if (message.contains("hydrology graph/work budget is outside trusted bounds")
                || message.contains("globalCellCount")) {
            return "HYDROLOGY_GRAPH_WORK_BUDGET";
        }
        if (message.contains("outside trusted bounds") && message.toLowerCase(java.util.Locale.ROOT).contains("budget")) {
            return "PLAN_RESOURCE_BUDGET";
        }
        if (isPreviewBudgetMessage(message)) {
            return "PREVIEW_CELL_BUDGET";
        }
        if (message.contains("MAXIMUM_RASTER_CELLS") || message.contains("raster cells")) {
            return "MACRO_RASTER_CELL_BUDGET";
        }
        return null;
    }

    private static String blockingStageId(String detail) {
        if (detail.startsWith("HYDROLOGY_GRAPH_WORK_BUDGET")) {
            return "HYDROLOGY_GRAPH_WORK_BUDGET";
        }
        if (detail.startsWith("PLAN_RESOURCE_BUDGET")) {
            return "PLAN_RESOURCE_BUDGET";
        }
        if (detail.startsWith("PREVIEW_CELL_BUDGET")) {
            return "PREVIEW_CELL_BUDGET";
        }
        if (detail.startsWith("MACRO_RASTER_CELL_BUDGET")) {
            return "MACRO_RASTER_CELL_BUDGET";
        }
        return "CELL_BUDGET_GATE";
    }

    static String toJson(ProbeReport report) {
        StringBuilder json = new StringBuilder(2_048);
        json.append("{\n");
        json.append("  \"taskId\": \"V2-13-03\",\n");
        json.append("  \"contract\": \"offline-generation-budget-measurement-v1\",\n");
        json.append("  \"width\": ").append(report.width()).append(",\n");
        json.append("  \"length\": ").append(report.length()).append(",\n");
        json.append("  \"cells\": ").append(report.cells()).append(",\n");
        json.append("  \"previewBudgetMaximumCells\": ").append(PreviewDimensionBudgetV2.MAXIMUM_CELLS)
                .append(",\n");
        json.append("  \"previewBudgetAdmits\": ").append(report.previewBudgetAdmits()).append(",\n");
        json.append("  \"macroRasterBudgetMaximumCells\": ")
                .append(MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS).append(",\n");
        json.append("  \"macroRasterBudgetAdmits\": ").append(report.macroRasterBudgetAdmits()).append(",\n");
        json.append("  \"scaleAdmissionOk\": ").append(report.scaleAdmissionOk()).append(",\n");
        json.append("  \"exportBudgetOk\": ").append(report.exportBudgetOk()).append(",\n");
        json.append("  \"estimatedDescriptorResidentBytes\": ")
                .append(report.estimatedDescriptorResidentBytes()).append(",\n");
        json.append("  \"mediumRetainedBytes\": ").append(report.mediumRetainedBytes()).append(",\n");
        json.append("  \"tileCount\": ").append(report.tileCount()).append(",\n");
        json.append("  \"mediumMaximumTileCount\": ").append(report.mediumMaximumTileCount()).append(",\n");
        json.append("  \"blockingStage\": ").append(quote(report.blockingStage())).append(",\n");
        json.append("  \"blockingDetail\": ").append(quote(report.blockingDetail())).append(",\n");
        json.append("  \"endToEndCompleted\": ").append(report.endToEndCompleted()).append(",\n");
        json.append("  \"stages\": [\n");
        for (int i = 0; i < report.stages().size(); i++) {
            StageResult stage = report.stages().get(i);
            json.append("    {\n");
            json.append("      \"stageId\": ").append(quote(stage.stageId())).append(",\n");
            json.append("      \"outcome\": ").append(quote(stage.outcome())).append(",\n");
            json.append("      \"wallNanos\": ").append(stage.wallNanos()).append(",\n");
            json.append("      \"wallSeconds\": ")
                    .append(String.format(java.util.Locale.ROOT, "%.3f", stage.wallNanos() / 1_000_000_000.0))
                    .append(",\n");
            json.append("      \"heapUsedBytesBefore\": ").append(stage.heapUsedBytesBefore()).append(",\n");
            json.append("      \"heapUsedBytesAfter\": ").append(stage.heapUsedBytesAfter()).append(",\n");
            json.append("      \"heapDeltaBytes\": ").append(stage.heapDeltaBytes()).append(",\n");
            json.append("      \"diskBytesAfterStage\": ").append(stage.diskBytesAfterStage()).append(",\n");
            json.append("      \"detail\": ").append(quote(stage.detail())).append('\n');
            json.append("    }");
            if (i + 1 < report.stages().size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    static void writeEvidence(Path directory, ProbeReport report) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("offline-budget-measurement.json"), toJson(report),
                StandardCharsets.UTF_8);
        StringBuilder text = new StringBuilder();
        text.append("V2-13-03 1024x1024 offline generation budget measurement\n");
        text.append("cells=").append(report.cells())
                .append(" previewAdmits=").append(report.previewBudgetAdmits())
                .append(" macroRasterAdmits=").append(report.macroRasterBudgetAdmits()).append('\n');
        text.append("scaleAdmissionOk=").append(report.scaleAdmissionOk())
                .append(" exportBudgetOk=").append(report.exportBudgetOk()).append('\n');
        text.append("estimatedDescriptorResidentBytes=").append(report.estimatedDescriptorResidentBytes())
                .append(" mediumRetainedBytes=").append(report.mediumRetainedBytes()).append('\n');
        text.append("tileCount=").append(report.tileCount()).append('/')
                .append(report.mediumMaximumTileCount()).append('\n');
        text.append("blockingStage=").append(report.blockingStage()).append('\n');
        text.append("blockingDetail=").append(report.blockingDetail()).append('\n');
        text.append("endToEndCompleted=").append(report.endToEndCompleted()).append('\n');
        for (StageResult stage : report.stages()) {
            text.append(stage.stageId()).append(' ').append(stage.outcome())
                    .append(" wallSeconds=")
                    .append(String.format(java.util.Locale.ROOT, "%.3f", stage.wallNanos() / 1_000_000_000.0))
                    .append(" heapDeltaBytes=").append(stage.heapDeltaBytes())
                    .append(" diskBytes=").append(stage.diskBytesAfterStage()).append('\n');
        }
        Files.writeString(directory.resolve("offline-budget-measurement.txt"), text.toString(),
                StandardCharsets.UTF_8);
    }

    private static StageResult timed(
            MemoryMXBean memory,
            Path workRoot,
            String stageId,
            StageBody body
    ) {
        System.gc();
        long before = memory.getHeapMemoryUsage().getUsed();
        long started = System.nanoTime();
        String outcome;
        String detail;
        try {
            StageBodyResult result = body.run();
            outcome = result.outcome();
            detail = result.detail();
        } catch (Exception exception) {
            outcome = "FAIL";
            detail = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        }
        long wall = System.nanoTime() - started;
        long after = memory.getHeapMemoryUsage().getUsed();
        long disk;
        try {
            disk = directorySize(workRoot);
        } catch (IOException exception) {
            disk = -1L;
        }
        return new StageResult(stageId, outcome, wall, before, after, after - before, disk, detail);
    }

    private static long directorySize(Path root) throws IOException {
        if (!Files.exists(root)) {
            return 0L;
        }
        AtomicLong total = new AtomicLong();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                total.addAndGet(attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        return total.get();
    }

    private static String quote(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    @FunctionalInterface
    private interface StageBody {
        StageBodyResult run() throws Exception;
    }

    private record StageBodyResult(String outcome, String detail) {
        static StageBodyResult pass(String detail) {
            return new StageBodyResult("PASS", detail);
        }

        static StageBodyResult reject(String detail) {
            return new StageBodyResult("REJECT", detail);
        }
    }

    /** Admission-only summary used by fast unit tests (no heavy fixture build). */
    static Map<String, Object> admissionSnapshot() {
        ScaleProfileV2 medium = ScaleProfileV2.defaults(ScaleClassV2.MEDIUM);
        TilePlanV2 tilePlan = TilePlanV2.of(WIDTH, LENGTH, medium);
        long estimatedResident = Math.multiplyExact(CELLS, DESCRIPTOR_INT_ARRAYS * Integer.BYTES);
        ExportBudgetV2 budget = ExportBudgetV2.defaults();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("cells", CELLS);
        snapshot.put("previewAdmits", PreviewDimensionBudgetV2.admits(WIDTH, LENGTH));
        snapshot.put("macroRasterAdmits", CELLS <= MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS);
        snapshot.put("scaleAdmission", ScaleAdmissionV2.admit(WIDTH, LENGTH, medium).admissionVersion());
        snapshot.put("tileCount", tilePlan.tileCount());
        snapshot.put("tileBudgetOk",
                tilePlan.tileCount() <= Math.min(budget.maximumTileCount(), medium.maximumTileCount()));
        snapshot.put("residentBudgetOk",
                estimatedResident <= Math.min(budget.maximumResidentBytes(), medium.maximumRetainedBytes()));
        snapshot.put("estimatedResidentBytes", estimatedResident);
        snapshot.put("mediumRetainedBytes", medium.maximumRetainedBytes());
        return snapshot;
    }
}
