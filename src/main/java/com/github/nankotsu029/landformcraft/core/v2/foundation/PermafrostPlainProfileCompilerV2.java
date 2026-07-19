package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.GlacialIcePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PermafrostPlainProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;

import java.util.Objects;

/**
 * Compiles {@link PermafrostPlainProfileV2} for an existing {@code PLAIN} feature under cold climate.
 * Not a FeatureKind and not a dedicated landform generator (V2-10-02).
 */
public final class PermafrostPlainProfileCompilerV2 {
    private static final String ZERO = "0".repeat(64);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public PermafrostPlainProfileV2 compile(
            TerrainIntentV2 intent,
            PlainPlanV2 sealedPlainPlan,
            String plainGeometryChecksum
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(sealedPlainPlan, "sealedPlainPlan");
        Objects.requireNonNull(plainGeometryChecksum, "plainGeometryChecksum");
        if (!sealedPlainPlan.canonicalChecksum().matches("^[0-9a-f]{64}$")) {
            throw failure("v2.permafrost-plain-checksum", "plain plan must be sealed");
        }

        TerrainIntentV2.Feature plain = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.PLAIN)
                .filter(feature -> feature.id().equals(sealedPlainPlan.featureId()))
                .findFirst()
                .orElseThrow(() -> failure("v2.permafrost-plain-missing",
                        "permafrost profile requires a PLAIN feature matching the sealed plan"));

        String climatePreset = resolveColdClimate(intent);
        String climateBinding = GlacialIcePlanCompilerV2.climateBindingChecksum(
                climatePreset, plainGeometryChecksum + "|" + sealedPlainPlan.canonicalChecksum());
        String geometryChecksum = GlacialIcePlanCompilerV2.climateBindingChecksum(
                climatePreset, plainGeometryChecksum);

        long work = Math.max(1L, sealedPlainPlan.estimatedRasterWorkUnits());
        if (work > PermafrostPlainProfileV2.MAXIMUM_WORK_UNITS) {
            throw failure("v2.permafrost-plain-budget", "permafrost profile work units exceed budget");
        }

        return new PermafrostPlainProfileV2(
                PermafrostPlainProfileV2.VERSION,
                "permafrost." + plain.id(),
                PermafrostPlainProfileV2.CONTRACT_VERSION,
                plain.id(),
                sealedPlainPlan.canonicalChecksum(),
                climatePreset,
                climateBinding,
                4,
                450_000,
                Math.min(64, Math.max(4, sealedPlainPlan.supportRadiusXZ())),
                work,
                geometryChecksum,
                ZERO);
    }

    public void requireColdClimate(TerrainIntentV2 intent) {
        resolveColdClimate(intent);
    }

    private static String resolveColdClimate(TerrainIntentV2 intent) {
        String env = intent.environment().climatePreset();
        if (env == null || env.isBlank()) {
            throw failure("v2.permafrost-warm-climate",
                    "permafrost profile requires environment climatePreset");
        }
        if (!GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(env)) {
            throw failure("v2.permafrost-warm-climate",
                    "environment climatePreset is warm/unsupported for permafrost");
        }
        return env;
    }

    public String plainGeometryChecksum(TerrainIntentV2.Feature plainFeature) {
        return codec.geometryChecksum(plainFeature.geometry());
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
