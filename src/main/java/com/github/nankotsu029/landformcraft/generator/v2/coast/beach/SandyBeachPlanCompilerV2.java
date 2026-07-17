package com.github.nankotsu029.landformcraft.generator.v2.coast.beach;

import com.github.nankotsu029.landformcraft.generator.v2.coast.CoastalFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.SandyBeachPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.List;
import java.util.Objects;

/** Compiles the typed SANDY_BEACH parameters into an executable fixed-point plan. */
public final class SandyBeachPlanCompilerV2 {
    public SandyBeachPlanV2 compile(
            TerrainIntentV2.Feature feature,
            CoastalFeaturePlanV2 coastalPlan,
            WorldBlueprintV2.Bounds bounds
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(coastalPlan, "coastalPlan");
        Objects.requireNonNull(bounds, "bounds");
        if (feature.kind() != TerrainIntentV2.FeatureKind.SANDY_BEACH
                || coastalPlan.kind() != TerrainIntentV2.FeatureKind.SANDY_BEACH
                || !feature.id().equals(coastalPlan.featureId())) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-plan-binding", "beach feature and coastal plan do not match");
        }
        TerrainIntentV2.SandyBeachParameters parameters =
                (TerrainIntentV2.SandyBeachParameters) feature.parameters();
        if (Math.min(bounds.width(), bounds.length()) < parameters.widthBlocks().minimum() * 2L + 1L) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-width-insufficient", "world bounds cannot contain the minimum beach width");
        }
        requireEndpointTaper(coastalPlan.geometry().paths().getFirst(), parameters.endpointTaperBlocks());
        long minimumSlope = parameters.shoreSlopeDegrees().minimumMillionths();
        long maximumSlope = parameters.shoreSlopeDegrees().maximumMillionths();
        long selectedSlope = Math.addExact(minimumSlope, (maximumSlope - minimumSlope) / 2L);
        int rise = SandyBeachFixedMathV2.tangentMillionths(selectedSlope);
        int maximumRise = SandyBeachFixedMathV2.tangentMillionths(maximumSlope);
        long maximumSurface = Math.addExact(
                Math.multiplyExact((long) bounds.waterLevel(), SandyBeachFixedMathV2.FIXED_SCALE),
                Math.multiplyExact(parameters.widthBlocks().maximum(), (long) maximumRise));
        long minimumBed = Math.subtractExact(
                Math.multiplyExact((long) bounds.waterLevel(), SandyBeachFixedMathV2.FIXED_SCALE),
                Math.multiplyExact((long) parameters.nearshoreDepthBlocks().target(),
                        SandyBeachFixedMathV2.FIXED_SCALE));
        if (maximumSurface > Math.multiplyExact((long) bounds.maxY(), SandyBeachFixedMathV2.FIXED_SCALE)
                || minimumBed < Math.multiplyExact((long) bounds.minY(), SandyBeachFixedMathV2.FIXED_SCALE)) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-vertical-bounds", "beach profile exceeds vertical generation bounds");
        }
        int support = Math.max(parameters.widthBlocks().maximum(),
                parameters.nearshoreDepthBlocks().atDistance() + 1);
        if (coastalPlan.supportRadiusXZ() != support) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-plan-binding", "coastal support cannot observe the nearshore outer edge");
        }
        return new SandyBeachPlanV2(
                SandyBeachPlanV2.VERSION,
                feature.id(),
                SandyBeachPlanV2.WidthProfileKind.ENDPOINT_TAPER,
                parameters.widthBlocks().minimum(),
                parameters.widthBlocks().maximum(),
                parameters.endpointTaperBlocks(),
                parameters.foreshoreShareMillionths(),
                minimumSlope,
                maximumSlope,
                selectedSlope,
                rise,
                parameters.nearshoreDepthBlocks().atDistance(),
                parameters.nearshoreDepthBlocks().target(),
                bounds.minY(), bounds.maxY(), bounds.waterLevel(),
                CoastalFoundationModuleV2.BEACH_LOCAL_WIDTH_FIELD_ID,
                CoastalFoundationModuleV2.BEACH_SURFACE_HEIGHT_FIELD_ID,
                CoastalFoundationModuleV2.BEACH_BAND_FIELD_ID,
                CoastalFoundationModuleV2.BEACH_SEMANTIC_SAND_FIELD_ID,
                support);
    }

    private static void requireEndpointTaper(
            CoastalFeaturePlanV2.BlockPath path,
            int endpointTaperBlocks
    ) {
        try {
            long length = 0L;
            List<CoastalFeaturePlanV2.BlockPoint> points = path.points();
            for (int index = 1; index < points.size(); index++) {
                long dx = Math.subtractExact(points.get(index).xMillionths(), points.get(index - 1).xMillionths());
                long dz = Math.subtractExact(points.get(index).zMillionths(), points.get(index - 1).zMillionths());
                long squared = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
                length = Math.addExact(length, SandyBeachFixedMathV2.integerSquareRoot(squared));
            }
            long required = Math.multiplyExact(2L * endpointTaperBlocks, SandyBeachFixedMathV2.FIXED_SCALE);
            if (length < required) {
                throw new SandyBeachGenerationException(
                        "v2.sandy-beach-endpoint-profile", "endpoint taper regions overlap");
            }
        } catch (ArithmeticException exception) {
            throw new SandyBeachGenerationException(
                    "v2.sandy-beach-overflow", "beach path length overflow", exception);
        }
    }
}
