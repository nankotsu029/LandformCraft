package com.github.nankotsu029.landformcraft.generator.v2.foundation.glacial;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.GlacialIcePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-10-01 glacial ice (sparse ADD_SOLID). */
public final class GlacialIceGeneratorV2 {
    public static final String VERSION = "foundation-glacial-ice-fixed-v1";

    private final GlacialIcePlanV2 plan;

    public GlacialIceGeneratorV2(GlacialIcePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public GlacialIcePlanV2 plan() {
        return plan;
    }

    public GlacialIceMetrics evaluate() {
        boolean coldClimateOk = GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(plan.climatePreset());
        boolean bedContactOk = sampleBedContact(
                blockOf(plan.headXMillionths()), blockOf(plan.headZMillionths())) == 1
                && sampleBedContact(
                        blockOf(plan.terminusXMillionths()), blockOf(plan.terminusZMillionths())) == 1;
        boolean flowTerminusOk = plan.headXMillionths() != plan.terminusXMillionths()
                || plan.headZMillionths() != plan.terminusZMillionths();
        boolean sparseIceOk = plan.orderedOps().stream()
                .allMatch(op -> GlacialIcePlanV2.OrderedVolumeOp.ADD_SOLID.equals(op.operationKind()))
                && plan.aabb().extentXBlocks() <= 512
                && plan.aabb().extentZBlocks() <= 512
                && plan.aabb().extentYBlocks() <= 128;
        boolean meltwaterHandoffOk = plan.meltwaterHandoffFeatureId().isBlank()
                || (!plan.meltwaterHandoffChecksum().isBlank()
                && sampleMeltwaterMask(
                        blockOf(plan.terminusXMillionths()),
                        blockOf(plan.terminusZMillionths())) == 1);
        boolean leakFree = !iceLeaksOutsideAabb();
        boolean budgetOk = plan.estimatedWorkUnits() <= GlacialIcePlanV2.MAXIMUM_WORK_UNITS
                && sparseIceOk;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean exportOk = whole.length() == 64 && sceneChecksum().length() == 64 && wholeTileOk;
        return new GlacialIceMetrics(
                coldClimateOk,
                bedContactOk,
                flowTerminusOk,
                sparseIceOk,
                meltwaterHandoffOk,
                leakFree,
                wholeTileOk,
                budgetOk,
                exportOk);
    }

    public int sampleSurfaceOwnership(int x, int z) {
        return insideFootprint(x, z) ? 1 : 0;
    }

    public int sampleVolumeOwnership(int x, int y, int z) {
        if (sampleSurfaceOwnership(x, z) == 0) {
            return 0;
        }
        int bedY = blockOf(plan.aabb().minYMillionths());
        int topY = bedY + plan.selectedThicknessBlocks();
        return y >= bedY && y < topY ? 1 : 0;
    }

    public int sampleThickness(int x, int z) {
        return sampleSurfaceOwnership(x, z) == 1 ? plan.selectedThicknessBlocks() : 0;
    }

    public int sampleFlowDirection(int x, int z) {
        return sampleSurfaceOwnership(x, z) == 1 ? plan.flowAzimuthDegrees() + 1 : 0;
    }

    public int sampleBedContact(int x, int z) {
        return sampleSurfaceOwnership(x, z);
    }

    public int sampleMeltwaterMask(int x, int z) {
        if (plan.meltwaterHandoffFeatureId().isBlank()) {
            return 0;
        }
        int tx = blockOf(plan.terminusXMillionths());
        int tz = blockOf(plan.terminusZMillionths());
        long dx = (long) x - tx;
        long dz = (long) z - tz;
        return (dx * dx + dz * dz) <= 4L ? 1 : 0;
    }

    public String sceneChecksum() {
        return digest("scene|" + plan.canonicalChecksum() + "|" + plan.geometryChecksum()
                + "|" + plan.iceKind().name() + "|" + plan.selectedThicknessBlocks());
    }

    public String exportChecksum() {
        return streamExport(false);
    }

    public String tileExportChecksum() {
        return streamExport(true);
    }

    private String streamExport(boolean tiled) {
        StringBuilder builder = new StringBuilder(256);
        // Both whole and tile streams use the same label so X-band merges match whole order.
        builder.append(VERSION).append("|whole|").append(plan.canonicalChecksum()).append('|');
        VolumeSdfAabbV2 aabb = plan.aabb();
        int minX = blockOf(aabb.minXMillionths());
        int maxX = blockOf(aabb.maxXMillionths());
        int minZ = blockOf(aabb.minZMillionths());
        int maxZ = blockOf(aabb.maxZMillionths());
        int bedY = blockOf(aabb.minYMillionths());
        int midX = minX + (maxX - minX) / 2;
        for (int z = minZ; z <= maxZ; z++) {
            if (tiled) {
                appendBand(builder, minX, midX, z, bedY);
                appendBand(builder, midX + 1, maxX, z, bedY);
            } else {
                appendBand(builder, minX, maxX, z, bedY);
            }
        }
        return digest(builder.toString());
    }

    private void appendBand(StringBuilder builder, int fromX, int toX, int z, int bedY) {
        if (fromX > toX) {
            return;
        }
        for (int x = fromX; x <= toX; x++) {
            builder.append(x).append(',').append(z).append(':')
                    .append(sampleSurfaceOwnership(x, z)).append('/')
                    .append(sampleThickness(x, z)).append('/')
                    .append(sampleFlowDirection(x, z)).append('/')
                    .append(sampleMeltwaterMask(x, z)).append('/')
                    .append(sampleVolumeOwnership(x, bedY, z)).append(';');
        }
    }

    private boolean insideFootprint(int x, int z) {
        long px = cellCenter(x);
        long pz = cellCenter(z);
        long half = Math.multiplyExact((long) plan.selectedHalfWidthBlocks(),
                (long) TerrainIntentV2.FIXED_SCALE);
        long dist = distanceToSegment(
                px, pz,
                plan.headXMillionths(), plan.headZMillionths(),
                plan.terminusXMillionths(), plan.terminusZMillionths());
        return dist <= half;
    }

    private boolean iceLeaksOutsideAabb() {
        VolumeSdfAabbV2 aabb = plan.aabb();
        int minX = blockOf(aabb.minXMillionths());
        int maxX = blockOf(aabb.maxXMillionths());
        int minZ = blockOf(aabb.minZMillionths());
        int maxZ = blockOf(aabb.maxZMillionths());
        int bedY = blockOf(aabb.minYMillionths());
        int topY = bedY + plan.selectedThicknessBlocks() - 1;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (sampleSurfaceOwnership(x, z) == 0) {
                    continue;
                }
                long px = cellCenter(x);
                long pz = cellCenter(z);
                if (px < aabb.minXMillionths() || px > aabb.maxXMillionths()
                        || pz < aabb.minZMillionths() || pz > aabb.maxZMillionths()) {
                    return true;
                }
                if (sampleVolumeOwnership(x, bedY, z) == 0 || sampleVolumeOwnership(x, topY, z) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long distanceToSegment(
            long px, long pz, long ax, long az, long bx, long bz
    ) {
        long abx = Math.subtractExact(bx, ax);
        long abz = Math.subtractExact(bz, az);
        long apx = Math.subtractExact(px, ax);
        long apz = Math.subtractExact(pz, az);
        long ab2 = Math.addExact(Math.multiplyExact(abx, abx), Math.multiplyExact(abz, abz));
        if (ab2 == 0L) {
            return length(apx, apz);
        }
        long dot = Math.addExact(Math.multiplyExact(apx, abx), Math.multiplyExact(apz, abz));
        double t = Math.max(0.0, Math.min(1.0, (double) dot / (double) ab2));
        long cx = ax + Math.round(abx * t);
        long cz = az + Math.round(abz * t);
        return length(Math.subtractExact(px, cx), Math.subtractExact(pz, cz));
    }

    private static long length(long dx, long dz) {
        return Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    private static long cellCenter(int cell) {
        return Math.multiplyExact((long) cell, (long) TerrainIntentV2.FIXED_SCALE)
                + TerrainIntentV2.FIXED_SCALE / 2L;
    }

    private static int blockOf(long millionths) {
        return Math.toIntExact(Math.floorDiv(millionths, TerrainIntentV2.FIXED_SCALE));
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record GlacialIceMetrics(
            boolean coldClimateOk,
            boolean bedContactOk,
            boolean flowTerminusOk,
            boolean sparseIceOk,
            boolean meltwaterHandoffOk,
            boolean leakFree,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean exportOk
    ) {
    }
}
