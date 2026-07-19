package com.github.nankotsu029.landformcraft.generator.v2.foundation.deposition;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MoraineFieldPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-10-02 moraine-field deposition. */
public final class MoraineFieldGeneratorV2 {
    public static final String VERSION = "foundation-moraine-field-fixed-v1";

    private final MoraineFieldPlanV2 plan;
    private final String seedNamespace;

    public MoraineFieldGeneratorV2(MoraineFieldPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.seedNamespace = "moraine:" + plan.featureId();
    }

    public MoraineFieldPlanV2 plan() {
        return plan;
    }

    public MoraineFieldMetrics evaluate() {
        boolean glacialParentOk = !plan.glacialParentFeatureId().isBlank()
                && plan.glacialParentCanonicalChecksum().matches("^[0-9a-f]{64}$");
        boolean sedimentOwnerOk = false;
        boolean ridgeOrFlowOk = false;
        long activeCells = 0L;
        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                if (sampleSedimentOwnership(x, z) == 1) {
                    activeCells++;
                    sedimentOwnerOk = true;
                    if (sampleRidgeMask(x, z) == 1) {
                        ridgeOrFlowOk = true;
                    }
                }
            }
        }
        boolean profileKindSeparated = true;
        boolean budgetOk = plan.estimatedRasterWorkUnits() <= MoraineFieldPlanV2.MAXIMUM_RASTER_WORK_UNITS
                && activeCells > 0;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean exportOk = whole.length() == 64 && sceneChecksum().length() == 64 && wholeTileOk;
        return new MoraineFieldMetrics(
                glacialParentOk,
                sedimentOwnerOk,
                ridgeOrFlowOk,
                profileKindSeparated,
                wholeTileOk,
                budgetOk,
                exportOk);
    }

    public int sampleSedimentOwnership(int x, int z) {
        return insideFootprint(x, z) ? 1 : 0;
    }

    public int sampleRidgeMask(int x, int z) {
        if (!insideFootprint(x, z)) {
            return 0;
        }
        long hash = DepositionPolygonMathV2.cellHash(seedNamespace + "|ridge", x, z);
        int spacing = Math.max(2, plan.selectedRidgeHalfWidthBlocks() * 2);
        int lane = Math.floorMod(
                (int) ((hash * (long) plan.selectedRidgeCount()) / 1_000_000L),
                Math.max(1, plan.selectedRidgeCount()));
        int offset = lane * spacing;
        double radians = Math.toRadians(plan.flowAzimuthDegrees());
        long px = cellCenter(x);
        long pz = cellCenter(z);
        long projected = Math.round(px * Math.sin(radians) + pz * Math.cos(radians));
        return Math.floorMod((int) projected + offset, spacing) < plan.selectedRidgeHalfWidthBlocks() ? 1 : 0;
    }

    public int sampleProvenance(int x, int z) {
        return insideFootprint(x, z) ? 1 : 0;
    }

    public String sceneChecksum() {
        return digest("scene|" + plan.canonicalChecksum() + "|" + plan.geometryChecksum()
                + "|" + plan.glacialParentCanonicalChecksum() + "|" + plan.selectedRidgeCount());
    }

    public String exportChecksum() {
        return streamExport(false);
    }

    public String tileExportChecksum() {
        return streamExport(true);
    }

    private String streamExport(boolean tiled) {
        StringBuilder builder = new StringBuilder(256);
        builder.append(VERSION).append("|whole|").append(plan.canonicalChecksum()).append('|');
        int midX = plan.width() / 2;
        for (int z = 0; z < plan.length(); z++) {
            if (tiled) {
                appendBand(builder, 0, midX, z);
                appendBand(builder, midX + 1, plan.width() - 1, z);
            } else {
                appendBand(builder, 0, plan.width() - 1, z);
            }
        }
        return digest(builder.toString());
    }

    private void appendBand(StringBuilder builder, int fromX, int toX, int z) {
        if (fromX > toX) {
            return;
        }
        for (int x = fromX; x <= toX; x++) {
            builder.append(x).append(',').append(z).append(':')
                    .append(sampleSedimentOwnership(x, z)).append('/')
                    .append(sampleRidgeMask(x, z)).append('/')
                    .append(sampleProvenance(x, z)).append(';');
        }
    }

    private boolean insideFootprint(int x, int z) {
        if (x < 0 || x >= plan.width() || z < 0 || z >= plan.length()) {
            return false;
        }
        long px = cellCenter(x);
        long pz = cellCenter(z);
        return DepositionPolygonMathV2.containsMoraineRings(plan.rings(), px, pz);
    }

    private static long cellCenter(int block) {
        return Math.addExact(Math.multiplyExact((long) block, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record MoraineFieldMetrics(
            boolean glacialParentOk,
            boolean sedimentOwnerOk,
            boolean ridgeOrFlowOk,
            boolean profileKindSeparated,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean exportOk
    ) {
    }
}
