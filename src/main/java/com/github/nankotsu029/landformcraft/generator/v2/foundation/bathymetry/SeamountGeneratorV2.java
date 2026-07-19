package com.github.nankotsu029.landformcraft.generator.v2.foundation.bathymetry;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OceanBasinPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SeamountPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only relief-cone sampler and export checksums for V2-10-04 seamount bathymetry. */
public final class SeamountGeneratorV2 {
    public static final String VERSION = "foundation-seamount-fixed-v1";

    private final SeamountPlanV2 plan;
    private final OceanBasinPlanV2 basinPlan;

    public SeamountGeneratorV2(SeamountPlanV2 plan, OceanBasinPlanV2 basinPlan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.basinPlan = Objects.requireNonNull(basinPlan, "basinPlan");
    }

    public SeamountMetrics evaluate() {
        boolean basinContainmentOk = plan.basinGeometryChecksum().matches("^[0-9a-f]{64}$")
                && plan.basinFeatureId().equals(basinPlan.featureId());
        boolean depthReliefOk = plan.selectedReliefBlocks() > 0 && plan.selectedBaseRadiusBlocks() > 0;
        boolean slopeOk = plan.selectedReliefBlocks() > 0 && plan.selectedBaseRadiusBlocks() > 0;
        boolean transitionOk = BathymetryFixedMathV2.contains(
                basinPlan.rings(), plan.centerXMillionths(), plan.centerZMillionths());
        long owned = 0L;
        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                if (sampleOwnership(x, z) == 0) {
                    continue;
                }
                owned++;
                if (sampleRelief(x, z) <= 0) {
                    depthReliefOk = false;
                }
            }
        }
        boolean budgetOk = plan.estimatedRasterWorkUnits() <= SeamountPlanV2.MAXIMUM_RASTER_WORK_UNITS
                && owned > 0;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean exportOk = whole.length() == 64 && wholeTileOk;
        return new SeamountMetrics(
                basinContainmentOk, depthReliefOk, slopeOk, transitionOk, wholeTileOk, budgetOk, exportOk);
    }

    public int sampleOwnership(int x, int z) {
        return distanceBlocks(x, z) <= plan.selectedBaseRadiusBlocks() ? 1 : 0;
    }

    public int sampleRelief(int x, int z) {
        int distance = distanceBlocks(x, z);
        if (distance > plan.selectedBaseRadiusBlocks()) {
            return 0;
        }
        int taper = Math.max(0, plan.selectedBaseRadiusBlocks() - distance);
        return Math.max(1, plan.selectedReliefBlocks() * taper / Math.max(1, plan.selectedBaseRadiusBlocks()));
    }

    public int sampleDepth(int x, int z) {
        if (sampleOwnership(x, z) == 0) {
            return 0;
        }
        return Math.max(0, plan.selectedSummitDepthBlocksBelowSea() - sampleRelief(x, z));
    }

    public int sampleSlope(int x, int z) {
        if (sampleOwnership(x, z) == 0) {
            return 0;
        }
        int here = sampleRelief(x, z);
        int east = x + 1 < plan.width() ? sampleRelief(x + 1, z) : here;
        int north = z + 1 < plan.length() ? sampleRelief(x, z + 1) : here;
        return Math.max(Math.abs(here - east), Math.abs(here - north));
    }

    public String exportChecksum() {
        return streamExport(false);
    }

    public String tileExportChecksum() {
        return streamExport(true);
    }

    private int distanceBlocks(int x, int z) {
        long px = cellCenter(x);
        long pz = cellCenter(z);
        long dx = px - plan.centerXMillionths();
        long dz = pz - plan.centerZMillionths();
        long hypot = BathymetryFixedMathV2.hypot(dx, dz);
        return Math.toIntExact(Math.max(0L, hypot / TerrainIntentV2.FIXED_SCALE));
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
                    .append(sampleRelief(x, z)).append(',')
                    .append(sampleDepth(x, z)).append(';');
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

    public record SeamountMetrics(
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
