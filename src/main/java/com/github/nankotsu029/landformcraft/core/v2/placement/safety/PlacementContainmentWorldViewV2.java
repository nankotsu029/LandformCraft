package com.github.nankotsu029.landformcraft.core.v2.placement.safety;

import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

/**
 * Post-apply predicted world view for containment preflight. Implementations overlay planned
 * mutation content onto the existing world inside mutation AABBs and expose every block inside the
 * union effect envelope. Must never mutate the live world.
 */
@FunctionalInterface
public interface PlacementContainmentWorldViewV2 {
    /**
     * Returns the canonical block-state string at the inclusive world coordinate. The coordinate
     * must lie inside the effect envelope; callers treat missing coverage as {@code ENVELOPE_GAP}.
     */
    String blockStateAt(int x, int y, int z);

    /** Convenience: whether the view claims coverage for the AABB (optional advisory). */
    default boolean covers(WorldAabbV2 region) {
        return region != null;
    }
}
