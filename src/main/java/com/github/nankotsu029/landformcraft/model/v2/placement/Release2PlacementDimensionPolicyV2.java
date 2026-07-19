package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.Objects;

/**
 * Single admission point for Release 2 Paper placement dimensions (V2-11-02).
 *
 * <p>Normal operation uses {@link Release2MeasuredDimensionGateV2}, whose configured ceiling is
 * clamped to the Feature Support Catalog hard limit, so no configuration can widen it. Layouts
 * above that ceiling are rejected before any world mutation or durable state write unless the
 * explicitly enabled {@link Release2MeasurementProfileV2} admits them. The catalog remains the
 * single source of the production limit; this type adds no dimension constant of its own.
 */
public record Release2PlacementDimensionPolicyV2(
        Release2MeasuredDimensionGateV2 productionGate,
        Release2MeasurementProfileV2 measurementProfile
) {
    public Release2PlacementDimensionPolicyV2 {
        Objects.requireNonNull(productionGate, "productionGate");
        Objects.requireNonNull(measurementProfile, "measurementProfile");
    }

    /** Catalog hard limit, measurement profile disabled. */
    public static Release2PlacementDimensionPolicyV2 production() {
        return new Release2PlacementDimensionPolicyV2(
                Release2MeasuredDimensionGateV2.production(),
                Release2MeasurementProfileV2.disabled());
    }

    /** Offline and unit callers that intentionally opt out of any published ceiling. */
    public static Release2PlacementDimensionPolicyV2 unlimited() {
        return new Release2PlacementDimensionPolicyV2(
                Release2MeasuredDimensionGateV2.unlimited(),
                Release2MeasurementProfileV2.disabled());
    }

    public static Release2PlacementDimensionPolicyV2 of(Release2MeasuredDimensionGateV2 productionGate) {
        return new Release2PlacementDimensionPolicyV2(
                productionGate, Release2MeasurementProfileV2.disabled());
    }

    /**
     * Rejects any layout the normal profile does not admit, unless the measurement profile
     * explicitly admits it for this world and actor.
     */
    public void requireAdmitted(
            int width, int length, String worldName, PlacementActorKindV2 actorKind) {
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("Release layout dimensions must be >= 1");
        }
        if (width <= productionGate.maximumWidth() && length <= productionGate.maximumLength()) {
            return;
        }
        if (measurementProfile.admits(width, length, worldName, actorKind)) {
            return;
        }
        throw new IllegalArgumentException(
                "Release 2 Paper placement dimensions " + width + "x" + length
                        + " exceed the normal-operation ceiling "
                        + productionGate.maximumWidth() + "x" + productionGate.maximumLength()
                        + " (Feature Support Catalog hard limit); "
                        + measurementProfile.describe()
                        + " — unmeasured dimensions require the V2-11-02 measurement profile on an"
                        + " isolated world from a CONSOLE/RCON operator");
    }
}
