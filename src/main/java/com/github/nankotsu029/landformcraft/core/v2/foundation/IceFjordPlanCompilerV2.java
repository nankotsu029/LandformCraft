package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.GlacialIcePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.IceFjordPlanV2;

import java.util.Objects;

/**
 * Compiles the {@code ICE_FJORD} COMPOSITE_PRESET from a sealed VALLEY_GLACIER plan plus a FJORD
 * feature. Does not invent a FeatureKind or dedicated world generator.
 */
public final class IceFjordPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public IceFjordPlanV2 compile(TerrainIntentV2 intent, GlacialIcePlanV2 valleyGlacier) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(valleyGlacier, "valleyGlacier");
        if (valleyGlacier.iceKind() != GlacialIcePlanV2.IceKind.VALLEY_GLACIER) {
            throw new FoundationSliceException("v2.ice-fjord-kind",
                    "ICE_FJORD preset requires a VALLEY_GLACIER plan");
        }

        TerrainIntentV2.Feature fjord = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.FJORD)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException(
                        "v2.ice-fjord-missing-fjord", "ICE_FJORD requires a FJORD feature"));

        if (!GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(valleyGlacier.climatePreset())) {
            throw new FoundationSliceException("v2.ice-fjord-warm-climate",
                    "ICE_FJORD requires cold climate binding from VALLEY_GLACIER");
        }
        String envClimate = intent.environment().climatePreset();
        if (envClimate != null && !envClimate.isBlank()
                && !GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(envClimate)) {
            throw new FoundationSliceException("v2.ice-fjord-warm-climate",
                    "ICE_FJORD environment climatePreset is warm/unsupported");
        }

        String fjordGeometryChecksum = codec.geometryChecksum(fjord.geometry());
        String geometryChecksum = GlacialIcePlanCompilerV2.climateBindingChecksum(
                valleyGlacier.climatePreset(),
                fjordGeometryChecksum + "|" + valleyGlacier.canonicalChecksum());

        return new IceFjordPlanV2(
                IceFjordPlanV2.VERSION,
                "ice-fjord." + valleyGlacier.featureId(),
                IceFjordPlanV2.CONTRACT_VERSION,
                fjord.id(),
                fjordGeometryChecksum,
                valleyGlacier.featureId(),
                valleyGlacier.canonicalChecksum(),
                valleyGlacier.climatePreset(),
                valleyGlacier.climateBindingChecksum(),
                IceFjordPlanV2.DEFAULT_SNOW_PROFILE_ID,
                Math.max(4, valleyGlacier.supportRadiusXZ()),
                Math.max(1L, valleyGlacier.estimatedWorkUnits()),
                geometryChecksum,
                ZERO);
    }
}
