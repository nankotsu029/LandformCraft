package com.github.nankotsu029.landformcraft.generator.v2.foundation.escarpment;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.EscarpmentPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only global-X/Z rasterizer for the V2-10-06 escarpment foundation profile. */
public final class EscarpmentGeneratorV2 {
    public static final String VERSION = "foundation-escarpment-fixed-v1";
    private static final long MINIMUM_LONG_SCARP_ARC = Math.multiplyExact(24L, TerrainIntentV2.FIXED_SCALE);

    private final EscarpmentPlanV2 plan;
    private final long faceRadius;
    private final long talusOuterRadius;
    private final long floorOuterRadius;

    public EscarpmentGeneratorV2(EscarpmentPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        faceRadius = Math.multiplyExact((long) plan.selectedTalusWidthBlocks(), TerrainIntentV2.FIXED_SCALE);
        talusOuterRadius = Math.multiplyExact(
                (long) plan.selectedTalusWidthBlocks() * 2L, TerrainIntentV2.FIXED_SCALE);
        floorOuterRadius = Math.multiplyExact(
                (long) plan.selectedFloorDropBlocks() + plan.selectedTalusWidthBlocks(),
                TerrainIntentV2.FIXED_SCALE);
    }

    public EscarpmentPlanV2 plan() {
        return plan;
    }

    public int width() {
        return plan.width();
    }

    public int length() {
        return plan.length();
    }

    public EscarpmentSample sampleAt(int x, int z) {
        if (x < 0 || x >= width() || z < 0 || z >= length()) {
            throw new FoundationSliceException("v2.escarpment-bounds", "sample outside bounds");
        }
        long px = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long pz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
        long distance = nearestCenterlineDistance(px, pz);
        boolean face = distance <= faceRadius;
        boolean talus = distance > faceRadius && distance <= talusOuterRadius;
        boolean floor = isDropSideOfFace(px, pz) && distance > faceRadius && distance <= floorOuterRadius;
        if (!face && !talus && !floor) {
            return EscarpmentSample.outside();
        }
        int elevation = face
                ? plan.selectedScarpHeightBlocks()
                : talus ? plan.selectedScarpHeightBlocks() / 3 : 0;
        return new EscarpmentSample(
                face ? 1 : 0,
                talus ? 1 : 0,
                floor ? 1 : 0,
                (face || talus || floor) ? 1 : 0,
                elevation);
    }

    public EscarpmentMetrics evaluate() {
        long faceCells = 0L;
        long talusCells = 0L;
        long floorCells = 0L;
        long ownerCells = 0L;
        for (int z = 0; z < length(); z++) {
            for (int x = 0; x < width(); x++) {
                EscarpmentSample sample = sampleAt(x, z);
                faceCells += sample.faceMask();
                talusCells += sample.talusMask();
                floorCells += sample.floorMask();
                ownerCells += sample.ownership();
            }
        }
        boolean longScarp = plan.centerline().getLast().arcLengthMillionths() >= MINIMUM_LONG_SCARP_ARC;
        boolean budgetOk = plan.estimatedRasterWorkUnits() <= EscarpmentPlanV2.MAXIMUM_RASTER_WORK_UNITS
                && ownerCells > 0;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean exportOk = whole.length() == 64 && wholeTileOk;
        boolean materialHandoffOk = !plan.materialHandoffFieldId().isBlank()
                && !plan.talusMaskFieldId().isBlank()
                && talusCells > 0;
        return new EscarpmentMetrics(
                longScarp && ownerCells > 0,
                faceCells > 0 && talusCells > 0 && floorCells > 0,
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
            EscarpmentSample sample = sampleAt(x, z);
            builder.append(x).append(',').append(z).append(':')
                    .append(sample.faceMask()).append('/')
                    .append(sample.talusMask()).append('/')
                    .append(sample.floorMask()).append('/')
                    .append(sample.ownership()).append(';');
        }
    }

    private boolean isDropSideOfFace(long px, long pz) {
        EscarpmentPlanV2.CenterlinePoint mid = plan.centerline().get(plan.centerline().size() / 2);
        return switch (plan.dropSide()) {
            case WEST -> px < mid.xMillionths();
            case EAST -> px > mid.xMillionths();
            case NORTH -> pz < mid.zMillionths();
            case SOUTH -> pz > mid.zMillionths();
        };
    }

    private long nearestCenterlineDistance(long px, long pz) {
        long best = Long.MAX_VALUE;
        for (int i = 1; i < plan.centerline().size(); i++) {
            EscarpmentPlanV2.CenterlinePoint a = plan.centerline().get(i - 1);
            EscarpmentPlanV2.CenterlinePoint b = plan.centerline().get(i);
            best = Math.min(best, distanceToSegment(px, pz,
                    a.xMillionths(), a.zMillionths(), b.xMillionths(), b.zMillionths()));
        }
        return best;
    }

    private static long distanceToSegment(long px, long pz, long ax, long az, long bx, long bz) {
        long dx = bx - ax;
        long dz = bz - az;
        if (dx == 0L && dz == 0L) {
            return EscarpmentFixedMathV2.hypot(px - ax, pz - az);
        }
        long tNumerator = Math.addExact(
                Math.multiplyExact(px - ax, dx),
                Math.multiplyExact(pz - az, dz));
        long tDenominator = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long t = Math.max(0L, Math.min(tDenominator, tNumerator));
        long projX = ax + dx * t / tDenominator;
        long projZ = az + dz * t / tDenominator;
        return EscarpmentFixedMathV2.hypot(px - projX, pz - projZ);
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record EscarpmentSample(
            int faceMask,
            int talusMask,
            int floorMask,
            int ownership,
            int elevationBlocks
    ) {
        public static EscarpmentSample outside() {
            return new EscarpmentSample(0, 0, 0, 0, 0);
        }

        public boolean active() {
            return ownership == 1;
        }
    }

    public record EscarpmentMetrics(
            boolean longScarpOwnerOk,
            boolean capFloorTalusOk,
            boolean materialHandoffOk,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean exportOk
    ) {
    }
}
