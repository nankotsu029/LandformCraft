package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.Objects;

/**
 * Version-frozen containment preflight policy (V2-6-05). Radii must match the envelope physics
 * policy values when binding an envelope; changing any constant requires a contract version bump.
 */
public record PlacementContainmentPolicyV2(
        String policyVersion,
        String catalogVersion,
        int fluidSupportRadiusXZ,
        int fluidSupportRadiusY,
        int gravityFallBlocks,
        int neighborUpdateRadius,
        ResourceBudget budget
) {
    public static final String POLICY_VERSION = "release-2-placement-containment-policy-v1";
    public static final String CATALOG_VERSION =
            "release-2-placement-block-physics-catalog-v1";
    public static final int MAX_RADIUS = PlacementEnvelopePlanV2.PhysicsPolicy.MAX_RADIUS;

    public PlacementContainmentPolicyV2 {
        policyVersion = nonBlank(policyVersion, "policyVersion", 64);
        if (!POLICY_VERSION.equals(policyVersion)) {
            throw new IllegalArgumentException("unknown placement containment policy version");
        }
        catalogVersion = nonBlank(catalogVersion, "catalogVersion", 64);
        if (!CATALOG_VERSION.equals(catalogVersion)) {
            throw new IllegalArgumentException("unknown placement block physics catalog version");
        }
        if (fluidSupportRadiusXZ < 0 || fluidSupportRadiusXZ > MAX_RADIUS
                || fluidSupportRadiusY < 0 || fluidSupportRadiusY > MAX_RADIUS
                || gravityFallBlocks < 0 || gravityFallBlocks > MAX_RADIUS
                || neighborUpdateRadius < 0 || neighborUpdateRadius > MAX_RADIUS) {
            throw new IllegalArgumentException("containment policy radii out of range");
        }
        Objects.requireNonNull(budget, "budget");
    }

    public static PlacementContainmentPolicyV2 standard() {
        PlacementEnvelopePlanV2.PhysicsPolicy physics = PlacementEnvelopePlanV2.PhysicsPolicy.standard();
        return new PlacementContainmentPolicyV2(
                POLICY_VERSION,
                CATALOG_VERSION,
                physics.fluidSupportRadiusXZ(),
                physics.fluidSupportRadiusY(),
                physics.gravityFallBlocks(),
                physics.neighborUpdateRadius(),
                ResourceBudget.standard());
    }

    public void requireMatchesEnvelope(PlacementEnvelopePlanV2.PhysicsPolicy physicsPolicy) {
        Objects.requireNonNull(physicsPolicy, "physicsPolicy");
        if (!PlacementEnvelopePlanV2.PhysicsPolicy.VERSION.equals(physicsPolicy.policyVersion())
                || physicsPolicy.fluidSupportRadiusXZ() != fluidSupportRadiusXZ
                || physicsPolicy.fluidSupportRadiusY() != fluidSupportRadiusY
                || physicsPolicy.gravityFallBlocks() != gravityFallBlocks
                || physicsPolicy.neighborUpdateRadius() != neighborUpdateRadius) {
            throw new IllegalArgumentException("containment policy does not match envelope physics policy");
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            long maximumScannedBlocks,
            int maximumCacheEntries,
            int maximumBfsNodes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "release-2-placement-containment-budget-v1";
        public static final long MAX_CANONICAL_BYTES = 256L * 1024L;

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown placement containment budget version");
            }
            if (maximumScannedBlocks < 1
                    || maximumCacheEntries < 1
                    || maximumBfsNodes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("invalid placement containment budget");
            }
        }

        public static ResourceBudget standard() {
            return new ResourceBudget(VERSION, 50_000_000L, 4_096, 50_000_000, MAX_CANONICAL_BYTES);
        }
    }

    private static String nonBlank(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and <= " + max);
        }
        return value;
    }
}
