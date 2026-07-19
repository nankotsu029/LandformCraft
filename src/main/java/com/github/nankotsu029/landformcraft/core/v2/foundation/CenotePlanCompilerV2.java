package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.CenotePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.CaveNetworkPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Compiles the {@code CENOTE} COMPOSITE_PRESET from sealed sinkhole and host cave plus
 * {@code FLOODED_CAVE} fluid hook. Does not invent a FeatureKind.
 */
public final class CenotePlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);

    public Optional<CenotePlanV2> compileOptional(
            TerrainIntentV2 intent,
            SinkholePlanV2 sinkhole,
            CaveNetworkPlanV2 hostCave
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(sinkhole, "sinkhole");
        Objects.requireNonNull(hostCave, "hostCave");

        Optional<TerrainIntentV2.Feature> flooded = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.FLOODED_CAVE)
                .findFirst();
        if (flooded.isEmpty()) {
            return Optional.empty();
        }
        if (!(flooded.get().parameters() instanceof TerrainIntentV2.FloodedCaveParameters parameters)) {
            throw new FoundationSliceException("v2.cenote-missing-fluid",
                    "FLOODED_CAVE requires FloodedCaveParameters");
        }
        if (!hostCave.featureId().equals(sinkhole.caveNetworkFeatureId())) {
            throw new FoundationSliceException("v2.cenote-orphan",
                    "cenote host cave does not match sinkhole cave binding");
        }
        String hint = parameters.hostCaveFeatureIdHint();
        if (!hint.isBlank() && !hint.equals(hostCave.featureId())) {
            throw new FoundationSliceException("v2.cenote-orphan",
                    "FLOODED_CAVE hostCaveFeatureIdHint does not match host cave");
        }

        String geometryChecksum = bindGeometryChecksum(
                sinkhole.geometryChecksum(), hostCave.canonicalChecksum(), parameters.fluidBodyId());
        return Optional.of(new CenotePlanV2(
                CenotePlanV2.VERSION,
                "cenote." + sinkhole.featureId(),
                CenotePlanV2.CONTRACT_VERSION,
                sinkhole.featureId(),
                sinkhole.canonicalChecksum(),
                hostCave.featureId(),
                hostCave.canonicalChecksum(),
                parameters.fluidBodyId(),
                flooded.get().id(),
                SinkholePlanV2.MATERIAL_HANDOFF_ID,
                Math.max(4, sinkhole.supportRadiusXZ()),
                Math.max(1L, sinkhole.estimatedWorkUnits()),
                geometryChecksum,
                ZERO));
    }

    private static String bindGeometryChecksum(String left, String middle, String right) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest((left + "|" + middle + "|" + right).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
