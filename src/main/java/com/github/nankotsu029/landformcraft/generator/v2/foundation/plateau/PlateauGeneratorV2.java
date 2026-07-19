package com.github.nankotsu029.landformcraft.generator.v2.foundation.plateau;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlateauPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-10-06 plateau foundation profile. */
public final class PlateauGeneratorV2 {
    public static final String VERSION = "foundation-plateau-fixed-v1";

    private final PlateauPlanV2 plan;
    private final String seedNamespace;

    public PlateauGeneratorV2(PlateauPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.seedNamespace = "plateau:" + plan.featureId() + ":" + plan.plateauProfile().name();
    }

    public PlateauPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public PlateauSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.plateau-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        if (!PlateauFixedMathV2.contains(plan.rings(), px, pz)) {
            return PlateauSample.outside();
        }
        int relief = capReliefVariationBlocks(x, z);
        int elevation = plan.selectedCapElevationBlocks() + relief;
        return new PlateauSample(1, 1, elevation, relief);
    }

    public PlateauMetrics evaluate() {
        long capCells = 0L;
        long ownerCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                PlateauSample sample = sampleAt(x, z);
                capCells += sample.capMask();
                ownerCells += sample.ownership();
            }
        }
        boolean budgetOk = plan.estimatedRasterWorkUnits() <= PlateauPlanV2.MAXIMUM_RASTER_WORK_UNITS
                && ownerCells > 0;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean exportOk = whole.length() == 64 && wholeTileOk;
        boolean materialHandoffOk = !plan.materialHandoffFieldId().isBlank()
                && !plan.capMaskFieldId().isBlank()
                && capCells > 0;
        return new PlateauMetrics(
                capCells > 0,
                materialHandoffOk,
                wholeTileOk,
                budgetOk,
                exportOk);
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
            PlateauSample sample = sampleAt(x, z);
            builder.append(x).append(',').append(z).append(':')
                    .append(sample.capMask()).append('/')
                    .append(sample.ownership()).append('/')
                    .append(sample.elevationBlocks()).append(';');
        }
    }

    private int capReliefVariationBlocks(int x, int z) {
        if (plan.selectedCapReliefBlocks() == 0) {
            return profileBiasBlocks(x, z);
        }
        long hash = PlateauFixedMathV2.cellHash(seedNamespace + "|relief", x, z);
        int variation = (int) (hash * (long) (plan.selectedCapReliefBlocks() + 1) / 1_000_000L);
        return Math.min(variation, plan.selectedCapReliefBlocks()) + profileBiasBlocks(x, z);
    }

    private int profileBiasBlocks(int x, int z) {
        return switch (plan.plateauProfile()) {
            case MESA -> 0;
            case BUTTE -> -Math.min(2, plan.selectedCapReliefBlocks());
            case GENERIC -> (x + z) % 2;
        };
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record PlateauSample(
            int capMask,
            int ownership,
            int elevationBlocks,
            int reliefBlocks
    ) {
        public static PlateauSample outside() {
            return new PlateauSample(0, 0, 0, 0);
        }

        public boolean active() {
            return ownership == 1;
        }
    }

    public record PlateauMetrics(
            boolean capOwnerOk,
            boolean materialHandoffOk,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean exportOk
    ) {
    }
}
