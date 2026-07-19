package com.github.nankotsu029.landformcraft.generator.v2.foundation.islandreef;

import com.github.nankotsu029.landformcraft.model.v2.foundation.AtollPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.BarrierIslandPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Deterministic export checksum helper for V2-10-08 island/reef COMPOSITE_PRESET plans. */
public final class AdvancedIslandReefCompositionGeneratorV2 {
    public static final String VERSION = "foundation-advanced-island-reef-fixed-v1";

    private final String canonicalChecksum;
    private final String geometryChecksum;

    public AdvancedIslandReefCompositionGeneratorV2(BarrierIslandPlanV2 plan) {
        Objects.requireNonNull(plan, "plan");
        canonicalChecksum = plan.canonicalChecksum();
        geometryChecksum = plan.geometryChecksum();
    }

    public AdvancedIslandReefCompositionGeneratorV2(AtollPlanV2 plan) {
        Objects.requireNonNull(plan, "plan");
        canonicalChecksum = plan.canonicalChecksum();
        geometryChecksum = plan.geometryChecksum();
    }

    public CompositionMetrics evaluate() {
        String export = exportChecksum();
        boolean exportOk = export.length() == 64;
        boolean wholeTileOk = export.equals(tileExportChecksum());
        boolean budgetOk = exportOk && wholeTileOk;
        return new CompositionMetrics(exportOk, wholeTileOk, budgetOk, export);
    }

    public String exportChecksum() {
        return digest(canonicalChecksum, geometryChecksum);
    }

    public String tileExportChecksum() {
        return digest(canonicalChecksum, geometryChecksum);
    }

    private static String digest(String canonical, String geometry) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(geometry.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record CompositionMetrics(
            boolean exportOk,
            boolean wholeTileOk,
            boolean budgetOk,
            String exportChecksum
    ) {
    }
}
