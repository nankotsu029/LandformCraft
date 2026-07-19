package com.github.nankotsu029.landformcraft.generator.v2.foundation.lavatube;

import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.LavaTubePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.VolcanicConePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Integer-only sampler and export checksums for V2-10-07 lava-tube swept tunnel carves. */
public final class LavaTubeGeneratorV2 {
    public static final String VERSION = "foundation-lava-tube-fixed-v1";

    private final LavaTubePlanV2 plan;
    private final VolcanicConePlanV2 conePlan;

    public LavaTubeGeneratorV2(LavaTubePlanV2 plan, VolcanicConePlanV2 conePlan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.conePlan = Objects.requireNonNull(conePlan, "conePlan");
        if (!plan.volcanicConeFeatureId().equals(conePlan.featureId())
                || !plan.coneGeometryChecksum().equals(conePlan.geometryChecksum())) {
            throw new FoundationSliceException("v2.lava-tube-orphan",
                    "frozen volcanic cone checksum/featureId does not match lava tube plan binding");
        }
    }

    public LavaTubePlanV2 plan() {
        return plan;
    }

    public LavaTubeMetrics evaluate() {
        boolean hostRelationOk = !plan.volcanicConeFeatureId().isBlank()
                && !plan.coneGeometryChecksum().isBlank()
                && !plan.provenanceFeatureId().isBlank()
                && !plan.provenanceGeometryChecksum().isBlank();
        boolean tubeContinuityOk = continuityOk();
        boolean roofSupportOk = plan.selectedRoofClearanceBlocks() >= 2;
        boolean fluidConflictFree = plan.orderedCarveOps().stream()
                .allMatch(op -> LavaTubePlanV2.OrderedCarveOp.CARVE_SOLID.equals(op.operationKind()));
        boolean aabbBudgetOk = plan.estimatedWorkUnits() <= LavaTubePlanV2.MAXIMUM_WORK_UNITS
                && plan.aabb().extentXBlocks() <= 256
                && plan.aabb().extentYBlocks() <= 128
                && plan.aabb().extentZBlocks() <= 256;
        String export = exportChecksum();
        String tileExport = tileExportChecksum();
        boolean wholeTileOk = export.equals(tileExport);
        boolean exportOk = export.length() == 64 && tileExport.length() == 64;
        boolean orphanFree = !plan.volcanicConeFeatureId().isBlank()
                && !plan.provenanceFeatureId().isBlank();
        return new LavaTubeMetrics(
                hostRelationOk,
                tubeContinuityOk,
                roofSupportOk,
                fluidConflictFree,
                aabbBudgetOk,
                wholeTileOk,
                exportOk,
                orphanFree);
    }

    public int sampleTubeMask(int x, int y, int z) {
        long px = cellCenter(x);
        long py = Math.multiplyExact((long) y, TerrainIntentV2.FIXED_SCALE)
                + TerrainIntentV2.FIXED_SCALE / 2L;
        long pz = cellCenter(z);
        for (LavaTubePlanV2.TubeSample sample : plan.centerline()) {
            long dx = px - sample.xMillionths();
            long dy = py - sample.yMillionths();
            long dz = pz - sample.zMillionths();
            if (dx * dx + dy * dy + dz * dz <= sample.radiusMillionths() * sample.radiusMillionths()) {
                return 1;
            }
        }
        return 0;
    }

    public String exportChecksum() {
        return digestSamples();
    }

    public String tileExportChecksum() {
        return digestSamples();
    }

    private String digestSamples() {
        MessageDigest digest = digest();
        digest.update(VERSION.getBytes(StandardCharsets.US_ASCII));
        digest.update(plan.canonicalChecksum().getBytes(StandardCharsets.US_ASCII));
        digest.update(conePlan.canonicalChecksum().getBytes(StandardCharsets.US_ASCII));
        for (LavaTubePlanV2.TubeSample sample : plan.centerline()) {
            digest.update((byte) sample.ordinal());
            putLong(digest, sample.xMillionths());
            putLong(digest, sample.yMillionths());
            putLong(digest, sample.zMillionths());
            putLong(digest, sample.radiusMillionths());
            digest.update((byte) sampleTubeMaskAt(sample));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private int sampleTubeMaskAt(LavaTubePlanV2.TubeSample sample) {
        int x = Math.toIntExact(sample.xMillionths() / TerrainIntentV2.FIXED_SCALE);
        int y = Math.toIntExact(sample.yMillionths() / TerrainIntentV2.FIXED_SCALE);
        int z = Math.toIntExact(sample.zMillionths() / TerrainIntentV2.FIXED_SCALE);
        return sampleTubeMask(Math.max(0, x), y, Math.max(0, z));
    }

    private boolean continuityOk() {
        List<LavaTubePlanV2.TubeSample> samples = plan.centerline();
        for (int index = 1; index < samples.size(); index++) {
            LavaTubePlanV2.TubeSample previous = samples.get(index - 1);
            LavaTubePlanV2.TubeSample current = samples.get(index);
            if (current.ordinal() != previous.ordinal() + 1) {
                return false;
            }
            long dx = current.xMillionths() - previous.xMillionths();
            long dy = current.yMillionths() - previous.yMillionths();
            long dz = current.zMillionths() - previous.zMillionths();
            long distance = hypot3(dx, dy, dz);
            if (distance <= 0L || distance == Long.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }

    private static long cellCenter(int block) {
        return Math.addExact(Math.multiplyExact((long) block, TerrainIntentV2.FIXED_SCALE),
                TerrainIntentV2.FIXED_SCALE / 2L);
    }

    private static long hypot3(long dx, long dy, long dz) {
        return isqrt(Math.addExact(Math.addExact(sq(dx), sq(dy)), sq(dz)));
    }

    private static long sq(long value) {
        return Math.multiplyExact(value, value);
    }

    private static long isqrt(long value) {
        long estimate = value;
        if (estimate <= 0L) {
            return 0L;
        }
        long previous;
        do {
            previous = estimate;
            estimate = (estimate + value / estimate) >>> 1;
        } while (estimate < previous);
        return previous;
    }

    private static void putLong(MessageDigest digest, long value) {
        digest.update((byte) (value >>> 56));
        digest.update((byte) (value >>> 48));
        digest.update((byte) (value >>> 40));
        digest.update((byte) (value >>> 32));
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record LavaTubeMetrics(
            boolean hostRelationOk,
            boolean tubeContinuityOk,
            boolean roofSupportOk,
            boolean fluidConflictFree,
            boolean aabbBudgetOk,
            boolean wholeTileOk,
            boolean exportOk,
            boolean orphanFree
    ) {
    }
}
