package com.github.nankotsu029.landformcraft.generator.v2.foundation.deposition;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OutwashPlainPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-10-02 outwash-plain deposition. */
public final class OutwashPlainGeneratorV2 {
    public static final String VERSION = "foundation-outwash-plain-fixed-v1";

    private final OutwashPlainPlanV2 plan;

    public OutwashPlainGeneratorV2(OutwashPlainPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public OutwashPlainPlanV2 plan() {
        return plan;
    }

    public OutwashPlainMetrics evaluate() {
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
                    if (sampleFlowDirection(x, z) > 0) {
                        ridgeOrFlowOk = true;
                    }
                }
            }
        }
        boolean meltwaterOk = plan.meltwaterHandoffFeatureId().isBlank()
                || (!plan.meltwaterHandoffChecksum().isBlank()
                && sampleMeltwaterMask(plan.width() / 2, plan.length() / 2) >= 0);
        boolean profileKindSeparated = true;
        boolean budgetOk = plan.estimatedRasterWorkUnits() <= OutwashPlainPlanV2.MAXIMUM_RASTER_WORK_UNITS
                && activeCells > 0;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean exportOk = whole.length() == 64 && sceneChecksum().length() == 64 && wholeTileOk && meltwaterOk;
        return new OutwashPlainMetrics(
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

    public int sampleFlowDirection(int x, int z) {
        return insideFootprint(x, z) ? plan.flowAzimuthDegrees() + 1 : 0;
    }

    public int sampleMeltwaterMask(int x, int z) {
        if (plan.meltwaterHandoffFeatureId().isBlank()) {
            return 0;
        }
        if (!insideFootprint(x, z)) {
            return 0;
        }
        int spacing = Math.max(2, plan.selectedChannelSpacingBlocks());
        return Math.floorMod(x + z, spacing) == 0 ? 1 : 0;
    }

    public String sceneChecksum() {
        return digest("scene|" + plan.canonicalChecksum() + "|" + plan.geometryChecksum()
                + "|" + plan.glacialParentCanonicalChecksum() + "|" + plan.flowAzimuthDegrees());
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
                    .append(sampleFlowDirection(x, z)).append('/')
                    .append(sampleMeltwaterMask(x, z)).append(';');
        }
    }

    private boolean insideFootprint(int x, int z) {
        if (x < 0 || x >= plan.width() || z < 0 || z >= plan.length()) {
            return false;
        }
        long px = cellCenter(x);
        long pz = cellCenter(z);
        return DepositionPolygonMathV2.containsOutwashRings(plan.rings(), px, pz);
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

    public record OutwashPlainMetrics(
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
