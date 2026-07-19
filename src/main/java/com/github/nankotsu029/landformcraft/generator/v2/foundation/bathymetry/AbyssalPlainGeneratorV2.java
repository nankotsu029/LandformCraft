package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AbyssalPlainPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-10-04 abyssal-plain bathymetry. */
public final class AbyssalPlainGeneratorV2 {
    public static final String VERSION = "foundation-abyssal-plain-fixed-v1";

    private final AbyssalPlainPlanV2 plan;
    private final OceanBasinPlanV2 basinPlan;
    private final String seedNamespace;

    public AbyssalPlainGeneratorV2(AbyssalPlainPlanV2 plan, OceanBasinPlanV2 basinPlan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.basinPlan = Objects.requireNonNull(basinPlan, "basinPlan");
        this.seedNamespace = "abyssal:" + plan.featureId();
    }

    public AbyssalPlainMetrics evaluate() {
        boolean basinContainmentOk = plan.basinGeometryChecksum().matches("^[0-9a-f]{64}$")
                && plan.basinFeatureId().equals(basinPlan.featureId());
        boolean depthReliefOk = true;
        boolean slopeOk = true;
        boolean transitionOk = true;
        long owned = 0L;
        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                if (sampleOwnership(x, z) == 0) {
                    continue;
                }
                owned++;
                int depth = sampleDepth(x, z);
                int relief = sampleRelief(x, z);
                if (depth < 0 || depth > plan.selectedFloorDepthBlocksBelowSea()) {
                    depthReliefOk = false;
                }
                if (relief < 0 || relief > plan.selectedFloorReliefBlocks()) {
                    depthReliefOk = false;
                }
                if (relief > 0 && sampleSlope(x, z) > plan.selectedFloorReliefBlocks() + 1) {
                    slopeOk = false;
                }
                long px = cellCenter(x);
                long pz = cellCenter(z);
                if (!BathymetryFixedMathV2.contains(basinPlan.rings(), px, pz)) {
                    transitionOk = false;
                }
            }
        }
        boolean budgetOk = plan.estimatedRasterWorkUnits() <= AbyssalPlainPlanV2.MAXIMUM_RASTER_WORK_UNITS
                && owned > 0;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean exportOk = whole.length() == 64 && wholeTileOk;
        return new AbyssalPlainMetrics(
                basinContainmentOk, depthReliefOk, slopeOk, transitionOk, wholeTileOk, budgetOk, exportOk);
    }

    public int sampleOwnership(int x, int z) {
        long px = cellCenter(x);
        long pz = cellCenter(z);
        return BathymetryFixedMathV2.contains(plan.rings(), px, pz) ? 1 : 0;
    }

    public int sampleDepth(int x, int z) {
        if (sampleOwnership(x, z) == 0) {
            return 0;
        }
        long relief = BathymetryFixedMathV2.cellHash(seedNamespace, x, z)
                * plan.selectedFloorReliefBlocks() / 1_000_000L;
        return Math.max(0, plan.selectedFloorDepthBlocksBelowSea() - Math.toIntExact(relief));
    }

    public int sampleRelief(int x, int z) {
        if (sampleOwnership(x, z) == 0) {
            return 0;
        }
        return Math.toIntExact(BathymetryFixedMathV2.cellHash(seedNamespace + "|relief", x, z)
                * plan.selectedFloorReliefBlocks() / 1_000_000L);
    }

    public int sampleSlope(int x, int z) {
        if (sampleOwnership(x, z) == 0) {
            return 0;
        }
        int here = sampleDepth(x, z);
        int east = x + 1 < plan.width() ? sampleDepth(x + 1, z) : here;
        int north = z + 1 < plan.length() ? sampleDepth(x, z + 1) : here;
        return Math.max(Math.abs(here - east), Math.abs(here - north));
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
                    .append(sampleOwnership(x, z)).append(',')
                    .append(sampleDepth(x, z)).append(',')
                    .append(sampleRelief(x, z)).append(';');
        }
    }

    private static long cellCenter(int block) {
        return Math.addExact(Math.multiplyExact((long) block, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record AbyssalPlainMetrics(
            boolean basinContainmentOk,
            boolean depthReliefOk,
            boolean slopeOk,
            boolean transitionOk,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean exportOk
    ) {
    }
}
